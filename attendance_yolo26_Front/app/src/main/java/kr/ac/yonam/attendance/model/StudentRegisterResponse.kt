package kr.ac.yonam.attendance.model

data class StudentRegisterResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val student: Student? = null,
    val matched: Boolean? = null,
    val status: String? = null
)
