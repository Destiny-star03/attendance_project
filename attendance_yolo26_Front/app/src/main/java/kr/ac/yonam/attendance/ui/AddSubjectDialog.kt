package kr.ac.yonam.attendance.ui

import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.DialogAddSubjectBinding
import kr.ac.yonam.attendance.model.Classroom
import kr.ac.yonam.attendance.model.CreateClassroomRequest
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ServerConfig
import java.util.Calendar
import java.util.Locale

class AddSubjectDialog : DialogFragment() {
    private var _binding: DialogAddSubjectBinding? = null
    private val binding: DialogAddSubjectBinding
        get() = requireNotNull(_binding)

    private var classrooms: List<Classroom> = emptyList()
    private var selectedClassroom: Classroom? = null
    private var selectedStartTime: String? = null
    private var selectedEndTime: String? = null

    private val dayOptions = listOf(
        "월요일",
        "화요일",
        "수요일",
        "목요일",
        "금요일",
        "토요일",
        "일요일"
    )

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddSubjectBinding.inflate(layoutInflater)

        setupClassroomSpinner(emptyList())
        setupDaySpinner()

        binding.buttonCreate.setOnClickListener {
            createSubject()
        }
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }
        binding.buttonStartTime.setOnClickListener {
            showTimePicker(isStartTime = true)
        }
        binding.buttonEndTime.setOnClickListener {
            showTimePicker(isStartTime = false)
        }
        binding.buttonShowNewClassroom.setOnClickListener {
            toggleNewClassroomForm()
        }
        binding.buttonSaveNewClassroom.setOnClickListener {
            createClassroomFromSubjectDialog()
        }

        loadClassrooms()

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
        val classroomId = selectedClassroom?.resolvedClassroomId
        val classroom = selectedClassroom?.classroomName
        val dayOfWeek = selectedDayOfWeek()
        val startTime = selectedStartTime
        val endTime = selectedEndTime

        if (subjectName.isBlank()) {
            showMessage(getString(R.string.subject_name_required), isError = true)
            return
        }
        if (classroomId == null) {
            showMessage(getString(R.string.classroom_select_required), isError = true)
            return
        }
        if (dayOfWeek.isNullOrBlank()) {
            showMessage(getString(R.string.day_of_week_required), isError = true)
            return
        }
        if (startTime.isNullOrBlank()) {
            showMessage(getString(R.string.start_time_required), isError = true)
            return
        }
        if (endTime.isNullOrBlank()) {
            showMessage(getString(R.string.end_time_required), isError = true)
            return
        }
        if (timeToMinutes(endTime) <= timeToMinutes(startTime)) {
            showMessage(getString(R.string.end_time_after_start_required), isError = true)
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val response = AttendanceRepository(serverUrl()).createSubject(
                subjectName = subjectName,
                professorName = professorName.ifBlank { null },
                classroom = classroom,
                classroomId = classroomId,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime
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

    private fun setupClassroomSpinner(labels: List<String>, selectedClassroomId: Int? = null) {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf(getString(R.string.classroom_select_label)) + labels
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        binding.spinnerClassroom.adapter = adapter
        binding.spinnerClassroom.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedClassroom = if (position > 0) classrooms.getOrNull(position - 1) else null
                binding.textMessage.visibility = View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedClassroom = null
            }
        }

        val selectedPosition = selectedClassroomId
            ?.let { id -> classrooms.indexOfFirst { it.resolvedClassroomId == id } }
            ?.takeIf { it >= 0 }
            ?.plus(1)
            ?: 0
        binding.spinnerClassroom.setSelection(selectedPosition, false)
        selectedClassroom = if (selectedPosition > 0) classrooms.getOrNull(selectedPosition - 1) else null
    }

    private fun setupDaySpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf(getString(R.string.day_of_week_select_label)) + dayOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        binding.spinnerDayOfWeek.adapter = adapter
        binding.spinnerDayOfWeek.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.textMessage.visibility = View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun selectedDayOfWeek(): String? {
        val position = binding.spinnerDayOfWeek.selectedItemPosition
        return if (position > 0) dayOptions.getOrNull(position - 1) else null
    }

    private fun loadClassrooms(selectedClassroomId: Int? = selectedClassroom?.resolvedClassroomId) {
        lifecycleScope.launch {
            val response = AttendanceRepository(serverUrl()).getClassrooms(activeOnly = true)
            classrooms = response.items.orEmpty().filter { it.resolvedClassroomId != null }

            setupClassroomSpinner(classrooms.map { it.toDisplayLabel() }, selectedClassroomId)

            if (response.success != true) {
                showMessage(response.message ?: getString(R.string.classroom_load_failed), isError = true)
            } else if (classrooms.isEmpty()) {
                showMessage(getString(R.string.classroom_manage_empty), isError = true)
            }
        }
    }

    private fun createClassroomFromSubjectDialog() {
        val classroomName = binding.editNewClassroomName.text?.toString()?.trim().orEmpty()
        if (classroomName.isBlank()) {
            showMessage(getString(R.string.classroom_name_required), isError = true)
            return
        }

        setNewClassroomLoading(true)
        lifecycleScope.launch {
            val repository = AttendanceRepository(serverUrl())
            val response = repository.createClassroom(
                CreateClassroomRequest(classroomName = classroomName)
            )

            if (response.success == true) {
                val createdId = response.classroom?.resolvedClassroomId
                val createdName = response.classroom?.classroomName ?: classroomName
                refreshClassroomsAfterCreate(createdId, createdName)
            } else {
                showMessage(response.message ?: getString(R.string.classroom_save_failed), isError = true)
                setNewClassroomLoading(false)
            }
        }
    }

    private suspend fun refreshClassroomsAfterCreate(createdId: Int?, createdName: String) {
        val response = AttendanceRepository(serverUrl()).getClassrooms(activeOnly = true)
        classrooms = response.items.orEmpty().filter { it.resolvedClassroomId != null }
        val fallbackId = classrooms.firstOrNull { it.classroomName == createdName }?.resolvedClassroomId
        val selectedId = createdId ?: fallbackId

        setupClassroomSpinner(classrooms.map { it.toDisplayLabel() }, selectedId)
        setNewClassroomLoading(false)

        if (response.success == true && selectedClassroom != null) {
            binding.layoutNewClassroom.visibility = View.GONE
            binding.editNewClassroomName.text?.clear()
            showMessage(getString(R.string.classroom_save_success), isError = false)
        } else if (response.success != true) {
            showMessage(response.message ?: getString(R.string.classroom_load_failed), isError = true)
        } else {
            showMessage(getString(R.string.classroom_load_failed), isError = true)
        }
    }

    private fun toggleNewClassroomForm() {
        binding.layoutNewClassroom.visibility =
            if (binding.layoutNewClassroom.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        binding.textMessage.visibility = View.GONE
    }

    private fun showTimePicker(isStartTime: Boolean) {
        val initial = parseTime(if (isStartTime) selectedStartTime else selectedEndTime)

        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                val selectedTime = formatTime(hourOfDay, minute)
                if (isStartTime) {
                    selectedStartTime = selectedTime
                    binding.buttonStartTime.text = selectedTime
                } else {
                    selectedEndTime = selectedTime
                    binding.buttonEndTime.text = selectedTime
                }
                binding.textMessage.visibility = View.GONE
            },
            initial.first,
            initial.second,
            true
        ).show()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressCreate.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.editSubjectName.isEnabled = !isLoading
        binding.editProfessorName.isEnabled = !isLoading
        binding.spinnerClassroom.isEnabled = !isLoading
        binding.buttonShowNewClassroom.isEnabled = !isLoading
        binding.editNewClassroomName.isEnabled = !isLoading
        binding.buttonSaveNewClassroom.isEnabled = !isLoading
        binding.spinnerDayOfWeek.isEnabled = !isLoading
        binding.buttonStartTime.isEnabled = !isLoading
        binding.buttonEndTime.isEnabled = !isLoading
        binding.buttonCreate.isEnabled = !isLoading
        binding.buttonCancel.isEnabled = !isLoading
    }

    private fun setNewClassroomLoading(isLoading: Boolean) {
        binding.progressCreate.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.editNewClassroomName.isEnabled = !isLoading
        binding.buttonSaveNewClassroom.isEnabled = !isLoading
        binding.buttonShowNewClassroom.isEnabled = !isLoading
        binding.spinnerClassroom.isEnabled = !isLoading
        binding.buttonCreate.isEnabled = !isLoading
        binding.buttonCancel.isEnabled = !isLoading
    }

    private fun parseTime(value: String?): Pair<Int, Int> {
        val parts = value.orEmpty().split(":").mapNotNull { it.toIntOrNull() }
        return if (parts.size == 2) {
            parts[0].coerceIn(0, 23) to parts[1].coerceIn(0, 59)
        } else {
            val calendar = Calendar.getInstance()
            calendar.get(Calendar.HOUR_OF_DAY) to calendar.get(Calendar.MINUTE)
        }
    }

    private fun formatTime(hour: Int, minute: Int): String {
        return String.format(Locale.US, "%02d:%02d", hour, minute)
    }

    private fun timeToMinutes(value: String): Int {
        val (hour, minute) = parseTime(value)
        return hour * 60 + minute
    }

    private fun Classroom.toDisplayLabel(): String {
        val name = classroomName ?: getString(R.string.classroom_name_empty)
        val meta = listOfNotNull(
            buildingName?.takeIf { it.isNotBlank() },
            floor?.takeIf { it.isNotBlank() }
        ).joinToString(" / ")
        return if (meta.isBlank()) name else "$name ($meta)"
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
