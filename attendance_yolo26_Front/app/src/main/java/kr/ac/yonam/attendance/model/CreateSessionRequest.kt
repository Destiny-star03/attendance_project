package kr.ac.yonam.attendance.model

import com.google.gson.annotations.SerializedName

data class CreateSessionRequest(
    @SerializedName("class_date")
    val classDate: String? = null,
    @SerializedName("start_time")
    val startTime: String? = null,
    @SerializedName("end_time")
    val endTime: String? = null
)
