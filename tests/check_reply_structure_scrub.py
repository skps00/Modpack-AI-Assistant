#!/usr/bin/env python3
"""Mirror AskReplyScrub.stripDuplicateSectionHeaders — drop duplicate section title lines."""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCRUB_PATHS = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic" / "AskReplyScrub.java",
    ROOT
    / "neoforge"
    / "1.21.1"
    / "src"
    / "main"
    / "java"
    / "com"
    / "skps9"
    / "packai"
    / "logic"
    / "AskReplyScrub.java",
)
SERVICE_PATHS = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "client" / "service" / "AskService.java",
    ROOT
    / "neoforge"
    / "1.21.1"
    / "src"
    / "main"
    / "java"
    / "com"
    / "skps9"
    / "packai"
    / "client"
    / "service"
    / "AskService.java",
)
SOURCES_PATHS = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic" / "ReplySources.java",
    ROOT
    / "neoforge"
    / "1.21.1"
    / "src"
    / "main"
    / "java"
    / "com"
    / "skps9"
    / "packai"
    / "logic"
    / "ReplySources.java",
)

# Mirror AskReplyScrub.INTERNAL_SECTION_TOKENS (Java List.of order).
INTERNAL_SECTION_TOKENS = (
    "PURPOSE",
    "GUIDE",
    "VARIANT",
    "AS_INGREDIENT",
    "CONTAINED",
    "CONSUME_USE",
    "TOOL_BUILD",
    "TETRA_USE",
    "WORLDGEN",
)
EXTRA_BARE_SECTION_TOKENS = ("RECIPE_CARDS",)
SCROLL_SECTION_REGEX = r"SCROLL_[A-Z0-9_]+"
INTERNAL_ROLE_VALUES = ("output", "input", "quest", "uses", "upgrade", "maintenance")
LANG_TREES = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "resources" / "assets" / "packai" / "lang",
    ROOT / "neoforge" / "1.21.1" / "src" / "main" / "resources" / "assets" / "packai" / "lang",
)
_LABEL_CACHE: dict[str, dict[str, str]] = {}


def internal_section_alternation() -> str:
    parts = [SCROLL_SECTION_REGEX]
    toks = list(INTERNAL_SECTION_TOKENS) + list(EXTRA_BARE_SECTION_TOKENS)
    toks.sort(key=len, reverse=True)
    parts.extend(re.escape(t) for t in toks)
    return "|".join(parts)


# Mirror AskReplyScrub.scrubInternalFieldEcho / hasInternalSourceLeak
ROLE_EQ_TOKEN = re.compile(
    r"(?i)\brole\s*[=＝]\s*(?:[A-Za-z0-9_\-]+(?:(?:\s*[、／,|｜/]\s*|\s+)(?!role\b)[A-Za-z0-9_\-]+)*)?"
)
ROLE_EQ_START = re.compile(r"(?i)role\s*[=＝]")
ROLE_VALUE_SEP = re.compile(r"[、／,|｜/\s]+")
BARE_INTERNAL_SECTION = re.compile(
    r"(?<![A-Za-z])(?:" + internal_section_alternation() + r")(?![A-Za-z])\s*[:：]\s*"
)
BARE_INTERNAL_SECTION_COLON = re.compile(
    r"(?<![A-Za-z])(?:" + internal_section_alternation() + r")(?![A-Za-z])\s*[:：]"
)
TAG_SQUARE = re.compile(
    r"\[\s*(" + internal_section_alternation() + r")\s*\]",
    re.IGNORECASE,
)
TAG_CJK = re.compile(
    r"【\s*(" + internal_section_alternation() + r")\s*】",
    re.IGNORECASE,
)
TAG_FW_PAREN = re.compile(
    r"（\s*(" + internal_section_alternation() + r")\s*）",
    re.IGNORECASE,
)
TAG_PAREN = re.compile(
    r"\(\s*(" + internal_section_alternation() + r")\s*\)",
    re.IGNORECASE,
)
PROMPT_SECTION_TAG = re.compile(
    r"\[\s*(?:" + internal_section_alternation() + r")\s*\]",
    re.IGNORECASE,
)
TRANSLATE_COLON_TOKEN = re.compile(
    r"(?<![A-Za-z])(" + internal_section_alternation() + r")(?![A-Za-z])(\s*[:：])"
)
HEADER_RE = re.compile(r"(【來源】|【来源】|\[Sources\])")
# Java Pattern \\u3000; Python re has no \\uXXXX — splice the char.
_IDSP = "\u3000"
_EMPTY_WS = r" \t" + _IDSP
EMPTY_BRACKETS = re.compile(
    "[（(][" + _EMPTY_WS + "]*[）)]"
    "|\\[[" + _EMPTY_WS + "]*\\]"
    "|【[" + _EMPTY_WS + "]*】"
    "|\\{[" + _EMPTY_WS + "]*\\}"
)
DUP_SEPARATORS = re.compile("([、，,／|;；·:：\\-])(?:[" + _EMPTY_WS + "]*\\1)+")
LEADING_ORPHAN_SEP = re.compile(r"^[ \t]*[、，,／|;；·]+", re.M)
TRAILING_ORPHAN_SEP = re.compile(r"[、，,／|;；·]+[ \t]*$", re.M)
HALF_ORPHAN_OPEN = re.compile("[（(][" + _EMPTY_WS + "]*$", re.M)
HALF_ORPHAN_CLOSE = re.compile("^[" + _EMPTY_WS + "]*[）)]", re.M)
SPACE_BEFORE_CLOSE = re.compile(r"[ \t]+([、，,）)])")
SPACE_AFTER_OPEN = re.compile(r"([（(])[ \t]+")
MULTISPACE = re.compile(r"[ \t]{2,}")
TRAILING_SPACE = re.compile(r"[ \t]+$", re.M)
LEAK_SOURCE = (
    "【來源】JEI（配方卡 role=output／input）、物品用途資料（PURPOSE：可飲用）、"
    "整合包本地取得索引（無掉落／任務路徑）"
)

