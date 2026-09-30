#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later

import ctypes as c
import os
import select
import sys

def main(parent):
    x = c.CDLL("libX11.so.6")
    display = c.c_void_p
    window = c.c_ulong
    uint = c.c_uint
    ulong = c.c_ulong
    integer = c.c_int
    ptr = c.POINTER

    def bind(name, result, *args):
        fn = getattr(x, name)
        fn.restype, fn.argtypes = result, args
        return fn

    open_display = bind("XOpenDisplay", display, c.c_char_p)
    root_window = bind("XDefaultRootWindow", window, display)
    atom = bind("XInternAtom", ulong, display, c.c_char_p, integer)
    query = bind("XQueryTree", integer, display, window, ptr(window), ptr(window), ptr(ptr(window)), ptr(uint))
    geometry = bind("XGetGeometry", integer, display, window, ptr(window), ptr(integer), ptr(integer), ptr(uint), ptr(uint), ptr(uint), ptr(uint))
    property_ = bind("XGetWindowProperty", integer, display, window, ulong, c.c_long, c.c_long, integer, ulong,
                     ptr(ulong), ptr(integer), ptr(ulong), ptr(ulong), ptr(c.c_void_p))
    resize = bind("XMoveResizeWindow", integer, display, window, integer, integer, uint, uint)
    free = bind("XFree", integer, c.c_void_p)
    flush = bind("XFlush", integer, display)
    pending = bind("XPending", integer, display)
    next_event = bind("XNextEvent", integer, display, c.c_void_p)
    connection = bind("XConnectionNumber", integer, display)
    select_input = bind("XSelectInput", integer, display, window, c.c_long)
    close = bind("XCloseDisplay", integer, display)

    handler_type = c.CFUNCTYPE(integer, display, c.c_void_p)
    ignore_error = handler_type(lambda *_: 0)
    bind("XSetErrorHandler", c.c_void_p, handler_type)(ignore_error)
    d = open_display(None)
    if not d:
        return
    root = root_window(d)
    pid_atom = atom(d, b"_NET_WM_PID", 0)
    select_input(d, root, (1 << 17) | (1 << 19))

    def bounds(w):
        r, left, top = window(), integer(), integer()
        width, height, border, depth = uint(), uint(), uint(), uint()
        if not geometry(d, w, c.byref(r), c.byref(left), c.byref(top), c.byref(width), c.byref(height), c.byref(border), c.byref(depth)):
            return None
        return left.value, top.value, width.value, height.value

    def compositor(w):
        kind, count, remaining, fmt, data = ulong(), ulong(), ulong(), integer(), c.c_void_p()
        property_(d, w, pid_atom, 0, 1, 0, 6, c.byref(kind), c.byref(fmt), c.byref(count), c.byref(remaining), c.byref(data))
        try:
            if kind.value != 6 or fmt.value != 32 or count.value != 1 or not data:
                return False
            pid = c.cast(data, ptr(ulong))[0]
            with open(f"/proc/{pid}/comm") as comm:
                return comm.read().strip() in {"kwin_wayland", "labwc"} and os.stat(f"/proc/{pid}").st_uid == os.getuid()
        except OSError:
            return False
        finally:
            if data:
                free(data)

    event = (c.c_long * 24)()
    try:
        while os.path.exists(f"/proc/{parent}"):
            while pending(d):
                next_event(d, c.byref(event))
            screen = bounds(root)
            children, count, r, p = ptr(window)(), uint(), window(), window()
            if screen and query(d, root, c.byref(r), c.byref(p), c.byref(children), c.byref(count)):
                try:
                    target = (0, 0, screen[2], screen[3])
                    for i in range(count.value):
                        w = children[i]
                        if compositor(w) and bounds(w) != target:
                            resize(d, w, *target)
                finally:
                    if children:
                        free(children)
            flush(d)
            if not pending(d):
                select.select([connection(d)], [], [], 2)
    finally:
        close(d)

if __name__ == "__main__":
    main(int(sys.argv[1]))
