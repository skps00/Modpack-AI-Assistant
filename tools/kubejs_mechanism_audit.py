#!/usr/bin/env python3
"""KubeJS mechanism coverage audit (read-only).

Scans every Prism instance that has a `minecraft/kubejs` folder and reports every
mechanism (API call name, custom recipe class, event family) that appears next to
an item-id literal — i.e. every way a pack can produce / transform / consume an item.

Outputs:
  docs/kubejs-mechanism-coverage.md   human-readable coverage table (seed for the role table)
  docs/kubejs-mechanism-coverage.json machine-readable detail

This is the S3 audit defined in docs/plans/2026-09-15_kubejs-acquisition-path-facts-v2.md.
Never writes anywhere else; safe to run any time (a few seconds per pack).
"""
from __future__ import annotations

import argparse
import collections
import json
import os
import re
import sys
import time

DEFAULT_INSTANCES = (
    r"C:\Users\skps9\Documents\PrismLauncher-Windows-MSVC-Portable-8.0"
    r"\PrismLauncher-Windows-MSVC-Portable-8.0\instances"
)

ITEM_LITERAL = re.compile(r"['\"](#?[a-z0-9_]+:[a-z0-9_./\-]+)['\"]", re.I)
NEW_RECIPE = re.compile(r"new\s+(\w*Recipe)\s*\(")
REGISTER_FN = re.compile(r"(\w*register\w*Recipe)\s*\(", re.I)
EVENT_FAMILY = re.compile(r"(\w+Events)\.(\w+)\s*\(")
CALL = re.compile(r"([A-Za-z_$][\w\.$]*)\s*\(")
LOOTJS = re.compile(r"LootJS")
REMOVE_CALL = re.compile(r"\b(?:event\.)?remove\s*\(")
# APIs that move an item into an inventory / the world (transform-ish)
MOVE_APIS = (
    "setStackInSlot", "setItemInHand", "setMainHandItem", "setItemSlot", "give", "giveItem",
    "popItem", "spawnItem", "addItem", "addDrop", "drops.add", "setItem", "insertItem",
    "addToInventory", "addLoot", "TradeItem.of", "RECIPES.add", "add(",
)
FAMILY_OF_DIR = {
    "server_scripts": "server",
    "startup_scripts": "startup",
    "client_scripts": "client",
}


def pack_instances(root: str):
    if not os.path.isdir(root):
        return []
    out = []
    for name in sorted(os.listdir(root)):
        kube = os.path.join(root, name, "minecraft", "kubejs")
        if os.path.isdir(kube):
            out.append((name, kube))
    return out


