package io.github.paulneja.archdroid.core.desktop

import io.github.paulneja.archdroid.core.desktop.input.TouchpadModel
import io.github.paulneja.archdroid.core.desktop.vnc.KeySyms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TouchpadModelTest {

    private val pad = TouchpadModel(sensitivity = 1f, scrollStepPx = 10f).apply { resize(100, 50) }

    @Test
    fun `starts centred and moves relatively`() {
        assertEquals(TouchpadModel.Action.Move(60, 25, 0), pad.move(10f, 0f))
    }

    @Test
    fun `the cursor stays on screen`() {
        pad.move(-1000f, 1000f)
        assertEquals(0f, pad.cursorX)
        assertEquals(49f, pad.cursorY)
    }

    @Test
    fun `one finger taps left click, two fingers right click`() {
        assertEquals(TouchpadModel.LEFT, (pad.tap(1) as TouchpadModel.Action.Click).button)
        assertEquals(TouchpadModel.RIGHT, (pad.tap(2) as TouchpadModel.Action.Click).button)
    }

    @Test
    fun `a long press drag holds the left button until release`() {
        pad.startDrag()
        assertEquals(TouchpadModel.LEFT, (pad.move(1f, 1f) as TouchpadModel.Action.Move).buttons)
        assertEquals(0, (pad.endDrag() as TouchpadModel.Action.Move).buttons)
        assertNull(pad.endDrag())
    }

    @Test
    fun `scrolling waits for a full step`() {
        assertNull(pad.scroll(4f))
        assertEquals(1, (pad.scroll(7f) as TouchpadModel.Action.Scroll).steps)
    }

    @Test
    fun `keysyms for text`() {
        assertEquals('a'.code, KeySyms.forCodePoint('a'.code))
        assertEquals(0xf1, KeySyms.forCodePoint('ñ'.code))
        assertEquals(0x010020ac, KeySyms.forCodePoint('€'.code))
        assertEquals(0xff0d, KeySyms.forCodePoint('\n'.code))
    }
}
