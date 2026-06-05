package kr.ac.yonam.attendance

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.data.ApiClient
import kr.ac.yonam.attendance.data.SessionDto
import kr.ac.yonam.attendance.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonCheckServer.setOnClickListener {
            checkServerConnection()
        }

        binding.buttonLoadActiveSession.setOnClickListener {
            loadActiveSession()
        }

        binding.buttonStartAttendance.setOnClickListener {
            Toast.makeText(this, "카메라 출석 화면은 2단계에서 구현합니다.", Toast.LENGTH_SHORT).show()
        }

        binding.buttonAttendanceList.setOnClickListener {
            Toast.makeText(this, "출석 현황 화면은 2단계에서 구현합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkServerConnection() {
        setLoading(true)
        binding.textServerStatus.text = "서버 상태: 확인 중..."

        lifecycleScope.launch {
            try {
                val response = api().health()
                if (response.status == "ok") {
                    binding.textServerStatus.text = "서버 상태: 연결 성공"
                } else {
                    binding.textServerStatus.text = "서버 상태: 알 수 없는 응답"
                }
            } catch (error: Exception) {
                binding.textServerStatus.text = "서버 상태: 연결 실패\n${error.message}"
            } finally {
                setLoading(false)
            }
        }
    }

    private fun loadActiveSession() {
        setLoading(true)
        binding.textSessionInfo.text = "활성 수업 조회 중..."

        lifecycleScope.launch {
            try {
                val response = api().getActiveSession()
                if (response.success && response.session != null) {
                    binding.textSessionInfo.text = formatSession(response.session)
                } else {
                    binding.textSessionInfo.text = response.message ?: "활성 수업이 없습니다."
                }
            } catch (error: Exception) {
                binding.textSessionInfo.text = "활성 수업 조회 실패\n${error.message}"
            } finally {
                setLoading(false)
            }
        }
    }

    private fun api() = ApiClient.create(binding.editServerUrl.text?.toString().orEmpty())

    private fun formatSession(session: SessionDto): String {
        return buildString {
            appendLine("과목: ${session.subjectName ?: "-"}")
            appendLine("날짜: ${session.classDate ?: "-"}")
            appendLine("시간: ${session.startTime ?: "-"} ~ ${session.endTime ?: "-"}")
            append("세션 ID: ${session.sessionId ?: "-"}")
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.buttonCheckServer.isEnabled = !isLoading
        binding.buttonLoadActiveSession.isEnabled = !isLoading
        binding.buttonStartAttendance.isEnabled = !isLoading
        binding.buttonAttendanceList.isEnabled = !isLoading
    }
}
