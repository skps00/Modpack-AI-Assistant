# -*- coding: utf-8 -*-
"""CurseForge official Upload API helper (Pack AI 0.2.3, 1.19.2 Forge).

Token: env CURSEFORGE_API_TOKEN (or CF_API_TOKEN). Never printed.
Usage:
  python cf_upload.py --dry      # 只查 game versions + 印 metadata，唔上傳
  python cf_upload.py            # 真上傳
"""
import hashlib
import json
import os
import sys
import urllib.error
import urllib.request

PROJECT_ID = 1643097
JAR = r"C:\Users\skps9\Documents\Code_Project\super_minecraft_AI_player\dist\packai-0.2.3+mc1.19.2-forge.jar"
DISPLAY = "packai-0.2.3+mc1.19.2-forge"
MC_VERSION = "1.19.2"
LOADER = "Forge"
CHANGELOG = """0.2.3 (Forge 1.19.2)

- Mining / worldgen facts: for items you can only get by mining, answers now name the dimension, biome(s), height range, vein size and veins per chunk, read from the pack's own worldgen files (never guessed; if the pack has no entry it says so instead of inventing numbers).
- How you get it: loot-table / structure-chest / mob-drop / mining routes are pulled from the pack's own data files.
- Completeness line: when the pack index holds facts the answer did not mention, a short "On record, not in the answer:" line lists them, so nothing is silently dropped.
- Fixes along the way: dimension files are now indexed at all (they were silently skipped); completeness check no longer counts the answer's own item marker as "already mentioned"; route scanning no longer truncates before filtering (large packs kept losing biome/dimension rows); datapack dimension overrides no longer leave stale biome->dimension mappings.
"""


def api_get(path, tok):
    req = urllib.request.Request("https://minecraft.curseforge.com" + path, headers={"X-Api-Token": tok})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        print("api_get HTTPError", e.code, e.read().decode("utf-8", "replace")[:300])
        raise


def token_candidates():
    out = []
    for k in ("CURSEFORGE_API_TOKEN", "CF_API_TOKEN", "CURSEFORGE_TOKEN", "CURSEFORGE_AUTHOR_TOKEN"):
        t = os.environ.get(k)
        if t and t.strip():
            out.append((k, t.strip()))
    if not out:
        print("NO_TOKEN: 未設定（放 C:\\Users\\skps9\\AppData\\Local\\hermes\\.env 或環境變數）")
        sys.exit(2)
    return out


def token():
    cands = token_candidates()
    if len(cands) == 1:
        print("token source:", cands[0][0], "(value hidden)")
        return cands[0][1]
    # 多過一個：逐個試讀 API，揀第一個通嘅（唔印值）
    for name, val in cands:
        try:
            api_get("/api/game/versions", val)
            print("token source:", name, "(value hidden) — validated")
            return val
        except Exception as e:
            print("token", name, "failed:", type(e).__name__)
    print("NO_WORKING_TOKEN")
    sys.exit(3)


def pick_versions(versions):
    mc = [v for v in versions if v.get("name") == MC_VERSION]
    ld = [v for v in versions if v.get("name") == LOADER]
    print("1.19.2 candidates:", [(v["id"], v["name"], v["gameVersionTypeID"]) for v in mc])
    print("Forge candidates:", [(v["id"], v["name"], v["gameVersionTypeID"]) for v in ld])
    ids = []
    # Forge modloader entry: gameVersionTypeID 1 (from the site's gameVersionTypeId=1 filter)
    for v in ld:
        if v.get("gameVersionTypeID") == 1:
            ids.append(v["id"])
    for v in mc:
        ids.append(v["id"])
    return ids


def main():
    dry = "--dry" in sys.argv
    jar = JAR
    print("jar:", jar, "size:", os.path.getsize(jar), "sha256:", hashlib.sha256(open(jar, "rb").read()).hexdigest())
    tok = token()
    versions = api_get("/api/game/versions", tok)
    ids = pick_versions(versions)
    meta = {
        "changelog": CHANGELOG,
        "changelogType": "markdown",
        "displayName": DISPLAY,
        "gameVersions": ids,
        "releaseType": "release",
    }
    print("metadata:", json.dumps(meta, ensure_ascii=False)[:400])
    print("gameVersions:", ids)
    if dry:
        print("DRY RUN — 冇上傳")
        return
    boundary = "----packai0x2x3"
    parts = []
    parts.append(("--%s\r\nContent-Disposition: form-data; name=\"metadata\"\r\n\r\n%s\r\n" % (boundary, json.dumps(meta, ensure_ascii=False))).encode("utf-8"))
    parts.append(("--%s\r\nContent-Disposition: form-data; name=\"file\"; filename=\"%s\"\r\nContent-Type: application/java-archive\r\n\r\n" % (boundary, os.path.basename(jar))).encode("utf-8"))
    parts.append(open(jar, "rb").read())
    parts.append(("\r\n--%s--\r\n" % boundary).encode("utf-8"))
    body = b"".join(parts)
    req = urllib.request.Request(
        "https://minecraft.curseforge.com/api/projects/%d/upload-file" % PROJECT_ID,
        data=body,
        headers={"X-Api-Token": tok, "Content-Type": "multipart/form-data; boundary=%s" % boundary},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=600) as r:
            print("HTTP", r.status, r.read().decode("utf-8")[:400])
    except urllib.error.HTTPError as e:
        print("HTTPError", e.code, e.read().decode("utf-8", "replace")[:600])


if __name__ == "__main__":
    main()
