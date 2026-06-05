package kr.ac.yonam.attendance.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ActivityServerSettingBinding
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ServerConfig

class ServerSettingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityServerSettingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServerSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.textDefaultServer.text =
            "기본값: ${ServerConfig.displayBaseUrl(ServerConfig.DEFAULT_BASE_URL)}"
        binding.textTabletGuide.text =
            "실제 태블릿에서는 ${ServerConfig.TABLET_BASE_URL_SAMPLE} 형식으로 입력하세요."
        binding.editServerUrl.setText(ServerConfig.displayBaseUrl(ServerConfig.getBaseUrl(this)))
        showReady()

        binding.buttonBack.setOnClickListener {
            finish()
        }
        binding.buttonSave.setOnClickListener {
            saveServerUrl()
        }
        binding.buttonCheckConnection.setOnClickListener {
            checkConnection()
        }
    }

    private fun saveServerUrl() {
        val normalizedUrl = ServerConfig.normalizeBaseUrl(binding.editServerUrl.text?.toString())
        ServerConfig.saveBaseUrl(this, normalizedUrl)
        binding.editServerUrl.setText(ServerConfig.displayBaseUrl(normalizedUrl))
        binding.textStatus.text = "서버 주소가 저장되었습니다."
        binding.textStatus.setTextColor(color(R.color.yonam_green))
    }

    private fun checkConnection() {
        val normalizedUrl = ServerConfig.normalizeBaseUrl(binding.editServerUrl.text?.toString())
        setLoading(true)
        binding.textStatus.text = "서버 연결 확인 중..."
        binding.textStatus.setTextColor(color(R.color.text_secondary))

        lifecycleScope.launch {
            val response = AttendanceRepository(normalizedUrl).checkHealth()
            if (response.status == "ok" || response.success == true) {
                binding.textStatus.text = "서버 연결 성공"
                binding.textStatus.setTextColor(color(R.color.yonam_green))
            } else {
                binding.textStatus.text = "서버 연결 실패\n${response.message ?: "서버 응답을 확인할 수 없습니다."}"
                binding.textStatus.setTextColor(color(R.color.yonam_red))
            }
            setLoading(false)
        }
    }

    private fun showReady() {
        binding.textStatus.text = "현재 연결 상태: 연결 전"
        binding.textStatus.setTextColor(color(R.color.text_secondary))
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonSave.isEnabled = !isLoading
        binding.buttonCheckConnection.isEnabled = !isLoading
        binding.buttonBack.isEnabled = !isLoading
    }

    private fun color(colorResId: Int): Int {
        return ContextCompat.getColor(this, colorResId)
    }
}
