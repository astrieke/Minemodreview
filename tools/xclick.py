"""Klickt auf einem X-Bildschirm an eine Stelle (für automatische Client-Screenshots).

Aufruf: DISPLAY=:97 python tools/xclick.py <x> <y>
Benötigt python-xlib (pip install python-xlib).
"""
import sys
import time

from Xlib import X, display
from Xlib.ext import xtest

d = display.Display()
x, y = int(sys.argv[1]), int(sys.argv[2])
d.screen().root.warp_pointer(x, y)
d.sync()
time.sleep(0.2)
xtest.fake_input(d, X.ButtonPress, 1)
d.sync()
time.sleep(0.1)
xtest.fake_input(d, X.ButtonRelease, 1)
d.sync()
