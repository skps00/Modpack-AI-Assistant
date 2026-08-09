#!/usr/bin/env python3
"""Static checks for thin P5 Machine brief (PackKnowledge + RecipeGetMarks + Ask wiring)."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    for tree in (
        "forge/1.19.2/src/main/java/com/skps9/packai",
        "neoforge/1.21.1/src/main/java/com/skps9/packai",
    ):
        marks = read(f"{tree}/logic/RecipeGetMarks.java")
        assert "MACHINE_MARK" in marks
        assert "extractMachine" in marks and "stripMachine" in marks

        pk = read(f"{tree}/client/knowledge/PackKnowledge.java")
        assert "machineBriefSectionOrEmpty" in pk
        assert "JeiLookup.isUsedAsCatalyst" in pk
        assert "isPlaceableBlockItem" in pk

        jei = read(f"{tree}/client/jei/JeiLookup.java")
        assert "isUsedAsCatalyst" in jei and "machineBrief" in jei
        assert "isPlaceableBlockItem" in jei
        assert "BlockItem" in jei
        assert "createRecipeCatalystLookup" in jei or "recipeTypeCatalysts" in jei
        assert "workstationCategories" in jei
        assert "categoryIconItem" in jei
        assert "DrawableIngredient" in jei
        assert "isNonMachineCategory" in jei
        # Fallback when CATALYST focus empty (DNA Analyzer icon-only) — BlockItem only
        assert "isPlaceableBlockItem(stack)" in jei
        assert "null, stack, RecipeIngredientRole.CATALYST" in jei or "null, stack," in jei
        # Vanilla furnace: category focus enough; do not require recipe limitFocus(CATALYST) count
        assert "catalystFocusCategories" in jei
        assert "matchRole != RecipeIngredientRole.CATALYST" in jei or "matchRole == RecipeIngredientRole.CATALYST" in jei

        spam = read(f"{tree}/client/jei/JeiUniversalSpam.java")
        assert "isNonMachineCategory" in spam
        assert "quest" in spam and "任務" in spam
        assert "heracles" in spam
        assert "ponder" in spam
        assert "information" in spam

        ask = read(f"{tree}/logic/AskEngine.java")
        assert "extractMachine" in ask
        assert "stripMachine" in ask
        assert "machineLines" in ask
        assert "isMachineQuestion" in ask

        svc = read(f"{tree}/client/service/AskService.java")
        assert "machineBriefSectionOrEmpty" in svc
        assert "MACHINE_MARK" in svc

        rl = read(f"{tree}/logic/ReplyLang.java")
        assert "sectionMachine" in rl and "machineAutoSuggest" in rl

        idx = read(f"{tree}/logic/PackIndex.java")
        assert "isMachineQuestion" in idx

    # Lang keys both trees — soft auto line (no hardcoded hopper faces)
    for lang_root in (
        "forge/1.19.2/src/main/resources/assets/packai/lang",
        "neoforge/1.21.1/src/main/resources/assets/packai/lang",
    ):
        en = read(f"{lang_root}/en_us.json")
        zh = read(f"{lang_root}/zh_tw.json")
        assert "packai.reply.section.machine" in en
        assert "packai.reply.machine_auto_suggest" in en
        assert "never places" in en
        assert "top/side" not in en.lower()
        assert "hopper out (below)" not in en.lower()
        assert "JEI" in en or "jei" in en.lower()
        assert "packai.reply.section.machine" in zh
        assert "漏斗" in zh or "管道" in zh
        assert "上方／側面" not in zh
        assert "不會" in zh or "不会" in zh
        assert "JEI" in zh

    # Marker round-trip logic (mirror Java)
    mark = "[[packai.machine]]\n"
    payload = "jei get text\n" + mark + "## Machine\nbrief\nsuggest"
    assert mark in payload
    i = payload.index(mark)
    machine = payload[i + len(mark) :].strip()
    get_part = payload[:i].strip()
    assert get_part == "jei get text"
    assert machine.startswith("## Machine")
    assert "suggest" in machine

    # Post-LLM ensure: section survives when LLM body has no Machine header
    def ensure_visible(body: str, section: str) -> str:
        if not section.strip():
            return body or ""
        section = section.strip()
        if not body or not body.strip():
            return section
        if section in body or "## Machine" in body or "## 機器" in body or "## 机器" in body:
            return body
        for hdr in ("【來源】", "【来源】", "[Sources]"):
            if hdr in body:
                at = body.index(hdr)
                return body[:at].rstrip() + "\n\n" + section + "\n\n" + body[at:]
        return body.rstrip() + "\n\n" + section

    llm_body = "## 怎麼來\ncraft\n\n## 怎麼用\nhopper tip paraphrased\n\n【來源】JEI"
    fixed = ensure_visible(llm_body, "## 機器\nI/O\n自動化：請以 JEI 為準")
    assert "## 機器" in fixed
    assert "自動化" in fixed
    assert fixed.index("## 機器") < fixed.index("【來源】")
    assert ensure_visible(fixed, "## 機器\nI/O\n自動化：請以 JEI 為準") == fixed  # idempotent
    # Soft auto tip alone must NOT block header inject
    tip_only = "## 怎麼用\n自動化：部分機器可用漏斗／管道／傳送帶，但側面與是否接受物品 I/O 請以本機 JEI／模組說明為準；Pack AI 不會幫你擺方塊或拉線。\n\n【來源】JEI"
    with_hdr = ensure_visible(tip_only, "## 機器\nI/O\n自動化：部分機器可用漏斗／管道／傳送帶，但側面與是否接受物品 I/O 請以本機 JEI／模組說明為準；Pack AI 不會幫你擺方塊或拉線。")
    assert "## 機器" in with_hdr

    for tree in (
        "forge/1.19.2/src/main/java/com/skps9/packai",
        "neoforge/1.21.1/src/main/java/com/skps9/packai",
    ):
        marks = read(f"{tree}/logic/RecipeGetMarks.java")
        assert "ensureVisibleInReply" in marks
        ask = read(f"{tree}/logic/AskEngine.java")
        assert "ensureVisibleInReply" in ask
        svc = read(f"{tree}/client/service/AskService.java")
        # Machine not gated solely inside attachCards block
        assert "Machine brief is independent" in svc or "shouldQueryJei()" in svc

    # Mirror non-machine keyword deny (quests must never qualify)
    def non_machine(s: str) -> bool:
        s = (s or "").lower()
        if "quest" in s or "任務" in s or "heracles" in s:
            return True
        if "ftbquests" in s or "ftb_quest" in s or ("ftb" in s and "quest" in s):
            return True
        return "information" in s or "info_category" in s or "ponder" in s

    assert non_machine("Quests")
    assert non_machine("ftbquests:quests")
    assert non_machine("任務")
    assert non_machine("Create Ponder")
    assert not non_machine("minecraft:furnace")
    assert not non_machine("Smelting")

    print("check_machine_brief OK")


if __name__ == "__main__":
    main()
