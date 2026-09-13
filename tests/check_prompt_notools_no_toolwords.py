#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""No-tools system-prompt lang keys must not mention tool names."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
# Keys that land in the no-tools system prompt (llmSystemLead + factCheck(code,false) +
# PackAuthorAgents.systemAddon + llmStyle(code,false)). Concatenate all — do
# not check a subset. fact_check_tools_note is tools-only (appended only when
# toolsOffered=true).
NOTOOLS_KEYS = (
    "packai.reply.llm_style_notools",
    "packai.reply.reply_pattern_notools",
    "packai.reply.ask_purpose_order.purpose_first_notools",
    "packai.reply.ask_purpose_order.ingredient_first",
    "packai.reply.fact_check",
    "packai.reply.guide_advisory",
    "packai.reply.sources_instruction",
    "packai.reply.llm_system_lead",
    "packai.reply.pack_author_agents_lead",
)
TOOLS_KEEP = (
    "packai.reply.llm_style",
    "packai.reply.reply_pattern",
    "packai.reply.recipe_cards_ai_marker",
)
TOOLS_ONLY = (
    "packai.reply.fact_check_tools_note",
)
TREES = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "resources" / "assets" / "packai" / "lang",
    ROOT / "neoforge" / "1.21.1" / "src" / "main" / "resources" / "assets" / "packai" / "lang",
)
LANGS = ("en_us.json", "zh_tw.json", "zh_cn.json")
FORBIDDEN = (
    "render_recipe_cards",
    "jei_lookup",
    "jei_info_use",
    "jei_info_acquire",
    "dump_level",
)


def main() -> None:
    print("notools keys:", ", ".join(NOTOOLS_KEYS))
    print("tools-keep keys:", ", ".join(TOOLS_KEEP))
    print("tools-only keys:", ", ".join(TOOLS_ONLY))
    for tree in TREES:
        for name in LANGS:
            path = tree / name
            data = json.loads(path.read_text(encoding="utf-8"))
            blob = []
            for key in NOTOOLS_KEYS:
                assert key in data, f"missing {key} in {path}"
                val = data[key]
                assert isinstance(val, str) and val.strip(), f"empty {key} in {path}"
                blob.append(val)
            joined = "\n".join(blob)
            for word in FORBIDDEN:
                assert word not in joined, (
                    f"{path} no-tools prompt keys still contain {word}"
                )
            for key in TOOLS_KEEP:
                assert key in data, f"missing tools-mode {key} in {path}"
                val = data[key]
                assert isinstance(val, str) and val.strip(), f"empty tools-mode {key} in {path}"
                assert "render_recipe_cards" in val, (
                    f"{path} tools-mode {key} lost render_recipe_cards"
                )
            for key in TOOLS_ONLY:
                assert key not in NOTOOLS_KEYS, f"{key} must not be in NOTOOLS_KEYS"
                assert key in data, f"missing tools-only {key} in {path}"
                val = data[key]
                assert isinstance(val, str) and val.strip(), f"empty tools-only {key} in {path}"
                for word in ("jei_lookup", "jei_info_use", "jei_info_acquire", "dump_level"):
                    assert word in val, f"{path} tools-only {key} missing {word}"
            print(f"OK {path.relative_to(ROOT)}")
    print("check_prompt_notools_no_toolwords OK")


if __name__ == "__main__":
    main()
