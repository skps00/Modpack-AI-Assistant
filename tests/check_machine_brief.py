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

        jei = read(f"{tree}/client/jei/JeiLookup.java")
        assert "isUsedAsCatalyst" in jei and "machineBrief" in jei

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

    # Lang keys both trees
    for lang_root in (
        "forge/1.19.2/src/main/resources/assets/packai/lang",
        "neoforge/1.21.1/src/main/resources/assets/packai/lang",
    ):
        en = read(f"{lang_root}/en_us.json")
        zh = read(f"{lang_root}/zh_tw.json")
        assert "packai.reply.section.machine" in en
        assert "packai.reply.machine_auto_suggest" in en
        assert "hopper" in en.lower() or "Hopper" in en
        assert "never places" in en
        assert "packai.reply.section.machine" in zh
        assert "漏斗" in zh
        assert "不會" in zh or "不会" in zh

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

    print("check_machine_brief OK")


if __name__ == "__main__":
    main()
