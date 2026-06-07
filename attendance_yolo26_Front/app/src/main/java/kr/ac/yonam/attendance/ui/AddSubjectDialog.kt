package kr.ac.yonam.attendance.ui

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.DialogAddSubjectBinding
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ServerConfig

class AddSubjectDialog : DialogFragment() {
    private var _binding: DialogAddSubjectBinding? = null
    private val binding: DialogAddSubjectBinding
        get() = requireNotNull(_binding)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddSubjectBinding.inflate(layoutInflater)

        binding.buttonCreate.setOnClickListener {
            createSubject()
        }
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun createSubject() {
        val subjectName = binding.editSubjectName.text?.toString()?.trim().orEmpty()
        val professorName = binding.editProfessorName.text?.toString()?.trim().orEmpty()
        val classroom = binding.editClassroom.text?.toString()?.trim().orEmpty()

        if (subjectName.isBlank()) {
            showMessage(getString(R.string.subject_name_required), isError = true)
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val response = AttendanceRepository(serverUrl()).createSubject(
                subjectName = subjectName,
                professorName = professorName.ifBlank { null },
                classroom = classroom.ifBlank { null }
            )

            if (response.success == true) {
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    Bundle().apply { putBoolean(KEY_CREATED, true) }
                )
                dismiss()
            } else {
                showMessage(response.message ?: getString(R.string.subject_create_failed), isError = true)
                setLoading(false)
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressCreate.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.editSubjectName.isEnabled = !isLoading
        binding.editProfessorName.isEnabled = !isLoading
        binding.editClassroom.isEnabled = !isLoading
        binding.buttonCreate.isEnabled = !isLoading
        binding.buttonCancel.isEnabled = !isLoading
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

    companion object {
        const val TAG = "AddSubjectDialog"
        const val REQUEST_KEY = "add_subject_result"
        const val KEY_CREATED = "created"

        private const val ARG_SERVER_URL = "arg_server_url"

        fun newInstance(serverUrl: String): AddSubjectDialog {
            return AddSubjectDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_SERVER_URL, serverUrl)
                }
            }
        }
    }
}