PURE_SECTION_HEADER = re.compile(
    r"^[ \t]*(?:\d+[.)][ \t]*)?"
    r"(怎么来|怎样来|怎么來|怎樣來|怎麼来|怎麼來|怎么用|怎麼用|怎样用|怎樣用|用途|作为材料|作為材料|How to get|How to use|Usage)"
    r"[ \t]*[:：]?[ \t]*$",
    re.IGNORECASE | re.MULTILINE,
)


def canonical_section_key(label: str) -> str:
    t = label.strip()
    lower = t.lower()
    if t in ("怎么来", "怎样来", "怎么來", "怎樣來", "怎麼来", "怎麼來") or lower == "how to get":
        return "how_to_get"
    if t in ("怎么用", "怎麼用", "怎样用", "怎樣用") or lower in ("how to use", "usage"):
        return "how_to_use"
    if t == "用途":
        return "purpose"
    if t in ("作为材料", "作為材料"):
        return "as_material"
    return lower


def strip_duplicate_section_headers(reply: str | None) -> str:
    if reply is None or not reply:
        return "" if reply is None else reply
    seen: set[str] = set()
    kept: list[str] = []
    for line in re.split(r"\r?\n", reply):
        m = PURE_SECTION_HEADER.match(line)
        if m:
            key = canonical_section_key(m.group(1))
            if key in seen:
                continue
            seen.add(key)
        kept.append(line)
    return "\n".join(kept)


