#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Plan A S2/S3/S4/S5: NUMBER bounds sync + no-tip row + named tips whitelist."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CFG = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/config/PackAiConfig.java"
REG = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/settings/SettingsRegistry.java"
SCREEN = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/settings/SettingsScreenV2.java"
COMPAT = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/WidgetCompat.java"
TRACE = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic/AskTrace.java"

# path → PackAiConfig define leaf name
PATH_TO_DEFINE = {
    "llm.dailyTokenLimit": "dailyTokenLimit",
    "token.maxJeiChars": "maxJeiChars",
    "token.historyTurns": "historyTurns",
    "token.maxFacts": "maxFacts",
    "llm.askMaxToolRounds": "askMaxToolRounds",
    "ui.recipeCardsPerItem": "recipeCardsPerItem",
    "ui.recipeCardsPerItemUse": "recipeCardsPerItemUse",
    "ui.knowledgeCacheMaxMb": "knowledgeCacheMaxMb",
    "llm.askTraceKeepFiles": "askTraceKeepFiles",
    "llm.traceKeepDays": "traceKeepDays",
    "ui.packIndexClipRadius": "packIndexClipRadius",
}

DEFINE_IN_RANGE_RE = re.compile(
    r'\.defineInRange\(\s*"([^"]+)"\s*,\s*[^,]+,\s*([^,]+),\s*([^)]+)\)',
    re.MULTILINE,
)

# NUMBER setter block: path … parseNumberInput(v, …, min, max)
ENTRY_NUMBER_RE = re.compile(
    r'e\(\s*"([^"]+)"\s*,\s*UiCategory\.\w+\s*,\s*"[^"]+"\s*,\s*"[^"]+"\s*,\s*'
    r"ControlType\.NUMBER\s*,\s*\"set\w+\"\s*,\s*"
    r".*?parseNumberInput\(\s*v\s*,\s*[^,]+,\s*([^,]+),\s*([^)]+)\)",
    re.DOTALL,
)

CONST_RE = {
    "AskTrace.KEEP_MIN": None,
    "AskTrace.KEEP_MAX": None,
    "AskTrace.KEEP_DAYS_MIN": None,
    "AskTrace.KEEP_DAYS_MAX": None,
}


def load_trace_consts(src: str) -> dict[str, int]:
    out: dict[str, int] = {}
    for name in ("KEEP_MIN", "KEEP_MAX", "KEEP_DAYS_MIN", "KEEP_DAYS_MAX"):
        m = re.search(rf"public static final int {name}\s*=\s*(\d+)\s*;", src)
        if not m:
            raise AssertionError(f"AskTrace missing {name}")
        out[f"AskTrace.{name}"] = int(m.group(1))
    return out


def parse_int_expr(expr: str, consts: dict[str, int]) -> int:
    e = expr.strip().rstrip(",")
    if e in consts:
        return consts[e]
    # Underscore digit separators only (100_000_000), not AskTrace.KEEP_* names.
    lit = e.replace("_", "")
    if re.fullmatch(r"-?\d+", lit):
        return int(lit)
    raise AssertionError(f"unresolved bound expr: {expr!r}")


def parse_config_ranges(cfg: str) -> dict[str, tuple[int, int]]:
    """Leaf define name → (min, max). Only resolves literal ints (AskTrace via caller)."""
    # We need AskTrace consts for keep files/days — pass in separately.
    return {}  # filled in main


