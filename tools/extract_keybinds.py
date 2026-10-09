#!/usr/bin/env python3
"""「要撳咩掣先用得到某功能？」— 離線抽取全包按鍵表。

來源（兩個都係本機檔案，唔使開遊戲）：
  ① `<instance>/options.txt`  → 玩家**實際**目前綁定（key_<id>:<keycode>）
  ② 各 mod jar 嘅 lang 檔    → keybind 嘅**人話名**（key.<modid>.<name>）＋分類（key.categories.*）

輸出：每包一張表（功能名／mod／目前按鍵／有冇未綁／有冇衝突），另出 JSON。

衝突偵測＝同一個按鍵綁咗 2 個以上功能（ViewBoard mod 就係為咗解呢個痛點）。

用法：
  python tools/extract_keybinds.py --pack packai_sandbox_atm8 --out docs/research/artifacts/2026-10-10-keybinds-atm8.json
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import zipfile
from collections import defaultdict

PRISM = os.environ.get("PACKAI_PRISM", r"C:\Users\skps9\Documents\PrismLauncher-Windows-MinGW-w64-Portable-11.1.0")
KEY_RE = re.compile(r"^key\.(?!categories\.)([a-z0-9_]+)\.(.+)$")


def read_options(inst: str) -> dict[str, str]:
    p = os.path.join(inst, "minecraft", "options.txt")
    out: dict[str, str] = {}
    if not os.path.isfile(p):
        return out
    with open(p, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            if not line.startswith("key_"):
                continue
            k, _, v = line.strip().partition(":")
            out[k[4:]] = v
    return out


def scan_lang(inst: str) -> dict[str, tuple[str, str]]:
    """回傳 keybind id -> (人話名, mod jar)"""
    mods = os.path.join(inst, "minecraft", "mods")
    found: dict[str, tuple[str, str]] = {}
    if not os.path.isdir(mods):
        return found
    for fn in sorted(os.listdir(mods)):
        if not fn.lower().endswith(".jar"):
            continue
        try:
            with zipfile.ZipFile(os.path.join(mods, fn)) as zf:
                for zi in zf.infolist():
                    n = zi.filename
                    if not n.endswith("en_us.json") or "/lang/" not in n:
                        continue
                    try:
                        d = json.loads(zf.read(zi).decode("utf-8", "replace"))
                    except Exception:
                        continue
                    if not isinstance(d, dict):
                        continue
                    for k, v in d.items():
                        m = KEY_RE.match(k)
                        if m and isinstance(v, str) and k not in found:
                            # id 用**完整** lang key（例 key.packai.open）——options.txt 亦係用呢個 id
                            found[k] = (v, fn)
        except Exception:
            continue
    return found


def scan_categories(inst: str) -> dict[str, str]:
    mods = os.path.join(inst, "minecraft", "mods")
    cats: dict[str, str] = {}
    for fn in sorted(os.listdir(mods)) if os.path.isdir(mods) else []:
        if not fn.lower().endswith(".jar"):
            continue
        try:
            with zipfile.ZipFile(os.path.join(mods, fn)) as zf:
                for zi in zf.infolist():
                    if zi.filename.endswith("en_us.json") and "/lang/" in zi.filename:
                        try:
                            d = json.loads(zf.read(zi).decode("utf-8", "replace"))
                        except Exception:
                            continue
                        if isinstance(d, dict):
                            for k, v in d.items():
                                if k.startswith("key.categories.") and isinstance(v, str):
                                    cats.setdefault(k[len("key.categories."):], v)
        except Exception:
            continue
    return cats


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pack", default="packai_sandbox_atm8")
    ap.add_argument("--out", default="")
    ap.add_argument("--md", default="")
    args = ap.parse_args()

    inst = os.path.join(PRISM, "instances", args.pack)
    if not os.path.isdir(inst):
        print("instance 唔存在:", inst, file=sys.stderr)
        return 2

    opts = read_options(inst)
    names = scan_lang(inst)
    cats = scan_categories(inst)
    print(f"包：{args.pack}")
    print(f"options.txt 內已綁鍵：{len(opts)} 條")
    print(f"jar 內登記嘅 keybind（lang）：{len(names)} 條  ← 有啲 mod 冇 lang 就要靠遊戲內睇")
    print(f"按鍵分類（key.categories）：{len(cats)} 個")

    rows = []
    for kid, (label, jar) in sorted(names.items()):
        cur = opts.get(kid, "")
        rows.append({"id": kid, "label": label, "mod_jar": jar, "key": cur,
                     "bound": bool(cur) and cur.lower() not in ("unknown", "key.keyboard.unknown")})
    # 衝突：同一按鍵綁多個功能
    bykey = defaultdict(list)
    for r in rows:
        if r["bound"]:
            bykey[r["key"]].append(r["label"])
    conflicts = {k: v for k, v in bykey.items() if len(v) > 1}
    unbound = [r["label"] for r in rows if not r["bound"]]

    print(f"\n有綁定：{sum(1 for r in rows if r['bound'])}；未綁／未知：{len(unbound)}")
    print(f"**衝突**（同一鍵綁多個功能）：{len(conflicts)} 組")
    for k, v in sorted(conflicts.items(), key=lambda x: -len(x[1]))[:10]:
        print(f"   {k}: {len(v)} 個 → {', '.join(v[:4])}")
    print("\n未綁定例子（頭 10）：")
    for u in unbound[:10]:
        print("   -", u)

    if args.out:
        os.makedirs(os.path.dirname(args.out), exist_ok=True)
        with open(args.out, "w", encoding="utf-8") as fh:
            json.dump({"pack": args.pack, "rows": rows, "conflicts": conflicts,
                       "unbound": unbound, "categories": cats}, fh, ensure_ascii=False, indent=1)
        print("\nwritten:", args.out)

    if args.md:
        os.makedirs(os.path.dirname(args.md), exist_ok=True)
        with open(args.md, "w", encoding="utf-8") as fh:
            fh.write(f"# 按鍵表 — {args.pack}（離線抽取）\n\n")
            fh.write(f"- 已綁：{sum(1 for r in rows if r['bound'])}；未綁／未知：{len(unbound)}\n")
            fh.write(f"- 衝突：{len(conflicts)} 組\n\n| 功能 | mod | 目前按鍵 |\n|---|---|---|\n")
            for r in sorted(rows, key=lambda x: (not x["bound"], x["label"].lower())):
                mark = r["key"] if r["bound"] else "（未綁）"
                fh.write(f"| {r['label']} | {r['mod_jar'][:34]} | {mark} |\n")
            if conflicts:
                fh.write("\n## 衝突\n\n")
                for k, v in conflicts.items():
                    fh.write(f"- `{k}`：{'、'.join(v)}\n")
        print("written:", args.md)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
