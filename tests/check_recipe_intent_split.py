"""Repair vs upgrade anvil-split structural checks (plan 2026-09-06).

Asserts PackIndex.maintenanceIntent keyword groups, RecipeCard UPGRADE helpers,
JeiRecipeCards catUid role split, JeiLookup catUid gate, AskService intent wiring.
"""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parent.parent
TREES = ("forge/1.19.2", "neoforge/1.21.1")
LANG = ("en_us", "zh_cn", "zh_tw")

REPAIR_KW = (
    "怎么修", "怎麼修", "如何修", "怎样修", "怎樣修",
    "修理", "修复", "修復", "維修", "维修", "耐久",
    "坏了", "壞了", "損壞", "损坏", "快坏", "快壞",
    "repair", "fix it", "mend", "durability", "damaged", "broken", "restore durability",
)
UPGRADE_KW = (
    "升级", "升級", "强化", "強化", "淬炼", "淬鍊", "重铸", "重鑄",
    "upgrade", "reforge",
)
BOTH_KW = ("保养", "保養", "磨刀", "打磨", "附魔", "enchant")
# Must NOT appear in repair group
REPAIR_FORBIDDEN = ("升级", "強化", "强化", "保养", "保養", "磨刀")
# Must NOT appear in upgrade group
UPGRADE_FORBIDDEN = ("修理", "修复", "repair")


def read(tree: str, rel: str) -> str:
    return (ROOT / tree / rel).read_text(encoding="utf-8")


def java_method_body(src: str, sig: str) -> str:
    m = re.search(sig, src)
    assert m, f"signature not found: {sig[:80]}"
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


def extract_bool_block(body: str, var: str) -> str:
    """Slice `boolean var = ...;` assignment from maintenanceIntent body."""
    m = re.search(rf"boolean {var}\s*=", body)
    assert m, f"boolean {var} not found"
    start = m.start()
    end = body.find(";", m.end())
    assert end > start
    return body[start:end]


def main() -> None:
    for tree in TREES:
        pack = read(tree, "src/main/java/com/skps9/packai/logic/PackIndex.java")
        assert "enum MaintenanceIntent" in pack
        assert "NONE" in pack and "REPAIR" in pack and "UPGRADE" in pack and "BOTH" in pack
        assert "maintenanceIntent(String question)" in pack
        mi = java_method_body(pack, r"public static MaintenanceIntent maintenanceIntent\(String question\)\s*\{")
        repair_blk = extract_bool_block(mi, "repair")
        upgrade_blk = extract_bool_block(mi, "upgrade")
        both_blk = extract_bool_block(mi, "both")
        for kw in REPAIR_KW:
            assert f'q.contains("{kw}")' in repair_blk, f"{tree}: repair missing {kw}"
        for kw in UPGRADE_KW:
            assert f'q.contains("{kw}")' in upgrade_blk, f"{tree}: upgrade missing {kw}"
        for kw in BOTH_KW:
            assert f'q.contains("{kw}")' in both_blk, f"{tree}: BOTH missing {kw}"
        for kw in REPAIR_FORBIDDEN:
            assert f'q.contains("{kw}")' not in repair_blk, f"{tree}: repair must not contain {kw}"
        for kw in UPGRADE_FORBIDDEN:
            assert f'q.contains("{kw}")' not in upgrade_blk, f"{tree}: upgrade must not contain {kw}"
        wrap = java_method_body(
            pack, r"public static boolean isMaintenanceOrientedQuestion\(String question\)\s*\{")
        assert "maintenanceIntent(question) != MaintenanceIntent.NONE" in wrap

        rc = read(tree, "src/main/java/com/skps9/packai/logic/RecipeCard.java")
        assert "UPGRADE" in rc
        assert "isUpgrade()" in rc and "isTrailingOptional()" in rc
        role = rc[rc.index("public String promptRole()"): rc.index("public String captionLangKey()")]
        assert 'return "upgrade";' in role

        jrc = read(tree, "src/main/java/com/skps9/packai/client/jei/JeiRecipeCards.java")
        assert "VANILLA_ANVIL_UID" in jrc
        assert "FocusRole.UPGRADE" in jrc
        assert "FocusRole.MAINTENANCE" in jrc

        cat = read(tree, "src/main/java/com/skps9/packai/client/jei/JeiCategoryCatalog.java")
        assert 'VANILLA_ANVIL_UID = "minecraft:anvil"' in cat

        jl = read(tree, "src/main/java/com/skps9/packai/client/jei/JeiLookup.java")
        assert "PackIndex.MaintenanceIntent" in jl
        gate = java_method_body(
            jl, r"private static boolean includeSelfRecipe\(PackIndex\.MaintenanceIntent intent")
        assert "VANILLA_ANVIL_UID" in gate
        assert "MaintenanceIntent.REPAIR" in gate
        assert "MaintenanceIntent.UPGRADE" in gate
        # REPAIR path must require vanilla anvil (non-anvil self-recipes excluded)
        assert "vanillaAnvil" in gate or "VANILLA_ANVIL_UID.equals" in gate

        svc = read(tree, "src/main/java/com/skps9/packai/client/service/AskService.java")
        assert "summarize(cardFocus, jeiLevel, maintIntent)" in svc
        assert "filterRecipeCardsByIntent" in svc
        assert "PackIndex.maintenanceIntent(question)" in svc
        filt = java_method_body(
            svc, r"static List<RecipeCard> filterRecipeCardsByIntent\(")
        assert "isMaintenance()" in filt and "isUpgrade()" in filt
        assert "MaintenanceIntent.NONE" in filt
        assert "purpose cards irrelevant for REPAIR/UPGRADE" in filt
        assert "maint={} upg={}" in svc

    for tree in TREES:
        for lang in LANG:
            p = ROOT / tree / f"src/main/resources/assets/packai/lang/{lang}.json"
            d = json.loads(p.read_text(encoding="utf-8"))
            rule = d.get("packai.reply.recipe_cards_ai_marker", "")
            assert "role=upgrade" in rule, f"{tree}/{lang}: upgrade rule missing"
            assert "role=maintenance" in rule, f"{tree}/{lang}: maintenance rule missing"

    print("check_recipe_intent_split OK")


if __name__ == "__main__":
    main()
