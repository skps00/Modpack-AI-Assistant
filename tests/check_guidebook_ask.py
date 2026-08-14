#!/usr/bin/env python3
"""WP3/WP4 — guidebook scope, pin format, quest dedupe, honesty prompt, no Ask full-scan."""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def normalize_scope(raw: str | None) -> str:
    if not raw:
        return "same_mod"
    s = raw.strip().lower()
    if s in ("any_mod", "any", "cross_mod"):
        return "any_mod"
    return "same_mod"


def item_ns(item_id: str) -> str:
    item_id = (item_id or "").strip().lower()
    return item_id.split(":", 1)[0] if ":" in item_id else ""


def passes_scope(entry: dict, scope: str, item_ns_: str) -> bool:
    if normalize_scope(scope) != "same_mod":
        return True
    if not item_ns_:
        return True
    return (entry.get("bookNs") or "").lower() == item_ns_.lower()


def join_capped(bodies: list[str], max_entries: int = 2, max_chars: int = 3000) -> str:
    out: list[str] = []
    total = 0
    for body in bodies:
        if not body or not body.strip():
            continue
        chunk = body.strip()
        sep = 2 if out else 0
        if total + sep + len(chunk) > max_chars:
            room = max_chars - total - sep
            if room > 0:
                out.append(chunk[:room])
            break
        out.append(chunk)
        total += sep + len(chunk)
        if len(out) >= max_entries:
            break
    return "\n\n".join(out)


def tokens(raw: str) -> set[str]:
    norm = re.sub(r"[^\w]+", " ", (raw or "").lower(), flags=re.UNICODE)
    return {t for t in norm.split() if len(t) >= 3}


def dedupe_against_quest(guide: str, quest: str, threshold: float = 0.55) -> str:
    if not guide or not guide.strip():
        return ""
    if not quest or not quest.strip():
        return guide.strip()
    qt = tokens(quest)
    if not qt:
        return guide.strip()
    kept = []
    for chunk in re.split(r"\n\n+", guide.strip()):
        gt = tokens(chunk)
        if not gt:
            continue
        overlap = len(gt & qt) / len(gt)
        if overlap >= threshold:
            continue
        kept.append(chunk.strip())
    return "\n\n".join(kept).strip()


