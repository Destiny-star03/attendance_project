package kr.ac.yonam.attendance.model

data class CurrentSessionResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val session: Session? = null
)
