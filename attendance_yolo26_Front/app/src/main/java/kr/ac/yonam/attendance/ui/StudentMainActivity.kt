package kr.ac.yonam.attendance.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ActivityStudentMainBinding
import kr.ac.yonam.attendance.model.Session
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ClassroomConfig
import kr.ac.yonam.attendance.util.ServerConfig

class StudentMainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStudentMainBinding
    private var currentSessionId: Int? = null
    private var currentSessionRefreshJob: Job? = null

    private val serverUrl: String by lazy {
        ServerConfig.normalizeBaseUrl(
            intent.getStringExtra(EXTRA_SERVER_URL) ?: ServerConfig.getBaseUrl(this)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindActions()
        updateStartAttendanceEnabled(isLoading = true)
    }

    override fun onResume() {
        super.onResume()
        startCurrentSessionAutoRefresh()
    }

    override fun onPause() {
        currentSessionRefreshJob?.cancel()
        currentSessionRefreshJob = null
        super.onPause()
    }

    private fun bindActions() {
        binding.buttonStartAttendance.setOnClickListener {
            val sessionId = currentSessionId
            if (sessionId == null) {
                showEmptySession()
                return@setOnClickListener
            }

            startActivity(
                Intent(this, AttendanceCameraActivity::class.java)
                    .putExtra(AttendanceCameraActivity.EXTRA_SERVER_URL, serverUrl)
                    .putExtra(AttendanceCameraActivity.EXTRA_SESSION_ID, sessionId)
            )
        }

        binding.buttonBackToRoleSelect.setOnClickListener {
            finish()
        }
    }

    private fun startCurrentSessionAutoRefresh() {
        currentSessionRefreshJob?.cancel()
        currentSessionRefreshJob = lifecycleScope.launch {
            var showLoading = true
            while (true) {
                fetchAndRenderCurrentSession(showLoading = showLoading)
                showLoading = false
                delay(CURRENT_SESSION_REFRESH_INTERVAL_MILLIS)
            }
        }
    }

    private suspend fun fetchAndRenderCurrentSession(showLoading: Boolean) {
        val classroomId = ClassroomConfig.getSelectedClassroomId(this)
        if (classroomId == null) {
            showClassroomRequired()
            if (showLoading) {
                openClassroomSelect()
            }
            return
        }

        if (showLoading) {
            setLoading(true)
            showSessionLoading()
        }

        try {
            val repository = AttendanceRepository(serverUrl)
            val response = repository.getCurrentSession(classroomId)
            val session = response.session
            if (response.success == true && session?.resolvedSessionId != null) {
                showSession(session)
            } else if (response.success == true && session != null) {
                showLoadFailed("현재 수업 응답에 session_id가 없습니다.")
            } else if (response.status == "no_current_session" || response.success == true) {
                showEmptySession()
            } else {
                showLoadFailed(response.message)
            }
        } catch (error: Exception) {
            showLoadFailed(error.message)
        } finally {
            if (showLoading) {
                setLoading(false)
            }
        }
    }

    private fun showSessionLoading() {
        currentSessionId = null
        updateStartAttendanceEnabled(isLoading = true)
        binding.textSubjectName.text = getString(R.string.loading)
        binding.textClassroom.text = ClassroomConfig.getSelectedClassroomName(this) ?: "-"
        binding.textClassDate.text = "-"
        binding.textStartTime.text = "-"
        binding.textEndTime.text = "-"
        binding.textActiveStatus.text = getString(R.string.loading)
        binding.textActiveStatus.setTextColor(color(R.color.text_secondary))
        binding.textSessionMessage.visibility = View.GONE
    }

    private fun showSession(session: Session) {
        currentSessionId = session.resolvedSessionId
        binding.textSubjectName.text = session.subjectName ?: "-"
        binding.textClassroom.text = session.classroomDisplayName()
        binding.textClassDate.text = session.classDate ?: "-"
        binding.textStartTime.text = session.startTime ?: "-"
        binding.textEndTime.text = session.endTime ?: "-"
        binding.textActiveStatus.text = if (session.isActive == true) {
            getString(R.string.session_active)
        } else {
            getString(R.string.session_inactive)
        }
        binding.textActiveStatus.setTextColor(
            color(if (session.isActive == true) R.color.yonam_green else R.color.text_secondary)
        )
        binding.textSessionMessage.visibility = View.GONE
        updateStartAttendanceEnabled(isLoading = false)
    }

    private fun showEmptySession() {
        currentSessionId = null
        binding.textSubjectName.text = "-"
        binding.textClassroom.text = ClassroomConfig.getSelectedClassroomName(this) ?: "-"
        binding.textClassDate.text = "-"
        binding.textStartTime.text = "-"
        binding.textEndTime.text = "-"
        binding.textActiveStatus.text = getString(R.string.session_inactive)
        binding.textActiveStatus.setTextColor(color(R.color.text_secondary))
        binding.textSessionMessage.text = getString(R.string.current_classroom_session_empty)
        binding.textSessionMessage.setTextColor(color(R.color.text_secondary))
        binding.textSessionMessage.visibility = View.VISIBLE
        updateStartAttendanceEnabled(isLoading = false)
    }

    private fun showLoadFailed(message: String? = null) {
        currentSessionId = null
        binding.textSubjectName.text = "-"
        binding.textClassroom.text = ClassroomConfig.getSelectedClassroomName(this) ?: "-"
        binding.textClassDate.text = "-"
        binding.textStartTime.text = "-"
        binding.textEndTime.text = "-"
        binding.textActiveStatus.text = getString(R.string.session_inactive)
        binding.textActiveStatus.setTextColor(color(R.color.yonam_red))
        binding.textSessionMessage.text = message?.takeIf { it.isNotBlank() }
            ?: getString(R.string.student_session_load_failed)
        binding.textSessionMessage.setTextColor(color(R.color.yonam_red))
        binding.textSessionMessage.visibility = View.VISIBLE
        updateStartAttendanceEnabled(isLoading = false)
    }

    private fun showClassroomRequired() {
        currentSessionId = null
        binding.textSubjectName.text = "-"
        binding.textClassroom.text = "-"
        binding.textClassDate.text = "-"
        binding.textStartTime.text = "-"
        binding.textEndTime.text = "-"
        binding.textActiveStatus.text = getString(R.string.session_inactive)
        binding.textActiveStatus.setTextColor(color(R.color.yonam_red))
        binding.textSessionMessage.text = getString(R.string.classroom_select_required)
        binding.textSessionMessage.setTextColor(color(R.color.yonam_red))
        binding.textSessionMessage.visibility = View.VISIBLE
        updateStartAttendanceEnabled(isLoading = false)
    }

    private fun openClassroomSelect() {
        startActivity(Intent(this, ClassroomSelectActivity::class.java))
    }

    private fun Session.classroomDisplayName(): String {
        return classroomName
            ?: classroom
            ?: ClassroomConfig.getSelectedClassroomName(this@StudentMainActivity)
            ?: "-"
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        updateStartAttendanceEnabled(isLoading)
        binding.buttonBackToRoleSelect.isEnabled = !isLoading
    }

    private fun updateStartAttendanceEnabled(isLoading: Boolean = false) {
        binding.buttonStartAttendance.isEnabled = !isLoading
    }

    private fun color(colorResId: Int): Int {
        return ContextCompat.getColor(this, colorResId)
    }

    companion object {
        const val EXTRA_SERVER_URL = "extra_server_url"
        private const val CURRENT_SESSION_REFRESH_INTERVAL_MILLIS = 30_000L
    }
}
