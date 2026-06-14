package kr.ac.yonam.attendance.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ActivityAdminMainBinding
import kr.ac.yonam.attendance.util.ServerConfig

class AdminMainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminMainBinding

    private val serverUrl: String by lazy {
        ServerConfig.normalizeBaseUrl(
            intent.getStringExtra(EXTRA_SERVER_URL) ?: ServerConfig.getBaseUrl(this)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindActions()
    }

    private fun bindActions() {
        binding.buttonManageClassrooms.setOnClickListener {
            startActivity(
                Intent(this, ClassroomManageActivity::class.java)
                    .putExtra(ClassroomManageActivity.EXTRA_SERVER_URL, serverUrl)
            )
        }

        binding.buttonManageSubjects.setOnClickListener {
            openSubjectManagement()
        }

        binding.buttonRegisterStudent.setOnClickListener {
            startActivity(
                Intent(this, RegisterStudentActivity::class.java)
                    .putExtra(RegisterStudentActivity.EXTRA_SERVER_URL, serverUrl)
            )
        }

        binding.buttonServerSetting.setOnClickListener {
            startActivity(Intent(this, ServerSettingActivity::class.java))
        }
    }

    private fun openSubjectManagement() {
        val subjectListActivity = runCatching {
            Class.forName("$packageName.ui.SubjectListActivity")
        }.getOrNull()

        if (subjectListActivity == null) {
            Toast.makeText(
                this,
                getString(R.string.admin_subject_management_not_ready),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        startActivity(
            Intent(this, subjectListActivity)
                .putExtra(SubjectListActivity.EXTRA_SERVER_URL, serverUrl)
        )
    }

    companion object {
        const val EXTRA_SERVER_URL = "extra_server_url"
    }
}
