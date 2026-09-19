"""packai card-placement sampler.

Two modes:
  pools   - scan the pack once, cache every category's FULL item pool to pools.json (deterministic)
  draw    - draw a FRESH random sample per category for each test run (seed recorded),
            dedupe across categories, write draw_<ts>.json (+ harness cases spec)

SK requirement (2026-09-19): every test run must randomly re-draw items per class,
so a run never reuses the previous run's items.

Usage:
  python tools/cardplace_sampler.py pools
  python tools/cardplace_sampler.py draw --per-cat 1 [--seed random|20260919] [--random-n 12]
"""
import argparse, datetime, io, json, os, random, re, secrets, zipfile

GAME = os.environ.get("PACKAI_GAME_DIR", r"C:/Users/skps9/Documents/packai_dev_game")
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "docs", "research", "artifacts",
                   "2026-09-19-cardplace-sample")
VARIANT = re.compile(r"(_pulling_\d|_predicate|_inventory|_gui|_display|_template|_base)$")
NON_SURVIVAL = re.compile(
    r"(creative|spawn_egg|debug|command_block|structure_block|barrier|jigsaw|light_block|"
    r"test_|unused|_icon|_display|_gui|_template|egg$|_spawner$|dummy|dev_)", re.I)
FUNC_SUF = ("wand", "staff", "remote", "clicker", "orb", "tablet", "controller", "tome", "scroll",
            "charm", "sigil", "totem", "spawner", "gun", "bow", "sword", "pickaxe", "shovel", "hoe",
            "shears", "hammer", "drill", "saw", "lens", "gadget", "axe", "key", "kit", "horn", "whistle")
MAT_SUF = ("ingot", "gem", "dust", "plate", "rod", "nugget", "scrap", "fragment", "crystal", "billet",
           "casing", "sheet", "wire", "alloy", "chunk", "shard", "pellet", "powder", "coil", "foil",
           "blend", "clump", "slurry", "gear", "spring", "mesh")
CATS = ("mod_normal", "mod_function", "mod_material", "tetra_tool", "kubejs_new", "event_item",
        "survival_random")


def rd(p):
    return io.open(p, encoding="utf-8", errors="replace").read()


def _has(leaf, sufs):
    return any(leaf == s or leaf.endswith("_" + s) for s in sufs)


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
    res = set()
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
                            for kk in ("item", "id"):
                                if isinstance(r.get(kk), str):
                                    res.add(r[kk])
                        elif isinstance(r, list):
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
            try:
                d = json.loads(rd(os.path.join(root, f)))
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
    return res


def kubejs_items():
    cre = re.compile(r"\.create\(\s*['\"]([a-z0-9_\-:./]+)['\"]\s*\)")
    hits = set()
    for r, _, fs in os.walk(os.path.join(GAME, "kubejs", "startup_scripts")):
        for f in fs:
            if f.endswith(".js"):
                for m in cre.finditer(rd(os.path.join(r, f))):
                    i = m.group(1)
                    hits.add(i if ":" in i else "kubejs:" + i)
    return hits


def event_items():
    pat = re.compile(r"ItemEvents\.\w+\s*\(\s*['\"]([a-z0-9_\-:./]+)['\"]")
    hits = set()
    for r, _, fs in os.walk(os.path.join(GAME, "kubejs", "server_scripts")):
        for f in fs:
            if f.endswith(".js"):
                for m in pat.finditer(rd(os.path.join(r, f))):
                    i = m.group(1)
                    hits.add(i if ":" in i else "kubejs:" + i)
    return hits