def test_behavior() -> None:
    dup = (
        "怎样来:\n"
        "1. 工作台: 合成。\n"
        "[[recipe_card:0]]\n"
        "2. 直接使用: 右键。\n"
        "1. 怎么来 :\n"
        "3. 作为材料（召唤祭坛）: 献祭。"
    )
    out = strip_duplicate_section_headers(dup)
    assert out.count("怎样来:") == 1
    assert "1. 怎么来 :" not in out
    assert "3. 作为材料（召唤祭坛）" in out
    assert "[[recipe_card:0]]" in out
    assert "2. 直接使用" in out

    twice = "怎么来:\n步骤一。\n怎么来:\n步骤二。"
    twice_out = strip_duplicate_section_headers(twice)
    assert twice_out.count("怎么来:") == 1
    assert "步骤一。" in twice_out and "步骤二。" in twice_out

    mixed = (
        "怎样来:\n"
        "1. 工作台:\n"
        "铁锭。\n"
        "1. 怎么来 :\n"
        "3. 作为材料:\n"
        "献祭。"
    )
    mixed_out = strip_duplicate_section_headers(mixed)
    assert "1. 怎么来 :" not in mixed_out
    assert "3. 作为材料:" in mixed_out
    assert "铁锭。" in mixed_out

    prose = "如果不知道怎么来，可以查 JEI。\n怎么来:\n箱子掉落。"
    prose_out = strip_duplicate_section_headers(prose)
    assert prose_out == prose

    distinct = "怎么用:\n手持。\n作为材料:\n合成。\n用途:\n装饰。"
    distinct_out = strip_duplicate_section_headers(distinct)
    assert distinct_out == distinct

    en = "How to get:\nloot\nHow to get:\nmore"
    en_out = strip_duplicate_section_headers(en)
    assert en_out.count("How to get:") == 1

    # English prose after a label must survive (label + space is not a pure header).
    en_prose = "Usage in combat is limited to tools.\nUsage:\nswing it"
    en_prose_out = strip_duplicate_section_headers(en_prose)
    assert "Usage in combat is limited to tools." in en_prose_out
    assert en_prose_out.count("Usage:") == 1

    en_prose_only = "Usage in combat is limited to tools."
    assert strip_duplicate_section_headers(en_prose_only) == en_prose_only

    assert strip_duplicate_section_headers("plain text\nno headers") == "plain text\nno headers"
    assert strip_duplicate_section_headers("") == ""
    assert strip_duplicate_section_headers(None) == ""

    cleaned = scrub_internal_field_echo(LEAK_SOURCE)
    assert "role=" not in cleaned, cleaned
    assert "PURPOSE" not in cleaned, cleaned
    assert "JEI（配方卡）" in cleaned, cleaned
    assert "物品用途資料（可飲用）" in cleaned, cleaned
    assert "整合包本地取得索引（無掉落／任務路徑）" in cleaned, cleaned
    assert "【來源】" in cleaned, cleaned
    assert "TOOL_BUILD" not in scrub_internal_field_echo("[TOOL_BUILD] parts")
    assert "TETRA_USE" not in scrub_internal_field_echo("[TETRA_USE] key=x")

    wg = scrub_internal_field_echo("WORLDGEN：洞穴")
    assert "WORLDGEN" not in wg, wg
    assert "洞穴" in wg, wg
    va = scrub_internal_field_echo("VARIANT：紅")
    assert "VARIANT" not in va, va
    assert "紅" in va, va
    co = scrub_internal_field_echo("CONTAINED：箱")
    assert "CONTAINED" not in co, co
    assert "箱" in co, co
    gu = scrub_internal_field_echo("GUIDE：看任務")
    assert "GUIDE" not in gu, gu
    assert "看任務" in gu, gu
    for tok in ("GUIDE", "VARIANT", "CONTAINED", "WORLDGEN"):
        assert BARE_INTERNAL_SECTION.search(f"{tok}："), tok
        assert not BARE_INTERNAL_SECTION.search(tok), tok  # body keeps bare token without colon
        assert PROMPT_SECTION_TAG.search(f"[{tok}]"), tok
        assert PROMPT_SECTION_TAG.search(f"[{tok.lower()}]"), tok
    role_out = scrub_internal_field_echo("Role=output")
    assert "role=" not in role_out.lower(), role_out
    assert has_internal_source_leak("Role=output")
    assert has_internal_source_leak("【來源】JEI（配方卡 Role=output）")
    assert has_internal_source_leak(LEAK_SOURCE)
    for raw, banned in (
        ("role=quest-as-obtain", ("-as-obtain", "as-obtain")),
        ("role=output, role=quest", ("=quest",)),
        ("role=maintenance-only", ("-only",)),
    ):
        cleaned_role = scrub_internal_field_echo(raw)
        for b in banned:
            assert b not in cleaned_role, (raw, cleaned_role)
        assert re.sub(r"[\s／/|,;]+", "", cleaned_role) == "", (raw, cleaned_role)
    assert not has_internal_source_leak("【來源】JEI, in-game GUIDE, web search")
    assert not has_internal_source_leak("【來源】JEI, in-game guide, web search")
    assert has_internal_source_leak("PURPOSE：可飲用")
    assert has_internal_source_leak("WORLDGEN：")
    assert has_internal_source_leak("[TOOL_BUILD] parts")

    src_purpose = scrub_internal_field_echo("【来源】JEI、物品提示 (PURPOSE)")
    assert "【来源】JEI、物品提示" in src_purpose, src_purpose
    assert "PURPOSE" not in src_purpose, src_purpose
    assert "()" not in src_purpose, src_purpose

    role_purpose = scrub_internal_field_echo(
        "JEI（配方卡 role=output／input）、物品用途資料（PURPOSE：可飲用）"
    )
    assert "role=" not in role_purpose, role_purpose
    assert "PURPOSE" not in role_purpose, role_purpose
    assert "配方卡" in role_purpose, role_purpose
    assert "可飲用" in role_purpose, role_purpose
    assert "()" not in role_purpose, role_purpose
    assert "（）" not in role_purpose, role_purpose

    wg_pair = scrub_internal_field_echo("A、(WORLDGEN)、B")
    assert "WORLDGEN" not in wg_pair, wg_pair
    assert "()" not in wg_pair, wg_pair
    assert "A、" in wg_pair, wg_pair
    assert "B" in wg_pair, wg_pair

    assert scrub_internal_field_echo("（可飲用）") == "（可飲用）"
    assert scrub_internal_field_echo("{{item:minecraft:stone}}") == "{{item:minecraft:stone}}"

    src_tw = translate_internal_tokens("【來源】JEI、物品提示（PURPOSE）", "zh_tw")
    assert "（物品用途資料）" in src_tw, src_tw
    assert "PURPOSE" not in src_tw, src_tw
    wg_tw = translate_internal_tokens("(WORLDGEN)", "zh_tw")
    assert "（世界生成資料）" in wg_tw, wg_tw
    assert "WORLDGEN" not in wg_tw, wg_tw
    role_out = translate_internal_tokens("role=output", "zh_tw")
    assert "合成產出" in role_out, role_out
    role_quest = translate_internal_tokens("role=quest-as-obtain", "zh_tw")
    assert "任務取得" in role_quest, role_quest
    assert "role=" not in role_quest.lower(), role_quest
    body_purpose = scrub_internal_field_echo("PURPOSE：可飲用")
    assert "PURPOSE" not in body_purpose, body_purpose
    assert "可飲用" in body_purpose, body_purpose
    keep_real = reply_sources_ensure(
        "1. x\n\n【來源】JEI, in-game GUIDE, web search", ["JEI"], "zh_tw"
    )
    assert keep_real == "1. x\n\n【來源】JEI, in-game GUIDE, web search", keep_real
    drop_unknown = translate_internal_tokens("【來源】JEI role=notarealrole", "zh_tw")
    assert "role=" not in drop_unknown.lower(), drop_unknown
    assert "notarealrole" not in drop_unknown, drop_unknown
    assert "JEI" in drop_unknown, drop_unknown
    tool_tw = translate_internal_tokens("【來源】[TOOL_BUILD] 零件", "zh_tw")
    assert "（工具組成資料）" in tool_tw, tool_tw
    assert "[" not in tool_tw, tool_tw
    tool_en = translate_internal_tokens("【來源】[TOOL_BUILD] 零件", "en_us")
    assert "(Tool build data)" in tool_en, tool_en
    recipe_role = translate_internal_tokens("【來源】JEI（配方卡 role=output）", "zh_tw")
    assert "（配方卡 合成產出）" in recipe_role, recipe_role
    assert "（（" not in recipe_role, recipe_role
    assert "））" not in recipe_role, recipe_role

    nested_purpose = translate_internal_tokens("（[PURPOSE]）", "zh_tw")
    assert "（物品用途資料）" in nested_purpose, nested_purpose
    assert "[" not in nested_purpose, nested_purpose
    assert "】" not in nested_purpose, nested_purpose
    dbl_square = translate_internal_tokens("[[PURPOSE]]", "zh_tw")
    assert "（物品用途資料）" in dbl_square, dbl_square
    assert "[" not in dbl_square, dbl_square
    cjk_nested = translate_internal_tokens("【[PURPOSE]】", "zh_tw")
    assert "（物品用途資料）" in cjk_nested, cjk_nested
    assert "[" not in cjk_nested, cjk_nested
    assert "】" not in cjk_nested, cjk_nested
    cjk_purpose = translate_internal_tokens("【PURPOSE】", "zh_tw")
    assert "（物品用途資料）" in cjk_purpose, cjk_purpose
    assert "PURPOSE" not in cjk_purpose, cjk_purpose
    assert "】" not in cjk_purpose, cjk_purpose
    role_duo = translate_internal_tokens("role=output、input", "zh_tw")
    assert "合成產出、作為材料" in role_duo, role_duo
    assert "input" not in role_duo, role_duo
    role_ws = translate_internal_tokens("role=output input", "zh_tw")
    assert "合成產出、作為材料" in role_ws, role_ws
    role_pipe_keep = translate_internal_tokens("role=output｜合成", "zh_tw")
    assert "合成產出、合成" in role_pipe_keep, role_pipe_keep
    assert "role=" not in role_pipe_keep.lower(), role_pipe_keep
    role_quest_task = translate_internal_tokens("role=quest_task | 任務", "zh_tw")
    assert "任務取得、任務" in role_quest_task, role_quest_task
    jei_role_pipe = translate_internal_tokens("JEI role=output｜合成", "zh_tw")
    assert "JEI、合成產出、合成" in jei_role_pipe, jei_role_pipe
    empty_role = reply_sources_ensure("1. x\n\n【來源】role=", ["JEI"], "zh_tw")
    assert "role=" not in empty_role.lower(), empty_role
    assert "【來源】" in empty_role, empty_role
    assert "JEI" in empty_role, empty_role
    fw_eq = translate_internal_tokens("role＝output", "zh_tw")
    assert "合成產出" in fw_eq, fw_eq
    assert "role" not in fw_eq.lower(), fw_eq
    dup_label = translate_internal_tokens("物品用途資料（PURPOSE：可飲用）", "zh_tw")
    assert "物品用途資料（可飲用）" in dup_label, dup_label
    assert "物品用途資料（物品用途資料" not in dup_label, dup_label
    assert "PURPOSE" not in dup_label, dup_label
    role_pipe_space = translate_internal_tokens("role=output | 合成", "zh_tw")
    assert "合成產出、合成" in role_pipe_space, role_pipe_space
    role_slash = translate_internal_tokens("role=output／input", "zh_tw")
    assert "合成產出、作為材料" in role_slash, role_slash
    role_en = translate_internal_tokens("role=output／input", "en_us")
    assert "Craft output" in role_en, role_en
    assert "Used as ingredient" in role_en or "used as ingredient" in role_en, role_en
    tool_parts_en = translate_internal_tokens("[TOOL_BUILD] parts", "en_us")
    assert "(Tool build data)" in tool_parts_en, tool_parts_en
    assert "parts" in tool_parts_en, tool_parts_en
    assert "[" not in tool_parts_en, tool_parts_en
    assert "]" not in tool_parts_en, tool_parts_en
    body_clear = "PURPOSE IS CLEAR: this is used for crafting."
    assert scrub_internal_field_echo(body_clear) == body_clear
    body_colon = scrub_internal_field_echo("PURPOSE：可飲用")
    assert "PURPOSE" not in body_colon, body_colon
    assert "可飲用" in body_colon, body_colon
    assert scrub_internal_field_echo("（可飲用）") == "（可飲用）"
    assert scrub_internal_field_echo("(3×3)") == "(3×3)"
    assert scrub_internal_field_echo("{{item:minecraft:stone}}") == "{{item:minecraft:stone}}"
    assert scrub_internal_field_echo("minecraft:iron_ingot") == "minecraft:iron_ingot"


