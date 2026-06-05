package kr.ac.yonam.attendance.model

import com.google.gson.annotations.SerializedName

data class EnrollStatusResponse(
    val success: Boolean? = null,
    val message: String? = null,
    @SerializedName("enroll_id")
    val enrollId: String? = null,
    @SerializedName("student_no")
    val studentNo: String? = null,
    val name: String? = null,
    val department: String? = null,
    @SerializedName("required_poses")
    val requiredPoses: List<String>? = null,
    @SerializedName("completed_poses")
    val completedPoses: List<String>? = null,
    @SerializedName("remaining_poses")
    val remainingPoses: List<String>? = null,
    val progress: Double? = null
)
