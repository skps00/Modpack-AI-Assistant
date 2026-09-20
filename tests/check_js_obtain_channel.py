#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""v6.14 js-obtain channel static asserts (Forge-only)."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    sites = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/JsObtainSites.java")
    cfg = read("forge/1.19.2/src/main/java/com/skps9/packai/config/PackAiConfig.java")
    idx = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/PackIndex.java")
    reply = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/ReplyLang.java")
    ctx = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/AskToolContext.java")
    harness = read(
        "forge/1.19.2/src/test/java/com/skps9/packai/logic/AskJsObtainSitesCheck.java"
    )
    fixture = read("docs/plans/fixtures/2026-09-16_nfwc_js_obtain_inventory.json")

    # Java source chars (not runtime string): backslash-escaped regex literal
    forbidden_src = r'(src:)|(\\.js\\b)|(\\.json\\b)|(kubejs[/\\\\])'
    assert forbidden_src in sites, "PLAYER_VISIBLE_FORBIDDEN drift vs plan"
    assert r"(src:)|(\.js\b)|(\.json\b)|(kubejs[/\\])" in fixture or forbidden_src in fixture

    assert "class JsObtainSites" in sites
    assert "record Site(" in sites
    assert "record Trigger(" in sites
    assert "TriggerKind" in sites
    assert "GateKind" in sites
    assert "triggerLabel" in sites
    assert "js-obtain-diag.jsonl" in sites
    assert "appendDiag" in sites

    assert "JS_OBTAIN_CHANNEL" in cfg
    assert "JS_OBTAIN_DIAG_LOG" in cfg
    assert "jsObtainChannel" in cfg
    assert "jsObtainDiagLog" in cfg
    assert "boolean jsObtainChannel()" in cfg
    assert "boolean jsObtainDiagLog()" in cfg
    assert "SPEC.save()" in cfg

    assert "jsObtainByOutput" in idx
    assert "indexJsObtainSites" in idx
    assert "MAX_JS_OBTAIN_LINES" in ctx
    assert "jsObtainByOutput.clear()" in idx
    assert "beginAskSession" in idx
    # must NOT clear jsObtain in beginAskSession
    begin = idx.split("void beginAskSession()")[1].split("public RetrieveResult")[0]
    assert "jsObtainByOutput" not in begin

    assert "jsProduce(" in reply
    assert "jsSwap(" in reply
    assert "jsTransform(" in reply
    assert "packai.reply.js_produce" in reply

    for lang in ("zh_cn", "zh_tw", "en_us"):
        lang_txt = read(f"forge/1.19.2/src/main/resources/assets/packai/lang/{lang}.json")
        assert "packai.reply.js_produce" in lang_txt
        assert "packai.reply.js_obtain_organ" in lang_txt

    assert "AskJsObtainSitesCheck" in harness
    assert "s17_sites" in harness
    assert "PLAYER_VISIBLE_FORBIDDEN" in harness
    assert "triggerLabelRules" in harness

    assert "s17_site_count" in fixture
    assert '"s17_site_count": 11' in fixture or '"s17_site_count":11' in fixture

    print("check_js_obtain_channel OK")


if __name__ == "__main__":
    main()
