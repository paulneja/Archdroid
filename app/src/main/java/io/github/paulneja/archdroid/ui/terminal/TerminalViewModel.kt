package io.github.paulneja.archdroid.ui.terminal

import android.annotation.SuppressLint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.termux.view.TerminalView
import io.github.paulneja.archdroid.core.terminal.ArchTerminalViewClient
import io.github.paulneja.archdroid.core.terminal.ExtraKeysModifierState
import io.github.paulneja.archdroid.core.terminal.SessionStore
import io.github.paulneja.archdroid.core.terminal.SessionSurvival
import io.github.paulneja.archdroid.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow

class TerminalViewModel(app: Application) : AndroidViewModel(app) {

    val store: SessionStore = ServiceLocator.sessionStore(app)

    val modifierState = ExtraKeysModifierState()
    val ctrlActive = MutableStateFlow(false)
    val altActive = MutableStateFlow(false)
    val shiftActive = MutableStateFlow(false)

    @SuppressLint("StaticFieldLeak") // Cleared in detachView when the AndroidView is released.
    private var viewRef: TerminalView? = null
    private var keyboardRequested = false
    val viewClient = ArchTerminalViewClient(app, { viewRef }, modifierState)

    /** Index of a protected tab waiting for the user to confirm its closing. */
    val pendingProtectedClose = MutableStateFlow<Int?>(null)

    init {
        modifierState.onChange = {
            ctrlActive.value = modifierState.ctrlActive
            altActive.value = modifierState.altActive
            shiftActive.value = modifierState.shiftActive
        }
    }

    fun onShown() {
        keyboardRequested = true
        if (store.tabs.value.isEmpty()) {
            store.ensureStarted(onFirstSession = ::showRequestedKeyboard)
        } else {
            showRequestedKeyboard()
        }
    }

    fun attachView(view: TerminalView) {
        viewRef = view
        view.setTerminalViewClient(viewClient)
        store.attachView(view)
        showRequestedKeyboard()
    }

    fun detachView() {
        viewRef?.let(store::detachView)
        viewRef = null
    }

    private fun showRequestedKeyboard() {
        val view = viewRef ?: return
        if (!keyboardRequested || store.activeTab() == null) return
        view.post {
            if (viewRef === view && keyboardRequested && store.activeTab() != null) {
                keyboardRequested = false
                viewClient.showKeyboard()
            }
        }
    }

    fun requestClose(index: Int) {
        val tab = store.tabs.value.getOrNull(index) ?: return
        if (SessionSurvival.needsCloseConfirmation(tab.protected.value)) {
            pendingProtectedClose.value = index
        } else {
            store.closeTab(index)
        }
    }

    fun confirmProtectedClose() {
        pendingProtectedClose.value?.let(store::closeTab)
        pendingProtectedClose.value = null
    }

    fun dismissProtectedClose() {
        pendingProtectedClose.value = null
    }

    fun sendText(text: String) {
        store.activeTab()?.session?.takeIf { it.isRunning }?.write(text)
    }

    fun sendCodePoint(codePoint: Int) {
        store.activeTab()?.session?.takeIf { it.isRunning }?.writeCodePoint(false, codePoint)
    }

    fun toggleCtrl() = modifierState.toggleCtrl()
    fun toggleAlt() = modifierState.toggleAlt()
    fun toggleShift() = modifierState.toggleShift()

    val textSize = viewClient.textSize
    fun decreaseTextSize() = viewClient.changeTextSize(-1)
    fun increaseTextSize() = viewClient.changeTextSize(1)
    fun resetTextSize() = viewClient.changeTextSize(ArchTerminalViewClient.DEFAULT_TEXT_SIZE - textSize.value)

    fun showKeyboard() = viewClient.showKeyboard()
    fun toggleKeyboard() = viewClient.toggleKeyboard()
}
