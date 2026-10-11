#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Offline bytecode mechanics-number extractor (stdlib only).

Reads mod jars read-only; writes solely to --out. Values are unverified
bytecode constants — NOT game-verified.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import struct
import sys
import zipfile
from pathlib import Path
from typing import Any, Optional

SCHEMA = 1
NOTE = "from bytecode; NOT game-verified"

# Prefilter keywords (dispatch + mech_numbers mechanics approach).
MECH_RE = re.compile(
    r"(?i)(reactor|energy|per\s*tick|generation|fuel|burn|capacity|speed|heat|"
    r"\beu\b|\brf\b|\bfe\b|\brate\b)"
)

# unit_hint only from these tokens in same-class string literals.
UNIT_TOKEN_RE = re.compile(
    r"(?i)(\btick\b|\bsecond\b|eu/t|rf/t|fe/t|mb/t|\bpercent\b|%)"
)

FORBIDDEN_NET = {
    "urllib",
    "urllib.request",
    "requests",
    "http",
    "http.client",
    "httplib",
    "aiohttp",
    "socket",
}


def _norm_rel(path: str) -> str:
    return path.replace("\\", "/")


def _mod_from_jar(jar_name: str) -> str:
    base = re.sub(r"\.jar$", "", jar_name, flags=re.I)
    base = re.sub(r"-(?:forge|fabric|quilt)(?:-|$)", "-", base, flags=re.I)
    base = re.sub(r"-?\d+(?:\.\d+)+(?:[.-].*)?$", "", base)
    return base or jar_name


def unit_hint_from_strings(strings: list[str]) -> Optional[str]:
    for s in strings:
        m = UNIT_TOKEN_RE.search(s)
        if not m:
            continue
        tok = m.group(1).lower()
        if tok == "%":
            return "percent"
        return tok
    return None


# Complete fixed-size opcode lengths for 0x00–0xCA (JVM spec).
# 0 = variable-length (tableswitch / lookupswitch / wide) — handled in walker.
# 0xCB–0xFF reserved/undefined → 0 (stop as walk_truncated; never guess).
_OP_SIZE = [0] * 256
for _op in range(0x00, 0xCB):
    _OP_SIZE[_op] = 1
for _op in (
    0x10,  # bipush
    0x12,  # ldc
    0x15, 0x16, 0x17, 0x18, 0x19,  # iload..aload
    0x36, 0x37, 0x38, 0x39, 0x3A,  # istore..astore
    0xA9,  # ret
    0xBC,  # newarray (atype u1) — NOT 3
):
    _OP_SIZE[_op] = 2
for _op in (
    0x11,  # sipush
    0x13,  # ldc_w
    0x14,  # ldc2_w
    0x84,  # iinc
    0x99, 0x9A, 0x9B, 0x9C, 0x9D, 0x9E,  # ifeq..ifle
    0x9F, 0xA0, 0xA1, 0xA2, 0xA3, 0xA4,  # if_icmp*
    0xA5, 0xA6,  # if_acmpeq / if_acmpne
    0xA7, 0xA8,  # goto / jsr
    0xB2, 0xB3, 0xB4, 0xB5,  # get/put static/field
    0xB6, 0xB7, 0xB8,  # invokevirtual/special/static
    0xBB,  # new
    0xBD,  # anewarray
    0xC0,  # checkcast
    0xC1,  # instanceof
    0xC6, 0xC7,  # ifnull / ifnonnull
):
    _OP_SIZE[_op] = 3
_OP_SIZE[0xC5] = 4  # multianewarray
_OP_SIZE[0xB9] = 5  # invokeinterface
_OP_SIZE[0xBA] = 5  # invokedynamic
_OP_SIZE[0xC8] = 5  # goto_w
_OP_SIZE[0xC9] = 5  # jsr_w
_OP_SIZE[0xAA] = 0  # tableswitch
_OP_SIZE[0xAB] = 0  # lookupswitch
_OP_SIZE[0xC4] = 0  # wide
# 0xCA breakpoint stays 1; 0xCB–0xFF remain 0


