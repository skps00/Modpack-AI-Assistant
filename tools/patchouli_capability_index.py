#!/usr/bin/env python3
"""「能力反查」可行性 spike：可以由包內文件（Patchouli 說明書）答「有咩可以做到 X」嗎？

做法：
  1. 掃所有 mod jar 嘅 `patchouli_books/**/*.json`，抽出文字欄位（text/pages/title）
  2. 抽「能力句」＝含能力標記（can/capable/allows/lets you/use it to/支援/可以/能夠）嘅句子
  3. 用**真實玩家問題**做測試查詢（來自主題研究：儲 unstackable／清大範圍／無線傳電／自動合成／近乎無限儲存）
     → 逐條睇索引內有冇命中（＝可唔可以靠包內文件答）
  4. 出覆蓋率報告（誠實：Patchouli 係散文，預計命中率低）

用法：
  python tools/patchouli_capability_index.py --out docs/research/artifacts/2026-10-10-patchouli-capability.json
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import zipfile
from collections import defaultdict

PRISM = os.environ.get("PRISM_OVERRIDE", r"C:\Users\skps9\Documents\PrismLauncher-Windows-MinGW-w64-Portable-11.1.0")
DEFAULT_PACK = "packai_sandbox_atm8"

CAP_MARKERS = re.compile(
    r"\b(can|can't|cannot|capable|allows?|lets you|let you|used to|use it to|enables?|provides?|supports?)\b"
    r"|可以|能夠|能够|支援|支持|用來|用于|能夠將", re.I)

# 真實玩家問題（來自主題研究）→ 每條一組關鍵詞（同義詞）
TEST_QUERIES = {
    "儲 unstackable / 特殊物品": ["unstackable", "unstack", "non-stackable", "cannot be stacked", "single item", "storage for items that"],
    "清大範圍（100x90）": ["clear a large area", "clear area", "large area", "dig area", "excavat", "quarry", "fill area", "bulldoz"],
    "無線傳電／能量": ["wireless", "wirelessly", "no cables", "wireless energy", "wireless power"],
    "自動合成": ["auto-craft", "autocraft", "automatic crafting", "crafting on demand", "auto craft"],
    "近乎無限儲存": ["virtually unlimited", "infinite storage", "unlimited storage", "mass storage", "near-infinite"],
    "機器維持運作（燃料／冷卻）": ["fuel", "coolant", "cooling", "needs to be cooled", "keep running", "reactor"],
    "範圍挖掘／破壞": ["area mining", "destroy blocks in", "break blocks in", "vein", "3x3", "radius"],
}
CLEAN = [(re.compile(r"\$\([^)]*\)"), " "), (re.compile(r"<[^>]+>"), " "), (re.compile(r"\\u[0-9a-fA-F]{4}"), " ")]


def clean(s: str) -> str:
    for rx, rep in CLEAN:
        s = rx.sub(rep, s)
    return re.sub(r"\s+", " ", s).strip()


def sentences(text: str) -> list[str]:
    return [clean(x) for x in re.split(r"(?<=[.!?。！？])\s+|\n", text) if len(x.strip()) > 25]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pack", default=DEFAULT_PACK)
    ap.add_argument("--out", default="docs/research/artifacts/2026-10-10-patchouli-capability.json")
    args = ap.parse_args()

    inst = os.path.join(PRISM, "instances", args.pack, "minecraft")
    mods = os.path.join(inst, "mods")
    files = 0
    cap_by_mod: dict[str, list[str]] = defaultdict(list)
    for fn in sorted(os.listdir(mods)):
        if not fn.lower().endswith(".jar"):
            continue
        try:
            with zipfile.ZipFile(os.path.join(mods, fn)) as zf:
                for zi in zf.infolist():
                    if "patchouli_books/" not in zi.filename or not zi.filename.endswith(".json"):
                        continue
                    files += 1
                    try:
                        obj = json.loads(zf.read(zi).decode("utf-8", "replace"))
                    except Exception:
                        continue
                    blob = json.dumps(obj, ensure_ascii=False)
                    for s in sentences(blob.replace("\\n", "\n")):
                        if CAP_MARKERS.search(s) and len(cap_by_mod[fn]) < 60:
                            cap_by_mod[fn].append(s)
        except Exception:
            continue

    total_sents = sum(len(v) for v in cap_by_mod.values())
    print(f"包：{args.pack}")
    print(f"Patchouli JSON 檔：{files}｜有 mod 名：{len(cap_by_mod)}｜抽到能力句：{total_sents}")
    print(f"（平均每 mod {total_sents/max(len(cap_by_mod),1):.0f} 句）\n")

    report = {}
    hits = 0
    for q, terms in TEST_QUERIES.items():
        found = []
        for mod, sents in cap_by_mod.items():
            for s in sents:
                if any(t.lower() in s.lower() for t in terms):
                    found.append({"mod": mod[:46], "sentence": s[:220]})
                    break
        report[q] = {"hit_mods": len(found), "examples": found[:3]}
        ok = "✅ 有命中" if found else "❌ 冇命中"
        print(f"{ok}  {q}（命中 {len(found)} 個 mod）")
        for e in found[:2]:
            print(f"      - {e['mod']}: {e['sentence'][:120]}")
        hits += 1 if found else 0
    print(f"\n測試查詢命中率：{hits}/{len(TEST_QUERIES)} = {100*hits/len(TEST_QUERIES):.0f}%")

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump({"pack": args.pack, "patchouli_files": files,
                   "mods_with_capabilities": len(cap_by_mod), "capability_sentences": total_sents,
                   "query_results": report,
                   "sample_capabilities": {k: v[:6] for k, v in list(cap_by_mod.items())[:5]}},
                  fh, ensure_ascii=False, indent=1)
    print("written:", args.out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
