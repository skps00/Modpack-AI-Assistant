"""Assistant strip focus: ignore live JEI hover; pin / draft-id / lastAsk win."""

from __future__ import annotations


def strip_focus(
    pin: str | None,
    draft_id: str | None,
    last_ask: str | None,
    hover: str | None,
) -> str | None:
    """Priority while AiAssistantScreen open — hover never wins."""
    if pin:
        return pin
    if draft_id:
        return draft_id
    if last_ask:
        return last_ask
    # live JEI ingredient hover intentionally ignored
    _ = hover
    return None


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

    assert clear_chat("p", "a") == (None, None)
    assert on_close("p", "a") == (None, "a")
    print("check_strip_focus_stable: OK")


if __name__ == "__main__":
    main()
