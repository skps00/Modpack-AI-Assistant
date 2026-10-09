#!/usr/bin/env python3
"""顧問引擎「示範原型」（唔係產品碼；plan v4 未批實作）。

目的：用**真實 ATM8 配方數據**，行一次 plan v4 描述嘅流程，睇下輸出會係咩樣：
  1. 由 jar 讀配方 → 建 item→recipe 索引（含 recipe type／catalyst proxy）
  2. 由目標物品做**依賴閉包**（BFS；含 tag 展開、循環偵測）
  3. 只計**固定比例**鏈 → 合計基礎材料（§3 契約：值＋來源＋未計入）
  4. 有機率／算唔到 → 出「我唔確定」或「另有機率加成，數值未有資料」

用法：
  python tools/p0_engine_demo.py --item mekanism:basic_control_circuit
  python tools/p0_engine_demo.py --item minecraft:anvil --depth 6
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import zipfile
from collections import Counter, defaultdict, deque

PRISM = os.environ.get("PACKAI_PRISM", r"C:\Users\skps9\Documents\PrismLauncher-Windows-MinGW-w64-Portable-11.1.0")
INSTANCE = "packai_sandbox_atm8"
CACHE = os.path.join(os.path.expandvars(r"%LOCALAPPDATA%"), "Temp", "p0_engine_index.json")

RECIPE_RE = re.compile(r"^(?:.*/)?data/([a-z0-9_]+)/recipes?/(.+)\.json$", re.I)
TAG_FILE_RE = re.compile(r"^data/([a-z0-9_]+)/tags/items?/(.+)\.json$", re.I)
CHANCE_KEYS = ("chance", "probability", "chance_percent")


def _flatten_ids(v, out):
    """由 ingredient／result 值抽出 item id（或 #tag）；碰到未知包裝會遞歸入去。"""
    if isinstance(v, str):
        if ":" in v or v.startswith("#"):
            out.append(v)
        return
    if isinstance(v, list):
        for x in v:
            _flatten_ids(x, out)
        return
    if isinstance(v, dict):
        if "tag" in v:                                 # 物品 tag → 加 '#' 標記
            tg = v["tag"]
            if isinstance(tg, str) and ":" in tg:
                out.append("#" + tg)
        known = ("item", "value", "values", "ingredient", "items",
                 "output", "result", "results", "outputs")
        hit = False
        for k in known:
            if k in v:
                _flatten_ids(v[k], out)
                hit = True
        skip = set(known) | {"count", "amount", "type", "chance", "probability",
                             "chance_percent", "nbt", "components"}
        for k, kk in v.items():                       # shaped 嘅 A/B/C 等自訂包裝
            if k not in skip and isinstance(kk, (dict, list)):
                _flatten_ids(kk, out)


def _counts(v, default=1):
    if isinstance(v, dict):
        c = v.get("count")
        if isinstance(c, int):
            return c
        if isinstance(c, dict):          # 區間 → 當變動
            return None
        if isinstance(c, (float, str)):
            return None
        return default
    return default


def build_index(inst_dir: str, force: bool = False) -> dict:
    if os.path.exists(CACHE) and not force:
        with open(CACHE, encoding="utf-8") as fh:
            return json.load(fh)

    mods = os.path.join(inst_dir, "minecraft", "mods")
    produces: dict[str, list] = defaultdict(list)      # item -> [recipe]
    tag_items: dict[str, list] = defaultdict(list)     # tag -> [item/tag]

    def take_recipe(raw: bytes, path: str):
        try:
            o = json.loads(raw.decode("utf-8", "replace"))
        except Exception:
            return
        if not isinstance(o, dict):
            return
        rtype = str(o.get("type", "?"))
        outs, ins = [], []

        def collect(v, ignore_tags: bool):
            """通用抽取：key 含 input/ingredient → 材料；含 output/result → 產出。
            非物品（chemical／fluid／gas）分支唔當 item tag。"""
            if isinstance(v, dict):
                for k, kk in v.items():
                    kl = k.lower()
                    skip_tag = ignore_tags or any(t in kl for t in ("chemical", "gas", "fluid", "energy", "heat"))
                    if any(t in kl for t in ("ingredient", "input", "catalyst")) or kl == "key":
                        _flatten_ids(kk, ins)
                        if skip_tag:
                            ins[:] = [x for x in ins if not x.startswith("#")]
                    elif any(t in kl for t in ("output", "result")):
                        _flatten_ids(kk, outs)
                    collect(kk, skip_tag)
            elif isinstance(v, list):
                for x in v:
                    collect(x, ignore_tags)

        collect(o, False)
        outs = [x for x in outs if isinstance(x, str) and ":" in x and not x.startswith("#")]
        ins = [x for x in ins if isinstance(x, str) and (":" in x or x.startswith("#"))]
        ins = sorted(set(x for x in ins if not x.endswith((".png", ".json", ".ogg", ".mcmeta"))))
        if not outs:
            return
        blobby = json.dumps(o)[:6000]
        chance = any(k in blobby for k in CHANCE_KEYS)
        cnt = _counts(o.get("result") if isinstance(o.get("result"), dict) else {},
                      _counts(o.get("output") if isinstance(o.get("output"), dict) else {},
                              _counts(o.get("count", 1))))
        for oid in outs:
            produces[oid].append({"type": rtype, "in": ins, "chance": chance, "n": cnt})

    def take_tag(raw: bytes, path: str):
        m = TAG_FILE_RE.match(path.replace("\\", "/"))
        if not m:
            return
        try:
            o = json.loads(raw.decode("utf-8", "replace"))
        except Exception:
            return
        vals = o.get("values") if isinstance(o, dict) else None
        if not vals:
            return
        ids = []
        _flatten_ids(vals, ids)
        tag_items[m.group(1) + ":" + m.group(2)] = [i if i.startswith("#") else i for i in ids]

    for fn in sorted(os.listdir(mods)):
        if not fn.lower().endswith(".jar"):
            continue
        try:
            with zipfile.ZipFile(os.path.join(mods, fn)) as zf:
                for zi in zf.infolist():
                    n = zi.filename
                    if not n.endswith(".json"):
                        continue
                    if "/recipes/" in n or "/recipe/" in n:
                        if RECIPE_RE.match(n):
                            take_recipe(zf.read(zi), n)
                    elif "/tags/items/" in n or "/tags/item/" in n:
                        take_tag(zf.read(zi), n)
        except Exception:
            continue

    # 縮細 cache：只留 item id 同關鍵欄位
    data = {
        "produces": {k: v for k, v in produces.items()},
        "tags": {k: v[:60] for k, v in tag_items.items()},
    }
    with open(CACHE, "w", encoding="utf-8") as fh:
        json.dump(data, fh)
    return data


