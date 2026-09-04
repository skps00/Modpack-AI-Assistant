#!/usr/bin/env python3
"""Mirror AskCardFallback.ensureCards — mod fully manages card marker placement.

Interleaved model markers (each after a method line, none past the source boundary) are
trusted unchanged; only missing, piled, or trailing-after-source markers are repaired.
"""

from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HELPER_PATHS = (
    ROOT / "forge" / "1.19.2" / "src" / "main" / "java" / "com" / "skps9" / "packai" / "logic" / "AskCardFallback.java",
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
    / "AskCardFallback.java",
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

HOW_GET = (
    "怎么来",
    "怎样来",
    "怎么取得",
    "怎么获得",
    "怎麼來",
    "怎樣來",
    "怎麼取得",
    "怎麼獲得",
    "怎么用",
    "怎样用",
    "怎麼用",
    "怎樣用",
)

METHOD_LINE = re.compile(r"(?m)^\s*(\d+)\.\s+([^\n:：]+)[:：]")
CARD_MARKER = re.compile(r"\[\[recipe_card:\d+\]\]")

SECTION_PREFIXES = ("怎么用", "怎样用", "用途", "怎么来", "怎样来", "作为材料")
SOURCE_HEADERS = ("【来源】", "来源", "【配方】", "【用途】")
GET_SECTION_PREFIXES = ("怎样来", "怎么来", "怎么取得", "怎么获得", "怎樣來", "怎麼來")
USE_SECTION_PREFIXES = ("怎么用", "用途", "怎麼用", "怎样用", "怎樣用")


def looks_like_how_to_get(reply: str | None) -> bool:
    if not reply:
        return False
    if any(h in reply for h in HOW_GET):
        return True
    lower = reply.lower()
    return "how to get" in lower or "how to obtain" in lower or "how to use" in lower


def is_section_title(line: str | None) -> bool:
    if line is None:
        return False
    trimmed = line.strip()
    if any(trimmed.startswith(header) for header in SOURCE_HEADERS):
        return True
    if ":" not in trimmed and "：" not in trimmed:
        return False
    return any(trimmed.startswith(prefix) for prefix in SECTION_PREFIXES)


def section_type_of(line: str | None) -> int:
    """0 = GET, 1 = USE, -1 = not a typed section title."""
    if line is None:
        return -1
    t = line.strip()
    if ":" not in t and "：" not in t:
        return -1
    for p in GET_SECTION_PREFIXES:
        if t.startswith(p):
            return 0
    for p in USE_SECTION_PREFIXES:
        if t.startswith(p):
            return 1
    return -1


def strip_markers(reply: str) -> str:
    return CARD_MARKER.sub("", reply)


def first_source_header_index(reply: str) -> int:
    off = 0
    for line in reply.split("\n"):
        t = line.strip()
        for header in SOURCE_HEADERS:
            if t.startswith(header):
                return off
        off += len(line) + 1
    return -1


def separator_has_content(reply: str, frm: int, to: int) -> bool:
    for line in reply[frm:to].split("\n"):
        t = line.strip()
        if not t:
            continue
        if CARD_MARKER.search(t):
            continue
        if is_section_title(t):
            continue
        return True
    return False


def reply_has_interleaved_markers(reply: str) -> bool:
    """True when reply has at least two markers interleaved (content-line separators —
    method line, bullet material line, or prose; section titles/blank lines do not count;
    none after source). Zero/one marker → fallback."""
    spans = [m.span() for m in CARD_MARKER.finditer(reply)]
    if not spans:
        return False
    if len(spans) < 2:
        return False
    src = first_source_header_index(reply)
    prev_end = spans[0][1]
    for s, e in spans:
        if src >= 0 and s > src:
            return False
    for s, e in spans[1:]:
        if not separator_has_content(reply, prev_end, s):
            return False
        prev_end = e
    return True


def count_method_lines(reply: str) -> int:
    return len(METHOD_LINE.findall(reply))


def collect_section_method_spans(reply: str, wanted_type: int) -> tuple[list[int], list[int]]:
    method_starts: list[int] = []
    method_ends: list[int] = []
    current_section = -1
    line_start = 0
    while line_start <= len(reply):
        nl = reply.find("\n", line_start)
        line_end = len(reply) if nl == -1 else nl
        line = reply[line_start:line_end]
        st = section_type_of(line)
        if st >= 0:
            current_section = st
        elif is_section_title(line):
            current_section = -1
        if current_section == wanted_type:
            m = METHOD_LINE.match(line)
            if m:
                method_starts.append(line_start + m.start())
                method_ends.append(line_start + m.end())
        if nl == -1:
            break
        line_start = nl + 1
    return method_starts, method_ends


def count_section_method_lines(reply: str, wanted_type: int) -> int:
    return len(collect_section_method_spans(reply, wanted_type)[0])


def collect_output_quest_indices(cards: list[dict]) -> list[int]:
    indices: list[int] = []
    for i, c in enumerate(cards):
        if c is None or c.get("empty") or c.get("input"):
            continue
        indices.append(i)
    return indices


def collect_input_indices(cards: list[dict]) -> list[int]:
    indices: list[int] = []
    for i, c in enumerate(cards):
        if c is None or c.get("empty") or not c.get("input"):
            continue
        indices.append(i)
    return indices


def find_block_end(reply: str, block_start: int, next_method_start: int) -> int:
    """End of a method's description block: stops at a section title, a blank line
    (paragraph boundary — trailing section prose like '本包无额外掉落…' must NOT be
    swallowed, smoke 05:21 2026-09-05), or the next method line."""
    line_start = block_start
    while line_start < next_method_start:
        line_end = reply.find("\n", line_start)
        actual_line_end = next_method_start if line_end == -1 or line_end >= next_method_start else line_end
        line = reply[line_start:actual_line_end]
        if is_section_title(line):
            return line_start
        if not line.strip():
            return line_start  # blank line — paragraph boundary
        if line_end == -1 or line_end >= next_method_start:
            break
        line_start = line_end + 1
    return next_method_start


def find_last_line_end(reply: str, block_start: int, block_end: int) -> int:
    last_end = block_start
    pos = block_start
    while pos < block_end:
        line_end = reply.find("\n", pos)
        actual_line_end = block_end if line_end == -1 or line_end >= block_end else line_end
        line = reply[pos:actual_line_end]
        if line.strip():
            last_end = actual_line_end
        if line_end == -1 or line_end >= block_end:
            break
        pos = line_end + 1
    return last_end


def try_insert_after_methods_sectioned(
    reply: str, card_indices: list[int], wanted_type: int
) -> str | None:
    method_starts, method_ends = collect_section_method_spans(reply, wanted_type)
    if not method_starts:
        return None
    count = min(len(method_starts), len(card_indices))
    insertions: list[tuple[int, int]] = []
    for i in range(count):
        block_start = method_ends[i]
        nl = reply.find("\n", block_start)
        block_start = len(reply) if nl == -1 else nl + 1  # after method line's newline
        next_method_start = method_starts[i + 1] if i + 1 < len(method_starts) else len(reply)
        block_end = find_block_end(reply, block_start, next_method_start)
        insert_pos = find_last_line_end(reply, block_start, block_end)
        insertions.append((insert_pos, card_indices[i]))
    if len(card_indices) > count:
        last_idx = len(method_starts) - 1
        bs_last = method_ends[last_idx]
        nl = reply.find("\n", bs_last)
        bs_last = len(reply) if nl == -1 else nl + 1  # after last method line's newline
        be_last = find_block_end(reply, bs_last, len(reply))
        ip_last = find_last_line_end(reply, bs_last, be_last)
        for j in range(count, len(card_indices)):
            insertions.append((ip_last, card_indices[j]))
    by_pos: dict[int, list[int]] = {}
    for pos, card_i in insertions:
        by_pos.setdefault(pos, []).append(card_i)
    sb = reply
    for pos in sorted(by_pos.keys(), reverse=True):
        at_pos = by_pos[pos]
        at_pos.sort()
        joined = "".join(f"\n[[recipe_card:{c}]]" for c in at_pos)
        sb = sb[:pos] + joined + sb[pos:]
    return sb


MATERIAL_USE_KEYWORDS = ("材料", "祭坛", "祭壇", "用途", "当作", "當作")
MATERIAL_USE_KEYWORDS_EN = ("ingredient", "material")


def is_material_use_label(label: str | None) -> bool:
    if label is None:
        return False
    t = label.strip()
    if any(kw in t for kw in MATERIAL_USE_KEYWORDS):
        return True
    lower = t.lower()
    return any(kw in lower for kw in MATERIAL_USE_KEYWORDS_EN)


def try_insert_after_material_use_method(reply: str, card_indices: list[int]) -> str | None:
    """Cluster all USE/input cards after material-use method (not one-per-method)."""
    method_starts: list[int] = []
    method_ends: list[int] = []
    labels: list[str] = []
    current_section = -1
    line_start = 0
    while line_start <= len(reply):
        nl = reply.find("\n", line_start)
        line_end = len(reply) if nl == -1 else nl
        line = reply[line_start:line_end]
        st = section_type_of(line)
        if st >= 0:
            current_section = st
        elif is_section_title(line):
            current_section = -1
        if current_section == 1:
            m = METHOD_LINE.match(line)
            if m:
                method_starts.append(line_start + m.start())
                method_ends.append(line_start + m.end())
                labels.append(m.group(2))
        if nl == -1:
            break
        line_start = nl + 1
    if not method_starts:
        return None
    target_idx = -1
    for i, lab in enumerate(labels):
        if is_material_use_label(lab):
            target_idx = i
    if target_idx < 0:
        target_idx = len(method_starts) - 1
    # Cluster right after the target material-use method's description block. find_block_end
    # stops at a blank line so trailing section prose (e.g. "本包无额外掉落...") is not
    # swallowed and cards are not pushed below the text (smoke 2026-09-05).
    block_start = method_ends[target_idx]
    nl = reply.find("\n", block_start)
    block_start = len(reply) if nl == -1 else nl + 1  # after method line's newline
    next_method_start = (
        method_starts[target_idx + 1] if target_idx + 1 < len(method_starts) else len(reply)
    )
    block_end = find_block_end(reply, block_start, next_method_start)
    insert_pos = find_last_line_end(reply, block_start, block_end)
    sorted_idx = sorted(card_indices)
    joined = "".join(f"\n[[recipe_card:{c}]]" for c in sorted_idx)
    return reply[:insert_pos] + joined + reply[insert_pos:]


def append_at_end(reply: str, card_indices: list[int]) -> str:
    extra = " ".join(f"[[recipe_card:{i}]]" for i in card_indices)
    base = reply if reply.endswith("\n") else reply + "\n"
    return base + extra


def marked_card_indices(reply: str) -> set[int]:
    """Distinct card indices referenced by per-card interleave markers in the reply."""
    marked: set[int] = set()
    for m in CARD_MARKER.finditer(reply):
        token = m.group()
        colon = token.index(":")
        close = token.rindex("]") - 1  # first of the two ]] — slice [:close]
        try:
            marked.add(int(token[colon + 1 : close].strip()))
        except ValueError:
            continue
    return marked


def ensure_cards(reply: str | None, cards: list[dict] | None) -> str:
    """cards: {empty, input} in catalog order. Trust when distinct marker set covers every
    non-empty card index; otherwise strip model markers and reinsert."""
    if reply is None or not str(reply).strip():
        return "" if reply is None else reply
    if not cards:
        return reply
    if not looks_like_how_to_get(reply):
        return reply
    output_indices = collect_output_quest_indices(cards)
    input_indices = collect_input_indices(cards)
    needed = set(output_indices) | set(input_indices)
    if reply_has_interleaved_markers(reply):
        marked = marked_card_indices(reply)
        if needed <= marked:
            # Model already interleaved markers after method lines; distinct marker set covers
            # every non-empty card index — trust it. Repair when coverage is short / piled /
            # trailing after source.
            return reply
        # Partial trust applies only when markers are clean (no duplicates) but some cards are
        # missing: keep the reply as-is and insert ONLY the missing cards (don't strip +
        # re-cluster everything, which used to move correctly-placed markers below trailing
        # prose). Smoke 05:21 2026-09-05: model wrote 0/1/2/4, card 3 (立方捕手, not mentioned
        # in text) was missing. Duplicate markers (raw > distinct) mean the model garbled the
        # reply — fall through to full strip + re-cluster below.
        if len([m for m in CARD_MARKER.finditer(reply)]) == len(marked):
            missing_input = [i for i in input_indices if i not in marked]
            missing_output = [i for i in output_indices if i not in marked]
            result = reply
            if missing_output:
                mo = try_insert_after_methods_sectioned(result, missing_output, 0)  # GET
                if mo is not None:
                    result = mo
            if missing_input:
                mi = try_insert_after_material_use_method(result, missing_input)
                if mi is not None:
                    result = mi
            return result
    stripped = strip_markers(reply)
    if not output_indices and not input_indices:
        return stripped

    result = stripped
    pending_append: list[int] = []

    if output_indices:
        mi = try_insert_after_methods_sectioned(result, output_indices, 0)  # GET
        if mi is not None:
            result = mi
        else:
            pending_append.extend(output_indices)
    if input_indices:
        # USE 卡聚喺材料 method 而非逐 method 配對（method lines 可能係工具用法）
        mi = try_insert_after_material_use_method(result, input_indices)
        if mi is not None:
            result = mi
        else:
            pending_append.extend(input_indices)
    return result if not pending_append else append_at_end(result, pending_append)


def test_behavior() -> None:
    how = "怎样来:\n1. 工作台:\n3铁锭+2木棍。"
    mixed = [
        {"empty": False, "input": False},  # 0 output
        {"empty": False, "input": False},  # 1 quest-as-output
        {"empty": False, "input": True},  # 2 input — append end
        {"empty": True, "input": False},  # 3 empty — skip
    ]
    filled = ensure_cards(how, mixed)
    assert "[[recipe_card:0]]" in filled
    assert filled.index("[[recipe_card:0]]") > filled.index("3铁锭+2木棍")
    assert "[[recipe_card:1]]" in filled  # remaining output appended
    assert "[[recipe_card:2]]" in filled  # input appended (no USE section)
    assert "[[recipe_card:3]]" not in filled
    assert filled.index("[[recipe_card:1]]") > filled.index("3铁锭+2木棍")
    assert filled.rstrip().endswith("[[recipe_card:2]]")

    two_methods = (
        "怎样来:\n"
        "1. 工作台:\n"
        "3 个铁锭 + 2 根木棍合成。\n"
        "2. 动力合成器:\n"
        "同样材料可自动合成。"
    )
    two_filled = ensure_cards(two_methods, mixed)
    assert "[[recipe_card:0]]" in two_filled and "[[recipe_card:1]]" in two_filled
    assert two_filled.index("[[recipe_card:0]]") > two_filled.index("3 个铁锭 + 2 根木棍合成")
    assert two_filled.index("[[recipe_card:1]]") > two_filled.index("同样材料可自动合成")
    assert two_filled.index("2. 动力合成器:") > two_filled.index("[[recipe_card:0]]")
    assert two_filled.rstrip().endswith("[[recipe_card:2]]")

    # Model markers piled at end — strip; output→GET, input→USE
    model_piled = (
        "怎样来:\n"
        "1. 工作台:\n"
        "下界合金碎片 + 龙息 + 恶魂之泪合成获得。\n"
        "怎么用:\n"
        "1. 手持使用（右键）后...\n"
        "2. 作为材料：用于召唤祭坛的仪式...\n"
        "[[recipe_card:0]] [[recipe_card:2]]"
    )
    piled_filled = ensure_cards(model_piled, mixed)
    assert "[[recipe_card:0]]" in piled_filled
    assert piled_filled.index("[[recipe_card:0]]") > piled_filled.index("下界合金碎片")
    assert piled_filled.index("[[recipe_card:0]]") < piled_filled.index("怎么用:")
    # input card2 follows the USE-section method line ("作为材料：..." — the only one
    # with a method colon; "手持使用（右键）后..." has no colon so is NOT a METHOD_LINE)
    assert piled_filled.index("[[recipe_card:2]]") > piled_filled.index("作为材料")
    assert "[[recipe_card:2]]" not in piled_filled.split("[[recipe_card:0]]")[0]
    # leftover output card1 injects into last GET method block (not append-at-end)
    assert piled_filled.index("[[recipe_card:1]]") > piled_filled.index("下界合金碎片")
    assert piled_filled.index("[[recipe_card:1]]") < piled_filled.index("怎么用:")
    assert piled_filled.count("[[recipe_card:0]]") == 1
    assert piled_filled.count("[[recipe_card:2]]") == 1

    # Single marker is NOT interleaved (needs >=2 markers), so the fallback reinserts all cards — same result for one GET method.
    already_correct = how + "\n[[recipe_card:0]]"
    reinserted = ensure_cards(already_correct, mixed)
    assert "[[recipe_card:0]]" in reinserted
    assert reinserted.index("[[recipe_card:0]]") > reinserted.index("3铁锭+2木棍")
    assert reinserted.index("[[recipe_card:1]]") > reinserted.index("3铁锭+2木棍")
    assert reinserted.rstrip().endswith("[[recipe_card:2]]")

    # Round-4 compliant: model interleaves EVERY marker after its method line — ensureCards must
    # NOT strip/re-cluster them (input cards were being dumped to the tail before Fix E).
    interleaved_reply = (
        "怎样来:\n1. 工作台:\n3 个铁锭 + 2 根木棍直接合成。\n[[recipe_card:0]]\n"
        "2. 动力合成器:\n同样的材料可自动合成。\n[[recipe_card:1]]\n\n"
        "怎么用:\n1. 手持挖掘：耐久 250，主手挖掘工具。\n"
        "2. 作为材料：参与合成 堂吉诃德（需弓、铁斧、钓鱼竿、铁镐…）。\n[[recipe_card:2]]\n"
        "3. 作为材料：参与合成 立方捕手（…）。\n[[recipe_card:3]]\n"
        "4. 作为材料：参与合成 初学者法术书（需书、铁铲、铁镐、铁斧、铁剑）。\n[[recipe_card:4]]\n\n"
        "本整合包索引未另列铁镐的掉落／交易／任务获取途径。\n\n【来源】JEI 配方卡、整合包本地配方索引\n"
    )
    interleaved_cards = [
        {"empty": False, "input": False},
        {"empty": False, "input": False},
        {"empty": False, "input": True},
        {"empty": False, "input": True},
        {"empty": False, "input": True},
    ]
    kept = ensure_cards(interleaved_reply, interleaved_cards)
    assert kept == interleaved_reply  # unchanged — model placement trusted
    assert kept.index("[[recipe_card:2]]") < kept.index("立方捕手")
    assert kept.index("[[recipe_card:3]]") < kept.index("初学者法术书")
    assert kept.index("[[recipe_card:4]]") < kept.index("本整合包索引")

    # Round-5 bullet form: model writes "作为材料" material recipes as "- xxx一起合成「yyy」。"
    # bullet lines (NO "N." prefix, NO method colon) each followed by a card marker. These
    # content separators must be trusted too — fallback used to dump every marker to the tail.
    bullet_interleaved = (
        "怎样来:\n1. 工作台:\n3 个铁锭 + 2 根木棍直接合成。\n[[recipe_card:0]]\n"
        "2. 动力合成器:\n同样材料可自动合成。\n[[recipe_card:1]]\n\n"
        "怎么用:\n1. 挖掘工具:耐久 250，主手挖掘工具。\n"
        "2. 作为材料:\n"
        "- 与弓、铁斧、钓鱼竿、神秘典籍、铁锹、熔岩桶、铁锄、末地水晶一起合成「堂吉诃德」。\n[[recipe_card:2]]\n"
        "- 与末影人的头、狂暴之斧、线、掘墓者之铲一起合成「立方捕手」。\n[[recipe_card:3]]\n"
        "- 与书、铁锹、铁斧、铁剑一起合成「初学者法术书」。\n[[recipe_card:4]]\n\n"
        "本整合包索引未另列铁镐的掉落／交易／任务获取途径。\n\n【来源】JEI 配方卡、整合包本地配方索引\n"
    )
    bullet_cards = [
        {"empty": False, "input": False},
        {"empty": False, "input": False},
        {"empty": False, "input": True},
        {"empty": False, "input": True},
        {"empty": False, "input": True},
    ]
    bullet_kept = ensure_cards(bullet_interleaved, bullet_cards)
    assert bullet_kept == bullet_interleaved  # unchanged — bullet-form placement trusted
    assert bullet_kept.index("[[recipe_card:2]]") < bullet_kept.index("立方捕手")
    assert bullet_kept.index("[[recipe_card:3]]") < bullet_kept.index("初学者法术书")
    assert bullet_kept.count("[[recipe_card:2]]") == 1
    assert bullet_kept.count("[[recipe_card:3]]") == 1
    assert bullet_kept.count("[[recipe_card:4]]") == 1

    # Coverage gate: model wrote GET markers (0/1) but NO USE/material markers while 5
    # non-empty cards exist → trust gate must NOT trust (2/5 coverage); fallback must fill
    # markers 2/3/4 so input cards aren't dumped below the text (smoke 04:33 2026-09-05).
    missing_use_markers = (
        "怎样来:\n1. 工作台:\n3 个铁锭 + 2 根木棍直接合成。\n[[recipe_card:0]]\n"
        "2. 动力合成器:\n同样材料可自动合成。\n[[recipe_card:1]]\n\n"
        "怎么用:\n1. 挖掘工具:耐久 250，主手挖掘工具。\n"
        "2. 作为材料：参与合成 堂吉诃德（需弓、铁斧、铁镐、末地水晶）。\n"
        "3. 作为材料：参与合成 立方捕手（需末影人的头、狂暴之斧、线）。\n"
        "4. 作为材料：参与合成 初学者法术书（需书、铁锹、铁斧、铁剑）。\n\n"
        "本整合包索引未另列铁镐的掉落／交易／任务获取途径。\n\n"
        "【来源】JEI 配方卡、整合包本地配方索引\n"
    )
    five_cards = [
        {"empty": False, "input": False},
        {"empty": False, "input": False},
        {"empty": False, "input": True},
        {"empty": False, "input": True},
        {"empty": False, "input": True},
    ]
    fixed = ensure_cards(missing_use_markers, five_cards)
    assert fixed.count("[[recipe_card:2]]") == 1
    assert fixed.count("[[recipe_card:3]]") == 1
    assert fixed.count("[[recipe_card:4]]") == 1
    assert fixed.index("[[recipe_card:2]]") < fixed.index("【来源】")  # before source boundary
    assert fixed.index("[[recipe_card:3]]") < fixed.index("【来源】")
    assert fixed.index("[[recipe_card:4]]") < fixed.index("【来源】")
    assert "立方捕手" in fixed and "初学者法术书" in fixed  # text kept

    # Partial trust (smoke 05:21 2026-09-05): model wrote GET markers 0/1 AND USE markers
    # 2/4 correctly interleaved but did NOT mention card 3 (立方捕手) anywhere. Coverage is
    # short (4/5) so we must NOT full-trust, but we must NOT strip + re-cluster either (that
    # moved the correctly-placed 2/4 below the trailing prose). Keep reply, insert only the
    # missing card 3 after the last material-use method line — BEFORE the trailing prose.
    partial_interleaved = (
        "怎样来:\n1. 工作台:\n3 个铁锭 + 2 根木棍直接合成。\n[[recipe_card:0]]\n"
        "2. 动力合成器:\n同样材料可自动合成。\n[[recipe_card:1]]\n\n"
        "怎么用:\n1. 挖掘工具:耐久 250，主手挖掘工具。\n"
        "2. 作为材料：参与合成 堂吉诃德（需弓、铁斧、铁镐、末地水晶）。\n[[recipe_card:2]]\n"
        "3. 作为材料：参与合成 初学者法术书（需书、铁锹、铁斧、铁剑）。\n[[recipe_card:4]]\n\n"
        "本包无额外掉落／任务途径记录，主要靠合成取得。\n\n"
        "【来源】JEI、整合包任务册或本地配方、物品 tooltip／PURPOSE\n"
    )
    partial_fixed = ensure_cards(partial_interleaved, five_cards)
    # correctly-placed 2/4 stay where the model put them (before prose)
    assert partial_fixed.index("[[recipe_card:2]]") < partial_fixed.index("本包无额外掉落")
    assert partial_fixed.index("[[recipe_card:4]]") < partial_fixed.index("本包无额外掉落")
    # missing 3 is inserted after the last material-use method, still before prose+source
    assert partial_fixed.count("[[recipe_card:3]]") == 1
    assert partial_fixed.index("[[recipe_card:3]]") < partial_fixed.index("本包无额外掉落")
    assert partial_fixed.index("[[recipe_card:3]]") < partial_fixed.index("【来源】")
    # no card marker may end up inside the trailing prose block (prose→source)
    prose_idx = partial_fixed.index("本包无额外掉落")
    src_idx = partial_fixed.index("【来源】")
    for mm in CARD_MARKER.finditer(partial_fixed):
        assert mm.start() < prose_idx or mm.start() >= src_idx, (
            "card marker must not sit inside trailing prose"
        )

    # Duplicate-marker hole: 5 raw markers but distinct set {0,1,2,3} — card 4 unmarked.
    # Raw-count gate would trust (5>=5); distinct-set gate must fall back and fill card 4.
    # Dup [[recipe_card:0]] sits after USE bullet-1 prose (content separator) so interleaved
    # gate passes; only the set-coverage hole forces fallback.
    dup_markers = (
        "怎样来:\n1. 工作台:\n3 个铁锭 + 2 根木棍直接合成。\n[[recipe_card:0]]\n"
        "2. 动力合成器:\n同样材料可自动合成。\n[[recipe_card:1]]\n\n"
        "怎么用:\n1. 挖掘工具:耐久 250，主手挖掘工具。\n[[recipe_card:0]]\n"
        "2. 作为材料：参与合成 堂吉诃德（需弓、铁斧、铁镐、末地水晶）。\n[[recipe_card:2]]\n"
        "3. 作为材料：参与合成 立方捕手（需末影人的头、狂暴之斧、线）。\n[[recipe_card:3]]\n"
        "4. 作为材料：参与合成 初学者法术书（需书、铁锹、铁斧、铁剑）。\n\n"
        "本整合包索引未另列铁镐的掉落／交易／任务获取途径。\n\n"
        "【来源】JEI 配方卡、整合包本地配方索引\n"
    )
    dup_cards = [
        {"empty": False, "input": False},
        {"empty": False, "input": False},
        {"empty": False, "input": True},
        {"empty": False, "input": True},
        {"empty": False, "input": True},
    ]
    dup_fixed = ensure_cards(dup_markers, dup_cards)
    assert dup_fixed.count("[[recipe_card:4]]") == 1
    assert dup_fixed.index("[[recipe_card:4]]") < dup_fixed.index("【来源】")
    assert dup_fixed.count("[[recipe_card:0]]") == 1  # duplicate stripped by fallback

    # v2: mostly-interleaved reply with ONE extra marker strictly AFTER 【来源】 must NOT be
    # trusted — condition (b) rejects via after-source; fallback strips and re-inserts ALL
    # cards (including the stray one) BEFORE the source boundary.
    base = interleaved_reply.rstrip()
    assert base.endswith("【来源】JEI 配方卡、整合包本地配方索引") or "【来源】" in base
    stray_after_source = base + "\n[[recipe_card:4]]\n"
    normalized = ensure_cards(stray_after_source, interleaved_cards)
    src_idx = normalized.index("【来源】")
    for mm in CARD_MARKER.finditer(normalized):
        assert mm.start() < src_idx, "card marker must stay before source header"
    assert normalized.count("[[recipe_card:") == len(interleaved_cards)

    # v2: a single marker piled after the source header is repaired too.
    piled_tail = "怎样来:\n1. 工作台:\n3 铁锭 + 2 木棍。\n\n【来源】JEI\n[[recipe_card:0]]\n"
    fixed = ensure_cards(piled_tail, [{"empty": False, "input": False}])
    assert fixed.index("[[recipe_card:0]]") < fixed.index("【来源】")

    purpose_only = "怎么用:\n1. 手持挖掘。"
    assert ensure_cards(purpose_only, []) == purpose_only
    purpose_filled = ensure_cards(purpose_only, mixed)
    # "1. 手持挖掘。" has no method colon → no USE method spans; all cards append
    assert purpose_filled.rstrip().endswith("[[recipe_card:0]] [[recipe_card:1]] [[recipe_card:2]]")

    purpose_piled = (
        "怎么用:\n"
        "1. 作为材料:\n"
        "用于召唤祭坛仪式。\n"
        "[[recipe_card:0]] [[recipe_card:2]]"
    )
    purpose_piled_filled = ensure_cards(purpose_piled, mixed)
    # input→USE method; outputs append (no GET)
    assert purpose_piled_filled.index("[[recipe_card:2]]") > purpose_piled_filled.index("用于召唤祭坛仪式")
    assert purpose_piled_filled.rstrip().endswith("[[recipe_card:0]] [[recipe_card:1]]")
    assert purpose_piled_filled.count("[[recipe_card:2]]") == 1

    prose_no_section = "控制机器需要红石信号。"
    assert ensure_cards(prose_no_section, mixed) == prose_no_section

    no_method = "怎样来:\n在工作台用铁锭和木棍合成。"
    no_method_filled = ensure_cards(no_method, [{"empty": False, "input": False}])
    assert no_method_filled.endswith("[[recipe_card:0]]")

    input_only = "怎样来:\n在工作台用铁锭和木棍合成。"
    input_only_filled = ensure_cards(
        input_only,
        [{"empty": False, "input": True}],
    )
    assert input_only_filled.endswith("[[recipe_card:0]]")

    assert ensure_cards(how, []) == how
    assert ensure_cards(how, None) == how
    assert ensure_cards("", mixed) == ""
    assert ensure_cards(None, mixed) == ""

    en = "How to get:\n1. Crafting Table:\nsticks + iron."
    en_filled = ensure_cards(en, [{"empty": False, "input": False}])
    assert "[[recipe_card:0]]" in en_filled
    assert en_filled.index("[[recipe_card:0]]") > en_filled.index("sticks + iron")

    # Full-width colon (U+FF1A) on method lines — DeepSeek Chinese replies
    assert count_method_lines("1. 工作台：") >= 1
    assert count_method_lines("1. 作为暗影之书材料：在暗影之书合成暗影之书。") >= 1
    fw_method_only = "怎样来:\n1. 工作台：\n铁锭×3 + 木棍×2 直接合成铁镐。"
    assert count_method_lines(fw_method_only) >= 1
    fw_filled = ensure_cards(fw_method_only, [{"empty": False, "input": False}])
    assert "[[recipe_card:0]]" in fw_filled
    assert fw_filled.index("[[recipe_card:0]]") > fw_filled.index("铁锭×3 + 木棍×2 直接合成铁镐")
    before_card, _, _ = fw_filled.partition("[[recipe_card:0]]")
    assert "1. 工作台：" in before_card and "铁锭×3" in before_card

    fw_two = (
        "怎样来:\n"
        "1. 工作台：\n"
        "铁锭×3 + 木棍×2 直接合成铁镐。\n"
        "2. 动力合成器：\n"
        "同样材料可自动合成。"
    )
    fw_two_filled = ensure_cards(fw_two, mixed)
    assert count_method_lines(fw_two) >= 2
    assert fw_two_filled.index("[[recipe_card:0]]") > fw_two_filled.index("铁锭×3 + 木棍×2 直接合成铁镐")
    assert fw_two_filled.index("[[recipe_card:1]]") > fw_two_filled.index("同样材料可自动合成")
    assert fw_two_filled.index("2. 动力合成器：") > fw_two_filled.index("[[recipe_card:0]]")
    assert is_section_title("怎样来：")
    assert is_section_title("怎样来:")

    # input-use cards cluster at material-use method (not one-per-method / not append-at-end)
    input_use_reply = (
        "怎么用：\n"
        "1. 作为暗影之书材料：在暗影之书合成暗影之书。\n"
        "2. 动力合成器：同样材料自动合成。"
    )
    input_use_cards = [
        {"empty": False, "input": True},
        {"empty": False, "input": True},
    ]
    input_use_filled = ensure_cards(input_use_reply, input_use_cards)
    assert "[[recipe_card:0]]" in input_use_filled
    assert "[[recipe_card:1]]" in input_use_filled
    assert input_use_filled.index("[[recipe_card:0]]") > input_use_filled.index("作为暗影之书材料")
    assert input_use_filled.index("[[recipe_card:0]]") < input_use_filled.index("2. 动力合成器")
    assert input_use_filled.index("[[recipe_card:1]]") > input_use_filled.index("作为暗影之书材料")
    assert input_use_filled.index("[[recipe_card:1]]") < input_use_filled.index("2. 动力合成器")
    assert input_use_filled.index("[[recipe_card:0]]") < input_use_filled.index("[[recipe_card:1]]")
    assert not input_use_filled.rstrip().endswith("[[recipe_card:0]] [[recipe_card:1]]")

    # --- section-aware regression (reviewer merged-pool misplacement) ---
    # USE before GET: output→GET 工作台, input→USE 作为材料 (NOT swapped)
    mixed_sections = (
        "怎么用：\n"
        "1. 作为材料：用于召唤祭坛。\n"
        "怎样来：\n"
        "1. 工作台：合成。"
    )
    mixed_cards = [
        {"empty": False, "input": False},  # 0 output
        {"empty": False, "input": True},  # 1 input
    ]
    mixed_filled = ensure_cards(mixed_sections, mixed_cards)
    assert mixed_filled.index("[[recipe_card:0]]") > mixed_filled.index("工作台")
    assert mixed_filled.index("[[recipe_card:0]]") > mixed_filled.index("合成")
    assert mixed_filled.index("[[recipe_card:1]]") > mixed_filled.index("作为材料")
    assert mixed_filled.index("[[recipe_card:1]]") > mixed_filled.index("用于召唤祭坛")
    assert mixed_filled.index("[[recipe_card:1]]") < mixed_filled.index("怎样来")
    assert mixed_filled.index("[[recipe_card:0]]") > mixed_filled.index("怎样来")

    # GET-only: both outputs follow GET method lines
    get_only = (
        "怎样来：\n"
        "1. 工作台：铁锭×3 合成。\n"
        "2. 动力合成器：自动合成。"
    )
    get_cards = [
        {"empty": False, "input": False},
        {"empty": False, "input": False},
    ]
    get_filled = ensure_cards(get_only, get_cards)
    assert get_filled.index("[[recipe_card:0]]") > get_filled.index("铁锭×3 合成")
    assert get_filled.index("[[recipe_card:1]]") > get_filled.index("自动合成")
    assert get_filled.index("2. 动力合成器") > get_filled.index("[[recipe_card:0]]")

    # USE-only: both inputs cluster at material-use method (before non-material method 2)
    use_only = (
        "怎么用：\n"
        "1. 作为暗影之书材料：在暗影之书合成。\n"
        "2. 动力合成器：自动合成。"
    )
    use_cards = [
        {"empty": False, "input": True},
        {"empty": False, "input": True},
    ]
    use_filled = ensure_cards(use_only, use_cards)
    assert use_filled.index("[[recipe_card:0]]") > use_filled.index("作为暗影之书材料")
    assert use_filled.index("[[recipe_card:0]]") < use_filled.index("2. 动力合成器")
    assert use_filled.index("[[recipe_card:1]]") > use_filled.index("作为暗影之书材料")
    assert use_filled.index("[[recipe_card:1]]") < use_filled.index("2. 动力合成器")

    # 【来源】 boundary: input cards must stay BEFORE source section (NFWC 硫磺花蜜 repro)
    source_boundary_reply = (
        "怎样来:\n"
        "1. 工作台:\n"
        "下界合金碎片 + 龙息 + 恶魂之泪直接合成。\n\n"
        "怎么用:\n"
        "1. 使用后重置附近的 BOSS 生成结构...\n"
        "2. 作为材料（召唤祭坛）：\n"
        "   - 硫磺花蜜、充能末影珍珠 -> 奥秘·领主。\n\n"
        "【来源】JEI、整合包任务书或本地配方...\n"
    )
    source_boundary_cards = [
        {"empty": False, "input": False},  # 0 output -> GET 工作台
        {"empty": False, "input": True},  # 1 input -> USE 作为材料
        {"empty": False, "input": True},  # 2 input -> USE leftover at last method block
    ]
    source_boundary_filled = ensure_cards(source_boundary_reply, source_boundary_cards)
    source_idx = source_boundary_filled.index("【来源】")
    assert source_boundary_filled.index("[[recipe_card:0]]") > source_boundary_filled.index("工作台")
    assert source_boundary_filled.index("[[recipe_card:1]]") < source_idx
    assert source_boundary_filled.index("[[recipe_card:2]]") < source_idx
    assert source_boundary_filled.index("[[recipe_card:1]]") > source_boundary_filled.index("作为材料")
    assert source_boundary_filled.index("[[recipe_card:2]]") > source_boundary_filled.index("作为材料")
    assert source_boundary_filled.index("[[recipe_card:1]]") < source_boundary_filled.index("[[recipe_card:2]]")
    assert is_section_title("【来源】JEI、整合包任务书")

    # Leftover order: 1 USE method line, 2 input-use cards — card0 before card1, both before 【来源】
    leftover_order_reply = (
        "怎么用:\n"
        "1. 作为材料（召唤祭坛）：\n"
        "   - 硫磺花蜜、充能末影珍珠 -> 奥秘·领主。\n\n"
        "【来源】JEI、整合包任务书\n"
    )
    leftover_order_cards = [
        {"empty": False, "input": True},
        {"empty": False, "input": True},
    ]
    leftover_order_filled = ensure_cards(leftover_order_reply, leftover_order_cards)
    leftover_source_idx = leftover_order_filled.index("【来源】")
    assert leftover_order_filled.index("[[recipe_card:0]]") < leftover_order_filled.index("[[recipe_card:1]]")
    assert leftover_order_filled.index("[[recipe_card:0]]") < leftover_source_idx
    assert leftover_order_filled.index("[[recipe_card:1]]") < leftover_source_idx
    assert leftover_order_filled.index("[[recipe_card:0]]") > leftover_order_filled.index("作为材料")
    assert leftover_order_filled.index("[[recipe_card:1]]") > leftover_order_filled.index("作为材料")

    # Method line AFTER 【来源】 must NOT host a card (non-typed boundary closes typed section)
    method_after_source = (
        "怎样来:\n"
        "1. 工作台:\n"
        "铁锭合成。\n\n"
        "【来源】JEI\n"
        "2. 动力合成器:\n"
        "自动合成。"
    )
    method_after_source_cards = [
        {"empty": False, "input": False},
        {"empty": False, "input": False},
    ]
    method_after_source_filled = ensure_cards(method_after_source, method_after_source_cards)
    source_pos = method_after_source_filled.index("【来源】")
    assert method_after_source_filled.index("[[recipe_card:0]]") < source_pos
    assert method_after_source_filled.index("[[recipe_card:0]]") > method_after_source_filled.index("铁锭合成")
    assert "[[recipe_card:1]]" not in method_after_source_filled.split("【来源】")[1]

    # Normal: method before 【来源】 still hosts card before 来源
    method_before_source = (
        "怎样来:\n"
        "1. 工作台:\n"
        "铁锭合成。\n"
        "2. 动力合成器:\n"
        "自动合成。\n\n"
        "【来源】JEI\n"
    )
    method_before_source_filled = ensure_cards(method_before_source, method_after_source_cards)
    before_source_idx = method_before_source_filled.index("【来源】")
    assert method_before_source_filled.index("[[recipe_card:0]]") < before_source_idx
    assert method_before_source_filled.index("[[recipe_card:1]]") < before_source_idx
    assert method_before_source_filled.index("[[recipe_card:0]]") > method_before_source_filled.index("铁锭合成")
    assert method_before_source_filled.index("[[recipe_card:1]]") > method_before_source_filled.index("自动合成")

    # 怎样用 heading (zh 書面語 variant — deepseek model 真實輸出, 2026-09-04 repro)
    # USE_SECTION_PREFIXES must recognise 怎样用 so input cards land in USE section,
    # output cards keep GET method-line interleave.
    zheyang_use_reply = (
        "怎样来:\n"
        "1. 工作台:\n"
        "3 个铁锭 + 2 根木棍直接合成。\n"
        "2. 动力合成器:\n"
        "同样材料可自动合成。\n\n"
        "怎样用:\n"
        "1. 手持挖掘：主手挖掘工具。\n"
        "2. 作为材料：做初学者法术书。\n\n"
        "【来源】JEI\n"
    )
    zheyang_use_cards = [
        {"empty": False, "input": False},  # 0 output -> GET 1. 工作台
        {"empty": False, "input": False},  # 1 output -> GET 2. 动力合成器
        {"empty": False, "input": True},   # 2 input -> USE (怎样用 section)
    ]
    zheyang_filled = ensure_cards(zheyang_use_reply, zheyang_use_cards)
    assert zheyang_filled.index("[[recipe_card:0]]") > zheyang_filled.index("工作台")
    assert zheyang_filled.index("[[recipe_card:0]]") < zheyang_filled.index("2. 动力合成器")
    assert zheyang_filled.index("[[recipe_card:1]]") > zheyang_filled.index("动力合成器")
    assert zheyang_filled.index("[[recipe_card:1]]") < zheyang_filled.index("怎样用")
    assert zheyang_filled.index("[[recipe_card:2]]") > zheyang_filled.index("怎样用")
    assert zheyang_filled.index("[[recipe_card:2]]") < zheyang_filled.index("【来源】")
    assert zheyang_filled.index("[[recipe_card:2]]") > zheyang_filled.index("作为材料")
    assert zheyang_filled.index("[[recipe_card:2]]") > zheyang_filled.index("手持挖掘")

    # Fix D: all input cards cluster at material-use method (not tool-usage methods)
    cluster_reply = (
        "怎么用:\n"
        "1. 挖掘：挖石头...\n"
        "2. 作为材料／工具 与多种配方：...\n"
        "3. 可当工具使用：砧板...\n"
    )
    cluster_cards = [
        {"empty": False, "input": True},
        {"empty": False, "input": True},
        {"empty": False, "input": True},
    ]
    cluster_filled = ensure_cards(cluster_reply, cluster_cards)
    assert cluster_filled.index("[[recipe_card:0]]") > cluster_filled.index("作为材料")
    assert cluster_filled.index("[[recipe_card:1]]") > cluster_filled.index("作为材料")
    assert cluster_filled.index("[[recipe_card:2]]") > cluster_filled.index("作为材料")
    assert cluster_filled.index("[[recipe_card:0]]") < cluster_filled.index("3. 可当工具使用")
    assert cluster_filled.index("[[recipe_card:2]]") < cluster_filled.index("3. 可当工具使用")
    assert cluster_filled.index("1. 挖掘") < cluster_filled.index("[[recipe_card:0]]")

    # No material method → last USE method block
    no_mat = ensure_cards(
        "怎么用:\n1. 手持挖掘：主手挖掘工具。\n",
        [{"empty": False, "input": True}],
    )
    assert no_mat.index("[[recipe_card:0]]") > no_mat.index("手持挖掘")

    # No USE method line → pendingAppend
    no_method = ensure_cards(
        "怎么用:\n随便用。\n",
        [{"empty": False, "input": True}],
    )
    assert no_method.rstrip().endswith("[[recipe_card:0]]")


def check_source(path: Path) -> None:
    src = path.read_text(encoding="utf-8")
    assert "ensureCards" in src, f"{path}: missing ensureCards"
    assert "[[recipe_card:" in src, f"{path}: missing recipe_card marker"
    assert "collectInputIndices" in src, f"{path}: must append input cards"
    assert "stripMarkers" in src, f"{path}: must strip model markers"
    assert "CARD_MARKER" in src, f"{path}: missing marker strip pattern"
    assert "looksLikeHowToGet" in src, f"{path}: missing how-to-get gate"
    assert "tryInsertAfterMethodsSectioned" in src, f"{path}: missing sectioned method-line insertion"
    assert "tryInsertAfterMaterialUseMethod" in src, f"{path}: missing material-use cluster insert"
    assert "GET_SECTION_PREFIXES" in src, f"{path}: missing GET section prefixes"
    assert "USE_SECTION_PREFIXES" in src, f"{path}: missing USE section prefixes"
    assert "sectionTypeOf" in src, f"{path}: missing sectionTypeOf"
    assert "replyContainsInterleavedMarkers" in src, f"{path}: missing interleaved trust gate"
    assert "firstSourceHeaderIndex" in src, f"{path}: missing source-boundary gate helper"
    assert "fully manages" in src.lower() or "mod fully" in src.lower()


def check_wiring(path: Path) -> None:
    src = path.read_text(encoding="utf-8")
    assert src.count("AskCardFallback.ensureCards") >= 2, f"{path}: wire both ask paths"
    idx = src.find("AskCardFallback.ensureCards")
    gate = src.find("RecipeCardsMode.resolveGateMarker", idx)
    assert gate > idx, f"{path}: ensureCards must run before resolveGateMarker"


def assert_dual_tree_identical(paths: tuple[Path, ...]) -> None:
    """Forge / neoforge source must stay byte-identical (mirror drift guard)."""
    assert len(paths) == 2, f"expected exactly 2 dual-tree paths, got {len(paths)}"
    a, b = paths
    if a.is_file() and b.is_file():
        assert a.read_text(encoding="utf-8") == b.read_text(encoding="utf-8"), (
            f"dual-tree drift: {a} != {b}"
        )


def main() -> None:
    test_behavior()
    for p in HELPER_PATHS:
        assert p.is_file(), f"missing {p}"
        check_source(p)
    for p in SERVICE_PATHS:
        assert p.is_file(), f"missing {p}"
        check_wiring(p)
    assert_dual_tree_identical(HELPER_PATHS)
    print("check_ask_card_fallback OK")


if __name__ == "__main__":
    main()
