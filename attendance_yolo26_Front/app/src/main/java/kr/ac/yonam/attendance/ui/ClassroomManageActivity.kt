package kr.ac.yonam.attendance.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ActivityClassroomManageBinding
import kr.ac.yonam.attendance.model.Classroom
import kr.ac.yonam.attendance.model.CreateClassroomRequest
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ServerConfig

class ClassroomManageActivity : AppCompatActivity(), ClassroomFormBottomSheetDialogFragment.Listener {
    private lateinit var binding: ActivityClassroomManageBinding
    private lateinit var adapter: ClassroomManageAdapter

    private val serverUrl: String by lazy {
        ServerConfig.normalizeBaseUrl(intent.getStringExtra(EXTRA_SERVER_URL))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClassroomManageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ClassroomManageAdapter(
            onEditClick = { classroom -> showClassroomForm(classroom) },
            onDeleteClick = { classroom -> confirmDelete(classroom) }
        )

        binding.recyclerClassrooms.layoutManager = LinearLayoutManager(this)
        binding.recyclerClassrooms.adapter = adapter

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonRefresh.setOnClickListener { loadClassrooms() }
        binding.buttonAddClassroom.setOnClickListener { showClassroomForm(null) }

        loadClassrooms()
    }

    private fun loadClassrooms() {
        setLoading(true)
        binding.textMessage.visibility = View.GONE
        binding.textEmpty.visibility = View.GONE

        lifecycleScope.launch {
            val response = AttendanceRepository(serverUrl).getClassrooms(activeOnly = true)
            if (response.success == true) {
                val classrooms = response.items.orEmpty()
                adapter.submitList(classrooms)
                binding.textClassroomCount.text = getString(R.string.classroom_count, classrooms.size)
                binding.textEmpty.visibility = if (classrooms.isEmpty()) View.VISIBLE else View.GONE
            } else {
                adapter.submitList(emptyList())
                binding.textClassroomCount.text = getString(R.string.classroom_count, 0)
                showMessage(response.message ?: getString(R.string.classroom_load_failed), isError = true)
            }
            setLoading(false)
        }
    }

    private fun showClassroomForm(classroom: Classroom?) {
        ClassroomFormBottomSheetDialogFragment
            .newInstance(classroom)
            .show(supportFragmentManager, ClassroomFormBottomSheetDialogFragment.TAG)
    }

    override fun onClassroomFormSaveRequested(
        sheet: ClassroomFormBottomSheetDialogFragment,
        classroomId: Int?,
        request: CreateClassroomRequest
    ) {
        lifecycleScope.launch {
            val repository = AttendanceRepository(serverUrl)
            val response = if (classroomId == null) {
                repository.createClassroom(request)
            } else {
                repository.updateClassroom(classroomId, request)
            }

            if (response.success == true) {
                sheet.dismiss()
                showMessage(response.message ?: getString(R.string.classroom_save_success), isError = false)
                loadClassrooms()
            } else {
                sheet.showError(response.message ?: getString(R.string.classroom_save_failed))
            }
        }
    }

    private fun confirmDelete(classroom: Classroom) {
        val classroomId = classroom.resolvedClassroomId
        if (classroomId == null) {
            showMessage(getString(R.string.classroom_invalid_id), isError = true)
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.classroom_deactivate)
            .setMessage(getString(R.string.classroom_delete_confirm, classroom.classroomName ?: "-"))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.classroom_deactivate) { _, _ -> deleteClassroom(classroomId) }
            .show()
    }

    private fun deleteClassroom(classroomId: Int) {
        setLoading(true)
        lifecycleScope.launch {
            val response = AttendanceRepository(serverUrl).deleteClassroom(classroomId)
            if (response.success == true) {
                showMessage(response.message ?: getString(R.string.classroom_delete_success), isError = false)
                loadClassrooms()
            } else {
                showMessage(response.message ?: getString(R.string.classroom_delete_failed), isError = true)
                setLoading(false)
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonRefresh.isEnabled = !isLoading
        binding.buttonAddClassroom.isEnabled = !isLoading
    }

    private fun showMessage(message: String, isError: Boolean) {
        binding.textMessage.text = message
        binding.textMessage.setTextColor(getColor(if (isError) R.color.yonam_red else R.color.yonam_green))
        binding.textMessage.visibility = View.VISIBLE
    }

    companion object {
        const val EXTRA_SERVER_URL = "extra_server_url"
    }
}

