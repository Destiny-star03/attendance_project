package kr.ac.yonam.attendance.model

import com.google.gson.annotations.SerializedName

data class StudentStats(
    @SerializedName("attendance_count")
    val attendanceCount: Int? = null,
    @SerializedName("late_count")
    val lateCount: Int? = null,
    @SerializedName("absence_count")
    val absenceCount: Int? = null
)
