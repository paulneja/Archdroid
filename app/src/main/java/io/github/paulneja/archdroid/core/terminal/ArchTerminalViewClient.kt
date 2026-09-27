package io.github.paulneja.archdroid.core.terminal

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

class ArchTerminalViewClient(
    private val context: Context,
    private val viewProvider: () -> TerminalView?,
    private val modifierState: ExtraKeysModifierState,
) : TerminalViewClient {

    var textSizeDp: Int = 13
        private set

    override fun onScale(scale: Float): Float {
        val view = viewProvider() ?: return 1f
        return if (scale < 0.9f || scale > 1.1f) {
            val newSize = (textSizeDp * if (scale < 0.9f) 0.9f else 1.1f).toInt().coerceIn(6, 36)
            if (newSize != textSizeDp) {
                textSizeDp = newSize
                view.setTextSize(newSize)
            }
            1.0f
        } else scale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        showKeyboard()
    }

    /** Focus must be acquired before asking Android to show the keyboard. */
    fun showKeyboard() {
        val view = viewProvider() ?: return
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.post {
            if (viewProvider() === view && view.isAttachedToWindow && view.hasFocus()) {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    fun toggleKeyboard() {
        val view = viewProvider() ?: return
        view.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = true

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) {}

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean = modifierState.ctrlActive

    override fun readAltKey(): Boolean = modifierState.altActive

    override fun readShiftKey(): Boolean = modifierState.shiftActive

    override fun readFnKey(): Boolean = false

    /** TerminalView has already captured the modifiers for the current keypress. */
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        modifierState.resetTransient()
        return false
    }

    override fun onEmulatorSet() {}

    override fun logError(tag: String, message: String) = Log.e(tag, message).let {}
    override fun logWarn(tag: String, message: String) = Log.w(tag, message).let {}
    override fun logInfo(tag: String, message: String) = Log.i(tag, message).let {}
    override fun logDebug(tag: String, message: String) = Log.d(tag, message).let {}
    override fun logVerbose(tag: String, message: String) = Log.v(tag, message).let {}
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) =
        Log.e(tag, message, e).let {}
    override fun logStackTrace(tag: String, e: Exception) = Log.e(tag, null, e).let {}
}

class ExtraKeysModifierState {
    @Volatile var ctrlActive: Boolean = false
    @Volatile var altActive: Boolean = false
    @Volatile var shiftActive: Boolean = false

    var onChange: (() -> Unit)? = null

    fun toggleCtrl() { ctrlActive = !ctrlActive; onChange?.invoke() }
    fun toggleAlt() { altActive = !altActive; onChange?.invoke() }
    fun toggleShift() { shiftActive = !shiftActive; onChange?.invoke() }
    fun resetTransient() {
        if (ctrlActive || altActive || shiftActive) {
            ctrlActive = false; altActive = false; shiftActive = false
            onChange?.invoke()
        }
    }
}
