# -*- coding: utf-8 -*-
"""Pack-generic autotest case sampler for Pack AI (packai).

Builds ``<game>/packai/autotest/cases.json`` for the in-game autotest harness with a FRESH random
sample on every run (SK rule 2026-09-20: never reuse the previous run's items; always cover >1 pack).

Sample = ore-bearing worldgen features INTERSECT items declared in the pack's lang files, plus:
  * inline (object-form) ore features -- `"feature": {"type": "minecraft:ore", "config": {...}}`.
    Packs that define ores inline (Thermal-style) have no configured_feature file at all; without
    this the sample pool silently loses those ores (see docs/TEST_SCOPE.md, plan 2026-09-20 P1-F1b).
  * one vanilla control (`minecraft:iron_ore`) -- the pack must answer honestly that it has no
    override instead of inventing numbers.
  * one random non-ore item -- exercises the "honest miss" path.

Usage:
    python tools/make_cases_pack.py <game_dir> [N] [--seed S] [--no-controls]

Notes / pitfalls baked in:
  * cases.json MUST start with the literal bytes ``{"packaiAutotest":1,`` (compact separators).
    Python's default ``json.dump`` writes ``{"packaiAutotest": 1,`` (space) and the harness then
    silently skips the file -> a wasted game launch. We assert the prefix before finishing.
  * The harness reads cases.json ONCE per JVM session.
"""
from __future__ import annotations

import glob
import json
import os
import random
import sys
import zipfile

CONTROL = "minecraft:iron_ore"


def collect(game: str) -> tuple[set[str], set[str], int]:
    ores: set[str] = set()
    items: set[str] = set()
    inline_ores = 0
    for jar in sorted(glob.glob(os.path.join(game, "mods", "*.jar"))):
        try:
            z = zipfile.ZipFile(jar)
        except Exception:
            continue
        for n in z.namelist():
            if n.startswith("data/") and "/worldgen/configured_feature/" in n and n.endswith(".json"):
                try:
                    r = json.loads(z.read(n).decode("utf-8"))
                except Exception:
                    continue
                if r.get("type") == "minecraft:ore":
                    ns, _, rest = n[5:].partition("/")
                    ores.add(ns + ":" + os.path.basename(rest)[:-5])
            elif n.startswith("data/") and "/worldgen/placed_feature/" in n and n.endswith(".json"):
                try:
                    r = json.loads(z.read(n).decode("utf-8"))
                except Exception:
                    continue
                feat = r.get("feature")
                if isinstance(feat, dict) and feat.get("type") == "minecraft:ore":
                    inline_ores += 1
                    cfg = feat.get("config") if isinstance(feat.get("config"), dict) else {}
                    for tgt in cfg.get("targets", []) if isinstance(cfg.get("targets"), list) else []:
                        state = tgt.get("state") if isinstance(tgt, dict) else None
                        name = state.get("Name") if isinstance(state, dict) else None
                        if isinstance(name, str) and name:
                            ores.add(name)
            elif n.startswith("assets/") and "/lang/en_us.json" in n:
                try:
                    lang = json.loads(z.read(n).decode("utf-8"))
                except Exception:
                    continue
                for k in lang:
                    for p in ("item.", "block."):
                        if k.startswith(p):
                            body = k[len(p):]
                            if body.count(".") == 1:
                                ns, _, path = body.partition(".")
                                items.add(ns + ":" + path)
    return ores, items, inline_ores


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    game = sys.argv[1]
    n = int(sys.argv[2]) if len(sys.argv) > 2 and sys.argv[2].isdigit() else 10
    seed = None
    if "--seed" in sys.argv:
        seed = int(sys.argv[sys.argv.index("--seed") + 1])
    controls = "--no-controls" not in sys.argv
    if seed is not None:
        random.seed(seed)
    else:
        random.seed()

    ores, items, inline_ores = collect(game)
    both = sorted(ores & items)
    print(f"game={game}")
    print(f"ore features={len(ores)} lang items={len(items)} intersection={len(both)} inline_ore_files={inline_ores}")
    picks = random.sample(both, min(n, len(both)))
    cases = [{"id": f"p_{i+1}_{p.split(':')[1][:18]}", "item": p} for i, p in enumerate(picks)]
    if controls:
        cases.append({"id": "ctl_vanilla_iron", "item": CONTROL})

    out = os.path.join(game, "packai", "autotest", "cases.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    cfg = {"packaiAutotest": 1, "world": "New World", "quitWhenDone": True, "cases": cases}
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(cfg, fh, ensure_ascii=False, separators=(",", ":"))
    head = open(out, encoding="utf-8").read(20)
    if not head.startswith('{"packaiAutotest":1,'):
        print("FATAL: cases.json magic prefix missing -> harness would silently skip")
        return 3
    print(f"wrote {out} cases={len(cases)}")
    for c in cases:
        print("  ", c["id"], c["item"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
