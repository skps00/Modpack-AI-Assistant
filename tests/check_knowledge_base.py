#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""KB-1 local knowledge base: config keys, classes, tool, jsonl fields. Add-only."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def setter_saves(cfg: str, name: str) -> None:
    needle = f"public static void {name}("
    assert needle in cfg, name
    idx = cfg.index(needle)
    chunk = cfg[idx : idx + 280]
    assert "SPEC.save()" in chunk, (name, chunk)


def main() -> None:
    entry = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/KnowledgeEntry.java")
    store = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/KnowledgeStore.java")
    lookup = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/KnowledgeLookup.java")
    unknown = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/UnknownItemLog.java")
    tool = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/KnowledgeLookupAskTool.java")
    cfg = read("forge/1.19.2/src/main/java/com/skps9/packai/config/PackAiConfig.java")
    ask = read("forge/1.19.2/src/main/java/com/skps9/packai/client/service/AskService.java")
    engine = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/AskEngine.java")
    loop = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/AskToolLoop.java")
    harness = read("forge/1.19.2/src/test/java/com/skps9/packai/logic/AskKnowledgeCheck.java")

    assert "class KnowledgeEntry" in entry
    assert "class KnowledgeStore" in store
    assert "class KnowledgeLookup" in lookup
    assert "class UnknownItemLog" in unknown
    assert "knowledge_lookup" in tool
    assert "class AskKnowledgeCheck" in harness

    assert 'define("knowledgeEnabled", true)' in cfg
    assert 'define("knowledgeRemote", false)' in cfg
    assert 'defineInRange("knowledgeCacheMaxMb"' in cfg
    assert 'defineInRange("unknownMaxLines"' in cfg
    assert 'defineInRange("unknownMaxMb"' in cfg

    setter_saves(cfg, "setKnowledgeEnabled")
    setter_saves(cfg, "setKnowledgeRemote")
    setter_saves(cfg, "setKnowledgeCacheMaxMb")
    setter_saves(cfg, "setUnknownMaxLines")
    setter_saves(cfg, "setUnknownMaxMb")

    for key in ('"ts"', '"item"', '"reason"', '"seen"'):
        assert f"addProperty({key}" in unknown, key
    assert "addProperty(\"question\"" not in unknown
    assert "addProperty(\"player\"" not in unknown
    assert "addProperty(\"name\"" not in unknown

    capable = loop[loop.index("CAPABLE_TOOLS") : loop.index("ALLOWLIST")]
    assert '"knowledge_lookup"' in capable
    assert "register(new KnowledgeLookupAskTool())" in engine
    assert "Pack AI knowledge item=" in ask
    assert "KnowledgeLookup.factsForItem" in ask
    assert "UnknownItemLog.record" in ask

    print("check_knowledge_base OK")


if __name__ == "__main__":
    main()
