package kr.ac.yonam.attendance.network

import kr.ac.yonam.attendance.model.ActiveSessionResponse
import kr.ac.yonam.attendance.model.AttendanceListResponse
import kr.ac.yonam.attendance.model.AttendanceResponse
import kr.ac.yonam.attendance.model.ClassroomListResponse
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
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.Query

interface AttendanceApi {
    @GET("health")
    suspend fun getHealth(): HealthResponse

    @GET("sessions/active")
    suspend fun getActiveSession(): ActiveSessionResponse

    @GET("sessions/current")
    suspend fun getCurrentSession(
        @Query("classroom_id") classroomId: Int
    ): Response<CurrentSessionResponse>

    @GET("sessions")
    suspend fun getSessions(): SessionListResponse

    @DELETE("sessions/{session_id}")
    suspend fun deleteSession(
        @Path("session_id") sessionId: Int
    ): CommonResponse

    @GET("sessions/{session_id}/attendance-students")
    suspend fun getSessionAttendanceStudents(
        @Path("session_id") sessionId: Int
    ): AttendanceListResponse

    @PUT("sessions/{session_id}/attendance-students/{student_id}")
    suspend fun updateSessionAttendanceStatus(
        @Path("session_id") sessionId: Int,
        @Path("student_id") studentId: Int,
        @Body request: UpdateAttendanceStatusRequest
    ): CommonResponse

    @GET("attendance")
    suspend fun getAttendance(
        @Query("session_id") sessionId: Int? = null,
        @Query("date") date: String? = null
    ): AttendanceListResponse

    @GET("students")
    suspend fun getStudents(): Response<StudentsResponse>

    @GET("students/{student_id}/stats")
    suspend fun getStudentStats(
        @Path("student_id") studentId: Int
    ): Response<StudentStatsResponse>

    @DELETE("students/{student_id}")
    suspend fun deleteStudent(
        @Path("student_id") studentId: Int
    ): CommonResponse

    @Multipart
    @POST("attendance/recognize")
    suspend fun recognizeAttendance(
        @Part image: MultipartBody.Part,
        @Part("session_id") sessionId: RequestBody? = null
    ): Response<AttendanceResponse>

    @Multipart
    @POST("students")
    suspend fun registerStudent(
        @Part("student_no") studentNo: RequestBody,
        @Part("name") name: RequestBody,
        @Part("department") department: RequestBody,
        @Part image: MultipartBody.Part
    ): StudentRegisterResponse

    @Multipart
    @POST("students/enroll/start")
    suspend fun startEnrollment(
        @Part("student_no") studentNo: RequestBody,
        @Part("name") name: RequestBody,
        @Part("department") department: RequestBody
    ): EnrollStartResponse

    @Multipart
    @POST("students/enroll/frame")
    suspend fun uploadEnrollmentFrame(
        @Part("enroll_id") enrollId: RequestBody,
        @Part("pose") pose: RequestBody,
        @Part image: MultipartBody.Part
    ): EnrollFrameResponse

    @Multipart
    @POST("students/enroll/complete")
    suspend fun completeEnrollment(
        @Part("enroll_id") enrollId: RequestBody
    ): EnrollCompleteResponse

    @GET("students/enroll/{enroll_id}/status")
    suspend fun getEnrollmentStatus(
        @Path("enroll_id") enrollId: String
    ): EnrollStatusResponse

    @DELETE("students/enroll/{enroll_id}")
    suspend fun cancelEnrollment(
        @Path("enroll_id") enrollId: String
    ): CommonResponse

    @GET("subjects")
    suspend fun getSubjects(): SubjectListResponse

    @GET("classrooms")
    suspend fun getClassrooms(
        @Query("active_only") activeOnly: Boolean? = null
    ): Response<ClassroomListResponse>

    @POST("classrooms")
    suspend fun createClassroom(
        @Body request: CreateClassroomRequest
    ): Response<ClassroomResponse>

    @GET("classrooms/{classroom_id}")
    suspend fun getClassroom(
        @Path("classroom_id") classroomId: Int
    ): Response<ClassroomResponse>

    @PUT("classrooms/{classroom_id}")
    suspend fun updateClassroom(
        @Path("classroom_id") classroomId: Int,
        @Body request: CreateClassroomRequest
    ): Response<ClassroomResponse>

    @DELETE("classrooms/{classroom_id}")
    suspend fun deleteClassroom(
        @Path("classroom_id") classroomId: Int
    ): Response<ClassroomResponse>

    @POST("subjects")
    suspend fun createSubject(
        @Body request: CreateSubjectRequest
    ): SubjectResponse

    @GET("subjects/{subject_id}")
    suspend fun getSubject(
        @Path("subject_id") subjectId: Int
    ): SubjectResponse

    @PUT("subjects/{subject_id}")
    suspend fun updateSubject(
        @Path("subject_id") subjectId: Int,
        @Body request: CreateSubjectRequest
    ): SubjectResponse

    @DELETE("subjects/{subject_id}")
    suspend fun deleteSubject(
        @Path("subject_id") subjectId: Int
    ): CommonResponse

    @GET("subjects/{subject_id}/students")
    suspend fun getSubjectStudents(
        @Path("subject_id") subjectId: Int
    ): SubjectStudentResponse

    @GET("subjects/{subject_id}/sessions")
    suspend fun getSubjectSessions(
        @Path("subject_id") subjectId: Int
    ): SessionListResponse

    @POST("subjects/{subject_id}/students/{student_id}")
    suspend fun addStudentToSubject(
        @Path("subject_id") subjectId: Int,
        @Path("student_id") studentId: Int
    ): CommonResponse

    @DELETE("subjects/{subject_id}/students/{student_id}")
    suspend fun removeStudentFromSubject(
        @Path("subject_id") subjectId: Int,
        @Path("student_id") studentId: Int
    ): CommonResponse

    @POST("subjects/{subject_id}/sessions")
    suspend fun createSubjectSession(
        @Path("subject_id") subjectId: Int,
        @Body request: CreateSessionRequest
    ): ActiveSessionResponse
}
