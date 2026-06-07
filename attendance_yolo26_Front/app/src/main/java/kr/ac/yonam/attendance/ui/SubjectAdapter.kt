package kr.ac.yonam.attendance.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ItemSubjectBinding
import kr.ac.yonam.attendance.model.Subject

class SubjectAdapter(
    private val onItemClick: (Subject) -> Unit
) : RecyclerView.Adapter<SubjectAdapter.SubjectViewHolder>() {
    private val items = mutableListOf<Subject>()

    fun submitList(newItems: List<Subject>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
        val binding = ItemSubjectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SubjectViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class SubjectViewHolder(
        private val binding: ItemSubjectBinding,
        private val onItemClick: (Subject) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(subject: Subject) {
            val context = binding.root.context
            binding.textSubjectName.text = subject.subjectName ?: context.getString(R.string.subject_name_empty)
            binding.textProfessorName.text = context.getString(
                R.string.subject_professor_format,
                subject.professorName ?: "-"
            )
            binding.textClassroom.text = context.getString(
                R.string.subject_classroom_format,
                subject.classroom ?: "-"
            )
            binding.root.setOnClickListener { onItemClick(subject) }
            binding.buttonDetail.setOnClickListener { onItemClick(subject) }
        }
    }
}
