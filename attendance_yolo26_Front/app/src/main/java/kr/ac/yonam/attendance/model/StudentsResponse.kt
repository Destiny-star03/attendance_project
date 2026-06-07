package kr.ac.yonam.attendance.model

data class StudentsResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val items: List<Student>? = null
)
