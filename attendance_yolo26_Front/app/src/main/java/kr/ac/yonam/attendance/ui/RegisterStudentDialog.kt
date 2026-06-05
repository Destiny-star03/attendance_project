package kr.ac.yonam.attendance.ui

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.DialogRegisterStudentBinding
import kr.ac.yonam.attendance.repository.AttendanceRepository
import kr.ac.yonam.attendance.util.ServerConfig

class RegisterStudentDialog : DialogFragment() {
    private var _binding: DialogRegisterStudentBinding? = null
    private val binding: DialogRegisterStudentBinding
        get() = requireNotNull(_binding)

    private var selectedImageUri: Uri? = null

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            binding.imagePreview.setImageURI(uri)
            binding.textSelectedImage.text = "얼굴 사진이 선택되었습니다."
            binding.textMessage.visibility = View.GONE
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogRegisterStudentBinding.inflate(layoutInflater)

        binding.buttonSelectImage.setOnClickListener {
            imagePicker.launch("image/*")
        }
        binding.buttonRegister.setOnClickListener {
            registerStudent()
        }
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("학생 등록")
            .setView(binding.root)
            .create()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun registerStudent() {
        val studentNo = binding.editStudentNo.text?.toString()?.trim().orEmpty()
        val name = binding.editName.text?.toString()?.trim().orEmpty()
        val department = binding.editDepartment.text?.toString()?.trim().orEmpty()
        val imageUri = selectedImageUri

        if (studentNo.isBlank()) {
            showMessage("학번을 입력해 주세요.", isError = true)
            return
        }
        if (name.isBlank()) {
            showMessage("이름을 입력해 주세요.", isError = true)
            return
        }
        if (imageUri == null) {
            showMessage("얼굴 사진을 선택해 주세요.", isError = true)
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val response = AttendanceRepository(serverUrl()).registerStudent(
                    context = requireContext(),
                    studentNo = studentNo,
                    name = name,
                    department = department,
                    imageUri = imageUri
                )

                showMessage(response.message ?: "서버 응답을 확인했습니다.", response.success != true)
                if (response.success == true) {
                    parentFragmentManager.setFragmentResult(
                        REQUEST_KEY,
                        Bundle().apply {
                            putBoolean(KEY_REGISTERED, true)
                            putString(KEY_STUDENT_NO, response.student?.studentNo ?: studentNo)
                            putString(KEY_STUDENT_NAME, response.student?.name ?: name)
                        }
                    )
                    dismiss()
                }
            } catch (error: Exception) {
                showMessage(
                    "학생 등록 중 오류가 발생했습니다.\n${error.message ?: getString(R.string.error_unknown)}",
                    isError = true
                )
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressRegister.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.buttonSelectImage.isEnabled = !isLoading
        binding.buttonRegister.isEnabled = !isLoading
        binding.buttonCancel.isEnabled = !isLoading
        binding.editStudentNo.isEnabled = !isLoading
        binding.editName.isEnabled = !isLoading
        binding.editDepartment.isEnabled = !isLoading
    }

    private fun showMessage(message: String, isError: Boolean) {
        binding.textMessage.text = message
        binding.textMessage.visibility = View.VISIBLE
        binding.textMessage.setTextColor(
            requireContext().getColor(
                if (isError) R.color.yonam_red else R.color.yonam_green
            )
        )
    }

    private fun serverUrl(): String {
        return ServerConfig.normalizeBaseUrl(requireArguments().getString(ARG_SERVER_URL))
    }

    companion object {
        const val TAG = "RegisterStudentDialog"
        const val REQUEST_KEY = "register_student_result"
        const val KEY_REGISTERED = "registered"
        const val KEY_STUDENT_NO = "student_no"
        const val KEY_STUDENT_NAME = "student_name"

        private const val ARG_SERVER_URL = "arg_server_url"

        fun newInstance(serverUrl: String): RegisterStudentDialog {
            return RegisterStudentDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_SERVER_URL, serverUrl)
                }
            }
        }
    }
}