class _Reader:
    __slots__ = ("b", "i", "n")

    def __init__(self, data: bytes) -> None:
        self.b = data
        self.i = 0
        self.n = len(data)

    def u1(self) -> int:
        if self.i >= self.n:
            raise ValueError("truncated")
        v = self.b[self.i]
        self.i += 1
        return v

    def u2(self) -> int:
        if self.i + 2 > self.n:
            raise ValueError("truncated")
        v = struct.unpack_from(">H", self.b, self.i)[0]
        self.i += 2
        return v

    def u4(self) -> int:
        if self.i + 4 > self.n:
            raise ValueError("truncated")
        v = struct.unpack_from(">I", self.b, self.i)[0]
        self.i += 4
        return v

    def bytes(self, n: int) -> bytes:
        if self.i + n > self.n:
            raise ValueError("truncated")
        v = self.b[self.i : self.i + n]
        self.i += n
        return v

    def skip(self, n: int) -> None:
        if self.i + n > self.n:
            raise ValueError("truncated")
        self.i += n


def _cp_utf8(cp: list[Any], idx: int) -> str:
    if idx <= 0 or idx >= len(cp):
        return ""
    e = cp[idx]
    if not e or e[0] != 1:
        return ""
    return e[1]


def _cp_class_name(cp: list[Any], idx: int) -> str:
    if idx <= 0 or idx >= len(cp):
        return ""
    e = cp[idx]
    if not e or e[0] != 7:
        return ""
    return _cp_utf8(cp, e[1]).replace("/", ".")


def _cp_field_name(cp: list[Any], fieldref_idx: int) -> str:
    if fieldref_idx <= 0 or fieldref_idx >= len(cp):
        return ""
    e = cp[fieldref_idx]
    if not e or e[0] != 9:
        return ""
    nat = cp[e[2]] if e[2] < len(cp) else None
    if not nat or nat[0] != 12:
        return ""
    return _cp_utf8(cp, nat[1])


def _cp_numeric(cp: list[Any], idx: int) -> Optional[tuple[Any, str]]:
    """Return (value, value_kind) for Integer/Float/Long/Double CP entries."""
    if idx <= 0 or idx >= len(cp):
        return None
    e = cp[idx]
    if not e:
        return None
    tag = e[0]
    if tag == 3:
        return e[1], "int"
    if tag == 4:
        return e[1], "float"
    if tag == 5:
        return e[1], "int"  # long → report as int-compatible whole number
    if tag == 6:
        return e[1], "double"
    return None


