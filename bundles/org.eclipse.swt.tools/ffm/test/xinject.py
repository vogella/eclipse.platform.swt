# Moves the pointer, clicks, scrolls and types through XTest, for FFMCrossCheck on GTK4.
import ctypes
import sys
import time

x11 = ctypes.CDLL("libX11.so.6")
xtst = ctypes.CDLL("libXtst.so.6")
x11.XOpenDisplay.restype = ctypes.c_void_p
display = ctypes.c_void_p(x11.XOpenDisplay(None))


def flush():
    x11.XFlush(display)
    time.sleep(0.2)


def motion(x, y):
    xtst.XTestFakeMotionEvent(display, 0, x, y, 0)
    flush()


def button(number):
    xtst.XTestFakeButtonEvent(display, number, 1, 0)
    xtst.XTestFakeButtonEvent(display, number, 0, 0)
    flush()


def key(keycode):
    xtst.XTestFakeKeyEvent(display, keycode, 1, 0)
    xtst.XTestFakeKeyEvent(display, keycode, 0, 0)
    flush()


time.sleep(float(sys.argv[1]) if len(sys.argv) > 1 else 1.0)
for x, y in ((40, 40), (60, 50), (80, 70)):
    motion(x, y)
button(1)
button(4)
button(5)
key(38)
key(39)
motion(90, 90)