def build_pools():
    jars = jar_items()
    pool = {c: set() for c in CATS}
    for ns, items in jars.items():
        for it in items:
            pool["mod_normal"].add(ns + ":" + it)
    # tetra: only ids that BOTH have a lang entry and ship an item model (drops lang-only keys
    # like `forged_description` / `tooltip_expand`)
    tetra_lang, tetra_models = set(), set()
    for f in sorted(os.listdir(os.path.join(GAME, "mods"))):
        if f.endswith(".jar") and f.lower().startswith("tetra"):
            with zipfile.ZipFile(os.path.join(GAME, "mods", f)) as z:
                for n in z.namelist():
                    if n.startswith("assets/tetra/models/item/") and n.endswith(".json"):
                        tetra_models.add(n[len("assets/tetra/models/item/"):-5])
                    if n == "assets/tetra/lang/en_us.json":
                        for k in json.loads(z.read(n).decode("utf-8")):
                            if k.startswith("item.tetra."):
                                v = k[len("item.tetra."):]
                                if re.fullmatch(r"[a-z0-9_/]+", v):
                                    tetra_lang.add(v)
    for v in sorted(tetra_lang.intersection(tetra_models)):
        pool["tetra_tool"].add("tetra:" + v)
    for iid in list(pool["mod_normal"]):
        leaf = iid.split(":")[-1].split("/")[-1].lower()
        if _has(leaf, FUNC_SUF):
            pool["mod_normal"].discard(iid)
            pool["mod_function"].add(iid)
        elif _has(leaf, MAT_SUF):
            pool["mod_normal"].discard(iid)
            pool["mod_material"].add(iid)
    pool["kubejs_new"] = set(kubejs_items())
    pool["event_item"] = set(event_items())
    universe = set().union(*[pool[c] for c in CATS if c != "survival_random"])
    res = recipe_results()
    pool["survival_random"] = {i for i in universe if i in res and not NON_SURVIVAL.search(i)}
    return {c: sorted(pool[c]) for c in CATS}, len(res)


def cmd_pools(args):
    pools, n_res = build_pools()
    os.makedirs(OUT, exist_ok=True)
    p = os.path.join(OUT, "pools.json")
    io.open(p, "w", encoding="utf-8").write(json.dumps(
        {"game_dir": GAME.replace("\\", "/"), "recipe_result_ids": n_res,
         "counts": {c: len(v) for c, v in pools.items()}, "pools": pools},
        indent=1, ensure_ascii=False))
    print("pools written:", p)
    for c, v in pools.items():
        print("  %-16s %6d" % (c, len(v)))


def cmd_draw(args):
    p = os.path.join(OUT, "pools.json")
    if not os.path.exists(p):
        raise SystemExit("run `pools` first")
    d = json.loads(rd(p))
    pools = d["pools"]
    seed = secrets.randbelow(10 ** 9) if args.seed == "random" else int(args.seed)
    rnd = random.Random(seed)
    used, draw = set(), {}
    for c in CATS:
        if c == "survival_random":
            continue
        avail = [i for i in pools[c] if i not in used]
        k = min(args.per_cat, len(avail))
        smp = rnd.sample(avail, k) if k else []
        used.update(smp)
        draw[c] = smp
    avail = [i for i in pools["survival_random"] if i not in used]
    k = min(args.random_n, len(avail))
    smp = rnd.sample(avail, k) if k else []
    used.update(smp)
    draw["survival_random"] = smp
    ts = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    out = {"seed": seed, "seed_mode": args.seed, "per_cat": args.per_cat,
           "random_n": args.random_n, "ts": ts, "game_dir": d["game_dir"],
           "draw": draw, "total": sum(len(v) for v in draw.values())}
    op = os.path.join(OUT, "draw_%s.json" % ts)
    io.open(op, "w", encoding="utf-8").write(json.dumps(out, indent=1, ensure_ascii=False))
    cases = [{"id": "%s_%d" % (c, i + 1), "item": it} for c in CATS for i, it in enumerate(draw[c])]
    cp = os.path.join(OUT, "cases_%s.json" % ts)
    io.open(cp, "w", encoding="utf-8").write(json.dumps({"seed": seed, "cases": cases}, indent=1, ensure_ascii=False))
    print("seed=%s  total=%d" % (seed, out["total"]))
    for c in CATS:
        print("  %-16s %2d  %s" % (c, len(draw[c]), ", ".join(draw[c][:3])))
    print("\ndraw  ->", op)
    print("cases ->", cp)


ap = argparse.ArgumentParser()
sub = ap.add_subparsers(dest="cmd", required=True)
a1 = sub.add_parser("pools")
a1.set_defaults(fn=cmd_pools)
a2 = sub.add_parser("draw")
a2.add_argument("--per-cat", type=int, default=1)
a2.add_argument("--random-n", type=int, default=12)
a2.add_argument("--seed", default="random")
a2.set_defaults(fn=cmd_draw)
args = ap.parse_args()
args.fn(args)
