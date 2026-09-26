package io.github.paulneja.archdroid.core.desktop

import io.github.paulneja.archdroid.core.desktop.vnc.RfbProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RfbProtocolTest {

    @Test
    fun `pixel format asks for little endian 32 bit true colour`() {
        val msg = RfbProtocol.setPixelFormat()
        assertEquals(20, msg.size)
        assertEquals(32, msg[4].toInt())
        assertEquals(24, msg[5].toInt())
        assertEquals(0, msg[6].toInt()) // little endian
        assertEquals(1, msg[7].toInt()) // true colour
        assertEquals(16, msg[14].toInt())
    }

    @Test
    fun `pointer events carry the mask and big endian coordinates`() {
        assertArrayEquals(
            byteArrayOf(5, 4, 0x01, 0x2c, 0x00, 0x0a),
            RfbProtocol.pointerEvent(RfbProtocol.BUTTON_RIGHT, 300, 10),
        )
    }

    @Test
    fun `key events use 32 bit keysyms`() {
        assertArrayEquals(
            byteArrayOf(4, 1, 0, 0, 0, 0, 0xff.toByte(), 0x0d),
            RfbProtocol.keyEvent(0xff0d, true),
        )
    }

    @Test
    fun `decodes BGRX into opaque ARGB`() {
        val dst = IntArray(1)
        RfbProtocol.decodeRow(byteArrayOf(0x33, 0x22, 0x11, 0), dst, 0, 1)
        assertEquals(0xFF112233.toInt(), dst[0])
    }

    @Test
    fun `copy rect handles overlapping downward moves`() {
        // 1 column, 4 rows: move rows 0..1 down by one.
        val fb = intArrayOf(1, 2, 3, 4)
        RfbProtocol.copyRect(fb, 1, 0, 0, 0, 1, 1, 2)
        assertArrayEquals(intArrayOf(1, 1, 2, 4), fb)
    }

    @Test
    fun `encodings list includes desktop resize`() {
        val msg = RfbProtocol.setEncodings(RfbProtocol.SUPPORTED_ENCODINGS)
        assertEquals(4 + 3 * 4, msg.size)
    }
}
