"""Maintenance/anvil recipe-card tier structural checks (A+B v1a + repair/upgrade split).

Verifies the maintenance-optional-card feature across the dual tree:
- PackIndex.isMaintenanceOrientedQuestion + maintenanceIntent keyword gate
- NOT wired into shouldAttachAskRecipeCards (v1a decision — repair asks w/o markers are text-only)
- RecipeCard FocusRole.MAINTENANCE/UPGRADE + isMaintenance/isUpgrade/isTrailingOptional
- AskCardFallback collectors/cardRole exclude trailing optional (never in needed set)
- RecipeCardsMode dropUnreferencedMaintenance both gates use isTrailingOptional
- JeiRecipeCards forItemParts/collectMaintenance/MAX_MAINTENANCE
- AskService maintIntent threading + appendRequirements skip + cardsOut maint/upg log
- hideUpgradeRecipes fully removed (config/settings/lang)
- lang x6 recipe_cards_ai_marker carries role=maintenance (+ role=upgrade covered by sibling check)
"""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent.parent
TREES = ("forge/1.19.2", "neoforge/1.21.1")
LANG = ("en_us", "zh_cn", "zh_tw")


def read(tree: str, rel: str) -> str:
    return (ROOT / tree / rel).read_text(encoding="utf-8")


def java_method_body(src: str, sig: str) -> str:
    """Brace-walk a method body from its SIGNATURE regex (never from a call site)."""
    m = re.search(sig, src)
    assert m, f"signature not found: {sig[:70]}"
    depth = 0
    i = m.end() - 1
    while i < len(src):
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
            if depth == 0:
                break
        i += 1
    return src[m.end() - 1: i]


def main() -> None:
    for tree in TREES:
        pack = read(tree, "src/main/java/com/skps9/packai/logic/PackIndex.java")
        assert "isMaintenanceOrientedQuestion(String question)" in pack
        assert "maintenanceIntent(String question)" in pack
        # multi-char keyword gate present in zh + en (via maintenanceIntent)
        for kw in ("修理", "修复", "耐久", "附魔", "升级", "repair", "enchant", "durability"):
            assert f'q.contains("{kw}")' in pack, f"{tree}: missing keyword {kw}"
        # no bare 修 token gate (FP guard)
        assert 'q.contains("修")' not in pack, f"{tree}: bare 修 keyword present"
        # v1a: maintenance NOT added to shouldAttachAskRecipeCards (pollution guard —
        # offline/keywords attach-all + RecipeEmbed auto-dump would orphan normal cards
        # into repair answers; repair asks w/o markers are intentionally text-only)
        sa_body = java_method_body(
            pack, r"public static boolean shouldAttachAskRecipeCards\(String question\)\s*\{")
        assert "isMaintenanceOrientedQuestion" not in sa_body

        rc = read(tree, "src/main/java/com/skps9/packai/logic/RecipeCard.java")
        assert "MAINTENANCE" in rc and "isMaintenance()" in rc
        assert "UPGRADE" in rc and "isUpgrade()" in rc and "isTrailingOptional()" in rc
        role = rc[rc.index("public String promptRole()"): rc.index("public String captionLangKey()")]
        assert "return \"maintenance\";" in role
        assert "return \"upgrade\";" in role

        fb = read(tree, "src/main/java/com/skps9/packai/logic/AskCardFallback.java")
        out_col = java_method_body(
            fb, r"private static List<Integer> collectOutputQuestIndices\(List<RecipeCard> cards\)\s*\{")
        in_col = java_method_body(
            fb, r"private static List<Integer> collectInputIndices\(List<RecipeCard> cards\)\s*\{")
        assert "!c.isTrailingOptional()" in out_col and "!c.isTrailingOptional()" in in_col
        cr = java_method_body(fb, r"private static int cardRole\(List<RecipeCard> cards, int idx\)\s*\{")
        assert "!c.isTrailingOptional()" in cr

        rcm = read(tree, "src/main/java/com/skps9/packai/logic/RecipeCardsMode.java")
        assert "dropUnreferencedMaintenance" in rcm and "keepEnd" in rcm
        drop = java_method_body(
            rcm, r"private static List<RecipeCard> dropUnreferencedMaintenance\(")
        assert drop.count("isTrailingOptional()") >= 2, f"{tree}: dropUnreferenced needs both gates"

        jrc = read(tree, "src/main/java/com/skps9/packai/client/jei/JeiRecipeCards.java")
        assert "forItemParts" in jrc and "ItemParts" in jrc
        assert "collectMaintenance" in jrc and "MAX_MAINTENANCE" in jrc
        assert "FocusRole.MAINTENANCE" in jrc
        assert "FocusRole.UPGRADE" in jrc
        assert "hideUpgradeRecipes" not in jrc

        jl = read(tree, "src/main/java/com/skps9/packai/client/jei/JeiLookup.java")
        assert "MaintenanceIntent" in jl
        assert "includeSelfRecipe" in jl
        assert "VANILLA_ANVIL_UID" in jl or "minecraft:anvil" in jl
        assert "hideUpgradeRecipes" not in jl

        svc = read(tree, "src/main/java/com/skps9/packai/client/service/AskService.java")
        assert svc.count("maintIntent") >= 2
        assert "summarize(cardFocus, jeiLevel, maintIntent)" in svc
        assert "filterRecipeCardsByIntent" in svc
        req = java_method_body(
            svc,
            r"static void appendRequirements\(StringBuilder jeiBlock, List<RecipeCard> recipeCards, String replyLang\)\s*\{")
        assert "c.isTrailingOptional()" in req
        assert "maint={} upg={}" in svc

        cfg = read(tree, "src/main/java/com/skps9/packai/config/PackAiConfig.java")
        assert "hideUpgradeRecipes" not in cfg and "HIDE_UPGRADE" not in cfg
        scr = read(tree, "src/main/java/com/skps9/packai/client/gui/PackAiSettingsScreen.java")
        assert "hide_upgrade_recipes" not in scr and "hideUpgradeRecipes" not in scr

    for tree in TREES:
        for lang in LANG:
            p = ROOT / tree / f"src/main/resources/assets/packai/lang/{lang}.json"
            d = json.loads(p.read_text(encoding="utf-8"))
            assert "packai.settings.hide_upgrade_recipes" not in d
            assert "packai.settings.tooltip.hide_upgrade_recipes" not in d
            rule = d.get("packai.reply.recipe_cards_ai_marker", "")
            assert "role=maintenance" in rule, f"{tree}/{lang}: maintenance rule missing"

    print("check_maintenance_intent OK")


if __name__ == "__main__":
    main()
