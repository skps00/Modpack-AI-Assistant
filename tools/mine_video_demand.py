#!/usr/bin/env python3
"""第二輪研究用：由高位影片嘅**內容**（唔止標題）挖玩家需求。

做法：用 yt-dlp 拎 top farm/automation 影片嘅 metadata（description／chapters／duration／views），
再由文字抽「提到嘅機器／材料／步驟詞」，對照 §9 嘅題型。

用法（背景跑，network）：
  python tools/mine_video_demand.py --out docs/research/artifacts/2026-10-10-video-demand.json
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys

YTDLP = os.environ.get("YTDLP", r"C:/Users/skps9/AppData/Local/hermes/hermes-agent/venv/Scripts/yt-dlp.exe")

QUERIES = [
    "minecraft automatic farm tutorial",
    "minecraft iron farm tutorial",
    "minecraft mob farm tutorial",
    "create mod farm tutorial",
    "all the mods 8 farm",
    "modded minecraft automation farm",
]
TOP_N = 5   # 每個 query 取 view 最高嘅幾條，攞埋 description

# 抽「機器／材料／步驟」關鍵詞
KINDS = {
    "作物/食物": r"wheat|carrot|potato|beetroot|sugar ?cane|bamboo|kelp|melon|pumpkin|cocoa|crop",
    "怪物/掉落": r"mob |creeper|zombie|skeleton|spider|blaze|witch|guardian|slime|ender|iron golem|drowned",
    "資源/礦": r"iron|cobble|stone|gravel|sand|cobble|obsidian|gold|copper|ore",
    "樹木": r"tree|wood|log|sapling",
    "流體": r"water|lava|fluid|honey|milk",
    "物流/儲存": r"hopper|conveyor|belt|pipe|duct|tunnel|chute|funnel|chest|barrel|storage",
    "紅石/機械": r"redstone|piston|observer|hopper clock|dispenser|dropper|comparator|repeater",
    "村民": r"villager|trading|trade|farmer",
    "機器 mod": r"mekanism|thermal|industrial foregoing|create|botania|ae2|applied energistics|ender io|rftools|immersive",
    "數值/產量": r"per hour|/h\b|\d+\s*k\b|efficien|rate|throughput",
}
STEP_WORDS = r"step ?\d|first,|then,|next,|finally,|you('| )?ll need|材料|build"


def run(args: list[str], timeout: int = 240) -> str:
    p = subprocess.run(args, capture_output=True, timeout=timeout)
    return p.stdout.decode("utf-8", "replace")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="docs/research/artifacts/2026-10-10-video-demand.json")
    args = ap.parse_args()

    rows = []
    for q in QUERIES:
        try:
            raw = run([YTDLP, "--skip-download", "--no-warnings", "-J", f"ytsearch12:{q}"])
            d = json.loads(raw or "{}")
        except Exception as e:
            print("ERR", q, repr(e)[:120], file=sys.stderr, flush=True)
            continue
        entries = [e for e in (d.get("entries") or []) if e]
        entries.sort(key=lambda e: -(e.get("view_count") or 0))
        picked = entries[:TOP_N]
        print(f"{q:34s} -> {len(entries)} 條，取 top {len(picked)}", flush=True)
        for e in picked:
            vid = e.get("id")
            desc = ""
            try:
                raw2 = run([YTDLP, "--skip-download", "--no-warnings", "--print", "%(description)s", vid], 180)
                desc = raw2.strip()
            except Exception:
                pass
            text = f"{e.get('title','')}\n{desc}"
            kinds = {k: len(re.findall(rx, text, re.I)) for k, rx in KINDS.items()}
            rows.append({
                "query": q, "id": vid, "title": e.get("title"),
                "views": e.get("view_count"), "duration": e.get("duration"),
                "desc_chars": len(desc),
                "kinds": {k: v for k, v in kinds.items() if v},
                "has_steps": bool(re.search(STEP_WORDS, text, re.I)),
                "desc_head": desc[:400],
            })

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(rows, fh, ensure_ascii=False, indent=1)
    print("written:", args.out, "rows=", len(rows), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
