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
import kr.ac.yonam.attendance.model.Session
import kr.ac.yonam.attendance.model.Student
import kr.ac.yonam.attendance.model.Subject
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ServerConfig

class SubjectDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySubjectDetailBinding
    private lateinit var studentAdapter: SubjectStudentAdapter
    private lateinit var sessionAdapter: SubjectSessionAdapter
    private var selectedTab: DetailTab = DetailTab.SESSIONS

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

        studentAdapter = SubjectStudentAdapter { student ->
            confirmRemoveStudent(student)
        }
        sessionAdapter = SubjectSessionAdapter(
            onSessionClick = { session -> openSessionAttendance(session) },
            onDeleteClick = { session -> confirmDeleteSession(session) }
        )

        binding.recyclerSubjectSessions.layoutManager = LinearLayoutManager(this)
        binding.recyclerSubjectSessions.adapter = sessionAdapter
        binding.recyclerSubjectStudents.layoutManager = LinearLayoutManager(this)
        binding.recyclerSubjectStudents.adapter = studentAdapter

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonRefresh.setOnClickListener { loadSubjectDetail() }
        binding.buttonTabSessions.setOnClickListener { selectTab(DetailTab.SESSIONS) }
        binding.buttonTabStudents.setOnClickListener { selectTab(DetailTab.STUDENTS) }
        binding.buttonAddStudent.setOnClickListener {
            AddStudentToSubjectDialog.newInstance(serverUrl, subjectId)
                .show(supportFragmentManager, AddStudentToSubjectDialog.TAG)
        }
        binding.buttonCreateSession.setOnClickListener {
            CreateSessionDialog.newInstance(serverUrl, subjectId)
                .show(supportFragmentManager, CreateSessionDialog.TAG)
        }
        binding.buttonDeleteSubject.setOnClickListener {
            confirmDeleteSubject()
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
                showMessage("수업 세션이 생성되었습니다.", isError = false)
                selectedTab = DetailTab.SESSIONS
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
        binding.textEmptySessions.visibility = View.GONE

        lifecycleScope.launch {
            val repository = AttendanceRepository(serverUrl)
            val subjectResponse = repository.getSubject(subjectId)
            val sessionsResponse = repository.getSubjectSessions(subjectId)
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

            if (sessionsResponse.success == true) {
                showSessions(sessionsResponse.items.orEmpty())
            } else {
                showSessions(emptyList())
                showMessage(
                    sessionsResponse.message ?: "수업 세션 목록을 불러오지 못했습니다.",
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

            selectTab(selectedTab)
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
        studentAdapter.submitList(students)
        binding.textStudentCount.text = getString(R.string.subject_student_count, students.size)
        binding.textEmptyStudents.visibility =
            if (selectedTab == DetailTab.STUDENTS && students.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showSessions(sessions: List<Session>) {
        sessionAdapter.submitSessions(sessions)
        binding.textEmptySessions.visibility =
            if (selectedTab == DetailTab.SESSIONS && sessions.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun selectTab(tab: DetailTab) {
        selectedTab = tab
        val showSessions = tab == DetailTab.SESSIONS

        binding.recyclerSubjectSessions.visibility = if (showSessions) View.VISIBLE else View.GONE
        binding.textEmptySessions.visibility =
            if (showSessions && sessionAdapter.itemCount == 0) View.VISIBLE else View.GONE

        binding.recyclerSubjectStudents.visibility = if (showSessions) View.GONE else View.VISIBLE
        binding.textEmptyStudents.visibility =
            if (!showSessions && studentAdapter.itemCount == 0) View.VISIBLE else View.GONE

        binding.buttonCreateSession.visibility = if (showSessions) View.VISIBLE else View.GONE
        binding.buttonAddStudent.visibility = if (showSessions) View.GONE else View.VISIBLE

        binding.buttonTabSessions.setTextColor(getColor(if (showSessions) android.R.color.white else R.color.text_primary))
        binding.buttonTabSessions.backgroundTintList = android.content.res.ColorStateList.valueOf(
            getColor(if (showSessions) R.color.yonam_deep_red else R.color.card_surface)
        )
        binding.buttonTabStudents.setTextColor(getColor(if (showSessions) R.color.text_primary else android.R.color.white))
        binding.buttonTabStudents.backgroundTintList = android.content.res.ColorStateList.valueOf(
            getColor(if (showSessions) R.color.card_surface else R.color.yonam_deep_red)
        )
    }

    private fun openSessionAttendance(session: Session) {
        val sessionId = session.resolvedSessionId
        if (sessionId == null) {
            showMessage("세션 ID가 없어 출석 학생 목록을 열 수 없습니다.", isError = true)
            return
        }

        SessionAttendanceDialog.newInstance(
            serverUrl = serverUrl,
            sessionId = sessionId,
            subjectName = session.subjectName,
            classDate = session.classDate,
            startTime = session.startTime,
            endTime = session.endTime
        ).show(supportFragmentManager, SessionAttendanceDialog.TAG)
    }

    private fun confirmDeleteSession(session: Session) {
        val sessionId = session.resolvedSessionId
        if (sessionId == null) {
            showMessage("세션 ID가 없어 삭제할 수 없습니다.", isError = true)
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("수업 세션 삭제")
            .setMessage("${session.classDate ?: "-"} ${session.startTime ?: "-"} - ${session.endTime ?: "-"} 세션을 삭제하시겠습니까?")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteSession(sessionId)
            }
            .show()
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

    private fun confirmDeleteSubject() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.subject_delete_confirm))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteSubject()
            }
            .show()
    }

    private fun deleteSession(sessionId: Int) {
        setLoading(true)
        lifecycleScope.launch {
            val response = AttendanceRepository(serverUrl).deleteSession(sessionId)
            if (response.success == true || response.deleted == true) {
                showMessage(response.message ?: "수업 세션이 삭제되었습니다.", isError = false)
                selectedTab = DetailTab.SESSIONS
                loadSubjectDetail()
            } else {
                showMessage(response.message ?: "수업 세션을 삭제하지 못했습니다.", isError = true)
                setLoading(false)
            }
        }
    }

    private fun deleteSubject() {
        setLoading(true)
        lifecycleScope.launch {
            val response = AttendanceRepository(serverUrl).deleteSubject(subjectId)
            if (response.success == true || response.deleted == true) {
                finish()
            } else {
                showMessage(response.message ?: getString(R.string.subject_delete_failed), isError = true)
                setLoading(false)
            }
        }
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
        binding.buttonDeleteSubject.isEnabled = !isLoading
    }

    private fun setActionsEnabled(enabled: Boolean) {
        binding.buttonRefresh.isEnabled = enabled
        binding.buttonAddStudent.isEnabled = enabled
        binding.buttonCreateSession.isEnabled = enabled
        binding.buttonDeleteSubject.isEnabled = enabled
    }

    companion object {
        const val EXTRA_SERVER_URL = "extra_server_url"
        const val EXTRA_SUBJECT_ID = "extra_subject_id"
    }

    private enum class DetailTab {
        SESSIONS,
        STUDENTS
    }
}