def scrub_internal_field_echo(text: str | None) -> str:
    if text is None or text == "":
        return "" if text is None else text
    t = ROLE_EQ_TOKEN.sub("", text)
    t = PROMPT_SECTION_TAG.sub("", t)
    t = TAG_CJK.sub("", t)
    t = TAG_FW_PAREN.sub("", t)
    t = TAG_PAREN.sub("", t)
    t = BARE_INTERNAL_SECTION.sub("", t)
    return strip_reply_debris(t)


def strip_reply_debris(t: str) -> str:
    for _ in range(8):
        prev = t
        t = EMPTY_BRACKETS.sub("", t)
        t = DUP_SEPARATORS.sub(r"\1", t)
        t = LEADING_ORPHAN_SEP.sub("", t)
        t = TRAILING_ORPHAN_SEP.sub("", t)
        t = HALF_ORPHAN_OPEN.sub("", t)
        t = HALF_ORPHAN_CLOSE.sub("", t)
        if t == prev:
            break
    t = SPACE_BEFORE_CLOSE.sub(r"\1", t)
    t = SPACE_AFTER_OPEN.sub(r"\1", t)
    t = MULTISPACE.sub(" ", t)
    t = TRAILING_SPACE.sub("", t)
    return t


def has_internal_source_leak(text: str | None) -> bool:
    if text is None or text == "":
        return False
    if ROLE_EQ_TOKEN.search(text):
        return True
    if "render_recipe_cards" in text:
        return True
    if PROMPT_SECTION_TAG.search(text):
        return True
    if TAG_CJK.search(text):
        return True
    if TAG_FW_PAREN.search(text) or TAG_PAREN.search(text):
        return True
    return BARE_INTERNAL_SECTION_COLON.search(text) is not None


