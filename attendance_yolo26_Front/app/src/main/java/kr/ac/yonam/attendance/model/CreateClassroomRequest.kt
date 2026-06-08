package kr.ac.yonam.attendance.model

import com.google.gson.annotations.SerializedName

data class CreateClassroomRequest(
    @SerializedName("classroom_name")
    val classroomName: String,
    @SerializedName("building_name")
    val buildingName: String? = null,
    val floor: String? = null,
    val description: String? = null
)
