package kr.ac.yonam.attendance.ui

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.DialogCreateSessionBinding
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ServerConfig

class CreateSessionDialog : DialogFragment() {
    private var _binding: DialogCreateSessionBinding? = null
    private val binding: DialogCreateSessionBinding
        get() = requireNotNull(_binding)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogCreateSessionBinding.inflate(layoutInflater)

        binding.buttonCreate.setOnClickListener {
            createSession()
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

    private fun createSession() {
        val classDate = binding.editClassDate.text?.toString()?.trim().orEmpty()
        val startTime = binding.editStartTime.text?.toString()?.trim().orEmpty()
        val endTime = binding.editEndTime.text?.toString()?.trim().orEmpty()

        if (classDate.isBlank()) {
            showMessage(getString(R.string.class_date_required), isError = true)
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val response = AttendanceRepository(serverUrl()).createSubjectSession(
                subjectId = subjectId(),
                classDate = classDate,
                startTime = startTime.ifBlank { null },
                endTime = endTime.ifBlank { null }
            )

            if (response.success == true) {
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    Bundle().apply { putBoolean(KEY_CREATED, true) }
                )
                showMessage(getString(R.string.session_created_active), isError = false)
                binding.buttonCreate.isEnabled = false
                binding.buttonCancel.isEnabled = true
                binding.buttonCancel.text = getString(R.string.close)
                binding.progressCreate.visibility = View.GONE
            } else {
                showMessage(response.message ?: getString(R.string.session_create_failed), isError = true)
                setLoading(false)
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressCreate.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.editClassDate.isEnabled = !isLoading
        binding.editStartTime.isEnabled = !isLoading
        binding.editEndTime.isEnabled = !isLoading
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

    private fun subjectId(): Int {
        return requireArguments().getInt(ARG_SUBJECT_ID)
    }

    companion object {
        const val TAG = "CreateSessionDialog"
        const val REQUEST_KEY = "create_session_result"
        const val KEY_CREATED = "created"

        private const val ARG_SERVER_URL = "arg_server_url"
        private const val ARG_SUBJECT_ID = "arg_subject_id"

        fun newInstance(serverUrl: String, subjectId: Int): CreateSessionDialog {
            return CreateSessionDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_SERVER_URL, serverUrl)
                    putInt(ARG_SUBJECT_ID, subjectId)
                }
            }
        }
    }
}
