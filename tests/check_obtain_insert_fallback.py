#!/usr/bin/env python3
"""Static gate: obtain path insert fallback when how-to-get heading is missing (T6c)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE_LOGIC = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic"
FORGE_TEST = ROOT / "forge/1.19.2/src/test/java/com/skps9/packai/logic"


def read_logic(name: str) -> str:
    return (FORGE_LOGIC / name).read_text(encoding="utf-8")


def read_test(name: str) -> str:
    return (FORGE_TEST / name).read_text(encoding="utf-8")


def main() -> None:
    fails: list[str] = []
    scrub = read_logic("AskReplyScrub.java")
    engine = read_logic("AskEngine.java")
    harness = read_test("ObtainFluidChainCheck.java")

    if "insertAtHowToGetTopOrAppend" not in scrub:
        fails.append("AskReplyScrub missing insertAtHowToGetTopOrAppend")

    m = re.search(
        r"public static String insertAtHowToGetTopOrAppend\s*\([^)]*\)\s*\{(.*?)^\s{4}\}",
        scrub,
        re.DOTALL | re.MULTILINE,
    )
    if not m:
        fails.append("cannot parse insertAtHowToGetTopOrAppend body")
    else:
        body = m.group(1)
        if re.search(
            r"if\s*\(\s*!head\.find\(\)\s*\)\s*\{\s*return\s+body\s*;\s*\}",
            body,
        ):
            fails.append("missing-heading branch must not silently return body")
        if "ReplySources.HEADER" not in body:
            fails.append("fallback must insert before ReplySources footer")
        if "insertAtHowToGetTop(body" not in body:
            fails.append("fallback must delegate when HOW_TO_GET_HEAD matches")

    if "insertAtHowToGetTopOrAppend" not in engine:
        fails.append("AskEngine must call insertAtHowToGetTopOrAppend")
    if "obtain.path_fallback" not in engine:
        fails.append("AskEngine trace missing obtain.path_fallback")
    if "ReplyLang.obtainHead" not in engine:
        fails.append("AskEngine must pass ReplyLang.obtainHead heading")

    for case in (
        "caseObtainInsertFallbackAfterHowToUse",
        "caseObtainInsertFallbackExistingHeadingMatchesTop",
        "caseObtainInsertFallbackSkipsDuplicateLine",
    ):
        if case not in harness:
            fails.append(f"ObtainFluidChainCheck missing {case}")

    if fails:
        for f in fails:
            print("FAIL", f)
        sys.exit(1)
    print("check_obtain_insert_fallback OK")


if __name__ == "__main__":
    main()
