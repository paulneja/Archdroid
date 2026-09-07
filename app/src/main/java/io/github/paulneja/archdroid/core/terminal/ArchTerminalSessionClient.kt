package io.github.paulneja.archdroid.core.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView

class ArchTerminalSessionClient(
    context: Context,
    private val viewProvider: () -> TerminalView?,
    private val onSessionFinished: (TerminalSession) -> Unit,
    private val onTitleChanged: (TerminalSession) -> Unit,
) : TerminalSessionClient {

    private val clipboard =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun onTextChanged(changedSession: TerminalSession) {
        viewProvider()?.onScreenUpdated()
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        onTitleChanged(changedSession)
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        onSessionFinished(finishedSession)
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText("archdroid", text))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession) {
        val text = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(null)
            ?.toString()
            ?: return
        session.emulator?.paste(text)
    }

    override fun onBell(session: TerminalSession) {}

    override fun onColorsChanged(session: TerminalSession) {
        viewProvider()?.onScreenUpdated()
    }

    override fun onTerminalCursorStateChange(state: Boolean) {}

    override fun getTerminalCursorStyle(): Int? = null

    override fun logError(tag: String, message: String) = Log.e(tag, message).let {}
    override fun logWarn(tag: String, message: String) = Log.w(tag, message).let {}
    override fun logInfo(tag: String, message: String) = Log.i(tag, message).let {}
    override fun logDebug(tag: String, message: String) = Log.d(tag, message).let {}
    override fun logVerbose(tag: String, message: String) = Log.v(tag, message).let {}
    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) =
        Log.e(tag, message, e).let {}
    override fun logStackTrace(tag: String, e: Exception) = Log.e(tag, null, e).let {}
}