def parse_class(data: bytes) -> dict[str, Any]:
    """Parse a class file; raises ValueError on structural failure."""
    r = _Reader(data)
    if r.u4() != 0xCAFEBABE:
        raise ValueError("bad magic")
    r.u2()  # minor
    r.u2()  # major
    cp_count = r.u2()
    cp: list[Any] = [None] * cp_count
    i = 1
    while i < cp_count:
        tag = r.u1()
        if tag == 1:  # Utf8
            ln = r.u2()
            raw = r.bytes(ln)
            try:
                s = raw.decode("utf-8")
            except UnicodeDecodeError:
                s = raw.decode("utf-8", "replace")
            cp[i] = (1, s)
        elif tag == 3:  # Integer
            cp[i] = (3, struct.unpack(">i", r.bytes(4))[0])
        elif tag == 4:  # Float
            cp[i] = (4, struct.unpack(">f", r.bytes(4))[0])
        elif tag == 5:  # Long
            cp[i] = (5, struct.unpack(">q", r.bytes(8))[0])
            i += 1
        elif tag == 6:  # Double
            cp[i] = (6, struct.unpack(">d", r.bytes(8))[0])
            i += 1
        elif tag == 7:  # Class
            cp[i] = (7, r.u2())
        elif tag == 8:  # String
            cp[i] = (8, r.u2())
        elif tag in (9, 10, 11):  # Fieldref / Methodref / InterfaceMethodref
            cp[i] = (tag, r.u2(), r.u2())
        elif tag == 12:  # NameAndType
            cp[i] = (12, r.u2(), r.u2())
        elif tag == 15:  # MethodHandle
            cp[i] = (15, r.u1(), r.u2())
        elif tag == 16:  # MethodType
            cp[i] = (16, r.u2())
        elif tag in (17, 18):  # Dynamic / InvokeDynamic
            cp[i] = (tag, r.u2(), r.u2())
        elif tag in (19, 20):  # Module / Package
            cp[i] = (tag, r.u2())
        else:
            raise ValueError(f"unknown cp tag {tag}")
        i += 1

    access = r.u2()
    this_class = _cp_class_name(cp, r.u2())
    super_class = _cp_class_name(cp, r.u2())
    ifaces = [r.u2() for _ in range(r.u2())]

    def read_attributes(count: int) -> list[tuple[str, bytes]]:
        attrs: list[tuple[str, bytes]] = []
        for _ in range(count):
            name = _cp_utf8(cp, r.u2())
            ln = r.u4()
            attrs.append((name, r.bytes(ln)))
        return attrs

    fields: list[dict[str, Any]] = []
    for _ in range(r.u2()):
        f_acc = r.u2()
        f_name = _cp_utf8(cp, r.u2())
        f_desc = _cp_utf8(cp, r.u2())
        f_attrs = read_attributes(r.u2())
        const_val = None
        for an, ad in f_attrs:
            if an == "ConstantValue" and len(ad) == 2:
                idx = struct.unpack(">H", ad)[0]
                const_val = _cp_numeric(cp, idx)
        fields.append(
            {
                "access": f_acc,
                "name": f_name,
                "desc": f_desc,
                "constant": const_val,
            }
        )

    methods: list[dict[str, Any]] = []
    for _ in range(r.u2()):
        m_acc = r.u2()
        m_name = _cp_utf8(cp, r.u2())
        m_desc = _cp_utf8(cp, r.u2())
        m_attrs = read_attributes(r.u2())
        code = b""
        for an, ad in m_attrs:
            if an == "Code" and len(ad) >= 8:
                # max_stack u2, max_locals u2, code_length u4, code
                code_len = struct.unpack_from(">I", ad, 4)[0]
                code = ad[8 : 8 + code_len]
        methods.append(
            {"access": m_acc, "name": m_name, "desc": m_desc, "code": code}
        )

    # class attributes (skip body — already positioned)
    read_attributes(r.u2())

    utf8_strings = [e[1] for e in cp if e and e[0] == 1]
    str_literals: list[str] = []
    for e in cp:
        if e and e[0] == 8:
            str_literals.append(_cp_utf8(cp, e[1]))

    return {
        "access": access,
        "this": this_class,
        "super": super_class,
        "interfaces": ifaces,
        "cp": cp,
        "fields": fields,
        "methods": methods,
        "utf8": utf8_strings,
        "strings": str_literals,
    }


def class_is_mechanics_relevant(parsed: dict[str, Any]) -> bool:
    hay = " ".join(
        [parsed.get("this") or ""]
        + [f["name"] for f in parsed["fields"]]
        + parsed["utf8"]
        + parsed["strings"]
    )
    return MECH_RE.search(hay) is not None


def _abs_num(value: Any) -> Optional[float]:
    if isinstance(value, bool):
        return float(int(value))
    if isinstance(value, (int, float)):
        if isinstance(value, float) and value != value:
            return None
        return abs(float(value))
    return None


