"""Promo motion-graphics v3 - 50s extended cut (title -> world -> cards -> 3 answers -> world -> end)."""
import os, sys, math, glob, subprocess
from PIL import Image, ImageDraw, ImageFont

T = os.path.expandvars(r"%LOCALAPPDATA%\Temp")
ROOT = os.path.join(T, "mg2")
OUT = os.path.join(T, "mg2_out")
FPS = 30
W, H = 1920, 1080
F_BLACK = r"C:\Windows\Fonts\ariblk.ttf"
F_SEMI = r"C:\Windows\Fonts\seguisb.ttf"
F_BOLD = r"C:\Windows\Fonts\segoeuib.ttf"
CYAN, VIOLET, AMBER = (34, 211, 238), (139, 92, 246), (245, 158, 11)
DARK = (9, 12, 17)
WX, WY, WW, WH = 180, 48, 1560, 878
CAP_Y = 962

CAP_A = "380 mods. You know what you want \u2014 not where it is."
CAP_B = "Ask in plain language.\nIn-game, on the spot."
CAP_C = "Hover an item. Hold Y.\nWhere it's from. What it's for. How to make it."
CAP_D = "Mined, looted, traded \u2014 read from the pack itself.\nEvery source line, on the record."
CAP_E = "Free & open source  \u00b7  Forge 1.19.2  \u00b7  NeoForge"

# (kind, key, frames, caption, zoom0, zoom1)
TL = [
    ("card", "title", 102, None, 1.0, 1.0),
    ("seg",  "s1", 210, CAP_A, 1.0, 1.0),
    ("card", "c1", 90, None, 1.0, 1.0),
    ("seg",  "s2", 240, CAP_B, 1.0, 1.0),
    ("card", "c2", 75, None, 1.0, 1.0),
    ("seg",  "s3", 165, CAP_C, 1.0, 1.0),
    ("seg",  "s4", 198, CAP_D, 1.0, 1.0),
    ("card", "c3", 105, None, 1.0, 1.0),
    ("seg",  "s5", 174, CAP_E, 1.0, 1.0),
    ("card", "end", 150, None, 1.0, 1.0),
]
TOTAL = sum(x[2] for x in TL)


def font(p, s): return ImageFont.truetype(p, s)


def iso_cube(d, cx, cy, s, col):
    top = s * 0.55
    d.polygon([(cx - s, cy - s + top), (cx, cy - s + 2 * top), (cx, cy + s), (cx - s, cy + s - top)], fill=tuple(int(c * .55) for c in col))
    d.polygon([(cx + s, cy - s + top), (cx + s, cy + s - top), (cx, cy + s), (cx, cy - s + 2 * top)], fill=tuple(int(c * .78) for c in col))
    d.polygon([(cx, cy - s), (cx + s, cy - s + top), (cx, cy - s + 2 * top), (cx - s, cy - s + top)], fill=col)


def wrap(d, text, fnt, maxw):
    out = []
    for para in text.split("\n"):
        cur = ""
        for w in para.split(" "):
            t = (cur + " " + w).strip()
            if d.textlength(t, font=fnt) <= maxw:
                cur = t
            else:
                if cur: out.append(cur)
                cur = w
        out.append(cur)
    return out


def make_mask():
    m = Image.new("L", (WW, WH), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, WW - 1, WH - 1], 18, fill=255)
    return m


def zoom_crop(im, z):
    if z <= 1.0001: return im.resize((WW, WH), Image.LANCZOS)
    cw, ch = int(W / z), int(H / z)
    x, y = (W - cw) // 2, (H - ch) // 2
    return im.crop((x, y, x + cw, y + ch)).resize((WW, WH), Image.LANCZOS)


def header(d):
    f = font(F_SEMI, 24); d.ellipse([WX + 2, 20, WX + 14, 32], fill=CYAN)
    d.text((WX + 26, 14), "PACK AI ASSISTANT", font=f, fill=(150, 162, 178))
    fr = font(F_SEMI, 20); s = "In-game AI for heavy modpacks"
    d.text((W - WX - d.textlength(s, font=fr), 16), s, font=fr, fill=(96, 106, 122))


