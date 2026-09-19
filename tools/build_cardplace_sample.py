import io, os, re, json, random, zipfile

GAME = r"C:/Users/skps9/Documents/packai_dev_game"
OUT = r"C:/Users/skps9/Documents/Code_Project/super_minecraft_AI_player/docs/research/artifacts/2026-09-19-cardplace-sample"
SEED = 20260919
PER_CAT = 20
VARIANT = re.compile(r"(_pulling_\d|_predicate|_inventory|_gui|_display|_template|_base)$")


def jar_items():
    out = {}
    for f in sorted(os.listdir(os.path.join(GAME, "mods"))):
        if not f.endswith(".jar"):
            continue
        try:
            with zipfile.ZipFile(os.path.join(GAME, "mods", f)) as z:
                for n in z.namelist():
                    if not (n.startswith("assets/") and "/models/item/" in n and n.endswith(".json")):
                        continue
                    p = n.split("/")
                    try:
                        k = p.index("item")
                    except ValueError:
                        continue
                    item = "/".join(p[k + 1:])[:-5]
                    if item and not VARIANT.search(item):
                        out.setdefault(p[1], set()).add(item)
        except Exception:
            continue
    return out


def tetra_lang_items():
    """real tetra item ids from assets/tetra/lang/en_us.json keys item.tetra.<id>"""
    ids = set()
    for f in sorted(os.listdir(os.path.join(GAME, "mods"))):
        if not f.endswith(".jar") or not f.lower().startswith("tetra"):
            continue
        try:
            with zipfile.ZipFile(os.path.join(GAME, "mods", f)) as z:
                for n in z.namelist():
                    if n == "assets/tetra/lang/en_us.json":
                        d = json.loads(z.read(n).decode("utf-8"))
                        for k in d:
                            if k.startswith("item.tetra."):
                                v = k[len("item.tetra."):]
                                if re.fullmatch(r"[a-z0-9_/]+", v):
                                    ids.add(v)
        except Exception:
            continue
    return sorted(ids)


def kubejs_items():
    cre = re.compile(r"\.create\(\s*['\"]([a-z0-9_\-:./]+)['\"]\s*\)")
    hits = set()
    for r, _, fs in os.walk(os.path.join(GAME, "kubejs", "startup_scripts")):
        for f in fs:
            if f.endswith(".js"):
                t = io.open(os.path.join(r, f), encoding="utf-8", errors="replace").read()
                for m in cre.finditer(t):
                    i = m.group(1)
                    hits.add(i if ":" in i else "kubejs:" + i)
    return sorted(hits)


def event_items():
    pat = re.compile(r"ItemEvents\.\w+\s*\(\s*['\"]([a-z0-9_\-:./]+)['\"]")
    hits = set()
    for r, _, fs in os.walk(os.path.join(GAME, "kubejs", "server_scripts")):
        for f in fs:
            if f.endswith(".js"):
                t = io.open(os.path.join(r, f), encoding="utf-8", errors="replace").read()
                for m in pat.finditer(t):
                    i = m.group(1)
                    hits.add(i if ":" in i else "kubejs:" + i)
    return sorted(hits)


def _has(leaf, sufs):
    return any(leaf == s or leaf.endswith("_" + s) for s in sufs)


FUNC_SUF = ("wand", "staff", "remote", "clicker", "orb", "tablet", "controller", "tome", "scroll",
            "charm", "sigil", "totem", "spawner", "gun", "bow", "sword", "pickaxe", "shovel", "hoe",
            "shears", "hammer", "drill", "saw", "lens", "gadget", "axe", "key", "kit", "horn", "whistle")
MAT_SUF = ("ingot", "gem", "dust", "plate", "rod", "nugget", "scrap", "fragment", "crystal", "billet",
           "casing", "sheet", "wire", "alloy", "chunk", "shard", "pellet", "powder", "coil", "foil",
           "blend", "clump", "slurry", "gear", "spring", "mesh", "block_of")


def main():
    jars = jar_items()
    tetra = tetra_lang_items()
    kj = kubejs_items()
    ev = event_items()
    pool = {"mod_normal": [], "mod_function": [], "mod_material": [],
            "tetra_tool": sorted({"tetra:" + i for i in tetra}),
            "kubejs_new": list(kj), "event_item": list(ev)}
    for ns, items in jars.items():
        if ns == "tetra":
            continue
        for it in items:
            iid, leaf = ns + ":" + it, it.split("/")[-1].lower()
            if _has(leaf, FUNC_SUF):
                pool["mod_function"].append(iid)
            elif _has(leaf, MAT_SUF):
                pool["mod_material"].append(iid)
            else:
                pool["mod_normal"].append(iid)
    rnd = random.Random(SEED)
    out = {"seed": SEED, "per_category": PER_CAT, "categories": {}}
    for cat, items in pool.items():
        items = sorted(set(items))
        smp = rnd.sample(items, min(PER_CAT, len(items))) if items else []
        out["categories"][cat] = {"pool_size": len(items), "sample_size": len(smp), "sample": smp}
    os.makedirs(OUT, exist_ok=True)
    io.open(os.path.join(OUT, "items.json"), "w", encoding="utf-8").write(json.dumps(out, indent=1, ensure_ascii=False))
    print("%-14s %8s %8s" % ("category", "pool", "sample"))
    for cat, d in out["categories"].items():
        print("%-14s %8d %8d" % (cat, d["pool_size"], d["sample_size"]))
    print("\ntetra item ids from jar lang:", len(tetra))
    for cat, d in out["categories"].items():
        print("\n[%s] (%d)\n  %s" % (cat, d["sample_size"], "\n  ".join(d["sample"][:8])))
    print("\nwrote", os.path.join(OUT, "items.json"))


main()
