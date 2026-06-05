package kr.ac.yonam.attendance.model

import com.google.gson.annotations.SerializedName

data class EnrollCompleteResponse(
    val success: Boolean? = null,
    val message: String? = null,
    @SerializedName("enroll_id")
    val enrollId: String? = null,
    val created: Boolean? = null,
    val updated: Boolean? = null,
    val student: Student? = null
)