def label_bundle(lang: str) -> dict[str, str]:
    code = lang if lang in ("zh_tw", "zh_cn", "en_us") else "zh_tw"
    if code not in _LABEL_CACHE:
        path = LANG_TREES[0] / f"{code}.json"
        data = json.loads(path.read_text(encoding="utf-8"))
        _LABEL_CACHE[code] = {
            k: v for k, v in data.items() if isinstance(k, str) and k.startswith("packai.label.") and isinstance(v, str) and v
        }
    return _LABEL_CACHE[code]


def src_label_key(token: str) -> str:
    u = token.strip().upper()
    if u.startswith("SCROLL_"):
        return "packai.label.src.scroll"
    return "packai.label.src." + u.lower()


def src_label(token: str, lang: str) -> str | None:
    hit = label_bundle(lang).get(src_label_key(token))
    return hit if hit else None


def bundle_lang(code: str | None) -> str:
    """Mirror ReplyLang.bundleLang: zh_cn/zh_tw → full-width wrap; else en_us."""
    c = (code or "").strip().lower().replace("-", "_")
    if not c:
        return "zh_tw"
    if not c.startswith("zh"):
        return "en_us"
    if c.startswith("zh_cn") or c.startswith("zh_sg") or c in ("zh_hans", "zh"):
        return "zh_cn"
    return "zh_tw"


def wrap_src_label(label: str, lang: str) -> str:
    if bundle_lang(lang) == "en_us":
        return "(" + label + ")"
    return "（" + label + "）"


def source_join(lang: str) -> str:
    return ", " if bundle_lang(lang) == "en_us" else "、"


def role_primary(v: str) -> str:
    dash = v.find("-")
    us = v.find("_")
    cut = -1
    if dash >= 0:
        cut = dash
    if us >= 0 and (cut < 0 or us < cut):
        cut = us
    return v if cut < 0 else v[:cut]


def role_label(raw: str, lang: str) -> str | None:
    v = raw.strip().lower()
    if not v:
        return None
    primary = role_primary(v)
    if primary not in INTERNAL_ROLE_VALUES:
        return None
    hit = label_bundle(lang).get("packai.label.role." + primary)
    return hit if hit else None


def translate_internal_tokens(footer: str | None, lang: str) -> str:
    return render_sources_footer(footer, lang)


def render_sources_footer(footer: str | None, lang: str) -> str:
    if footer is None or footer == "":
        return "" if footer is None else footer
    hm = HEADER_RE.search(footer)
    if hm and hm.start() == 0:
        end = hm.end()
        while end < len(footer) and footer[end] in (" ", "\t"):
            end += 1
        header = footer[:end]
        body = footer[end:]
    else:
        header = ""
        body = footer
    raw_items = split_top_level(body)
    out: list[str] = []
    mutated = False
    for raw in raw_items:
        it = raw.strip()
        if not it:
            mutated = True
            continue
        nxt = replace_internal_tokens(it, lang)
        nxt = normalise_brackets(nxt, lang)
        nxt = collapse_label_duplication(nxt, lang)
        nxt = strip_reply_debris(nxt).strip()
        if nxt != it:
            mutated = True
        if not nxt or is_pure_junk(nxt):
            mutated = True
            continue
        out.append(nxt)
    deduped: list[str] = []
    seen: set[str] = set()
    for it in out:
        if it not in seen:
            seen.add(it)
            deduped.append(it)
    if len(deduped) != len(out):
        mutated = True
    if not deduped:
        return ""
    if not mutated:
        return footer
    return header + source_join(lang).join(deduped)


def split_top_level(text: str) -> list[str]:
    items: list[str] = []
    if not text:
        return items
    cur: list[str] = []
    round_d = square = cjk = curly = 0
    i = 0
    n = len(text)

    def is_list_punct(ch: str) -> bool:
        return ch in "、,／/｜|;；・\n\r"

    def is_horiz_ws(ch: str) -> bool:
        return ch in " \t\u3000"

    def starts_role_eq(idx: int) -> bool:
        m = ROLE_EQ_START.search(text, idx)
        return m is not None and m.start() == idx

    def flush() -> None:
        if cur:
            items.append("".join(cur))
            cur.clear()

    while i < n:
        c = text[i]
        nested = round_d > 0 or square > 0 or cjk > 0 or curly > 0
        if not nested and is_list_punct(c):
            flush()
            while i < n and (is_list_punct(text[i]) or is_horiz_ws(text[i])):
                i += 1
            continue
        if not nested and is_horiz_ws(c):
            j = i
            while j < n and is_horiz_ws(text[j]):
                j += 1
            if j < n and starts_role_eq(j):
                flush()
                i = j
                continue
        if c == "(" or c == "（":
            round_d += 1
        elif (c == ")" or c == "）") and round_d > 0:
            round_d -= 1
        elif c == "[":
            square += 1
        elif c == "]" and square > 0:
            square -= 1
        elif c == "【":
            cjk += 1
        elif c == "】" and cjk > 0:
            cjk -= 1
        elif c == "{":
            curly += 1
        elif c == "}" and curly > 0:
            curly -= 1
        cur.append(c)
        i += 1
    flush()
    return items


def replace_internal_tokens(text: str, lang: str) -> str:
    t = replace_role_eq(text, lang)
    t = replace_tagged_tokens(t, lang)
    t = replace_colon_tokens(t, lang)
    return replace_exact_item_token(t, lang)


