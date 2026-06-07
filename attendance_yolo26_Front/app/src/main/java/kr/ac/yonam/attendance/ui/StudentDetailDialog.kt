package kr.ac.yonam.attendance.ui

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.DialogStudentDetailBinding
import kr.ac.yonam.attendance.model.AttendanceItem
import kr.ac.yonam.attendance.model.StudentStats
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ServerConfig

class StudentDetailDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogStudentDetailBinding.inflate(layoutInflater)
        val args = requireArguments()

        val studentId = args.getInt(ARG_STUDENT_ID).takeIf { it > 0 }
        val serverUrl = ServerConfig.normalizeBaseUrl(args.getString(ARG_SERVER_URL))

        binding.textStudentNo.text = args.getString(ARG_STUDENT_NO).orEmpty().ifBlank { "-" }
        binding.textStudentName.text = args.getString(ARG_NAME).orEmpty().ifBlank { "-" }
        binding.textDepartment.text = args.getString(ARG_DEPARTMENT).orEmpty().ifBlank { "-" }
        showLoading(binding)

        if (studentId == null) {
            showStatsError(binding, "학생 ID가 없습니다.")
        } else {
            loadStudentStats(binding, serverUrl, studentId)
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("학생 상세")
            .setView(binding.root)
            .setPositiveButton("확인", null)
            .create()
    }

    private fun loadStudentStats(
        binding: DialogStudentDetailBinding,
        serverUrl: String,
        studentId: Int
    ) {
        lifecycleScope.launch {
            val response = AttendanceRepository(serverUrl).getStudentStats(studentId)
            if (response.success == true) {
                showStats(binding, response.stats)
            } else {
                showStatsError(binding, response.message ?: "통계를 불러오지 못했습니다.")
            }
        }
    }

    private fun showLoading(binding: DialogStudentDetailBinding) {
        binding.textStatsMessage.text = "통계를 불러오는 중..."
        binding.textStatsMessage.visibility = View.VISIBLE
        binding.textStatsMessage.setTextColor(color(R.color.text_secondary))
        binding.textPresentCount.text = "출석 0회"
        binding.textLateCount.text = "지각 0회"
        binding.textAbsentCount.text = "결석 0회"
    }

    private fun showStats(binding: DialogStudentDetailBinding, stats: StudentStats?) {
        binding.textStatsMessage.visibility = View.GONE
        binding.textPresentCount.text = "출석 ${stats?.attendanceCount ?: 0}회"
        binding.textLateCount.text = "지각 ${stats?.lateCount ?: 0}회"
        binding.textAbsentCount.text = "결석 ${stats?.absenceCount ?: 0}회"
    }

    private fun showStatsError(binding: DialogStudentDetailBinding, message: String) {
        binding.textStatsMessage.text = message
        binding.textStatsMessage.visibility = View.VISIBLE
        binding.textStatsMessage.setTextColor(color(R.color.yonam_red))
        binding.textPresentCount.text = "출석 0회"
        binding.textLateCount.text = "지각 0회"
        binding.textAbsentCount.text = "결석 0회"
    }

    private fun color(colorResId: Int): Int {
        return ContextCompat.getColor(requireContext(), colorResId)
    }

    companion object {
        const val TAG = "StudentDetailDialog"

        private const val ARG_STUDENT_ID = "arg_student_id"
        private const val ARG_STUDENT_NO = "arg_student_no"
        private const val ARG_NAME = "arg_name"
        private const val ARG_DEPARTMENT = "arg_department"
        private const val ARG_SERVER_URL = "arg_server_url"

        fun newInstance(item: AttendanceItem, serverUrl: String): StudentDetailDialog {
            return StudentDetailDialog().apply {
                arguments = Bundle().apply {
                    item.studentId?.let { putInt(ARG_STUDENT_ID, it) }
                    putString(ARG_STUDENT_NO, item.studentNo)
                    putString(ARG_NAME, item.name)
                    putString(ARG_DEPARTMENT, item.department)
                    putString(ARG_SERVER_URL, serverUrl)
                }
            }
        }
    }
}
