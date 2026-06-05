package kr.ac.yonam.attendance.model

import com.google.gson.annotations.SerializedName

data class AttendanceItem(
    @SerializedName("attendance_id")
    val attendanceId: Int? = null,
    @SerializedName("session_id")
    val sessionId: Int? = null,
    @SerializedName("subject_name")
    val subjectName: String? = null,
    @SerializedName("class_date")
    val classDate: String? = null,
    @SerializedName("start_time")
    val startTime: String? = null,
    @SerializedName("end_time")
    val endTime: String? = null,
    @SerializedName("student_id")
    val studentId: Int? = null,
    @SerializedName("student_no")
    val studentNo: String? = null,
    val name: String? = null,
    val department: String? = null,
    @SerializedName("attendance_date")
    val attendanceDate: String? = null,
    @SerializedName("attendance_time")
    val attendanceTime: String? = null,
    val status: String? = null,
    val confidence: Double? = null,
    val distance: Double? = null
)
