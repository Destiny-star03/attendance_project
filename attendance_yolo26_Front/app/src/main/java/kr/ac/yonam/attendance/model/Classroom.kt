package kr.ac.yonam.attendance.model

import com.google.gson.annotations.SerializedName

data class Classroom(
    @SerializedName("classroom_id")
    val classroomId: Int? = null,
    val id: Int? = null,
    @SerializedName("classroom_name")
    val classroomName: String? = null,
    @SerializedName("building_name")
    val buildingName: String? = null,
    val floor: Int? = null,
    val description: String? = null,
    @SerializedName("is_active")
    val isActive: Boolean? = null
) {
    val resolvedClassroomId: Int?
        get() = classroomId ?: id
}
