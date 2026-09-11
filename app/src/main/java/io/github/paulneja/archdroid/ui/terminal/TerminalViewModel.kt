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
        val title: MutableStateFlow<String> = MutableStateFlow("shell"),
    )

    val modifierState = ExtraKeysModifierState()
    val ctrlActive = MutableStateFlow(false)
    val altActive = MutableStateFlow(false)

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

    var onAllSessionsClosed: (() -> Unit)? = null

    // Cleared by detachView() from AndroidView's onRelease, so it never outlives the view.
    @SuppressLint("StaticFieldLeak")
    private var viewRef: TerminalView? = null

    init {
        modifierState.onChange = {
            ctrlActive.value = modifierState.ctrlActive
            altActive.value = modifierState.altActive
        }
        viewModelScope.launch {
            val result = chroot.prepareSession()
            _preparing.value = false
            if (!result.ok) {
                _error.value = result.log.joinToString("\n")
            } else {
                createSession()
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
        val tab = Tab(
            session = TerminalSessionFactory.create(
                ArchTerminalSessionClient(
                    context = getApplication(),
                    viewProvider = { viewRef },
                    onSessionFinished = { s -> mainHandler.post { removeSession(s) } },
                    onTitleChanged = { s ->
                        mainHandler.post {
                            _tabs.value.find { it.session == s }?.let { tab ->
                                s.title?.takeIf { it.isNotBlank() }?.let { tab.title.value = it }
                            }
                        }
                    },
                )
            )
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
        _tabs.value.getOrNull(index)?.session?.finishIfRunning()
    }

    private fun removeSession(session: TerminalSession) {
        val index = _tabs.value.indexOfFirst { it.session == session }
        if (index < 0) return
        _tabs.value = _tabs.value.toMutableList().apply { removeAt(index) }
        if (_tabs.value.isEmpty()) {
            onAllSessionsClosed?.invoke()
            return
        }
        val newIndex = _activeIndex.value.coerceAtMost(_tabs.value.lastIndex)
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

    fun closeAll() {
        _tabs.value.forEach { it.session.finishIfRunning() }
    }

    override fun onCleared() {
        closeAll()
    }
}
