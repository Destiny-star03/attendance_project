package kr.ac.yonam.attendance.model

import com.google.gson.annotations.SerializedName

data class EnrollFrameResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val status: String? = null,
    @SerializedName("enroll_id")
    val enrollId: String? = null,
    val pose: String? = null,
    @SerializedName("completed_poses")
    val completedPoses: List<String>? = null,
    @SerializedName("remaining_poses")
    val remainingPoses: List<String>? = null,
    val progress: Double? = null
)
