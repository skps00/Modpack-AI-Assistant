"""Next-step buttons: no hotbar dump; focus/pending only."""

from __future__ import annotations


def next_step_ok(pending: list[str], has_focus: bool) -> bool:
    """Block ask when nothing targeted and no picks."""
    return bool(pending) or has_focus


def next_step_selected(pending: list[str]) -> list[str]:
    """Send pending as today; empty pending = focus-only (no extras)."""
    return list(pending)


def main() -> None:
    assert next_step_ok([], False) is False
    assert next_step_ok([], True) is True
    assert next_step_ok(["mod:a"], False) is True
    assert next_step_selected([]) == []
    assert next_step_selected(["mod:a", "mod:b"]) == ["mod:a", "mod:b"]
    # Hotbar dump must not be implied: selected never invents slots.
    assert next_step_selected([]) == []
    print("check_next_step_focus: OK")


if __name__ == "__main__":
    main()
