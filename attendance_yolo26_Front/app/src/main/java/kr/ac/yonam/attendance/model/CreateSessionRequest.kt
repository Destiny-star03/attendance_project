package kr.ac.yonam.attendance.model

import com.google.gson.annotations.SerializedName

data class CreateSessionRequest(
    @SerializedName("class_date")
    val classDate: String? = null,
    @SerializedName("start_time")
    val startTime: String? = null,
    @SerializedName("end_time")
    val endTime: String? = null,
    @SerializedName("classroom_id")
    val classroomId: Int? = null,
    @SerializedName("day_of_week")
    val dayOfWeek: String? = null,
    val activate: Boolean? = null
)
