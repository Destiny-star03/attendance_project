package kr.ac.yonam.attendance.model

import com.google.gson.annotations.SerializedName

data class Student(
    @SerializedName("student_id")
    val studentId: Int? = null,
    @SerializedName("student_no")
    val studentNo: String? = null,
    val name: String? = null,
    val department: String? = null,
    val status: String? = null
)
