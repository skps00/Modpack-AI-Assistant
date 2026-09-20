#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Plan F stage-1 + fix1: ModularFrameStandard static asserts (Forge-only)."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE = ROOT / "forge/1.19.2"
LANGS = [
    FORGE / "src/main/resources/assets/packai/lang/en_us.json",
    FORGE / "src/main/resources/assets/packai/lang/zh_cn.json",
    FORGE / "src/main/resources/assets/packai/lang/zh_tw.json",
]
JAVA = FORGE / "src/main/java/com/skps9/packai/logic/ModularFrameStandard.java"
ENGINE = FORGE / "src/main/java/com/skps9/packai/logic/AskEngine.java"
HONEST = FORGE / "src/main/java/com/skps9/packai/logic/HonestMiss.java"
HARNESS = FORGE / "src/test/java/com/skps9/packai/logic/ModularFrameStandardCheck.java"

OLD_NEG = re.compile(
    r"(只是空框架合成，不是这把定制工具的取得方式)"
    r"|(只是空框架合成，不是這把定製工具的取得方式)"
    r"|(empty-frame craft only -- not how this customized tool was obtained)"
)
# Java string / char literals only (not comments): "..." or '...'
JAVA_STRING_LIT = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])\'')
CJK = re.compile(r"[\u3400-\u9fff]")


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


def cjk_in_string_literals(src: str) -> list[str]:
    hits: list[str] = []
    for m in JAVA_STRING_LIT.finditer(src):
        lit = m.group(0)
        if CJK.search(lit):
            hits.append(lit[:80])
    return hits


def main() -> int:
    java = read(JAVA)
    assert "enum Kind" in java
    assert "STANDARD" in java and "MODIFIED" in java and "UNKNOWN" in java
    assert "installExpectedPartMaps" in java
    assert "tetraBlankFramePartMaps" in java or "TETRA_BLANK_FRAMES" in java
    assert "partsFromToolBuildText" in java
    assert "record Match" in java or "class Match" in java or "Match(" in java
    assert "FrameRecipe" in java
    assert "classifyDetailed" in java
    assert "Files." not in java and "ZipFile" not in java and "Http" not in java
    assert "ToolBuildFacts.UNPARSED" in java
    # fix1: static craft metadata (no runtime jar I/O)
    assert "tetra:stonecutter" in java
    assert "tetra:modular_sword" in java
    assert "ingredientItemIds" in java
    # Plan A: shapeless / variantCount on FrameRecipe + install pass-through
    assert "boolean shapeless" in java or "shapeless," in java
    assert "variantCount" in java
    assert re.search(
        r"new FrameRecipe\(\s*norm,\s*r\.ingredientItemIds\(\),\s*r\.resultItemId\(\),\s*"
        r"r\.shapeless\(\),\s*r\.variantCount\(\)\s*\)",
        java,
    ), "installExpectedRecipes must pass-through shapeless+variantCount (5-arg)"

    # S11: classifier must be language-agnostic (no CJK in string literals).
    cjk_hits = cjk_in_string_literals(java)
    if cjk_hits:
        print("FAIL: ModularFrameStandard.java CJK string literal:", cjk_hits[0])
        return 1

    engine = read(ENGINE)
    assert "ModularFrameStandard.classifyDetailedInstalled" in engine or (
        "ModularFrameStandard.classifyInstalled" in engine
        and "classifyDetailed" in java
    )
    assert "ensureAskMissAcquirePlayerVisible" in engine
    assert "ensureFrameStandardRecipeVisible" in engine
    assert "honest-miss: askMissAcquirePlayer inserted" in engine
    assert "frame-standard: recipe line inserted" in engine
    assert "replaceHowToGetBody" in engine

    honest = read(HONEST)
    assert "ensureAskMissAcquirePlayerVisible" in honest
    assert "ensureFrameStandardRecipeVisible" in honest
    assert "frameStandardRecipeLine" in honest
    scrub = read(FORGE / "src/main/java/com/skps9/packai/logic/AskReplyScrub.java")
    assert "replaceHowToGetBody" in scrub
    # HonestMiss already has acquire-question CJK literals (pre-existing); do not gate here.
    harness = read(HARNESS)
    assert "standardS4" in harness
    assert "modifiedS4" in harness
    assert "unknownS4" in harness
    assert "stubDiscriminates" in harness
    assert "s8Cost" in harness
    assert "s10NoOverrideAcquire" in harness
    assert "fix1StandardRecipeInsert" in harness
    # S11 on harness too (plan whitelist); ReplyLang may return CJK at runtime — literals must not.
    harness_cjk = cjk_in_string_literals(harness)
    if harness_cjk:
        print("FAIL: ModularFrameStandardCheck.java CJK string literal:", harness_cjk[0])
        return 1

    for lang_path in LANGS:
        data = json.loads(lang_path.read_text(encoding="utf-8"))
        frame_key = "packai.reply.frame_standard_recipe"
        if frame_key not in data:
            print(f"FAIL: {lang_path.name} missing {frame_key}")
            return 1
        frame = data[frame_key]
        if "%1$s" not in frame or "%2$s" not in frame:
            print(f"FAIL: {lang_path.name} {frame_key} missing placeholders")
            return 1
        for key in (
            "packai.reply.llm_style",
            "packai.reply.llm_style_notools",
            "packai.reply.tool_build",
        ):
            text = data[key]
            if OLD_NEG.search(text):
                print(f"FAIL: {lang_path.name} {key} still has old negation policy")
                return 1
            # new semantics tokens
            low = text.lower()
            ok_new = (
                "空白模组剑合成" in text
                or "空白模組劍合成" in text
                or "empty-frame" in low
                or "empty modular" in low
            )
            if not ok_new:
                print(f"FAIL: {lang_path.name} {key} missing blank-frame craft label")
                return 1
            # fix1: STANDARD vs modified must be distinct in policy text
            ok_split = (
                ("标准框架" in text and "特制版" in text)
                or ("標準框架" in text and "特製版" in text)
                or ("STANDARD" in text and "Modified" in text)
                or ("standard recipe" in low and "modified" in low)
            )
            if not ok_split:
                print(f"FAIL: {lang_path.name} {key} missing STANDARD/modified split")
                return 1
            # notools must not name tools
            if key.endswith("notools"):
                for bad in (
                    "render_recipe_cards",
                    "jei_lookup",
                    "jei_info_use",
                    "jei_info_acquire",
                    "dump_level",
                ):
                    if bad in text:
                        print(f"FAIL: {lang_path.name} notools mentions {bad}")
                        return 1

    print("check_modular_frame_standard OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
