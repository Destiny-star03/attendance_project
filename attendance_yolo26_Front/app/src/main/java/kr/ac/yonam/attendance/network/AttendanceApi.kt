package kr.ac.yonam.attendance.network

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
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface AttendanceApi {
    @GET("health")
    suspend fun getHealth(): HealthResponse

    @GET("sessions/active")
    suspend fun getActiveSession(): ActiveSessionResponse

    @GET("sessions")
    suspend fun getSessions(): SessionListResponse

    @GET("attendance")
    suspend fun getAttendance(
        @Query("session_id") sessionId: Int? = null,
        @Query("date") date: String? = null
    ): AttendanceListResponse

    @Multipart
    @POST("attendance/recognize")
    suspend fun recognizeAttendance(
        @Part image: MultipartBody.Part,
        @Part("session_id") sessionId: RequestBody? = null
    ): AttendanceResponse

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
}
