package kr.ac.yonam.attendance.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ActivitySubjectListBinding
import kr.ac.yonam.attendance.model.Subject
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ServerConfig

class SubjectListActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySubjectListBinding
    private lateinit var adapter: SubjectAdapter

    private val serverUrl: String by lazy {
        ServerConfig.normalizeBaseUrl(intent.getStringExtra(EXTRA_SERVER_URL))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubjectListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = SubjectAdapter { subject ->
            openSubjectDetail(subject)
        }

        binding.recyclerSubjects.layoutManager = LinearLayoutManager(this)
        binding.recyclerSubjects.adapter = adapter

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonRefresh.setOnClickListener { loadSubjects() }
        binding.buttonAddSubject.setOnClickListener {
            AddSubjectDialog.newInstance(serverUrl).show(supportFragmentManager, AddSubjectDialog.TAG)
        }

        supportFragmentManager.setFragmentResultListener(
            AddSubjectDialog.REQUEST_KEY,
            this
        ) { _, bundle ->
            if (bundle.getBoolean(AddSubjectDialog.KEY_CREATED, false)) {
                loadSubjects()
            }
        }

        loadSubjects()
    }

    private fun loadSubjects() {
        setLoading(true)
        binding.textError.visibility = View.GONE
        binding.textEmpty.visibility = View.GONE

        lifecycleScope.launch {
            val response = AttendanceRepository(serverUrl).getSubjects()
            if (response.success == true) {
                showSubjects(response.items.orEmpty())
            } else {
                showError(response.message ?: getString(R.string.subject_list_load_failed))
                showSubjects(emptyList())
            }
            setLoading(false)
        }
    }

    private fun showSubjects(subjects: List<Subject>) {
        adapter.submitList(subjects)
        binding.textSubjectCount.text = getString(R.string.subject_count, subjects.size)
        binding.textEmpty.visibility =
            if (subjects.isEmpty() && binding.textError.visibility != View.VISIBLE) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun openSubjectDetail(subject: Subject) {
        val subjectId = subject.resolvedSubjectId
        if (subjectId == null) {
            showError(getString(R.string.subject_invalid_id))
            return
        }

        startActivity(
            Intent(this, SubjectDetailActivity::class.java)
                .putExtra(SubjectDetailActivity.EXTRA_SERVER_URL, serverUrl)
                .putExtra(SubjectDetailActivity.EXTRA_SUBJECT_ID, subjectId)
        )
    }

    private fun showError(message: String) {
        binding.textError.text = message
        binding.textError.visibility = View.VISIBLE
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonRefresh.isEnabled = !isLoading
        binding.buttonAddSubject.isEnabled = !isLoading
    }

    companion object {
        const val EXTRA_SERVER_URL = "extra_server_url"
    }
}
