"""Quest button: prefer readable titles + SNBT schematic key hints."""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


@dataclass
class Hit:
    title: str
    items: list[str] = field(default_factory=list)
    chapter: str = ""
    description: str = ""
    score: int = 0


def accept_key(raw: str) -> bool:
    s = (raw or "").strip()
    if len(s) < 2 or len(s) > 64:
        return False
    if ":" in s or "/" in s:
        return True
    if len(s) < 3:
        return False
    return all(c.isalnum() or c in "_.-" for c in s)


def add_variant_hints(slice_: str, items: list[str]) -> None:
    seen = {i.lower() for i in items if i}
    for m in re.finditer(r'\bkey\s*:\s*"([^"]+)"', slice_, re.I):
        k = m.group(1)
        if accept_key(k) and k.lower() not in seen:
            seen.add(k.lower())
            items.append(k.lower())
    for m in re.finditer(r"\bschematics\s*:\s*\[([^\]]*)\]", slice_, re.I):
        for qm in re.finditer(r'"([^"]+)"', m.group(1) or ""):
            s = qm.group(1).strip().lower()
            if s and s not in seen:
                seen.add(s)
                items.append(s)


def has_readable_title(h: Hit) -> bool:
    t = (h.title or "").strip()
    if not t:
        return False
    if re.fullmatch(r"[0-9A-Fa-f]{11,16}", t):
        return False
    # file-id style (goldenagetetra)
    if (
        len(t) >= 10
        and t == t.lower()
        and " " not in t
        and re.fullmatch(r"[a-z][a-z0-9_]{9,}", t)
    ):
        return False
    return True


def prefer_readable(scored: list[Hit]) -> list[Hit]:
    titled = [h for h in scored if has_readable_title(h)]
    return titled if titled else scored


def looks_like_quest_id(s: str) -> bool:
    t = (s or "").strip()
    if not t:
        return False
    if re.fullmatch(r"[0-9A-Fa-f]{11,16}", t, re.I):
        return True
    if (
        len(t) >= 10
        and t == t.lower()
        and " " not in t
        and re.fullmatch(r"[a-z][a-z0-9_]{9,}", t)
    ):
        return True
    return False


def looks_like_registry_path_label(label: str, item_id: str) -> bool:
    path = item_id.split("{", 1)[0].lower()
    if ":" in path:
        path = path.split(":", 1)[1]
    human = path.replace("_", " ").replace("/", " ").strip()
    lab = label.strip().lower()
    return lab == human or lab == path or lab == item_id.lower()


def mentions_variant(h: Hit, tokens: list[str]) -> bool:
    blob = f"{h.chapter} {h.title} {h.description}".lower()
    items = [i.lower() for i in h.items]
    for tok in tokens:
        t = tok.lower()
        if len(t) < 2:
            continue
        if t in blob:
            return True
        if any(t in it for it in items):
            return True
    return False


def prefer_variant(scored: list[Hit], tokens: list[str]) -> list[Hit]:
    if not scored or not tokens:
        return scored
    ok = [h for h in scored if mentions_variant(h, tokens)]
    return ok if ok else scored


def main() -> None:
    slice_ = '''
    title: "能量瓶改造"
    tasks: [{
      item: {
        id: "tetra:scroll_rolled"
        tag: {
          BlockEntityTag: {
            data: [{
              key: "energy_bottle"
              schematics: ["tetra:energy_bottle"]
            }]
          }
        }
      }
    }]
    '''
    items = ["tetra:scroll_rolled"]
    add_variant_hints(slice_, items)
    assert "energy_bottle" in items
    assert "tetra:energy_bottle" in items

    untitled = Hit("", ["tetra:scroll_rolled"], score=10)
    titled = Hit("能量瓶改造", ["tetra:scroll_rolled", "energy_bottle"], score=10)
    out = prefer_readable([untitled, titled])
    assert out == [titled]

    file_id = Hit("goldenagetetra", ["tetra:scroll_rolled"], score=10)
    out2 = prefer_readable([file_id, titled])
    assert out2 == [titled]
    assert looks_like_quest_id("goldenagetetra")
    assert not looks_like_quest_id("能量瓶改造")
    assert not has_readable_title(file_id)

    soft = prefer_variant([untitled, titled], ["energy_bottle", "tetra:energy_bottle"])
    assert soft == [titled]

    assert looks_like_registry_path_label("Scroll Rolled", "tetra:scroll_rolled")
    assert looks_like_registry_path_label("scroll rolled", "tetra:scroll_rolled")
    assert not looks_like_registry_path_label("能量瓶改造", "tetra:scroll_rolled")

    for tree in ("forge/1.19.2", "neoforge/1.21.1"):
        src = (ROOT / tree / "src/main/java/com/skps9/packai/logic/QuestGuide.java").read_text(
            encoding="utf-8"
        )
        assert "preferReadableTitleHits" in src
        assert "addVariantHintsFromSlice" in src
        assert "looksLikeRegistryPathLabel" in src

    print("check_quest_title_prefer: OK")


if __name__ == "__main__":
    main()
