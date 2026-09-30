package io.github.paulneja.archdroid.ui.desktop

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.InputType
import android.view.GestureDetector
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import io.github.paulneja.archdroid.core.desktop.input.TouchpadModel
import io.github.paulneja.archdroid.core.desktop.vnc.KeySyms
import io.github.paulneja.archdroid.core.desktop.vnc.RfbClient
import io.github.paulneja.archdroid.core.desktop.vnc.RfbProtocol
import kotlin.math.abs
import kotlin.math.min

@SuppressLint("ViewConstructor")
class VncView(context: Context, private val client: () -> RfbClient?) : View(context) {

    enum class TouchMode { TOUCHPAD, DIRECT }

    var touchMode = TouchMode.TOUCHPAD
        set(value) { field = value; invalidate() }

    var mouseConnected = false
        set(value) { field = value; invalidate() }

    @Volatile
    private var bitmap: Bitmap? = null
    private val dst = RectF()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val cursorFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val cursorStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val arrow = Path().apply {
        moveTo(0f, 0f); lineTo(0f, 22f); lineTo(6f, 17f); lineTo(10f, 26f)
        lineTo(14f, 24f); lineTo(10f, 15f); lineTo(17f, 15f); close()
    }

    val touchpad = TouchpadModel()
    private var mouseButtons = 0
    private var fingers = 0
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.BLACK)
    }

    fun resizeFramebuffer(width: Int, height: Int) {

        bitmap = createBitmap(width, height)
        post {
            touchpad.resize(width, height)
            invalidate()
        }
    }

    fun update(pixels: IntArray, stride: Int, x: Int, y: Int, w: Int, h: Int) {
        val bmp = bitmap ?: return
        if (x + w > bmp.width || y + h > bmp.height) return
        synchronized(bmp) { bmp.setPixels(pixels, y * stride + x, stride, x, y, w, h) }
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        val scale = min(width / bmp.width.toFloat(), height / bmp.height.toFloat())
        val w = bmp.width * scale
        val h = bmp.height * scale
        dst.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
        synchronized(bmp) { canvas.drawBitmap(bmp, null, dst, paint) }
        if (touchMode == TouchMode.TOUCHPAD && !mouseConnected) {
            canvas.withTranslation(dst.left + touchpad.cursorX * scale, dst.top + touchpad.cursorY * scale) {
                drawPath(arrow, cursorFill)
                drawPath(arrow, cursorStroke)
            }
        }
    }

    private fun scale(): Float {
        val bmp = bitmap ?: return 1f
        return min(width / bmp.width.toFloat(), height / bmp.height.toFloat())
    }

    private fun toFramebuffer(x: Float, y: Float): Pair<Float, Float> {
        val s = scale()
        return (x - dst.left) / s to (y - dst.top) / s
    }

    private val longPress = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            if (touchMode == TouchMode.TOUCHPAD && fingers == 1) {
                send(touchpad.startDrag())
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) return onMouse(event)
        longPress.onTouchEvent(event)
        when (touchMode) {
            TouchMode.TOUCHPAD -> onTouchpad(event)
            TouchMode.DIRECT -> onDirect(event)
        }
        return true
    }

    private fun onTouchpad(event: MotionEvent) {
        val s = scale()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fingers = 1; moved = false
                lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> fingers = event.pointerCount
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                if (abs(dx) + abs(dy) > TOUCH_SLOP) moved = true
                if (event.pointerCount >= 2) touchpad.scroll(dy)?.let(::send)
                else send(touchpad.move(dx / s, dy / s))
                lastX = event.x; lastY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val drag = touchpad.endDrag()
                if (drag != null) send(drag) else if (!moved) send(touchpad.tap(fingers))
                fingers = 0
            }
            MotionEvent.ACTION_CANCEL -> {
                touchpad.endDrag()?.let(::send)
                fingers = 0
            }
        }
    }

    private fun onDirect(event: MotionEvent) {
        val (x, y) = toFramebuffer(event.x, event.y)
        touchpad.place(x, y)
        val buttons = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> TouchpadModel.LEFT
            else -> 0
        }
        client()?.pointer(buttons, x.toInt(), y.toInt())
    }

    private fun onMouse(event: MotionEvent): Boolean {
        mouseConnected = true
        val (x, y) = toFramebuffer(event.x, event.y)
        touchpad.place(x, y)
        mouseButtons = 0
        if (event.buttonState and MotionEvent.BUTTON_PRIMARY != 0) mouseButtons = mouseButtons or RfbProtocol.BUTTON_LEFT
        if (event.buttonState and MotionEvent.BUTTON_TERTIARY != 0) mouseButtons = mouseButtons or RfbProtocol.BUTTON_MIDDLE
        if (event.buttonState and MotionEvent.BUTTON_SECONDARY != 0) mouseButtons = mouseButtons or RfbProtocol.BUTTON_RIGHT
        client()?.pointer(mouseButtons, x.toInt(), y.toInt())
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) return super.onGenericMotionEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE -> return onMouse(event)
            MotionEvent.ACTION_SCROLL -> {
                val (x, y) = toFramebuffer(event.x, event.y)
                val v = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                if (v != 0f) {
                    val wheel = if (v > 0) RfbProtocol.WHEEL_UP else RfbProtocol.WHEEL_DOWN
                    client()?.pointer(mouseButtons or wheel, x.toInt(), y.toInt())
                    client()?.pointer(mouseButtons, x.toInt(), y.toInt())
                }
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    private fun send(action: TouchpadModel.Action) {
        val c = client() ?: return
        when (action) {
            is TouchpadModel.Action.Move -> c.pointer(action.buttons, action.x, action.y)
            is TouchpadModel.Action.Click -> {
                c.pointer(action.button, action.x, action.y)
                c.pointer(0, action.x, action.y)
            }
            is TouchpadModel.Action.Scroll -> {
                val wheel = if (action.steps < 0) RfbProtocol.WHEEL_UP else RfbProtocol.WHEEL_DOWN
                repeat(abs(action.steps)) {
                    c.pointer(wheel, action.x, action.y)
                    c.pointer(0, action.x, action.y)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = key(event, down = true)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = key(event, down = false)

    private fun key(event: KeyEvent, down: Boolean): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return false
        val keysym = KeySyms.forKeyCode(event.keyCode)
            ?: event.getUnicodeChar(event.metaState and (KeyEvent.META_SHIFT_MASK or KeyEvent.META_CAPS_LOCK_ON))
                .takeIf { it > 0 }?.let(KeySyms::forCodePoint)
            ?: return false
        client()?.key(keysym, down)
        return true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                text.codePoints().forEach { cp ->
                    val keysym = KeySyms.forCodePoint(cp)
                    client()?.key(keysym, true)
                    client()?.key(keysym, false)
                }
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength) {
                    client()?.key(BACKSPACE, true)
                    client()?.key(BACKSPACE, false)
                }
                return true
            }
        }
    }

    private companion object {
        const val TOUCH_SLOP = 12f
        const val BACKSPACE = 0xff08
    }
}
