package kr.ac.yonam.attendance.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ActivityRegisterStudentBinding
import kr.ac.yonam.attendance.model.EnrollCompleteResponse
import kr.ac.yonam.attendance.model.EnrollFrameResponse
import kr.ac.yonam.attendance.model.EnrollStartResponse
import kr.ac.yonam.attendance.model.EnrollStatusResponse
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ImageUtil
import kr.ac.yonam.attendance.util.ServerConfig
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RegisterStudentActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterStudentBinding
    private lateinit var repository: AttendanceRepository
    private lateinit var cameraExecutor: ExecutorService

    private enum class EnrollUiState {
        INPUT_INFO,
        WAITING_FACE,
        CAPTURING,
        POSE_ACCEPTED,
        NEXT_POSE_WAIT,
        READY_TO_COMPLETE,
        COMPLETING,
        COMPLETED,
        ERROR,
        CANCELLED
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var enrollId: String? = null
    private var currentPose: String = DEFAULT_POSE
    private var requiredPoses: List<String> = POSE_ORDER
    private val completedPoses = mutableSetOf<String>()

    @Volatile
    private var isRequesting = false

    @Volatile
    private var autoCaptureEnabled = false

    @Volatile
    private var isScreenDestroyed = false

    private var uiState: EnrollUiState = EnrollUiState.INPUT_INFO
    private var nextCaptureAtMillis = 0L
    private var lastOverlayStatus: String? = null
    private var lastOverlayMessage: String? = null
    private var lastOverlayProgress: String? = null
    private var lastOverlayAtMillis = 0L

    private var frameUploadJob: Job? = null
    private var statusJob: Job? = null
    private var completeJob: Job? = null
    private var cancelJob: Job? = null
    private var finishJob: Job? = null
    private var captureOverlayJob: Job? = null
    private var nextPoseJob: Job? = null

    private val serverUrl: String by lazy {
        ServerConfig.normalizeBaseUrl(intent.getStringExtra(EXTRA_SERVER_URL))
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCameraPreview()
        } else {
            enterError("카메라 권한이 필요합니다.")
            binding.buttonRetake.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterStudentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = AttendanceRepository(serverUrl)
        cameraExecutor = Executors.newSingleThreadExecutor()

        bindActions()
        setupBackHandler()
        enterInputInfo()
    }

    override fun onStart() {
        super.onStart()
        if (enrollId != null && uiState != EnrollUiState.COMPLETED && uiState != EnrollUiState.COMPLETING) {
            ensureCameraPermission()
        }
    }

    override fun onStop() {
        stopAutoCapture()
        stopCamera()
        frameUploadJob?.cancel()
        statusJob?.cancel()
        captureOverlayJob?.cancel()
        nextPoseJob?.cancel()

        if (isFinishing && enrollId != null && uiState != EnrollUiState.COMPLETED) {
            cancelEnrollmentIfNeeded(finishAfterCancel = false)
        }

        super.onStop()
    }

    private fun bindActions() {
        binding.buttonBack.setOnClickListener {
            cancelEnrollmentAndFinish()
        }
        binding.buttonStartEnrollment.setOnClickListener {
            startEnrollment()
        }
        binding.buttonRetake.setOnClickListener {
            retryAutoCapture()
        }
        binding.buttonCancelEnrollment.setOnClickListener {
            cancelEnrollmentAndFinish()
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this) {
            cancelEnrollmentAndFinish()
        }
    }

    private fun enterInputInfo() {
        uiState = EnrollUiState.INPUT_INFO
        autoCaptureEnabled = false
        isRequesting = false
        enrollId = null
        currentPose = DEFAULT_POSE
        requiredPoses = POSE_ORDER
        completedPoses.clear()
        cancelPendingStateJobs()

        binding.cameraSection.visibility = View.GONE
        binding.buttonRetake.isEnabled = false
        binding.buttonCancelEnrollment.isEnabled = false
        binding.buttonCancelEnrollment.text = "등록 취소"
        binding.progressEnroll.progress = 0
        binding.textProgress.text = "진행률 0%"
        binding.textCompletedPoses.text = "완료된 자세: -"
        binding.textRemainingPoses.text = "남은 자세: " + POSE_ORDER.joinToString(", ") { poseLabel(it) }
        binding.textPoseGuide.text = "학번, 이름, 학과를 입력한 뒤 등록을 시작하세요."
        binding.textMessage.visibility = View.GONE
        setOverlayState("enroll_waiting", "정면을 바라보세요", "진행률 0%")
        setFormEnabled(true)
        setLoading(false)
    }

    private fun startEnrollment() {
        val studentNo = binding.editStudentNo.text?.toString()?.trim().orEmpty()
        val name = binding.editName.text?.toString()?.trim().orEmpty()
        val department = binding.editDepartment.text?.toString()?.trim().orEmpty()

        if (studentNo.isBlank()) {
            showMessage("학번을 입력해 주세요.", isError = true)
            return
        }
        if (name.isBlank()) {
            showMessage("이름을 입력해 주세요.", isError = true)
            return
        }

        setFormEnabled(false)
        setLoading(true)
        autoCaptureEnabled = false
        isRequesting = false

        lifecycleScope.launch {
            val response = repository.startEnrollment(studentNo, name, department)
            setLoading(false)

            if (response.success == true && !response.enrollId.isNullOrBlank()) {
                handleEnrollmentStarted(response)
            } else {
                setFormEnabled(true)
                enterError(response.message ?: "학생 얼굴 등록을 시작하지 못했습니다.")
            }
        }
    }

    private fun handleEnrollmentStarted(response: EnrollStartResponse) {
        enrollId = response.enrollId
        requiredPoses = response.requiredPoses.orEmpty().ifEmpty { POSE_ORDER }
        completedPoses.clear()
        completedPoses.addAll(response.completedPoses.orEmpty())
        currentPose = resolveNextPose(
            nextPose = response.nextPose,
            remainingPoses = response.remainingPoses,
            fallback = requiredPoses.firstOrNull() ?: DEFAULT_POSE
        )

        binding.cameraSection.visibility = View.VISIBLE
        binding.buttonRetake.isEnabled = true
        binding.buttonCancelEnrollment.isEnabled = true
        binding.buttonCancelEnrollment.text = "등록 취소"
        updateProgress(
            required = requiredPoses,
            completed = completedPoses.toList(),
            remaining = response.remainingPoses ?: remainingPoses(),
            serverProgress = response.progress
        )
        showMessage(response.message ?: "자동 촬영을 시작합니다.", isError = false)
        enterWaitingFace(resetDelay = true, helperMessage = "얼굴을 원 안에 맞춰주세요")
        ensureCameraPermission()
        refreshEnrollmentStatus()
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
                imageAnalysis = ImageAnalysis.Builder()
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
                    enableAutoCaptureIfNeeded()
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
        analysis: ImageAnalysis?
    ) {
        if (analysis == null) {
            enterError("카메라 프레임 분석기를 준비하지 못했습니다.")
            return
        }

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
            enableAutoCaptureIfNeeded()
        } catch (error: Exception) {
            enterError("카메라 프리뷰를 시작하지 못했습니다.")
        }
    }

    private fun enableAutoCaptureIfNeeded() {
        if (enrollId == null || uiState == EnrollUiState.COMPLETING || uiState == EnrollUiState.COMPLETED) {
            return
        }
        autoCaptureEnabled = true
        if (uiState != EnrollUiState.WAITING_FACE && uiState != EnrollUiState.NEXT_POSE_WAIT) {
            enterWaitingFace(resetDelay = true, helperMessage = "얼굴을 원 안에 맞춰주세요")
        }
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        val currentEnrollId = enrollId
        if (
            isScreenDestroyed ||
            !autoCaptureEnabled ||
            uiState != EnrollUiState.WAITING_FACE ||
            isRequesting ||
            currentEnrollId.isNullOrBlank() ||
            now < nextCaptureAtMillis
        ) {
            imageProxy.close()
            return
        }

        isRequesting = true
        uiState = EnrollUiState.CAPTURING
        scheduleCapturingOverlay()

        val pose = currentPose
        val jpegBytes = try {
            ImageUtil.imageProxyToJpegBytes(imageProxy)
        } catch (error: Exception) {
            null
        } finally {
            imageProxy.close()
        }

        if (jpegBytes == null) {
            isRequesting = false
            captureOverlayJob?.cancel()
            enterWaitingFace(retryDelayMillis = NO_FACE_RETRY_DELAY_MILLIS, helperMessage = "얼굴을 원 안에 맞춰주세요")
            return
        }

        frameUploadJob?.cancel()
        frameUploadJob = lifecycleScope.launch {
            try {
                val response = repository.uploadEnrollmentFrame(currentEnrollId, pose, jpegBytes)
                handleFrameResponse(response)
            } finally {
                isRequesting = false
            }
        }
    }

    private fun scheduleCapturingOverlay() {
        captureOverlayJob?.cancel()
        captureOverlayJob = lifecycleScope.launch {
            delay(CAPTURING_OVERLAY_DELAY_MILLIS)
            if (!isScreenDestroyed && uiState == EnrollUiState.CAPTURING && isRequesting) {
                setOverlayState("capturing", "얼굴 확인 중입니다", "잠시만 기다려주세요")
            }
        }
    }

    private fun handleFrameResponse(response: EnrollFrameResponse) {
        if (isScreenDestroyed || uiState == EnrollUiState.COMPLETED || uiState == EnrollUiState.CANCELLED) return

        captureOverlayJob?.cancel()
        updateProgressFromResponse(response)

        if (response.success == true) {
            when {
                response.readyToComplete == true || response.status == "ready_to_complete" -> {
                    enterReadyToComplete()
                }
                response.status == "accepted" || response.status == "in_progress" || response.status == null -> {
                    enterPoseAccepted(response)
                }
                else -> {
                    enterWaitingFace(retryDelayMillis = AUTO_CAPTURE_INTERVAL_MILLIS, helperMessage = poseGuide(currentPose))
                }
            }
        } else {
            when (response.status) {
                "no_face" -> enterWaitingFace(
                    retryDelayMillis = NO_FACE_RETRY_DELAY_MILLIS,
                    helperMessage = "얼굴을 원 안에 맞춰주세요"
                )
                "multiple_faces" -> enterWaitingFace(
                    retryDelayMillis = MULTIPLE_FACES_RETRY_DELAY_MILLIS,
                    helperMessage = "한 명만 화면에 들어오게 해주세요"
                )
                "invalid_pose" -> enterError("자세 정보 오류입니다.")
                "invalid_enroll_id" -> enterError("등록 세션 오류입니다.")
                "server_error" -> enterError("서버 오류가 발생했습니다.")
                else -> enterError(response.message ?: "촬영 프레임을 등록하지 못했습니다.")
            }
        }
    }

    private fun enterWaitingFace(
        resetDelay: Boolean = false,
        retryDelayMillis: Long = AUTO_CAPTURE_INTERVAL_MILLIS,
        helperMessage: String = "얼굴을 원 안에 맞춰주세요"
    ) {
        uiState = EnrollUiState.WAITING_FACE
        autoCaptureEnabled = enrollId != null
        if (resetDelay) {
            nextCaptureAtMillis = System.currentTimeMillis() + INITIAL_CAPTURE_DELAY_MILLIS
        } else {
            nextCaptureAtMillis = System.currentTimeMillis() + retryDelayMillis
        }

        binding.textPoseGuide.text = poseGuide(currentPose)
        binding.buttonRetake.isEnabled = true
        setOverlayState("waiting_face", poseGuide(currentPose), helperMessage)
    }

    private fun enterPoseAccepted(response: EnrollFrameResponse) {
        uiState = EnrollUiState.POSE_ACCEPTED
        autoCaptureEnabled = false

        val acceptedPose = response.pose ?: currentPose
        if (acceptedPose.isNotBlank()) {
            completedPoses.add(acceptedPose)
        }
        updateProgressFromResponse(response)

        binding.textPoseGuide.text = "${poseLabel(acceptedPose)} 등록 완료"
        setOverlayState("accepted", "${poseLabel(acceptedPose)} 등록 완료", enrollProgressText(), force = true)
        showMessage(response.message ?: "${poseLabel(acceptedPose)} 자세가 저장되었습니다.", isError = false)

        if (response.readyToComplete == true) {
            enterReadyToComplete()
            return
        }

        val nextPose = resolveNextPose(
            nextPose = response.nextPose,
            remainingPoses = response.remainingPoses,
            fallback = remainingPoses().firstOrNull() ?: currentPose
        )
        enterNextPoseWait(nextPose)
    }

    private fun enterNextPoseWait(nextPose: String) {
        uiState = EnrollUiState.NEXT_POSE_WAIT
        nextPoseJob?.cancel()
        nextPoseJob = lifecycleScope.launch {
            delay(POSE_ACCEPTED_HOLD_MILLIS)
            if (isScreenDestroyed || uiState != EnrollUiState.NEXT_POSE_WAIT) return@launch
            currentPose = nextPose
            enterWaitingFace(resetDelay = true, helperMessage = "얼굴을 원 안에 맞춰주세요")
        }
    }

    private fun enterReadyToComplete() {
        if (uiState == EnrollUiState.COMPLETING || uiState == EnrollUiState.COMPLETED) return

        uiState = EnrollUiState.READY_TO_COMPLETE
        autoCaptureEnabled = false
        setOverlayState("completed", "모든 자세 등록 완료", enrollProgressText(), force = true)
        completeEnrollment()
    }

    private fun completeEnrollment() {
        val currentEnrollId = enrollId ?: return
        if (uiState == EnrollUiState.COMPLETING || uiState == EnrollUiState.COMPLETED) return

        uiState = EnrollUiState.COMPLETING
        autoCaptureEnabled = false
        isRequesting = false
        setLoading(true)
        setOverlayState("capturing", "최종 등록 중입니다", "잠시만 기다려주세요", force = true)

        completeJob?.cancel()
        completeJob = lifecycleScope.launch {
            val response = repository.completeEnrollment(currentEnrollId)
            setLoading(false)
            if (response.success == true) {
                handleCompletedStatus(response)
            } else {
                enterError(response.message ?: "등록 완료 처리에 실패했습니다.")
                binding.buttonRetake.isEnabled = true
            }
        }
    }

    private fun handleCompletedStatus(response: EnrollCompleteResponse) {
        uiState = EnrollUiState.COMPLETED
        autoCaptureEnabled = false
        isRequesting = false
        enrollId = null
        cancelPendingStateJobs()
        stopCamera()

        binding.progressEnroll.progress = 100
        binding.textProgress.text = "진행률 100%"
        binding.textPoseGuide.text = "학생 등록 완료"
        binding.buttonCancelEnrollment.text = "닫기"
        binding.buttonRetake.isEnabled = false
        setOverlayState("completed", "학생 등록 완료", null, force = true)
        showMessage(response.message ?: "학생 등록 완료", isError = false)
        finishAfterComplete()
    }

    private fun refreshEnrollmentStatus() {
        val currentEnrollId = enrollId ?: return
        statusJob?.cancel()
        statusJob = lifecycleScope.launch {
            val response = repository.getEnrollmentStatus(currentEnrollId)
            if (response.success == true) {
                handleStatusResponse(response)
            } else if (uiState != EnrollUiState.WAITING_FACE) {
                showMessage(response.message ?: "등록 상태를 확인하지 못했습니다.", isError = true)
            }
        }
    }

    private fun handleStatusResponse(response: EnrollStatusResponse) {
        if (uiState == EnrollUiState.COMPLETING || uiState == EnrollUiState.COMPLETED) return

        completedPoses.clear()
        completedPoses.addAll(response.completedPoses.orEmpty())
        currentPose = resolveNextPose(
            nextPose = response.nextPose,
            remainingPoses = response.remainingPoses,
            fallback = currentPose
        )
        updateProgress(
            required = response.requiredPoses ?: requiredPoses,
            completed = response.completedPoses.orEmpty(),
            remaining = response.remainingPoses ?: remainingPoses(),
            serverProgress = response.progress
        )

        if (response.readyToComplete == true || response.status == "ready_to_complete") {
            enterReadyToComplete()
        } else {
            enterWaitingFace(resetDelay = false, retryDelayMillis = AUTO_CAPTURE_INTERVAL_MILLIS)
        }
    }

    private fun retryAutoCapture() {
        val currentEnrollId = enrollId
        if (currentEnrollId.isNullOrBlank()) {
            enterInputInfo()
            return
        }

        isRequesting = false
        enterWaitingFace(resetDelay = true, helperMessage = "얼굴을 원 안에 맞춰주세요")
        showMessage("자동 촬영을 다시 시도합니다.", isError = false)
    }

    private fun cancelEnrollmentAndFinish() {
        uiState = EnrollUiState.CANCELLED
        stopAutoCapture()
        cancelEnrollmentIfNeeded(finishAfterCancel = true)
    }

    private fun cancelEnrollmentIfNeeded(finishAfterCancel: Boolean) {
        val currentEnrollId = enrollId
        if (currentEnrollId.isNullOrBlank() || uiState == EnrollUiState.COMPLETED) {
            if (finishAfterCancel) finish()
            return
        }

        stopAutoCapture()
        enrollId = null
        setLoading(true)
        cancelJob?.cancel()
        cancelJob = lifecycleScope.launch {
            repository.cancelEnrollment(currentEnrollId)
            setLoading(false)
            if (finishAfterCancel) finish()
        }
    }

    private fun stopAutoCapture() {
        autoCaptureEnabled = false
        isRequesting = false
        captureOverlayJob?.cancel()
        nextPoseJob?.cancel()
    }

    private fun stopCamera() {
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        cameraProvider?.unbindAll()
        cameraProvider = null
    }

    private fun enterError(message: String) {
        uiState = EnrollUiState.ERROR
        autoCaptureEnabled = false
        isRequesting = false
        captureOverlayJob?.cancel()
        binding.buttonRetake.isEnabled = enrollId != null
        setOverlayState("error", message, null, force = true)
        showMessage(message, isError = true)
    }

    private fun updateProgressFromResponse(response: EnrollFrameResponse) {
        response.completedPoses.orEmpty().forEach { completedPoses.add(it) }
        if (!response.pose.isNullOrBlank() && response.status == "accepted") {
            completedPoses.add(response.pose)
        }
        updateProgress(
            required = response.requiredPoses ?: requiredPoses,
            completed = completedPoses.toList(),
            remaining = response.remainingPoses ?: remainingPoses(),
            serverProgress = response.progress
        )
    }

    private fun updateProgress(
        required: List<String>,
        completed: List<String>,
        remaining: List<String>,
        serverProgress: Int?
    ) {
        requiredPoses = required.ifEmpty { POSE_ORDER }
        completedPoses.clear()
        completedPoses.addAll(completed)

        val progress = serverProgress ?: if (requiredPoses.isNotEmpty()) {
            (completedPoses.size * 100 / requiredPoses.size)
        } else {
            0
        }
        binding.progressEnroll.progress = progress.coerceIn(0, 100)
        binding.textProgress.text = enrollProgressText()
        binding.textCompletedPoses.text = if (completedPoses.isEmpty()) {
            "완료된 자세: -"
        } else {
            "완료된 자세: " + completedPoses.joinToString(", ") { poseLabel(it) }
        }
        binding.textRemainingPoses.text = if (remaining.isEmpty()) {
            "남은 자세: -"
        } else {
            "남은 자세: " + remaining.joinToString(", ") { poseLabel(it) }
        }
    }

    private fun setOverlayState(
        status: String,
        message: String?,
        progressText: String?,
        force: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val sameState = status == lastOverlayStatus &&
            message == lastOverlayMessage &&
            progressText == lastOverlayProgress

        if (!force && sameState && now - lastOverlayAtMillis < SAME_OVERLAY_THROTTLE_MILLIS) {
            return
        }

        lastOverlayStatus = status
        lastOverlayMessage = message
        lastOverlayProgress = progressText
        lastOverlayAtMillis = now
        binding.faceGuideOverlay.setGuideState(status, message, progressText)
    }

    private fun cancelPendingStateJobs() {
        frameUploadJob?.cancel()
        statusJob?.cancel()
        completeJob?.cancel()
        captureOverlayJob?.cancel()
        nextPoseJob?.cancel()
    }

    private fun remainingPoses(): List<String> {
        return requiredPoses.filter { it !in completedPoses }
    }

    private fun enrollProgressText(): String {
        return "진행률 ${binding.progressEnroll.progress}%"
    }

    private fun poseGuide(pose: String): String {
        return when (pose) {
            "front" -> "정면을 바라보세요"
            "left" -> "얼굴을 왼쪽으로 돌려주세요"
            "right" -> "얼굴을 오른쪽으로 돌려주세요"
            "up" -> "얼굴을 위로 들어주세요"
            "down" -> "얼굴을 아래로 숙여주세요"
            else -> "정면을 바라보세요"
        }
    }

    private fun poseLabel(pose: String): String {
        return when (pose) {
            "front" -> "front"
            "left" -> "left"
            "right" -> "right"
            "up" -> "up"
            "down" -> "down"
            else -> pose
        }
    }

    private fun resolveNextPose(
        nextPose: String?,
        remainingPoses: List<String>?,
        fallback: String
    ): String {
        return nextPose?.takeIf { it.isNotBlank() }
            ?: remainingPoses.orEmpty().firstOrNull()
            ?: fallback
    }

    private fun setFormEnabled(enabled: Boolean) {
        binding.editStudentNo.isEnabled = enabled
        binding.editName.isEnabled = enabled
        binding.editDepartment.isEnabled = enabled
        binding.buttonStartEnrollment.isEnabled = enabled
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonStartEnrollment.isEnabled = !isLoading && enrollId == null
        binding.buttonRetake.isEnabled = !isLoading && enrollId != null && uiState != EnrollUiState.COMPLETING
        binding.buttonCancelEnrollment.isEnabled = !isLoading
    }

    private fun showMessage(message: String, isError: Boolean) {
        binding.textMessage.text = message
        binding.textMessage.visibility = View.VISIBLE
        binding.textMessage.setTextColor(
            ContextCompat.getColor(
                this,
                if (isError) R.color.yonam_red else R.color.yonam_green
            )
        )
    }

    private fun finishAfterComplete() {
        finishJob?.cancel()
        finishJob = lifecycleScope.launch {
            delay(COMPLETE_CLOSE_DELAY_MILLIS)
            if (!isScreenDestroyed) {
                finish()
            }
        }
    }

    override fun onDestroy() {
        isScreenDestroyed = true
        stopAutoCapture()
        cancelPendingStateJobs()
        cancelJob?.cancel()
        finishJob?.cancel()
        stopCamera()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SERVER_URL = "extra_server_url"

        private const val DEFAULT_POSE = "front"
        private const val INITIAL_CAPTURE_DELAY_MILLIS = 800L
        private const val AUTO_CAPTURE_INTERVAL_MILLIS = 1000L
        private const val NO_FACE_RETRY_DELAY_MILLIS = 1400L
        private const val MULTIPLE_FACES_RETRY_DELAY_MILLIS = 1600L
        private const val CAPTURING_OVERLAY_DELAY_MILLIS = 350L
        private const val POSE_ACCEPTED_HOLD_MILLIS = 1000L
        private const val COMPLETE_CLOSE_DELAY_MILLIS = 1200L
        private const val SAME_OVERLAY_THROTTLE_MILLIS = 1000L

        private val POSE_ORDER = listOf("front", "left", "right", "up", "down")
    }
}
