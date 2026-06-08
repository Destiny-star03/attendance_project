package kr.ac.yonam.attendance.util

import android.content.Context
import kr.ac.yonam.attendance.model.Classroom

object ClassroomConfig {
    private const val PREF_NAME = "classroom_config"
    private const val KEY_SELECTED_CLASSROOM_ID = "selected_classroom_id"
    private const val KEY_SELECTED_CLASSROOM_NAME = "selected_classroom_name"

    fun getSelectedClassroomId(context: Context): Int? {
        val value = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_SELECTED_CLASSROOM_ID, -1)
        return value.takeIf { it > 0 }
    }

    fun getSelectedClassroomName(context: Context): String? {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_CLASSROOM_NAME, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun saveSelectedClassroom(context: Context, classroom: Classroom) {
        val classroomId = classroom.resolvedClassroomId ?: return
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_SELECTED_CLASSROOM_ID, classroomId)
            .putString(KEY_SELECTED_CLASSROOM_NAME, classroom.classroomName.orEmpty())
            .apply()
    }
}
