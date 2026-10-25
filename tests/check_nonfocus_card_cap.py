#!/usr/bin/env python3
"""Non-focus recipe card cap — forge tool path + catalog extras."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    paths = (
        "forge/1.19.2/src/main/java/com/skps9/packai/logic/NonFocusCardCap.java",
        "forge/1.19.2/src/main/java/com/skps9/packai/logic/RenderRecipeCardsAskTool.java",
        "forge/1.19.2/src/main/java/com/skps9/packai/client/service/AskService.java",
        "forge/1.19.2/src/test/java/com/skps9/packai/logic/NonFocusCardCapCheck.java",
    )
    for rel in paths:
        src = (ROOT / rel).read_text(encoding="utf-8")
        if rel.endswith("NonFocusCardCap.java"):
            assert "MAX_OUTPUT = 2" in src
            assert "MAX_USES = 1" in src
            assert "MAX_TOTAL = 2" in src
            assert "Pack AI cards nonfocus item=" in src
            assert "capToolRole" in src
            assert "capItemCards" in src
        if rel.endswith("RenderRecipeCardsAskTool.java"):
            assert "NonFocusCardCap.capToolRole" in src
            assert "countEmissionsForItem" in src
        if rel.endswith("AskService.java"):
            assert "NonFocusCardCap.capItemCards" in src
        if rel.endswith("NonFocusCardCapCheck.java"):
            assert "negativeControlDisabled" in src
            assert "nonFocusUsesThreeToOne" in src

    tmp = (ROOT / "forge/1.19.2/tmp-check.gradle").read_text(encoding="utf-8")
    assert "runNonFocusCardCapCheck" in tmp
    assert "com.skps9.packai.logic.NonFocusCardCapCheck" in tmp

    print("check_nonfocus_card_cap: OK")


if __name__ == "__main__":
    main()
