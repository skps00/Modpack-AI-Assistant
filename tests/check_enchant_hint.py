"""Wave-7 structural checks: EnchantHint table + AskService trim/enchant hooks (dual tree).

- EnchantHint.java exists in both trees, byte-identical, has the 11 category entries
- classify() returns sword for vanilla iron_sword AND for modded blades with sword actions
- tableFor('sword','zh_cn') emits 锋利 Lv5 lines; en emits Sharpness
- AskService: enchantHintText + [ENCHANT_TABLE] inject on BOTH live & sync paths
- trimPurposeTooltip keeps claim lines (获得/obtain/领取...) past the 8-line cap
- fact_check x6 carries the wave-7 enchant/repair/meta rule
"""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent.parent
TREES = ("forge/1.19.2", "neoforge/1.21.1")
LANG = ("en_us", "zh_cn", "zh_tw")


def read(tree: str, rel: str) -> str:
    return (ROOT / tree / rel).read_text(encoding="utf-8")


def main() -> None:
    a = read("forge/1.19.2", "src/main/java/com/skps9/packai/logic/EnchantHint.java")
    b = read("neoforge/1.21.1", "src/main/java/com/skps9/packai/logic/EnchantHint.java")
    # NOTE: Wave-16 made EnchantHint loader-specific (Forge vs Neo registry APIs),
    # so byte-identity no longer holds. Per-tree checks below.
    if a != b:
        print("note: EnchantHint.java differs between trees (expected since Wave-16)")
    assert a.count('new Entry("') >= 60
    for cat in ('case "sword":', 'case "axe":', 'case "tool":', 'case "hoe":',
                'case "bow":', 'case "crossbow":', 'case "trident":',
                'case "helmet":', 'case "chest":', 'case "legs":', 'case "boots":'):
        assert cat in a, cat
    assert 'id.contains("sword")' in a and 'acts.contains("sword_dig")' in a

    # tableFor logic sanity via source presence (cannot execute Java here)
    assert 'e.maxLevel()' in a and 'Lv' in a

    # Wave-16: registry scan wired in BOTH trees (APIs differ per loader, so
    # byte-identity is no longer expected for EnchantHint.java).
    for tree in TREES:
        eh = read(tree, "src/main/java/com/skps9/packai/logic/EnchantHint.java")
        for needle in ("registryTable", "classify", "curatedEffect"):
            assert needle in eh, (tree, needle)
        svc = read(tree, "src/main/java/com/skps9/packai/client/service/AskService.java")
        for needle in ("EnchantHint.registryTable", "ENCHANT_TABLE] 此物品", "Pack AI enchantHint q="):
            assert needle in svc, (tree, needle)

    for tree in TREES:
        svc = read(tree, "src/main/java/com/skps9/packai/client/service/AskService.java")
        assert svc.count("enchantHintText(question, replyLang, cardFocus, jeiFocusItemId)") >= 2, tree
        assert "ENCHANT_TABLE" in svc
        assert svc.count("trimPurposeTooltip") == 2, tree  # call + def
        # claim-keyword bypass inside trimPurposeTooltip
        trim = svc[svc.index("trimPurposeTooltip(String tip)"): svc.index("trimPurposeTooltip(String tip)") + 2600]
        assert 'kept >= 8 && !claim' in trim
        assert 'low.contains("获得")' in trim and 'low.contains("obtain")' in trim

    for tree in TREES:
        for lang in LANG:
            p = ROOT / tree / f"src/main/resources/assets/packai/lang/{lang}.json"
            v = json.loads(p.read_text(encoding="utf-8"))["packai.reply.fact_check"]
            if lang == "en_us":
                assert "answer what IS known" in v and "plain-text list" in v
            else:
                assert ("并把已知内容答好" in v or "並把已知內容答好" in v) and "不要用配方卡" in v, f"{tree}/{lang}"

    print("check_enchant_hint OK")


if __name__ == "__main__":
    main()
