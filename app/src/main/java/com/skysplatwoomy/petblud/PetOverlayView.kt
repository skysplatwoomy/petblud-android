package com.skysplatwoomy.petblud

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.withRotation
import kotlin.math.cos
import kotlin.math.sin

@SuppressLint("ViewConstructor")
class PetOverlayView(
    context: Context,
    private val petId: Long,
    private val species: PetSpecies,
    private val spriteSheet: Bitmap?,
    private val idleBitmap: Bitmap,
    private val listener: Listener,
) : View(context) {
    interface Listener {
        fun onPointerDown(
            petId: Long,
            localX: Float,
            localY: Float,
            rawX: Float,
            rawY: Float,
            eventTimeMillis: Long,
        )

        fun onPointerMove(petId: Long, rawX: Float, rawY: Float, eventTimeMillis: Long)

        fun onPointerUp(
            petId: Long,
            localX: Float,
            localY: Float,
            rawX: Float,
            rawY: Float,
            eventTimeMillis: Long,
        )

        fun onPointerCancel(petId: Long)
        fun onLongPress(petId: Long, rawX: Float, rawY: Float)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val source = Rect()
    private val destination = Rect()
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var animation: PetAnimation = PetAnimation.IDLE
    private var frameIndex: Int = 0
    private var rotationDegrees = 0f
    private var spriteWidthPx = 1
    private var spriteHeightPx = 1
    private var downRawX = 0f
    private var downRawY = 0f
    private var longPressTriggered = false

    private val longPressRunnable = Runnable {
        longPressTriggered = true
        listener.onPointerCancel(petId)
        listener.onLongPress(petId, downRawX, downRawY)
        performLongClick()
    }

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isClickable = true
        isLongClickable = true
    }

    fun showFrame(
        value: PetAnimation?,
        index: Int,
        rotationDegrees: Float,
        spriteWidthPx: Int,
        spriteHeightPx: Int,
    ) {
        val nextAnimation = value ?: PetAnimation.IDLE
        val nextIndex = index.coerceIn(0, nextAnimation.frameCount - 1)
        val nextSpriteWidth = spriteWidthPx.coerceAtLeast(1)
        val nextSpriteHeight = spriteHeightPx.coerceAtLeast(1)
        if (
            animation == nextAnimation && frameIndex == nextIndex &&
            this.rotationDegrees == rotationDegrees &&
            this.spriteWidthPx == nextSpriteWidth && this.spriteHeightPx == nextSpriteHeight
        ) {
            return
        }
        animation = nextAnimation
        frameIndex = nextIndex
        this.rotationDegrees = rotationDegrees
        this.spriteWidthPx = nextSpriteWidth
        this.spriteHeightPx = nextSpriteHeight
        invalidate()
    }

    fun toSpriteLocal(localX: Float, localY: Float): Pair<Float, Float> {
        val centerX = width / 2f
        val centerY = height / 2f
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val cosine = cos(radians).toFloat()
        val sine = sin(radians).toFloat()
        val dx = localX - centerX
        val dy = localY - centerY
        val unrotatedX = cosine * dx + sine * dy + centerX
        val unrotatedY = -sine * dx + cosine * dy + centerY
        val spriteLeft = (width - spriteWidthPx) / 2f
        val spriteTop = (height - spriteHeightPx) / 2f
        return (unrotatedX - spriteLeft).coerceIn(0f, spriteWidthPx.toFloat()) to
            (unrotatedY - spriteTop).coerceIn(0f, spriteHeightPx.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val visualScale = SpriteVisualScale.forAnimation(species, animation)
        val renderWidth = (spriteWidthPx * visualScale).toInt().coerceAtLeast(1)
        val renderHeight = (spriteHeightPx * visualScale).toInt().coerceAtLeast(1)
        val left = (width - renderWidth) / 2
        val top = (height - renderHeight) / 2
        destination.set(left, top, left + renderWidth, top + renderHeight)
        canvas.withRotation(rotationDegrees, width / 2f, height / 2f) {
            val sheet = spriteSheet
            if (
                sheet != null &&
                sheet.width >= CELL_WIDTH * SHEET_COLUMNS &&
                sheet.height >= CELL_HEIGHT * SHEET_ROWS
            ) {
                val column = frameIndex.coerceIn(0, animation.frameCount - 1)
                source.set(
                    column * CELL_WIDTH,
                    animation.row * CELL_HEIGHT,
                    (column + 1) * CELL_WIDTH,
                    (animation.row + 1) * CELL_HEIGHT,
                )
                drawBitmap(sheet, source, destination, paint)
            } else {
                source.set(0, 0, idleBitmap.width, idleBitmap.height)
                drawBitmap(idleBitmap, source, destination, paint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downRawX = event.rawX
                downRawY = event.rawY
                longPressTriggered = false
                handler.postDelayed(longPressRunnable, longPressTimeout)
                listener.onPointerDown(
                    petId,
                    event.x,
                    event.y,
                    event.rawX,
                    event.rawY,
                    event.eventTime,
                )
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (dx * dx + dy * dy > touchSlop * touchSlop) {
                    handler.removeCallbacks(longPressRunnable)
                }
                if (!longPressTriggered) {
                    listener.onPointerMove(petId, event.rawX, event.rawY, event.eventTime)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (!longPressTriggered) {
                    listener.onPointerUp(
                        petId,
                        event.x,
                        event.y,
                        event.rawX,
                        event.rawY,
                        event.eventTime,
                    )
                    performClick()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                listener.onPointerCancel(petId)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun performLongClick(): Boolean {
        super.performLongClick()
        return true
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }

    companion object {
        const val CELL_WIDTH = 192
        const val CELL_HEIGHT = 208
        const val SHEET_COLUMNS = 8
        const val SHEET_ROWS = 11
    }
}
