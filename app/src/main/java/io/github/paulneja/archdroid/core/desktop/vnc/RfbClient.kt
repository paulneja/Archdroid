package io.github.paulneja.archdroid.core.desktop.vnc

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/** RFB 3.8, no auth (Xvnc listens on localhost only), Raw + CopyRect. */
class RfbClient(private val listener: Listener) {

    interface Listener {
        fun onResize(width: Int, height: Int)
        fun onUpdate(pixels: IntArray, width: Int, x: Int, y: Int, w: Int, h: Int)
        fun onClipboard(text: String) {}
    }

    private lateinit var socket: Socket
    private lateinit var input: DataInputStream
    private lateinit var output: DataOutputStream
    private val writeLock = Any()

    var width = 0
        private set
    var height = 0
        private set
    private var framebuffer = IntArray(0)

    fun connect(host: String, port: Int) {
        socket = Socket().apply {
            tcpNoDelay = true
            connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS)
        }
        input = DataInputStream(BufferedInputStream(socket.getInputStream(), 1 shl 16))
        output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
        handshake()
    }

    private fun handshake() {
        val version = ByteArray(12).also(input::readFully)
        if (!String(version, Charsets.US_ASCII).startsWith("RFB ")) throw IOException("not an RFB server")
        output.write(RfbProtocol.VERSION.toByteArray(Charsets.US_ASCII))
        output.flush()

        val count = input.readUnsignedByte()
        if (count == 0) throw IOException("server refused: ${readString()}")
        val types = ByteArray(count).also(input::readFully).map { it.toInt() and 0xFF }
        if (RfbProtocol.SECURITY_NONE !in types) throw IOException("server requires authentication")
        output.writeByte(RfbProtocol.SECURITY_NONE)
        output.flush()
        if (input.readInt() != 0) throw IOException("security handshake failed: ${readString()}")

        output.writeByte(1) // shared
        output.flush()
        width = input.readUnsignedShort()
        height = input.readUnsignedShort()
        input.skipBytes(16) // server pixel format; we impose ours below
        readString() // desktop name
        allocate()

        synchronized(writeLock) {
            output.write(RfbProtocol.setPixelFormat())
            output.write(RfbProtocol.setEncodings(RfbProtocol.SUPPORTED_ENCODINGS))
            output.write(RfbProtocol.updateRequest(incremental = false, 0, 0, width, height))
            output.flush()
        }
        listener.onResize(width, height)
    }

    /** Blocks, dispatching server messages until the connection closes. */
    fun run() {
        while (true) {
            when (val type = input.readUnsignedByte()) {
                RfbProtocol.MSG_FRAMEBUFFER_UPDATE -> readUpdate()
                RfbProtocol.MSG_SET_COLOUR_MAP -> {
                    input.skipBytes(3)
                    val colours = input.readUnsignedShort()
                    input.skipBytes(colours * 6)
                }
                RfbProtocol.MSG_BELL -> Unit
                RfbProtocol.MSG_CUT_TEXT -> {
                    input.skipBytes(3)
                    val length = input.readInt()
                    val text = ByteArray(length).also(input::readFully)
                    listener.onClipboard(String(text, Charsets.ISO_8859_1))
                }
                else -> throw IOException("unknown server message $type")
            }
            synchronized(writeLock) {
                output.write(RfbProtocol.updateRequest(incremental = true, 0, 0, width, height))
                output.flush()
            }
        }
    }

    private fun readUpdate() {
        input.skipBytes(1)
        val rects = input.readUnsignedShort()
        repeat(rects) {
            val x = input.readUnsignedShort()
            val y = input.readUnsignedShort()
            val w = input.readUnsignedShort()
            val h = input.readUnsignedShort()
            when (input.readInt()) {
                RfbProtocol.ENCODING_RAW -> readRaw(x, y, w, h)
                RfbProtocol.ENCODING_COPY_RECT -> {
                    val sx = input.readUnsignedShort()
                    val sy = input.readUnsignedShort()
                    RfbProtocol.copyRect(framebuffer, width, sx, sy, x, y, w, h)
                    listener.onUpdate(framebuffer, width, x, y, w, h)
                }
                RfbProtocol.ENCODING_DESKTOP_SIZE -> {
                    width = w
                    height = h
                    allocate()
                    listener.onResize(width, height)
                }
                else -> throw IOException("unexpected encoding")
            }
        }
    }

    private var rowBuffer = ByteArray(0)

    private fun readRaw(x: Int, y: Int, w: Int, h: Int) {
        val rowBytes = w * 4
        if (rowBuffer.size < rowBytes) rowBuffer = ByteArray(rowBytes)
        for (row in 0 until h) {
            input.readFully(rowBuffer, 0, rowBytes)
            RfbProtocol.decodeRow(rowBuffer, framebuffer, (y + row) * width + x, w)
        }
        listener.onUpdate(framebuffer, width, x, y, w, h)
    }

    fun pointer(buttonMask: Int, x: Int, y: Int) = send(
        RfbProtocol.pointerEvent(buttonMask, x.coerceIn(0, width - 1), y.coerceIn(0, height - 1))
    )

    fun key(keysym: Int, down: Boolean) = send(RfbProtocol.keyEvent(keysym, down))

    fun cutText(text: String) = send(RfbProtocol.clientCutText(text))

    private fun send(bytes: ByteArray) {
        runCatching {
            synchronized(writeLock) {
                output.write(bytes)
                output.flush()
            }
        }
    }

    fun close() {
        runCatching { socket.close() }
    }

    private fun allocate() {
        framebuffer = IntArray(width * height)
    }

    private fun readString(): String {
        val length = input.readInt()
        return String(ByteArray(length).also(input::readFully), Charsets.UTF_8)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 3_000
    }
}
