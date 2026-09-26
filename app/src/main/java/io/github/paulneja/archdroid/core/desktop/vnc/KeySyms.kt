package io.github.paulneja.archdroid.core.desktop.vnc

import android.view.KeyEvent

/** Android key codes and characters to X11 keysyms. */
object KeySyms {
    private val SPECIAL = mapOf(
        KeyEvent.KEYCODE_DEL to 0xff08, // BackSpace
        KeyEvent.KEYCODE_TAB to 0xff09,
        KeyEvent.KEYCODE_ENTER to 0xff0d,
        KeyEvent.KEYCODE_NUMPAD_ENTER to 0xff8d,
        KeyEvent.KEYCODE_ESCAPE to 0xff1b,
        KeyEvent.KEYCODE_FORWARD_DEL to 0xffff,
        KeyEvent.KEYCODE_MOVE_HOME to 0xff50,
        KeyEvent.KEYCODE_DPAD_LEFT to 0xff51,
        KeyEvent.KEYCODE_DPAD_UP to 0xff52,
        KeyEvent.KEYCODE_DPAD_RIGHT to 0xff53,
        KeyEvent.KEYCODE_DPAD_DOWN to 0xff54,
        KeyEvent.KEYCODE_PAGE_UP to 0xff55,
        KeyEvent.KEYCODE_PAGE_DOWN to 0xff56,
        KeyEvent.KEYCODE_MOVE_END to 0xff57,
        KeyEvent.KEYCODE_INSERT to 0xff63,
        KeyEvent.KEYCODE_SHIFT_LEFT to 0xffe1,
        KeyEvent.KEYCODE_SHIFT_RIGHT to 0xffe2,
        KeyEvent.KEYCODE_CTRL_LEFT to 0xffe3,
        KeyEvent.KEYCODE_CTRL_RIGHT to 0xffe4,
        KeyEvent.KEYCODE_CAPS_LOCK to 0xffe5,
        KeyEvent.KEYCODE_META_LEFT to 0xffeb,
        KeyEvent.KEYCODE_META_RIGHT to 0xffec,
        KeyEvent.KEYCODE_ALT_LEFT to 0xffe9,
        KeyEvent.KEYCODE_ALT_RIGHT to 0xfe03, // ISO_Level3_Shift (AltGr)
        KeyEvent.KEYCODE_SPACE to 0x0020,
        KeyEvent.KEYCODE_F1 to 0xffbe, KeyEvent.KEYCODE_F2 to 0xffbf, KeyEvent.KEYCODE_F3 to 0xffc0,
        KeyEvent.KEYCODE_F4 to 0xffc1, KeyEvent.KEYCODE_F5 to 0xffc2, KeyEvent.KEYCODE_F6 to 0xffc3,
        KeyEvent.KEYCODE_F7 to 0xffc4, KeyEvent.KEYCODE_F8 to 0xffc5, KeyEvent.KEYCODE_F9 to 0xffc6,
        KeyEvent.KEYCODE_F10 to 0xffc7, KeyEvent.KEYCODE_F11 to 0xffc8, KeyEvent.KEYCODE_F12 to 0xffc9,
    )

    fun forKeyCode(keyCode: Int): Int? = SPECIAL[keyCode]

    /** Latin-1 maps 1:1; everything else uses the Unicode keysym range. */
    fun forCodePoint(codePoint: Int): Int = when {
        codePoint == '\n'.code -> 0xff0d
        codePoint in 0x20..0x7e || codePoint in 0xa0..0xff -> codePoint
        else -> 0x01000000 or codePoint
    }
}
