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
        if (store.tabs.value.isEmpty()) {
            store.ensureStarted(onFirstSession = { viewClient.showKeyboard() })
        } else {
            // Wait for the view to be attached and focusable again.
            viewRef?.post { viewClient.showKeyboard() }
        }
    }

    fun attachView(view: TerminalView) {
        viewRef = view
        view.setTerminalViewClient(viewClient)
        store.attachView(view)
    }

    fun detachView() {
        viewRef?.let(store::detachView)
        viewRef = null
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

    fun showKeyboard() = viewClient.showKeyboard()
    fun toggleKeyboard() = viewClient.toggleKeyboard()
}
