package kr.ac.yonam.attendance.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ActivityStudentMainBinding
import kr.ac.yonam.attendance.model.Session
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ServerConfig

class StudentMainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStudentMainBinding

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
        loadActiveSession()
    }

    private fun bindActions() {
        binding.buttonStartAttendance.setOnClickListener {
            startActivity(
                Intent(this, AttendanceCameraActivity::class.java)
                    .putExtra(AttendanceCameraActivity.EXTRA_SERVER_URL, serverUrl)
            )
        }

        binding.buttonBackToRoleSelect.setOnClickListener {
            finish()
        }
    }

    private fun loadActiveSession() {
        setLoading(true)
        showSessionLoading()

        lifecycleScope.launch {
            try {
                val response = AttendanceRepository(serverUrl).getActiveSession()
                if (response.success == true && response.session != null) {
                    showSession(response.session)
                } else if (response.success == true) {
                    showEmptySession()
                } else {
                    showLoadFailed()
                }
            } catch (error: Exception) {
                showLoadFailed()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun showSessionLoading() {
        binding.textSubjectName.text = getString(R.string.loading)
        binding.textClassDate.text = "-"
        binding.textStartTime.text = "-"
        binding.textEndTime.text = "-"
        binding.textActiveStatus.text = getString(R.string.loading)
        binding.textActiveStatus.setTextColor(color(R.color.text_secondary))
        binding.textSessionMessage.visibility = View.GONE
    }

    private fun showSession(session: Session) {
        binding.textSubjectName.text = session.subjectName ?: "-"
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
    }

    private fun showEmptySession() {
        binding.textSubjectName.text = "-"
        binding.textClassDate.text = "-"
        binding.textStartTime.text = "-"
        binding.textEndTime.text = "-"
        binding.textActiveStatus.text = getString(R.string.session_inactive)
        binding.textActiveStatus.setTextColor(color(R.color.text_secondary))
        binding.textSessionMessage.text = getString(R.string.session_empty)
        binding.textSessionMessage.setTextColor(color(R.color.text_secondary))
        binding.textSessionMessage.visibility = View.VISIBLE
    }

    private fun showLoadFailed() {
        binding.textSubjectName.text = "-"
        binding.textClassDate.text = "-"
        binding.textStartTime.text = "-"
        binding.textEndTime.text = "-"
        binding.textActiveStatus.text = getString(R.string.session_inactive)
        binding.textActiveStatus.setTextColor(color(R.color.yonam_red))
        binding.textSessionMessage.text = getString(R.string.student_session_load_failed)
        binding.textSessionMessage.setTextColor(color(R.color.yonam_red))
        binding.textSessionMessage.visibility = View.VISIBLE
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonStartAttendance.isEnabled = !isLoading
        binding.buttonBackToRoleSelect.isEnabled = !isLoading
    }

    private fun color(colorResId: Int): Int {
        return ContextCompat.getColor(this, colorResId)
    }

    companion object {
        const val EXTRA_SERVER_URL = "extra_server_url"
    }
}
