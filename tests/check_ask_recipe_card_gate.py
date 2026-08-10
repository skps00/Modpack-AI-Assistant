"""shouldAttachAskRecipeCards: craft/acquire only — not every item Ask."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def should_attach(question: str | None) -> bool:
    if question is None or not str(question).strip():
        return False
    q = str(question).lower()
    craft = any(
        x in q
        for x in (
            "如何做",
            "怎麼做",
            "怎么做",
            "怎麼合成",
            "怎么合成",
            "如何合成",
            "如何製作",
            "如何制作",
            "配方",
            "合成",
            "製作",
            "制作",
            "how to make",
            "how to craft",
            "how do i craft",
            "how do i make",
            "craft ",
            " crafting",
            "recipe",
        )
    )
    acquire = any(
        x in q
        for x in (
            "如何取得",
            "怎麼取得",
            "怎么取得",
            "如何獲得",
            "如何获得",
            "怎麼獲得",
            "怎么获得",
            "怎麼來",
            "怎么来",
            "如何得到",
            "怎麼得到",
            "怎么得到",
            "how to get",
            "how do i get",
            "where to get",
            "where can i get",
            "obtain",
        )
    )
    return craft or acquire


def main() -> None:
    assert should_attach("如何做鑽石")
    assert should_attach("怎么合成这个")
    assert should_attach("how to get iron")
    assert not should_attach("魔力转化器")
    assert not should_attach("tetra 工作台放什麼")
    assert not should_attach("这个有什么用")
    assert not should_attach("check it's code")
    assert not should_attach("")
    assert not should_attach(None)

    for tree in ("forge/1.19.2", "neoforge/1.21.1"):
        src = (ROOT / tree / "src/main/java/com/skps9/packai/logic/PackIndex.java").read_text(
            encoding="utf-8"
        )
        start = src.index("public static boolean shouldAttachAskRecipeCards")
        chunk = src[start : start + 500]
        assert "isCraftOrientedQuestion" in chunk
        assert "isAcquireOrientedQuestion" in chunk
        assert "isCodeOrBehaviorQuestion" not in chunk.split("{", 1)[1].split("}", 1)[0]
        assert "return false" in chunk

    print("check_ask_recipe_card_gate: OK")


if __name__ == "__main__":
    main()
