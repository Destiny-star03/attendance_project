package kr.ac.yonam.attendance.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ItemClassroomManageBinding
import kr.ac.yonam.attendance.model.Classroom

class ClassroomManageAdapter(
    private val onEditClick: (Classroom) -> Unit,
    private val onDeleteClick: (Classroom) -> Unit
) : RecyclerView.Adapter<ClassroomManageAdapter.ClassroomViewHolder>() {
    private val items = mutableListOf<Classroom>()

    fun submitList(newItems: List<Classroom>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassroomViewHolder {
        val binding = ItemClassroomManageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ClassroomViewHolder(binding, onEditClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: ClassroomViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ClassroomViewHolder(
        private val binding: ItemClassroomManageBinding,
        private val onEditClick: (Classroom) -> Unit,
        private val onDeleteClick: (Classroom) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(classroom: Classroom) {
            val context = binding.root.context
            val name = classroom.classroomName ?: context.getString(R.string.classroom_name_empty)
            val building = classroom.buildingName ?: "-"
            val floor = classroom.floor?.toString() ?: "-"
            val activeText = if (classroom.isActive == false) {
                context.getString(R.string.session_inactive)
            } else {
                context.getString(R.string.session_active)
            }

            binding.textClassroomName.text = name
            binding.textClassroomMeta.text = context.getString(
                R.string.classroom_manage_meta,
                building,
                floor,
                activeText
            )
            binding.textClassroomDescription.text = classroom.description.orEmpty()
            binding.textClassroomDescription.visibility =
                if (classroom.description.isNullOrBlank()) {
                    android.view.View.GONE
                } else {
                    android.view.View.VISIBLE
                }

            binding.root.setOnClickListener { onEditClick(classroom) }
            binding.buttonEdit.setOnClickListener { onEditClick(classroom) }
            binding.buttonDelete.setOnClickListener { onDeleteClick(classroom) }
        }
    }
}
