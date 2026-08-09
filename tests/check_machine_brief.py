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
        assert "replyMentionsAutomation" in marks
        assert "stripTrailingAutoSuggest" in marks
        assert "【機器】" in marks and "[Machine]" in marks

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
        assert "MACHINE_BRIEF_MAX_CATS" in jei
        assert "MACHINE_BRIEF_MAX_EXAMPLES" in jei
        assert "shortIoLine" in jei
        assert "collectMachineBriefExamples" in jei
        assert "includeHidden" in jei
        assert "lastCatalystMatchPath" in jei or "path=" in pk
        assert "typeLookup" in jei
        # Vanilla furnace: category focus enough; do not require recipe limitFocus(CATALYST) count
        assert "catalystFocusCategories" in jei
        assert "workstation category skipped" in jei or "Pack mods (custommachinery" in jei
        assert "sameItem" in jei

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
        assert "machineBriefCats" in rl and "machineBriefExamples" in rl
        assert "bundleLang" in rl and "isSimplifiedChinese" in rl
        assert '"zh_cn"' in rl or "zh_cn" in rl

        pk = read(f"{tree}/client/knowledge/PackKnowledge.java")
        assert "Pack AI machine brief catalyst=" in pk
        assert "path=" in pk
        assert "machineAutoSuggest" in pk

        idx = read(f"{tree}/logic/PackIndex.java")
        assert "isMachineQuestion" in idx

        gui = read(f"{tree}/client/gui/AiAssistantScreen.java")
        assert "isSectionHeader" in gui
        assert "displaySectionHeader" in gui
        assert "SUGGEST_COLOR" in gui

    # Lang keys both trees — soft auto line (no hardcoded hopper faces)
    for lang_root in (
        "forge/1.19.2/src/main/resources/assets/packai/lang",
        "neoforge/1.21.1/src/main/resources/assets/packai/lang",
    ):
        en = read(f"{lang_root}/en_us.json")
        zh = read(f"{lang_root}/zh_tw.json")
        zh_cn = read(f"{lang_root}/zh_cn.json")
        assert "packai.reply.section.machine" in en
        assert "packai.reply.machine_auto_suggest" in en
        assert "packai.reply.machine_brief_cats" in en
        assert "packai.reply.machine_brief_examples" in en
        assert "[Machine]" in en
        assert "not guaranteed" in en.lower() or "only if" in en.lower()
        assert "never places" in en
        assert "top/side" not in en.lower()
        assert "hopper out (below)" not in en.lower()
        assert "JEI" in en or "jei" in en.lower()
        assert "packai.reply.section.machine" in zh
        assert "【機器】" in zh
        assert "## 機器" not in zh  # readable bracket header, not markdown
        assert "不一定" in zh
        assert "漏斗" in zh or "管道" in zh
        assert "上方／側面" not in zh
        assert "不會" in zh or "不会" in zh
        assert "JEI" in zh
        assert "packai.reply.section.machine" in zh_cn
        assert "【机器】" in zh_cn
        assert "不一定" in zh_cn
        assert "自动化" in zh_cn or "漏斗" in zh_cn

    # Marker round-trip logic (mirror Java)
    mark = "[[packai.machine]]\n"
    payload = "jei get text\n" + mark + "[Machine]\nbrief\nsuggest"
    assert mark in payload
    i = payload.index(mark)
    machine = payload[i + len(mark) :].strip()
    get_part = payload[:i].strip()
    assert get_part == "jei get text"
    assert machine.startswith("[Machine]")
    assert "suggest" in machine

    # Post-LLM ensure: section survives when LLM body has no Machine header
    def reply_mentions_automation(body: str) -> bool:
        b = (body or "").lower()
        return any(
            k in b
            for k in (
                "hopper",
                "漏斗",
                "管道",
                "傳送帶",
                "传送带",
                "automation",
                "自動化",
                "自动化",
                "belt",
                "pipe",
            )
        )

    def strip_tip(section: str) -> str:
        lines = []
        for line in section.split("\n"):
            t = line.strip()
            if t.startswith("自動化") or t.startswith("自动化") or t.startswith("Automation"):
                continue
            lines.append(line)
        return "\n".join(lines).strip()

    def ensure_visible(body: str, section: str) -> str:
        if not section.strip():
            return body or ""
        section = section.strip()
        if not body or not body.strip():
            return section
        if (
            section in body
            or "[Machine]" in body
            or "【機器】" in body
            or "【机器】" in body
            or "## Machine" in body
            or "## 機器" in body
            or "## 机器" in body
        ):
            return body
        if reply_mentions_automation(body):
            section = strip_tip(section)
            if not section:
                return body
        for hdr in ("【來源】", "【来源】", "[Sources]"):
            if hdr in body:
                at = body.index(hdr)
                return body[:at].rstrip() + "\n\n" + section + "\n\n" + body[at:]
        return body.rstrip() + "\n\n" + section

    llm_body = "## 怎麼來\ncraft\n\n## 怎麼用\nhopper tip paraphrased\n\n【來源】JEI"
    fixed = ensure_visible(llm_body, "【機器】\nJEI：冶煉\n例：鐵礦 → 鐵錠\n自動化提示：不一定…")
    assert "【機器】" in fixed
    assert "JEI：冶煉" in fixed
    # tip stripped because body already said hopper
    assert "自動化提示" not in fixed
    assert fixed.index("【機器】") < fixed.index("【來源】")
    assert ensure_visible(fixed, "【機器】\nJEI：冶煉") == fixed  # idempotent

    # Soft auto tip alone must NOT block header inject
    tip_only = (
        "## 怎麼用\n"
        "自動化提示：不一定能自動進料／出料。僅當本機 JEI／模組說明支援物品 I/O 時，才可考慮漏斗／管道／傳送帶（哪一面另查）。"
        "Pack AI 不會幫你擺方塊或拉線。\n\n【來源】JEI"
    )
    with_hdr = ensure_visible(
        tip_only,
        "【機器】\nJEI：冶煉\n自動化提示：不一定能自動進料／出料。僅當本機 JEI／模組說明支援物品 I/O 時，才可考慮漏斗／管道／傳送帶（哪一面另查）。"
        "Pack AI 不會幫你擺方塊或拉線。",
    )
    assert "【機器】" in with_hdr
    assert "JEI：冶煉" in with_hdr
    # tip already in 怎麼用 → stripped from Machine block
    assert with_hdr.count("自動化提示") == 1

    # No automation in body → keep tip
    plain = "## 怎麼用\nright-click to open\n\n【來源】JEI"
    with_tip = ensure_visible(plain, "【機器】\nJEI：冶煉\n自動化提示：不一定…")
    assert "自動化提示" in with_tip

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

    # Edge: syringe/quest blocked by BlockItem + isNonMachineCategory (code presence)
    for tree in (
        "forge/1.19.2/src/main/java/com/skps9/packai",
        "neoforge/1.21.1/src/main/java/com/skps9/packai",
    ):
        pk = read(f"{tree}/client/knowledge/PackKnowledge.java")
        assert "Handheld JEI tab icons" in pk or "syringe" in pk.lower() or "isPlaceableBlockItem" in pk
        jei = read(f"{tree}/client/jei/JeiLookup.java")
        assert "DNA Analyzer" in jei or "Analyzer" in jei
        assert "syringe" in jei.lower() or "Handheld tools" in jei

    print("check_machine_brief OK")


if __name__ == "__main__":
    main()
