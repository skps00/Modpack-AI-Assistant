#!/usr/bin/env python3
"""Mirror AiAssistantScreen blankRowsForNewlines / spacing constants."""

CAPTION_TO_CARD_GAP = 4
CARD_BODY_TAIL = 4
CARD_OVERFLOW_PAD = 6
NUMBERED_STEP_PAD = 2
OUTSIDE_DRAW_PAD = 14  # JeiLayoutDraw — chat must NOT reserve full pad


def blank_rows_for_newlines(consecutive_newlines: int) -> int:
    """Soft \\n → 0 empty rows; paragraph \\n\\n+ → 1 empty row."""
    return 1 if consecutive_newlines >= 2 else 0


def test_blank_rows():
    assert blank_rows_for_newlines(0) == 0
    assert blank_rows_for_newlines(1) == 0
    assert blank_rows_for_newlines(2) == 1
    assert blank_rows_for_newlines(5) == 1


def test_constants_sane():
    assert CAPTION_TO_CARD_GAP == 4
    assert CARD_BODY_TAIL == 4
    assert CARD_OVERFLOW_PAD < OUTSIDE_DRAW_PAD
    assert NUMBERED_STEP_PAD == 2


def test_numbered_paragraph_gap_not_double():
    # Mirror appendTextAtoms: each \\n → one break atom; \\n\\n → two breaks → 1 blank row.
    text = "1. craft oak\n\n2. use axe"
    raw_lines = text.split("\n")
    pending = 0
    blanks = 0
    content = []
    for i, raw in enumerate(raw_lines):
        if i > 0:
            pending += 1
        if raw == "":
            continue
        blanks += blank_rows_for_newlines(pending)
        pending = 0
        content.append(raw)
    blanks += blank_rows_for_newlines(pending)
    assert content == ["1. craft oak", "2. use axe"]
    assert blanks == 1

    # Single \\n between steps → soft wrap, no blank row.
    text2 = "1. craft oak\n2. use axe"
    raw2 = text2.split("\n")
    pending = 0
    blanks = 0
    for i, raw in enumerate(raw2):
        if i > 0:
            pending += 1
        if raw == "":
            continue
        blanks += blank_rows_for_newlines(pending)
        pending = 0
    assert blanks == 0


def test_user_held_icon_after_label():
    """User held-item line: ofText(label) then ofItem — not left ICON_COL before You:/你:."""
    from pathlib import Path

    root = Path(__file__).resolve().parents[1]
    for rel in (
        "forge/1.19.2/src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java",
        "neoforge/1.21.1/src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java",
    ):
        src = (root / rel).read_text(encoding="utf-8")
        start = src.index("if (msg.isUser() && msg.hasHeldItem())")
        end = src.index("} else if (msg.isUser())", start)
        block = src[start:end]
        assert "InlinePiece.ofText(label)" in block, rel
        assert "InlinePiece.ofItem(icon)" in block, rel
        assert block.index("InlinePiece.ofText(label)") < block.index("InlinePiece.ofItem(icon)"), rel
        assert "new ChatLine(part, color, first ? icon" not in block, rel


if __name__ == "__main__":
    test_blank_rows()
    test_constants_sane()
    test_numbered_paragraph_gap_not_double()
    test_user_held_icon_after_label()
    print("check_ask_chat_spacing: OK")
