package kr.ac.yonam.attendance.model

data class SubjectStudentResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val subject: Subject? = null,
    val items: List<Student>? = null
)
