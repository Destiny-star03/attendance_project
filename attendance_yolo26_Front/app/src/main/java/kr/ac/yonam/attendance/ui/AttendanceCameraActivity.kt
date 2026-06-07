package kr.ac.yonam.attendance.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ActivityAttendanceCameraBinding
import kr.ac.yonam.attendance.model.AttendanceItem
import kr.ac.yonam.attendance.model.AttendanceResponse
import kr.ac.yonam.attendance.model.Session
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ImageUtil
import kr.ac.yonam.attendance.util.ServerConfig
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AttendanceCameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAttendanceCameraBinding
    private lateinit var adapter: AttendanceAdapter
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var repository: AttendanceRepository

    private var cameraProvider: ProcessCameraProvider? = null
    private var activeSessionId: Int? = null
    private val studentItems = mutableListOf<AttendanceItem>()

    @Volatile
    private var isRequesting = false

    @Volatile
    private var isScreenDestroyed = false

    private var lastRequestAtMillis = 0L
    private var pauseUntilMillis = 0L

    private val serverUrl: String by lazy {
        ServerConfig.normalizeBaseUrl(intent.getStringExtra(EXTRA_SERVER_URL))
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCameraPreview()
        } else {
            binding.faceGuideOverlay.setGuideState("no_face", "카메라 권한이 필요합니다")
            showRecognitionMessage("카메라 권한이 필요합니다.", R.color.yonam_red)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttendanceCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = AttendanceRepository(serverUrl)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupStudentList()
        bindActions()
        showInitialRecognitionState()
        loadStudents()
        checkServerConnection()
        loadActiveSession()
        ensureCameraPermission()
    }

    private fun setupStudentList() {
        adapter = AttendanceAdapter { item ->
            StudentDetailDialog.newInstance(item, serverUrl).show(supportFragmentManager, StudentDetailDialog.TAG)
        }
        binding.recyclerStudents.layoutManager = LinearLayoutManager(this)
        binding.recyclerStudents.adapter = adapter
    }

    private fun bindActions() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
        binding.buttonRefreshStudents.setOnClickListener {
            loadStudents()
            showInitialRecognitionState()
        }
    }

    private fun ensureCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCameraPreview()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCameraPreview() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(binding.previewCamera.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .apply {
                        setAnalyzer(cameraExecutor) { imageProxy ->
                            analyzeFrame(imageProxy)
                        }
                    }

                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    showInitialRecognitionState()
                } catch (frontCameraError: Exception) {
                    bindBackCameraFallback(provider, preview, imageAnalysis)
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun bindBackCameraFallback(
        provider: ProcessCameraProvider,
        preview: Preview,
        imageAnalysis: ImageAnalysis
    ) {
        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
            showInitialRecognitionState()
        } catch (error: Exception) {
            binding.faceGuideOverlay.setGuideState("no_face", "카메라 프리뷰를 시작하지 못했습니다")
            showRecognitionMessage("카메라 프리뷰를 시작하지 못했습니다.", R.color.yonam_red)
        }
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (
            isScreenDestroyed ||
            isRequesting ||
            now < pauseUntilMillis ||
            now - lastRequestAtMillis < REQUEST_INTERVAL_MILLIS
        ) {
            imageProxy.close()
            return
        }

        isRequesting = true
        lastRequestAtMillis = now

        val jpegBytes = try {
            ImageUtil.imageProxyToJpegBytes(imageProxy)
        } catch (error: Exception) {
            null
        } finally {
            imageProxy.close()
        }

        if (jpegBytes == null) {
            isRequesting = false
            return
        }

        lifecycleScope.launch {
            try {
                val response = repository.recognizeAttendance(jpegBytes, activeSessionId)
                handleRecognitionResponse(response)
            } finally {
                isRequesting = false
            }
        }
    }

    private fun handleRecognitionResponse(response: AttendanceResponse) {
        when (response.status) {
            "no_face", "image_error" -> showNoFace()
            "recognizing" -> showRecognizing(response)
            "attended" -> showAttended(response)
            "already_attended" -> showAlreadyAttended(response)
            "unknown" -> showUnknown()
            "multiple_faces" -> showMultipleFaces()
            "network_error" -> showNetworkError(response.message)
            else -> showNetworkError(response.message ?: "서버 응답을 확인할 수 없습니다.")
        }
    }

    private fun showNoFace() {
        binding.faceGuideOverlay.setGuideState("no_face", "얼굴을 원 안에 맞춰주세요")
        showRecognitionMessage("얼굴을 화면에 맞춰주세요", R.color.text_primary)
        binding.textRecognitionTimer.text = "0.0 / 3초"
        binding.textRecognizedStudent.text = "현재 인식된 학생: -"
        binding.progressRecognition.progress = 0
    }

    private fun showRecognizing(response: AttendanceResponse) {
        val elapsed = response.elapsedSeconds ?: 0.0
        val hold = response.holdSeconds ?: 3.0
        binding.faceGuideOverlay.setGuideState(
            "recognizing",
            "인식 중...",
            "${formatSeconds(elapsed)} / ${formatSeconds(hold)}초"
        )
        showRecognitionMessage("인식 중... ${formatSeconds(elapsed)} / ${formatSeconds(hold)}초", R.color.yonam_blue)
        binding.textRecognitionTimer.text = "${formatSeconds(elapsed)} / ${formatSeconds(hold)}초"
        binding.progressRecognition.progress = ((elapsed / hold).coerceIn(0.0, 1.0) * 300).toInt()
        binding.textRecognizedStudent.text = recognizedStudentText(response)
        updateStudentFromRecognition(response, STATUS_RECOGNIZING)
    }

    private fun showAttended(response: AttendanceResponse) {
        binding.faceGuideOverlay.setGuideState("attended", "출석 완료")
        showRecognitionMessage("출석 완료", R.color.yonam_green)
        val hold = response.holdSeconds ?: 3.0
        binding.textRecognitionTimer.text = "${formatSeconds(hold)} / ${formatSeconds(hold)}초"
        binding.progressRecognition.progress = 300
        binding.textRecognizedStudent.text = recognizedStudentText(response)
        updateStudentFromRecognition(response, STATUS_ATTENDED)
        loadAttendanceAndSyncStatus()
        pauseAndResetRecognition()
    }

    private fun showAlreadyAttended(response: AttendanceResponse) {
        binding.faceGuideOverlay.setGuideState("already_attended", "이미 출석함")
        showRecognitionMessage("이미 출석함", R.color.yonam_blue)
        binding.textRecognizedStudent.text = recognizedStudentText(response)
        updateStudentFromRecognition(response, STATUS_ALREADY_ATTENDED)
        loadAttendanceAndSyncStatus()
        pauseAndResetRecognition()
    }

    private fun showUnknown() {
        binding.faceGuideOverlay.setGuideState("unknown", "미등록 사용자입니다")
        showRecognitionMessage("미등록 사용자", R.color.yonam_red)
        binding.textRecognizedStudent.text = "현재 인식된 학생: -"
    }

    private fun showMultipleFaces() {
        binding.faceGuideOverlay.setGuideState("multiple_faces", "한 명만 화면에 들어오게 해주세요")
        showRecognitionMessage("한 명만 촬영해 주세요", R.color.yonam_red)
        binding.textRecognizedStudent.text = "현재 인식된 학생: -"
    }

    private fun showNetworkError(message: String?) {
        binding.faceGuideOverlay.setGuideState("unknown", message ?: "서버 오류가 발생했습니다")
        showRecognitionMessage("서버 연결 오류", R.color.yonam_red)
        binding.textRecognitionTimer.text = "0.0 / 3초"
        binding.textRecognizedStudent.text = message ?: "서버 응답을 받지 못했습니다."
        binding.progressRecognition.progress = 0
    }

    private fun pauseAndResetRecognition() {
        pauseUntilMillis = System.currentTimeMillis() + RESULT_HOLD_MILLIS
        lifecycleScope.launch {
            delay(RESULT_HOLD_MILLIS)
            if (!isScreenDestroyed) {
                showInitialRecognitionState()
            }
        }
    }

    private fun loadStudents() {
        showStudentListMessage("학생 목록을 불러오는 중입니다.", isError = false)
        setStudentListLoading(true)

        lifecycleScope.launch {
            try {
                val response = repository.getStudents()
                if (response.success == true) {
                    val students = response.items.orEmpty()
                    studentItems.clear()
                    studentItems.addAll(
                        students.map { student ->
                            AttendanceItem(
                                studentId = student.studentId,
                                studentNo = student.studentNo,
                                name = student.name,
                                department = student.department,
                                status = normalizeAttendanceStatus(student.attendanceStatus ?: student.status)
                            )
                        }
                    )
                    adapter.submitList(studentItems.toList())

                    if (studentItems.isEmpty()) {
                        showStudentListMessage("등록된 학생이 없습니다.", isError = false)
                    } else {
                        hideStudentListMessage()
                    }

                    loadAttendanceAndSyncStatus()
                } else {
                    studentItems.clear()
                    adapter.submitList(emptyList())
                    val errorMessage = response.message?.takeIf { it.isNotBlank() }?.let {
                        "학생 목록을 불러오지 못했습니다.\n$it"
                    } ?: "학생 목록을 불러오지 못했습니다."
                    showStudentListMessage(
                        errorMessage,
                        isError = true
                    )
                }
            } catch (error: Exception) {
                studentItems.clear()
                adapter.submitList(emptyList())
                showStudentListMessage(
                    "학생 목록을 불러오지 못했습니다.\n${error.message ?: "알 수 없는 오류"}",
                    isError = true
                )
            } finally {
                setStudentListLoading(false)
            }
        }
    }

    private fun updateStudentFromRecognition(response: AttendanceResponse, status: String) {
        val student = response.student ?: return
        val updated = updateStudentStatus(student.studentId, status) ||
            updateStudentStatusByStudentNo(student.studentNo, status)

        if (!updated) {
            upsertStudentFromRecognition(response, status)
        }
    }

    private fun upsertStudentFromRecognition(response: AttendanceResponse, status: String) {
        val student = response.student ?: return
        val identity = student.studentNo ?: student.studentId?.toString() ?: return
        val item = AttendanceItem(
            sessionId = response.session?.sessionId ?: activeSessionId,
            subjectName = response.session?.subjectName,
            classDate = response.session?.classDate,
            startTime = response.session?.startTime,
            endTime = response.session?.endTime,
            studentId = student.studentId,
            studentNo = student.studentNo,
            name = student.name,
            department = student.department,
            status = normalizeAttendanceStatus(status)
        )

        val index = studentItems.indexOfFirst { it.sameStudent(identity, student.studentId) }
        if (index >= 0) {
            studentItems[index] = mergeKnownFields(studentItems[index], item)
        } else {
            studentItems.add(0, item)
        }
        adapter.submitList(studentItems.toList())
        if (studentItems.isNotEmpty()) {
            hideStudentListMessage()
        }
    }

    private fun loadAttendanceAndSyncStatus() {
        lifecycleScope.launch {
            val response = repository.getAttendance(sessionId = activeSessionId)
            if (response.success == false) return@launch

            response.session?.let { session ->
                activeSessionId = session.sessionId ?: activeSessionId
                showSession(session)
            }

            mergeAttendanceRecords(response.items.orEmpty())
            adapter.submitList(studentItems.toList())
        }
    }

    private fun mergeAttendanceRecords(records: List<AttendanceItem>) {
        records.forEach { record ->
            val normalizedRecord = record.copy(status = normalizeAttendanceStatus(record.status))
            val identity = normalizedRecord.studentNo ?: normalizedRecord.studentId?.toString()
            val index = studentItems.indexOfFirst {
                it.sameStudent(identity, normalizedRecord.studentId)
            }

            if (index >= 0) {
                studentItems[index] = mergeKnownFields(studentItems[index], normalizedRecord)
            }
        }
    }

    private fun updateStudentStatus(studentId: Int?, status: String): Boolean {
        return updateStudentStatusInternal(studentId = studentId, studentNo = null, status = status)
    }

    private fun updateStudentStatusByStudentNo(studentNo: String?, status: String): Boolean {
        return updateStudentStatusInternal(studentId = null, studentNo = studentNo, status = status)
    }

    private fun updateStudentStatusInternal(
        studentId: Int?,
        studentNo: String?,
        status: String
    ): Boolean {
        val normalizedStatus = normalizeAttendanceStatus(status)
        val index = studentItems.indexOfFirst { item ->
            when {
                studentId != null && item.studentId == studentId -> true
                !studentNo.isNullOrBlank() && item.studentNo == studentNo -> true
                else -> false
            }
        }

        if (index < 0) return false

        studentItems[index] = studentItems[index].copy(status = normalizedStatus)
        adapter.submitList(studentItems.toList())
        return true
    }

    private fun AttendanceItem.sameStudent(identity: String?, otherStudentId: Int?): Boolean {
        return when {
            identity != null && studentNo == identity -> true
            otherStudentId != null && studentId == otherStudentId -> true
            else -> false
        }
    }

    private fun mergeKnownFields(base: AttendanceItem, update: AttendanceItem): AttendanceItem {
        return base.copy(
            attendanceId = update.attendanceId ?: base.attendanceId,
            sessionId = update.sessionId ?: base.sessionId,
            subjectName = update.subjectName ?: base.subjectName,
            classDate = update.classDate ?: base.classDate,
            startTime = update.startTime ?: base.startTime,
            endTime = update.endTime ?: base.endTime,
            studentId = update.studentId ?: base.studentId,
            studentNo = update.studentNo ?: base.studentNo,
            name = update.name ?: base.name,
            department = update.department ?: base.department,
            attendanceDate = update.attendanceDate ?: base.attendanceDate,
            attendanceTime = update.attendanceTime ?: base.attendanceTime,
            status = update.status ?: base.status,
            confidence = update.confidence ?: base.confidence,
            distance = update.distance ?: base.distance
        )
    }

    private fun normalizeAttendanceStatus(status: String?): String {
        return when (status) {
            "attended", "present", "success", "checked_in" -> STATUS_ATTENDED
            "already_attended" -> STATUS_ALREADY_ATTENDED
            "recognizing" -> STATUS_RECOGNIZING
            "late" -> STATUS_LATE
            "absent" -> STATUS_ABSENT
            "pending", null, "" -> STATUS_PENDING
            else -> status
        }
    }

    private fun checkServerConnection() {
        lifecycleScope.launch {
            val response = repository.checkHealth()
            if (response.status == "ok" || response.success == true) {
                binding.textServerStatus.text = "서버 연결 성공"
                binding.textServerStatus.setTextColor(color(R.color.yonam_green))
            } else {
                binding.textServerStatus.text = "서버 연결 실패"
                binding.textServerStatus.setTextColor(color(R.color.yonam_red))
            }
        }
    }

    private fun loadActiveSession() {
        lifecycleScope.launch {
            val response = repository.getActiveSession()
            activeSessionId = response.session?.sessionId
            showSession(response.session)
            loadAttendanceAndSyncStatus()
        }
    }

    private fun showInitialRecognitionState() {
        binding.faceGuideOverlay.setGuideState("idle", "얼굴을 원 안에 맞춰주세요")
        showRecognitionMessage("얼굴을 화면에 맞춰주세요", R.color.text_primary)
        binding.textRecognitionTimer.text = "0.0 / 3초"
        binding.textRecognizedStudent.text = "현재 인식된 학생: -"
        binding.progressRecognition.progress = 0
    }

    private fun showRecognitionMessage(message: String, colorResId: Int) {
        binding.textRecognitionStatus.text = message
        binding.textRecognitionStatus.setTextColor(color(colorResId))
    }

    private fun recognizedStudentText(response: AttendanceResponse): String {
        val student = response.student ?: return "현재 인식된 학생: -"
        return "현재 인식된 학생: ${student.name ?: "-"} / ${student.studentNo ?: "-"} / ${student.department ?: "-"}"
    }

    private fun showSession(session: Session?) {
        binding.textSessionInfo.text = if (session == null) {
            "현재 수업: -"
        } else {
            "현재 수업: ${session.subjectName ?: "-"} / ${session.classDate ?: "-"} / " +
                "${session.startTime ?: "-"}~${session.endTime ?: "-"}"
        }
    }

    private fun formatSeconds(value: Double): String {
        return String.format("%.1f", value)
    }

    private fun showStudentListMessage(message: String, isError: Boolean) {
        binding.textStudentListMessage.text = message
        binding.textStudentListMessage.visibility = View.VISIBLE
        binding.textStudentListMessage.setTextColor(
            color(if (isError) R.color.yonam_red else R.color.text_secondary)
        )
    }

    private fun hideStudentListMessage() {
        binding.textStudentListMessage.visibility = View.GONE
    }

    private fun setStudentListLoading(isLoading: Boolean) {
        binding.buttonRefreshStudents.isEnabled = !isLoading
    }

    override fun onDestroy() {
        isScreenDestroyed = true
        isRequesting = false
        cameraProvider?.unbindAll()
        cameraProvider = null
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
        super.onDestroy()
    }

    private fun color(colorResId: Int): Int {
        return ContextCompat.getColor(this, colorResId)
    }

    companion object {
        const val EXTRA_SERVER_URL = "extra_server_url"
        private const val REQUEST_INTERVAL_MILLIS = 800L
        private const val RESULT_HOLD_MILLIS = 2500L

        private const val STATUS_PENDING = "pending"
        private const val STATUS_RECOGNIZING = "recognizing"
        private const val STATUS_ATTENDED = "attended"
        private const val STATUS_ALREADY_ATTENDED = "already_attended"
        private const val STATUS_LATE = "late"
        private const val STATUS_ABSENT = "absent"
    }
}