def progress(d, i):
    p = max(0.0, min(1.0, i / (TOTAL - 1)))
    d.rectangle([0, H - 4, W, H], fill=(255, 255, 255, 28))
    d.rectangle([0, H - 4, int(W * p), H], fill=(34, 211, 238, 225))
    # travelling shimmer (keeps even static footage feeling alive)
    sx = int((i * 9) % (W + 240)) - 120
    x0, x1 = max(0, sx), min(W, sx + 120)
    if x1 > x0:
        d.rectangle([x0, H - 4, x1, H], fill=(190, 250, 255, 70))


def caption(d, text, t_in_seg, seg_frames):
    al = 1.0
    if t_in_seg < 0.35: al = t_in_seg / 0.35
    elif t_in_seg > seg_frames / FPS - 0.25: al = max(0.0, (seg_frames / FPS - t_in_seg) / 0.25)
    al = max(0.0, min(1.0, al))
    ease = al * al * (3 - 2 * al)
    dur = seg_frames / FPS
    fnt = font(F_BLACK, 46)
    lines = wrap(d, text, fnt, 1600)
    lh = 54
    block_h = len(lines) * lh
    base_top = 1066 - block_h + 6          # bottom-anchored: never runs off the frame
    y0 = base_top - int(22 * (1 - ease))   # slides DOWN into place
    for i, ln in enumerate(lines):
        tw = d.textlength(ln, font=fnt); x = (W - tw) / 2; y = y0 + i * lh
        d.text((x + 3, y + 3), ln, font=fnt, fill=(0, 0, 0, int(180 * al)))
        d.text((x, y), ln, font=fnt, fill=(255, 255, 255, int(255 * al)))
    # accent bar: fixed position (never collides with the window), width reveals
    bw = int(190 * min(1.0, t_in_seg / 0.5)) if t_in_seg < 0.5 else 190
    if t_in_seg > dur - 0.35:
        bw = int(190 * al)
    bw = max(3, bw)
    bx = W / 2 - bw / 2
    by = base_top - 20
    d.rounded_rectangle([bx, by, bx + bw, by + 5], 2, fill=(34, 211, 238, int(235 * al)))


def title_card(prog):
    img = Image.new("RGB", (W, H), DARK); d = ImageDraw.Draw(img, "RGBA")
    for i, c in enumerate([CYAN, VIOLET, AMBER]):
        iso_cube(d, W / 2 - 150 + i * 150, 330 + math.sin(prog * 6 + i * .8) * 14, 52, c)
    al = min(1.0, max(0.0, prog / 0.45)); ease = al * al * (3 - 2 * al)
    ft = font(F_BLACK, 104); t1 = "PACK AI ASSISTANT"
    ty = 500 + int(26 * (1 - ease))
    d.text(((W - d.textlength(t1, font=ft)) / 2 + 4, ty + 4), t1, font=ft, fill=(0, 0, 0, int(150 * al)))
    d.text(((W - d.textlength(t1, font=ft)) / 2, ty), t1, font=ft, fill=(255, 255, 255, int(255 * al)))
    al2 = min(1.0, max(0.0, (prog - 0.35) / 0.45)); e2 = al2 * al2 * (3 - 2 * al2)
    f2 = font(F_BOLD, 40)
    for i, s in enumerate(["In-game AI for heavy modpacks", "Ask in plain language \u2014 it reads your pack, not a wiki"]):
        base = (34, 211, 238) if i == 0 else (150, 162, 178)
        d.text(((W - d.textlength(s, font=f2)) / 2, 640 + i * 54 + int(20 * (1 - e2))), s, font=f2, fill=base + (int(255 * al2),))
    return img


def end_card(prog):
    img = Image.new("RGB", (W, H), DARK); d = ImageDraw.Draw(img, "RGBA")
    for i, c in enumerate([CYAN, VIOLET, AMBER]):
        iso_cube(d, W / 2 - 100 + i * 100, 350 + math.sin(prog * 5 + i * .7) * 10, 40, c)
    al = min(1.0, max(0.0, prog / 0.5)); ease = al * al * (3 - 2 * al)
    ft = font(F_BLACK, 84); t1 = "PACK AI ASSISTANT"
    ey = 470 + int(24 * (1 - ease))
    d.text(((W - d.textlength(t1, font=ft)) / 2, ey), t1, font=ft, fill=(255, 255, 255, int(255 * al)))
    al2 = min(1.0, max(0.0, (prog - 0.4) / 0.5)); e2 = al2 * al2 * (3 - 2 * al2)
    f2 = font(F_BOLD, 38)
    for i, s in enumerate(["Free & open source  \u00b7  Forge 1.19.2  \u00b7  NeoForge", "Client-only \u2014 no server install"]):
        base = (34, 211, 238) if i == 0 else (150, 162, 178)
        d.text(((W - d.textlength(s, font=f2)) / 2, 600 + i * 56 + int(18 * (1 - e2))), s, font=f2, fill=base + (int(255 * al2),))
    return img


