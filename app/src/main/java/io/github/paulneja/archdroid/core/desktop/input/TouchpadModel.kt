package io.github.paulneja.archdroid.core.desktop.input

class TouchpadModel(
    private val sensitivity: Float = 1.6f,
    private val scrollStepPx: Float = 60f,
) {
    var width = 1
        private set
    var height = 1
        private set
    var cursorX = 0f
        private set
    var cursorY = 0f
        private set

    /** Events the backend must send, in order. */
    sealed interface Action {
        data class Move(val x: Int, val y: Int, val buttons: Int) : Action
        data class Click(val x: Int, val y: Int, val button: Int) : Action
        data class Scroll(val x: Int, val y: Int, val steps: Int) : Action
    }

    private var dragging = false
    private var scrollAccumulator = 0f

    fun resize(w: Int, h: Int) {
        width = w.coerceAtLeast(1)
        height = h.coerceAtLeast(1)
        cursorX = cursorX.coerceIn(0f, (width - 1).toFloat()).takeIf { cursorX > 0f } ?: (width / 2f)
        cursorY = cursorY.coerceIn(0f, (height - 1).toFloat()).takeIf { cursorY > 0f } ?: (height / 2f)
    }

    /** [dx], [dy] are finger deltas in framebuffer pixels. */
    fun move(dx: Float, dy: Float): Action {
        cursorX = (cursorX + dx * sensitivity).coerceIn(0f, (width - 1).toFloat())
        cursorY = (cursorY + dy * sensitivity).coerceIn(0f, (height - 1).toFloat())
        return Action.Move(cursorX.toInt(), cursorY.toInt(), if (dragging) LEFT else 0)
    }

    fun tap(fingers: Int): Action =
        Action.Click(cursorX.toInt(), cursorY.toInt(), if (fingers >= 2) RIGHT else LEFT)

    fun startDrag(): Action {
        dragging = true
        return Action.Move(cursorX.toInt(), cursorY.toInt(), LEFT)
    }

    fun endDrag(): Action? {
        if (!dragging) return null
        dragging = false
        return Action.Move(cursorX.toInt(), cursorY.toInt(), 0)
    }

    /** Two-finger vertical movement; returns a scroll once a full step has accumulated. */
    fun scroll(dy: Float): Action? {
        scrollAccumulator += dy
        val steps = (scrollAccumulator / scrollStepPx).toInt()
        if (steps == 0) return null
        scrollAccumulator -= steps * scrollStepPx
        return Action.Scroll(cursorX.toInt(), cursorY.toInt(), steps)
    }

    /** Direct touch and real mice place the cursor absolutely. */
    fun place(x: Float, y: Float) {
        cursorX = x.coerceIn(0f, (width - 1).toFloat())
        cursorY = y.coerceIn(0f, (height - 1).toFloat())
    }

    companion object {
        const val LEFT = 1
        const val RIGHT = 4
    }
}