def expand_tag(tag: str, tags: dict, depth: int = 0) -> list[str]:
    key = tag[1:] if tag.startswith("#") else tag
    vals = tags.get(key, [])
    out = []
    for v in vals:
        if v.startswith("#") and depth < 3:
            out.extend(expand_tag(v, tags, depth + 1))
        elif not v.startswith("#"):
            out.append(v)
    return out


def closure(item: str, idx: dict, max_depth: int, tally: Counter, depth: int = 0,
            path: tuple = (), seen: set | None = None, lines: list | None = None,
            limit: int = 4000) -> None:
    """BFS/DFS 依賴閉包；固定比例就加落 tally，否則標『未計入』。"""
    seen = seen if seen is not None else set()
    lines = lines if lines is not None else []
    if len(lines) > limit:
        return
    pad = "  " * depth
    if item in path:                                   # 循環
        lines.append(f"{pad}↻ {item}（循環，已停止展開）")
        return
    recs = idx["produces"].get(item)
    if not recs:                                       # 葉＝基礎材料
        tally[item] += 1
        lines.append(f"{pad}• {item}（基礎材料）")
        return
    recs = sorted(recs, key=lambda r: (r["chance"], len(r["in"])))
    r = recs[0]
    flag = " ⚠機率" if r["chance"] else ""
    lines.append(f"{pad}{item} ← [{r['type']}]{flag} 需要 {len(r['in'])} 種材料"
                 + (f"；另有 {len(recs)-1} 條替代配方" if len(recs) > 1 else ""))
    if depth >= max_depth or r["chance"]:
        if r["chance"]:
            lines.append(f"{pad}  └ 機率配方 → **未計入**（數值未有資料）")
        tally["(未展開)"] += 1
        return
    key = (item, r["type"])
    if key in seen:
        return
    seen.add(key)
    for ing in r["in"]:
        if ing.startswith("#"):
            cand = expand_tag(ing, idx["tags"])
            if not cand:
                lines.append(f"{pad}  └ {ing} → **我唔確定**（tag 解唔到）")
                continue
            closure(cand[0], idx, max_depth, tally, depth + 1, path + (item,), seen, lines, limit)
            lines[-1] += f"   〔tag {ing} 展開成 {len(cand)} 個候選，取第一個〕"
        else:
            closure(ing, idx, max_depth, tally, depth + 1, path + (item,), seen, lines, limit)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--item", default="mekanism:basic_control_circuit")
    ap.add_argument("--depth", type=int, default=4)
    ap.add_argument("--rebuild", action="store_true")
    args = ap.parse_args()

    inst = os.path.join(PRISM, "instances", INSTANCE)
    print(f"包：{INSTANCE}\n目標物品：{args.item}\n")
    idx = build_index(inst, args.rebuild)
    print(f"索引：{len(idx['produces']):,} 個可合成物品、{len(idx['tags']):,} 個 item tag\n")

    recs = idx["produces"].get(args.item, [])
    if not recs:
        print("→ 引擎答：**我唔確定**（呢個包嘅配方索引冇呢件嘢嘅配方）")
        return 0

    print("── ① 邊啲配方做得出呢件嘢 ──")
    for r in sorted(recs, key=lambda x: len(x["in"]))[:5]:
        print(f"  [{r['type']}] 材料 {len(r['in'])} 種" + ("  ⚠機率" if r["chance"] else "") +
              f"  例：{', '.join(r['in'][:4])}")
    print()

    print(f"── ② 依賴閉包（深度 {args.depth}；每層只揀最簡單配方）──")
    tally: Counter = Counter()
    lines: list = []
    closure(args.item, idx, args.depth, tally, lines=lines)
    for l in lines[:60]:
        print("  " + l)
    print()

    print("── ③ 合計基礎材料（只計固定比例鏈）──")
    for k, v in tally.most_common(12):
        print(f"  {k:42s} × {v}")
    print()

    print("── ④ 引擎輸出（plan §3 契約）──")
    prob = sum(1 for r in recs if r["chance"])
    print(f"  • 做 1 個 {args.item}：保證要走 {len(lines)} 步（見上）")
    print(f"  • 基礎材料需求：見 ③（每個數值來源＝JEI/配方 JSON）")
    if prob:
        print(f"  • 未計入：{prob}/{len(recs)} 條替代配方含**機率產出** → 「另有機率加成，數值未有資料」")
    print(f"  • 循環／deep>{args.depth}：已停止展開，標明未計入（誠實 > 扮全知）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
