package kr.ac.yonam.attendance.model

data class StudentStatsResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val student: Student? = null,
    val stats: StudentStats? = null
)
