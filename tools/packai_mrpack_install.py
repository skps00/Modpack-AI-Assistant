#!/usr/bin/env python3
"""Install a Modrinth modpack (.mrpack) as a Prism instance — no GUI, no API key.

Why: the server test route needs MORE packs (SK: "download and test more pack"), and
CurseForge's download API needs a Core key we do not have. Modrinth is public, so this
tool pulls a pack straight from its API, unpacks the overrides, downloads every required
client file (sha1-checked), and writes the Prism files (mmc-pack.json / instance.cfg) so
`packai_smoke_linux.py` can boot it.

Usage:
  python3 tools/packai_mrpack_install.py --slug create_plus --name packai_sandbox_createplus
  python3 tools/packai_mrpack_install.py --slug minimalcreate --dry-run

Layout produced: <prism-root>/instances/<name>/{mmc-pack.json,instance.cfg,minecraft/...}
"""
from __future__ import annotations

import argparse
import json
import hashlib
import io
import os
import shutil
import sys
import time
import urllib.parse
import urllib.request
import uuid
import zipfile

API = "https://api.modrinth.com/v2"
UA = {"User-Agent": "packai-server-test/1.0 (skps952608@gmail.com)"}
DEFAULT_PRISM_ROOT = os.path.expanduser("~/opt/prism-data")
DEFAULT_JAVA = os.path.expanduser("~/opt/jdk-17.0.20.1+1/bin/java")
GAME = "1.19.2"
LOADER = "forge"


def log(msg: str) -> None:
    print("%s %s" % (time.strftime("%H:%M:%S"), msg), flush=True)


def get_json(url: str):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.load(r)


def fetch(url: str, dest: str, sha1: str = "", tries: int = 3) -> tuple:
    """Download to dest (atomic). Returns (ok, bytes, note)."""
    for attempt in range(1, tries + 1):
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=300) as r:
                data = r.read()
            got = hashlib.sha1(data).hexdigest()
            if sha1 and got != sha1:
                return False, 0, "sha1 mismatch (%s != %s)" % (got[:12], sha1[:12])
            tmp = dest + ".part"
            with open(tmp, "wb") as fh:
                fh.write(data)
            os.replace(tmp, dest)
            return True, len(data), ""
        except Exception as exc:  # noqa: BLE001 - report, retry
            if attempt == tries:
                return False, 0, repr(exc)[:160]
            time.sleep(2 * attempt)
    return False, 0, "unreachable"


def pick_version(slug: str, game: str, loader: str, version_id: str = "") -> dict:
    if version_id:
        return get_json("%s/version/%s" % (API, version_id))
    q = urllib.parse.urlencode({"loaders": json.dumps([loader]),
                                "game_versions": json.dumps([game])})
    versions = get_json("%s/project/%s/version?%s" % (API, slug, q))
    if not versions:
        raise SystemExit("冇搵到 %s 嘅 %s %s 版本" % (slug, loader, game))
    return versions[0]


