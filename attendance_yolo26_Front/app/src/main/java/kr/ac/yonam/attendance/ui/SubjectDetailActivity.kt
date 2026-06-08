package kr.ac.yonam.attendance.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ActivitySubjectDetailBinding
import kr.ac.yonam.attendance.model.Student
import kr.ac.yonam.attendance.model.Subject
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ServerConfig

class SubjectDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySubjectDetailBinding
    private lateinit var adapter: SubjectStudentAdapter

    private val serverUrl: String by lazy {
        ServerConfig.normalizeBaseUrl(intent.getStringExtra(EXTRA_SERVER_URL))
    }

    private val subjectId: Int by lazy {
        intent.getIntExtra(EXTRA_SUBJECT_ID, -1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubjectDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = SubjectStudentAdapter { student ->
            confirmRemoveStudent(student)
        }

        binding.recyclerSubjectStudents.layoutManager = LinearLayoutManager(this)
        binding.recyclerSubjectStudents.adapter = adapter

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonRefresh.setOnClickListener { loadSubjectDetail() }
        binding.buttonAddStudent.setOnClickListener {
            AddStudentToSubjectDialog.newInstance(serverUrl, subjectId)
                .show(supportFragmentManager, AddStudentToSubjectDialog.TAG)
        }
        binding.buttonCreateSession.setOnClickListener {
            CreateSessionDialog.newInstance(serverUrl, subjectId)
                .show(supportFragmentManager, CreateSessionDialog.TAG)
        }

        supportFragmentManager.setFragmentResultListener(
            AddStudentToSubjectDialog.REQUEST_KEY,
            this
        ) { _, bundle ->
            if (bundle.getBoolean(AddStudentToSubjectDialog.KEY_ADDED, false)) {
                loadSubjectDetail()
            }
        }

        supportFragmentManager.setFragmentResultListener(
            CreateSessionDialog.REQUEST_KEY,
            this
        ) { _, bundle ->
            if (bundle.getBoolean(CreateSessionDialog.KEY_CREATED, false)) {
                showMessage(
                    getString(
                        if (bundle.getBoolean(CreateSessionDialog.KEY_ACTIVATED, false)) {
                            R.string.session_created_active
                        } else {
                            R.string.session_created_inactive
                        }
                    ),
                    isError = false
                )
                loadSubjectDetail()
            }
        }

        if (subjectId <= 0) {
            showMessage(getString(R.string.subject_invalid_id), isError = true)
            setActionsEnabled(false)
        } else {
            loadSubjectDetail()
        }
    }

    private fun loadSubjectDetail() {
        setLoading(true)
        binding.textMessage.visibility = View.GONE
        binding.textEmptyStudents.visibility = View.GONE

        lifecycleScope.launch {
            val repository = AttendanceRepository(serverUrl)
            val subjectResponse = repository.getSubject(subjectId)
            val studentsResponse = repository.getSubjectStudents(subjectId)

            if (subjectResponse.success == true && subjectResponse.subject != null) {
                showSubject(subjectResponse.subject)
            } else {
                showSubject(studentsResponse.subject)
                showMessage(
                    subjectResponse.message ?: getString(R.string.subject_detail_load_failed),
                    isError = true
                )
            }

            if (studentsResponse.success == true) {
                showStudents(studentsResponse.items.orEmpty())
            } else {
                showStudents(emptyList())
                showMessage(
                    studentsResponse.message ?: getString(R.string.subject_students_load_failed),
                    isError = true
                )
            }

            setLoading(false)
        }
    }

    private fun showSubject(subject: Subject?) {
        binding.textSubjectName.text = subject?.subjectName ?: getString(R.string.subject_name_empty)
        binding.textProfessorName.text = getString(
            R.string.subject_professor_format,
            subject?.professorName ?: "-"
        )
        binding.textClassroom.text = getString(
            R.string.subject_classroom_format,
            subject?.classroom ?: "-"
        )
    }

    private fun showStudents(students: List<Student>) {
        adapter.submitList(students)
        binding.textStudentCount.text = getString(R.string.subject_student_count, students.size)
        binding.textEmptyStudents.visibility = if (students.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmRemoveStudent(student: Student) {
        val studentId = student.studentId
        if (studentId == null) {
            showMessage(getString(R.string.student_invalid_id), isError = true)
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.remove_student)
            .setMessage(getString(R.string.remove_student_confirm, student.name ?: "-"))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.remove) { _, _ ->
                removeStudent(studentId)
            }
            .show()
    }

    private fun removeStudent(studentId: Int) {
        setLoading(true)
        lifecycleScope.launch {
            val response = AttendanceRepository(serverUrl).removeStudentFromSubject(subjectId, studentId)
            if (response.success == true) {
                showMessage(response.message ?: getString(R.string.remove_student_success), isError = false)
                loadSubjectDetail()
            } else {
                showMessage(response.message ?: getString(R.string.remove_student_failed), isError = true)
                setLoading(false)
            }
        }
    }

    private fun showMessage(message: String, isError: Boolean) {
        binding.textMessage.text = message
        binding.textMessage.visibility = View.VISIBLE
        binding.textMessage.setTextColor(getColor(if (isError) R.color.yonam_red else R.color.yonam_green))
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonRefresh.isEnabled = !isLoading
        binding.buttonAddStudent.isEnabled = !isLoading
        binding.buttonCreateSession.isEnabled = !isLoading
    }

    private fun setActionsEnabled(enabled: Boolean) {
        binding.buttonRefresh.isEnabled = enabled
        binding.buttonAddStudent.isEnabled = enabled
        binding.buttonCreateSession.isEnabled = enabled
    }

    companion object {
        const val EXTRA_SERVER_URL = "extra_server_url"
        const val EXTRA_SUBJECT_ID = "extra_subject_id"
    }
}
