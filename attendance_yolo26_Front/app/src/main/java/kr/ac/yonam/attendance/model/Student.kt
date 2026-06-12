package kr.ac.yonam.attendance.model

import com.google.gson.annotations.SerializedName

data class Student(
    @SerializedName("student_id")
    val studentId: Int? = null,
    @SerializedName("student_no")
    val studentNo: String? = null,
    val name: String? = null,
    val department: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("is_active")
    val isActive: Boolean? = null,
    val attendanceStatus: String? = null,
    val status: String? = null
)
