#!/usr/bin/env python3
"""Probe: 由「需求」自由文字，睇 runtime 文字來源（物品顯示名 ＋ mod 描述）實際撈得到幾多。

背景：落點 B（能力反查）四輪 plan review 都撞同一族「機制可達性」漏洞。落手改碼前，
先用**離線**方式量一次真值：一個 1.19.2 Forge 整合包（NFWC / packai_sandbox）嘅
item/block 顯示名（zh_cn + en_us，即 runtime I18n 會載入嘅同一批）＋ mods.toml 描述，
對 N 條「能力需求」問題實際命中幾多。

唔改任何 product code、唔開遊戲、唔 javap。只讀 jar 內 lang / mods.toml。

用法:
  python tools/probe_capability_text_hits.py [--mods DIR] [--lang zh_cn] [--top 8]
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import zipfile
from collections import defaultdict

DEFAULT_MODS = (
    r"C:\Users\skps9\Documents\PrismLauncher-Windows-MinGW-w64-Portable-11.1.0"
    r"\instances\packai_sandbox\minecraft\mods"
)

# 能力需求問題（第一條係研究已證「只有 tooltip 有」嘅難題；其餘係 realistic 需求）
QUESTIONS = [
    ("q1", "有冇嘢可以儲存唔可以堆疊嘅物品", ["儲存", "堆疊", "物品"]),
    ("q2", "點樣自動合成物品", ["自動", "合成"]),
    ("q3", "有冇無線傳電", ["無線", "傳電", "能量"]),
    ("q4", "點樣清走一個 100x90 範圍", ["清", "範圍", "範圍內"]),
    ("q5", "白色混凝土點自動生產", ["白色", "混凝土"]),
    ("q6", "有冇自動農場", ["自動", "農場", "種植"]),
    ("q7", "有冇自動挖礦機", ["自動", "挖礦", "採礦"]),
    ("q8", "點樣抽水／抽液體", ["抽水", "液體", "流體"]),
    ("q9", "有冇物品分類系統", ["分類", "儲存"]),
    ("q10", "點樣遠距傳送物品", ["傳送", "遠距", "傳輸"]),
    ("q11", "有冇自動釣魚", ["釣魚"]),
    ("q12", "有冇怪物農場／刷怪塔", ["刷怪", "怪物"]),
    ("q13", "點樣儲存大量物品", ["儲存", "大量"]),
    ("q14", "有冇飛行裝備", ["飛行", "飛"]),
]

LANG_KEY_RE = re.compile(r"^(item|block|tile|entity)\.")


def read_lang(zf: zipfile.ZipFile, lang_name: str) -> dict[str, str]:
    out: dict[str, str] = {}
    for name in zf.namelist():
        if not name.endswith(".json") or "/lang/" not in name:
            continue
        base = name.rsplit("/", 1)[-1]
        if base not in (f"{lang_name}.json",):
            continue
        try:
            with zf.open(name) as fh:
                data = json.loads(fh.read().decode("utf-8", "replace"))
        except Exception:
            continue
        if isinstance(data, dict):
            for k, v in data.items():
                if isinstance(v, str) and v.strip():
                    out.setdefault(k, v)
    return out


def read_desc(zf: zipfile.ZipFile) -> str:
    for name in ("META-INF/mods.toml", "META-INF/neoforge.mods.toml", "mcmod.info", "fabric.mod.json"):
        try:
            with zf.open(name) as fh:
                raw = fh.read().decode("utf-8", "replace")
        except Exception:
            continue
        m = re.search(r'description\s*=\s*"""(.*?)"""', raw, re.S)
        if m:
            return " ".join(m.group(1).split())[:400]
        m = re.search(r'description\s*=\s*"([^"]*)"', raw, re.S)
        if m:
            return " ".join(m.group(1).split())[:400]
        m = re.search(r'"description"\s*:\s*"([^"]*)"', raw, re.S)
        if m:
            return " ".join(m.group(1).split())[:400]
    return ""


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--mods", default=DEFAULT_MODS)
    ap.add_argument("--lang", default="zh_cn")
    ap.add_argument("--lang2", default="en_us")
    ap.add_argument("--top", type=int, default=8)
    args = ap.parse_args()

    if not os.path.isdir(args.mods):
        print("mods dir not found:", args.mods)
        return 2

    labels: list[tuple[str, str, str]] = []   # (label, namespace, key)
    descs: list[tuple[str, str, str]] = []    # (desc, modfile, modid_guess)
    jars = sorted(f for f in os.listdir(args.mods) if f.endswith(".jar"))
    for jar in jars:
        # namespace from lang path
        try:
            with zipfile.ZipFile(os.path.join(args.mods, jar)) as zf:
                for name in zf.namelist():
                    if "/lang/" in name and name.endswith(f"/{args.lang}.json"):
                        ns = name.split("/")[1] if name.count("/") >= 3 else "?"
                        try:
                            with zf.open(name) as fh:
                                data = json.loads(fh.read().decode("utf-8", "replace"))
                        except Exception:
                            continue
                        if isinstance(data, dict):
                            for k, v in data.items():
                                if isinstance(v, str) and v.strip() and LANG_KEY_RE.match(k):
                                    labels.append((v.strip(), ns, k))
                d = read_desc(zf)
                if d:
                    descs.append((d, jar, ""))
        except Exception as e:
            print("skip", jar, e, file=sys.stderr)

    print(f"jars={len(jars)}  labels({args.lang})={len(labels)}  mods with desc={len(descs)}")
    print("=" * 100)

    label_index: dict[str, list[tuple[str, str, str]]] = defaultdict(list)
    for lab, ns, key in labels:
        label_index[lab.lower()].append((lab, ns, key))

    for qid, question, tokens in QUESTIONS:
        hits: dict[str, int] = {}
        where: dict[str, str] = {}
        for tok in tokens:
            t = tok.lower()
            for lab, ns, key in labels:
                low = lab.lower()
                if t in low:
                    hits[ns] = hits.get(ns, 0) + (3 if low == t else 2 if low.startswith(t) else 1)
                    where.setdefault(ns, f'{lab}')
            for desc, jar, _ in descs:
                if t in desc.lower():
                    mod = jar.rsplit("-", 1)[0]
                    hits[mod] = hits.get(mod, 0) + 2
                    where.setdefault(mod, "desc")
        ranked = sorted(hits.items(), key=lambda kv: (-kv[1], kv[0]))[: args.top]
        print(f"\n[{qid}] {question}   tokens={tokens}")
        if not ranked:
            print("    -> 零命中")
        for ns, sc in ranked:
            print(f"    {sc:3d}  {ns:<32} {where.get(ns, '')[:60]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
