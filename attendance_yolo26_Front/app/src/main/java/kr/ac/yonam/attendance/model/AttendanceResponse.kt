package kr.ac.yonam.attendance.model

import com.google.gson.annotations.SerializedName

data class RecognitionInfo(
    val distance: Double? = null,
    val similarity: Double? = null
)

data class AttendanceResult(
    val marked: Boolean? = null,
    val message: String? = null
)

data class AttendanceResponse(
    val success: Boolean? = null,
    val matched: Boolean? = null,
    val status: String? = null,
    val message: String? = null,
    @SerializedName("hold_seconds")
    val holdSeconds: Double? = null,
    @SerializedName("elapsed_seconds")
    val elapsedSeconds: Double? = null,
    @SerializedName("remaining_seconds")
    val remainingSeconds: Double? = null,
    val session: Session? = null,
    val student: Student? = null,
    val recognition: RecognitionInfo? = null,
    val attendance: AttendanceResult? = null
)