def main() -> int:
    errs: list[str] = []
    cfg = CFG.read_text(encoding="utf-8")
    reg = REG.read_text(encoding="utf-8")
    screen = SCREEN.read_text(encoding="utf-8")
    compat = COMPAT.read_text(encoding="utf-8")
    trace = TRACE.read_text(encoding="utf-8")
    consts = load_trace_consts(trace)

    # --- PackAiConfig defineInRange map (leaf → min,max) ---
    cfg_bounds: dict[str, tuple[int, int]] = {}
    for m in DEFINE_IN_RANGE_RE.finditer(cfg):
        leaf, lo, hi = m.group(1), m.group(2).strip(), m.group(3).strip()
        try:
            cfg_bounds[leaf] = (parse_int_expr(lo, consts), parse_int_expr(hi, consts))
        except AssertionError:
            # non-literal (e.g. AskToolLoop.MAX_LLM_ROUNDS as default only) — try again
            # for askMaxToolRounds default is MAX_LLM_ROUNDS but min/max are 1,8 literals
            try:
                cfg_bounds[leaf] = (parse_int_expr(lo, consts), parse_int_expr(hi, consts))
            except AssertionError as e:
                # skip keys we don't care about if bounds unresolved
                if leaf in PATH_TO_DEFINE.values():
                    errs.append(f"PackAiConfig {leaf}: {e}")

    # Re-parse carefully: default arg may be non-literal
    DEFINE_FLEX = re.compile(
        r'\.defineInRange\(\s*"([^"]+)"\s*,\s*([^,]+),\s*([^,]+),\s*([^)]+)\)',
        re.MULTILINE,
    )
    cfg_bounds = {}
    for m in DEFINE_FLEX.finditer(cfg):
        leaf, _default, lo, hi = m.group(1), m.group(2), m.group(3).strip(), m.group(4).strip()
        if leaf not in PATH_TO_DEFINE.values():
            continue
        try:
            cfg_bounds[leaf] = (parse_int_expr(lo, consts), parse_int_expr(hi, consts))
        except AssertionError as e:
            errs.append(f"PackAiConfig {leaf}: {e}")

    # --- Registry NUMBER parseNumberInput bounds ---
    if "parseInt(" in reg.replace("Integer.parseInt(", ""):
        # allow Integer.parseInt inside parseNumberInput only
        stripped = re.sub(
            r"public static int parseNumberInput\(.*?^    \}",
            "",
            reg,
            count=1,
            flags=re.DOTALL | re.MULTILINE,
        )
        if re.search(r"\bparseInt\s*\(", stripped) or re.search(
            r"Integer\.parseInt\s*\(", stripped
        ):
            errs.append("SettingsRegistry NUMBER setters must not call parseInt/Integer.parseInt")

    found_paths: set[str] = set()
    for m in ENTRY_NUMBER_RE.finditer(reg):
        path, lo, hi = m.group(1), m.group(2).strip(), m.group(3).strip()
        found_paths.add(path)
        if path not in PATH_TO_DEFINE:
            errs.append(f"unexpected NUMBER path {path}")
            continue
        leaf = PATH_TO_DEFINE[path]
        if leaf not in cfg_bounds:
            errs.append(f"no PackAiConfig bounds for {leaf}")
            continue
        want_lo, want_hi = cfg_bounds[leaf]
        # Symbol consts only allowed for AskTrace keep*
        try:
            got_lo = parse_int_expr(lo, consts)
            got_hi = parse_int_expr(hi, consts)
        except AssertionError as e:
            errs.append(f"{path}: {e}")
            continue
        if (got_lo, got_hi) != (want_lo, want_hi):
            errs.append(
                f"{path}: registry ({got_lo},{got_hi}) != PackAiConfig ({want_lo},{want_hi})"
            )
        if leaf in ("askTraceKeepFiles", "traceKeepDays"):
            if "AskTrace." not in lo and "AskTrace." not in hi:
                # must use symbols for keep* per plan
                if leaf == "askTraceKeepFiles" and (
                    "AskTrace.KEEP_MIN" not in m.group(0) or "AskTrace.KEEP_MAX" not in m.group(0)
                ):
                    errs.append(f"{path}: must use AskTrace.KEEP_MIN/MAX symbols")
                if leaf == "traceKeepDays" and (
                    "AskTrace.KEEP_DAYS_MIN" not in m.group(0)
                    or "AskTrace.KEEP_DAYS_MAX" not in m.group(0)
                ):
                    errs.append(f"{path}: must use AskTrace.KEEP_DAYS_MIN/MAX symbols")
        else:
            if "AskTrace." in lo or "AskTrace." in hi:
                errs.append(f"{path}: non-keep* must use literal bounds, not AskTrace")

    missing = set(PATH_TO_DEFINE) - found_paths
    if missing:
        errs.append(f"missing NUMBER parseNumberInput for {sorted(missing)}")
    if len(found_paths) != 11:
        errs.append(f"expected 11 NUMBER entries with parseNumberInput, got {len(found_paths)}")

    if "parseNumberInput" not in reg:
        errs.append("parseNumberInput missing")

    # --- S3 tipLines null guard + editBoxNoTip ---
    if "tip == null" not in compat and "tip==null" not in compat:
        errs.append("WidgetCompat.tipLines missing null guard")
    if "editBoxNoTip" not in compat:
        errs.append("WidgetCompat.editBoxNoTip missing")
    if screen.count("editBoxNoTip") < 1:
        errs.append("SettingsScreenV2 must call editBoxNoTip")

    # --- S4: valueBox must not pass e.tooltipKey as tip ---
    if re.search(r"editBox\([^)]*e\.tooltipKey", screen, re.DOTALL):
        errs.append("valueBox must not pass e.tooltipKey tip")
    if "WidgetCompat.editBox(" in screen and "editBoxNoTip" in screen:
        # search box still uses editBox with tip — OK; value path uses noTip
        pass

    # --- S5: six named tips still present ---
    for needle in (
        "packai.settings.v2.tooltip.search",
        "packai.settings.tooltip.done",
        "packai.settings.v2.tooltip.reset_all",
        "packai.settings.v2.tooltip.reset_page",
        "packai.settings.tooltip.knowledge_test",
        "packai.settings.tooltip.knowledge_clear_cache",
    ):
        if needle not in screen:
            errs.append(f"missing named tip {needle}")

    # --- S6/S7 source pins ---
    if "mouseMoved" not in screen:
        errs.append("SettingsScreenV2 missing mouseMoved")
    if "updateHoverHighlight" not in screen:
        errs.append("SettingsScreenV2 missing updateHoverHighlight")
    if "hasControlDown" not in screen:
        errs.append("SettingsScreenV2 missing Ctrl+wheel (hasControlDown)")
    if "Pack AI settingsLayout" not in screen:
        errs.append("missing S9 settingsLayout diag log string")

    if errs:
        for e in errs:
            print(f"FAIL: {e}")
        return 1
    print("check_settings_number_bounds OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
