package kr.ac.yonam.attendance.model

import com.google.gson.annotations.SerializedName

data class Subject(
    @SerializedName("subject_id")
    val subjectId: Int? = null,
    val id: Int? = null,
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
    val endTime: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("student_count")
    val studentCount: Int? = null
) {
    val resolvedSubjectId: Int?
        get() = subjectId ?: id
}

data class SubjectEnrollment(
    val id: Int? = null,
    @SerializedName("subject_id")
    val subjectId: Int? = null,
    @SerializedName("student_id")
    val studentId: Int? = null
)
