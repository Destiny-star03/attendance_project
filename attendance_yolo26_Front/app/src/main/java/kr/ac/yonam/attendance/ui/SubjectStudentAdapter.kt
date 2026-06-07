package kr.ac.yonam.attendance.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ItemSubjectStudentBinding
import kr.ac.yonam.attendance.model.Student

class SubjectStudentAdapter(
    private val onRemoveClick: (Student) -> Unit
) : RecyclerView.Adapter<SubjectStudentAdapter.SubjectStudentViewHolder>() {
    private val items = mutableListOf<Student>()

    fun submitList(newItems: List<Student>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectStudentViewHolder {
        val binding = ItemSubjectStudentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SubjectStudentViewHolder(binding, onRemoveClick)
    }

    override fun onBindViewHolder(holder: SubjectStudentViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class SubjectStudentViewHolder(
        private val binding: ItemSubjectStudentBinding,
        private val onRemoveClick: (Student) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(student: Student) {
            val context = binding.root.context
            binding.textStudentNo.text = student.studentNo ?: context.getString(R.string.student_no_empty)
            binding.textStudentName.text = student.name ?: context.getString(R.string.student_name_empty)
            binding.textDepartment.text = student.department ?: context.getString(R.string.student_department_empty)
            binding.buttonRemoveStudent.setOnClickListener {
                onRemoveClick(student)
            }
        }
    }
}
