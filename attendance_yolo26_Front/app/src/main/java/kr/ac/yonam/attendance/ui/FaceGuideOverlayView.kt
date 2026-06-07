package kr.ac.yonam.attendance.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.min
import kr.ac.yonam.attendance.R

class FaceGuideOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66000000")
        style = Paint.Style.FILL
    }

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
    }

    private val messagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 42f
        isFakeBoldText = true
        setShadowLayer(5f, 0f, 2f, Color.BLACK)
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 32f
        setShadowLayer(5f, 0f, 2f, Color.BLACK)
    }

    private val messageBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99000000")
        style = Paint.Style.FILL
    }

    private val guideRect = RectF()
    private val messageBackgroundRect = RectF()

    private var status: String = STATUS_IDLE
    private var message: String = DEFAULT_MESSAGE
    private var progressText: String? = null

    init {
        setWillNotDraw(false)
    }

    fun setGuideState(status: String, message: String?, progressText: String? = null) {
        this.status = status
        this.message = message?.takeIf { it.isNotBlank() } ?: DEFAULT_MESSAGE
        this.progressText = progressText?.takeIf { it.isNotBlank() }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val guideWidth = min(width * 0.42f, height * 0.45f)
        val guideHeight = min(guideWidth * 1.35f, height * 0.72f)
        val centerX = width / 2f
        val centerY = height * 0.43f

        guideRect.set(
            centerX - guideWidth / 2f,
            centerY - guideHeight / 2f,
            centerX + guideWidth / 2f,
            centerY + guideHeight / 2f
        )

        drawDimmedOutsideGuide(canvas)

        guidePaint.color = colorForStatus(status)
        canvas.drawOval(guideRect, guidePaint)

        drawGuideText(canvas, centerX)
    }

    private fun drawDimmedOutsideGuide(canvas: Canvas) {
        val overlayPath = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            addOval(guideRect, Path.Direction.CCW)
        }
        canvas.drawPath(overlayPath, dimPaint)
    }

    private fun drawGuideText(canvas: Canvas, centerX: Float) {
        val hasProgress = progressText != null
        val messageY = (guideRect.bottom + 58f).coerceAtMost(height - if (hasProgress) 82f else 42f)
        val progressY = messageY + 42f
        val backgroundHeight = if (hasProgress) 96f else 58f

        messageBackgroundRect.set(
            28f,
            messageY - 42f,
            width - 28f,
            messageY - 42f + backgroundHeight
        )
        canvas.drawRoundRect(messageBackgroundRect, 20f, 20f, messageBackgroundPaint)
        canvas.drawText(message, centerX, messageY, messagePaint)
        progressText?.let {
            canvas.drawText(it, centerX, progressY, progressPaint)
        }
    }

    private fun colorForStatus(status: String): Int {
        val colorResId = when (status) {
            "accepted", "completed", "attended", "already_attended", "enroll_success" -> R.color.yonam_green
            "no_face", "multiple_faces", "error", "unknown", "enroll_error" -> R.color.yonam_red
            "idle", "waiting_face", "capturing", "recognizing", "enroll_waiting", "enroll_capturing" -> R.color.yonam_blue
            else -> R.color.yonam_blue
        }
        return ContextCompat.getColor(context, colorResId)
    }

    companion object {
        private const val STATUS_IDLE = "idle"
        private const val DEFAULT_MESSAGE = "얼굴을 원 안에 맞춰주세요"
    }
}
