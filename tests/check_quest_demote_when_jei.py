#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""JEI craft present → demote quest narrative (optional reward note, not primary get)."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    for tree in (
        "forge/1.19.2/src/main/java/com/skps9/packai/logic",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/logic",
    ):
        ask = read(f"{tree}/AskEngine.java")
        assert "demoteQuestNarrative(" in ask
        assert "hasNonQuestAcquirePath(" in ask
        assert "questOptionalRewardNote" in ask
        # When demoted, skip purposeQuests embedding of full quest body
        assert "if (!demoteQuestNarrative)" in ask
        assert "askEaseBand" in read(f"{tree}/CraftPriority.java")
        assert "hasNonQuestObtainPath(" in ask
        assert "hasNonQuestCraftObtain(" in ask
        assert "role=(?:input|output|quest)" in ask
        assert 'role=output' in ask
        # Quest-only RECIPE_CARDS are not craft obtain.
        assert "hasRecipeCardCatalog(" in ask
        assert "isQuestCategory(String categoryTitle, String categoryUid)" in read(
            f"{tree}/CraftPriority.java"
        )
        cp = read(f"{tree}/CraftPriority.java")
        assert '"任务"' in cp
        assert '"任务奖励"' in cp
        rc = read(f"{tree}/RecipeCard.java")
        assert 'return "quest"' in rc
        assert "packai.screen.quest_reward" in rc
        assert "canRepeat" in read(f"{tree}/QuestGuide.java")
        assert "QUEST_REPEAT_MARK" in read(f"{tree}/PackIndex.java")
        jei_cards = read(f"{tree.replace('/logic', '/client/jei')}/JeiRecipeCards.java")
        assert "askEaseBand" in jei_cards
        assert "pickWithQuestReserve" in jei_cards
        assert "isQuestCategory(catTitle, JeiCategoryCatalog.categoryUid(category))" in read(
            f"{tree.replace('/logic', '/client/jei')}/JeiLookup.java"
        )
        rl = read(f"{tree}/ReplyLang.java")
        assert "questOptionalRewardNote" in rl
        assert "packai.reply.quest_optional_reward" in rl
        assert "recipeCardCategoryTitlesFromJei" in ask
        assert "titleCoveredByCardCategories" in ask
        ask_svc = read(f"{tree.replace('/logic', '/client/service')}/AskService.java")
        assert "c.promptRole()" in ask_svc

    for tree in (
        "forge/1.19.2/src/main/resources/assets/packai/lang",
        "neoforge/1.21.1/src/main/resources/assets/packai/lang",
    ):
        for lang in ("en_us.json", "zh_tw.json", "zh_cn.json"):
            text = read(f"{tree}/{lang}")
            assert "packai.reply.quest_optional_reward" in text
            assert "packai.screen.quest_reward" in text
            assert "role=quest" in text

    import re

    catalog_re = re.compile(r"^\d+\s*\|\s*role=", re.M)
    output_re = re.compile(r"^\d+\s*\|\s*role=output\s*\|", re.M)

    def has_non_quest_obtain(has_recipe_get: bool, jei: str, acquire_nonquest: bool) -> bool:
        if acquire_nonquest:
            return True
        if not has_recipe_get:
            return False
        if catalog_re.search(jei or ""):
            return bool(output_re.search(jei or ""))
        return True

    quest_only = (
        "0 | role=quest | Template | saber → trinket\n"
        "1 | role=input | Altar | trinket"
    )
    assert not has_non_quest_obtain(True, quest_only, False)
    assert has_non_quest_obtain(True, "0 | role=output | Crafting | A → B", False)
    assert has_non_quest_obtain(True, quest_only, True)
    assert has_non_quest_obtain(True, "no catalog lines", False)
    assert not has_non_quest_obtain(False, quest_only, False)

    print("check_quest_demote_when_jei: OK")


if __name__ == "__main__":
    main()