def replace_role_eq(text: str, lang: str) -> str:
    join = source_join(lang)

    def repl(m: re.Match[str]) -> str:
        raw = m.group(0)
        eq = -1
        for i, ch in enumerate(raw):
            if ch in "=＝":
                eq = i
                break
        rest = "" if eq < 0 else raw[eq + 1 :].strip()
        labels: list[str] = []
        if rest:
            for part in ROLE_VALUE_SEP.split(rest):
                if not part:
                    continue
                lab = role_label(part, lang)
                if lab is not None:
                    labels.append(lab)
        return join.join(labels)

    return ROLE_EQ_TOKEN.sub(repl, text)


def replace_tagged_tokens(text: str, lang: str) -> str:
    t = text

    def br(m: re.Match[str]) -> str:
        lab = src_label(m.group(1), lang)
        return "" if lab is None else wrap_src_label(lab, lang)

    for _ in range(8):
        prev = t
        t = TAG_SQUARE.sub(br, t)
        t = TAG_CJK.sub(br, t)
        t = TAG_FW_PAREN.sub(br, t)
        t = TAG_PAREN.sub(br, t)
        if t == prev:
            break
    return t


def replace_colon_tokens(text: str, lang: str) -> str:
    def colon(m: re.Match[str]) -> str:
        lab = src_label(m.group(1), lang)
        return "" if lab is None else lab + m.group(2)

    return TRANSLATE_COLON_TOKEN.sub(colon, text)


def is_section_token_name(t: str) -> bool:
    u = t.strip().upper()
    if u.startswith("SCROLL_") and len(u) > 7:
        return True
    return u in INTERNAL_SECTION_TOKENS or u in EXTRA_BARE_SECTION_TOKENS


def looks_like_role_value(t: str) -> bool:
    if not t:
        return False
    for ch in t:
        if not (ch.isalnum() or ch in "_-"):
            return False
    return role_primary(t.lower()) in INTERNAL_ROLE_VALUES


def replace_exact_item_token(text: str, lang: str) -> str:
    t = text.strip()
    if not t:
        return text
    if is_section_token_name(t):
        lab = src_label(t, lang)
        return "" if lab is None else lab
    if looks_like_role_value(t):
        lab = role_label(t, lang)
        return "" if lab is None else lab
    return text


def fully_wrapped_inner(t: str) -> str | None:
    if t is None or len(t) < 2:
        return None
    pairs = {"（": "）", "(": ")", "[": "]", "【": "】"}
    o = t[0]
    c = pairs.get(o)
    if c is None or t[-1] != c:
        return None
    d = 0
    for i, ch in enumerate(t):
        if ch == o:
            d += 1
        elif ch == c:
            d -= 1
            if d == 0 and i < len(t) - 1:
                return None
    return t[1:-1] if d == 0 else None


def is_known_src_label(text: str, lang: str) -> bool:
    s = (text or "").strip()
    if not s:
        return False
    bundle = label_bundle(lang)
    for tok in list(INTERNAL_SECTION_TOKENS) + list(EXTRA_BARE_SECTION_TOKENS):
        lab = bundle.get(src_label_key(tok))
        if lab and s == lab:
            return True
    scroll = bundle.get("packai.label.src.scroll")
    return bool(scroll) and s == scroll


def peel_once(t: str, lang: str) -> str:
    if t.startswith("[[") or t.startswith("{{"):
        return t
    inner = fully_wrapped_inner(t)
    if inner is None:
        return t
    trimmed = inner.strip()
    if fully_wrapped_inner(trimmed) is not None:
        return trimmed
    if is_known_src_label(trimmed, lang):
        return wrap_src_label(trimmed, lang)
    return t


def normalise_brackets(text: str, lang: str) -> str:
    if text is None or text == "":
        return "" if text is None else text
    t = text.strip()
    if t.startswith("[[") or t.startswith("{{"):
        return t
    for _ in range(8):
        prev = t
        t = peel_once(t, lang)
        if t == prev:
            break
    if is_known_src_label(t, lang):
        return wrap_src_label(t, lang)
    return t


def known_labels_longest_first(lang: str) -> list[str]:
    bundle = label_bundle(lang)
    labels: list[str] = []
    for tok in list(INTERNAL_SECTION_TOKENS) + list(EXTRA_BARE_SECTION_TOKENS):
        lab = bundle.get(src_label_key(tok))
        if lab and lab not in labels:
            labels.append(lab)
    scroll = bundle.get("packai.label.src.scroll")
    if scroll and scroll not in labels:
        labels.append(scroll)
    for r in INTERNAL_ROLE_VALUES:
        lab = bundle.get("packai.label.role." + r)
        if lab and lab not in labels:
            labels.append(lab)
    labels.sort(key=len, reverse=True)
    return labels


def collapse_label_duplication(text: str, lang: str) -> str:
    if text is None or text == "":
        return "" if text is None else text
    t = text
    for lab in known_labels_longest_first(lang):
        t = t.replace(lab + "（" + lab + "：", lab + "（")
        t = t.replace(lab + "（" + lab + ":", lab + "（")
        t = t.replace(lab + "(" + lab + "：", lab + "(")
        t = t.replace(lab + "(" + lab + ":", lab + "(")
        t = t.replace(lab + "（" + lab + "）", lab)
        t = t.replace(lab + "(" + lab + ")", lab)
    return t


def is_pure_junk(s: str) -> bool:
    t = (s or "").strip()
    if not t:
        return True
    u = re.sub(r"(?i)role\s*[=＝]\s*", "", t)
    u = re.sub(r"[=＝｜|,;；、，／/·・\s]+", "", u)
    return u == ""


