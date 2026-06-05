package kr.ac.yonam.attendance.model

data class AttendanceListResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val session: Session? = null,
    val date: String? = null,
    val items: List<AttendanceItem>? = null
)
