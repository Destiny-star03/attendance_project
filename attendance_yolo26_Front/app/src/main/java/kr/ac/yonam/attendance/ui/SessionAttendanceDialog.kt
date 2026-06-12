package kr.ac.yonam.attendance.ui

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.DialogSessionAttendanceBinding
import kr.ac.yonam.attendance.model.AttendanceItem
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ServerConfig

class SessionAttendanceDialog : DialogFragment() {
    private var _binding: DialogSessionAttendanceBinding? = null
    private val binding: DialogSessionAttendanceBinding
        get() = requireNotNull(_binding)
    private lateinit var adapter: SessionAttendanceStudentAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSessionAttendanceBinding.inflate(layoutInflater)
        adapter = SessionAttendanceStudentAdapter { item ->
            showStatusPicker(item)
        }
        binding.recyclerAttendanceStudents.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerAttendanceStudents.adapter = adapter
        binding.textTitle.text = subjectName()
        binding.textSessionMeta.text = "${classDate()} / ${startTime()} - ${endTime()}"
        binding.buttonClose.setOnClickListener { dismiss() }
        loadStudents()

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun loadStudents() {
        setLoading(true)
        lifecycleScope.launch {
            val response = AttendanceRepository(serverUrl()).getSessionAttendanceStudents(sessionId())
            if (response.success == true) {
                adapter.submitList(response.items.orEmpty())
                showMessage(response.message ?: "세션 출석 학생 목록을 불러왔습니다.", isError = false)
            } else {
                adapter.submitList(emptyList())
                showMessage(response.message ?: "세션 출석 학생 목록을 불러오지 못했습니다.", isError = true)
            }
            setLoading(false)
        }
    }

    private fun showStatusPicker(item: AttendanceItem) {
        val studentId = item.studentId
        if (studentId == null) {
            showMessage("학생 ID가 없어 출석 상태를 변경할 수 없습니다.", isError = true)
            return
        }

        val statuses = arrayOf(
            SessionAttendanceStudentAdapter.STATUS_PRESENT,
            SessionAttendanceStudentAdapter.STATUS_LATE,
            SessionAttendanceStudentAdapter.STATUS_ABSENT,
            SessionAttendanceStudentAdapter.STATUS_PENDING
        )
        val labels = statuses.map { SessionAttendanceStudentAdapter.statusText(it) }.toTypedArray()
        val currentStatus = SessionAttendanceStudentAdapter.normalizeStatus(item.status)
        val checkedIndex = statuses.indexOf(currentStatus).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(item.name ?: "학생")
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                dialog.dismiss()
                updateStatus(studentId, statuses[which])
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun updateStatus(studentId: Int, status: String) {
        setLoading(true)
        lifecycleScope.launch {
            val response = AttendanceRepository(serverUrl()).updateSessionAttendanceStatus(
                sessionId = sessionId(),
                studentId = studentId,
                status = status
            )
            if (response.success == true) {
                showMessage(response.message ?: "출석 상태가 변경되었습니다.", isError = false)
                loadStudents()
            } else {
                showMessage(response.message ?: "출석 상태를 변경하지 못했습니다.", isError = true)
                setLoading(false)
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun showMessage(message: String, isError: Boolean) {
        binding.textMessage.text = message
        binding.textMessage.setTextColor(
            requireContext().getColor(if (isError) R.color.yonam_red else R.color.text_secondary)
        )
        binding.textMessage.visibility = View.VISIBLE
    }

    private fun serverUrl(): String {
        return ServerConfig.normalizeBaseUrl(requireArguments().getString(ARG_SERVER_URL))
    }

    private fun sessionId(): Int {
        return requireArguments().getInt(ARG_SESSION_ID)
    }

    private fun subjectName(): String {
        return requireArguments().getString(ARG_SUBJECT_NAME).orEmpty().ifBlank { "세션 출석 관리" }
    }

    private fun classDate(): String {
        return requireArguments().getString(ARG_CLASS_DATE).orEmpty().ifBlank { "-" }
    }

    private fun startTime(): String {
        return requireArguments().getString(ARG_START_TIME).orEmpty().ifBlank { "-" }
    }

    private fun endTime(): String {
        return requireArguments().getString(ARG_END_TIME).orEmpty().ifBlank { "-" }
    }

    companion object {
        const val TAG = "SessionAttendanceDialog"

        private const val ARG_SERVER_URL = "arg_server_url"
        private const val ARG_SESSION_ID = "arg_session_id"
        private const val ARG_SUBJECT_NAME = "arg_subject_name"
        private const val ARG_CLASS_DATE = "arg_class_date"
        private const val ARG_START_TIME = "arg_start_time"
        private const val ARG_END_TIME = "arg_end_time"

        fun newInstance(
            serverUrl: String,
            sessionId: Int,
            subjectName: String?,
            classDate: String?,
            startTime: String?,
            endTime: String?
        ): SessionAttendanceDialog {
            return SessionAttendanceDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_SERVER_URL, serverUrl)
                    putInt(ARG_SESSION_ID, sessionId)
                    putString(ARG_SUBJECT_NAME, subjectName)
                    putString(ARG_CLASS_DATE, classDate)
                    putString(ARG_START_TIME, startTime)
                    putString(ARG_END_TIME, endTime)
                }
            }
        }
    }
}
