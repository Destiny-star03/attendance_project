package kr.ac.yonam.attendance.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kr.ac.yonam.attendance.databinding.ItemRegisteredStudentBinding
import kr.ac.yonam.attendance.model.Student

class RegisteredStudentAdapter(
    private val onClick: (Student) -> Unit
) : RecyclerView.Adapter<RegisteredStudentAdapter.RegisteredStudentViewHolder>() {
    private val items = mutableListOf<Student>()

    fun submitList(students: List<Student>) {
        items.clear()
        items.addAll(students)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RegisteredStudentViewHolder {
        val binding = ItemRegisteredStudentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RegisteredStudentViewHolder(binding, onClick)
    }

    override fun onBindViewHolder(holder: RegisteredStudentViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class RegisteredStudentViewHolder(
        private val binding: ItemRegisteredStudentBinding,
        private val onClick: (Student) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(student: Student) {
            binding.textStudentName.text = student.name ?: "이름 없음"
            binding.textStudentInfo.text = "${student.studentNo ?: "학번 없음"} / ${student.department ?: "학과 없음"}"
            binding.root.setOnClickListener { onClick(student) }
        }
    }
}
