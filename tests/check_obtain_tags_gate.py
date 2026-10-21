#!/usr/bin/env python3
"""Static gate for T6e obtain-graph tags gate + cache tag stamp + bench probes."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/client/ClientSetup.java"
LOGIC = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic"
RUNTIME = LOGIC / "ObtainGraphRuntime.java"
CACHE = LOGIC / "ObtainGraphCache.java"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def main() -> None:
    fails: list[str] = []
    client = read(CLIENT)
    runtime = read(RUNTIME)
    cache = read(CACHE)

    if "TagsUpdatedEvent" not in client or "onTagsUpdated" not in client:
        fails.append("ClientSetup must register TagsUpdatedEvent -> onTagsUpdated")

    if "tagsApplied" not in runtime or "markTagsApplied" not in runtime:
        fails.append("ObtainGraphRuntime missing tagsApplied / markTagsApplied")
    if "mayStartBuild" not in runtime and "!mayStartBuild()" not in runtime:
        fails.append("ObtainGraphRuntime missing tags gate before build")
    if "tagsBypass" not in runtime or "tags_gate=bypass" not in runtime:
        fails.append("ObtainGraphRuntime missing tags gate bypass path")

    if "tagsAppliedForCache()" not in runtime or "tryWrite" not in runtime:
        fails.append("cache write must be gated on tagsApplied")
    if runtime.find("tryWrite") < runtime.find("tagsAppliedForCache"):
        fails.append("tryWrite must follow tagsAppliedForCache check")

    if "computeTagsStamp" not in cache:
        fails.append("ObtainGraphCache missing computeTagsStamp")
    if "joinFingerprint" not in cache or "tagsStamp" not in cache:
        fails.append("fingerprint must include tags stamp")

    if "tags=" not in runtime or "sentinel=" not in runtime:
        fails.append("bench log must include tags= and sentinel=")

    if fails:
        for f in fails:
            print("FAIL", f)
        sys.exit(1)
    print("check_obtain_tags_gate OK")


if __name__ == "__main__":
    main()
