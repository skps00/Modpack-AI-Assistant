#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""KB-1/KB-2 knowledge base: config keys, classes, tool, remote, settings. Add-only."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def method_body(cfg: str, name: str) -> str:
    """Brace-matched body of ``public static void {name}(...) { ... }``."""
    needle = f"public static void {name}("
    assert needle in cfg, name
    idx = cfg.index(needle)
    brace = cfg.find("{", idx)
    if brace < 0:
        raise AssertionError(f"{name}: no opening brace after signature")
    depth = 0
    for i in range(brace, len(cfg)):
        c = cfg[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return cfg[brace : i + 1]
    raise AssertionError(f"{name}: braces not balanced")


def setter_saves(cfg: str, name: str) -> None:
    body = method_body(cfg, name)
    assert "SPEC.save()" in body, (name, body)


def main() -> None:
    entry = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/KnowledgeEntry.java")
    store = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/KnowledgeStore.java")
    lookup = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/KnowledgeLookup.java")
    remote = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/KnowledgeRemote.java")
    unknown = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/UnknownItemLog.java")
    tool = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/KnowledgeLookupAskTool.java")
    cfg = read("forge/1.19.2/src/main/java/com/skps9/packai/config/PackAiConfig.java")
    ask = read("forge/1.19.2/src/main/java/com/skps9/packai/client/service/AskService.java")
    engine = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/AskEngine.java")
    loop = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/AskToolLoop.java")
    harness = read("forge/1.19.2/src/test/java/com/skps9/packai/logic/AskKnowledgeCheck.java")
    remote_harness = read(
        "forge/1.19.2/src/test/java/com/skps9/packai/logic/AskKnowledgeRemoteCheck.java"
    )
    # B3: PackAiSettingsScreen deleted — knowledge UI in SettingsRegistry + SettingsScreenV2
    settings_registry = read(
        "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/settings/SettingsRegistry.java"
    )
    settings = read(
        "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/settings/SettingsScreenV2.java"
    )
    en = read("forge/1.19.2/src/main/resources/assets/packai/lang/en_us.json")
    zh_cn = read("forge/1.19.2/src/main/resources/assets/packai/lang/zh_cn.json")
    zh_tw = read("forge/1.19.2/src/main/resources/assets/packai/lang/zh_tw.json")

    assert "class KnowledgeEntry" in entry
    assert "class KnowledgeStore" in store
    assert "class KnowledgeLookup" in lookup
    assert "class KnowledgeRemote" in remote
    assert "class UnknownItemLog" in unknown
    assert "knowledge_lookup" in tool
    assert "class AskKnowledgeCheck" in harness
    assert "class AskKnowledgeRemoteCheck" in remote_harness

    assert 'define("knowledgeEnabled", true)' in cfg
    assert 'define("knowledgeRemote", false)' in cfg
    assert 'defineInRange("knowledgeCacheMaxMb"' in cfg
    assert 'define("knowledgeUrl"' in cfg
    assert "skps00/packai-knowledge/main" in cfg
    assert 'defineInRange("unknownMaxLines"' in cfg
    assert 'defineInRange("unknownMaxMb"' in cfg

    setter_saves(cfg, "setKnowledgeEnabled")
    setter_saves(cfg, "setKnowledgeRemote")
    setter_saves(cfg, "setKnowledgeCacheMaxMb")
    setter_saves(cfg, "setKnowledgeUrl")
    setter_saves(cfg, "setUnknownMaxLines")
    setter_saves(cfg, "setUnknownMaxMb")

    assert "/items/" in remote
    assert "If-None-Match" in remote
    assert "MAX_BODY_BYTES" in remote
    assert "fetcherOverride" in remote
    assert "remoteOffNoFetch" in remote_harness
    assert "etag200Then304" in remote_harness

    # Tab.KNOWLEDGE gone — knowledge controls live under UiCategory.ADVANCED
    assert "UiCategory.ADVANCED" in settings_registry
    assert "ui.knowledgeEnabled" in settings_registry
    assert "ui.knowledgeUrl" in settings_registry
    assert "testKnowledgeConnection" in settings
    assert "KnowledgeRemote.testConnection" in settings
    assert "CompletableFuture" in settings

    for lang in (en, zh_cn, zh_tw):
        assert "packai.settings.tab.knowledge" in lang
        assert "packai.settings.knowledge_test" in lang
        assert "packai.settings.knowledge_url" in lang

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
    assert "ensureRemote" in lookup

    print("check_knowledge_base OK")


if __name__ == "__main__":
    main()
