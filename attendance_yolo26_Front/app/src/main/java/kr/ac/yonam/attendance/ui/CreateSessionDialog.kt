package kr.ac.yonam.attendance.ui

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.DialogCreateSessionBinding
import kr.ac.yonam.attendance.model.Subject
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ServerConfig
import java.util.Calendar
import java.util.Locale

class CreateSessionDialog : DialogFragment() {
    private var _binding: DialogCreateSessionBinding? = null
    private val binding: DialogCreateSessionBinding
        get() = requireNotNull(_binding)
    private var subject: Subject? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogCreateSessionBinding.inflate(layoutInflater)

        binding.buttonCreate.setOnClickListener {
            createSession()
        }
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }
        binding.editClassDate.setOnClickListener {
            showDatePicker()
        }
        binding.layoutClassDate.setEndIconOnClickListener {
            showDatePicker()
        }
        binding.editStartTime.setOnClickListener {
            showTimePicker(isStartTime = true)
        }
        binding.layoutStartTime.setEndIconOnClickListener {
            showTimePicker(isStartTime = true)
        }
        binding.editEndTime.setOnClickListener {
            showTimePicker(isStartTime = false)
        }
        binding.layoutEndTime.setEndIconOnClickListener {
            showTimePicker(isStartTime = false)
        }
        loadSubjectDefaults()

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
        if (startTime.isBlank()) {
            showMessage(getString(R.string.start_time_required), isError = true)
            return
        }
        if (endTime.isBlank()) {
            showMessage(getString(R.string.end_time_required), isError = true)
            return
        }
        if (timeToMinutes(endTime) <= timeToMinutes(startTime)) {
            showMessage(getString(R.string.end_time_after_start_required), isError = true)
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val repository = AttendanceRepository(serverUrl())
            val currentSubject = subject ?: repository.getSubject(subjectId()).subject
            subject = currentSubject

            if (currentSubject == null) {
                showMessage(getString(R.string.subject_detail_load_failed), isError = true)
                setLoading(false)
                return@launch
            }

            val response = repository.createSubjectSession(
                subjectId = subjectId(),
                classDate = classDate,
                startTime = startTime,
                endTime = endTime,
                classroomId = currentSubject?.classroomId,
                dayOfWeek = currentSubject?.dayOfWeek
            )

            if (response.success == true) {
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY,
                    Bundle().apply {
                        putBoolean(KEY_CREATED, true)
                    }
                )
                showMessage(
                    "수업 세션이 생성되었습니다.",
                    isError = false
                )
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

    private fun showDatePicker() {
        val initial = parseDate(binding.editClassDate.text?.toString()?.trim().orEmpty())
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                binding.editClassDate.setText(formatDate(year, month + 1, dayOfMonth))
                binding.textMessage.visibility = View.GONE
            },
            initial.get(Calendar.YEAR),
            initial.get(Calendar.MONTH),
            initial.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker(isStartTime: Boolean) {
        val target = if (isStartTime) binding.editStartTime else binding.editEndTime
        val initial = parseTime(target.text?.toString()?.trim().orEmpty())

        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                target.setText(formatTime(hourOfDay, minute))
                binding.textMessage.visibility = View.GONE
            },
            initial.first,
            initial.second,
            true
        ).show()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressCreate.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.editClassDate.isEnabled = !isLoading
        binding.editStartTime.isEnabled = !isLoading
        binding.editEndTime.isEnabled = !isLoading
        binding.buttonCreate.isEnabled = !isLoading
        binding.buttonCancel.isEnabled = !isLoading
    }

    private fun loadSubjectDefaults() {
        lifecycleScope.launch {
            val response = AttendanceRepository(serverUrl()).getSubject(subjectId())
            if (response.success == true && response.subject != null) {
                subject = response.subject
                if (binding.editStartTime.text.isNullOrBlank()) {
                    binding.editStartTime.setText(response.subject.startTime.orEmpty())
                }
                if (binding.editEndTime.text.isNullOrBlank()) {
                    binding.editEndTime.setText(response.subject.endTime.orEmpty())
                }
            }
        }
    }

    private fun parseDate(value: String): Calendar {
        val calendar = Calendar.getInstance()
        val parts = value.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size == 3) {
            calendar.set(parts[0], parts[1] - 1, parts[2])
        }
        return calendar
    }

    private fun parseTime(value: String): Pair<Int, Int> {
        val parts = value.split(":").mapNotNull { it.toIntOrNull() }
        return if (parts.size == 2) {
            parts[0].coerceIn(0, 23) to parts[1].coerceIn(0, 59)
        } else {
            val calendar = Calendar.getInstance()
            calendar.get(Calendar.HOUR_OF_DAY) to calendar.get(Calendar.MINUTE)
        }
    }

    private fun formatDate(year: Int, month: Int, day: Int): String {
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
    }

    private fun formatTime(hour: Int, minute: Int): String {
        return String.format(Locale.US, "%02d:%02d", hour, minute)
    }

    private fun timeToMinutes(value: String): Int {
        val (hour, minute) = parseTime(value)
        return hour * 60 + minute
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
