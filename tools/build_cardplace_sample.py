import io, os, re, json, random, zipfile

GAME = r"C:/Users/skps9/Documents/packai_dev_game"
OUT = r"C:/Users/skps9/Documents/Code_Project/super_minecraft_AI_player/docs/research/artifacts/2026-09-19-cardplace-sample"
SEED = 20260919
PER_CAT = 20
RANDOM_N = 12
VARIANT = re.compile(r"(_pulling_\d|_predicate|_inventory|_gui|_display|_template|_base)$")
# creative / technical / non-survival heuristics
NON_SURVIVAL = re.compile(
    r"(creative|spawn_egg|debug|command_block|structure_block|barrier|jigsaw|light_block|"
    r"test_|unused|_icon|_display|_gui|_template|egg$|_spawner$|dummy|dev_)", re.I)


def rd(p):
    return io.open(p, encoding="utf-8", errors="replace").read()


def jar_items():
    out = {}
    for f in sorted(os.listdir(os.path.join(GAME, "mods"))):
        if not f.endswith(".jar"):
            continue
        try:
            with zipfile.ZipFile(os.path.join(GAME, "mods", f)) as z:
                for n in z.namelist():
                    if n.startswith("assets/") and "/models/item/" in n and n.endswith(".json"):
                        p = n.split("/")
                        try:
                            k = p.index("item")
                        except ValueError:
                            continue
                        it = "/".join(p[k + 1:])[:-5]
                        if it and not VARIANT.search(it):
                            out.setdefault(p[1], set()).add(it)
        except Exception:
            continue
    return out


def recipe_results():
    """item ids that appear as a recipe result (jar data + kubejs data) = survival obtainable signal"""
    res = set()
    pat = re.compile(r'"(?:item|id)"\s*:\s*"([a-z0-9_\-:./]+)"')
    for f in sorted(os.listdir(os.path.join(GAME, "mods"))):
        if not f.endswith(".jar"):
            continue
        try:
            with zipfile.ZipFile(os.path.join(GAME, "mods", f)) as z:
                for n in z.namelist():
                    if "/recipes/" in n and n.endswith(".json") and n.startswith("data/"):
                        try:
                            d = json.loads(z.read(n).decode("utf-8", "replace"))
                        except Exception:
                            continue
                        r = d.get("result") or d.get("output")
                        if isinstance(r, str):
                            res.add(r)
                        elif isinstance(r, dict):
                            if isinstance(r.get("item"), str):
                                res.add(r["item"])
                            elif isinstance(r.get("id"), str):
                                res.add(r["id"])
                        elif isinstance(r, list) and r and isinstance(r[0], dict):
                            for it in r:
                                if isinstance(it, dict) and isinstance(it.get("item"), str):
                                    res.add(it["item"])
        except Exception:
            continue
    for root, _, fs in os.walk(os.path.join(GAME, "kubejs", "data")):
        if "recipes" not in root.replace("\\", "/"):
            continue
        for f in fs:
            if not f.endswith(".json"):
                continue
            t = rd(os.path.join(root, f))
            try:
                d = json.loads(t)
            except Exception:
                continue
            for key in ("result", "output"):
                r = d.get(key)
                if isinstance(r, str):
                    res.add(r)
                elif isinstance(r, dict):
                    for kk in ("item", "id"):
                        if isinstance(r.get(kk), str):
                            res.add(r[kk])
                elif isinstance(r, list):
                    for it in r:
                        if isinstance(it, dict) and isinstance(it.get("item"), str):
                            res.add(it["item"])
            if not res:
                for m in pat.finditer(t):
                    res.add(m.group(1))
    return res


def kubejs_items():
    cre = re.compile(r"\.create\(\s*['\"]([a-z0-9_\-:./]+)['\"]\s*\)")
    hits = set()
    for r, _, fs in os.walk(os.path.join(GAME, "kubejs", "startup_scripts")):
        for f in fs:
            if f.endswith(".js"):
                t = rd(os.path.join(r, f))
                for m in cre.finditer(t):
                    i = m.group(1)
                    hits.add(i if ":" in i else "kubejs:" + i)
    return hits


def event_items():
    pat = re.compile(r"ItemEvents\.\w+\s*\(\s*['\"]([a-z0-9_\-:./]+)['\"]")
    hits = set()
    for r, _, fs in os.walk(os.path.join(GAME, "kubejs", "server_scripts")):
        for f in fs:
            if f.endswith(".js"):
                t = rd(os.path.join(r, f))
                for m in pat.finditer(t):
                    i = m.group(1)
                    hits.add(i if ":" in i else "kubejs:" + i)
    return hits