def _walk_code_numbers(
    code: bytes, cp: list[Any]
) -> tuple[list[tuple[Any, str, Optional[str]]], bool]:
    """Walk Code bytes → (hits, truncated).

    hits: (value, value_kind, field_owner_or_None).
    truncated: True if an instruction would run past len(code) or hits
    reserved/undefined opcode — never raises.
    """
    out: list[tuple[Any, str, Optional[str]]] = []
    last_num: Optional[tuple[Any, str]] = None
    i = 0
    n = len(code)
    truncated = False

    def need(nbytes: int) -> bool:
        nonlocal truncated
        if i + nbytes > n:
            truncated = True
            return False
        return True

    while i < n:
        if not need(1):
            break
        op = code[i]
        if op == 0x02:  # iconst_m1
            last_num = (-1, "int")
            out.append((-1, "int", None))
            i += 1
        elif 0x03 <= op <= 0x08:  # iconst_0..5
            v = op - 0x03
            last_num = (v, "int")
            out.append((v, "int", None))
            i += 1
        elif op == 0x09:  # lconst_0
            last_num = (0, "int")
            out.append((0, "int", None))
            i += 1
        elif op == 0x0A:  # lconst_1
            last_num = (1, "int")
            out.append((1, "int", None))
            i += 1
        elif op == 0x0B:  # fconst_0
            last_num = (0.0, "float")
            out.append((0.0, "float", None))
            i += 1
        elif op == 0x0C:  # fconst_1
            last_num = (1.0, "float")
            out.append((1.0, "float", None))
            i += 1
        elif op == 0x0D:  # fconst_2
            last_num = (2.0, "float")
            out.append((2.0, "float", None))
            i += 1
        elif op == 0x0E:  # dconst_0
            last_num = (0.0, "double")
            out.append((0.0, "double", None))
            i += 1
        elif op == 0x0F:  # dconst_1
            last_num = (1.0, "double")
            out.append((1.0, "double", None))
            i += 1
        elif op == 0x10:  # bipush
            if not need(2):
                break
            v = struct.unpack_from(">b", code, i + 1)[0]
            last_num = (v, "int")
            out.append((v, "int", None))
            i += 2
        elif op == 0x11:  # sipush
            if not need(3):
                break
            v = struct.unpack_from(">h", code, i + 1)[0]
            last_num = (v, "int")
            out.append((v, "int", None))
            i += 3
        elif op == 0x12:  # ldc
            if not need(2):
                break
            idx = code[i + 1]
            num = _cp_numeric(cp, idx)
            if num:
                last_num = num
                out.append((num[0], num[1], None))
            i += 2
        elif op in (0x13, 0x14):  # ldc_w / ldc2_w
            if not need(3):
                break
            idx = struct.unpack_from(">H", code, i + 1)[0]
            num = _cp_numeric(cp, idx)
            if num:
                last_num = num
                out.append((num[0], num[1], None))
            i += 3
        elif op in (0xB3, 0xB5):  # putstatic / putfield
            if not need(3):
                break
            idx = struct.unpack_from(">H", code, i + 1)[0]
            fname = _cp_field_name(cp, idx)
            if last_num is not None and fname:
                out.append((last_num[0], last_num[1], fname))
            i += 3
        elif op == 0xC4:  # wide: 4 bytes, or 6 if iinc
            if not need(2):
                break
            wop = code[i + 1]
            wlen = 6 if wop == 0x84 else 4
            if not need(wlen):
                break
            i += wlen
        elif op == 0xAA:  # tableswitch
            # pad to 4-byte alignment relative to code start (index 0)
            if not need(1):
                break
            j = i + 1
            pad = (4 - (j % 4)) % 4
            j += pad
            if j + 12 > n:
                truncated = True
                break
            _default, low, high = struct.unpack_from(">iii", code, j)
            j += 12
            if high < low:
                truncated = True
                break
            slots = high - low + 1
            # ponytail: reject absurd jump tables (corrupt/misaligned) rather than OOM
            if slots > (n - j) // 4 or j + slots * 4 > n:
                truncated = True
                break
            i = j + slots * 4
        elif op == 0xAB:  # lookupswitch
            if not need(1):
                break
            j = i + 1
            pad = (4 - (j % 4)) % 4
            j += pad
            if j + 8 > n:
                truncated = True
                break
            _default, npairs = struct.unpack_from(">ii", code, j)
            j += 8
            if npairs < 0 or npairs > (n - j) // 8 or j + npairs * 8 > n:
                truncated = True
                break
            i = j + npairs * 8
        else:
            sz = _OP_SIZE[op]
            if sz <= 0:
                # reserved / undefined / unhandled — stop cleanly
                truncated = True
                break
            if not need(sz):
                break
            i += sz
    return out, truncated


