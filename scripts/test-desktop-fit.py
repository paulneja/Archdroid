#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise the nested desktop fitter against a real, isolated X server."""
import ctypes as c
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import unittest


@unittest.skipUnless(shutil.which('Xvfb') and shutil.which('xrandr'), 'requires Xvfb and xrandr')
class DesktopFitTest(unittest.TestCase):
    def test_rotation_preserves_other_windows_and_exits_with_session(self):
        with tempfile.TemporaryDirectory() as tmp:
            number = Path(tmp) / 'display'
            with number.open('w') as output:
                server = subprocess.Popen(['Xvfb', '-displayfd', str(output.fileno()), '-screen', '0', '800x600x24'],
                                          pass_fds=(output.fileno(),), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            parent = subprocess.Popen(['sleep', '30'])
            fitter = None
            connection = None
            libc = c.CDLL(None)
            libc.prctl(15, b'kwin_wayland', 0, 0, 0)
            try:
                self.wait_for(lambda: number.read_text().strip())
                env = dict(os.environ, DISPLAY=':' + number.read_text().strip())
                x = c.CDLL('libX11.so.6')

                def bind(name, result, *args):
                    fn = getattr(x, name)
                    fn.restype, fn.argtypes = result, args
                    return fn

                d, w, u, i = c.c_void_p, c.c_ulong, c.c_uint, c.c_int
                connection = bind('XOpenDisplay', d, c.c_char_p)(env['DISPLAY'].encode())
                root = bind('XDefaultRootWindow', w, d)(connection)
                create = bind('XCreateSimpleWindow', w, d, w, i, i, u, u, u, w, w)
                nested = create(connection, root, 13, 17, 320, 240, 0, 0, 0)
                unrelated = create(connection, root, 7, 9, 80, 60, 0, 0, 0)
                atom = bind('XInternAtom', w, d, c.c_char_p, i)(connection, b'_NET_WM_PID', 0)
                pid = w(os.getpid())
                bind('XChangeProperty', i, d, w, w, w, i, i, c.c_void_p, i)(connection, nested, atom, 6, 32, 0, c.byref(pid), 1)
                map_window = bind('XMapWindow', i, d, w)
                map_window(connection, nested)
                map_window(connection, unrelated)
                bind('XSync', i, d, i)(connection, 0)
                get_geometry = bind('XGetGeometry', i, d, w, c.POINTER(w), c.POINTER(i), c.POINTER(i),
                                    c.POINTER(u), c.POINTER(u), c.POINTER(u), c.POINTER(u))

                def bounds(window):
                    r, left, top, width, height, border, depth = w(), i(), i(), u(), u(), u(), u()
                    get_geometry(connection, window, c.byref(r), c.byref(left), c.byref(top),
                                 c.byref(width), c.byref(height), c.byref(border), c.byref(depth))
                    return left.value, top.value, width.value, height.value

                script = Path(__file__).resolve().parents[1] / 'app/src/main/assets/desktop/fit-nested.py'
                fitter = subprocess.Popen(['python3', str(script), str(parent.pid)], env=env, stderr=subprocess.PIPE)
                self.wait_for(lambda: bounds(nested) == (0, 0, 800, 600))
                self.assertEqual((7, 9, 80, 60), bounds(unrelated))
                # Shrink the host, then grow it back: the compositor follows both changes.
                subprocess.run(['xrandr', '--fb', '640x480'], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                self.wait_for(lambda: bounds(nested) == (0, 0, 640, 480))
                subprocess.run(['xrandr', '--fb', '800x600'], env=env, check=True, stdout=subprocess.DEVNULL)
                self.wait_for(lambda: bounds(nested) == (0, 0, 800, 600))
                self.assertEqual((7, 9, 80, 60), bounds(unrelated))
                parent.terminate()
                parent.wait(timeout=3)
                self.assertEqual(0, fitter.wait(timeout=4), fitter.stderr.read().decode())
            finally:
                libc.prctl(15, b'python3', 0, 0, 0)
                if fitter is not None:
                    if fitter.poll() is None:
                        fitter.terminate()
                    fitter.communicate(timeout=3)
                if connection:
                    bind('XCloseDisplay', i, d)(connection)
                parent.terminate()
                parent.wait(timeout=3)
                server.terminate()
                server.wait(timeout=3)

    @staticmethod
    def wait_for(condition):
        deadline = time.monotonic() + 4
        while time.monotonic() < deadline:
            if condition():
                return
            time.sleep(.03)
        raise AssertionError('X11 geometry did not follow the host display')


if __name__ == '__main__':
    unittest.main()
