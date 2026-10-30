"""Klickt oder drückt eine Taste auf einem X-Bildschirm (für automatische Client-Screenshots).

Aufruf:
  DISPLAY=:97 python tools/xclick.py <x> <y>     Linksklick an (x, y)
  DISPLAY=:97 python tools/xclick.py key <name>  Taste drücken, z. B. "e"
Benötigt python-xlib (pip install python-xlib).
"""
import sys
import time

from Xlib import X, XK, display
from Xlib.ext import xtest

d = display.Display()
if sys.argv[1] == 'key':
    code = d.keysym_to_keycode(XK.string_to_keysym(sys.argv[2]))
    xtest.fake_input(d, X.KeyPress, code)
    d.sync()
    time.sleep(0.1)
    xtest.fake_input(d, X.KeyRelease, code)
    d.sync()
else:
    x, y = int(sys.argv[1]), int(sys.argv[2])
    d.screen().root.warp_pointer(x, y)
    d.sync()
    time.sleep(0.2)
    xtest.fake_input(d, X.ButtonPress, 1)
    d.sync()
    time.sleep(0.1)
    xtest.fake_input(d, X.ButtonRelease, 1)
    d.sync()
