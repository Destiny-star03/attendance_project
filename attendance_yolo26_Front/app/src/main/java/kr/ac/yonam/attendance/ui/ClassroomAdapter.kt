package kr.ac.yonam.attendance.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ItemClassroomBinding
import kr.ac.yonam.attendance.model.Classroom

class ClassroomAdapter(
    private val onItemClick: (Classroom) -> Unit
) : RecyclerView.Adapter<ClassroomAdapter.ClassroomViewHolder>() {
    private val items = mutableListOf<Classroom>()
    private var selectedClassroomId: Int? = null

    fun submitList(newItems: List<Classroom>, selectedId: Int?) {
        items.clear()
        items.addAll(newItems)
        selectedClassroomId = selectedId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassroomViewHolder {
        val binding = ItemClassroomBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ClassroomViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ClassroomViewHolder, position: Int) {
        holder.bind(items[position], selectedClassroomId)
    }

    override fun getItemCount(): Int = items.size

    class ClassroomViewHolder(
        private val binding: ItemClassroomBinding,
        private val onItemClick: (Classroom) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(classroom: Classroom, selectedClassroomId: Int?) {
            val context = binding.root.context
            val classroomId = classroom.resolvedClassroomId
            val isSelected = classroomId != null && classroomId == selectedClassroomId

            binding.textClassroomName.text =
                classroom.classroomName ?: context.getString(R.string.classroom_name_empty)
            binding.textBuildingName.text = context.getString(
                R.string.classroom_building_format,
                classroom.buildingName ?: "-"
            )
            binding.textFloor.text = context.getString(
                R.string.classroom_floor_format,
                classroom.floor?.toString() ?: "-"
            )
            binding.buttonSelect.text = context.getString(
                if (isSelected) R.string.classroom_selected else R.string.classroom_select
            )
            binding.buttonSelect.isEnabled = !isSelected
            binding.root.strokeColor = context.getColor(
                if (isSelected) R.color.yonam_deep_red else R.color.divider
            )
            binding.root.strokeWidth = if (isSelected) 2 else 1

            binding.root.setOnClickListener { onItemClick(classroom) }
            binding.buttonSelect.setOnClickListener { onItemClick(classroom) }
        }
    }
}
