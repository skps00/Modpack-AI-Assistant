"""Assistant strip focus: ignore live JEI hover; pin / pending NBT / draft-id / lastAsk."""

from __future__ import annotations


def strip_focus(
    pin: str | None,
    draft_id: str | None,
    last_ask: str | None,
    hover: str | None,
    pending: list[str] | None = None,
) -> str | None:
    """Priority while AiAssistantScreen open — hover never wins.

    Bare draft ``mod:id`` must not crush pending/lastAsk for the same registry id
    (Tetra scroll NBT). Draft wins only when id differs (user retarget) or no rich focus.
    """
    if pin:
        return pin
    pending = pending or []
    rich: str | None = None
    if pending:
        if last_ask:
            lid = last_ask.lower()
            for p in pending:
                if p and p.lower() == lid:
                    rich = last_ask
                    break
        if rich is None:
            rich = pending[0]
    else:
        rich = last_ask
    if draft_id:
        if rich is None:
            return draft_id
        if draft_id.lower() == rich.lower():
            return rich
        return draft_id
    _ = hover
    return rich


def clear_chat(pin: str | None, last_ask: str | None) -> tuple[None, None]:
    """Clear chat drops pin + lastAskFocus."""
    return None, None


def on_close(pin: str | None, last_ask: str | None) -> tuple[None, str | None]:
    """Screen close clears pin only; lastAsk dies with screen instance."""
    return None, last_ask


def main() -> None:
    # Regression: hover 黑暗祭壇 must not override cursed_ingot last-ask / draft.
    assert strip_focus(None, None, "cursed_ingot", "dark_altar") == "cursed_ingot"
    assert strip_focus(None, "cursed_ingot", None, "dark_altar") == "cursed_ingot"
    assert strip_focus("pinned", "cursed_ingot", "old", "dark_altar") == "pinned"
    assert strip_focus(None, None, None, "dark_altar") is None

    # High: bare draft id same as pending → keep pending (NBT focus), not bare stable.
    scroll = "tetra:scroll_rolled"
    assert strip_focus(None, scroll, scroll, None, [scroll]) == scroll
    assert strip_focus(None, scroll, None, None, [scroll]) == scroll
    # Different draft id → intentional retarget.
    assert strip_focus(None, "minecraft:dirt", scroll, None, [scroll]) == "minecraft:dirt"

    assert clear_chat("p", "a") == (None, None)
    assert on_close("p", "a") == (None, "a")

    # askBlocking must accept stripFocus (mirror askAsync) in both trees.
    from pathlib import Path

    root = Path(__file__).resolve().parents[1]
    for rel in (
        "forge/1.19.2/src/main/java/com/skps9/packai/client/service/AskService.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/client/service/AskService.java",
    ):
        src = (root / rel).read_text(encoding="utf-8")
        assert "ItemStack stripFocus" in src
        assert "resolveAskTarget(mc, question, stripFocus)" in src
        assert "resolveAskTarget(mc, question, ItemStack.EMPTY)" not in src.split("askBlocking")[1]

    print("check_strip_focus_stable: OK")


if __name__ == "__main__":
    main()
