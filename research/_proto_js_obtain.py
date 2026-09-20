# Prototype JsObtainSites vs NFWC A_hard — calibrate before Java port.
import json
import os
import re
from pathlib import Path

KUBE = Path(os.environ.get(
    "PACKAI_PRISM",
    r"C:\Users\skps9\Documents\PrismLauncher-Windows-MinGW-w64-Portable-11.1.0",
)) / "instances" / "AI_test_NFWC_DIM" / "minecraft" / "kubejs"
FIX = json.loads(
    Path("docs/plans/fixtures/2026-09-16_nfwc_js_obtain_inventory.json").read_text(
        encoding="utf-8"
    )
)

ID = r"[a-z0-9_]+:[a-z0-9_./-]+"
GIVE = re.compile(
    rf"\.(?:give|giveInHand|addItem|insertItem|addToInventory)\b\s*\(\s*"
    rf"(?:Item\.of\s*\(\s*)?['\"]({ID})['\"]",
    re.I,
)
LOOT = re.compile(
    rf"(?:addLoot|LootEntry|dropItem|spawnItem)\b[^\n]{{0,80}}?['\"]({ID})['\"]",
    re.I,
)
SETSLOT = re.compile(
    rf"\.(?:setStackInSlot|setItemSlot)\s*\([^,)]*,\s*(?:Item\.of\s*\(\s*)?['\"]({ID})['\"]",
    re.I,
)
TABLE = re.compile(rf"['\"]({ID})['\"]\s*:\s*(?:function|\()", re.I)
ASSIGN = re.compile(rf"\w+\s*\[\s*['\"]({ID})['\"]\s*\]\s*=\s*function", re.I)
BLOCK_RC = re.compile(rf"BlockEvents\.rightClicked\s*\(\s*['\"]({ID})['\"]", re.I)
ITEM_RC = re.compile(
    rf"ItemEvents\.(?:rightClicked|firstRightClicked)\s*\(\s*['\"]({ID})['\"]", re.I
)
ENT_DEATH = re.compile(rf"EntityEvents\.death\s*\(\s*['\"]({ID})['\"]", re.I)
HELD = [
    (re.compile(rf"\bitem\s*==\s*['\"]({ID})['\"]", re.I), "mainhand"),
    (
        re.compile(
            rf"getMainHandItem\s*\(\s*\)\s*==\s*(?:Item\.of\s*\(\s*)?['\"]({ID})['\"]",
            re.I,
        ),
        "mainhand",
    ),
    (
        re.compile(
            rf"getOffHandItem\s*\(\s*\)\s*==\s*(?:Item\.of\s*\(\s*)?['\"]({ID})['\"]",
            re.I,
        ),
        "offhand",
    ),
    (re.compile(rf"mainitem\s*\?\.\s*id\s*==\s*['\"]({ID})['\"]", re.I), "mainhand"),
    (
        re.compile(
            rf"mainHandItem\s*==\s*(?:Item\.of\s*\(\s*)?['\"]({ID})['\"]",
            re.I,
        ),
        "mainhand",
    ),
]
BODY_GATE = [
    re.compile(
        rf"getPlayerChestCavityItemMap\s*\([^)]*\)\s*\.has\s*\(\s*['\"]({ID})['\"]",
        re.I,
    ),
    re.compile(rf"\bitemMap\.has\s*\(\s*['\"]({ID})['\"]", re.I),
    re.compile(
        rf"organScores\.get\s*\(\s*new\s+ResourceLocation\s*\(\s*['\"]chestcavity['\"]\s*,\s*['\"]([^'\"]+)['\"]",
        re.I,
    ),
]


def line_of(text: str, pos: int) -> int:
    return text.count("\n", 0, pos) + 1


def extract_brace(text: str, from_pos: int, limit: int = 6000):
    brace = text.find("{", from_pos)
    if brace < 0 or brace - from_pos > 120:
        return None, -1
    depth = 0
    i = brace
    end = min(len(text), brace + limit)
    in_s = None
    esc = False
    while i < end:
        c = text[i]
        if in_s:
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == in_s:
                in_s = None
            i += 1
            continue
        if c in "'\"`":
            in_s = c
            i += 1
            continue
        if c == "/" and i + 1 < end:
            if text[i + 1] == "/":
                nl = text.find("\n", i)
                if nl < 0:
                    break
                i = nl
                continue
            if text[i + 1] == "*":
                j = text.find("*/", i + 2)
                i = (j + 2) if j >= 0 else end
                continue
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return text[brace : i + 1], brace
        i += 1
    return text[brace:end], brace


def brace_end(text: str, brace: int) -> int:
    depth = 0
    i = brace
    in_s = None
    esc = False
    while i < len(text):
        c = text[i]
        if in_s:
            if esc:
                esc = False
            elif c == "\\":
                esc = True
            elif c == in_s:
                in_s = None
            i += 1
            continue
        if c in "'\"`":
            in_s = c
            i += 1
            continue
        if c == "/" and i + 1 < len(text):
            if text[i + 1] == "/":
                nl = text.find("\n", i)
                i = nl if nl >= 0 else len(text)
                continue
            if text[i + 1] == "*":
                j = text.find("*/", i + 2)
                i = (j + 2) if j >= 0 else len(text)
                continue
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return len(text) - 1


