package kr.ac.yonam.attendance.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ActivityRegisterStudentBinding
import kr.ac.yonam.attendance.model.EnrollFrameResponse
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ServerConfig
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class RegisterStudentActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterStudentBinding
    private lateinit var repository: AttendanceRepository
    private lateinit var cameraExecutor: ExecutorService

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var enrollId: String? = null
    private var currentPoseIndex = 0
    private var isCapturing = false
    private val completedPoses = mutableSetOf<String>()

    private val poseOrder = listOf("front", "left", "right", "up", "down")

    private val serverUrl: String by lazy {
        ServerConfig.normalizeBaseUrl(intent.getStringExtra(EXTRA_SERVER_URL))
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCameraPreview()
        } else {
            showMessage("카메라 권한이 필요합니다.", isError = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterStudentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = AttendanceRepository(serverUrl)
        cameraExecutor = Executors.newSingleThreadExecutor()

        bindActions()
        showReadyState()
    }

    private fun bindActions() {
        binding.buttonBack.setOnClickListener {
            finish()
        }
        binding.buttonStartEnrollment.setOnClickListener {
            startEnrollment()
        }
        binding.buttonCapturePose.setOnClickListener {
            captureCurrentPose()
        }
        binding.buttonRetake.setOnClickListener {
            showCurrentPoseGuide()
            showMessage("현재 자세를 다시 촬영합니다.", isError = false)
        }
        binding.buttonCancelEnrollment.setOnClickListener {
            cancelEnrollmentAndFinish()
        }
    }

    private fun showReadyState() {
        binding.cameraSection.visibility = View.GONE
        binding.buttonCapturePose.isEnabled = false
        binding.buttonRetake.isEnabled = false
        binding.buttonCancelEnrollment.isEnabled = false
        binding.progressEnroll.progress = 0
        binding.textProgress.text = "진행률 0%"
        binding.textCompletedPoses.text = "완료된 자세: -"
        binding.textRemainingPoses.text = "남은 자세: " + poseOrder.joinToString(", ") { poseLabel(it) }
        binding.textPoseGuide.text = "학번, 이름, 학과를 입력한 뒤 등록을 시작하세요."
        binding.textMessage.visibility = View.GONE
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

        lifecycleScope.launch {
            val response = repository.startEnrollment(studentNo, name, department)
            setLoading(false)

            if (response.success == true && !response.enrollId.isNullOrBlank()) {
                enrollId = response.enrollId
                currentPoseIndex = 0
                completedPoses.clear()
                binding.cameraSection.visibility = View.VISIBLE
                binding.buttonCapturePose.isEnabled = true
                binding.buttonRetake.isEnabled = true
                binding.buttonCancelEnrollment.isEnabled = true
                showCurrentPoseGuide()
                updateProgress(
                    requiredPoses = response.requiredPoses.orEmpty().ifEmpty { poseOrder },
                    completed = completedPoses.toList(),
                    remaining = response.requiredPoses.orEmpty().ifEmpty { poseOrder },
                    serverProgress = 0.0
                )
                showMessage(response.message ?: "학생 얼굴 등록을 시작합니다.", isError = false)
                ensureCameraPermission()
                refreshEnrollmentStatus()
            } else {
                setFormEnabled(true)
                showMessage(response.message ?: "학생 얼굴 등록을 시작하지 못했습니다.", isError = true)
            }
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
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageCapture
                    )
                } catch (frontCameraError: Exception) {
                    bindBackCameraFallback(provider, preview)
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun bindBackCameraFallback(
        provider: ProcessCameraProvider,
        preview: Preview
    ) {
        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        } catch (error: Exception) {
            showMessage("카메라 프리뷰를 시작하지 못했습니다.", isError = true)
        }
    }

    private fun captureCurrentPose() {
        val currentEnrollId = enrollId
        val capture = imageCapture
        if (currentEnrollId.isNullOrBlank() || capture == null || isCapturing) return

        val pose = poseOrder.getOrNull(currentPoseIndex) ?: return
        val imageFile = File(cacheDir, "enroll_${pose}_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(imageFile).build()

        isCapturing = true
        setCaptureEnabled(false)
        showMessage("${poseGuide(pose)} 촬영 중입니다.", isError = false)

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val bytes = try {
                        imageFile.readBytes()
                    } finally {
                        imageFile.delete()
                    }
                    uploadPoseFrame(currentEnrollId, pose, bytes)
                }

                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        isCapturing = false
                        setCaptureEnabled(true)
                        showMessage("이미지를 촬영하지 못했습니다.", isError = true)
                    }
                }
            }
        )
    }

    private fun uploadPoseFrame(
        currentEnrollId: String,
        pose: String,
        jpegBytes: ByteArray
    ) {
        lifecycleScope.launch {
            val response = repository.uploadEnrollmentFrame(currentEnrollId, pose, jpegBytes)
            handleFrameResponse(response)
            isCapturing = false
            setCaptureEnabled(enrollId != null)
        }
    }

    private fun handleFrameResponse(response: EnrollFrameResponse) {
        if (response.success == true) {
            response.completedPoses.orEmpty().forEach { completedPoses.add(it) }
            if (!response.pose.isNullOrBlank()) {
                completedPoses.add(response.pose)
            }
            updateProgress(
                requiredPoses = poseOrder,
                completed = completedPoses.toList(),
                remaining = response.remainingPoses.orEmpty(),
                serverProgress = response.progress
            )
            moveToNextPose()

            if (completedPoses.containsAll(poseOrder)) {
                completeEnrollment()
            } else {
                showCurrentPoseGuide()
                showMessage(response.message ?: "촬영이 완료되었습니다.", isError = false)
            }
        } else {
            showMessage(frameFailureMessage(response), isError = true)
            updateProgress(
                requiredPoses = poseOrder,
                completed = completedPoses.toList(),
                remaining = response.remainingPoses.orEmpty(),
                serverProgress = response.progress
            )
        }
    }

    private fun moveToNextPose() {
        val nextIndex = poseOrder.indexOfFirst { it !in completedPoses }
        currentPoseIndex = if (nextIndex >= 0) nextIndex else poseOrder.lastIndex
    }

    private fun completeEnrollment() {
        val currentEnrollId = enrollId ?: return
        setCaptureEnabled(false)
        setLoading(true)

        lifecycleScope.launch {
            val response = repository.completeEnrollment(currentEnrollId)
            setLoading(false)
            if (response.success == true) {
                showMessage(response.message ?: "등록 완료", isError = false)
                binding.textPoseGuide.text = "등록 완료"
                binding.progressEnroll.progress = 100
                binding.textProgress.text = "진행률 100%"
                enrollId = null
                binding.buttonRetake.isEnabled = false
                binding.buttonCancelEnrollment.text = "닫기"
            } else {
                setCaptureEnabled(true)
                showMessage(response.message ?: "등록 완료 처리에 실패했습니다.", isError = true)
            }
        }
    }

    private fun refreshEnrollmentStatus() {
        val currentEnrollId = enrollId ?: return
        lifecycleScope.launch {
            val response = repository.getEnrollmentStatus(currentEnrollId)
            if (response.success == true) {
                completedPoses.clear()
                completedPoses.addAll(response.completedPoses.orEmpty())
                updateProgress(
                    requiredPoses = response.requiredPoses ?: poseOrder,
                    completed = response.completedPoses.orEmpty(),
                    remaining = response.remainingPoses.orEmpty(),
                    serverProgress = response.progress
                )
                moveToNextPose()
                showCurrentPoseGuide()
            }
        }
    }

    private fun cancelEnrollmentAndFinish() {
        val currentEnrollId = enrollId
        if (currentEnrollId.isNullOrBlank()) {
            finish()
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            repository.cancelEnrollment(currentEnrollId)
            setLoading(false)
            finish()
        }
    }

    private fun showCurrentPoseGuide() {
        val pose = poseOrder.getOrNull(currentPoseIndex) ?: "front"
        binding.textPoseGuide.text = poseGuide(pose)
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
            "front" -> "정면"
            "left" -> "왼쪽"
            "right" -> "오른쪽"
            "up" -> "위"
            "down" -> "아래"
            else -> pose
        }
    }

    private fun frameFailureMessage(response: EnrollFrameResponse): String {
        return when (response.status) {
            "no_face" -> "얼굴을 찾지 못했습니다"
            "multiple_faces" -> "한 명만 촬영해 주세요"
            else -> response.message ?: "촬영 이미지를 등록하지 못했습니다."
        }
    }

    private fun updateProgress(
        requiredPoses: List<String>,
        completed: List<String>,
        remaining: List<String>,
        serverProgress: Double?
    ) {
        val progress = serverProgress?.toInt()
            ?: if (requiredPoses.isNotEmpty()) {
                (completed.size * 100 / requiredPoses.size)
            } else {
                0
            }
        binding.progressEnroll.progress = progress.coerceIn(0, 100)
        binding.textProgress.text = "진행률 ${binding.progressEnroll.progress}%"
        binding.textCompletedPoses.text = if (completed.isEmpty()) {
            "완료된 자세: -"
        } else {
            "완료된 자세: " + completed.joinToString(", ") { poseLabel(it) }
        }
        binding.textRemainingPoses.text = if (remaining.isEmpty()) {
            "남은 자세: -"
        } else {
            "남은 자세: " + remaining.joinToString(", ") { poseLabel(it) }
        }
    }

    private fun setFormEnabled(enabled: Boolean) {
        binding.editStudentNo.isEnabled = enabled
        binding.editName.isEnabled = enabled
        binding.editDepartment.isEnabled = enabled
        binding.buttonStartEnrollment.isEnabled = enabled
    }

    private fun setCaptureEnabled(enabled: Boolean) {
        binding.buttonCapturePose.isEnabled = enabled
        binding.buttonRetake.isEnabled = enabled
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonStartEnrollment.isEnabled = !isLoading && enrollId == null
        binding.buttonCapturePose.isEnabled = !isLoading && enrollId != null && !isCapturing
        binding.buttonRetake.isEnabled = !isLoading && enrollId != null && !isCapturing
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

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SERVER_URL = "extra_server_url"
    }
}
