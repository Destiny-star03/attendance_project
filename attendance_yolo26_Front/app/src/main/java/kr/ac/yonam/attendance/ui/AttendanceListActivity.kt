package kr.ac.yonam.attendance.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ActivityAttendanceListBinding
import kr.ac.yonam.attendance.model.AttendanceItem
import kr.ac.yonam.attendance.model.Session
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ServerConfig

class AttendanceListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAttendanceListBinding
    private lateinit var adapter: AttendanceAdapter

    private val serverUrl: String by lazy {
        ServerConfig.normalizeBaseUrl(intent.getStringExtra(EXTRA_SERVER_URL))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttendanceListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = AttendanceAdapter { item ->
            StudentDetailDialog.newInstance(item, serverUrl).show(supportFragmentManager, StudentDetailDialog.TAG)
        }

        binding.recyclerAttendance.layoutManager = LinearLayoutManager(this)
        binding.recyclerAttendance.adapter = adapter

        binding.buttonRefresh.setOnClickListener {
            loadAttendance()
        }

        loadAttendance()
    }

    private fun loadAttendance() {
        setLoading(true)
        binding.textError.visibility = View.GONE
        binding.textEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // session_id와 date를 보내지 않으면 서버가 현재 활성 세션 기준으로 조회한다.
                val response = AttendanceRepository(serverUrl).getAttendance()
                showSession(response.session)

                if (response.success == true) {
                    showAttendanceItems(response.items.orEmpty())
                } else {
                    showError(response.message ?: "출석 현황을 조회하지 못했습니다.")
                    showAttendanceItems(emptyList())
                }
            } catch (error: Exception) {
                showSession(null)
                showError(
                    "서버 오류로 출석 현황을 불러오지 못했습니다.\n" +
                        (error.message ?: getString(R.string.error_unknown))
                )
                showAttendanceItems(emptyList())
            } finally {
                setLoading(false)
            }
        }
    }

    private fun showSession(session: Session?) {
        binding.textSubjectName.text = session?.subjectName ?: "-"
        binding.textClassDate.text = session?.classDate ?: "-"
        binding.textStartTime.text = session?.startTime ?: "-"
        binding.textEndTime.text = session?.endTime ?: "-"
    }

    private fun showAttendanceItems(items: List<AttendanceItem>) {
        adapter.submitList(items)
        binding.textAttendanceCount.text = "출석 인원 ${items.size}명"
        binding.textEmpty.visibility =
            if (items.isEmpty() && binding.textError.visibility != View.VISIBLE) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun showError(message: String) {
        binding.textError.text = message
        binding.textError.visibility = View.VISIBLE
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonRefresh.isEnabled = !isLoading
    }

    companion object {
        const val EXTRA_SERVER_URL = "extra_server_url"
    }
}
