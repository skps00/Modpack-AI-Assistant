#!/usr/bin/env python3
"""X11 screenshot for this server (GNOME's D-Bus screenshot API denies us).

Grabs the whole root window on :0 via python-xlib ZPixmap and saves a PNG.
Usage: python xshot.py [out.png] [x] [y] [w] [h]
"""
import sys

from PIL import Image
from Xlib import display


def grab(path="/tmp/xshot.png", box=None):
    d = display.Display(":0")
    root = d.screen().root
    geom = root.get_geometry()
    x, y, w, h = 0, 0, geom.width, geom.height
    if box:
        x, y, w, h = box
    raw = root.get_image(x, y, w, h, 2, 0xFFFFFFFF)  # ZPixmap, all planes
    data = raw.data
    img = Image.frombytes("RGB", (w, h), bytes(data), "raw", "BGRX")
    img.save(path)
    print("saved %s (%dx%d) from (%d,%d)" % (path, w, h, x, y))
    return path


if __name__ == "__main__":
    args = sys.argv[1:]
    if len(args) >= 5:
        grab(args[0], (int(args[1]), int(args[2]), int(args[3]), int(args[4])))
    elif args:
        grab(args[0])
    else:
        grab()
