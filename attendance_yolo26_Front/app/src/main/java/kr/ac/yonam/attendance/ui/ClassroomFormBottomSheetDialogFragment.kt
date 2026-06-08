package kr.ac.yonam.attendance.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.DialogClassroomFormBinding
import kr.ac.yonam.attendance.model.Classroom
import kr.ac.yonam.attendance.model.CreateClassroomRequest

class ClassroomFormBottomSheetDialogFragment : BottomSheetDialogFragment() {
    interface Listener {
        fun onClassroomFormSaveRequested(
            sheet: ClassroomFormBottomSheetDialogFragment,
            classroomId: Int?,
            request: CreateClassroomRequest
        )
    }

    private var _binding: DialogClassroomFormBinding? = null
    private val binding: DialogClassroomFormBinding
        get() = _binding ?: error("Dialog binding is only valid between onCreateView and onDestroyView")

    private val classroomId: Int?
        get() = arguments
            ?.getInt(ARG_CLASSROOM_ID, NO_CLASSROOM_ID)
            ?.takeIf { it != NO_CLASSROOM_ID }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogClassroomFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isEdit = arguments?.getBoolean(ARG_IS_EDIT) == true
        binding.textDialogTitle.text = getString(
            if (isEdit) R.string.classroom_edit else R.string.classroom_add
        )
        binding.editClassroomName.setText(arguments?.getString(ARG_CLASSROOM_NAME).orEmpty())
        binding.editBuildingName.setText(arguments?.getString(ARG_BUILDING_NAME).orEmpty())
        binding.editFloor.setText(arguments?.getString(ARG_FLOOR).orEmpty(), false)
        binding.editDescription.setText(arguments?.getString(ARG_DESCRIPTION).orEmpty())
        binding.buttonSave.text = getString(
            if (isEdit) R.string.classroom_update_save else R.string.save
        )

        setupFloorDropdown()
        binding.buttonCancel.setOnClickListener { dismiss() }
        binding.buttonSave.setOnClickListener { submitForm() }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val bottomSheetDialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = bottomSheetDialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return

        bottomSheet.post {
            val screenHeight = resources.displayMetrics.heightPixels
            val expandedHeight = (screenHeight * EXPANDED_HEIGHT_RATIO).toInt()
            val peekHeight = (screenHeight * PEEK_HEIGHT_RATIO).toInt()

            bottomSheet.layoutParams = bottomSheet.layoutParams.apply {
                height = expandedHeight
            }

            val behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.isDraggable = true
            behavior.peekHeight = peekHeight
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    fun showError(message: String) {
        val currentBinding = _binding ?: return
        setLoading(false)
        currentBinding.textMessage.text = message
        currentBinding.textMessage.visibility = View.VISIBLE
        currentBinding.formScroll.post {
            currentBinding.formScroll.smoothScrollTo(0, currentBinding.textMessage.bottom)
        }
    }

    private fun submitForm() {
        val classroomName = binding.editClassroomName.text?.toString()?.trim().orEmpty()
        val buildingName = binding.editBuildingName.text?.toString()?.trim().orEmpty()
        val floor = binding.editFloor.text?.toString()?.trim().orEmpty()
        val description = binding.editDescription.text?.toString()?.trim().orEmpty()

        if (classroomName.isBlank()) {
            showError(getString(R.string.classroom_name_required))
            return
        }

        val request = CreateClassroomRequest(
            classroomName = classroomName,
            buildingName = buildingName.ifBlank { null },
            floor = floor.ifBlank { null },
            description = description.ifBlank { null }
        )

        setLoading(true)
        val listener = activity as? Listener
        if (listener == null) {
            showError(getString(R.string.classroom_save_failed))
            return
        }
        listener.onClassroomFormSaveRequested(this, classroomId, request)
    }

    private fun setupFloorDropdown() {
        val floors = listOf("B1", "1층", "2층", "3층", "4층", "5층", "6층", "7층", "8층", "9층", "10층")
        binding.editFloor.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_list_item_1,
                floors
            )
        )
    }

    private fun setLoading(isLoading: Boolean) {
        val currentBinding = _binding ?: return
        currentBinding.progressSave.visibility = if (isLoading) View.VISIBLE else View.GONE
        currentBinding.editClassroomName.isEnabled = !isLoading
        currentBinding.editBuildingName.isEnabled = !isLoading
        currentBinding.editFloor.isEnabled = !isLoading
        currentBinding.editDescription.isEnabled = !isLoading
        currentBinding.buttonSave.isEnabled = !isLoading
        currentBinding.buttonCancel.isEnabled = !isLoading
    }

    companion object {
        const val TAG = "ClassroomFormBottomSheet"
        private const val ARG_IS_EDIT = "arg_is_edit"
        private const val ARG_CLASSROOM_ID = "arg_classroom_id"
        private const val ARG_CLASSROOM_NAME = "arg_classroom_name"
        private const val ARG_BUILDING_NAME = "arg_building_name"
        private const val ARG_FLOOR = "arg_floor"
        private const val ARG_DESCRIPTION = "arg_description"
        private const val NO_CLASSROOM_ID = -1
        private const val EXPANDED_HEIGHT_RATIO = 0.9f
        private const val PEEK_HEIGHT_RATIO = 0.7f

        fun newInstance(classroom: Classroom?): ClassroomFormBottomSheetDialogFragment {
            return ClassroomFormBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_IS_EDIT, classroom != null)
                    putInt(ARG_CLASSROOM_ID, classroom?.resolvedClassroomId ?: NO_CLASSROOM_ID)
                    putString(ARG_CLASSROOM_NAME, classroom?.classroomName.orEmpty())
                    putString(ARG_BUILDING_NAME, classroom?.buildingName.orEmpty())
                    putString(ARG_FLOOR, classroom?.floor.orEmpty())
                    putString(ARG_DESCRIPTION, classroom?.description.orEmpty())
                }
            }
        }
    }
}
