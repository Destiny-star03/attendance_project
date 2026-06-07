package kr.ac.yonam.attendance.model

import com.google.gson.annotations.SerializedName

data class EnrollFrameResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val status: String? = null,
    @SerializedName("enroll_id")
    val enrollId: String? = null,
    val pose: String? = null,
    @SerializedName("required_poses")
    val requiredPoses: List<String>? = null,
    @SerializedName("completed_poses")
    val completedPoses: List<String>? = null,
    @SerializedName("remaining_poses")
    val remainingPoses: List<String>? = null,
    @SerializedName("next_pose")
    val nextPose: String? = null,
    val progress: Int? = null,
    @SerializedName("ready_to_complete")
    val readyToComplete: Boolean? = null,
    val student: Student? = null
)