def extract_records(
    parsed: dict[str, Any],
    *,
    mod: str,
    jar: str,
    min_abs: float = 0.0,
    truncated_out: Optional[list[str]] = None,
) -> list[dict[str, Any]]:
    cls = parsed["this"]
    hint = unit_hint_from_strings(parsed["strings"] + parsed["utf8"])
    seen: set[tuple] = set()
    records: list[dict[str, Any]] = []

    def add(owner: str, owner_kind: str, value: Any, value_kind: str) -> None:
        # Normalize floats that are whole ints for stable JSON
        if value_kind in ("float", "double") and isinstance(value, float):
            if value != value:  # NaN
                return
        if min_abs > 0:
            aval = _abs_num(value)
            if aval is not None and aval < min_abs:
                return
        key = (cls, owner, value, value_kind)
        if key in seen:
            return
        seen.add(key)
        records.append(
            {
                "tier": "A3",
                "mod": mod,
                "class": cls,
                "owner": owner,
                "owner_kind": owner_kind,
                "value": value,
                "value_kind": value_kind,
                "jar": jar,
                "unit_hint": hint,
                "note": NOTE,
            }
        )

    for f in parsed["fields"]:
        cv = f.get("constant")
        if cv:
            add(f["name"], "field", cv[0], cv[1])

    for m in parsed["methods"]:
        mname = m["name"]
        if mname == "<clinit>":
            okind = "clinit"
        else:
            okind = "method"
        hits, trunc = _walk_code_numbers(m["code"], parsed["cp"])
        if trunc and truncated_out is not None:
            truncated_out.append(f"{cls}#{mname}")
        for value, vkind, field_owner in hits:
            if field_owner:
                add(field_owner, "field", value, vkind)
            else:
                add(mname, okind, value, vkind)
    return records


def sort_records(records: list[dict]) -> list[dict]:
    def sk(r: dict) -> tuple:
        v = r.get("value")
        if isinstance(v, float):
            vkey = ("f", f"{v:.17g}")
        else:
            vkey = ("i", str(v))
        return (
            r.get("mod") or "",
            r.get("class") or "",
            r.get("owner") or "",
            r.get("owner_kind") or "",
            r.get("value_kind") or "",
            vkey,
            r.get("jar") or "",
        )

    return sorted(records, key=sk)


