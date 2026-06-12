package kr.ac.yonam.attendance.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ActivityAdminMainBinding
import kr.ac.yonam.attendance.model.Session
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ClassroomConfig
import kr.ac.yonam.attendance.util.ServerConfig

class AdminMainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminMainBinding
    private var currentSessionRefreshJob: Job? = null

    private val serverUrl: String by lazy {
        ServerConfig.normalizeBaseUrl(
            intent.getStringExtra(EXTRA_SERVER_URL) ?: ServerConfig.getBaseUrl(this)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindActions()
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
        binding.buttonManageClassrooms.setOnClickListener {
            startActivity(
                Intent(this, ClassroomManageActivity::class.java)
                    .putExtra(ClassroomManageActivity.EXTRA_SERVER_URL, serverUrl)
            )
        }

        binding.buttonManageSubjects.setOnClickListener {
            openSubjectManagement()
        }

        binding.buttonRegisterStudent.setOnClickListener {
            startActivity(
                Intent(this, RegisterStudentActivity::class.java)
                    .putExtra(RegisterStudentActivity.EXTRA_SERVER_URL, serverUrl)
            )
        }

        binding.buttonAttendanceList.setOnClickListener {
            startActivity(
                Intent(this, AttendanceListActivity::class.java)
                    .putExtra(AttendanceListActivity.EXTRA_SERVER_URL, serverUrl)
            )
        }

        binding.buttonServerSetting.setOnClickListener {
            startActivity(Intent(this, ServerSettingActivity::class.java))
        }

        binding.buttonBackToRoleSelect.setOnClickListener {
            finish()
        }
    }

    private fun openSubjectManagement() {
        val subjectListActivity = runCatching {
            Class.forName("$packageName.ui.SubjectListActivity")
        }.getOrNull()

        if (subjectListActivity == null) {
            Toast.makeText(
                this,
                getString(R.string.admin_subject_management_not_ready),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        startActivity(
            Intent(this, subjectListActivity)
                .putExtra(SubjectListActivity.EXTRA_SERVER_URL, serverUrl)
        )
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
        binding.textSubjectName.text = getString(R.string.loading)
        binding.textClassroom.text = ClassroomConfig.getSelectedClassroomName(this) ?: "-"
        binding.textClassDate.text = "-"
        binding.textStartTime.text = "-"
        binding.textEndTime.text = "-"
        binding.textSessionMessage.visibility = View.GONE
    }

    private fun showSession(session: Session) {
        binding.textSubjectName.text = session.subjectName ?: "-"
        binding.textClassroom.text = session.classroomDisplayName()
        binding.textClassDate.text = session.classDate ?: "-"
        binding.textStartTime.text = session.startTime ?: "-"
        binding.textEndTime.text = session.endTime ?: "-"
        binding.textSessionMessage.visibility = View.GONE
    }

    private fun showEmptySession() {
        binding.textSubjectName.text = "-"
        binding.textClassroom.text = ClassroomConfig.getSelectedClassroomName(this) ?: "-"
        binding.textClassDate.text = "-"
        binding.textStartTime.text = "-"
        binding.textEndTime.text = "-"
        binding.textSessionMessage.text = getString(R.string.current_classroom_session_empty)
        binding.textSessionMessage.setTextColor(color(R.color.text_secondary))
        binding.textSessionMessage.visibility = View.VISIBLE
    }

    private fun showLoadFailed(message: String? = null) {
        binding.textSubjectName.text = "-"
        binding.textClassroom.text = ClassroomConfig.getSelectedClassroomName(this) ?: "-"
        binding.textClassDate.text = "-"
        binding.textStartTime.text = "-"
        binding.textEndTime.text = "-"
        binding.textSessionMessage.text = message?.takeIf { it.isNotBlank() }
            ?: getString(R.string.student_session_load_failed)
        binding.textSessionMessage.setTextColor(color(R.color.yonam_red))
        binding.textSessionMessage.visibility = View.VISIBLE
    }

    private fun showClassroomRequired() {
        binding.textSubjectName.text = "-"
        binding.textClassroom.text = "-"
        binding.textClassDate.text = "-"
        binding.textStartTime.text = "-"
        binding.textEndTime.text = "-"
        binding.textSessionMessage.text = getString(R.string.admin_classroom_not_selected)
        binding.textSessionMessage.setTextColor(color(R.color.yonam_red))
        binding.textSessionMessage.visibility = View.VISIBLE
    }

    private fun Session.classroomDisplayName(): String {
        return classroomName
            ?: classroom
            ?: ClassroomConfig.getSelectedClassroomName(this@AdminMainActivity)
            ?: "-"
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonManageClassrooms.isEnabled = !isLoading
        binding.buttonManageSubjects.isEnabled = !isLoading
        binding.buttonRegisterStudent.isEnabled = !isLoading
        binding.buttonAttendanceList.isEnabled = !isLoading
        binding.buttonServerSetting.isEnabled = !isLoading
        binding.buttonBackToRoleSelect.isEnabled = !isLoading
    }

    private fun color(colorResId: Int): Int {
        return ContextCompat.getColor(this, colorResId)
    }

    companion object {
        const val EXTRA_SERVER_URL = "extra_server_url"
        private const val CURRENT_SESSION_REFRESH_INTERVAL_MILLIS = 30_000L
    }
}
