package kr.ac.yonam.attendance.ui

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
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
    private lateinit var binding: DialogStudentDetailBinding
    private var studentId: Int? = null
    private lateinit var serverUrl: String

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogStudentDetailBinding.inflate(layoutInflater)
        val args = requireArguments()

        studentId = args.getInt(ARG_STUDENT_ID).takeIf { it > 0 }
        serverUrl = ServerConfig.normalizeBaseUrl(args.getString(ARG_SERVER_URL))

        binding.textStudentNo.text = args.getString(ARG_STUDENT_NO).orEmpty().ifBlank { "-" }
        binding.textStudentName.text = args.getString(ARG_NAME).orEmpty().ifBlank { "-" }
        binding.textDepartment.text = args.getString(ARG_DEPARTMENT).orEmpty().ifBlank { "-" }
        showLoading()

        if (studentId == null) {
            showStatsError("학생 ID가 없습니다.")
        } else {
            loadStudentStats(studentId!!)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("학생 상세")
            .setView(binding.root)
            .setNegativeButton("삭제", null)
            .setPositiveButton("확인", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(color(R.color.yonam_red))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                confirmDeleteStudent()
            }
        }

        return dialog
    }

    private fun loadStudentStats(studentId: Int) {
        lifecycleScope.launch {
            val response = AttendanceRepository(serverUrl).getStudentStats(studentId)
            if (response.success == true) {
                showStats(response.stats)
            } else {
                showStatsError(response.message ?: "통계를 불러오지 못했습니다.")
            }
        }
    }

    private fun confirmDeleteStudent() {
        val id = studentId
        if (id == null) {
            showStatsError("학생 ID가 없어 삭제할 수 없습니다.")
            return
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("학생 삭제")
            .setMessage("정말 이 학생을 삭제하시겠습니까?")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ ->
                deleteStudent(id)
            }
            .show()
    }

    private fun deleteStudent(studentId: Int) {
        setDeleteEnabled(false)
        lifecycleScope.launch {
            val response = AttendanceRepository(serverUrl).deleteStudent(studentId)
            if (response.success == true || response.deleted == true) {
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    bundleOf(KEY_DELETED to true)
                )
                dismiss()
            } else {
                showStatsError(response.message ?: "학생을 삭제하지 못했습니다.")
                setDeleteEnabled(true)
            }
        }
    }

    private fun showLoading() {
        binding.textStatsMessage.text = "통계를 불러오는 중..."
        binding.textStatsMessage.visibility = View.VISIBLE
        binding.textStatsMessage.setTextColor(color(R.color.text_secondary))
        binding.textPresentCount.text = "출석 0명"
        binding.textLateCount.text = "지각 0명"
        binding.textAbsentCount.text = "결석 0명"
    }

    private fun showStats(stats: StudentStats?) {
        binding.textStatsMessage.visibility = View.GONE
        binding.textPresentCount.text = "출석 ${stats?.attendanceCount ?: 0}명"
        binding.textLateCount.text = "지각 ${stats?.lateCount ?: 0}명"
        binding.textAbsentCount.text = "결석 ${stats?.absenceCount ?: 0}명"
    }

    private fun showStatsError(message: String) {
        binding.textStatsMessage.text = message
        binding.textStatsMessage.visibility = View.VISIBLE
        binding.textStatsMessage.setTextColor(color(R.color.yonam_red))
        binding.textPresentCount.text = "출석 0명"
        binding.textLateCount.text = "지각 0명"
        binding.textAbsentCount.text = "결석 0명"
    }

    private fun setDeleteEnabled(enabled: Boolean) {
        (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = enabled
    }

    private fun color(colorResId: Int): Int {
        return ContextCompat.getColor(requireContext(), colorResId)
    }

    companion object {
        const val TAG = "StudentDetailDialog"
        const val REQUEST_KEY = "student_detail_result"
        const val KEY_DELETED = "student_deleted"

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