def scan_pack(pack: str, kube: str):
    js_files = []
    json_count = 0
    for dirpath, _dirnames, filenames in os.walk(kube):
        for fn in filenames:
            low = fn.lower()
            if low.endswith(".js"):
                js_files.append(os.path.join(dirpath, fn))
            elif low.endswith(".json"):
                json_count += 1

    mech = collections.Counter()             # mechanism name -> sites
    mech_meta = {}                           # mechanism name -> example
    new_recipes = collections.Counter()
    register_fns = collections.Counter()
    events = collections.Counter()
    move_apis = collections.Counter()
    lootjs_files = 0
    item_ids = set()
    remove_sites = 0

    for path in js_files:
        try:
            with open(path, encoding="utf-8", errors="ignore") as fh:
                txt = fh.read()
        except OSError:
            continue
        rel = os.path.relpath(path, kube).replace("\\", "/")
        if LOOTJS.search(txt):
            lootjs_files += 1
        for m in NEW_RECIPE.finditer(txt):
            new_recipes[m.group(1)] += 1
        for m in REGISTER_FN.finditer(txt):
            register_fns[m.group(1)] += 1
        for m in EVENT_FAMILY.finditer(txt):
            events[f"{m.group(1)}.{m.group(2)}"] += 1
        if REMOVE_CALL.search(txt):
            remove_sites += REMOVE_CALL.findall(txt).__len__()
        for lineno, line in enumerate(txt.splitlines(), 1):
            literals = list(ITEM_LITERAL.finditer(line))
            if not literals:
                continue
            for lit in literals:
                item_ids.add(lit.group(1).lower())
            calls = [m.group(1) for m in CALL.finditer(line)]
            if not calls:
                continue
            # owning call = innermost call whose '(' starts before the first Item.of/quote
            head = line[: literals[0].start()]
            owner = None
            for m in CALL.finditer(head):
                owner = m.group(1)
            if owner is None:
                continue
            owner = owner.split("(")[0]
            mech[owner] += 1
            if owner not in mech_meta:
                mech_meta[owner] = {
                    "pack": pack,
                    "file": rel,
                    "line": lineno,
                    "snippet": line.strip()[:200],
                }
            for api in MOVE_APIS:
                if api in line:
                    move_apis[api] += 1
                    break

    return {
        "pack": pack,
        "js_files": len(js_files),
        "json_files": json_count,
        "item_ids": len(item_ids),
        "lootjs_files": lootjs_files,
        "remove_sites": remove_sites,
        "mechanisms": mech,
        "mechanism_count": len(mech),
        "mechanism_meta": mech_meta,
        "new_recipes": new_recipes,
        "register_fns": register_fns,
        "events": events,
        "move_apis": move_apis,
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--instances", default=DEFAULT_INSTANCES)
    ap.add_argument("--out-md", default="docs/kubejs-mechanism-coverage.md")
    ap.add_argument("--out-json", default="docs/kubejs-mechanism-coverage.json")
    ap.add_argument("--limit-packs", type=int, default=0)
    args = ap.parse_args()

    packs = pack_instances(args.instances)
    if args.limit_packs:
        packs = packs[: args.limit_packs]
    if not packs:
        print("no packs found under", args.instances)
        return 2

    totals = {
        "mechanisms": collections.Counter(),
        "meta": {},
        "new_recipes": collections.Counter(),
        "register_fns": collections.Counter(),
        "events": collections.Counter(),
        "move_apis": collections.Counter(),
        "packs": [],
        "packs_with_mech": collections.Counter(),
    }

    for pack, kube in packs:
        t0 = time.time()
        res = scan_pack(pack, kube)
        dt = time.time() - t0
        print(f"[{pack}] js={res['js_files']} json={res['json_files']} "
              f"mech={len(res['mechanisms'])} ids={res['item_ids']} {dt:.1f}s", flush=True)
        totals["packs"].append({k: v for k, v in res.items()
                                if k not in ("mechanisms", "mechanism_meta",
                                             "new_recipes", "register_fns", "events",
                                             "move_apis")})
        for name, cnt in res["mechanisms"].items():
            totals["mechanisms"][name] += cnt
            totals["packs_with_mech"][name] += 1
            totals["meta"].setdefault(name, res["mechanism_meta"][name])
        totals["new_recipes"] += res["new_recipes"]
        totals["register_fns"] += res["register_fns"]
        totals["events"] += res["events"]
        totals["move_apis"] += res["move_apis"]

    md = []
    md.append("# KubeJS mechanism coverage (auto-generated)\n")
    md.append("Source: `tools/kubejs_mechanism_audit.py` — scans every Prism instance with a "
              "`minecraft/kubejs` folder. Regenerate after a pack update.\n")
    md.append(f"- Packs scanned: **{len(totals['packs'])}**")
    md.append(f"- Distinct mechanisms (call names seen on item-id lines): "
              f"**{len(totals['mechanisms'])}**")
    md.append(f"- Distinct custom recipe classes: **{len(totals['new_recipes'])}**")
    md.append(f"- Distinct event families: **{len(totals['events'])}**\n")

    def table(title, counter, meta=None, top=80):
        md.append(f"## {title}\n")
        md.append("| name | sites | packs | example |")
        md.append("|---|---|---|---|")
        for name, cnt in counter.most_common(top):
            ex = ""
            if meta and name in meta:
                m = meta[name]
                ex = f"`{m['pack']}/{m['file']}:{m['line']}`"
            md.append(f"| `{name}` | {cnt} | {totals['packs_with_mech'].get(name, '')} | {ex} |")
        md.append("")

    table("Mechanisms on item-id lines (role table seed)", totals["mechanisms"],
          totals["meta"])
    table("Custom recipe classes (`new *Recipe(...)`) — invisible to JEI unless the mod ships a plugin",
          totals["new_recipes"])
    table("register*Recipe functions", totals["register_fns"])
    table("Event families", totals["events"])
    table("Item-moving APIs", totals["move_apis"])

    md.append("## Packs scanned\n")
    md.append("| pack | js | json | item ids | mechanisms | LootJS files |")
    md.append("|---|---|---|---|---|---|")
    for p in sorted(totals["packs"], key=lambda r: -r["js_files"]):
        md.append(f"| {p['pack']} | {p['js_files']} | {p['json_files']} | {p['item_ids']} | "
                  f"{p.get('mechanism_count', '')} | {p['lootjs_files']} |")
    md.append("")

    with open(args.out_md, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(md))
    with open(args.out_json, "w", encoding="utf-8", newline="\n") as fh:
        json.dump({
            "packs": totals["packs"],
            "mechanisms": dict(totals["mechanisms"].most_common()),
            "mechanism_meta": totals["meta"],
            "new_recipes": dict(totals["new_recipes"].most_common()),
            "register_fns": dict(totals["register_fns"].most_common()),
            "events": dict(totals["events"].most_common()),
            "move_apis": dict(totals["move_apis"].most_common()),
        }, fh, ensure_ascii=False, indent=1)

    print(f"\nwrote {args.out_md} and {args.out_json}")
    print("distinct mechanisms:", len(totals["mechanisms"]),
          "| recipe classes:", len(totals["new_recipes"]),
          "| event families:", len(totals["events"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