def reply_sources_ensure(answer: str, labels: list[str], lang: str) -> str:
    m = re.search(r"(【來源】|【来源】|\[Sources\])", answer)
    if not m:
        return answer
    footer = answer[m.start() :]
    translated = translate_internal_tokens(footer, lang)
    empty_footer = not translated.strip() or not HEADER_RE.sub("", translated, count=1).strip()
    if not empty_footer and not has_internal_source_leak(translated):
        if translated == footer:
            return answer
        return answer[: m.start()] + translated
    body = answer[: m.start()].rstrip()
    canonical = "CANONICAL" if not labels else labels[0]
    return (body + "\n\n" if body else "") + "【來源】" + canonical


def extract_list_of_strings(src: str, name: str) -> list[str]:
    m = re.search(
        rf"(?:public |private )?static final List<String> {name}\s*=\s*List\.of\((.*?)\);",
        src,
        re.S,
    )
    assert m, f"missing List.of {name}"
    return re.findall(r'"([^"]+)"', m.group(1))


def extract_java_string(src: str, name: str) -> str:
    m = re.search(rf"static final String {name}\s*=\s*\"([^\"]+)\"", src)
    assert m, f"missing String {name}"
    return m.group(1)


def check_source(path: Path) -> str:
    src = path.read_text(encoding="utf-8")
    assert "stripDuplicateSectionHeaders" in src, f"{path}: missing stripDuplicateSectionHeaders"
    assert "PURE_SECTION_HEADER" in src, f"{path}: missing header pattern"
    assert "canonicalSectionKey" in src, f"{path}: missing canonicalSectionKey"
    assert "scrubInternalFieldEcho" in src, f"{path}: missing scrubInternalFieldEcho"
    assert "ROLE_EQ_TOKEN" in src, f"{path}: missing ROLE_EQ_TOKEN"
    assert "BARE_INTERNAL_SECTION" in src, f"{path}: missing BARE_INTERNAL_SECTION"
    assert "BARE_INTERNAL_SECTION_COLON" in src, f"{path}: missing BARE_INTERNAL_SECTION_COLON"
    assert "EMPTY_BRACKETS" in src, f"{path}: missing EMPTY_BRACKETS"
    assert "DUP_SEPARATORS" in src, f"{path}: missing DUP_SEPARATORS"
    assert "LEADING_ORPHAN_SEP" in src, f"{path}: missing LEADING_ORPHAN_SEP"
    assert "TRAILING_ORPHAN_SEP" in src, f"{path}: missing TRAILING_ORPHAN_SEP"
    assert "HALF_ORPHAN_OPEN" in src, f"{path}: missing HALF_ORPHAN_OPEN"
    assert "stripReplyDebris" in src, f"{path}: missing stripReplyDebris"
    role_m = re.search(
        r"ROLE_EQ_TOKEN\s*=\s*Pattern\.compile\(\s*\"([^\"]+)\"",
        src,
    )
    assert role_m, f"{path}: missing ROLE_EQ_TOKEN compile string"
    role_pat = role_m.group(1)
    assert "A-Za-z0-9_" in role_pat, f"{path}: ROLE_EQ_TOKEN must use alnum/_/- values"
    assert "＝" in role_pat, f"{path}: ROLE_EQ_TOKEN must match fullwidth equals"
    assert "(?!role" in role_pat, f"{path}: ROLE_EQ_TOKEN must not swallow a following role="
    assert "splitTopLevel" in src, f"{path}: missing splitTopLevel"
    assert "renderSourcesFooter" in src, f"{path}: missing renderSourcesFooter"
    assert "normaliseBrackets" in src, f"{path}: missing normaliseBrackets"
    assert "collapseLabelDuplication" in src, f"{path}: missing collapseLabelDuplication"
    assert "TAG_CJK" in src, f"{path}: missing TAG_CJK"
    assert "PROMPT_SECTION_TAG" in src, f"{path}: missing PROMPT_SECTION_TAG"
    java_tokens = extract_list_of_strings(src, "INTERNAL_SECTION_TOKENS")
    assert java_tokens == list(INTERNAL_SECTION_TOKENS), (
        f"{path}: INTERNAL_SECTION_TOKENS {java_tokens} != {list(INTERNAL_SECTION_TOKENS)}"
    )
    java_extra = extract_list_of_strings(src, "EXTRA_BARE_SECTION_TOKENS")
    assert java_extra == list(EXTRA_BARE_SECTION_TOKENS), (
        f"{path}: EXTRA_BARE_SECTION_TOKENS {java_extra} != {list(EXTRA_BARE_SECTION_TOKENS)}"
    )
    java_scroll = extract_java_string(src, "SCROLL_SECTION_REGEX")
    assert java_scroll == SCROLL_SECTION_REGEX, (
        f"{path}: SCROLL_SECTION_REGEX {java_scroll!r} != {SCROLL_SECTION_REGEX!r}"
    )
    java_roles = extract_list_of_strings(src, "INTERNAL_ROLE_VALUES")
    assert java_roles == list(INTERNAL_ROLE_VALUES), (
        f"{path}: INTERNAL_ROLE_VALUES {java_roles} != {list(INTERNAL_ROLE_VALUES)}"
    )
    assert "translateInternalTokens" in src, f"{path}: missing translateInternalTokens"
    assert "ReplySources.HEADER" in src, f"{path}: scrubPromptEcho must spare sources footer"
    assert "lookupLabel" in src, f"{path}: footer labels must use ReplyLang.lookupLabel"
    assert '"[" + label + "]"' not in src, f"{path}: [TOKEN] must wrap as locale parens, not [label]"
    assert "ReplyLang.bundleLang" in src, f"{path}: bracket wrap must use ReplyLang.bundleLang"
    assert "wrapSrcLabel" in src, f"{path}: missing wrapSrcLabel"
    tag_m = re.search(
        r"PROMPT_SECTION_TAG\s*=\s*Pattern\.compile\((.*?)\);",
        src,
        re.S,
    )
    assert tag_m, f"{path}: missing PROMPT_SECTION_TAG compile"
    tag_body = tag_m.group(1)
    assert "internalSectionAlternation()" in tag_body, f"{path}: PROMPT_SECTION_TAG must use shared alternation"
    assert "CASE_INSENSITIVE" in tag_body, f"{path}: PROMPT_SECTION_TAG must be case-insensitive"
    bare_m = re.search(
        r"BARE_INTERNAL_SECTION\s*=\s*Pattern\.compile\((.*?)\);",
        src,
        re.S,
    )
    assert bare_m, f"{path}: missing BARE_INTERNAL_SECTION compile"
    assert "internalSectionAlternation()" in bare_m.group(1), (
        f"{path}: BARE_INTERNAL_SECTION must use shared alternation"
    )
    assert "[:：]?" not in bare_m.group(1), f"{path}: body bare token must require colon"
    assert "[:：]" in bare_m.group(1), f"{path}: body bare token must consume colon"
    colon_m = re.search(
        r"BARE_INTERNAL_SECTION_COLON\s*=\s*Pattern\.compile\((.*?)\);",
        src,
        re.S,
    )
    assert colon_m, f"{path}: missing BARE_INTERNAL_SECTION_COLON compile"
    colon_body = colon_m.group(1)
    assert "internalSectionAlternation()" in colon_body, (
        f"{path}: BARE_INTERNAL_SECTION_COLON must use shared alternation"
    )
    assert "[:：]?" not in colon_body, f"{path}: footer colon must be required, not optional"
    alt_m = re.search(
        r"private static String internalSectionAlternation\(\) \{.*?\n    \}",
        src,
        re.S,
    )
    assert alt_m, f"{path}: missing internalSectionAlternation"
    alt_body = alt_m.group(0)
    assert "length()" in alt_body, f"{path}: internalSectionAlternation must sort by length"
    leak_m = re.search(
        r"public static boolean hasInternalSourceLeak\(String text\) \{.*?\n    \}",
        src,
        re.S,
    )
    assert leak_m, f"{path}: missing hasInternalSourceLeak"
    leak_body = leak_m.group(0)
    assert "text.contains(token)" not in leak_body, (
        f"{path}: footer gate must not bare-contains tokens"
    )
    assert "ROLE_EQ_TOKEN" in leak_body, f"{path}: footer gate must use ROLE_EQ_TOKEN"
    assert "PROMPT_SECTION_TAG" in leak_body, f"{path}: footer gate must use PROMPT_SECTION_TAG"
    assert "TAG_CJK" in leak_body, f"{path}: footer gate must catch 【TOKEN】"
    assert "BARE_INTERNAL_SECTION_COLON" in leak_body, (
        f"{path}: footer gate must require colon on bare tokens"
    )
    assert "BARE_INTERNAL_SECTION.matcher" not in leak_body, (
        f"{path}: footer gate must not use optional-colon BARE_INTERNAL_SECTION"
    )
    assert "render_recipe_cards" in leak_body, f"{path}: footer gate must keep render_recipe_cards"
    m_safe = re.search(
        r"public static boolean isPlayerSafeLine\(String line\) \{.*?\n    \}",
        src,
        re.S,
    )
    assert m_safe, f"{path}: missing isPlayerSafeLine"
    safe_body = m_safe.group(0)
    assert "toLowerCase(Locale.ROOT)" in safe_body, (
        f"{path}: isPlayerSafeLine role= must ignore case"
    )
    assert 'contains("role=")' in safe_body, f"{path}: isPlayerSafeLine must gate role="
    assert '"role="' in src, f"{path}: PLAYER_UNSAFE_MARKERS must keep role="
    return safe_body


