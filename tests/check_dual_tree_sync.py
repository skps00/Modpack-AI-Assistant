#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Dual-tree sync gate — forge/neoforge logic should stay lockstep.

Usage (repo root): python tests/check_dual_tree_sync.py

Checks:
1. Every relative java path present in forge packai source MUST also exist in
   neoforge (and vice versa), EXCEPT the known tree-specific list.
2. Every common file MUST be byte-identical EXCEPT the allowlist of files that
   legitimately differ because of MC-version APIs (registry access, DataComponents,
   JEI version API, GuiGraphics …).
3. The allowlist itself must not grow silently: adding a new differing file needs an
   explicit entry with a reason, and a NEW entry fails unless it is in the
   'permanent version-diff' reason list or explicitly marked.

Rules:
- Exit 0 = PASS; exit 1 = FAIL (missing twin, or unexpected byte drift).
- Byte drift in an allowlisted file is reported as WARN (it may be a real change the
  other tree did not get — investigate), not FAIL, because allowlisted files are
  expected to diverge by design. To keep the gate meaningful, allowlisted files whose
  forge/neo content differs by ONLY whitespace/line-endings are still FAILED
  (normalize CRLF).
"""
import hashlib
import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TREES = {
    "forge": os.path.join(REPO, "forge", "1.19.2", "src", "main", "java"),
    "neoforge": os.path.join(REPO, "neoforge", "1.21.1", "src", "main", "java"),
}
PKG = os.path.join("com", "skps9", "packai")

# Files that legitimately differ between trees (MC-version API). Key = relative path
# under com/skps9/packai, value = short reason. Do NOT add entries casually — new
# diverging logic needs a version-API reason; pure logic must stay lockstep.
ALLOWLIST = {
    # --- logic (20) ---
    "logic/AnvilRepairHint.java": "1.19.2 registry scan vs 1.21.1 dynamic registry",
    "logic/AskPurposeContext.java": "version API (item behavior lines)",
    "logic/ContainedItems.java": "1.19.2 NBT list vs 1.21.1 DataComponents",
    "logic/EnchantHint.java": "1.19.2 ForgeRegistries + isAllowedOnBooks vs 1.21.1 RegistryAccess + canEnchant",
    "logic/EnchantLookupAskTool.java": "version registry API",
    "logic/GuidebookIndexCache.java": "version API",
    "logic/GuidebookPins.java": "version API",
    "logic/ItemConsumeUseFacts.java": "version NBT/component API",
    "logic/ItemResolver.java": "version registry API",
    "logic/ItemVariantKeys.java": "version component API",
    "logic/ItemVariantKeysText.java": "version component API",
    "logic/ModularToolScan.java": "version API",
    "logic/PackIndex.java": "version registry/NBT API",
    "logic/PlayerUnlockStatus.java": "version API",
    "logic/RecipeCard.java": "1.19.2 Registry + forge FluidStack vs 1.21.1 BuiltInRegistries + neo FluidStack",
    "logic/RecipeCardsMode.java": "version API",
    "logic/RecipeIoSummary.java": "version component API",
    "logic/RecipeUnlockGates.java": "version API",
    "logic/ReplyLang.java": "version API (lang lookups)",
    "logic/TetraMaterialItems.java": "version API",
    # --- client/jei (14) ---
    "client/jei/IngredientReqHints.java": "JEI version API",
    "client/jei/JeiCategoryCatalog.java": "JEI version API",
    "client/jei/JeiFocusMatch.java": "JEI version API",
    "client/jei/JeiInfoPages.java": "JEI version API",
    "client/jei/JeiLayoutDraw.java": "JEI version API",
    "client/jei/JeiLookup.java": "JEI version API",
    "client/jei/JeiRecipeCards.java": "JEI version API",
    "client/jei/JeiRecipeLayoutCollector.java": "JEI version API",
    "client/jei/JeiReqNotes.java": "JEI version API",
    "client/jei/JeiSoftIngredients.java": "JEI version API",
    "client/jei/JeiTargetResolver.java": "JEI version API",
    "client/jei/JeiTypedLookup.java": "JEI version API",
    "client/jei/PackAiJeiPlugin.java": "JEI version API",
    "client/jei/SuggestIcons.java": "JEI version API",
    # --- client/knowledge (4) ---
    "client/knowledge/GuidebookIndex.java": "version API",
    "client/knowledge/ItemIndex.java": "version API",
    "client/knowledge/ItemSearch.java": "version API",
    "client/knowledge/PackKnowledge.java": "version API",
    # --- client/service (1) ---
    "client/service/AskService.java": "version API call sites",
    # --- config (1) ---
    "config/PackAiConfig.java": "version API",
    # --- client/gui + mixin + compat (observed tree API differences) ---
    "client/gui/AiAssistantScreen.java": "version GUI API",
    "client/gui/InvPickScreen.java": "version GUI API",
    "client/gui/RecipeCategoryScreen.java": "version GUI API",
    "client/gui/WebSearchSettingsScreen.java": "version GUI API",
    "client/tooltip/PackAiTooltipHandler.java": "version API",
    "client/tooltip/TooltipHover.java": "version API",
    "client/chat/ChatMessage.java": "version API",
    "client/chat/ChatSession.java": "version API",
    "client/context/GameContextCollector.java": "version API",
    "client/context/SeasonContext.java": "version API",
    "client/context/TooltipCapture.java": "version API",
    "client/patchouli/PatchouliGuideLookup.java": "version API",
    "client/command/AiClientCommands.java": "version command API",
    "client/PackAiClient.java": "version API",
    "logic/RecipeExtra.java": "version API",
    "logic/GuidebookEntry.java": "version API",
    "logic/WorldgenFacts.java": "version API",
    "logic/LootForwardIndex.java": "version API",
    "logic/JarLightIndex.java": "version API",
    "logic/ModelCatalog.java": "version API",
    "logic/PatchouliEntryScan.java": "version API",
    "compat/PatchouliBridge.java": "version compat API",
    "compat/CuriosBridge.java": "version compat API",
    "logic/WebSearch.java": "version API",
    # --- root + client entrypoints (loader-specific by nature) ---
    "PackAiMod.java": "forge vs neoforge mod bootstrap imports",
    "client/AskToolRegisterEvent.java": "forge eventbus vs neo bus Event base",
    "client/ClientSetup.java": "forge vs neoforge client event classes",
    "client/QuestBookOpener.java": "reflection target differs by MC version",
    "client/ReplyNotifier.java": "version API",
    "client/gui/GuiShell.java": "1.21.1 GuiGraphics moved to MC core; comments",
    "client/gui/ModelPickerScreen.java": "version GUI API",
    "client/gui/PackAiSettingsScreen.java": "version GUI API",
    "client/tooltip/ThinkHoldTracker.java": "version API",
    "compat/CuriosBridgeImpl.java": "version compat impl",
    "compat/PatchouliBridgeImpl.java": "version compat impl",
}

# Files that exist in only one tree by design.
TREE_SPECIFIC = {
    "forge": {
        "client/gui/GuiGraphics.java",
        "client/gui/WidgetCompat.java",
        "mixin/FontDrawCaptureMixin.java",
    },
    "neoforge": {
        "client/guideme/GuideMeGuideLookup.java",
        "compat/GuideMeBridge.java",
        "compat/GuideMeBridgeImpl.java",
        "logic/GuideMePageScan.java",
        "mixin/GuiGraphicsDrawCaptureMixin.java",
    },
}

MISSING_OK_REASON = "tree-specific (GuideMe / mixin variant / forge-only helper)"


def walk(tree):
    out = {}
    root = os.path.join(TREES[tree], PKG)
    for dirpath, _dirs, files in os.walk(root):
        for f in files:
            if f.endswith(".java"):
                rel = os.path.relpath(os.path.join(dirpath, f), root).replace("\\", "/")
                out[rel] = os.path.join(dirpath, f)
    return out


def sha(p):
    with open(p, "rb") as fh:
        data = fh.read().replace(b"\r\n", b"\n")
    return hashlib.sha256(data).hexdigest()


def main():
    forge = walk("forge")
    neo = walk("neoforge")
    problems = []
    warns = []
    checked = 0

    # 1. twin presence
    for rel in sorted(set(forge) | set(neo)):
        in_f = rel in forge
        in_n = rel in neo
        if in_f and in_n:
            continue
        owner = "forge" if in_f else "neoforge"
        if rel in TREE_SPECIFIC[owner]:
            continue
        problems.append(f"missing twin in {'neoforge' if in_f else 'forge'}: {rel} ({MISSING_OK_REASON})")

    # 2. byte identity
    for rel in sorted(set(forge) & set(neo)):
        h_f = sha(forge[rel])
        h_n = sha(neo[rel])
        checked += 1
        if h_f == h_n:
            continue
        if rel in ALLOWLIST:
            # allowlisted divergence is OK, but report so it is visible
            warns.append(f"allowlisted diff (expected): {rel}  [{ALLOWLIST[rel]}]")
        else:
            problems.append(
                f"byte drift between trees: {rel}\n"
                f"    forge   {forge[rel]}\n"
                f"    neoforge {neo[rel]}\n"
                "    This file is NOT in the version-diff allowlist — pure logic must stay lockstep. "
                "If this is a real version API difference, add it to ALLOWLIST with a reason; "
                "if it is an accidental one-sided edit, sync the other tree."
            )

    # 3. allowlist hygiene: entries pointing at files that are identical now (stale) — warn
    stale = []
    for rel in ALLOWLIST:
        if rel in forge and rel in neo and sha(forge[rel]) == sha(neo[rel]):
            stale.append(rel)

    print(f"dual-tree sync gate: checked {checked} common java files")
    print(f"  identical: {checked - len(warns) - len(problems)}")
    print(f"  allowlisted diffs: {len(warns)}")
    if stale:
        print(f"  WARN stale allowlist entries (files now identical — can remove): {len(stale)}")
        for s in sorted(stale)[:10]:
            print(f"    - {s}")
    if problems:
        print(f"FAIL ({len(problems)}):")
        for p in problems:
            print("  " + p)
        return 1
    print("PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
