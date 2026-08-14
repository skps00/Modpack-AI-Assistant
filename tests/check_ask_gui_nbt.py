#!/usr/bin/env python3
"""Ask GUI: keep focus NBT on icons; 零件 strip in 怎么来/obtain cluster; 来源 last; skip waiting."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TREES = (
    "forge/1.19.2/src/main/java/com/skps9/packai",
    "neoforge/1.21.1/src/main/java/com/skps9/packai",
)


def part_icon_ids(parts: list[dict]) -> list[str]:
    """GUI must skip blank itemId — never invent an item."""
    out: list[str] = []
    for p in parts:
        item = (p or {}).get("itemId") or ""
        if item.strip():
            out.append(item.strip())
    return out


def main() -> None:
    assert part_icon_ids(
        [
            {"itemId": "minecraft:copper_ingot"},
            {"itemId": ""},
            {"itemId": "golden_age:thunder_gem1"},
        ]
    ) == ["minecraft:copper_ingot", "golden_age:thunder_gem1"]
    assert part_icon_ids([{"itemId": ""}, {"slot": "sword/hilt"}]) == []

    for tree in TREES:
        resolver = (ROOT / tree / "logic/ItemResolver.java").read_text(encoding="utf-8")
        assert "preferFocusNbt" in resolver
        scan = (ROOT / tree / "logic/ModularToolScan.java").read_text(encoding="utf-8")
        assert "partItemStacks" in scan
        screen = (ROOT / tree / "client/gui/AiAssistantScreen.java").read_text(encoding="utf-8")
        assert "preferFocusNbt" in screen
        assert "partItemStacks" in screen
        assert "toolPartsStrip" in screen
        assert "packai.screen.tool_parts" in screen
        strip = screen.split("private void renderInputHeldStrip", 1)[1].split(
            "private void renderAccuracyNote", 1)[0]
        assert "partItemStacks" not in strip
        user_held = screen.split("if (msg.isUser() && msg.hasHeldItem())", 1)[1]
        user_held = user_held.split("} else if (msg.isUser())", 1)[0]
        assert "toolPartsStrip" not in user_held
        assert "partItemStacks" not in user_held
        asst = screen.split("private void appendAssistantBody", 1)[1]
        asst = asst.split("private void flushInlineParts", 1)[0]
        assert "toolPartsStrip" in asst
        assert "packai.status.waiting" in asst
        assert "splitTrailingSources" in asst
        assert "insertObtainClusterAt" in asst
        assert "ChatLine.recipe" in asst
        assert "appendToolPartIcons" not in asst
        parts_fn = screen.split("private RecipeCard toolPartsStrip", 1)[1]
        parts_fn = parts_fn.split("private ItemStack heldIconOf", 1)[0]
        assert "materialStrip" in parts_fn
        assert "packai.screen.tool_parts" in parts_fn
        assert "iconRow" not in parts_fn
        embed = (ROOT / tree / "logic/RecipeEmbed.java").read_text(encoding="utf-8")
        assert "splitTrailingSources" in embed
        assert "insertObtainClusterAt" in embed
        assert "HOW_TO_GET_HEAD" in embed
        src_idx = embed.find("public static void splitTrailingSources")
        obtain_idx = embed.find("public static int insertObtainClusterAt")
        assert src_idx > 0 and obtain_idx > src_idx
        asst_src = asst.find("splitTrailingSources")
        asst_obtain = asst.find("insertObtainClusterAt")
        asst_recipe = asst.find("ChatLine.recipe")
        assert 0 <= asst_src < asst_obtain < asst_recipe
        assert "packai.screen.accuracy_note" in screen
        assert "renderAccuracyNote" in screen
        assert "stackForTemplateId" in screen

    for lang in (
        "forge/1.19.2/src/main/resources/assets/packai/lang",
        "neoforge/1.21.1/src/main/resources/assets/packai/lang",
    ):
        en = (ROOT / lang / "en_us.json").read_text(encoding="utf-8")
        zh = (ROOT / lang / "zh_tw.json").read_text(encoding="utf-8")
        cn = (ROOT / lang / "zh_cn.json").read_text(encoding="utf-8")
        assert "packai.screen.accuracy_note" in en
        assert "AI replies may be inaccurate" in en
        assert "不一定百分百準確" in zh
        assert "不一定百分百准确" in cn
        assert "packai.screen.tool_parts" in en
        assert "Parts:" in en
        assert "零件：" in zh
        assert "零件：" in cn

    print("check_ask_gui_nbt OK")


if __name__ == "__main__":
    main()
