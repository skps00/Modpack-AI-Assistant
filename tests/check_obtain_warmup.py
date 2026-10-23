#!/usr/bin/env python3
"""Static gate: obtain-graph ingredient cache warmup before incremental build."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUNTIME = (
    ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/logic/ObtainGraphRuntime.java"
)
CONFIG = ROOT / "forge/1.19.2/src/main/java/com/skps9/packai/config/PackAiConfig.java"
LANGS = [
    ROOT / "forge/1.19.2/src/main/resources/assets/packai/lang/en_us.json",
    ROOT / "forge/1.19.2/src/main/resources/assets/packai/lang/zh_cn.json",
    ROOT / "forge/1.19.2/src/main/resources/assets/packai/lang/zh_tw.json",
]
LANG_KEY = "packai.config.obtain_warm_ingredients"


def main() -> None:
    fails: list[str] = []
    runtime = RUNTIME.read_text(encoding="utf-8")
    config = CONFIG.read_text(encoding="utf-8")

    # 1: warmIngredientCaches exists; call site between Builder and pending.add
    if "warmIngredientCaches" not in runtime:
        fails.append("ObtainGraphRuntime missing warmIngredientCaches")
    else:
        builder_idx = runtime.find("new ObtainGraph.Builder(gen, BuildContext.clientDefaults())")
        pending_idx = runtime.find("pending.add(List.copyOf(all.subList")
        warm_call_idx = runtime.find("warmIngredientCaches(all)")
        if builder_idx < 0:
            fails.append("missing ObtainGraph.Builder(gen, ...) in onRecipesUpdated path")
        elif pending_idx < 0:
            fails.append("missing pending.add batch loop")
        elif warm_call_idx < 0:
            fails.append("missing warmIngredientCaches(all) call")
        elif not (builder_idx < warm_call_idx < pending_idx):
            fails.append(
                "warmIngredientCaches must be after Builder and before pending.add "
                f"(builder={builder_idx}, warm={warm_call_idx}, pending={pending_idx})"
            )

    # 2: warmup method must not use RecipeIo (bench stats)
    warm_m = re.search(
        r"private static void warmIngredientCaches\(List<Recipe<\?>> recipes\) \{.*?\n    \}",
        runtime,
        re.DOTALL,
    )
    if not warm_m:
        fails.append("could not parse warmIngredientCaches method body")
    elif "RecipeIo." in warm_m.group(0):
        fails.append("warmIngredientCaches must not reference RecipeIo")

    # 3: gated by PackAiConfig.obtainWarmIngredients()
    on_recipes = runtime[runtime.find("public void onRecipesUpdated") :]
    if "PackAiConfig.obtainWarmIngredients()" not in on_recipes:
        fails.append("warmup must be guarded by PackAiConfig.obtainWarmIngredients()")

    # 4: config default true
    if not re.search(r'define\("obtainWarmIngredients",\s*true\)', config):
        fails.append('PackAiConfig must define obtainWarmIngredients default true')

    # 5: three lang files
    for lang_path in LANGS:
        text = lang_path.read_text(encoding="utf-8")
        if LANG_KEY not in text:
            fails.append(f"missing {LANG_KEY} in {lang_path.name}")

    if fails:
        for f in fails:
            print("FAIL", f)
        sys.exit(1)
    print("check_obtain_warmup OK")


if __name__ == "__main__":
    main()