def write_db(path: Path, artifact: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(artifact, ensure_ascii=False, indent=2, sort_keys=True)
    path.write_bytes((payload + "\n").encode("utf-8"))


def load_db(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def scan_instance(
    instance: Path,
    mods_dir: Path,
    mods_filter: Optional[re.Pattern[str]],
    max_classes: int,
    min_abs: float = 0.0,
) -> dict[str, Any]:
    records: list[dict] = []
    jars_scanned = 0
    classes_seen = 0
    classes_parsed = 0
    classes_relevant = 0
    classes_extracted = 0
    unparsed = 0
    unparsed_names: list[str] = []
    walk_truncated_names: list[str] = []

    empty_scanned = {
        "mods_jars": 0,
        "classes_seen": 0,
        "classes_parsed": 0,
        "classes_relevant": 0,
        "classes_extracted": 0,
        "unparsed": 0,
        "unparsed_samples": [],
        "walk_truncated": 0,
        "walk_truncated_samples": [],
        "max_classes": max_classes,
        "mods_filter": mods_filter.pattern if mods_filter else None,
        "min_abs": min_abs,
    }

    if not mods_dir.is_dir():
        return {"records": [], "scanned": empty_scanned}

    for jar_name in sorted(os.listdir(mods_dir)):
        low = jar_name.lower()
        if not low.endswith(".jar") or low.endswith(".jar.disabled"):
            continue
        if mods_filter and not mods_filter.search(jar_name):
            continue
        jar_path = mods_dir / jar_name
        jars_scanned += 1
        mod = _mod_from_jar(jar_name)
        try:
            zf = zipfile.ZipFile(jar_path)
        except (zipfile.BadZipFile, OSError):
            continue
        try:
            names = sorted(
                n for n in zf.namelist() if n.endswith(".class") and not n.endswith("/")
            )
            for name in names:
                if max_classes > 0 and classes_extracted >= max_classes:
                    break
                classes_seen += 1
                try:
                    data = zf.read(name)
                except Exception:
                    unparsed += 1
                    if len(unparsed_names) < 8:
                        unparsed_names.append(f"{jar_name}!{name}")
                    continue
                try:
                    parsed = parse_class(data)
                    classes_parsed += 1
                except Exception:
                    unparsed += 1
                    if len(unparsed_names) < 8:
                        unparsed_names.append(f"{jar_name}!{name}")
                    continue
                if not class_is_mechanics_relevant(parsed):
                    continue
                classes_relevant += 1
                if max_classes > 0 and classes_extracted >= max_classes:
                    break
                classes_extracted += 1
                records.extend(
                    extract_records(
                        parsed,
                        mod=mod,
                        jar=jar_name,
                        min_abs=min_abs,
                        truncated_out=walk_truncated_names,
                    )
                )
        finally:
            zf.close()
        if max_classes > 0 and classes_extracted >= max_classes:
            break

    records = sort_records(records)
    # Dedup truncated method ids; keep stable order for determinism
    seen_t: set[str] = set()
    trunc_unique: list[str] = []
    for t in walk_truncated_names:
        if t not in seen_t:
            seen_t.add(t)
            trunc_unique.append(t)
    return {
        "records": records,
        "scanned": {
            "mods_jars": jars_scanned,
            "classes_seen": classes_seen,
            "classes_parsed": classes_parsed,
            "classes_relevant": classes_relevant,
            "classes_extracted": classes_extracted,
            "unparsed": unparsed,
            "unparsed_samples": unparsed_names,
            "walk_truncated": len(trunc_unique),
            "walk_truncated_samples": trunc_unique[:16],
            "max_classes": max_classes,
            "mods_filter": mods_filter.pattern if mods_filter else None,
            "min_abs": min_abs,
        },
    }


def cmd_build(args: argparse.Namespace) -> int:
    instance = Path(args.instance)
    if not instance.is_dir():
        sys.stderr.write(f"error: --instance not a directory: {instance}\n")
        return 2
    mods = instance / "mods"
    out = Path(args.out)
    # Hard requirement: only write to --out
    filt = re.compile(args.mods_filter) if args.mods_filter else None
    result = scan_instance(
        instance, mods, filt, args.max_classes, min_abs=float(args.min_abs)
    )
    records = result["records"]
    by_mod: dict[str, int] = {}
    for r in records:
        by_mod[r["mod"]] = by_mod.get(r["mod"], 0) + 1
    artifact = {
        "schema": SCHEMA,
        "instance": instance.name,
        "note": NOTE,
        "scanned": result["scanned"],
        "counts": {"total": len(records), "by_mod": dict(sorted(by_mod.items()))},
        "records": records,
    }
    write_db(out, artifact)
    sc = result["scanned"]
    print(
        f"OK build instance={instance.name} out={_norm_rel(str(out))} "
        f"jars={sc['mods_jars']} parsed={sc['classes_parsed']}/{sc['classes_seen']} "
        f"relevant={sc['classes_relevant']} extracted={sc['classes_extracted']} "
        f"unparsed={sc['unparsed']} walk_truncated={sc['walk_truncated']} "
        f"records={len(records)}"
    )
    if sc["unparsed_samples"]:
        print("UNPARSED_SAMPLES " + "; ".join(sc["unparsed_samples"]))
    if sc["walk_truncated"] and sc["walk_truncated_samples"]:
        print("WALK_TRUNCATED_SAMPLES " + "; ".join(sc["walk_truncated_samples"]))
    return 0


def _haystack(r: dict) -> str:
    parts = [
        r.get("mod") or "",
        r.get("class") or "",
        r.get("owner") or "",
        r.get("owner_kind") or "",
        r.get("unit_hint") or "",
        r.get("note") or "",
        r.get("jar") or "",
        str(r.get("value") if r.get("value") is not None else ""),
        r.get("value_kind") or "",
    ]
    return " ".join(parts).lower()


def query_records(db: dict, terms: str, limit: int) -> list[dict]:
    toks = [t.lower() for t in terms.split() if t.strip()]
    if not toks:
        return []
    hits = []
    for r in db.get("records") or []:
        h = _haystack(r)
        if all(t in h for t in toks):
            hits.append(r)
    return hits[:limit]


def cmd_query(args: argparse.Namespace) -> int:
    db = load_db(Path(args.db))
    hits = query_records(db, args.terms, args.limit)
    if args.json:
        print(
            json.dumps(
                {"terms": args.terms, "hits": hits, "note": NOTE},
                ensure_ascii=False,
                indent=2,
            )
        )
    else:
        if not hits:
            print("NO_MATCH")
            print("NO_NUMBER")
        else:
            for r in hits:
                uh = r.get("unit_hint")
                print(
                    f"A3/bytecode mod={r['mod']} {r['class']}#{r['owner']} "
                    f"value={r['value']} ({r['value_kind']}) "
                    f"unit_hint={uh} unverified=from bytecode; NOT game-verified "
                    f"jar={r['jar']}"
                )
    return 0


def build_parser() -> argparse.ArgumentParser:
    ap = argparse.ArgumentParser(
        description="Offline bytecode mechanics-number extractor (unverified)"
    )
    sub = ap.add_subparsers(dest="cmd", required=True)

    b = sub.add_parser("build", help="Scan instance mod jars → JSON db")
    b.add_argument("--instance", required=True, help="Path to <minecraft> dir")
    b.add_argument("--out", required=True, help="Output JSON path (only write target)")
    b.add_argument(
        "--mods-filter",
        default=None,
        help="Regex filter on jar file names",
    )
    b.add_argument(
        "--max-classes",
        type=int,
        default=0,
        help="Max mechanics-relevant classes to extract (0=unlimited)",
    )
    b.add_argument(
        "--min-abs",
        type=float,
        default=0.0,
        help=(
            "Drop numeric values with abs(value) < N (default 0=keep all). "
            "Use --min-abs 2 to suppress iconst_*/lconst_* floods of 0/1/-1."
        ),
    )
    b.set_defaults(func=cmd_build)

    q = sub.add_parser("query", help="Query db by terms")
    q.add_argument("--db", required=True)
    q.add_argument("--terms", required=True)
    q.add_argument("--limit", type=int, default=20)
    q.add_argument("--json", action="store_true")
    q.set_defaults(func=cmd_query)
    return ap


def main(argv: Optional[list[str]] = None) -> int:
    ap = build_parser()
    args = ap.parse_args(argv)
    return int(args.func(args))


if __name__ == "__main__":
    sys.exit(main())
