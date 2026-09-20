#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""D-batch: unified model picker writes setCloudModel + setOllamaModel; hiddenInUi ollama row.

D2: pin effectiveModelTagKey (single tag source) + valueBox boxX = r.x + labelW + 4.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PICKER = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/ModelPickerScreen.java"
REG = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/settings/SettingsRegistry.java"
SCREEN = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/settings/SettingsScreenV2.java"
CATALOG = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic/ModelCatalog.java"
CONFIG = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/config/PackAiConfig.java"
LANG_DIR = ROOT / "forge/1.19.2/src/main/resources/assets/packai/lang"

TAG_KEYS = (
    "packai.settings.model_tag.offline",
    "packai.settings.model_tag.local",
    "packai.settings.model_tag.cloud",
    "packai.settings.model_tag.cloud_no_key",
)


def main() -> int:
    picker = PICKER.read_text(encoding="utf-8")
    reg = REG.read_text(encoding="utf-8")
    screen = SCREEN.read_text(encoding="utf-8")
    catalog = CATALOG.read_text(encoding="utf-8")
    config = CONFIG.read_text(encoding="utf-8")
    errs: list[str] = []

    if "PackAiConfig.setCloudModel(" not in picker:
        errs.append("ModelPickerScreen missing PackAiConfig.setCloudModel(")
    if "PackAiConfig.setOllamaModel(" not in picker:
        errs.append("ModelPickerScreen missing PackAiConfig.setOllamaModel(")
    if "setUiModel(" in picker:
        errs.append("ModelPickerScreen must not call setUiModel")

    if "eHidden(" not in reg or "llm.ollamaModel" not in reg:
        errs.append("SettingsRegistry must keep llm.ollamaModel via eHidden")
    if "hiddenInUi" not in reg:
        errs.append("SettingsRegistry missing hiddenInUi field")

    if "e.hiddenInUi" not in screen:
        errs.append("SettingsScreenV2 must skip hiddenInUi in filter")
    if 'MODEL_PICKER_PATHS = Set.of("llm.model")' not in screen.replace(" ", ""):
        # tolerate whitespace
        if not re.search(r'MODEL_PICKER_PATHS\s*=\s*Set\.of\(\s*"llm\.model"\s*\)', screen):
            errs.append("MODEL_PICKER_PATHS must be only llm.model")
    if "ollamaModel" not in screen:
        errs.append("SettingsScreenV2 must still mention ollamaModel (setters gate)")

    if "pendingDone" not in catalog:
        errs.append("ModelCatalog missing pendingDone (CAS defer)")
    if "refreshAsync(true" not in picker and "refreshAsync(true," not in picker:
        errs.append("ModelPickerScreen must force refreshAsync(true, …)")

    if "enum Target" not in picker and "enum Target {" not in picker:
        errs.append("ModelPickerScreen missing Target enum")
    if "buildRows" not in picker:
        errs.append("ModelPickerScreen missing buildRows")

    # --- D2 §5A.5: single-source model tag ---
    if "effectiveModelTagKey" not in config:
        errs.append("PackAiConfig missing effectiveModelTagKey()")
    if "packai.settings.model_tag.offline" not in config:
        errs.append("effectiveModelTagKey must return offline lang key")
    if "packai.settings.model_tag.cloud_no_key" not in config:
        errs.append("effectiveModelTagKey must return cloud_no_key lang key")
    if "LlmClient.resolveApiKey()" not in config.split("effectiveModelTagKey", 1)[-1][:800]:
        errs.append("effectiveModelTagKey must check resolveApiKey() (empty → cloud_no_key)")
    if "effectiveModelTagKey()" not in screen:
        errs.append("SettingsScreenV2.valueSummary must use effectiveModelTagKey()")
    if "effectiveModelTagKey()" not in picker:
        errs.append("ModelPickerScreen footer must use effectiveModelTagKey()")
    for lang_name in ("en_us.json", "zh_cn.json", "zh_tw.json"):
        lang_path = LANG_DIR / lang_name
        data = json.loads(lang_path.read_text(encoding="utf-8"))
        for key in TAG_KEYS:
            if key not in data or not str(data[key]).strip():
                errs.append(f"{lang_name} missing/empty {key}")

    # --- D2 §5B.1 / §2: valueBox after label (boxX = r.x + labelW + 4) ---
    if not re.search(r"labelCap\s*=\s*.*0\.55", screen):
        errs.append("SettingsScreenV2 missing labelW cap 0.55*r.w")
    if not re.search(
        r"labelW\s*=\s*Math\.min\(\s*this\.font\.width\(label\)\s*,\s*labelCap\s*\)",
        screen,
    ):
        errs.append("SettingsScreenV2 missing labelW = min(font.width(label), labelCap)")
    if not re.search(r"boxX\s*=\s*r\.x\s*\+\s*labelW\s*\+\s*4", screen):
        errs.append("SettingsScreenV2 valueBox must use boxX = r.x + labelW + 4")
    if re.search(r"boxX\s*=\s*r\.x\s*\+\s*r\.w\s*/\s*2", screen):
        errs.append("SettingsScreenV2 must not use boxX = r.x + r.w/2")
    if re.search(r"boxX\s*=\s*r\.x\s*;", screen) or re.search(
        r"boxX\s*=\s*r\.x\s*$", screen, re.M
    ):
        errs.append("SettingsScreenV2 must not use boxX = r.x (covers label)")

    if errs:
        for e in errs:
            print(f"FAIL: {e}")
        return 1
    print("check_settings_model_picker OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
