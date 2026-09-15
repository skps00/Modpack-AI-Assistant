import os, re, collections, json, sys

ROOT = r"C:\Users\skps9\Documents\PrismLauncher-Windows-MSVC-Portable-8.0\PrismLauncher-Windows-MSVC-Portable-8.0\instances\AI_test_NFWC_DIM\minecraft\kubejs"
ITEM = re.compile(r"Item\.of\(\s*['\"]([a-z0-9_]+:[a-z0-9_./\-]+)['\"]", re.I)
CALL = re.compile(r"([A-Za-z_][\w\.]*)\s*\(")
CUSTOM_RECIPE = re.compile(r"new\s+(\w*Recipe)\s*\(")
REG = re.compile(r"(register\w*Recipe)\s*\(")
EVENT = re.compile(r"(\w+Events)\.(\w+)\s*\(")
BADGE = re.compile(r"(\w*[Rr]itual\w*)\.(\w+)\s*\(")

files = []
for dirpath, dirnames, filenames in os.walk(ROOT):
    for fn in filenames:
        if fn.lower().endswith((".js", ".json")):
            files.append(os.path.join(dirpath, fn))

js_files = [f for f in files if f.lower().endswith(".js")]
json_files = [f for f in files if f.lower().endswith(".json")]

by_dir = collections.Counter()
for f in js_files:
    rel = os.path.relpath(f, ROOT).replace("\\", "/")
    by_dir[rel.split("/")[0]] += 1

# call names on lines that carry an Item.of( literal -> these are the "producing" statements
prod_calls = collections.Counter()
consum_calls = collections.Counter()
custom_classes = collections.Counter()
regs = collections.Counter()
events = collections.Counter()
ritual_calls = collections.Counter()
transforms = collections.Counter()
all_calls = collections.Counter()
items_out = collections.Counter()
TRANSFORM_KEYS = ["setStackInSlot", "setItemInHand", "give(", "giveItem", "popItem", "spawnItem", "addItem", "addDrop", "drops.add", "setItem", "insertItem", "addToInventory", "add("]
LOOTJS = 0
lootjs_sites = []

for f in js_files:
    try:
        txt = open(f, encoding="utf-8", errors="ignore").read()
    except Exception:
        continue
    if "LootJS" in txt:
        LOOTJS += 1
        lootjs_sites.append(os.path.relpath(f, ROOT).replace("\\", "/"))
    for m in CUSTOM_RECIPE.finditer(txt):
        custom_classes[m.group(1)] += 1
    for m in REG.finditer(txt):
        regs[m.group(1)] += 1
    for m in EVENT.finditer(txt):
        events[m.group(1) + "." + m.group(2)] += 1
    for m in BADGE.finditer(txt):
        ritual_calls[m.group(1) + "." + m.group(2)] += 1
    for line in txt.splitlines():
        for m in CALL.finditer(line):
            all_calls[m.group(1).split(".")[-1]] += 1
        hits = list(ITEM.finditer(line))
        if hits:
            for h in hits:
                items_out[h.group(1)] += 1
            names = [m.group(1) for m in CALL.finditer(line)]
            if names:
                # first + last call name on the statement (producer APIs usually wrap Item.of)
                prod_calls[names[0]] += 1
                if len(names) > 1:
                    prod_calls["<last>" + names[-1]] += 1
        for k in TRANSFORM_KEYS:
            if k in line:
                transforms[k] += 1
                if hits:
                    transforms[k + " +Item.of"] += 1

# data-driven recipe / loot files
data_files = collections.Counter()
for f in json_files:
    rel = os.path.relpath(f, ROOT).replace("\\", "/")
    parts = rel.split("/")
    if "recipes" in parts:
        idx = parts.index("recipes")
        data_files["data/**/recipes/**"] += 1
    if "loot_tables" in parts or "loot_table" in rel:
        data_files["data/**/loot_tables/**"] += 1
    if "quests" in parts:
        data_files["data/**/quests/**"] += 1

out = {
    "kubejs_root": ROOT,
    "js_files": len(js_files),
    "json_files": len(json_files),
    "js_by_top_dir": by_dir.most_common(),
    "distinct_item_of_ids": len(items_out),
    "top_item_of_ids": items_out.most_common(12),
    "custom_recipe_classes": custom_classes.most_common(20),
    "register_fns": regs.most_common(10),
    "event_families": events.most_common(25),
    "ritual_like_calls": ritual_calls.most_common(30),
    "transform_api_hits": transforms.most_common(20),
    "lootjs_files": LOOTJS,
    "lootjs_examples": lootjs_sites[:6],
    "data_json_groups": data_files.most_common(),
    "top_calls_on_producing_lines": prod_calls.most_common(40),
}
print(json.dumps(out, ensure_ascii=False, indent=1))
