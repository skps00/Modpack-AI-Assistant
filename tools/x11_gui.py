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



def _title(d, w):
    """Window title, tolerating clients that only set _NET_WM_NAME (UTF8)."""
    t = ""
    try:
        raw = w.get_wm_name()
        t = (raw.decode("utf-8", "replace") if isinstance(raw, bytes) else raw) or ""
    except Exception:
        t = ""
    if not t:
        try:
            prop = w.get_full_property(d.intern_atom("_NET_WM_NAME"), 0)
            if prop and prop.value:
                v = prop.value
                t = (v.decode("utf-8", "replace") if isinstance(v, bytes) else str(v)) or ""
        except Exception:
            t = ""
    return t.strip()


def windows(d):
    """Top-level windows with a title OR a dialog-sized geometry.

    Returns (id, title, x, y, w, h). Untitled-but-large windows matter: Qt dialogs on
    this box set only _NET_WM_NAME, and we must not miss them.
    """
    root = d.screen().root
    out = []
    for w in root.query_tree().children:
        try:
            title = _title(d, w)
            g = w.get_geometry()
            if title or (g.width >= 300 and g.height >= 150):
                out.append((hex(w.id), title or "(untitled)", g.x, g.y, g.width, g.height))
        except Exception:
            continue
    return out


def _windows_cli():
    d = d0()
    for wid, title, x, y, w, h in windows(d):
        print("%s|%s|%dx%d+%d+%d" % (wid, title, w, h, x, y))
    return 0



def _find(d, needle):
    return [w for w in windows(d) if needle.lower() in w[1].lower()]


def wizard(d, rounds=25):
    """Walk Prism's first-run Quick Setup wizard: Escape account dialogs, Tab+Return to advance."""
    for i in range(rounds):
        dlg = [w for w in windows(d) if "account" in w[1].lower() and
               ("add" in w[1].lower() or "microsoft" in w[1].lower())]
        if dlg:
            key(d, "Escape")
            time.sleep(0.6)
            continue
        wiz_w = _find(d, "Quick Setup")
        if not wiz_w:
            print("wizard gone after %d step(s)" % i)
            return 0
        key(d, "Tab")
        key(d, "Return")
        time.sleep(0.8)
    print("wizard: gave up after %d steps" % rounds)
    return 1


def _wizard_cli():
    d = d0()
    return wizard(d)


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
    elif cmd == "wizard":
        return _wizard_cli()
    elif cmd == "windows":
        return _windows_cli()
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