def enclosing_if_conds(body: str, sink_rel_pos: int):
    """Innermost-first list of `if (...)` conds whose `{...}` covers sink."""
    out = []
    for m in re.finditer(r"\bif\s*\(", body):
        j = m.end() - 1
        depth = 0
        cond_end = -1
        while j < len(body):
            if body[j] == "(":
                depth += 1
            elif body[j] == ")":
                depth -= 1
                if depth == 0:
                    cond_end = j
                    break
            j += 1
        if cond_end < 0:
            continue
        k = cond_end + 1
        while k < len(body) and body[k].isspace():
            k += 1
        if k >= len(body) or body[k] != "{":
            continue
        end = brace_end(body, k)
        if m.start() <= sink_rel_pos <= end:
            out.append((end - m.start(), body[m.start() : cond_end + 1]))
    out.sort()  # smaller span = innermost
    return [c for _, c in out]


def has_organ_param(header: str) -> bool:
    return bool(
        re.search(r"function\s*\([^)]*\borgan\b", header, re.I)
        or re.search(r"\(\s*\w+\s*,\s*organ\b", header, re.I)
    )


def parse_file(rel_kube: str):
    path = KUBE / rel_kube
    if not path.exists():
        return []
    text = path.read_text(encoding="utf-8", errors="replace")
    rel = "kubejs/" + rel_kube.replace("\\", "/")
    sites = []
    handlers = []
    for pat, kind in [
        (TABLE, "table"),
        (ASSIGN, "assign"),
        (BLOCK_RC, "block_rc"),
        (ITEM_RC, "item_rc"),
        (ENT_DEATH, "ent_death"),
    ]:
        for m in pat.finditer(text):
            handlers.append((m.start(), m.end(), m.group(1).lower(), kind))
    handlers.sort()
    for start, end, hid, hkind in handlers:
        body, brace = extract_brace(text, end - 1 if hkind in ("table", "assign") else end)
        if not body:
            continue
        entry_line = line_of(text, start)
        fn_head = text[start : brace + 80] if brace >= 0 else text[start : end + 80]
        organ_param = has_organ_param(fn_head)

        sinks = []
        for m in GIVE.finditer(body):
            sinks.append((m.start(), m.group(1).lower(), "PRODUCE"))
        for m in LOOT.finditer(body):
            sinks.append((m.start(), m.group(1).lower(), "PRODUCE"))
        for m in SETSLOT.finditer(body):
            sinks.append((m.start(), m.group(1).lower(), "TRANSFORM"))

        for spos, out_id, kind in sinks:
            if out_id == "minecraft:air":
                continue
            abs_pos = brace + spos
            line = line_of(text, abs_pos)
            conds = enclosing_if_conds(body, spos)
            cond = conds[0] if conds else ""
            held_id = held_slot = None
            for c in conds:
                for hp, slot in HELD:
                    hm = hp.search(c)
                    if hm:
                        held_id = hm.group(1).lower()
                        held_slot = slot
                        cond = c
                        break
                if held_id:
                    break
            if hkind == "block_rc" and held_id is None:
                held_id = hid
                held_slot = "trigger_block"

            organ_gate = None
            gate_kind = "NONE"
            if hkind in ("table", "assign") and organ_param:
                organ_gate = hid
                gate_kind = "TABLE_KEY"
            else:
                for bp in BODY_GATE:
                    bm = bp.search(body)
                    if bm:
                        organ_gate = bm.group(1).lower()
                        if ":" not in organ_gate:
                            organ_gate = "chestcavity:" + organ_gate
                        gate_kind = "BODY_CHECK"
                        break

            if hkind == "ent_death":
                tkind = "ENTITY_DEATH"
            elif hkind == "block_rc":
                tkind = "BLOCK_RIGHT_CLICK"
            elif hkind in ("table", "assign") and (
                "damage" in rel.lower() or "hurt" in rel.lower()
            ):
                tkind = "HURT_BY_PLAYER"
            else:
                tkind = "RIGHT_CLICK"

            sites.append(
                {
                    "outId": out_id,
                    "kind": kind,
                    "rel": rel,
                    "line": line,
                    "entryLine": entry_line,
                    "organGate": organ_gate,
                    "gateKind": gate_kind,
                    "heldItem": held_id,
                    "heldSlot": held_slot,
                    "triggerKind": tkind,
                }
            )
    return sites


def main():
    files = {meta["rel"].replace("kubejs/", "") for meta in FIX["A_hard_gate"].values()}
    all_sites = []
    for f in sorted(files):
        all_sites.extend(parse_file(f))

    want = {(s["outId"], s["rel"], s["line"]) for s in FIX["s17_sites"]}
    got = {(s["outId"], s["rel"], s["line"]) for s in all_sites if s["kind"] == "PRODUCE"}
    print("MATCH", len(want & got), "/", len(want))
    print("MISSING", sorted(want - got))
    ah = set(FIX["A_hard_gate"])
    print("EXTRA A_hard", sorted(t for t in (got - want) if t[0] in ah))
    for oid in sorted(FIX["A_hard_gate"]):
        meta = FIX["A_hard_gate"][oid]
        hits = [s for s in all_sites if s["outId"] == oid and s["kind"] == "PRODUCE"]
        print(
            oid,
            "expect",
            meta["sink_lines"],
            "got",
            [
                (
                    s["line"],
                    s["gateKind"],
                    s["organGate"],
                    s["heldItem"],
                    s["heldSlot"],
                    s["triggerKind"],
                )
                for s in hits
            ],
        )


if __name__ == "__main__":
    main()
