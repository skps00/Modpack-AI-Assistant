"""Structural checks: repair_lookup AskTool + AnvilRepairHint (dual tree).

- RepairLookupAskTool: name repair_lookup, return \"\" empty convention
- AnvilRepairHint: repairMaterials + isValidRepairItem + getRepairIngredient
- AskToolLoop CAPABLE_TOOLS + QUERY_TOOLS
- AskEngine register(new RepairLookupAskTool())
- LlmClient: toolSchemaDescription / toolSchemaRequired optional / toolMissNote teaching
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
        desc_fn = llm[llm.index("toolSchemaDescription"): llm.index("toolSchemaRequired")]
        assert "repair_lookup" in desc_fn, (tree, "toolSchemaDescription repair_lookup")
        assert "best-effort" in desc_fn, (tree, "description best-effort")

        req_start = llm.index("toolSchemaRequired")
        req_end = llm.index("static JsonArray nativeToolsSchema", req_start)
        req_fn = llm[req_start:req_end]
        assert "repair_lookup" in req_fn, (tree, "toolSchemaRequired repair_lookup")
        opt_idx = req_fn.index("repair_lookup")
        opt_snip = req_fn[max(0, opt_idx - 80): opt_idx + 120]
        assert "enchant_lookup" in opt_snip or "// item optional" in req_fn, (tree, "optional branch")
        assert 'req.add("item")' not in opt_snip, (tree, "repair_lookup must not require item")

        miss_fn = llm[llm.index("toolMissNote"): llm.index("toolSchemaDescription")]
        assert "repair_lookup" in miss_fn, (tree, "toolMissNote repair_lookup")
        assert "do not claim" in miss_fn, (tree, "toolMissNote teaching")

    print("check_repair_lookup OK")


if __name__ == "__main__":
    main()
