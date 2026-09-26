package io.github.paulneja.archdroid.core.desktop.vnc

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Pure encoding and decoding of RFB messages, kept separate so it can be unit tested. */
object RfbProtocol {
    const val VERSION = "RFB 003.008\n"
    const val SECURITY_NONE = 1

    const val MSG_FRAMEBUFFER_UPDATE = 0
    const val MSG_SET_COLOUR_MAP = 1
    const val MSG_BELL = 2
    const val MSG_CUT_TEXT = 3

    const val ENCODING_RAW = 0
    const val ENCODING_COPY_RECT = 1
    const val ENCODING_DESKTOP_SIZE = -223

    val SUPPORTED_ENCODINGS = intArrayOf(ENCODING_COPY_RECT, ENCODING_RAW, ENCODING_DESKTOP_SIZE)

    const val BUTTON_LEFT = 1
    const val BUTTON_MIDDLE = 2
    const val BUTTON_RIGHT = 4
    const val WHEEL_UP = 8
    const val WHEEL_DOWN = 16

    /** 32 bpp, depth 24, little endian, true colour: bytes arrive as B, G, R, padding. */
    fun setPixelFormat(): ByteArray = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN).apply {
        put(0) // message type
        put(ByteArray(3))
        put(32) // bits per pixel
        put(24) // depth
        put(0) // big endian: no
        put(1) // true colour
        putShort(255); putShort(255); putShort(255)
        put(16); put(8); put(0) // red, green, blue shifts
        put(ByteArray(3))
    }.array()

    fun setEncodings(encodings: IntArray): ByteArray =
        ByteBuffer.allocate(4 + encodings.size * 4).order(ByteOrder.BIG_ENDIAN).apply {
            put(2)
            put(0)
            putShort(encodings.size.toShort())
            encodings.forEach(::putInt)
        }.array()

    fun updateRequest(incremental: Boolean, x: Int, y: Int, w: Int, h: Int): ByteArray =
        ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN).apply {
            put(3)
            put(if (incremental) 1 else 0)
            putShort(x.toShort()); putShort(y.toShort()); putShort(w.toShort()); putShort(h.toShort())
        }.array()

    fun keyEvent(keysym: Int, down: Boolean): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).apply {
            put(4)
            put(if (down) 1 else 0)
            put(ByteArray(2))
            putInt(keysym)
        }.array()

    fun pointerEvent(buttonMask: Int, x: Int, y: Int): ByteArray =
        ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN).apply {
            put(5)
            put(buttonMask.toByte())
            putShort(x.toShort()); putShort(y.toShort())
        }.array()

    fun clientCutText(text: String): ByteArray {
        val bytes = text.toByteArray(Charsets.ISO_8859_1)
        return ByteBuffer.allocate(8 + bytes.size).order(ByteOrder.BIG_ENDIAN).apply {
            put(6)
            put(ByteArray(3))
            putInt(bytes.size)
            put(bytes)
        }.array()
    }

    /** B, G, R, X byte quads into opaque ARGB ints. */
    fun decodeRow(src: ByteArray, dst: IntArray, dstOffset: Int, pixels: Int) {
        var s = 0
        for (i in 0 until pixels) {
            val b = src[s].toInt() and 0xFF
            val g = src[s + 1].toInt() and 0xFF
            val r = src[s + 2].toInt() and 0xFF
            dst[dstOffset + i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            s += 4
        }
    }

    /** Copies a rectangle within the framebuffer; row order avoids clobbering overlaps. */
    fun copyRect(fb: IntArray, stride: Int, sx: Int, sy: Int, dx: Int, dy: Int, w: Int, h: Int) {
        if (dy <= sy) {
            for (row in 0 until h) System.arraycopy(fb, (sy + row) * stride + sx, fb, (dy + row) * stride + dx, w)
        } else {
            for (row in h - 1 downTo 0) System.arraycopy(fb, (sy + row) * stride + sx, fb, (dy + row) * stride + dx, w)
        }
    }
}
