#!/usr/bin/env python3
"""Static gate for LD5 obtain graph disk cache (T4)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE_LOGIC = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic"


def read(name: str) -> str:
    return (FORGE_LOGIC / name).read_text(encoding="utf-8")


def main() -> None:
    fails: list[str] = []
    cache = read("ObtainGraphCache.java")
    runtime = read("ObtainGraphRuntime.java")

    if "class ObtainGraphCache" not in cache:
        fails.append("missing ObtainGraphCache class")
    if "MAX_CACHE_BYTES" not in cache or "8 * 1024 * 1024" not in cache:
        fails.append("missing 8 MB cap constant")
    if "GZIP_THRESHOLD_BYTES" not in cache or "2 * 1024 * 1024" not in cache:
        fails.append("missing 2 MB gzip threshold")
    if "GZIPInputStream" not in cache or "GZIPOutputStream" not in cache:
        fails.append("missing gzip stream support")
    if ".json.gz" not in cache:
        fails.append("missing .json.gz cache path")
    if "reason=cap" not in cache:
        fails.append("missing cap skip log reason=cap")

    fp_blob = cache
    for token in (
        "modsStamp",
        "SCHEMA_VERSION",
        "BUILDER_VERSION",
        "datapackDirHash",
        "forgeVersion",
    ):
        if token not in fp_blob:
            fails.append(f"fingerprint missing {token}")

    if "obtain.cache.corrupt" not in cache:
        fails.append("missing corrupt log")
    if not re.search(r"catch\s*\(\s*Throwable", cache):
        fails.append("cache IO must catch Throwable")

    if "ObtainGraphCache.tryLoad" not in runtime:
        fails.append("onRecipesUpdated must try cache load first")
    if "obtain.cache_write=skipped" not in runtime:
        fails.append("runtime must log obtain.cache_write=skipped when write returns 0")
    if re.search(r"cacheBytes\s*>\s*0", runtime) is None:
        fails.append("runtime must gate ok log on tryWrite return > 0")

    for label, src in (("ObtainGraphCache", cache), ("ObtainGraphRuntime", runtime)):
        if "System.exit" in src:
            fails.append(f"{label} must not call System.exit")
        if re.search(r"throw\s+new\s+RuntimeException", src):
            fails.append(f"{label} must not throw RuntimeException on cache path")

    if fails:
        for f in fails:
            print("FAIL", f)
        sys.exit(1)
    print("check_obtain_graph_cache OK")


if __name__ == "__main__":
    main()
