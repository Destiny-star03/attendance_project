package kr.ac.yonam.attendance.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ActivityMainBinding
import kr.ac.yonam.attendance.model.Session
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ServerConfig

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var lastLoadedServerUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applySavedServerUrl()
        showConnectionReady()
        showSessionLoading()
        bindActions()
        loadActiveSession()
    }

    override fun onResume() {
        super.onResume()
        val beforeUrl = currentServerUrl()
        applySavedServerUrl()
        val afterUrl = currentServerUrl()
        if (beforeUrl != afterUrl && lastLoadedServerUrl != null) {
            showConnectionReady()
            loadActiveSession()
        }
    }

    private fun bindActions() {
        binding.buttonCheckServer.setOnClickListener {
            checkServerConnection()
        }
        binding.buttonStartAttendance.setOnClickListener {
            startActivity(
                Intent(this, AttendanceCameraActivity::class.java)
                    .putExtra(AttendanceCameraActivity.EXTRA_SERVER_URL, currentServerUrl())
            )
        }
        binding.buttonAttendanceList.setOnClickListener {
            startActivity(
                Intent(this, AttendanceListActivity::class.java)
                    .putExtra(AttendanceListActivity.EXTRA_SERVER_URL, currentServerUrl())
            )
        }
        binding.buttonRegisterStudent.setOnClickListener {
            startActivity(
                Intent(this, RegisterStudentActivity::class.java)
                    .putExtra(RegisterStudentActivity.EXTRA_SERVER_URL, currentServerUrl())
            )
        }
        binding.buttonServerSetting.setOnClickListener {
            startActivity(Intent(this, ServerSettingActivity::class.java))
        }
    }

    private fun loadActiveSession() {
        setLoading(true)
        showSessionLoading()
        lastLoadedServerUrl = currentServerUrl()

        lifecycleScope.launch {
            try {
                val response = AttendanceRepository(currentServerUrl()).getActiveSession()
                if (response.success == true && response.session != null) {
                    showSession(response.session)
                } else {
                    showEmptySession(response.message ?: getString(R.string.session_empty))
                }
            } catch (error: Exception) {
                showEmptySession(
                    getString(
                        R.string.session_load_failed,
                        error.message ?: getString(R.string.error_unknown)
                    )
                )
            } finally {
                setLoading(false)
            }
        }
    }

    private fun checkServerConnection() {
        setLoading(true)
        binding.textServerStatus.text = getString(R.string.server_status_checking)
        binding.textServerStatus.setTextColor(color(R.color.text_secondary))

        lifecycleScope.launch {
            try {
                val response = AttendanceRepository(currentServerUrl()).checkHealth()
                if (response.status == "ok") {
                    showConnectionSuccess()
                } else {
                    showConnectionFailure(response.message ?: getString(R.string.server_status_unknown))
                }
            } catch (error: Exception) {
                showConnectionFailure(error.message ?: getString(R.string.error_unknown))
            } finally {
                setLoading(false)
            }
        }
    }

    private fun showConnectionReady() {
        binding.textServerStatus.text = getString(R.string.server_status_ready)
        binding.textServerStatus.setTextColor(color(R.color.text_secondary))
    }

    private fun showConnectionSuccess() {
        binding.textServerStatus.text = getString(R.string.server_status_success)
        binding.textServerStatus.setTextColor(color(R.color.yonam_green))
    }

    private fun showConnectionFailure(message: String) {
        binding.textServerStatus.text = getString(R.string.server_status_failed, message)
        binding.textServerStatus.setTextColor(color(R.color.yonam_red))
    }

    private fun showSessionLoading() {
        binding.textSubjectName.text = getString(R.string.loading)
        binding.textClassDate.text = "-"
        binding.textStartTime.text = "-"
        binding.textEndTime.text = "-"
        binding.textActiveStatus.text = getString(R.string.loading)
        binding.textActiveStatus.setTextColor(color(R.color.text_secondary))
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
    }

    private fun showEmptySession(message: String) {
        binding.textSubjectName.text = message
        binding.textClassDate.text = "-"
        binding.textStartTime.text = "-"
        binding.textEndTime.text = "-"
        binding.textActiveStatus.text = getString(R.string.session_inactive)
        binding.textActiveStatus.setTextColor(color(R.color.yonam_red))
    }

    private fun setLoading(isLoading: Boolean) {
        binding.buttonCheckServer.isEnabled = !isLoading
        binding.buttonStartAttendance.isEnabled = !isLoading
        binding.buttonAttendanceList.isEnabled = !isLoading
        binding.buttonRegisterStudent.isEnabled = !isLoading
        binding.buttonServerSetting.isEnabled = !isLoading
    }

    private fun currentServerUrl(): String {
        return ServerConfig.normalizeBaseUrl(binding.editServerUrl.text?.toString())
    }

    private fun applySavedServerUrl() {
        binding.editServerUrl.setText(ServerConfig.displayBaseUrl(ServerConfig.getBaseUrl(this)))
    }

    private fun color(colorResId: Int): Int {
        return ContextCompat.getColor(this, colorResId)
    }
}
