package kr.ac.yonam.attendance.data

import okhttp3.MultipartBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface AttendanceApi {
    @GET("health")
    suspend fun health(): HealthResponse

    @GET("sessions/active")
    suspend fun getActiveSession(): SessionResponse

    @Multipart
    @POST("attendance/recognize")
    suspend fun recognizeAttendance(
        @Part image: MultipartBody.Part
    ): RecognizeResponse

    @GET("attendance")
    suspend fun getAttendance(
        @Query("session_id") sessionId: Int? = null,
        @Query("date") date: String? = null
    ): AttendanceListResponse
}
