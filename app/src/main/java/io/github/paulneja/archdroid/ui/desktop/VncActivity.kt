package io.github.paulneja.archdroid.ui.desktop

import android.app.Activity
import android.content.Context
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.Gravity
import android.view.InputDevice
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.paulneja.archdroid.R
import io.github.paulneja.archdroid.core.desktop.DesktopScripts
import io.github.paulneja.archdroid.core.desktop.vnc.RfbClient
import kotlin.concurrent.thread

/** Full-screen viewer for the VNC display engine. */
class VncActivity : Activity() {

    @Volatile
    private var client: RfbClient? = null
    @Volatile
    private var running = true
    private lateinit var view: VncView
    private lateinit var status: TextView
    private lateinit var inputManager: InputManager

    private val devices = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refreshDevices()
        override fun onInputDeviceRemoved(deviceId: Int) = refreshDevices()
        override fun onInputDeviceChanged(deviceId: Int) = refreshDevices()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        view = VncView(this) { client }
        status = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setText(R.string.desktop_connecting)
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0x66000000)
            addView(button(R.string.desktop_keyboard) { toggleKeyboard() })
            addView(button(R.string.desktop_touch_mode) { toggleTouchMode() })
        }
        setContentView(FrameLayout(this).apply {
            addView(view)
            addView(status, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
            addView(toolbar, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))
        })

        inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(devices, null)
        refreshDevices()
        view.requestFocus()
        connectLoop()
    }

    private fun button(label: Int, onClick: () -> Unit) = TextView(this).apply {
        setText(label)
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(32, 20, 32, 20)
        setOnClickListener { onClick() }
    }

    private fun connectLoop() = thread(name = "vnc") {
        while (running) {
            val rfb = RfbClient(object : RfbClient.Listener {
                override fun onResize(width: Int, height: Int) = view.resizeFramebuffer(width, height)
                override fun onUpdate(pixels: IntArray, width: Int, x: Int, y: Int, w: Int, h: Int) =
                    view.update(pixels, width, x, y, w, h)
            })
            try {
                rfb.connect("127.0.0.1", DesktopScripts.VNC_PORT)
                client = rfb
                runOnUiThread { status.text = "" }
                rfb.run()
            } catch (_: Exception) {
                runOnUiThread { status.setText(R.string.desktop_connecting) }
            } finally {
                client = null
                rfb.close()
            }
            if (running) Thread.sleep(RECONNECT_MILLIS)
        }
    }

    /** Mouse plugged in: use it directly. Unplugged: back to the touchpad and its cursor. */
    private fun refreshDevices() {
        val hasMouse = InputDevice.getDeviceIds().any { id ->
            InputDevice.getDevice(id)?.let { !it.isVirtual && it.supportsSource(InputDevice.SOURCE_MOUSE) } == true
        }
        view.mouseConnected = hasMouse
    }

    private fun toggleKeyboard() {
        view.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun toggleTouchMode() {
        view.touchMode = if (view.touchMode == VncView.TouchMode.TOUCHPAD) {
            VncView.TouchMode.DIRECT
        } else {
            VncView.TouchMode.TOUCHPAD
        }
    }

    override fun onDestroy() {
        running = false
        client?.close()
        inputManager.unregisterInputDeviceListener(devices)
        super.onDestroy()
    }

    private companion object {
        const val RECONNECT_MILLIS = 1_000L
    }
}
