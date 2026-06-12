package kr.ac.yonam.attendance.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ItemSessionAttendanceStudentBinding
import kr.ac.yonam.attendance.model.AttendanceItem

class SessionAttendanceStudentAdapter(
    private val onStatusClick: (AttendanceItem) -> Unit
) : RecyclerView.Adapter<SessionAttendanceStudentAdapter.ViewHolder>() {
    private val items = mutableListOf<AttendanceItem>()

    fun submitList(newItems: List<AttendanceItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemSessionAttendanceStudentBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ),
            onStatusClick
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(
        private val binding: ItemSessionAttendanceStudentBinding,
        private val onStatusClick: (AttendanceItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AttendanceItem) {
            val status = normalizeStatus(item.status)
            val statusText = statusText(status)
            val time = item.attendanceTime?.takeIf { it.isNotBlank() } ?: "-"
            binding.textStudentName.text = item.name ?: "이름 없음"
            binding.textStudentMeta.text = "${item.studentNo ?: "학번 없음"} / ${item.department ?: "학과 없음"}"
            binding.textAttendanceStatus.text = "$statusText / 출석 시간: $time"
            binding.textAttendanceStatus.setTextColor(statusColor(status))
            binding.buttonChangeStatus.setOnClickListener { onStatusClick(item) }
        }

        private fun statusColor(status: String): Int {
            val colorResId = when (status) {
                STATUS_PRESENT -> R.color.yonam_green
                STATUS_LATE -> R.color.yonam_blue
                STATUS_ABSENT -> R.color.yonam_red
                else -> R.color.text_secondary
            }
            return ContextCompat.getColor(binding.root.context, colorResId)
        }
    }

    companion object {
        const val STATUS_PRESENT = "present"
        const val STATUS_LATE = "late"
        const val STATUS_ABSENT = "absent"
        const val STATUS_PENDING = "pending"

        fun normalizeStatus(status: String?): String {
            return when (status) {
                "attended", "already_attended", "success", "checked_in" -> STATUS_PRESENT
                STATUS_PRESENT, STATUS_LATE, STATUS_ABSENT -> status
                else -> STATUS_PENDING
            }
        }

        fun statusText(status: String): String {
            return when (status) {
                STATUS_PRESENT -> "출석"
                STATUS_LATE -> "지각"
                STATUS_ABSENT -> "결석"
                else -> "미출석"
            }
        }
    }
}
