package kr.ac.yonam.attendance.repository

import android.content.Context
import android.net.Uri
import kr.ac.yonam.attendance.model.ActiveSessionResponse
import kr.ac.yonam.attendance.model.AttendanceListResponse
import kr.ac.yonam.attendance.model.AttendanceResponse
import kr.ac.yonam.attendance.model.ClassroomListResponse
import kr.ac.yonam.attendance.model.ClassroomRequest
import kr.ac.yonam.attendance.model.ClassroomResponse
import kr.ac.yonam.attendance.model.CommonResponse
import kr.ac.yonam.attendance.model.CreateClassroomRequest
import kr.ac.yonam.attendance.model.CreateSessionRequest
import kr.ac.yonam.attendance.model.CreateSubjectRequest
import kr.ac.yonam.attendance.model.CurrentSessionResponse
import kr.ac.yonam.attendance.model.EnrollCompleteResponse
import kr.ac.yonam.attendance.model.EnrollFrameResponse
import kr.ac.yonam.attendance.model.EnrollStartResponse
import kr.ac.yonam.attendance.model.EnrollStatusResponse
import kr.ac.yonam.attendance.model.HealthResponse
import kr.ac.yonam.attendance.model.SessionListResponse
import kr.ac.yonam.attendance.model.StudentRegisterResponse
import kr.ac.yonam.attendance.model.StudentStatsResponse
import kr.ac.yonam.attendance.model.StudentsResponse
import kr.ac.yonam.attendance.model.SubjectListResponse
import kr.ac.yonam.attendance.model.SubjectResponse
import kr.ac.yonam.attendance.model.SubjectStudentResponse
import kr.ac.yonam.attendance.model.UpdateAttendanceStatusRequest
import kr.ac.yonam.attendance.network.ApiClient
import kr.ac.yonam.attendance.network.AttendanceApi
import kr.ac.yonam.attendance.util.ImageUtil
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Response
import java.io.IOException

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

    suspend fun getCurrentSession(classroomId: Int): CurrentSessionResponse {
        return try {
            val response = api.getCurrentSession(classroomId)
            if (response.isSuccessful) {
                response.body() ?: CurrentSessionResponse(
                    success = false,
                    status = "empty_body",
                    message = "현재 수업 조회 응답이 비어 있습니다."
                )
            } else {
                parseCurrentSessionError(response.errorBody()?.string())
                    ?: CurrentSessionResponse(
                        success = false,
                        status = "server_error",
                        message = "현재 수업 조회 서버 오류가 발생했습니다. (HTTP ${response.code()})"
                    )
            }
        } catch (error: IOException) {
            CurrentSessionResponse(
                success = false,
                status = "network_error",
                message = error.message ?: "서버 연결 오류가 발생했습니다."
            )
        } catch (error: Exception) {
            CurrentSessionResponse(
                success = false,
                status = "client_error",
                message = error.message ?: "현재 수업 조회 응답을 처리하지 못했습니다."
            )
        }
    }

    suspend fun getSessions(): SessionListResponse {
        return safeCall(
            call = { api.getSessions() },
            fallback = { SessionListResponse(success = false, message = it) }
        )
    }

    suspend fun deleteSession(sessionId: Int): CommonResponse {
        return safeCall(
            call = { api.deleteSession(sessionId) },
            fallback = {
                CommonResponse(
                    success = false,
                    status = "server_error",
                    message = it,
                    deleted = false
                )
            }
        )
    }

    suspend fun getSessionAttendanceStudents(sessionId: Int): AttendanceListResponse {
        return safeCall(
            call = { api.getSessionAttendanceStudents(sessionId) },
            fallback = { AttendanceListResponse(success = false, message = it, items = emptyList()) }
        )
    }

    suspend fun updateSessionAttendanceStatus(
        sessionId: Int,
        studentId: Int,
        status: String
    ): CommonResponse {
        return safeCall(
            call = {
                api.updateSessionAttendanceStatus(
                    sessionId = sessionId,
                    studentId = studentId,
                    request = UpdateAttendanceStatusRequest(status)
                )
            },
            fallback = {
                CommonResponse(
                    success = false,
                    status = "server_error",
                    message = it
                )
            }
        )
    }

    suspend fun getAttendance(
        sessionId: Int? = null,
        date: String? = null
    ): AttendanceListResponse {
        // 학생 목록 API가 없어도 출석 화면은 이 응답의 items로 상태를 동기화한다.
        return safeCall(
            call = { api.getAttendance(sessionId = sessionId, date = date) },
            fallback = { AttendanceListResponse(success = false, message = it) }
        )
    }

    suspend fun getStudents(): StudentsResponse {
        return try {
            val response = api.getStudents()
            if (response.isSuccessful) {
                response.body() ?: StudentsResponse(
                    success = false,
                    message = "학생 목록 응답이 비어 있습니다."
                )
            } else {
                val serverMessage = parseErrorMessage(response.errorBody()?.string())
                StudentsResponse(
                    success = false,
                    message = serverMessage
                        ?: "학생 목록을 불러오지 못했습니다. (HTTP ${response.code()})"
                )
            }
        } catch (error: IOException) {
            StudentsResponse(
                success = false,
                message = "학생 목록 조회 중 네트워크 오류가 발생했습니다: ${error.message ?: "알 수 없는 오류"}"
            )
        } catch (error: Exception) {
            StudentsResponse(
                success = false,
                message = "학생 목록 응답 처리 오류가 발생했습니다: ${error.message ?: "알 수 없는 오류"}"
            )
        }
    }

    suspend fun getStudentStats(studentId: Int): StudentStatsResponse {
        return try {
            val response = api.getStudentStats(studentId)
            if (response.isSuccessful) {
                response.body() ?: StudentStatsResponse(
                    success = false,
                    message = "학생 통계 응답이 비어 있습니다."
                )
            } else {
                StudentStatsResponse(
                    success = false,
                    message = "학생 통계를 불러오지 못했습니다. (HTTP ${response.code()})"
                )
            }
        } catch (error: Exception) {
            StudentStatsResponse(
                success = false,
                message = "학생 통계 조회 중 네트워크 오류가 발생했습니다: ${error.message ?: "알 수 없는 오류"}"
            )
        }
    }

    suspend fun deleteStudent(studentId: Int): CommonResponse {
        return safeCall(
            call = { api.deleteStudent(studentId) },
            fallback = {
                CommonResponse(
                    success = false,
                    status = "server_error",
                    message = it,
                    deleted = false
                )
            }
        )
    }

    suspend fun getSubjects(): SubjectListResponse {
        return safeCall(
            call = { api.getSubjects() },
            fallback = { SubjectListResponse(success = false, message = it, items = emptyList()) }
        )
    }

    suspend fun getClassrooms(activeOnly: Boolean? = null): ClassroomListResponse {
        return safeCall(
            call = {
                classroomListResponse(
                    response = api.getClassrooms(activeOnly = activeOnly),
                    emptyMessage = "강의실 목록 응답이 비어 있습니다.",
                    httpMessage = { "강의실 목록을 불러오지 못했습니다. (HTTP $it)" }
                )
            },
            fallback = { ClassroomListResponse(success = false, message = it, items = emptyList()) }
        )
    }

    suspend fun createClassroom(request: CreateClassroomRequest): ClassroomResponse {
        return safeCall(
            call = {
                classroomResponse(
                    response = api.createClassroom(request),
                    emptyMessage = "강의실 저장 응답이 비어 있습니다.",
                    httpMessage = { "강의실을 저장하지 못했습니다. (HTTP $it)" }
                )
            },
            fallback = { ClassroomResponse(success = false, message = it) }
        )
    }

    suspend fun createClassroom(request: ClassroomRequest): ClassroomResponse {
        return createClassroom(request.toCreateClassroomRequest())
    }

    suspend fun createClassroom(
        classroomName: String,
        buildingName: String? = null,
        floor: String? = null,
        description: String? = null
    ): ClassroomResponse {
        return createClassroom(
            CreateClassroomRequest(
                classroomName = classroomName,
                buildingName = buildingName,
                floor = floor,
                description = description
            )
        )
    }

    suspend fun getClassroom(classroomId: Int): ClassroomResponse {
        return safeCall(
            call = {
                classroomResponse(
                    response = api.getClassroom(classroomId),
                    emptyMessage = "강의실 상세 응답이 비어 있습니다.",
                    httpMessage = { "강의실 정보를 불러오지 못했습니다. (HTTP $it)" }
                )
            },
            fallback = { ClassroomResponse(success = false, message = it) }
        )
    }

    suspend fun updateClassroom(
        classroomId: Int,
        request: CreateClassroomRequest
    ): ClassroomResponse {
        return safeCall(
            call = {
                classroomResponse(
                    response = api.updateClassroom(classroomId, request),
                    emptyMessage = "강의실 수정 응답이 비어 있습니다.",
                    httpMessage = { "강의실을 수정하지 못했습니다. (HTTP $it)" }
                )
            },
            fallback = { ClassroomResponse(success = false, message = it) }
        )
    }

    suspend fun updateClassroom(
        classroomId: Int,
        request: ClassroomRequest
    ): ClassroomResponse {
        return updateClassroom(classroomId, request.toCreateClassroomRequest())
    }

    suspend fun updateClassroom(
        classroomId: Int,
        classroomName: String,
        buildingName: String? = null,
        floor: String? = null,
        description: String? = null
    ): ClassroomResponse {
        return updateClassroom(
            classroomId = classroomId,
            request = CreateClassroomRequest(
                classroomName = classroomName,
                buildingName = buildingName,
                floor = floor,
                description = description
            )
        )
    }

    suspend fun deleteClassroom(classroomId: Int): CommonResponse {
        return safeCall(
            call = {
                val response = api.deleteClassroom(classroomId)
                if (response.isSuccessful) {
                    val body = response.body()
                    CommonResponse(
                        success = body?.success ?: false,
                        status = body?.status,
                        message = body?.message ?: "강의실 비활성화 응답이 비어 있습니다.",
                        deleted = body?.success == true
                    )
                } else {
                    CommonResponse(
                        success = false,
                        status = "http_error",
                        message = "강의실을 비활성화하지 못했습니다. (HTTP ${response.code()})",
                        deleted = false
                    )
                }
            },
            fallback = {
                CommonResponse(
                    success = false,
                    status = "server_error",
                    message = it,
                    deleted = false
                )
            }
        )
    }

    suspend fun createSubject(request: CreateSubjectRequest): SubjectResponse {
        return safeCall(
            call = { api.createSubject(request) },
            fallback = { SubjectResponse(success = false, message = it) }
        )
    }

    suspend fun createSubject(
        subjectName: String,
        professorName: String? = null,
        classroom: String? = null,
        classroomId: Int? = null,
        dayOfWeek: String? = null,
        startTime: String? = null,
        endTime: String? = null
    ): SubjectResponse {
        return createSubject(
            CreateSubjectRequest(
                subjectName = subjectName,
                professorName = professorName,
                classroom = classroom,
                classroomId = classroomId,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime
            )
        )
    }

    suspend fun getSubject(subjectId: Int): SubjectResponse {
        return safeCall(
            call = { api.getSubject(subjectId) },
            fallback = { SubjectResponse(success = false, message = it) }
        )
    }

    suspend fun updateSubject(
        subjectId: Int,
        request: CreateSubjectRequest
    ): SubjectResponse {
        return safeCall(
            call = { api.updateSubject(subjectId, request) },
            fallback = { SubjectResponse(success = false, message = it) }
        )
    }

    suspend fun updateSubject(
        subjectId: Int,
        subjectName: String? = null,
        professorName: String? = null,
        classroom: String? = null,
        classroomId: Int? = null,
        dayOfWeek: String? = null,
        startTime: String? = null,
        endTime: String? = null
    ): SubjectResponse {
        return updateSubject(
            subjectId = subjectId,
            request = CreateSubjectRequest(
                subjectName = subjectName,
                professorName = professorName,
                classroom = classroom,
                classroomId = classroomId,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime
            )
        )
    }

    suspend fun deleteSubject(subjectId: Int): CommonResponse {
        return safeCall(
            call = { api.deleteSubject(subjectId) },
            fallback = {
                CommonResponse(
                    success = false,
                    status = "server_error",
                    message = it,
                    deleted = false
                )
            }
        )
    }

    suspend fun getSubjectStudents(subjectId: Int): SubjectStudentResponse {
        return safeCall(
            call = { api.getSubjectStudents(subjectId) },
            fallback = { SubjectStudentResponse(success = false, message = it, items = emptyList()) }
        )
    }

    suspend fun getSubjectSessions(subjectId: Int): SessionListResponse {
        return safeCall(
            call = { api.getSubjectSessions(subjectId) },
            fallback = { SessionListResponse(success = false, message = it, items = emptyList()) }
        )
    }

    suspend fun addStudentToSubject(subjectId: Int, studentId: Int): CommonResponse {
        return safeCall(
            call = { api.addStudentToSubject(subjectId, studentId) },
            fallback = {
                CommonResponse(
                    success = false,
                    status = "server_error",
                    message = it
                )
            }
        )
    }

    suspend fun removeStudentFromSubject(subjectId: Int, studentId: Int): CommonResponse {
        return safeCall(
            call = { api.removeStudentFromSubject(subjectId, studentId) },
            fallback = {
                CommonResponse(
                    success = false,
                    status = "server_error",
                    message = it,
                    deleted = false
                )
            }
        )
    }

    suspend fun createSubjectSession(
        subjectId: Int,
        request: CreateSessionRequest
    ): ActiveSessionResponse {
        return safeCall(
            call = { api.createSubjectSession(subjectId, request) },
            fallback = { ActiveSessionResponse(success = false, message = it) }
        )
    }

    suspend fun createSubjectSession(
        subjectId: Int,
        classDate: String,
        startTime: String? = null,
        endTime: String? = null,
        classroomId: Int? = null,
        dayOfWeek: String? = null,
        activate: Boolean? = null
    ): ActiveSessionResponse {
        return createSubjectSession(
            subjectId = subjectId,
            request = CreateSessionRequest(
                classDate = classDate,
                startTime = startTime,
                endTime = endTime,
                classroomId = classroomId,
                dayOfWeek = dayOfWeek
            )
        )
    }

    suspend fun recognizeAttendance(
        jpegBytes: ByteArray,
        sessionId: Int? = null
    ): AttendanceResponse {
        if (sessionId == null) {
            return AttendanceResponse(
                success = false,
                status = "no_current_session",
                message = "현재 진행 중인 수업이 없습니다."
            )
        }

        return try {
            val response = api.recognizeAttendance(
                image = ImageUtil.createJpegImagePart(jpegBytes),
                sessionId = textPart(sessionId.toString())
            )

            if (response.isSuccessful) {
                response.body() ?: AttendanceResponse(
                    success = false,
                    status = "empty_body",
                    message = "출석 인식 응답이 비어 있습니다."
                )
            } else {
                parseAttendanceError(response.errorBody()?.string())
                    ?: AttendanceResponse(
                        success = false,
                        status = "server_error",
                        message = "출석 인식 서버 오류가 발생했습니다. (HTTP ${response.code()})"
                    )
            }
        } catch (error: IOException) {
            AttendanceResponse(
                success = false,
                status = "network_error",
                message = error.message ?: "서버 연결 오류가 발생했습니다."
            )
        } catch (error: Exception) {
            AttendanceResponse(
                success = false,
                status = "client_error",
                message = error.message ?: "출석 인식 응답을 처리하지 못했습니다."
            )
        }
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
            fallback = { EnrollStartResponse(success = false, status = "server_error", message = it) }
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
                    status = "server_error",
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
                    status = "server_error",
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
                    status = "server_error",
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
                    status = "server_error",
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

    private fun parseAttendanceError(errorBody: String?): AttendanceResponse? {
        if (errorBody.isNullOrBlank()) return null

        return runCatching {
            val json = JSONObject(errorBody)
            AttendanceResponse(
                success = json.optBoolean("success", false),
                status = json.optString("status").takeIf { it.isNotBlank() } ?: "server_error",
                message = json.optString("message").takeIf { it.isNotBlank() }
                    ?: "출석 인식 서버 오류가 발생했습니다."
            )
        }.getOrNull()
    }

    private fun parseCurrentSessionError(errorBody: String?): CurrentSessionResponse? {
        if (errorBody.isNullOrBlank()) return null

        return runCatching {
            val json = JSONObject(errorBody)
            CurrentSessionResponse(
                success = json.optBoolean("success", false),
                status = json.optString("status").takeIf { it.isNotBlank() } ?: "server_error",
                message = json.optString("message").takeIf { it.isNotBlank() }
                    ?: "현재 수업 조회 서버 오류가 발생했습니다."
            )
        }.getOrNull()
    }

    private fun parseErrorMessage(errorBody: String?): String? {
        if (errorBody.isNullOrBlank()) return null

        return runCatching {
            JSONObject(errorBody)
                .optString("message")
                .takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun classroomListResponse(
        response: Response<ClassroomListResponse>,
        emptyMessage: String,
        httpMessage: (Int) -> String
    ): ClassroomListResponse {
        return if (response.isSuccessful) {
            response.body() ?: ClassroomListResponse(
                success = false,
                status = "empty_body",
                message = emptyMessage,
                items = emptyList()
            )
        } else {
            ClassroomListResponse(
                success = false,
                status = "http_error",
                message = httpMessage(response.code()),
                items = emptyList()
            )
        }
    }

    private fun classroomResponse(
        response: Response<ClassroomResponse>,
        emptyMessage: String,
        httpMessage: (Int) -> String
    ): ClassroomResponse {
        return if (response.isSuccessful) {
            response.body() ?: ClassroomResponse(
                success = false,
                status = "empty_body",
                message = emptyMessage
            )
        } else {
            ClassroomResponse(
                success = false,
                status = "http_error",
                message = httpMessage(response.code())
            )
        }
    }

    private fun ClassroomRequest.toCreateClassroomRequest(): CreateClassroomRequest {
        return CreateClassroomRequest(
            classroomName = classroomName.orEmpty(),
            buildingName = buildingName,
            floor = floor?.toString(),
            description = description
        )
    }
}
