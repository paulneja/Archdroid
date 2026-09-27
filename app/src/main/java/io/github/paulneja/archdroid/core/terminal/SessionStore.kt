package io.github.paulneja.archdroid.core.terminal

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import io.github.paulneja.archdroid.core.chroot.ChrootController
import io.github.paulneja.archdroid.ui.terminal.TerminalTabState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** Process-wide owner of terminal sessions. Must be used on the main thread. */
class SessionStore(
    private val context: Context,
    private val chroot: ChrootController,
    private val scope: CoroutineScope,
) {
    class Tab(
        val id: String = UUID.randomUUID().toString(),
        val session: TerminalSession,
        val processIdentity: CompletableDeferred<Result<SessionProcessTree.ProcessIdentity>>,
        val title: MutableStateFlow<String> = MutableStateFlow("shell"),
        val closing: MutableStateFlow<Boolean> = MutableStateFlow(false),
        val protected: MutableStateFlow<Boolean> = MutableStateFlow(false),
    ) {
        var terminationSucceeded = false
        var closeFailed = false
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()

    private val _activeIndex = MutableStateFlow(-1)
    val activeIndex: StateFlow<Int> = _activeIndex.asStateFlow()

    private val _preparing = MutableStateFlow(false)
    val preparing: StateFlow<Boolean> = _preparing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _closeError = MutableStateFlow<String?>(null)
    val closeError: StateFlow<String?> = _closeError.asStateFlow()

    private val _broken = MutableStateFlow<List<String>>(emptyList())
    val broken: StateFlow<List<String>> = _broken.asStateFlow()

    /** Bumped whenever a tab's protection changes, so observers can recount. */
    private val _protectionChanges = MutableStateFlow(0)
    val protectionChanges: StateFlow<Int> = _protectionChanges.asStateFlow()

    private val _allClosed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val allClosed: SharedFlow<Unit> = _allClosed.asSharedFlow()

    private var view: TerminalView? = null
    private var starting = false

    fun protectedCount(): Int = _tabs.value.count { it.protected.value }

    fun attachView(terminalView: TerminalView) {
        view = terminalView
        activeTab()?.let { terminalView.attachSession(it.session) }
    }

    fun detachView(terminalView: TerminalView) {
        if (view === terminalView) view = null
    }

    fun activeTab(): Tab? = _tabs.value.getOrNull(_activeIndex.value)

    /** Mounts and opens the first shell the first time the terminal is shown. */
    fun ensureStarted(onFirstSession: () -> Unit = {}) {
        if (_tabs.value.isNotEmpty()) return
        startSession(onFirstSession)
    }

    fun createSession() {
        if (starting) return
        if (_tabs.value.isEmpty()) {
            startSession()
        } else {
            createPreparedSession()
        }
    }

    private fun startSession(onSessionCreated: () -> Unit = {}) {
        if (starting) return
        starting = true
        _preparing.value = true
        _error.value = null
        _broken.value = emptyList()
        scope.launch {
            try {
                val result = chroot.prepareSession()
                if (!result.ok) {
                    _error.value = result.log.joinToString("\n").ifBlank {
                        "could not prepare the terminal session"
                    }
                    return@launch
                }
                // Avoid spawning a session that cannot enter the rootfs.
                val missing = chroot.missingEssentials()
                if (missing.isNotEmpty()) {
                    _broken.value = missing
                } else {
                    createPreparedSession()
                    onSessionCreated()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                _error.value = failure.message ?: failure.toString()
            } finally {
                _preparing.value = false
                starting = false
            }
        }
    }

    private fun createPreparedSession() {
        val session = TerminalSessionFactory.create(
            ArchTerminalSessionClient(
                context = context,
                viewProvider = { view },
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
        val tab = Tab(session = session, processIdentity = CompletableDeferred())
        _tabs.value = _tabs.value + tab
        _activeIndex.value = _tabs.value.lastIndex
        // The process spawns on first size; give it one when no view is attached.
        view?.attachSession(session) ?: session.updateSize(80, 24)
        scope.launch {
            tab.processIdentity.complete(runCatching { SessionProcessTree.readIdentity(awaitPid(session)) })
        }
    }

    private suspend fun awaitPid(session: TerminalSession): Int {
        repeat(PID_WAIT_STEPS) {
            if (session.pid > 0) return session.pid
            delay(PID_WAIT_STEP_MILLIS)
        }
        error("terminal process did not start")
    }

    fun selectTab(index: Int) {
        val tab = _tabs.value.getOrNull(index) ?: return
        _activeIndex.value = index
        view?.attachSession(tab.session)
    }

    fun setProtected(index: Int, protect: Boolean) {
        val tab = _tabs.value.getOrNull(index) ?: return
        tab.protected.value = protect
        _protectionChanges.value++
        scope.launch {
            val identity = tab.processIdentity.await().getOrNull() ?: return@launch
            SessionProcessTree.setOomScore(
                identity,
                if (protect) ProcessShield.PROTECTED_OOM_SCORE else ProcessShield.DEFAULT_OOM_SCORE,
            )
        }
    }

    fun closeTab(index: Int) {
        val tab = _tabs.value.getOrNull(index) ?: return
        if (tab.closing.value) return
        tab.closing.value = true
        tab.closeFailed = false
        tab.terminationSucceeded = false
        _closeError.value = null
        scope.launch {
            val identity = tab.processIdentity.await().getOrElse { failure ->
                if (_tabs.value.any { it.id == tab.id }) {
                    tab.closeFailed = true
                    tab.closing.value = false
                    _closeError.value = failure.message ?: failure.javaClass.simpleName
                }
                return@launch
            }
            SessionProcessTree.terminate(identity).fold(
                onSuccess = {
                    tab.terminationSucceeded = true
                    if (TerminalTabState.canFinalizeClose(tab.terminationSucceeded)) {
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

    fun closeUnprotected() {
        _tabs.value.withIndex()
            .filter { !it.value.protected.value }
            .map { it.value.id }
            .forEach { id -> _tabs.value.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let(::closeTab) }
    }

    private fun removeSession(session: TerminalSession) {
        val tab = _tabs.value.firstOrNull { it.session == session } ?: return
        if (tab.closing.value || tab.closeFailed) {
            if (!TerminalTabState.canFinalizeClose(tab.terminationSucceeded)) return
        }
        finalizeSession(session)
    }

    private fun finalizeSession(session: TerminalSession) {
        val oldTabs = _tabs.value
        val index = oldTabs.indexOfFirst { it.session == session }
        if (index < 0) return
        val newIndex = TerminalTabState.afterRemoval(
            size = oldTabs.size,
            activeIndex = _activeIndex.value.coerceIn(0, oldTabs.lastIndex),
            removedIndex = index,
        )
        _tabs.value = oldTabs.toMutableList().apply { removeAt(index) }
        _activeIndex.value = newIndex
        if (_tabs.value.isEmpty()) {
            _allClosed.tryEmit(Unit)
            return
        }
        view?.attachSession(_tabs.value[newIndex].session)
    }

    private companion object {
        const val PID_WAIT_STEPS = 50
        const val PID_WAIT_STEP_MILLIS = 50L
    }
}
