package kr.ac.yonam.attendance.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ActivityClassroomSelectBinding
import kr.ac.yonam.attendance.model.Classroom
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ClassroomConfig
import kr.ac.yonam.attendance.util.ServerConfig

class ClassroomSelectActivity : AppCompatActivity() {
    private lateinit var binding: ActivityClassroomSelectBinding
    private lateinit var adapter: ClassroomAdapter
    private var currentClassrooms: List<Classroom> = emptyList()
    private var hasLoadedClassrooms: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityClassroomSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ClassroomAdapter { classroom -> selectClassroom(classroom) }
        binding.recyclerClassrooms.layoutManager = LinearLayoutManager(this)
        binding.recyclerClassrooms.adapter = adapter

        binding.buttonRefresh.setOnClickListener { loadClassrooms() }
        binding.buttonAdminTop.setOnClickListener { openAdminPage() }
        binding.buttonEmptyAdmin.setOnClickListener { openAdminPage() }

        updateSelectedClassroom()
        loadClassrooms()
    }

    override fun onResume() {
        super.onResume()
        if (hasLoadedClassrooms) {
            updateSelectionState()
        } else {
            updateSelectedClassroom()
        }
    }

    private fun loadClassrooms() {
        setLoading(true)
        binding.textMessage.visibility = View.GONE
        binding.textEmpty.visibility = View.GONE
        binding.buttonEmptyAdmin.visibility = View.GONE

        lifecycleScope.launch {
            val response = AttendanceRepository(ServerConfig.getBaseUrl(this@ClassroomSelectActivity))
                .getClassrooms()

            if (response.success == true) {
                val classrooms = response.items.orEmpty()
                currentClassrooms = classrooms
                hasLoadedClassrooms = true
                val selectedId = validatedSelectedClassroomId(classrooms)
                adapter.submitList(classrooms, selectedId)
                binding.textEmpty.visibility = if (classrooms.isEmpty()) View.VISIBLE else View.GONE
                binding.buttonEmptyAdmin.visibility = if (classrooms.isEmpty()) View.VISIBLE else View.GONE
            } else {
                hasLoadedClassrooms = false
                adapter.submitList(emptyList(), ClassroomConfig.getSelectedClassroomId(this@ClassroomSelectActivity))
                binding.textEmpty.visibility = View.VISIBLE
                binding.buttonEmptyAdmin.visibility = View.VISIBLE
                showMessage(
                    response.message ?: getString(R.string.classroom_load_failed),
                    isError = true
                )
            }

            setLoading(false)
        }
    }

    private fun selectClassroom(classroom: Classroom) {
        if (classroom.resolvedClassroomId == null) {
            Toast.makeText(this, R.string.classroom_invalid_id, Toast.LENGTH_SHORT).show()
            return
        }

        ClassroomConfig.saveSelectedClassroom(this, classroom)
        adapter.updateSelectedClassroom(classroom.resolvedClassroomId)
        updateSelectedClassroom()
        startActivity(
            Intent(this, RoleSelectActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun openAdminPage() {
        startActivity(
            Intent(this, AdminMainActivity::class.java)
                .putExtra(EXTRA_SERVER_URL, ServerConfig.getBaseUrl(this))
        )
    }

    private fun updateSelectedClassroom() {
        val selectedName = ClassroomConfig.getSelectedClassroomName(this)
        binding.textSelectedClassroom.text = if (selectedName.isNullOrBlank()) {
            getString(R.string.classroom_selected_none)
        } else {
            getString(R.string.classroom_selected_format, selectedName)
        }
    }

    private fun updateSelectionState() {
        val selectedId = validatedSelectedClassroomId(currentClassrooms)
        adapter.updateSelectedClassroom(selectedId)
    }

    private fun validatedSelectedClassroomId(classrooms: List<Classroom>): Int? {
        val selectedId = ClassroomConfig.getSelectedClassroomId(this)
        val isValid = selectedId != null && classrooms.any { it.resolvedClassroomId == selectedId }
        if (!isValid) {
            ClassroomConfig.clearSelectedClassroom(this)
            updateSelectedClassroom()
            return null
        }

        updateSelectedClassroom()
        return selectedId
    }

    private fun showMessage(message: String, isError: Boolean) {
        binding.textMessage.text = message
        binding.textMessage.setTextColor(getColor(if (isError) R.color.yonam_red else R.color.yonam_green))
        binding.textMessage.visibility = View.VISIBLE
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonRefresh.isEnabled = !isLoading
    }

    companion object {
        private const val EXTRA_SERVER_URL = "extra_server_url"
    }
}
