"""Scroll materials: inline {{item}} inject; no FLOW materialStrip attach."""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

SOURCES = re.compile(r"(?m)(【來源】|【来源】|\[Sources\])")
SCROLL_MATERIALS_HEADER = "[SCROLL_MATERIALS]"
NONE_MATERIALS_LINE = "none (no material required)"


def format_item_marker(item_id: str, count: int = 1) -> str:
    clean = (item_id or "").strip().lower()
    n = max(1, count)
    if n <= 1:
        return "{{item:" + clean + "}}"
    return "{{item:" + clean + "×" + str(n) + "}}"


def inline_materials_line(purpose: str, title: str, none_label: str) -> str:
    if SCROLL_MATERIALS_HEADER not in (purpose or ""):
        return ""
    head = (title.strip() + "：") if title and title.strip() else ""
    body = purpose.split(SCROLL_MATERIALS_HEADER, 1)[1]
    low_lines = [ln.strip().lower() for ln in body.splitlines() if ln.strip()]
    if any(t == NONE_MATERIALS_LINE or t == "none" or t.startswith("none (") for t in low_lines):
        return head + (none_label.strip() if none_label else NONE_MATERIALS_LINE)
    specs: list[tuple[str, int]] = []
    for ln in body.splitlines():
        t = ln.strip()
        if not t.lower().startswith("install_items"):
            continue
        rhs = t.split(":", 1)[1].strip() if ":" in t else ""
        for part in re.split(r"[,;]", rhs):
            part = part.strip()
            if not part or part.startswith("…") or part.startswith("..."):
                continue
            m = re.match(r"([a-z0-9_]+:[a-z0-9_./-]+)\s*[×xX*]?\s*(\d+)?", part, re.I)
            if not m:
                continue
            specs.append((m.group(1).lower(), int(m.group(2) or "1")))
        break
    if not specs:
        return ""
    return head + " ".join(format_item_marker(i, c) for i, c in specs)


def inject_inline_materials(answer: str, purpose: str, title: str, none_label: str) -> str:
    block = inline_materials_line(purpose, title, none_label)
    if not block:
        return answer or ""
    raw = answer or ""
    if "{{item:" in raw or block in raw:
        return raw
    m = SOURCES.search(raw)
    if m:
        before = raw[: m.start()].rstrip()
        after = raw[m.start() :]
        if not before:
            return block + "\n\n" + after
        return before + "\n\n" + block + "\n\n" + after
    split = raw.find("\n\n")
    if split < 0:
        split = raw.find("\n")
    if split < 0:
        return block if not raw.strip() else raw + "\n\n" + block
    before = raw[:split].rstrip()
    after = raw[split:].lstrip()
    if not before:
        return block + (("\n\n" + after) if after else "")
    if not after:
        return before + "\n\n" + block
    return before + "\n\n" + block + "\n\n" + after


def main() -> None:
    for tree in ("forge/1.19.2", "neoforge/1.21.1"):
        ask = (ROOT / tree / "src/main/java/com/skps9/packai/client/service/AskService.java").read_text(
            encoding="utf-8"
        )
        assert "withScrollMaterialInline" in ask
        assert "injectInlineMaterials" in ask
        text = (ROOT / tree / "src/main/java/com/skps9/packai/logic/TetraSchematicText.java").read_text(
            encoding="utf-8"
        )
        assert "formatItemMarker" in text
        assert "inlineMaterialsLine" in text
        assert "injectInlineMaterials" in text
        embed = (ROOT / tree / "src/main/java/com/skps9/packai/logic/RecipeEmbed.java").read_text(
            encoding="utf-8"
        )
        assert "itemCount" in embed
        assert "itemCountFromMatch" in embed
        ui = (ROOT / tree / "src/main/java/com/skps9/packai/client/gui/AiAssistantScreen.java").read_text(
            encoding="utf-8"
        )
        assert "InlinePiece" in ui
        assert "flushInlineParts" in ui
        assert "hasSpans" in ui
        assert "isScrollMaterialStrip" in ui
        assert "materialStrip" in ui
        lang = (ROOT / tree / "src/main/resources/assets/packai/lang/zh_tw.json").read_text(
            encoding="utf-8"
        )
        assert "packai.screen.scroll_materials_none" in lang
        assert "無需材料" in lang

    purpose = (
        "[SCROLL_MATERIALS]\n"
        "install_items (pick one / 任選其一): minecraft:amethyst_shard×64, minecraft:iron_ingot"
    )
    out = inject_inline_materials(
        "解鎖能量瓶。\n\n【來源】本包 PURPOSE",
        purpose,
        "工作台材料（任選其一）",
        "無需材料",
    )
    assert "{{item:minecraft:amethyst_shard×64}}" in out
    assert "{{item:minecraft:iron_ingot}}" in out
    assert out.index("{{item:") < out.index("【來源】")
    assert "工作台材料" in out

    none_out = inject_inline_materials(
        "升級說明。",
        "[SCROLL_MATERIALS]\nnone (no material required)",
        "工作台材料（任選其一）",
        "無需材料",
    )
    assert "無需材料" in none_out
    assert "{{item:" not in none_out

    print("check_scroll_material_card: OK")


if __name__ == "__main__":
    main()