def check_sources_ensure(path: Path) -> None:
    src = path.read_text(encoding="utf-8")
    assert "hasInternalSourceLeak" in src, f"{path}: missing hasInternalSourceLeak"
    assert "role=" in src, f"{path}: leak gate must mention role="
    assert "AskReplyScrub.hasInternalSourceLeak" in src, (
        f"{path}: footer gate must delegate to AskReplyScrub"
    )
    assert "AskReplyScrub.translateInternalTokens" in src, (
        f"{path}: ensure must translate footer tokens before leak gate"
    )
    assert "emptyFooter" in src or "isBlank()" in src, (
        f"{path}: ensure must replace an emptied footer with canonical labels"
    )


def check_wiring(path: Path) -> None:
    src = path.read_text(encoding="utf-8")
    assert src.count("AskReplyScrub.stripDuplicateSectionHeaders") >= 2, f"{path}: wire both ask paths"
    idx = src.find("AskReplyScrub.stripDuplicateSectionHeaders")
    cards = src.find("AskCardFallback.ensureCards", idx)
    assert cards > idx, f"{path}: stripDuplicateSectionHeaders must run before ensureCards"


def main() -> None:
    test_behavior()
    safe_bodies: list[str] = []
    for p in SCRUB_PATHS:
        assert p.is_file(), f"missing {p}"
        safe_bodies.append(check_source(p))
    assert len(set(safe_bodies)) == 1, "isPlayerSafeLine must be identical in both trees"
    for p in SOURCES_PATHS:
        assert p.is_file(), f"missing {p}"
        check_sources_ensure(p)
    for p in SERVICE_PATHS:
        assert p.is_file(), f"missing {p}"
        check_wiring(p)
    print("check_reply_structure_scrub OK")


if __name__ == "__main__":
    main()
