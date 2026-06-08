package kr.ac.yonam.attendance.model

import com.google.gson.annotations.SerializedName

data class ClassroomRequest(
    @SerializedName("classroom_name")
    val classroomName: String? = null,
    @SerializedName("building_name")
    val buildingName: String? = null,
    val floor: Int? = null,
    val description: String? = null,
    @SerializedName("is_active")
    val isActive: Boolean? = null
)
