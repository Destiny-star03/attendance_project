package kr.ac.yonam.attendance.repository

import android.content.Context
import android.net.Uri
import kr.ac.yonam.attendance.model.ActiveSessionResponse
import kr.ac.yonam.attendance.model.AttendanceListResponse
import kr.ac.yonam.attendance.model.AttendanceResponse
import kr.ac.yonam.attendance.model.CommonResponse
import kr.ac.yonam.attendance.model.EnrollCompleteResponse
import kr.ac.yonam.attendance.model.EnrollFrameResponse
import kr.ac.yonam.attendance.model.EnrollStartResponse
import kr.ac.yonam.attendance.model.EnrollStatusResponse
import kr.ac.yonam.attendance.model.HealthResponse
import kr.ac.yonam.attendance.model.SessionListResponse
import kr.ac.yonam.attendance.model.StudentRegisterResponse
import kr.ac.yonam.attendance.network.ApiClient
import kr.ac.yonam.attendance.network.AttendanceApi
import kr.ac.yonam.attendance.util.ImageUtil
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class AttendanceRepository private constructor(
    private val api: AttendanceApi
) {
    constructor(context: Context) : this(ApiClient.create(context))
    constructor(serverUrl: String) : this(ApiClient.create(serverUrl))

    suspend fun checkHealth(): HealthResponse {
        return safeCall(
            call = { api.getHealth() },
            fallback = { HealthResponse(success = false, message = it) }
        )
    }

    suspend fun getActiveSession(): ActiveSessionResponse {
        return safeCall(
            call = { api.getActiveSession() },
            fallback = { ActiveSessionResponse(success = false, message = it) }
        )
    }

    suspend fun getSessions(): SessionListResponse {
        return safeCall(
            call = { api.getSessions() },
            fallback = { SessionListResponse(success = false, message = it) }
        )
    }

    suspend fun getAttendance(
        sessionId: Int? = null,
        date: String? = null
    ): AttendanceListResponse {
        // 학생 목록 API가 없을 때도 출석 화면은 이 응답의 items로 상태를 동기화한다.
        return safeCall(
            call = { api.getAttendance(sessionId = sessionId, date = date) },
            fallback = { AttendanceListResponse(success = false, message = it) }
        )
    }

    suspend fun recognizeAttendance(
        jpegBytes: ByteArray,
        sessionId: Int? = null
    ): AttendanceResponse {
        return safeCall(
            call = {
                api.recognizeAttendance(
                    image = ImageUtil.createJpegImagePart(jpegBytes),
                    sessionId = sessionId?.let { textPart(it.toString()) }
                )
            },
            fallback = {
                AttendanceResponse(
                    success = false,
                    status = "network_error",
                    message = it
                )
            }
        )
    }

    suspend fun registerStudent(
        context: Context,
        studentNo: String,
        name: String,
        department: String?,
        imageUri: Uri
    ): StudentRegisterResponse {
        return safeCall(
            call = {
                api.registerStudent(
                    studentNo = textPart(studentNo),
                    name = textPart(name),
                    department = textPart(department.orEmpty()),
                    image = ImageUtil.createImagePart(context, imageUri)
                )
            },
            fallback = { StudentRegisterResponse(success = false, message = it) }
        )
    }

    suspend fun startEnrollment(
        studentNo: String,
        name: String,
        department: String?
    ): EnrollStartResponse {
        return safeCall(
            call = {
                api.startEnrollment(
                    studentNo = textPart(studentNo),
                    name = textPart(name),
                    department = textPart(department.orEmpty())
                )
            },
            fallback = { EnrollStartResponse(success = false, message = it) }
        )
    }

    suspend fun uploadEnrollmentFrame(
        enrollId: String,
        pose: String,
        jpegBytes: ByteArray
    ): EnrollFrameResponse {
        return safeCall(
            call = {
                api.uploadEnrollmentFrame(
                    enrollId = textPart(enrollId),
                    pose = textPart(pose),
                    image = ImageUtil.createJpegImagePart(jpegBytes)
                )
            },
            fallback = {
                EnrollFrameResponse(
                    success = false,
                    status = "network_error",
                    message = it,
                    enrollId = enrollId,
                    pose = pose
                )
            }
        )
    }

    suspend fun completeEnrollment(enrollId: String): EnrollCompleteResponse {
        return safeCall(
            call = { api.completeEnrollment(textPart(enrollId)) },
            fallback = {
                EnrollCompleteResponse(
                    success = false,
                    message = it,
                    enrollId = enrollId
                )
            }
        )
    }

    suspend fun getEnrollmentStatus(enrollId: String): EnrollStatusResponse {
        return safeCall(
            call = { api.getEnrollmentStatus(enrollId) },
            fallback = {
                EnrollStatusResponse(
                    success = false,
                    message = it,
                    enrollId = enrollId
                )
            }
        )
    }

    suspend fun cancelEnrollment(enrollId: String): CommonResponse {
        return safeCall(
            call = { api.cancelEnrollment(enrollId) },
            fallback = {
                CommonResponse(
                    success = false,
                    message = it,
                    enrollId = enrollId
                )
            }
        )
    }

    private suspend fun <T> safeCall(
        call: suspend () -> T,
        fallback: (String) -> T
    ): T {
        return try {
            call()
        } catch (error: Exception) {
            fallback(error.message ?: "네트워크 오류가 발생했습니다.")
        }
    }

    private fun textPart(value: String): RequestBody {
        return value.toRequestBody("text/plain".toMediaType())
    }
}
