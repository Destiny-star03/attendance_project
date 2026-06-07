package kr.ac.yonam.attendance.model

import com.google.gson.annotations.SerializedName

data class CreateSubjectRequest(
    @SerializedName("subject_name")
    val subjectName: String? = null,
    @SerializedName("professor_name")
    val professorName: String? = null,
    val classroom: String? = null
)
