package kr.ac.yonam.attendance.model

data class ClassroomResponse(
    val success: Boolean? = null,
    val status: String? = null,
    val message: String? = null,
    val classroom: Classroom? = null
)
