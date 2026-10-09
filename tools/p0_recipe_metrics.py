#!/usr/bin/env python3
"""P0 量測（離線部分）— 顧問引擎 plan v4 §5。

量度（唔需要開遊戲）：
  ① 類別數／配方總數
  ② 固定比例 vs 機率輸出比例 M/N
  ③ NBT 覆蓋率
  ⑤a 掃描時間／峰值記憶體／估算索引大小
  ④ catalyst 覆蓋率 —— **只做 proxy**（唔可以代替 JEI runtime；見 --note）

用法：
  python tools/p0_recipe_metrics.py --out docs/research/artifacts/2026-10-10-p0-recipe-metrics.json
"""
from __future__ import annotations

import argparse
import io
import json
import os
import re
import sys
import time
import zipfile
from collections import Counter, defaultdict

PRISM = os.environ.get("PACKAI_PRISM", r"C:\Users\skps9\Documents\PrismLauncher-Windows-MinGW-w64-Portable-11.1.0")
INSTANCES = {
    "ATM8": "packai_sandbox_atm8",
    "StarTech": "packai_sandbox_startech",
    "NWFC": "AI_test_NFWC_DIM",
    "E9E": "packai_sandbox_e9e",
    "UniversIO(REI-only)": "packai_sandbox_universio",
}

RECIPE_RE = re.compile(r"^(?:.*/)?data/([a-z0-9_]+)/recipes?/(.+)\.json$", re.I)
# 機率關鍵字（跨 mod loader 常見寫法）
CHANCE_KEYS = ("chance", "probability", "chance_percent", "chancePercent")
# 區間／變動數量
RANGE_KEYS = ("min", "max")


def _walk(obj, keys, out):
    """深度搜尋：有冇任何層出現指定 key。"""
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k in keys:
                out.add(k)
            _walk(v, keys, out)
    elif isinstance(obj, list):
        for v in obj:
            _walk(v, keys, out)


def _has_nbt(obj) -> bool:
    """item stack 帶 NBT／components 就算。"""
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k in ("nbt", "NBT", "components", "tag") and isinstance(v, (dict, str)) and v:
                if k == "tag" and isinstance(v, str):
                    continue  # 物品 tag（forge:ingots/iron）唔算 NBT
                return True
            if _has_nbt(v):
                return True
    elif isinstance(obj, list):
        return any(_has_nbt(v) for v in obj)
    return False


def _ingredients(obj) -> list:
    """粗略抽出 ingredient 欄位（key 含 ingredient/input/item/catalys* 等）。"""
    found = []

    def rec(o, key_hint=""):
        if isinstance(o, dict):
            for k, v in o.items():
                kl = k.lower()
                if any(t in kl for t in ("ingredient", "input", "item", "catalyst", "result", "output")):
                    found.append((kl, v))
                rec(v, kl)
        elif isinstance(o, list):
            for v in o:
                rec(v, key_hint)

    rec(obj)
    return found


