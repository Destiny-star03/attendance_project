package kr.ac.yonam.attendance.ui

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.DialogAddStudentToSubjectBinding
import kr.ac.yonam.attendance.model.Student
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ServerConfig

class AddStudentToSubjectDialog : DialogFragment() {
    private var _binding: DialogAddStudentToSubjectBinding? = null
    private val binding: DialogAddStudentToSubjectBinding
        get() = requireNotNull(_binding)

    private var students: List<Student> = emptyList()
    private var selectedStudent: Student? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddStudentToSubjectBinding.inflate(layoutInflater)

        binding.buttonAdd.setOnClickListener {
            addSelectedStudent()
        }
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }

    override fun onStart() {
        super.onStart()
        loadStudents()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun loadStudents() {
        setLoading(true)
        binding.textEmpty.visibility = View.GONE
        binding.textMessage.visibility = View.GONE

        lifecycleScope.launch {
            val response = AttendanceRepository(serverUrl()).getStudents()
            if (response.success == true) {
                students = response.items.orEmpty()
                showStudents(students)
            } else {
                students = emptyList()
                showStudents(students)
                showMessage(response.message ?: getString(R.string.students_load_failed), isError = true)
            }
            setLoading(false)
        }
    }

    private fun showStudents(items: List<Student>) {
        selectedStudent = null
        binding.buttonAdd.isEnabled = false
        binding.textEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

        val labels = items.map { student ->
            getString(
                R.string.student_select_format,
                student.studentNo ?: "-",
                student.name ?: "-",
                student.department ?: "-"
            )
        }
        binding.listStudents.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_single_choice,
            labels
        )
        binding.listStudents.choiceMode = android.widget.ListView.CHOICE_MODE_SINGLE
        binding.listStudents.setOnItemClickListener { _, _, position, _ ->
            selectedStudent = students.getOrNull(position)
            binding.buttonAdd.isEnabled = selectedStudent != null
        }
    }

    private fun addSelectedStudent() {
        val studentId = selectedStudent?.studentId
        if (studentId == null) {
            showMessage(getString(R.string.student_select_required), isError = true)
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val response = AttendanceRepository(serverUrl()).addStudentToSubject(subjectId(), studentId)
            if (response.success == true) {
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    Bundle().apply { putBoolean(KEY_ADDED, true) }
                )
                dismiss()
            } else {
                showMessage(response.message ?: getString(R.string.add_student_failed), isError = true)
                setLoading(false)
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.listStudents.isEnabled = !isLoading
        binding.buttonCancel.isEnabled = !isLoading
        binding.buttonAdd.isEnabled = !isLoading && selectedStudent != null
    }

    private fun showMessage(message: String, isError: Boolean) {
        binding.textMessage.text = message
        binding.textMessage.visibility = View.VISIBLE
        binding.textMessage.setTextColor(
            requireContext().getColor(if (isError) R.color.yonam_red else R.color.yonam_green)
        )
    }

    private fun serverUrl(): String {
        return ServerConfig.normalizeBaseUrl(requireArguments().getString(ARG_SERVER_URL))
    }

    private fun subjectId(): Int {
        return requireArguments().getInt(ARG_SUBJECT_ID)
    }

    companion object {
        const val TAG = "AddStudentToSubjectDialog"
        const val REQUEST_KEY = "add_student_to_subject_result"
        const val KEY_ADDED = "added"

        private const val ARG_SERVER_URL = "arg_server_url"
        private const val ARG_SUBJECT_ID = "arg_subject_id"

        fun newInstance(serverUrl: String, subjectId: Int): AddStudentToSubjectDialog {
            return AddStudentToSubjectDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_SERVER_URL, serverUrl)
                    putInt(ARG_SUBJECT_ID, subjectId)
                }
            }
        }
    }
}
