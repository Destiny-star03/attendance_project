package kr.ac.yonam.attendance.model

import com.google.gson.annotations.SerializedName

data class StudentStats(
    val student: Student? = null,
    @SerializedName("attendance_count")
    val attendanceCount: Int? = null,
    @SerializedName("last_attendance_date")
    val lastAttendanceDate: String? = null,
    @SerializedName("last_attendance_time")
    val lastAttendanceTime: String? = null
)
