package kr.ac.yonam.attendance.data

import com.google.gson.annotations.SerializedName

data class HealthResponse(
    val status: String?
)

data class SessionDto(
    @SerializedName("session_id")
    val sessionId: Int?,
    @SerializedName("subject_name")
    val subjectName: String?,
    @SerializedName("class_date")
    val classDate: String?,
    @SerializedName("start_time")
    val startTime: String?,
    @SerializedName("end_time")
    val endTime: String?,
    @SerializedName("is_active")
    val isActive: Boolean?
)

data class SessionResponse(
    val success: Boolean,
    val message: String?,
    val session: SessionDto?
)

data class StudentDto(
    @SerializedName("student_id")
    val studentId: Int?,
    @SerializedName("student_no")
    val studentNo: String?,
    val name: String?,
    val department: String?
)

data class RecognitionDto(
    val distance: Double?,
    val similarity: Double?
)

data class AttendanceMarkDto(
    val marked: Boolean?,
    val message: String?
)

data class RecognizeResponse(
    val success: Boolean,
    val matched: Boolean?,
    val status: String?,
    val message: String?,
    @SerializedName("hold_seconds")
    val holdSeconds: Double?,
    @SerializedName("elapsed_seconds")
    val elapsedSeconds: Double?,
    @SerializedName("remaining_seconds")
    val remainingSeconds: Double?,
    val session: SessionDto?,
    val student: StudentDto?,
    val recognition: RecognitionDto?,
    val attendance: AttendanceMarkDto?
)

data class AttendanceRecordDto(
    @SerializedName("attendance_id")
    val attendanceId: Int?,
    @SerializedName("session_id")
    val sessionId: Int?,
    @SerializedName("subject_name")
    val subjectName: String?,
    @SerializedName("student_id")
    val studentId: Int?,
    @SerializedName("student_no")
    val studentNo: String?,
    val name: String?,
    val department: String?,
    @SerializedName("attendance_date")
    val attendanceDate: String?,
    @SerializedName("attendance_time")
    val attendanceTime: String?,
    val status: String?,
    val distance: Double?
)

data class AttendanceListResponse(
    val success: Boolean,
    val message: String?,
    val session: SessionDto?,
    val date: String?,
    val items: List<AttendanceRecordDto>?
)
