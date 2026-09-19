"""Coverage matrix: categories AVAILABLE in packai's tool/facts data vs categories the ANSWER covers."""
import io, os, json, re

D = os.path.join(os.environ["LOCALAPPDATA"], "Temp", "autotest_results_20260919-231137")
CATS = {
    "recipe":     [r"\[RECIPE_CARDS\]", r"\[card:\d+\]", r"\[JEI\]", r"crafts", r"Stonecutter", r"Crafting Table"],
    "usages":     [r"usage", r"used for", r"used to", r"how to use", r"catalyst", r"\[PURPOSE\]"],
    "tooltip":    [r"tooltip", r"Shift-Right", r"Hold §", r"Can only be placed"],
    "loot":       [r"\bloot\b", r"chest", r"dungeon"],
    "trade":      [r"trade", r"villager"],
    "quest":      [r"quest"],
    "guide":      [r"guide", r"Patchouli", r"worn_notebook"],
    "script":     [r"kubejs", r"script"],
    "tags":       [r"\btags?\b", r"#c:", r"#minecraft:"],
    "mobdrop":    [r"mob drop", r"Mob Drops", r"drops from"],
    "fishing":    [r"fishing"],
    "worldgen":   [r"worldgen", r"world gen", r"generate"],
}
# Categories the ANSWER text cannot be expected to name (mechanism names / negative boilerplate).
# Frozen set: excluded from the headline ratio, reported separately.  (R1 finding)
EXCLUDED = {"script", "tags", "fishing", "trade", "worldgen"}
rows = []
for f in sorted(os.listdir(D)):
    if f == "index.jsonl" or not f.endswith(".jsonl"):
        continue
    recs = [json.loads(l) for l in io.open(os.path.join(D, f), encoding="utf-8", errors="replace") if l.strip()]
    tools = "\n".join(str(r.get("result") or "") for r in recs if r.get("event") == "tool.result")
    facts = "\n".join(str(r.get("content") or "") for r in recs if r.get("event") == "send.facts")
    ans = next((r.get("body") for r in recs if r.get("event") == "display.body.final"), "") or ""
    avail = [c for c, pats in CATS.items() if any(re.search(p, tools + facts, re.I) for p in pats)]
    cov = [c for c in avail if any(re.search(p, ans, re.I) for p in CATS[c])]
    miss = [c for c in avail if c not in cov]
    a2 = [c for c in avail if c not in EXCLUDED]
    c2 = [c for c in a2 if c in cov]
    rows.append((f[17:-6], avail, cov, miss, len(ans), a2, c2))

print("%-40s %5s %5s  missing" % ("case", "avail", "cover"))
tot_a = tot_c = tot_a2 = tot_c2 = 0
cat_miss = {}
for name, a, c, m, alen, a2, c2 in rows:
    tot_a += len(a); tot_c += len(c); tot_a2 += len(a2); tot_c2 += len(c2)
    for x in m:
        cat_miss[x] = cat_miss.get(x, 0) + 1
    print("%-40s %5d %5d  %s" % (name[:40], len(a), len(c), ",".join(m) or "-"))
print()
print("ALL categories      : available=%d covered=%d => %.0f%%" % (tot_a, tot_c, 100.0 * tot_c / max(1, tot_a)))
print("DETECTABLE subset   : available=%d covered=%d => %.0f%%   (excludes %s)" % (
    tot_a2, tot_c2, 100.0 * tot_c2 / max(1, tot_a2), ",".join(sorted(EXCLUDED))))
print("per-category miss counts (available but not mentioned):", sorted(cat_miss.items(), key=lambda x: -x[1]))
print("answer lengths:", [r[4] for r in rows], "min/max:", min(r[4] for r in rows), max(r[4] for r in rows))
