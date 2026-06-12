package kr.ac.yonam.attendance.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import kr.ac.yonam.attendance.R
import kr.ac.yonam.attendance.databinding.ItemSubjectSessionBinding
import kr.ac.yonam.attendance.databinding.ItemSubjectSessionDateHeaderBinding
import kr.ac.yonam.attendance.model.Session

class SubjectSessionAdapter(
    private val onSessionClick: (Session) -> Unit,
    private val onDeleteClick: (Session) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = mutableListOf<Row>()

    fun submitSessions(sessions: List<Session>) {
        items.clear()
        sessions
            .sortedWith(
                compareByDescending<Session> { it.classDate.orEmpty() }
                    .thenBy { it.startTime.orEmpty() }
            )
            .groupBy { it.classDate.orEmpty().ifBlank { "날짜 없음" } }
            .forEach { (date, dateSessions) ->
                items.add(Row.Header(date))
                dateSessions.forEach { items.add(Row.Item(it)) }
            }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is Row.Header -> VIEW_TYPE_HEADER
            is Row.Item -> VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(ItemSubjectSessionDateHeaderBinding.inflate(inflater, parent, false))
        } else {
            ItemViewHolder(
                ItemSubjectSessionBinding.inflate(inflater, parent, false),
                onSessionClick,
                onDeleteClick
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = items[position]) {
            is Row.Header -> (holder as HeaderViewHolder).bind(row.date)
            is Row.Item -> (holder as ItemViewHolder).bind(row.session)
        }
    }

    override fun getItemCount(): Int = items.size

    private class HeaderViewHolder(
        private val binding: ItemSubjectSessionDateHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(date: String) {
            binding.textDateHeader.text = date
        }
    }

    private class ItemViewHolder(
        private val binding: ItemSubjectSessionBinding,
        private val onSessionClick: (Session) -> Unit,
        private val onDeleteClick: (Session) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(session: Session) {
            val context = binding.root.context
            val start = session.startTime ?: "-"
            val end = session.endTime ?: "-"
            val classroom = session.classroomName ?: session.classroom ?: "-"
            val subject = session.subjectName ?: "-"
            val active = session.isActive == true

            binding.textSessionTime.text = "$start - $end"
            binding.textSessionMeta.text = "$subject / $classroom"
            binding.textSessionStatus.text = if (active) "현재 시간 기준 진행 중" else "예정/종료"
            binding.textSessionStatus.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (active) R.color.yonam_green else R.color.text_secondary
                )
            )
            binding.root.setOnClickListener { onSessionClick(session) }
            binding.buttonDeleteSession.setOnClickListener { onDeleteClick(session) }
        }
    }

    private sealed class Row {
        data class Header(val date: String) : Row()
        data class Item(val session: Session) : Row()
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 1
        private const val VIEW_TYPE_ITEM = 2
    }
}
