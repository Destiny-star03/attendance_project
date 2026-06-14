package kr.ac.yonam.attendance.ui

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
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
    private val selectedStudentIds = linkedSetOf<Int>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddStudentToSubjectBinding.inflate(layoutInflater)

        binding.buttonAdd.setOnClickListener {
            addSelectedStudents()
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
            val repository = AttendanceRepository(serverUrl())
            val studentsResponse = repository.getStudents()
            val subjectStudentsResponse = repository.getSubjectStudents(subjectId())

            if (studentsResponse.success == true) {
                val enrolledIds = subjectStudentsResponse.items
                    .orEmpty()
                    .mapNotNull { it.studentId }
                    .toSet()
                students = studentsResponse.items
                    .orEmpty()
                    .filter { student ->
                        val studentId = student.studentId
                        studentId != null && studentId !in enrolledIds
                    }
                showStudents(students)

                if (subjectStudentsResponse.success != true) {
                    showMessage(
                        subjectStudentsResponse.message ?: getString(R.string.subject_students_load_failed),
                        isError = true
                    )
                }
            } else {
                students = emptyList()
                showStudents(students)
                showMessage(studentsResponse.message ?: getString(R.string.students_load_failed), isError = true)
            }
            setLoading(false)
        }
    }

    private fun showStudents(items: List<Student>) {
        selectedStudentIds.clear()
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
            android.R.layout.simple_list_item_multiple_choice,
            labels
        )
        binding.listStudents.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        binding.listStudents.clearChoices()
        binding.listStudents.setOnItemClickListener { _, _, position, _ ->
            val studentId = students.getOrNull(position)?.studentId ?: return@setOnItemClickListener
            if (binding.listStudents.isItemChecked(position)) {
                selectedStudentIds.add(studentId)
            } else {
                selectedStudentIds.remove(studentId)
            }
            binding.buttonAdd.isEnabled = selectedStudentIds.isNotEmpty()
        }
    }

    private fun addSelectedStudents() {
        val ids = selectedStudentIds.toList()
        if (ids.isEmpty()) {
            showMessage(getString(R.string.student_select_required), isError = true)
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val repository = AttendanceRepository(serverUrl())
            var successCount = 0
            val failedMessages = mutableListOf<String>()

            ids.forEach { studentId ->
                val response = repository.addStudentToSubject(subjectId(), studentId)
                if (response.success == true) {
                    successCount += 1
                } else {
                    failedMessages.add(response.message ?: getString(R.string.add_student_failed))
                }
            }

            if (successCount > 0 && failedMessages.isEmpty()) {
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    Bundle().apply { putBoolean(KEY_ADDED, true) }
                )
                dismiss()
            } else if (successCount > 0) {
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    Bundle().apply { putBoolean(KEY_ADDED, true) }
                )
                showMessage(
                    "${successCount}명 추가 완료, ${failedMessages.size}명 추가 실패\n${failedMessages.first()}",
                    isError = true
                )
                loadStudents()
            } else {
                showMessage(
                    failedMessages.firstOrNull() ?: getString(R.string.add_student_failed),
                    isError = true
                )
                setLoading(false)
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.listStudents.isEnabled = !isLoading
        binding.buttonCancel.isEnabled = !isLoading
        binding.buttonAdd.isEnabled = !isLoading && selectedStudentIds.isNotEmpty()
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
