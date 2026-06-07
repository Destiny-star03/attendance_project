package kr.ac.yonam.attendance.model

data class SubjectListResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val items: List<Subject>? = null
)
