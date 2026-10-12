#!/usr/bin/env python3
"""X11 click/keys helper (XTest) for driving GUI apps on :0.

Usage:
  python xclick.py click X Y          # left click at absolute coords
  python xclick.py dclick X Y         # double click
  python xclick.py key Return [Alt_L] # press keysym with optional modifiers
  python xclick.py type "some text"   # type ASCII text
  python xclick.py move X Y
"""
import sys
import time

from Xlib import X, XK, display
from Xlib.ext import xtest


def d0():
    return display.Display(":0")


def move(d, x, y):
    xtest.fake_input(d, X.MotionNotify, x=int(x), y=int(y))
    d.sync()


def click(d, x, y, double=False, button=1):
    move(d, x, y)
    time.sleep(0.15)
    xtest.fake_input(d, X.ButtonPress, button)
    xtest.fake_input(d, X.ButtonRelease, button)
    d.sync()
    if double:
        time.sleep(0.08)
        xtest.fake_input(d, X.ButtonPress, button)
        xtest.fake_input(d, X.ButtonRelease, button)
        d.sync()


def key(d, keysym, mods=()):
    kc = d.keysym_to_keycode(XK.string_to_keysym(keysym))
    mc = [d.keysym_to_keycode(XK.string_to_keysym(m)) for m in mods]
    for m in mc:
        xtest.fake_input(d, X.KeyPress, m)
    xtest.fake_input(d, X.KeyPress, kc)
    xtest.fake_input(d, X.KeyRelease, kc)
    for m in reversed(mc):
        xtest.fake_input(d, X.KeyRelease, m)
    d.sync()


def type_text(d, text):
    for ch in text:
        if ch == " ":
            ks = "space"
        elif ch == "/":
            ks = "slash"
        elif ch == ".":
            ks = "period"
        elif ch == "-":
            ks = "minus"
        elif ch == "_":
            key(d, "minus", mods=("Shift_L",))
            continue
        else:
            ks = ch
        try:
            key(d, ks)
        except Exception as exc:
            print("skip %r: %r" % (ch, exc))
        time.sleep(0.03)


def main() -> int:
    a = sys.argv[1:]
    d = d0()
    if not a:
        print(__doc__)
        return 2
    cmd = a[0]
    if cmd == "click":
        click(d, int(a[1]), int(a[2]))
    elif cmd == "dclick":
        click(d, int(a[1]), int(a[2]), double=True)
    elif cmd == "move":
        move(d, int(a[1]), int(a[2]))
    elif cmd == "key":
        key(d, a[1], a[2:])
    elif cmd == "type":
        type_text(d, a[1])
    else:
        print("unknown command %r" % cmd)
        return 2
    time.sleep(0.2)
    print("ok")
    return 0


if __name__ == "__main__":
    sys.exit(main())
