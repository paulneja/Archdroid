package io.github.paulneja.archdroid.ui.terminal

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import io.github.paulneja.archdroid.core.chroot.ChrootController
import io.github.paulneja.archdroid.core.terminal.ArchTerminalSessionClient
import io.github.paulneja.archdroid.core.terminal.ArchTerminalViewClient
import io.github.paulneja.archdroid.core.terminal.ExtraKeysModifierState
import io.github.paulneja.archdroid.core.terminal.SessionProcessTree
import io.github.paulneja.archdroid.core.terminal.TerminalSessionFactory
import io.github.paulneja.archdroid.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class TerminalViewModel(app: Application) : AndroidViewModel(app) {

    private val chroot: ChrootController = ServiceLocator.chroot(app)
    private val mainHandler = Handler(Looper.getMainLooper())

    class Tab(
        val id: String = UUID.randomUUID().toString(),
        val session: TerminalSession,
        val processIdentity: SessionProcessTree.ProcessIdentity?,
        val title: MutableStateFlow<String> = MutableStateFlow("shell"),
        val closing: MutableStateFlow<Boolean> = MutableStateFlow(false),
    ) {
        var exitObserved = false
        var terminationSucceeded = false
        var closeFailed = false
    }

    val modifierState = ExtraKeysModifierState()
    val ctrlActive = MutableStateFlow(false)
    val altActive = MutableStateFlow(false)
    val shiftActive = MutableStateFlow(false)

    // AndroidViewModel hands us the Application, not an Activity: safe to hold.
    val viewClient = ArchTerminalViewClient(app, { viewRef }, modifierState)

    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs = _tabs.asStateFlow()

    private val _activeIndex = MutableStateFlow(0)
    val activeIndex = _activeIndex.asStateFlow()

    private val _preparing = MutableStateFlow(true)
    val preparing = _preparing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _closeError = MutableStateFlow<String?>(null)
    val closeError = _closeError.asStateFlow()

    /** Essential paths missing from the rootfs; non-empty means it cannot be entered. */
    private val _broken = MutableStateFlow<List<String>>(emptyList())
    val broken = _broken.asStateFlow()

    var onAllSessionsClosed: (() -> Unit)? = null

    // Cleared by detachView() from AndroidView's onRelease, so it never outlives the view.
    @SuppressLint("StaticFieldLeak")
    private var viewRef: TerminalView? = null

    init {
        modifierState.onChange = {
            ctrlActive.value = modifierState.ctrlActive
            altActive.value = modifierState.altActive
            shiftActive.value = modifierState.shiftActive
        }
        viewModelScope.launch {
            val result = chroot.prepareSession()
            if (!result.ok) {
                _preparing.value = false
                _error.value = result.log.joinToString("\n")
                return@launch
            }
            // Check before spawning: a gutted rootfs makes the session die instantly and
            // the screen close itself, which reads as the app refusing to open at all.
            val missing = chroot.missingEssentials()
            _preparing.value = false
            if (missing.isNotEmpty()) {
                _broken.value = missing
            } else {
                createSession()
                // Nothing raises the keyboard on its own, and a terminal with no way to
                // type is not a terminal.
                viewClient.showKeyboard()
            }
        }
    }

    fun attachView(view: TerminalView) {
        viewRef = view
        view.setTerminalViewClient(viewClient)
        activeTab()?.let { view.attachSession(it.session) }
    }

    fun detachView() {
        viewRef = null
    }

    fun activeTab(): Tab? = _tabs.value.getOrNull(_activeIndex.value)

    fun createSession() {
        val session = TerminalSessionFactory.create(
            ArchTerminalSessionClient(
                context = getApplication(),
                viewProvider = { viewRef },
                notifyFinished = { s -> mainHandler.post { removeSession(s) } },
                notifyTitleChanged = { s ->
                    mainHandler.post {
                        _tabs.value.find { it.session == s }?.let { tab ->
                            s.title?.takeIf { it.isNotBlank() }?.let { tab.title.value = it }
                        }
                    }
                },
            )
        )
        val tab = Tab(
            session = session,
            processIdentity = runCatching { SessionProcessTree.readIdentity(session.pid) }.getOrNull(),
        )
        _tabs.value = _tabs.value + tab
        _activeIndex.value = _tabs.value.lastIndex
        viewRef?.attachSession(tab.session)
    }

    fun selectTab(index: Int) {
        val tab = _tabs.value.getOrNull(index) ?: return
        _activeIndex.value = index
        viewRef?.attachSession(tab.session)
    }

    fun closeTab(index: Int) {
        val tab = _tabs.value.getOrNull(index) ?: return
        if (tab.closing.value) return
        val identity = tab.processIdentity
        if (identity == null) {
            _closeError.value = "could not read the terminal process identity"
            return
        }
        tab.closing.value = true
        tab.closeFailed = false
        tab.terminationSucceeded = false
        _closeError.value = null
        viewModelScope.launch {
            SessionProcessTree.terminate(identity).fold(
                onSuccess = {
                    tab.terminationSucceeded = true
                    if (TerminalTabState.canFinalizeClose(tab.exitObserved, tab.terminationSucceeded)) {
                        finalizeSession(tab.session)
                    }
                },
                onFailure = { failure ->
                    if (_tabs.value.any { it.id == tab.id }) {
                        tab.closeFailed = true
                        tab.terminationSucceeded = false
                        tab.closing.value = false
                        _closeError.value = failure.message ?: failure.javaClass.simpleName
                    }
                },
            )
        }
    }

    private fun removeSession(session: TerminalSession) {
        val tab = _tabs.value.firstOrNull { it.session == session } ?: return
        if (tab.closing.value || tab.closeFailed) {
            tab.exitObserved = true
            if (!TerminalTabState.canFinalizeClose(tab.exitObserved, tab.terminationSucceeded)) return
        }
        finalizeSession(session)
    }

    private fun finalizeSession(session: TerminalSession) {
        val oldTabs = _tabs.value
        val index = oldTabs.indexOfFirst { it.session == session }
        if (index < 0) return
        val newIndex = TerminalTabState.afterRemoval(
            size = oldTabs.size,
            activeIndex = _activeIndex.value,
            removedIndex = index,
        )
        _tabs.value = oldTabs.toMutableList().apply { removeAt(index) }
        if (_tabs.value.isEmpty()) {
            _activeIndex.value = -1
            viewModelScope.launch {
                val result = chroot.releaseSession(remainingSessions = 0)
                if (result != null && !result.ok) {
                    _error.value = result.log.joinToString("\n")
                }
                onAllSessionsClosed?.invoke()
            }
            return
        }
        _activeIndex.value = newIndex
        viewRef?.attachSession(_tabs.value[newIndex].session)
    }

    fun sendText(text: String) {
        activeTab()?.session?.takeIf { it.isRunning }?.write(text)
    }

    fun sendCodePoint(codePoint: Int) {
        activeTab()?.session?.takeIf { it.isRunning }?.writeCodePoint(false, codePoint)
    }

    fun toggleCtrl() = modifierState.toggleCtrl()
    fun toggleAlt() = modifierState.toggleAlt()
    fun toggleShift() = modifierState.toggleShift()

    fun showKeyboard() = viewClient.showKeyboard()
    fun toggleKeyboard() = viewClient.toggleKeyboard()

    fun closeAll() {
        _tabs.value.forEach { it.session.finishIfRunning() }
    }

    override fun onCleared() {
        closeAll()
    }
}