def write_prism_files(root: str, name: str, game: str, forge_ver: str, java: str, mem_mb: int) -> None:
    pack = {
        "components": [
            {"cachedName": "LWJGL 3", "cachedVersion": "3.3.1", "cachedVolatile": True,
             "dependencyOnly": True, "uid": "org.lwjgl3", "version": "3.3.1"},
            {"cachedName": "Minecraft", "cachedRequires": [{"suggests": "3.3.1", "uid": "org.lwjgl3"}],
             "cachedVersion": game, "important": True, "uid": "net.minecraft", "version": game},
        ],
        "formatVersion": 1,
    }
    if forge_ver:
        pack["components"].append({
            "cachedName": "Forge", "cachedRequires": [{"equals": game, "uid": "net.minecraft"}],
            "cachedVersion": forge_ver, "uid": "net.minecraftforge", "version": forge_ver})
    with io.open(os.path.join(root, "mmc-pack.json"), "w", encoding="utf-8") as fh:
        json.dump(pack, fh, indent=4)

    cfg = "\r\n".join([
        "[General]",
        "ManagedPack=false",
        "name=%s" % name,
        "InstanceType=OneSix",
        "uuid=%s" % uuid.uuid4().hex,
        "ConfigVersion=1.3",
        "JavaPath=%s" % java,
        "OverrideJavaLocation=true",
        "OverrideMemory=true",
        "MinMemAlloc=1024",
        "MaxMemAlloc=%d" % mem_mb,
        "AutomaticJava=true",
        "",
    ])
    with io.open(os.path.join(root, "instance.cfg"), "w", encoding="utf-8") as fh:
        fh.write(cfg)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--slug", required=True, help="Modrinth project slug / id")
    ap.add_argument("--name", default="", help="instance 目錄名（default: packai_sandbox_<slug>）")
    ap.add_argument("--prism-root", default=DEFAULT_PRISM_ROOT)
    ap.add_argument("--game", default=GAME)
    ap.add_argument("--loader", default=LOADER)
    ap.add_argument("--version-id", default="", help="指定 Modrinth version id（否則用最新）")
    ap.add_argument("--java", default=DEFAULT_JAVA)
    ap.add_argument("--mem-mb", type=int, default=4096)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--force", action="store_true", help="已有 instance 都照覆蓋")
    a = ap.parse_args()

    name = a.name or ("packai_sandbox_" + a.slug.replace("_", "").replace("-", ""))
    root = os.path.join(a.prism_root, "instances", name)
    if os.path.isdir(root) and not a.force:
        log("instance 已存在：%s（要覆蓋加 --force）" % root)
        return 2

    v = pick_version(a.slug, a.game, a.loader, a.version_id)
    files = v.get("files") or []
    primary = next((f for f in files if f.get("primary")), files[0] if files else None)
    if not primary:
        log("version 冇檔（%s）" % v.get("version_number"))
        return 3
    log("pack=%s version=%s file=%s (%.1f MB) sha1=%s" % (
        a.slug, v["version_number"], primary["filename"],
        primary["size"] / 1e6, (primary.get("hashes", {}).get("sha1") or "")[:12]))
    if a.dry_run:
        for f in files:
            log("  would download %s (%.1f MB)" % (f["filename"], f["size"] / 1e6))
        return 0

    os.makedirs(root, exist_ok=True)
    game_dir = os.path.join(root, "minecraft")
    os.makedirs(game_dir, exist_ok=True)

    mrpack = "/tmp/%s_%s.mrpack" % (a.slug, v["id"][:8])
    ok, n, note = fetch(primary["url"], mrpack, primary.get("hashes", {}).get("sha1", ""))
    if not ok:
        log("下載 mrpack 失敗：%s" % note)
        return 4
    log("mrpack 下載好（%.1f MB）" % (n / 1e6))

    with zipfile.ZipFile(mrpack) as z:
        index = json.loads(z.read("modrinth.index.json").decode("utf-8"))
        for prefix in ("overrides/", "client-overrides/"):
            for m in z.namelist():
                if m.startswith(prefix) and not m.endswith("/"):
                    rel = m[len(prefix):]
                    dst = os.path.join(game_dir, rel)
                    os.makedirs(os.path.dirname(dst), exist_ok=True)
                    with z.open(m) as src, open(dst, "wb") as out:
                        shutil.copyfileobj(src, out)
    log("overrides 解好")

    deps = index.get("dependencies", {})
    forge_ver = deps.get("forge", "")
    log("deps: %s" % json.dumps(deps))
    write_prism_files(root, name, deps.get("minecraft", a.game), forge_ver, a.java, a.mem_mb)

    dl = skip = fail = 0
    total_bytes = 0
    failed = []
    for f in index.get("files", []):
        env = (f.get("env") or {}).get("client", "required")
        if env == "unsupported":
            continue
        dst = os.path.join(game_dir, f["path"])
        want = (f.get("hashes") or {}).get("sha1", "")
        if os.path.isfile(dst) and want:
            with open(dst, "rb") as fh:
                if hashlib.sha1(fh.read()).hexdigest() == want:
                    skip += 1
                    continue
        if not (f.get("downloads") or []):
            fail += 1
            failed.append((f["path"], "no download url"))
            continue
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        ok, n, note = fetch(f["downloads"][0], dst, want)
        if ok:
            dl += 1
            total_bytes += n
        else:
            fail += 1
            failed.append((f["path"], note))
        if (dl + fail) % 40 == 0:
            log("  … %d downloaded / %d failed" % (dl, fail))
    log("檔案：downloaded=%d skipped=%d failed=%d（%.1f MB）" % (dl, skip, fail, total_bytes / 1e6))
    for path, note in failed[:15]:
        log("  FAIL %s — %s" % (path, note))
    mods = len([x for x in os.listdir(os.path.join(game_dir, "mods"))
                if x.endswith(".jar")]) if os.path.isdir(os.path.join(game_dir, "mods")) else 0
    log("instance 準備好：%s（mods=%d）" % (root, mods))
    log("下一步：python3 tools/packai_smoke_linux.py --instance %s --cases <cases.json> --world <world>" % name)
    return 0 if fail == 0 else 5


if __name__ == "__main__":
    sys.exit(main())
