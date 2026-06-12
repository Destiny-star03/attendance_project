package kr.ac.yonam.attendance.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ActivityRoleSelectBinding
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ServerConfig

class RoleSelectActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRoleSelectBinding
    private var lastCheckedServerUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindActions()
        updateServerUrl()
        checkServerConnection()
    }

    override fun onResume() {
        super.onResume()
        val beforeUrl = lastCheckedServerUrl
        updateServerUrl()
        if (beforeUrl != null && beforeUrl != currentServerUrl()) {
            checkServerConnection()
        }
    }

    private fun bindActions() {
        binding.buttonStartStudent.setOnClickListener {
            startActivity(
                Intent(this, resolveActivity("StudentMainActivity", MainActivity::class.java))
                    .putExtra(EXTRA_SERVER_URL, currentServerUrl())
            )
        }

        binding.buttonServerSetting.setOnClickListener {
            startActivity(Intent(this, ServerSettingActivity::class.java))
        }

        binding.buttonChangeClassroom.setOnClickListener {
            startActivity(Intent(this, ClassroomSelectActivity::class.java))
        }
    }

    private fun checkServerConnection() {
        val serverUrl = currentServerUrl()
        lastCheckedServerUrl = serverUrl
        setLoading(true)
        binding.textServerStatus.text = getString(R.string.server_status_checking)
        binding.textServerStatus.setTextColor(color(R.color.text_secondary))

        lifecycleScope.launch {
            val response = AttendanceRepository(serverUrl).checkHealth()
            if (response.status == "ok" || response.success == true) {
                binding.textServerStatus.text = getString(R.string.server_status_success)
                binding.textServerStatus.setTextColor(color(R.color.yonam_green))
            } else {
                binding.textServerStatus.text = getString(
                    R.string.server_status_failed,
                    response.message ?: getString(R.string.server_status_unknown)
                )
                binding.textServerStatus.setTextColor(color(R.color.yonam_red))
            }
            setLoading(false)
        }
    }

    private fun updateServerUrl() {
        binding.textServerUrl.text = getString(
            R.string.role_select_server_url,
            ServerConfig.displayBaseUrl(ServerConfig.getBaseUrl(this))
        )
    }

    private fun currentServerUrl(): String {
        return ServerConfig.normalizeBaseUrl(ServerConfig.getBaseUrl(this))
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressLoading.visibility =
            if (isLoading) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun color(colorResId: Int): Int {
        return ContextCompat.getColor(this, colorResId)
    }

    private fun resolveActivity(
        activityName: String,
        fallbackActivity: Class<out AppCompatActivity>
    ): Class<*> {
        return runCatching {
            Class.forName("$packageName.ui.$activityName")
        }.getOrDefault(fallbackActivity)
    }

    companion object {
        private const val EXTRA_SERVER_URL = "extra_server_url"
    }
}
