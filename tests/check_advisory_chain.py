#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Static gate for S-A1 AdvisoryChain data layer (structure / grep only)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORGE = ROOT / "forge" / "1.19.2" / "src"
MAIN = FORGE / "main" / "java" / "com" / "skps9" / "packai" / "logic"
TEST = FORGE / "test" / "java" / "com" / "skps9" / "packai" / "logic"

CHAIN = MAIN / "AdvisoryChain.java"
FACTS = MAIN / "PackConfigFacts.java"
HARNESS = TEST / "AdvisoryChainCheck.java"

FORBIDDEN_NET = re.compile(
    r"\b(URLConnection|HttpClient|HttpURLConnection|Socket\b|URI\.create\s*\(\s*\"https?|"
    r"java\.net\.http|openConnection\s*\()",
    re.I,
)
# Writes outside designated cache: allow only comments / CACHE_REL / MECH_NUMBERS_REL reads.
WRITE_CALL = re.compile(
    r"\b(Files\.(write|writeString|writeBytes|newBufferedWriter|newOutputStream)|"
    r"FileWriter|FileOutputStream|PrintWriter)\s*\(",
)


def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")


def main() -> int:
    errors: list[str] = []

    def check(cond: bool, msg: str) -> None:
        if not cond:
            errors.append(msg)

    check(CHAIN.is_file(), f"missing {CHAIN}")
    check(FACTS.is_file(), f"missing {FACTS}")
    check(HARNESS.is_file(), f"missing {HARNESS}")
    if errors:
        print("FAIL")
        for e in errors:
            print(" -", e)
        return 1

    chain = read(CHAIN)
    facts = read(FACTS)
    harness = read(HARNESS)

    check("class AdvisoryChain" in chain, "AdvisoryChain class missing")
    check("static List<String> build(" in chain or "build(String itemId" in chain, "build() missing")
    check("PackContext" in chain, "PackContext missing")
    check("省料排序未支援" in chain, "must state 省料排序未支援")
    check("BAND_FISH" in chain and "BAND_QUEST_ONCE" in chain, "band constants missing")
    check("pickFastest" in chain, "pickFastest missing")
    check("tie-break" in chain or "tieBreakNote" in chain, "tie-break helper missing")
    check("AskEngine" not in chain, "AdvisoryChain must not reference AskEngine")
    check("AskToolLoop" not in chain, "AdvisoryChain must not reference AskToolLoop")

    check("class PackConfigFacts" in facts, "PackConfigFacts class missing")
    check("CACHE_REL" in facts, "CACHE_REL designation missing")
    check("mech-numbers.json" in facts, "A3 mech-numbers path missing")
    check("MAX_CONFIG_FILES" in facts, "A1 file cap missing")
    check("tier" in facts and "NumberFact" in facts, "NumberFact/tier missing")

    check("AdvisoryChainCheck" in harness, "harness class missing")
    check("22" in harness or "CASES" in harness, "22 cases missing")
    check("negative" in harness.lower() or "zzz" in harness, "negative control missing")
    check("ranking" in harness.lower() or "pickFastest" in harness, "ranking test missing")
    check("modsJarNameHash" in harness or "modsJar" in harness, "mod jar hash snapshot missing")

    for label, src in (("AdvisoryChain", chain), ("PackConfigFacts", facts), ("Harness", harness)):
        if FORBIDDEN_NET.search(src):
            errors.append(f"{label}: network API forbidden")
        for m in WRITE_CALL.finditer(src):
            # Allow only if clearly documented as designated cache — none expected in S-A1.
            errors.append(f"{label}: write call forbidden outside designated cache: {m.group(0)}")

    # Number emission must carry tier + source in build path.
    check('tier=" +' in chain or "tier=" in chain, "build must emit tier=")
    check('source=" +' in chain or "source=" in chain, "build must emit source=")
    check("未收錄" in chain, "must support 未收錄")

    # D1/3b: rate-label gate — rate signal AND not duration/interval → 每 tick;
    # else numeric → 其他數值（非每 tick）.
    check("RATE_MARKER" in chain, "D1 RATE_MARKER missing")
    check("DURATION_MARKER" in chain, "3b DURATION_MARKER missing")
    check("isRateFact" in chain, "D1 isRateFact gate missing")
    check("其他數值（非每 tick）" in chain, "D1 other-values section missing")
    check("DURATION_MARKER.matcher" in chain or "DURATION_MARKER" in chain, "3b duration exclude wiring")
    rate_bits = (
        "tick", "per_tick", "pertick", "fuel", "usage", "consumption", "consume",
        "generat", "emit", "output", "burn", "power", "mb/t", "fe/t", "rf/t", "j/t", "eu/t",
    )
    for bit in rate_bits:
        check(bit in chain, f"D1 rate allowlist missing token: {bit}")
    duration_bits = (
        "lifetime", "duration", "cooldown", "timeout", "interval", "delay", "despawn", "sleep",
    )
    for bit in duration_bits:
        check(bit in chain, f"3b duration exclude missing token: {bit}")
    # Static audit: any source line that hardcodes a 每 tick value emission without
    # going through isRateFact / RATE_MARKER is a fail. Allow 未收錄 rows + formatFactLine.
    for i, line in enumerate(chain.splitlines(), 1):
        if "每 tick" not in line:
            continue
        if "未收錄" in line or "RATE_MARKER" in line or "isRateFact" in line:
            continue
        if "formatFactLine" in line or 'formatFactLine("每 tick"' in line or 'label + ":' in line:
            continue
        if "每 tick 數字" in line or "每 tick:" in line and ("lines.add" in line or 'add("每 tick' in line):
            # bare lines.add("每 tick: " + n.key()...) without rate gate → fail
            if "n.key()" in line or "valueRaw" in line:
                if "isRateFact" not in line and "formatFactLine" not in line:
                    errors.append(
                        f"D1: line {i} emits 每 tick value without rate marker gate: {line.strip()}"
                    )

    # 3b: harness must lock known mislabels (duration/interval ≠ rate).
    check("rateLabelClassifier" in harness or "rate_label_classifier" in harness,
          "3b rateLabelClassifier missing in harness")
    for needle in (
        "attributes.ticks",
        "daysuntilpirateshipsdespawn",
        "updaterate",
        "defaultBurnRate",
        "energyPerTick",
        "fuelUsageRate",
    ):
        check(needle in harness, f"3b harness missing classifier fixture: {needle}")

    # D2 craft routes + D3 defaultconfigs note
    check("findCraftRecipeSources" in facts, "D2 findCraftRecipeSources missing")
    check("BAND_SCRIPT" in chain and "配方" in chain, "D2 craft route band/label missing")
    check("NOTE_DEFAULTCONFIG" in facts or "pack 預設" in facts, "D3 defaultconfigs note missing")
    check("defaultconfigs" in facts, "D3 defaultconfigs reader missing")

    # Engine wiring out of scope — ensure new classes not imported by AskEngine yet (optional soft).
    ask_engine = MAIN / "AskEngine.java"
    if ask_engine.is_file():
        ae = read(ask_engine)
        check("AdvisoryChain" not in ae, "AskEngine must not wire AdvisoryChain in S-A1")

    if errors:
        print("FAIL check_advisory_chain")
        for e in errors:
            print(" -", e)
        return 1
    print("PASS check_advisory_chain")
    return 0


if __name__ == "__main__":
    sys.exit(main())