CARDS = {
 "c1": ("Grounded in YOUR pack", ["JEI-accurate recipes \u00b7 quest progress \u00b7 pack scripts", "No wiki guessing"]),
 "c2": ("Hover an item. Hold Y.", ["One item at a time \u2014 click another to switch"]),
 "c3": ("Reads what makes your pack different",
        ["KubeJS-modified items & recipes \u00b7 FTB quests", "Client-only \u00b7 your own key, or fully offline"]),
}


def info_card(key, prog):
    img = Image.new("RGB", (W, H), DARK); d = ImageDraw.Draw(img, "RGBA")
    head, subs = CARDS[key]
    for i, c in enumerate([CYAN, VIOLET, AMBER]):
        iso_cube(d, W / 2 - 76 + i * 76, 300 + math.sin(prog * 5 + i * .7) * 8, 30, c)
    al = min(1.0, max(0.0, prog / 0.35)); ease = al * al * (3 - 2 * al)
    ft = font(F_BLACK, 74)
    lines = wrap(d, head, ft, 1500)
    y = 430 + int(26 * (1 - ease))
    for ln in lines:
        d.text(((W - d.textlength(ln, font=ft)) / 2, y), ln, font=ft, fill=(255, 255, 255, int(255 * al))); y += 92
    al2 = min(1.0, max(0.0, (prog - 0.28) / 0.4)); e2 = al2 * al2 * (3 - 2 * al2)
    f2 = font(F_BOLD, 38)
    for s in subs:
        d.text(((W - d.textlength(s, font=f2)) / 2, y + 20 + int(18 * (1 - e2))), s, font=f2, fill=(34, 211, 238, int(255 * al2))); y += 62
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    mask = make_mask()
    cache, idx = {}, {}
    for k in ("s1", "s2", "s3", "s4", "s5"):
        cache[k] = sorted(glob.glob(os.path.join(ROOT, k, "f_*.png")))
    gi = 0; done = 0
    for (kind, key, n, cap, z0, z1) in TL:
        for j in range(n):
            t = j / FPS
            prog = t / max(1e-6, n / FPS)
            if kind == "card":
                frame = (title_card(t) if key == "title" else end_card(t) if key == "end" else info_card(key, t)).convert("RGBA")
            else:
                z = z0 + (z1 - z0) * (j / max(1, n - 1))
                base = Image.open(cache[key][j]).convert("RGB")
                frame = Image.new("RGBA", (W, H), DARK + (255,))
                sh = Image.new("RGBA", (W, H), (0, 0, 0, 0))
                ImageDraw.Draw(sh).rounded_rectangle([WX - 5, WY - 5, WX + WW + 5, WY + WH + 9], 22, fill=(0, 0, 0, 120))
                frame.alpha_composite(sh)
                PAD = 14
                iw, ih = WW - 2 * PAD, WH - 2 * PAD
                inner = zoom_crop(base, z).resize((iw, ih), Image.LANCZOS)
                frame.paste(inner.convert("RGBA"), (WX + PAD, WY + PAD), mask.resize((iw, ih)))
                ImageDraw.Draw(frame).rounded_rectangle([WX + PAD, WY + PAD, WX + PAD + iw - 1, WY + PAD + ih - 1], 14, outline=(255, 255, 255, 34), width=1)
            d = ImageDraw.Draw(frame, "RGBA")
            if kind == "seg":
                header(d); caption(d, cap, t, n)
            progress(d, gi)
            frame.convert("RGB").save(os.path.join(OUT, f"g_{gi:05d}.png"))
            gi += 1; done += 1
    print("frames", done, "=>", done / FPS, "s")


if __name__ == "__main__":
    main()
