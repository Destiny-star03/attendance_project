package kr.ac.yonam.attendance.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ItemAttendanceBinding
import kr.ac.yonam.attendance.model.AttendanceItem

class AttendanceAdapter(
    private val onItemClick: (AttendanceItem) -> Unit
) : RecyclerView.Adapter<AttendanceAdapter.AttendanceViewHolder>() {
    private val items = mutableListOf<AttendanceItem>()

    fun submitList(newItems: List<AttendanceItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttendanceViewHolder {
        val binding = ItemAttendanceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AttendanceViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: AttendanceViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class AttendanceViewHolder(
        private val binding: ItemAttendanceBinding,
        private val onItemClick: (AttendanceItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AttendanceItem) {
            binding.textStudentNo.text = item.studentNo ?: "학번 없음"
            binding.textStudentName.text = item.name ?: "이름 없음"
            binding.textDepartment.text = item.department ?: "학과 없음"
            binding.textAttendanceTime.text = "출석 시간: ${item.attendanceTime ?: "-"}"
            binding.textAttendanceStatus.text = statusText(item.status)
            binding.textAttendanceStatus.setTextColor(statusColor(item.status))
            binding.root.setOnClickListener {
                onItemClick(item)
            }
        }

        private fun statusText(status: String?): String {
            return when (status) {
                "recognizing" -> "인식중"
                "present", "attended" -> "출석완료"
                "already_attended" -> "이미출석"
                "late" -> "지각"
                "absent" -> "결석"
                "pending", null, "" -> "출석전"
                else -> status
            }
        }

        private fun statusColor(status: String?): Int {
            val colorResId = when (status) {
                "recognizing", "already_attended" -> R.color.yonam_blue
                "present", "attended" -> R.color.yonam_green
                "late", "absent" -> R.color.yonam_red
                else -> R.color.text_secondary
            }
            return ContextCompat.getColor(binding.root.context, colorResId)
        }
    }
}
