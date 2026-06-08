package kr.ac.yonam.attendance.model

import com.google.gson.annotations.SerializedName

data class CreateSubjectRequest(
    @SerializedName("subject_name")
    val subjectName: String? = null,
    @SerializedName("professor_name")
    val professorName: String? = null,
    val classroom: String? = null,
    @SerializedName("classroom_id")
    val classroomId: Int? = null,
    @SerializedName("day_of_week")
    val dayOfWeek: String? = null,
    @SerializedName("start_time")
    val startTime: String? = null,
    @SerializedName("end_time")
    val endTime: String? = null
)
