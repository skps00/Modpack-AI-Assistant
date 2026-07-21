"""Turn common recipe snippets into plain Chinese for players (no raw code as main answer)."""

from __future__ import annotations

import json
import re
from typing import Any


_ITEM_RE = re.compile(
    r"(?:Item\.of\()?['\"]([a-z0-9_.:/-]+(?:#[a-z0-9_./-]+)?)['\"]\)?",
    re.I,
)
_SHAPED_RE = re.compile(
    r"event\.shaped\(\s*(?P<out>[^,\n]+)\s*,\s*\[(?P<pattern>[\s\S]*?)\]\s*,\s*\{(?P<keys>[\s\S]*?)\}\s*\)",
    re.I,
)
_SHAPELESS_RE = re.compile(
    r"event\.shapeless\(\s*(?P<out>[^,\n]+)\s*,\s*\[(?P<ings>[\s\S]*?)\]\s*\)",
    re.I,
)
_REMOVE_RE = re.compile(
    r"event\.remove\(\s*\{([^}]*)\}\s*\)",
    re.I,
)


def plainify_snippets(snippets: list[str], sources: list[str] | None = None) -> str | None:
    """
    Try to build a player-facing answer from snippets.
    Returns None if nothing useful could be parsed.
    """
    texts: list[str] = []
    for i, snip in enumerate(snippets):
        body = snip
        if body.startswith("// file:"):
            body = body.split("\n", 1)[1] if "\n" in body else ""
        texts.append(body)

    parts: list[str] = []
    for body in texts:
        p = _plainify_one(body)
        if p:
            parts.append(p)

    if not parts:
        return None

    src = "、".join(sources or []) or "(未知檔案)"
    return "\n\n".join(parts) + f"\n\n【來源】{src}\n【注意】此為整合包本地設定，可能與通用 wiki 不同。"


def _plainify_one(text: str) -> str | None:
    t = text.strip()
    if not t:
        return None

    # Datapack / JSON recipe
    if t.lstrip().startswith("{"):
        try:
            data = json.loads(t)
            got = _plainify_json_recipe(data)
            if got:
                return got
        except json.JSONDecodeError:
            pass

    m = _SHAPED_RE.search(t)
    if m:
        out = _clean_item(m.group("out"))
        keys = _parse_key_map(m.group("keys"))
        pattern_lines = [
            ln.strip().strip("'\"")
            for ln in m.group("pattern").split(",")
            if ln.strip().strip("'\"")
        ]
        mats = _materials_from_pattern(pattern_lines, keys)
        return (
            f"【作法】用工作台（有序）合成 {out}\n"
            f"【材料】{_fmt_mats(mats)}\n"
            f"【步驟】1. 打開工作台 2. 依配方擺放材料 3. 取出 {out}"
        )

    m = _SHAPELESS_RE.search(t)
    if m:
        out = _clean_item(m.group("out"))
        ings = [_clean_item(x) for x in _ITEM_RE.findall(m.group("ings"))]
        mats: dict[str, int] = {}
        for ing in ings:
            mats[ing] = mats.get(ing, 0) + 1
        return (
            f"【作法】用工作台（無序）合成 {out}\n"
            f"【材料】{_fmt_mats(mats)}\n"
            f"【步驟】1. 打開工作台 2. 放入材料（順序不限）3. 取出 {out}"
        )

    m = _REMOVE_RE.search(t)
    if m:
        inner = m.group(1)
        return f"【作法】整合包已移除／封鎖某些配方（{inner.strip()}）\n【步驟】請改用任務書或其他模組配方取得物品"

    return None


def _plainify_json_recipe(data: dict[str, Any]) -> str | None:
    rtype = str(data.get("type") or "")
    result = data.get("result") or data.get("output")
    out = _json_item(result)
    if not out:
        return None

    if "shapeless" in rtype:
        ings = data.get("ingredients") or []
        mats: dict[str, int] = {}
        for ing in ings:
            name = _json_item(ing)
            if name:
                mats[name] = mats.get(name, 0) + 1
        return (
            f"【作法】用工作台（無序）合成 {out}\n"
            f"【材料】{_fmt_mats(mats)}\n"
            f"【步驟】1. 打開工作台 2. 放入材料 3. 取出 {out}"
        )

    if "shaped" in rtype or rtype.endswith(":crafting_shaped"):
        key = data.get("key") or {}
        pattern = data.get("pattern") or []
        keymap = {str(k): _json_item(v) for k, v in key.items()}
        mats = _materials_from_pattern([str(p) for p in pattern], keymap)
        return (
            f"【作法】用工作台（有序）合成 {out}\n"
            f"【材料】{_fmt_mats(mats)}\n"
            f"【步驟】1. 打開工作台 2. 依圖案擺放 3. 取出 {out}"
        )
    return None


def _parse_key_map(blob: str) -> dict[str, str]:
    out: dict[str, str] = {}
    for m in re.finditer(
        r"([A-Za-z0-9])\s*:\s*((?:Item\.of\()?['\"][^'\"]+['\"]\)?|#[a-z0-9_/:.-]+)",
        blob,
    ):
        out[m.group(1)] = _clean_item(m.group(2))
    return out


def _materials_from_pattern(pattern_lines: list[str], keys: dict[str, str]) -> dict[str, int]:
    mats: dict[str, int] = {}
    for line in pattern_lines:
        for ch in line:
            if ch in (" ", ".", "_"):
                continue
            name = keys.get(ch) or ch
            mats[name] = mats.get(name, 0) + 1
    return mats


def _clean_item(raw: str) -> str:
    s = raw.strip()
    m = _ITEM_RE.search(s)
    if m:
        return m.group(1)
    s = s.strip("'\"")
    if s.startswith("Item.of("):
        s = s[8:].rstrip(")")
        s = s.strip("'\"")
    return s


def _json_item(obj: Any) -> str | None:
    if obj is None:
        return None
    if isinstance(obj, str):
        return obj
    if isinstance(obj, dict):
        if "item" in obj:
            count = obj.get("count")
            base = str(obj["item"])
            return f"{base} x{count}" if count and count != 1 else base
        if "id" in obj:
            return str(obj["id"])
        if "tag" in obj:
            return "#" + str(obj["tag"]).lstrip("#")
    return None


def _fmt_mats(mats: dict[str, int]) -> str:
    if not mats:
        return "（未解析到材料）"
    return "、".join(f"{k} ×{v}" for k, v in mats.items())


def friendly_offline(sources: list[str], question: str) -> str:
    """No LLM and plainify failed: list files only, no code dump."""
    src = "\n".join(f"- {s}" for s in sources[:8]) if sources else "- （沒有找到相關檔案）"
    return (
        "找到與這個問題可能相關的整合包設定檔，但無法自動翻成白話說明。\n"
        "請設定 OPENAI_API_KEY（或本機 Ollama）以獲得完整說明，或打開下列檔案查看：\n"
        f"{src}\n"
        f"問題：{question}"
    )
