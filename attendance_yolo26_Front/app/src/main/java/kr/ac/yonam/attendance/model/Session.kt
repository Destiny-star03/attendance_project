package kr.ac.yonam.attendance.model

import com.google.gson.annotations.SerializedName

data class Session(
    @SerializedName("session_id")
    val sessionId: Int? = null,
    val id: Int? = null,
    @SerializedName("subject_id")
    val subjectId: Int? = null,
    @SerializedName("subject_name")
    val subjectName: String? = null,
    @SerializedName("classroom_id")
    val classroomId: Int? = null,
    @SerializedName("classroom_name")
    val classroomName: String? = null,
    val classroom: String? = null,
    @SerializedName("class_date")
    val classDate: String? = null,
    @SerializedName("start_time")
    val startTime: String? = null,
    @SerializedName("end_time")
    val endTime: String? = null,
    @SerializedName("is_active")
    val isActive: Boolean? = null
) {
    val resolvedSessionId: Int?
        get() = sessionId ?: id
}

data class ActiveSessionResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val session: Session? = null
)

data class SessionListResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val items: List<Session>? = null
)
