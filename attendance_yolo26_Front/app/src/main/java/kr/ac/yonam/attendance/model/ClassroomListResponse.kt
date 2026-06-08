package kr.ac.yonam.attendance.model

data class ClassroomListResponse(
    val success: Boolean? = null,
    val status: String? = null,
    val message: String? = null,
    val items: List<Classroom>? = null
)
