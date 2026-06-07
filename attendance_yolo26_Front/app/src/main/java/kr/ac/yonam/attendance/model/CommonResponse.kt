package kr.ac.yonam.attendance.model

import com.google.gson.annotations.SerializedName

data class CommonResponse(
    val success: Boolean? = null,
    val status: String? = null,
    val message: String? = null,
    @SerializedName("enroll_id")
    val enrollId: String? = null,
    val deleted: Boolean? = null,
    val enrollment: SubjectEnrollment? = null
)
