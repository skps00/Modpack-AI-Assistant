#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Batch A guard: every PackAiConfig setX saves; dead lang gone; five UI keys wired."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CFG = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/config/PackAiConfig.java"
UI = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/settings/SettingsScreenV2.java"
LANGS = [
    ROOT / "forge/1.19.2/src/main/resources/assets/packai/lang/en_us.json",
    ROOT / "forge/1.19.2/src/main/resources/assets/packai/lang/zh_cn.json",
    ROOT / "forge/1.19.2/src/main/resources/assets/packai/lang/zh_tw.json",
]

# Fail-closed: only empty unless a setter must not persist (file+line evidence required).
EXCLUDED: set[str] = set()

DEAD_LANG = (
    "packai.settings.hint",
    "packai.settings.save_all",
    # B3: orphaned by PackAiSettingsScreen / RecipeCategoryScreen deletion
    "packai.settings.tab.connection",
    "packai.settings.tab.ask",
    "packai.settings.tab.recipes",
    "packai.settings.tab.quests",
    "packai.settings.knowledge_enabled.on",
    "packai.settings.knowledge_enabled.off",
    "packai.settings.knowledge_remote.on",
    "packai.settings.knowledge_remote.off",
    "packai.settings.sidebar.left",
    "packai.settings.sidebar.right",
    "packai.settings.prefer_obtain.craft",
    "packai.settings.prefer_obtain.quest",
    "packai.settings.prefer_obtain.loot",
    "packai.settings.prefer_obtain.balanced",
    "packai.settings.web_search",
    "packai.settings.ingredient_nbt.auto",
    "packai.settings.ingredient_nbt.always",
    "packai.settings.ingredient_nbt.never",
    "packai.settings.show_hidden_quests.on",
    "packai.settings.show_hidden_quests.off",
    "packai.settings.attach_quests.on",
    "packai.settings.attach_quests.off",
    "packai.settings.guidebook_scope.same_mod",
    "packai.settings.guidebook_scope.any_mod",
    "packai.settings.guidebook_related.on",
    "packai.settings.guidebook_related.off",
    "packai.settings.quest_match_hotbar.on",
    "packai.settings.quest_match_hotbar.off",
    "packai.settings.scan_mod_jars.on",
    "packai.settings.scan_mod_jars.off",
    "packai.settings.unpack_stored_items.on",
    "packai.settings.unpack_stored_items.off",
    "packai.settings.log_full_prompt.on",
    "packai.settings.log_full_prompt.off",
    "packai.settings.ask_trace_jsonl.on",
    "packai.settings.ask_trace_jsonl.off",
    "packai.settings.ask_native_tools.auto",
    "packai.settings.ask_native_tools.force",
    "packai.settings.ask_native_tools.off",
    "packai.settings.tooltip.save_key",
    "packai.settings.tooltip.refresh_models",
    "packai.settings.tooltip.web_search",
    "packai.settings.tooltip.save_all",
    "packai.recipe_cats.search_hint",
    "packai.recipe_cats.reset",
    "packai.recipe_cats.hint",
    "packai.recipe_cats.tooltip.reset",
    "packai.recipe_cats.tooltip.done",
    "packai.settings.tooltip.tab.connection",
    "packai.settings.tooltip.tab.ask",
    "packai.settings.tooltip.tab.recipes",
    "packai.settings.tooltip.tab.quests",
    "packai.settings.tooltip.tab.knowledge",
    "packai.settings.recipe_backend.auto",
    "packai.settings.recipe_backend.jei",
    "packai.settings.recipe_backend.emi",
    "packai.settings.show_token_usage.on",
    "packai.settings.show_token_usage.off",
    "packai.settings.recipe_cards_mode.keywords",
    "packai.settings.recipe_cards_mode.always",
    "packai.settings.recipe_cards_mode.never",
    "packai.settings.modular_tool_single_item.on",
    "packai.settings.modular_tool_single_item.off",
)

UI_KEYS = (
    "recipeCardMirrorCategories",
    "ingredientNbtSkipPatterns",
    "ingredientNbtKeepPatterns",
    "ollamaBaseUrl",
    "ollamaModel",
)

LABEL_TOOLTIP = {
    "recipeCardMirrorCategories": (
        "packai.settings.recipe_card_mirror",
        "packai.settings.tooltip.recipe_card_mirror",
    ),
    "ingredientNbtSkipPatterns": (
        "packai.settings.ingredient_nbt_skip",
        "packai.settings.tooltip.ingredient_nbt_skip",
    ),
    "ingredientNbtKeepPatterns": (
        "packai.settings.ingredient_nbt_keep",
        "packai.settings.tooltip.ingredient_nbt_keep",
    ),
    "ollamaBaseUrl": (
        "packai.settings.ollama_base",
        "packai.settings.tooltip.ollama_base",
    ),
    "ollamaModel": (
        "packai.settings.ollama_model",
        "packai.settings.tooltip.ollama_model",
    ),
}

SETTER_RE = re.compile(r"public static void (set\w+)\s*\(")


def method_body(src: str, name: str) -> str:
    needle = f"public static void {name}("
    if needle not in src:
        raise AssertionError(f"missing setter: {name}")
    idx = src.index(needle)
    brace = src.find("{", idx)
    if brace < 0:
        raise AssertionError(f"{name}: no opening brace")
    depth = 0
    for i in range(brace, len(src)):
        c = src[i]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return src[brace : i + 1]
    raise AssertionError(f"{name}: braces not balanced")


def main() -> int:
    cfg = CFG.read_text(encoding="utf-8")
    ui = UI.read_text(encoding="utf-8")
    langs = [p.read_text(encoding="utf-8") for p in LANGS]

    setters = SETTER_RE.findall(cfg)
    assert setters, "no public static void setX in PackAiConfig"
    missing_save: list[str] = []
    for name in setters:
        if name in EXCLUDED:
            continue
        body = method_body(cfg, name)
        if "SPEC.save()" not in body:
            missing_save.append(name)
    if missing_save:
        print("FAIL: setters missing SPEC.save():", ", ".join(missing_save))
        return 1

    for key in DEAD_LANG:
        for i, text in enumerate(langs):
            if f'"{key}"' in text:
                print(f"FAIL: dead lang key still present: {key} in {LANGS[i].name}")
                return 1

    for key in UI_KEYS:
        if key not in ui:
            print(f"FAIL: UI missing config key reference: {key}")
            return 1
        label, tip = LABEL_TOOLTIP[key]
        for i, text in enumerate(langs):
            if f'"{label}"' not in text:
                print(f"FAIL: missing label {label} in {LANGS[i].name}")
                return 1
            if f'"{tip}"' not in text:
                print(f"FAIL: missing tooltip {tip} in {LANGS[i].name}")
                return 1

    print(
        f"OK: setters_with_save={len(setters) - len(EXCLUDED)} excluded={len(EXCLUDED)} "
        f"dead_lang_absent={len(DEAD_LANG)} ui_keys={len(UI_KEYS)}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
