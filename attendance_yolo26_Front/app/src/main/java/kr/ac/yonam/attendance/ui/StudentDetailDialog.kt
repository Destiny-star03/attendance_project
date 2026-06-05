package kr.ac.yonam.attendance.ui

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kr.ac.yonam.attendance.databinding.DialogStudentDetailBinding
import kr.ac.yonam.attendance.model.AttendanceItem

class StudentDetailDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogStudentDetailBinding.inflate(layoutInflater)
        val args = requireArguments()

        binding.textStudentNo.text = args.getString(ARG_STUDENT_NO).orEmpty().ifBlank { "-" }
        binding.textStudentName.text = args.getString(ARG_NAME).orEmpty().ifBlank { "-" }
        binding.textDepartment.text = args.getString(ARG_DEPARTMENT).orEmpty().ifBlank { "-" }

        // 학생 통계 API가 연결되기 전까지 기본 더미 통계를 표시한다.
        binding.textPresentCount.text = "출석 0회"
        binding.textLateCount.text = "지각 0회"
        binding.textAbsentCount.text = "결석 0회"

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("학생 상세")
            .setView(binding.root)
            .setPositiveButton("확인", null)
            .create()
    }

    companion object {
        const val TAG = "StudentDetailDialog"

        private const val ARG_STUDENT_NO = "arg_student_no"
        private const val ARG_NAME = "arg_name"
        private const val ARG_DEPARTMENT = "arg_department"

        fun newInstance(item: AttendanceItem): StudentDetailDialog {
            return StudentDetailDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_STUDENT_NO, item.studentNo)
                    putString(ARG_NAME, item.name)
                    putString(ARG_DEPARTMENT, item.department)
                }
            }
        }
    }
}
