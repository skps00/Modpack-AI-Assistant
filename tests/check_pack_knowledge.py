#!/usr/bin/env python3
"""Static checks for PackKnowledge minimal (recipeBackend, marks, get+use sections)."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> None:
    forge_pk = read("forge/1.19.2/src/main/java/com/skps9/packai/client/knowledge/PackKnowledge.java")
    neo_pk = read("neoforge/1.21.1/src/main/java/com/skps9/packai/client/knowledge/PackKnowledge.java")
    assert "shouldQueryJei" in forge_pk and "shouldQueryJei" in neo_pk
    assert "net.minecraftforge.fml.ModList" in forge_pk
    assert "net.neoforged.fml.ModList" in neo_pk

    marks = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/RecipeGetMarks.java")
    assert "EMI_PREVIEW" in marks and "NO_RECIPE_UI" in marks
    assert "RecipeGetMarks" in read(
        "neoforge/1.21.1/src/main/java/com/skps9/packai/logic/RecipeGetMarks.java"
    )

    forge_cfg = read("forge/1.19.2/src/main/java/com/skps9/packai/config/PackAiConfig.java")
    neo_cfg = read("neoforge/1.21.1/src/main/java/com/skps9/packai/config/PackAiConfig.java")
    assert 'define("recipeBackend", "auto")' in forge_cfg
    assert "recipeBackend()" in forge_cfg and "recipeBackend()" in neo_cfg
    # emi pref: JEI still wins when loaded (do not force EMI_STUB-only)
    for pk in (forge_pk, neo_pk):
        idx = pk.index('if ("emi".equals(pref))')
        chunk = pk[idx : idx + 320]
        assert "if (jei)" in chunk
        assert chunk.index("if (jei)") < chunk.index("EMI_STUB")
        assert "return Backend.JEI" in chunk

    forge_ask = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/AskEngine.java")
    neo_ask = read("neoforge/1.21.1/src/main/java/com/skps9/packai/logic/AskEngine.java")
    assert "sectionHowToGet" in forge_ask and "sectionHowToUse" in forge_ask
    assert "sectionHowToGet" in neo_ask and "emiPreview" in neo_ask
    assert "machineBriefSectionOrEmpty" in forge_pk and "machineBriefSectionOrEmpty" in neo_pk
    assert "MACHINE_MARK" in marks

    rs = read("forge/1.19.2/src/main/java/com/skps9/packai/logic/ReplySources.java")
    assert "labelPurpose" in rs and "EMI" in rs

    en = read("forge/1.19.2/src/main/resources/assets/packai/lang/en_us.json")
    assert "How to get" in en and "emi_preview_gap" in en
    assert "Truth ladder" in en or "truth ladder" in en.lower() or "Truth ladder" in en

    toml = read("forge/1.19.2/src/main/resources/META-INF/mods.toml")
    assert 'modId="emi"' in toml
    neo_toml = read("neoforge/1.21.1/src/main/templates/META-INF/neoforge.mods.toml")
    assert 'modId="emi"' in neo_toml

    print("check_pack_knowledge OK")


if __name__ == "__main__":
    main()
