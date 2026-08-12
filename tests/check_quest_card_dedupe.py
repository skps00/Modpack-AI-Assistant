#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Quest JEI card reserve + chat-link dedupe when card already shows quest title."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    for tree in (
        "forge/1.19.2/src/main/java/com/skps9/packai",
        "neoforge/1.21.1/src/main/java/com/skps9/packai",
    ):
        cp = read(f"{tree}/logic/CraftPriority.java")
        assert "isQuestCategory(String categoryTitle, String categoryUid)" in cp
        assert "ftbquests" in cp

        cards = read(f"{tree}/client/jei/JeiRecipeCards.java")
        assert "pickWithQuestReserve" in cards
        assert "questSigs" in cards
        assert "applyQuestRecipeMeta" in cards

        qg = read(f"{tree}/logic/QuestGuide.java")
        assert "questTitlesCoveredByCards" in qg
        assert "scrubCoveredRelatedQuestLines" in qg
        assert "hitMatchingCardTitle" in qg
        assert "！'" in qg or "'！'" in qg or "case '！'" in qg

        ask = read(f"{tree}/logic/AskEngine.java")
        assert "recipeCardCategoryTitlesFromJei" in ask
        assert "titleCoveredByCardCategories" in ask

        svc = read(f"{tree}/client/service/AskService.java")
        assert "dedupeQuestChatWhenCardShows" in svc

        ui = read(f"{tree}/client/gui/AiAssistantScreen.java")
        assert "questTitlesCoveredByCards" in ui
        assert "hitMatchingCardTitle" in ui
        assert "questOpenAction" in ui
        assert "InlinePiece.ofLink(cat, open)" in ui

        rc = read(f"{tree}/logic/RecipeCard.java")
        assert "questOpenId" in rc
        assert "hasQuestOpen" in rc

    print("check_quest_card_dedupe: OK")


if __name__ == "__main__":
    main()