def scan_instance(inst_dir: str) -> dict:
    mods = os.path.join(inst_dir, "minecraft", "mods")
    kjs = os.path.join(inst_dir, "minecraft", "kubejs")
    types = Counter()
    ns = Counter()
    n_files = 0
    n_parsed = 0
    n_unparsable = 0
    n_prob = 0            # 有機率／區間輸出
    n_fixed = 0
    n_nbt = 0
    n_tag_only = 0        # ingredient 只靠 tag
    prob_types = Counter()
    nbt_types = Counter()
    edge_guess = 0
    t0 = time.perf_counter()
    max_bytes = 0

    def handle(raw: bytes, path: str) -> None:
        nonlocal n_files, n_parsed, n_unparsable, n_prob, n_fixed, n_nbt, n_tag_only, edge_guess, max_bytes
        m = RECIPE_RE.match(path.replace("\\", "/"))
        if not m:
            return
        n_files += 1
        max_bytes += len(raw)
        rtype = ""
        try:
            obj = json.loads(raw.decode("utf-8", "replace"))
        except Exception:
            n_unparsable += 1
            return
        n_parsed += 1
        if isinstance(obj, dict):
            rtype = str(obj.get("type", "?"))
        types[rtype] += 1
        ns[m.group(1)] += 1

        ck: set = set()
        _walk(obj, set(CHANCE_KEYS), ck)
        rk: set = set()
        _walk(obj, set(RANGE_KEYS), rk)
        if ck or rk:
            n_prob += 1
            prob_types[rtype] += 1
        else:
            n_fixed += 1

        if _has_nbt(obj):
            n_nbt += 1
            nbt_types[rtype] += 1

        ings = _ingredients(obj)
        if ings:
            edge_guess += sum(1 for _, v in ings if isinstance(v, (dict, list)))
        # 只靠 tag 的 ingredient
        for k, v in ings:
            if "ingredient" in k and isinstance(v, dict) and "tag" in v:
                n_tag_only += 1
                break

    if os.path.isdir(mods):
        for fn in sorted(os.listdir(mods)):
            if not fn.lower().endswith(".jar"):
                continue
            jp = os.path.join(mods, fn)
            try:
                with zipfile.ZipFile(jp) as zf:          # with = 用完即關（Windows 鎖檔）
                    for zi in zf.infolist():
                        if not zi.filename.endswith(".json"):
                            continue
                        if "/recipes/" not in zi.filename and "/recipe/" not in zi.filename:
                            continue
                        if not RECIPE_RE.match(zi.filename):
                            continue
                        handle(zf.read(zi), zi.filename)
            except Exception:
                continue

    # datapack 目錄（世界／instance 內）
    for root, _dirs, files in os.walk(os.path.join(inst_dir, "minecraft")):
        if "kubejs" in root:
            continue
        for f in files:
            if f.endswith(".json") and ("recipes" in root or "recipe" in root) and "data" in root:
                p = os.path.join(root, f)
                try:
                    with open(p, "rb") as fh:
                        handle(fh.read(), p.replace(os.sep, "/"))
                except Exception:
                    pass

    secs = time.perf_counter() - t0
    kjs_files = kjs_lines = 0
    if os.path.isdir(kjs):
        for root, _d, files in os.walk(kjs):
            for f in files:
                if f.endswith((".js", ".json")):
                    kjs_files += 1
                    try:
                        with open(os.path.join(root, f), encoding="utf-8", errors="replace") as fh:
                            kjs_lines += fh.read().count("\n")
                    except Exception:
                        pass

    total = n_prob + n_fixed
    return {
        "recipe_files": n_files,
        "parsed": n_parsed,
        "unparsable": n_unparsable,
        "distinct_recipe_types": len(types),
        "namespaces": len(ns),
        "M_fixed_ratio": n_fixed,
        "N_probabilistic": n_prob,
        "M_over_N": round(n_fixed / n_prob, 2) if n_prob else None,
        "fixed_pct": round(100.0 * n_fixed / total, 2) if total else None,
        "nbt_recipes": n_nbt,
        "nbt_pct": round(100.0 * n_nbt / total, 2) if total else None,
        "tag_only_recipes": n_tag_only,
        "tag_only_pct": round(100.0 * n_tag_only / total, 2) if total else None,
        "graph_edges_guess": edge_guess,
        "scan_seconds": round(secs, 2),
        "raw_json_mb": round(max_bytes / 1048576.0, 2),
        "est_index_mb": round(max_bytes / 1048576.0 * 0.35, 2),   # 粗略：索引約 raw 35%
        "top_prob_types": prob_types.most_common(8),
        "top_nbt_types": nbt_types.most_common(5),
        "top_types": types.most_common(10),
        "kubejs_files": kjs_files,
        "kubejs_lines": kjs_lines,
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="docs/research/artifacts/2026-10-10-p0-recipe-metrics.json")
    ap.add_argument("--packs", nargs="*", default=list(INSTANCES))
    args = ap.parse_args()

    res = {}
    for name in args.packs:
        inst = INSTANCES.get(name, name)
        d = os.path.join(PRISM, "instances", inst)
        if not os.path.isdir(d):
            print(f"[skip] {name}: {d} 唔存在", file=sys.stderr)
            continue
        r = scan_instance(d)
        r["instance"] = inst
        res[name] = r
        print(f"{name:22s} files={r['recipe_files']:6d} types={r['distinct_recipe_types']:4d} "
              f"fixed={r['M_fixed_ratio']:6d} prob={r['N_probabilistic']:5d} "
              f"fixed%={r['fixed_pct']} nbt%={r['nbt_pct']} {r['scan_seconds']}s")

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(res, fh, ensure_ascii=False, indent=1)
    print("written:", args.out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
