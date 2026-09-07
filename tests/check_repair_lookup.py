"""Structural checks: repair_lookup AskTool + AnvilRepairHint (dual tree).

- RepairLookupAskTool: name repair_lookup, return "" empty convention
- AnvilRepairHint: repairMaterials + isValidRepairItem + getRepairIngredient
- AskToolLoop CAPABLE_TOOLS + QUERY_TOOLS
- AskEngine register(new RepairLookupAskTool())
- RepairLookupAskTool: description, toolMissNote, argsSchemaJson required:[]
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TREES = ("forge/1.19.2", "neoforge/1.21.1")
REL_LOGIC = "src/main/java/com/skps9/packai/logic"


def read(tree: str, name: str) -> str:
    return (ROOT / tree / REL_LOGIC / name).read_text(encoding="utf-8")


def main() -> None:
    for tree in TREES:
        tool = read(tree, "RepairLookupAskTool.java")
        assert '"repair_lookup"' in tool, (tree, "repair_lookup name")
        assert 'return ""' in tool, (tree, 'return "" empty convention')
        assert "best-effort" in tool, (tree, "description best-effort")
        assert "toolMissNote" in tool, (tree, "toolMissNote")
        assert "do not claim" in tool, (tree, "toolMissNote teaching")
        assert "additionalProperties" in tool, (tree, "additionalProperties")
        # argsSchemaJson is a Java string literal → escaped quotes; normalize before matching
        assert '"required":[]' in tool.replace('\\"', '"'), (tree, "required empty")

        hint = read(tree, "AnvilRepairHint.java")
        assert "repairMaterials(" in hint, (tree, "repairMaterials")
        assert "isValidRepairItem" in hint, (tree, "isValidRepairItem")
        assert "getRepairIngredient" in hint, (tree, "getRepairIngredient")

        loop = read(tree, "AskToolLoop.java")
        assert '"repair_lookup"' in loop, (tree, "AskToolLoop mention")
        capable = loop[loop.index("CAPABLE_TOOLS"): loop.index("ALLOWLIST")]
        assert '"repair_lookup"' in capable, (tree, "CAPABLE_TOOLS")
        query = loop[loop.index("QUERY_TOOLS"): loop.index("QUERY_TOOLS") + 400]
        assert '"repair_lookup"' in query, (tree, "QUERY_TOOLS")

        engine = read(tree, "AskEngine.java")
        assert "register(new RepairLookupAskTool())" in engine, (tree, "AskEngine register")

        llm = read(tree, "LlmClient.java")
        assert "toolSchemaDescription" not in llm, (tree, "no toolSchemaDescription table")
        assert "AskToolLoop.byName" in llm, (tree, "byName delegate")

    print("check_repair_lookup OK")


if __name__ == "__main__":
    main()