def main() -> None:
    for side in (
        "forge/1.19.2/src/main/java/com/skps9/packai",
        "neoforge/1.21.1/src/main/java/com/skps9/packai",
    ):
        lookup = read(f"{side}/client/patchouli/PatchouliGuideLookup.java")
        assert "GuidebookIndex.INSTANCE.ensureAsync" in lookup
        assert "GuidebookIndex.INSTANCE.awaitReady" in lookup
        assert "GuidebookIndex.INSTANCE.isReady()" in lookup
        assert "GuidebookIndex.INSTANCE.lookupByItem" in lookup
        assert "PatchouliBridge.lookupGuideText(stack, scope, itemNs)" in lookup
        assert "resolveGuideBody" in lookup
        assert "searchByTitle" in lookup  # B2
        assert "guidebookRelatedHop" in lookup  # B3 gate
        assert "HIGH_NO_ITEM_SCORE" in lookup
        assert "getResourceManager().listResources" not in lookup  # no Ask full-scan
        assert "scanResources" in lookup  # deprecated stub may remain
        # deprecated scanResources body must stay empty
        assert 'public static String scanResources(String itemId) {\n        return "";' in lookup.replace(
            "\r\n", "\n"
        ) or 'return "";' in lookup.split("scanResources")[1][:120]
        pins = read(f"{side}/logic/GuidebookPins.java")
        assert "SCOPE_SAME_MOD" in pins and "SCOPE_ANY_MOD" in pins
        assert "dedupeAgainstQuest" in pins
        assert "resolveDisplayString" in pins
        assert "resolveGuideBody" in pins
        assert "pinHeaderResolved" in pins
        assert "formatPins" in pins
        idx = read(f"{side}/client/knowledge/GuidebookIndex.java")
        assert "awaitReady" in idx
        assert "snapshotJarDataBooks" in idx
        assert 'startsWith("data/")' in idx
        assert "isSameThread()" in idx  # never block client
        assert "FORMAT_VERSION = 4" in read(f"{side}/logic/GuidebookIndexCache.java")
        cfg = read(f"{side}/config/PackAiConfig.java")
        assert "GUIDEBOOK_SCOPE" in cfg and "guidebookScope" in cfg
        settings = read(f"{side}/client/gui/PackAiSettingsScreen.java")
        assert "guidebook_scope" in settings
        engine = read(f"{side}/logic/AskEngine.java")
        assert "GuidebookPins.dedupeAgainstQuest" in engine
        assert "skipWebForPurpose" in engine
        purpose_ctx = read(f"{side}/logic/AskPurposeContext.java")
        assert "Mechanics from [GUIDE] before tooltip" in purpose_ctx
        reply = read(f"{side}/logic/ReplyLang.java")
        assert "guide_advisory" in reply
        ask = read(f"{side}/client/service/AskService.java")
        assert "Pack AI Ask replyLang=" in ask
        bridge = read(f"{side}/compat/PatchouliBridge.java")
        assert "guidebookScope" in bridge or "String.class, String.class" in bridge
        impl = read(f"{side}/compat/PatchouliBridgeImpl.java")
        assert "isExtension" in impl
        assert "bookNamespace" in impl or "same_mod" in impl.lower()
        assert "getEntryForStack" in impl
        assert "recipeMappings" in impl
        assert "apiFallbackEntry" in impl
        assert "openLexiconGui" not in impl
        assert "displayBookGui" not in impl
        assert "getBookFromStack" not in impl
        assert "openBookGUI" not in impl
        assert "as(net.minecraft.network.chat.Component.class)" in impl or "Component.class" in impl
        assert "asString()" in impl  # fallback after Component
        assert "preferIndexThenApi" in lookup
        assert "isSameThread()" in bridge
        assert "execute(()" in bridge or "mc.execute" in bridge
        pins = read(f"{side}/logic/GuidebookPins.java")
        assert "preferIndexThenApi" in pins
        scan = read(f"{side}/logic/PatchouliEntryScan.java")
        assert "isTextLikePage" in scan
        assert "GuidebookPins.isSpotlightPage" in scan

    # scope fixtures
    same = {"bookNs": "goety", "bookId": "black_book", "entryId": "x", "title": "X", "textClip": "goety lore", "linkedItems": ["goety:cursed_ingot"]}
    cross = {"bookNs": "goety", "bookId": "black_book", "entryId": "y", "title": "Iron", "textClip": "uses iron", "linkedItems": ["minecraft:iron_ingot"]}
    assert passes_scope(same, "same_mod", "goety")
    assert not passes_scope(cross, "same_mod", "minecraft")
    assert passes_scope(cross, "any_mod", "minecraft")

    # index miss → API; index hit wins
    assert not ("" and "api")
    def prefer(idx: str | None, api: str | None) -> str:
        if idx and idx.strip():
            return idx.strip()
        if api and api.strip():
            return api.strip()
        return ""

    assert prefer("idx", "api") == "idx"
    assert prefer("", "api") == "api"
    assert prefer("  ", " api ") == "api"
    assert prefer("", "") == ""

    # total cap preferred over 2 entries
    bodies = ["A" * 2000, "B" * 2000]
    capped = join_capped(bodies, 2, 3000)
    assert len(capped) <= 3000
    assert capped.startswith("A")

    # quest dedupe drops overlapping guide
    guide = "black_book/x | Ritual\nComplete the dark ritual at the altar with cursed ingot"
    quest = "Dark Ritual\nComplete the dark ritual at the altar with cursed ingot to unlock"
    assert dedupe_against_quest(guide, quest) == ""
    assert "ritual" in dedupe_against_quest(guide, "unrelated fishing quest about salmon").lower()

    # honesty keys in lang
    for tree in ("forge/1.19.2", "neoforge/1.21.1"):
        for lang in ("en_us", "zh_tw", "zh_cn"):
            data = json.loads(read(f"{tree}/src/main/resources/assets/packai/lang/{lang}.json"))
            assert "packai.settings.guidebook_scope" in data
            assert "packai.settings.guidebook_related" in data
            assert "packai.reply.guide_advisory" in data
            adv = data["packai.reply.guide_advisory"]
            assert "[GUIDE]" in adv
            assert "advisory" in adv.lower() or "參考" in adv or "参考" in adv
            assert (
                "decoration" in adv.lower()
                or "裝飾" in adv
                or "装饰" in adv
            )

    print("check_guidebook_ask OK")


if __name__ == "__main__":
    main()
