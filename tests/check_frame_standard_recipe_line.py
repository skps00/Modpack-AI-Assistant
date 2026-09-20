#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Plan A: STANDARD frame how-to-get = deterministic recipe line (static pins)."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE = ROOT / "forge/1.19.2"
LANGS = {
    "en_us": FORGE / "src/main/resources/assets/packai/lang/en_us.json",
    "zh_cn": FORGE / "src/main/resources/assets/packai/lang/zh_cn.json",
    "zh_tw": FORGE / "src/main/resources/assets/packai/lang/zh_tw.json",
}
ENGINE = FORGE / "src/main/java/com/skps9/packai/logic/AskEngine.java"
SCRUB = FORGE / "src/main/java/com/skps9/packai/logic/AskReplyScrub.java"
HONEST = FORGE / "src/main/java/com/skps9/packai/logic/HonestMiss.java"

# Pin: HOW_TO_GET_LABEL / EMPTY_HOW_TO_GET prose must not drift (parity / mirror checks).
HOW_TO_GET_LABEL_PIN = (
    "(?:怎么来|怎么來|怎麼来|怎麼來|怎样来|怎樣來|How to get|取得方式|获取方式|獲取方式|取得方法|How to obtain)"
)
# EMPTY_HOW_TO_GET is split across two Java string literals — pin both halves.
EMPTY_HOW_TO_GET_PIN_A = (
    r'(?=[ \\t]*(?:#{1,3}[ \\t]*)?(?:\\d+[.)][ \\t]*)?(?:怎么用|怎麼用|怎样用|怎樣用|How to use|作为材料|作為材料)'
)
EMPTY_HOW_TO_GET_PIN_B = r'|【来源】|【來源】|\\[Sources\\]|\\z)'

SHAPED = {
    "zh_cn": "合成台（有序合成）：%1$s → %2$s。摆放位置请以 JEI 为准。这是空白模组框架合成版本。",
    "zh_tw": "合成台（有序合成）：%1$s → %2$s。擺放位置請以 JEI 為準。這是空白模組框架合成版本。",
    "en_us": (
        "Crafting table (shaped): %1$s → %2$s. Check JEI for the layout. "
        "This is the empty modular / empty-frame craft version."
    ),
}
SHAPELESS = {
    "zh_cn": "合成台（无序合成）：%1$s → %2$s。这是空白模组框架合成版本。",
    "zh_tw": "合成台（無序合成）：%1$s → %2$s。這是空白模組框架合成版本。",
    "en_us": (
        "Crafting table (shapeless): %1$s → %2$s. "
        "This is the empty modular / empty-frame craft version."
    ),
}
VARIANTS = {
    "zh_cn": "（同族材料版本共 %1$s 种，其他木板／石料版本一样可以合成）",
    "zh_tw": "（同族材料版本共 %1$s 種，其他木板／石料版本一樣可以合成）",
    "en_us": (
        " (this frame family has %1$s material variants; "
        "other plank/stone versions craft the same frame)"
    ),
}


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


def extract_java_string(src: str, name: str) -> str:
    # private static final String NAME = "....";  (may span concat)
    m = re.search(
        rf'(?:private\s+)?static\s+final\s+String\s+{re.escape(name)}\s*=\s*((?:"(?:\\.|[^"\\])*"\s*\+\s*)*"(?:\\.|[^"\\])*")\s*;',
        src,
    )
    if not m:
        raise AssertionError(f"missing Java string {name}")
    parts = re.findall(r'"(?:\\.|[^"\\])*"', m.group(1))
    out = "".join(json.loads(p) for p in parts)
    return out


def main() -> int:
    for code, path in LANGS.items():
        data = json.loads(path.read_text(encoding="utf-8"))
        for key, want in (
            ("packai.reply.frame_standard_recipe", SHAPELESS[code]),
            ("packai.reply.frame_standard_recipe_shaped", SHAPED[code]),
            ("packai.reply.frame_standard_recipe_variants", VARIANTS[code]),
        ):
            if key not in data:
                print(f"FAIL: {path.name} missing {key}")
                return 1
            if data[key] != want:
                print(f"FAIL: {path.name} {key} mismatch")
                print("  got:", data[key])
                print("  want:", want)
                return 1
            if "%1$s" not in data[key] and key.endswith("variants"):
                # variants uses %1$s
                print(f"FAIL: {path.name} {key} missing %1$s")
                return 1
            if key != "packai.reply.frame_standard_recipe_variants":
                if "%1$s" not in data[key] or "%2$s" not in data[key]:
                    print(f"FAIL: {path.name} {key} missing placeholders")
                    return 1

    engine = read(ENGINE)
    assert "replaceHowToGetBody" in engine
    assert "ensureFrameStandardRecipeVisible" in engine
    assert "how-to-get replaced (STANDARD frame)" in engine
    assert "how-to-get heading not found -> appended" in engine
    assert "frame-standard: recipe line inserted" in engine
    # Both replace path and append fallback must remain.
    assert "AskReplyScrub.replaceHowToGetBody" in engine
    assert "HonestMiss.ensureFrameStandardRecipeVisible" in engine

    honest = read(HONEST)
    assert "frameStandardRecipeLine" in honest
    assert "frame_standard_recipe_shaped" in honest
    assert "frame_standard_recipe_variants" in honest

    scrub = read(SCRUB)
    assert "replaceHowToGetBody" in scrub
    label = extract_java_string(scrub, "HOW_TO_GET_LABEL")
    if label != HOW_TO_GET_LABEL_PIN:
        print("FAIL: HOW_TO_GET_LABEL changed")
        print("  got:", label)
        return 1
    # EMPTY_HOW_TO_GET must keep its unanchored lookahead (do not reuse it as section end).
    if EMPTY_HOW_TO_GET_PIN_A not in scrub or EMPTY_HOW_TO_GET_PIN_B not in scrub:
        print("FAIL: EMPTY_HOW_TO_GET lookahead prose changed")
        return 1
    # replaceHowToGetBody must NOT use EMPTY_HOW_TO_GET as end bound.
    # Extract method body roughly and ban EMPTY_HOW_TO_GET inside it.
    m = re.search(
        r"public static String replaceHowToGetBody\(.*?\)\s*\{(.*?)\n    \}",
        scrub,
        re.S,
    )
    if not m:
        print("FAIL: replaceHowToGetBody method not found")
        return 1
    body = m.group(1)
    if "EMPTY_HOW_TO_GET" in body:
        print("FAIL: replaceHowToGetBody must not use EMPTY_HOW_TO_GET for end bound")
        return 1
    for need in ("HOW_TO_USE_HEAD", "HOW_TO_UPGRADE_HEAD", "AS_MATERIAL_HEAD", "ReplySources.HEADER"):
        if need not in body:
            print(f"FAIL: replaceHowToGetBody missing end bound {need}")
            return 1

    print("check_frame_standard_recipe_line OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