def _has(leaf, sufs):
    return any(leaf == s or leaf.endswith("_" + s) for s in sufs)


FUNC_SUF = ("wand", "staff", "remote", "clicker", "orb", "tablet", "controller", "tome", "scroll",
            "charm", "sigil", "totem", "spawner", "gun", "bow", "sword", "pickaxe", "shovel", "hoe",
            "shears", "hammer", "drill", "saw", "lens", "gadget", "axe", "key", "kit", "horn", "whistle")
MAT_SUF = ("ingot", "gem", "dust", "plate", "rod", "nugget", "scrap", "fragment", "crystal", "billet",
           "casing", "sheet", "wire", "alloy", "chunk", "shard", "pellet", "powder", "coil", "foil",
           "blend", "clump", "slurry", "gear", "spring", "mesh")


def main():
    jars = jar_items()
    kj = kubejs_items()
    ev = event_items()
    res = recipe_results()
    pool = {"mod_normal": set(), "mod_function": set(), "mod_material": set(),
            "tetra_tool": set(), "kubejs_new": set(kj), "event_item": set(ev)}
    for ns, items in jars.items():
        for it in items:
            iid, leaf = ns + ":" + it, it.split("/")[-1].lower()
            pool["mod_normal"].add(iid)
    # tetra real ids
    for f in sorted(os.listdir(os.path.join(GAME, "mods"))):
        if f.endswith(".jar") and f.lower().startswith("tetra"):
            with zipfile.ZipFile(os.path.join(GAME, "mods", f)) as z:
                for n in z.namelist():
                    if n == "assets/tetra/lang/en_us.json":
                        for k in json.loads(z.read(n).decode("utf-8")):
                            if k.startswith("item.tetra."):
                                v = k[len("item.tetra."):]
                                if re.fullmatch(r"[a-z0-9_/]+", v):
                                    pool["tetra_tool"].add("tetra:" + v)
    # classify mod_normal into function/material
    for iid in list(pool["mod_normal"]):
        if iid.startswith("tetra:"):
            continue
        leaf = iid.split(":")[-1].split("/")[-1].lower()
        if _has(leaf, FUNC_SUF):
            pool["mod_normal"].discard(iid)
            pool["mod_function"].add(iid)
        elif _has(leaf, MAT_SUF):
            pool["mod_normal"].discard(iid)
            pool["mod_material"].add(iid)
    # survival-random pool: whole universe ∩ recipe results, minus creative/technical
    universe = set(pool["mod_normal"]) | set(pool["mod_function"]) | set(pool["mod_material"]) | \
        set(pool["tetra_tool"]) | set(pool["kubejs_new"]) | set(pool["event_item"])
    surv = sorted(i for i in universe if i in res and not NON_SURVIVAL.search(i))
    rnd = random.Random(SEED)
    out = {"seed": SEED, "per_category": PER_CAT, "random_n": RANDOM_N,
           "notes": "survival_random = universe ∩ recipe-results, minus creative/technical name heuristics; "
                    "deduped against all other categories",
           "categories": {}}
    used = set()
    for cat in ("mod_normal", "mod_function", "mod_material", "tetra_tool", "kubejs_new", "event_item"):
        items = sorted(pool[cat] - used)
        smp = rnd.sample(items, min(PER_CAT, len(items))) if items else []
        used.update(smp)
        out["categories"][cat] = {"pool_size": len(items), "sample_size": len(smp), "sample": smp}
    avail = [i for i in surv if i not in used]
    rnd2 = random.Random(SEED + 7)
    rsmp = rnd2.sample(avail, min(RANDOM_N, len(avail)))
    out["categories"]["survival_random"] = {"pool_size": len(surv), "available_after_dedup": len(avail),
                                           "sample_size": len(rsmp), "sample": rsmp}
    os.makedirs(OUT, exist_ok=True)
    io.open(os.path.join(OUT, "items.json"), "w", encoding="utf-8").write(json.dumps(out, indent=1, ensure_ascii=False))
    print("%-16s %8s %8s" % ("category", "pool", "sample"))
    for cat, d in out["categories"].items():
        print("%-16s %8s %8d" % (cat, d["pool_size"], d["sample_size"]))
    print("\nrecipe-result ids (survival signal):", len(res), "| survival pool:", len(surv))
    for cat, d in out["categories"].items():
        print("\n[%s] (%d)\n  %s" % (cat, d["sample_size"], "\n  ".join(d["sample"][:10])))
    print("\nwrote", os.path.join(OUT, "items.json"))


main()
