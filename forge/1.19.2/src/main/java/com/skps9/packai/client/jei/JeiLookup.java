package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.CraftPriority;
import com.skps9.packai.logic.Plainify;
import com.skps9.packai.logic.RecipeCategoryPrefs;
import com.skps9.packai.logic.ReplyLang;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;

/**
 * Full JEI scan (R / U / catalyst), then compact text for the LLM.
 */
public final class JeiLookup {
    private static final int MAX_SCAN_PER_CAT = 2000;
    /** Cap ingredient lines listed per category in the LLM JEI block (rest → see JEI). */
    static final int MAX_LISTED_PER_CAT = 3;
    /** Vanilla-sized crafts: few unique mats. Large grids (Create mechanical) need more. */
    static final int MAX_INPUT_LABELS_SMALL = 8;
    /** Unique Name×N lines for large grids (Create 9×9 unique types). */
    static final int MAX_INPUT_LABELS_LARGE = 81;
    private static final int UNIVERSAL_MIN_RAW = 20;
    /** Machine section: few JEI tabs + sample I/O — not full catalyst dump (LLM already covers how-to-use). */
    private static final int MACHINE_BRIEF_MAX_CATS = 3;
    private static final int MACHINE_BRIEF_MAX_EXAMPLES = 2;
    private static final int MACHINE_BRIEF_SCAN_PER_CAT = 24;
    private static final int MACHINE_BRIEF_MAX_CHARS = 360;
    private static final int UNIVERSAL_SAME_OUT_PCT = 80;

    private JeiLookup() {}

    /**
     * Cap recipe detail lines for the LLM. Prefer shorter lines first (simpler crafts).
     * When truncated, appends {@code moreLine} (already localized).
     */
    public static List<String> capListedDetails(List<String> details, int max, String moreLine) {
        if (details == null || details.isEmpty()) {
            return List.of();
        }
        int cap = Math.max(1, max);
        List<String> sorted = new ArrayList<>(details);
        sorted.sort(Comparator.comparingInt(String::length).thenComparing(s -> s));
        if (sorted.size() <= cap) {
            return List.copyOf(sorted);
        }
        List<String> out = new ArrayList<>(sorted.subList(0, cap));
        if (moreLine != null && !moreLine.isBlank()) {
            out.add(moreLine);
        }
        return out;
    }

    public static String summarize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (!ModList.get().isLoaded("jei")) {
            return null;
        }
        try {
            return summarizeUnsafe(stack);
        } catch (NoClassDefFoundError | Exception e) {
            PackAiMod.LOGGER.debug("JEI lookup skipped: {}", e.toString());
            return null;
        }
    }

    /** Tool-loop dump level; pre-Pass-2 lookup used one dump. */
    public static String summarize(ItemStack stack, com.skps9.packai.logic.AskToolContext.JeiDumpLevel level) {
        return summarize(stack);
    }

    /** True when JEI lists this stack as a recipe-type / layout catalyst (machine / workstation). */
    public static boolean isUsedAsCatalyst(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !ModList.get().isLoaded("jei")) {
            return false;
        }
        try {
            return isUsedAsCatalystUnsafe(stack);
        } catch (Throwable e) {
            // One broken JEI category / LinkageError must not kill Machine for furnace.
            PackAiMod.LOGGER.warn("JEI catalyst check skipped: {}", e.toString());
            return false;
        }
    }

    /** Placeable block form — Machine section / icon-workstation path requires this. */
    public static boolean isPlaceableBlockItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof BlockItem;
    }

    /**
     * Compact catalyst-only JEI text for the Machine section (no header totals).
     * @return null when not usable / empty
     */
    public static String machineBrief(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !ModList.get().isLoaded("jei")) {
            return null;
        }
        try {
            return machineBriefUnsafe(stack);
        } catch (Throwable e) {
            PackAiMod.LOGGER.warn("JEI machine brief skipped: {}", e.toString());
            return null;
        }
    }

    /**
     * Which detection path last succeeded for {@link #isUsedAsCatalyst} — for INFO logs.
     * Values: focus | typeLookup | icon | (empty when false).
     */
    private static final ThreadLocal<String> LAST_CATALYST_PATH = ThreadLocal.withInitial(() -> "");

    /** Last match path for PackKnowledge logging (empty when not a catalyst). */
    public static String lastCatalystMatchPath() {
        String p = LAST_CATALYST_PATH.get();
        return p == null ? "" : p;
    }

    private static boolean isUsedAsCatalystUnsafe(ItemStack stack) {
        LAST_CATALYST_PATH.set("");
        Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
        if (opt.isEmpty()) {
            return false;
        }
        IJeiRuntime runtime = opt.get();
        IRecipeManager recipes = runtime.getRecipeManager();
        IFocusFactory focuses = runtime.getJeiHelpers().getFocusFactory();
        IFocus<ItemStack> asCatalyst = focuses.createFocus(
                RecipeIngredientRole.CATALYST, VanillaTypes.ITEM_STACK, stack.copy());
        // JEI lists type catalysts on the category (furnace→Smelting). Recipe lookup with
        // CATALYST focus is often empty — catalyst is not a per-recipe layout ingredient.
        // includeHidden: packs that hide all Smelting recipes still keep furnace as catalyst;
        // without it JEI isCategoryHidden drops the category (visible recipe count = 0).
        if (!catalystFocusCategories(recipes, asCatalyst).isEmpty()) {
            LAST_CATALYST_PATH.set("focus");
            return true;
        }
        // Unusual Prehistory DNA Analyzer etc.: category icon / addRecipeCatalyst only —
        // not present as layout CATALYST, so focus path above is empty.
        // Handheld tools used as JEI tab icons (syringe, crumble horn) must NOT qualify.
        if (!isPlaceableBlockItem(stack)) {
            return false;
        }
        String path = workstationMatchPath(recipes, stack);
        if (path.isEmpty()) {
            return false;
        }
        LAST_CATALYST_PATH.set(path);
        return true;
    }

    /** JEI categories for this stack as recipe-type catalyst (spam / quest / PackAI-hidden stripped). */
    private static List<IRecipeCategory<?>> catalystFocusCategories(
            IRecipeManager recipes, IFocus<ItemStack> asCatalyst
    ) {
        List<IRecipeCategory<?>> out = new ArrayList<>();
        List<IRecipeCategory<?>> raw;
        try {
            raw = recipes.createRecipeCategoryLookup()
                    .limitFocus(List.of(asCatalyst))
                    .includeHidden()
                    .get()
                    .toList();
        } catch (Throwable t) {
            PackAiMod.LOGGER.warn("JEI CATALYST category focus failed: {}", t.toString());
            return out;
        }
        for (IRecipeCategory<?> category : raw) {
            try {
                String uid = JeiCategoryCatalog.categoryUid(category);
                if (RecipeCategoryPrefs.isHidden(uid)) {
                    continue;
                }
                RecipeType<?> type = category.getRecipeType();
                String catTitle = category.getTitle().getString();
                if (JeiUniversalSpam.isSpamCategory(type, catTitle)
                        || JeiUniversalSpam.isNonMachineCategory(type, catTitle)) {
                    continue;
                }
                out.add(category);
            } catch (Throwable t) {
                PackAiMod.LOGGER.debug("JEI CATALYST category skipped: {}", t.toString());
            }
        }
        return out;
    }

    private static String machineBriefUnsafe(ItemStack stack) {
        String lang = ReplyLang.current();
        Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
        if (opt.isEmpty()) {
            return null;
        }
        IJeiRuntime runtime = opt.get();
        IRecipeManager recipes = runtime.getRecipeManager();
        IIngredientManager ingredients = runtime.getIngredientManager();
        IFocusFactory focuses = runtime.getJeiHelpers().getFocusFactory();
        IFocus<ItemStack> asCatalyst = focuses.createFocus(
                RecipeIngredientRole.CATALYST, VanillaTypes.ITEM_STACK, stack.copy());
        // Compact: JEI tab names + ≤2 short a→b samples (no "機器X：" spam / no full catalyst dump).
        List<IRecipeCategory<?>> cats = catalystFocusCategories(recipes, asCatalyst);
        if (cats.isEmpty() && isPlaceableBlockItem(stack)) {
            cats = workstationCategories(recipes, stack);
        }
        if (cats.isEmpty()) {
            return null;
        }
        List<String> catNames = new ArrayList<>();
        LinkedHashSet<String> examples = new LinkedHashSet<>();
        int n = 0;
        for (IRecipeCategory<?> category : cats) {
            if (n >= MACHINE_BRIEF_MAX_CATS) {
                break;
            }
            String catTitle = Plainify.stripMcFormat(category.getTitle().getString());
            if (!catTitle.isBlank()) {
                catNames.add(catTitle);
            }
            n++;
            if (examples.size() < MACHINE_BRIEF_MAX_EXAMPLES) {
                try {
                    collectMachineBriefExamples(
                            recipes, ingredients, category, stack, lang, examples, MACHINE_BRIEF_MAX_EXAMPLES);
                } catch (Throwable t) {
                    PackAiMod.LOGGER.debug("JEI machine brief samples skipped: {}", t.toString());
                }
            }
        }
        if (catNames.isEmpty()) {
            return null;
        }
        String join = ReplyLang.sourceJoin(lang);
        StringBuilder stub = new StringBuilder();
        stub.append(ReplyLang.machineBriefCats(lang, String.join(join, catNames))).append('\n');
        if (!examples.isEmpty()) {
            String exJoin = lang != null && lang.toLowerCase().startsWith("zh") ? "；" : "; ";
            stub.append(ReplyLang.machineBriefExamples(lang, String.join(exJoin, examples))).append('\n');
        }
        String out = stub.toString().trim();
        if (out.length() > MACHINE_BRIEF_MAX_CHARS) {
            out = out.substring(0, MACHINE_BRIEF_MAX_CHARS) + "…";
        }
        return out;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void collectMachineBriefExamples(
            IRecipeManager recipes,
            IIngredientManager ingredients,
            IRecipeCategory<?> category,
            ItemStack focusStack,
            String lang,
            LinkedHashSet<String> examples,
            int maxExamples
    ) {
        RecipeType type = category.getRecipeType();
        IRecipeCategory cat = category;
        List<?> found = recipes.createRecipeLookup(type).includeHidden().get()
                .limit(MACHINE_BRIEF_SCAN_PER_CAT).toList();
        for (Object recipe : found) {
            if (examples.size() >= maxExamples) {
                return;
            }
            try {
                JeiRecipeLayoutCollector.CollectedLayout layout =
                        JeiRecipeLayoutCollector.collect(cat, recipe, ingredients);
                if (involvesSpamItem(layout)) {
                    continue;
                }
                String line = shortIoLine(layout, ingredients, lang, focusStack);
                if (line != null && !line.isBlank()) {
                    examples.add(line);
                }
            } catch (Exception ignored) {
                // ponytail: skip broken recipe layouts
            }
        }
    }

    /** Short a→b without repeating workstation name (Machine section already names the block). */
    private static String shortIoLine(
            JeiRecipeLayoutCollector.CollectedLayout layout,
            IIngredientManager ingredients,
            String lang,
            ItemStack focusStack
    ) {
        List<String> inputs = labels(layout.itemStacksOnePerSlot(RecipeIngredientRole.INPUT, focusStack), 3);
        List<String> outputs = labels(layout.itemStacksOnePerSlot(RecipeIngredientRole.OUTPUT, focusStack), 2);
        addUniqueLabels(outputs, extraOutputLabels(layout, ingredients, RecipeIngredientRole.OUTPUT, 4), 6);
        if (inputs.isEmpty() && outputs.isEmpty()) {
            return null;
        }
        String join = ReplyLang.sourceJoin(lang);
        String in = inputs.isEmpty() ? ReplyLang.jeiNoMats(lang) : String.join(join, inputs);
        String out = outputs.isEmpty() ? ReplyLang.jeiNoOut(lang) : String.join(join, outputs);
        return in + " → " + out;
    }

    /**
     * Categories where {@code stack} is the JEI workstation: recipe-type catalysts
     * ({@code createRecipeCatalystLookup}) or category {@link IRecipeCategory#getIcon()} item
     * (mods that only set the tab icon, e.g. Unusual Prehistory Analyzer).
     * Does not require visible recipes — packs may hide every Smelting recipe while furnace
     * remains the type catalyst.
     */
    private static List<IRecipeCategory<?>> workstationCategories(IRecipeManager recipes, ItemStack stack) {
        List<IRecipeCategory<?>> out = new ArrayList<>();
        for (IRecipeCategory<?> category : eligibleWorkstationCategories(recipes)) {
            try {
                if (workstationPathForCategory(recipes, category, stack).isEmpty()) {
                    continue;
                }
                out.add(category);
            } catch (Throwable t) {
                PackAiMod.LOGGER.debug("JEI workstation category skipped: {}", t.toString());
            }
        }
        return out;
    }

    /** First matching path across categories: typeLookup then icon. Empty = not a workstation. */
    private static String workstationMatchPath(IRecipeManager recipes, ItemStack stack) {
        for (IRecipeCategory<?> category : eligibleWorkstationCategories(recipes)) {
            try {
                String path = workstationPathForCategory(recipes, category, stack);
                if (!path.isEmpty()) {
                    return path;
                }
            } catch (Throwable t) {
                PackAiMod.LOGGER.debug("JEI workstation category skipped: {}", t.toString());
            }
        }
        return "";
    }

    private static List<IRecipeCategory<?>> eligibleWorkstationCategories(IRecipeManager recipes) {
        List<IRecipeCategory<?>> out = new ArrayList<>();
        List<IRecipeCategory<?>> raw;
        try {
            // includeHidden: JEI drops catalyst-only categories when all recipes are hidden.
            raw = recipes.createRecipeCategoryLookup().includeHidden().get().toList();
        } catch (Throwable t) {
            PackAiMod.LOGGER.warn("JEI category list failed: {}", t.toString());
            return out;
        }
        for (IRecipeCategory<?> category : raw) {
            try {
                String uid = JeiCategoryCatalog.categoryUid(category);
                if (RecipeCategoryPrefs.isHidden(uid)) {
                    continue;
                }
                RecipeType<?> type = category.getRecipeType();
                String catTitle = category.getTitle().getString();
                if (JeiUniversalSpam.isSpamCategory(type, catTitle)
                        || JeiUniversalSpam.isNonMachineCategory(type, catTitle)) {
                    continue;
                }
                out.add(category);
            } catch (Throwable t) {
                PackAiMod.LOGGER.debug("JEI workstation category skipped: {}", t.toString());
            }
        }
        return out;
    }

    /** @return typeLookup | icon | empty */
    private static String workstationPathForCategory(
            IRecipeManager recipes, IRecipeCategory<?> category, ItemStack stack
    ) {
        RecipeType<?> type = category.getRecipeType();
        for (ItemStack cat : JeiRecipeCards.recipeTypeCatalysts(recipes, type, 64)) {
            if (sameItem(cat, stack)) {
                return "typeLookup";
            }
        }
        ItemStack icon = categoryIconItem(category);
        if (sameItem(icon, stack)) {
            return "icon";
        }
        return "";
    }

    private static boolean isWorkstationForCategory(
            IRecipeManager recipes, IRecipeCategory<?> category, ItemStack stack
    ) {
        return !workstationPathForCategory(recipes, category, stack).isEmpty();
    }

    private static boolean sameItem(ItemStack a, ItemStack b) {
        return a != null && b != null && !a.isEmpty() && !b.isEmpty() && a.getItem() == b.getItem();
    }

    /**
     * Best-effort ItemStack from JEI category icon. API only exposes {@link IDrawable};
     * JEI's DrawableIngredient holds the stack (1.19 field {@code ingredient}, 1.21+
     * {@code typedIngredient}). Reflection — compileOnly API jar has no DrawableIngredient.
     */
    private static ItemStack categoryIconItem(IRecipeCategory<?> category) {
        try {
            IDrawable icon = category.getIcon();
            if (icon == null) {
                return ItemStack.EMPTY;
            }
            for (Class<?> c = icon.getClass(); c != null; c = c.getSuperclass()) {
                if (!"DrawableIngredient".equals(c.getSimpleName())) {
                    continue;
                }
                try {
                    var typedField = c.getDeclaredField("typedIngredient");
                    typedField.setAccessible(true);
                    Object ti = typedField.get(icon);
                    if (ti instanceof ITypedIngredient<?> typed) {
                        return typed.getItemStack().orElse(ItemStack.EMPTY);
                    }
                } catch (NoSuchFieldException ignored) {
                    // JEI 11 / 1.19.2 uses raw ingredient field
                }
                try {
                    var ingField = c.getDeclaredField("ingredient");
                    ingField.setAccessible(true);
                    Object ing = ingField.get(icon);
                    if (ing instanceof ItemStack s) {
                        return s;
                    }
                    if (ing instanceof ITypedIngredient<?> typed) {
                        return typed.getItemStack().orElse(ItemStack.EMPTY);
                    }
                } catch (NoSuchFieldException ignored) {
                    // no item icon
                }
                break;
            }
        } catch (Throwable t) {
            PackAiMod.LOGGER.debug("JEI category icon read skipped: {}", t.toString());
        }
        return ItemStack.EMPTY;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String summarizeUnsafe(ItemStack stack) {
        String lang = ReplyLang.current();
        Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
        if (opt.isEmpty()) {
            return null;
        }
        IJeiRuntime runtime = opt.get();
        IRecipeManager recipes = runtime.getRecipeManager();
        IIngredientManager ingredients = runtime.getIngredientManager();
        IFocusFactory focuses = runtime.getJeiHelpers().getFocusFactory();

        IFocus<ItemStack> asOutput = focuses.createFocus(
                RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack.copy());
        IFocus<ItemStack> asInput = focuses.createFocus(
                RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, stack.copy());
        IFocus<ItemStack> asCatalyst = focuses.createFocus(
                RecipeIngredientRole.CATALYST, VanillaTypes.ITEM_STACK, stack.copy());

        String itemName = stack.getHoverName().getString();
        String itemId = "";
        var key = Registry.ITEM.getKey(stack.getItem());
        if (key != null) {
            itemId = key.toString();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(ReplyLang.jeiHeader(lang, itemName, itemId, JeiUniversalSpam.skipReasonLabel(lang)));
        sb.append(CraftPriority.preferenceHint(lang)).append('\n');

        int[] totals = {0, 0}; // useful, skipped
        appendSection(sb, recipes, ingredients, asOutput, stack, RecipeIngredientRole.OUTPUT,
                ReplyLang.jeiSectionRecipes(lang), totals, lang);
        appendSection(sb, recipes, ingredients, asInput, stack, RecipeIngredientRole.INPUT,
                ReplyLang.jeiSectionUses(lang), totals, lang);
        appendSection(sb, recipes, ingredients, asCatalyst, stack, RecipeIngredientRole.CATALYST,
                ReplyLang.jeiSectionCatalyst(lang), totals, lang);

        if (totals[0] == 0 && totals[1] == 0) {
            return ReplyLang.jeiEmpty(lang, itemName);
        }
        if (totals[0] == 0) {
            sb.append(ReplyLang.jeiZeroUseful(lang, totals[1]));
        } else {
            sb.append(ReplyLang.jeiTotals(lang, totals[0], totals[1]));
        }

        String out = sb.toString().trim();
        int maxChars = PackAiConfig.maxJeiChars();
        if (out.length() > maxChars) {
            out = out.substring(0, maxChars) + ReplyLang.jeiTruncated(lang, totals[0]);
        }
        PackAiMod.LOGGER.debug(
                "JEI summarize {} useful={} skipped={} chars={}",
                itemId.isEmpty() ? itemName : itemId, totals[0], totals[1], out.length());
        return out;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void appendSection(
            StringBuilder sb,
            IRecipeManager recipes,
            IIngredientManager ingredients,
            IFocus<ItemStack> focus,
            ItemStack focusStack,
            RecipeIngredientRole matchRole,
            String title,
            int[] totals,
            String lang
    ) {
        List<IRecipeCategory<?>> categories;
        if (focus != null) {
            // includeHidden: JEI GUI U/R still lists recipes whose output item is hidden.
            var lookup = recipes.createRecipeCategoryLookup().limitFocus(List.of(focus)).includeHidden();
            categories = new ArrayList<>(lookup.get().toList());
        } else {
            categories = workstationCategories(recipes, focusStack);
        }
        categories.removeIf(c -> {
            String uid = JeiCategoryCatalog.categoryUid(c);
            return RecipeCategoryPrefs.isHidden(uid);
        });
        categories.sort(Comparator
                .comparingInt((IRecipeCategory<?> c) -> CraftPriority.askEaseBand(c.getTitle().getString()))
                .thenComparingInt(c -> RecipeCategoryPrefs.sortKey(
                        JeiCategoryCatalog.categoryUid(c), c.getTitle().getString()))
                .thenComparingInt(c -> CraftPriority.speedTier(c.getTitle().getString()))
                .thenComparing(c -> c.getTitle().getString()));
        if (categories.isEmpty()) {
            return;
        }

        String skipLabel = JeiUniversalSpam.skipReasonLabel(lang);
        StringBuilder section = new StringBuilder();
        boolean anyUseful = false;
        List<String> includedCats = new ArrayList<>();
        for (IRecipeCategory<?> category : categories) {
            RecipeType type = category.getRecipeType();
            IRecipeCategory cat = category;
            String catTitle = category.getTitle().getString();

            if (JeiUniversalSpam.isSpamCategory(type, catTitle)) {
                long n = focus != null
                        ? recipes.createRecipeLookup(type).limitFocus(List.of(focus)).includeHidden().get()
                                .limit(MAX_SCAN_PER_CAT + 1L).count()
                        : recipes.createRecipeLookup(type).includeHidden().get()
                                .limit(MAX_SCAN_PER_CAT + 1L).count();
                int skipped = (int) Math.min(n, MAX_SCAN_PER_CAT);
                totals[1] += skipped;
                section.append(ReplyLang.jeiSkipped(lang, catTitle, skipped, skipLabel));
                continue;
            }
            // Quests / info / ponder: never treat as machine catalyst I/O.
            if (matchRole == RecipeIngredientRole.CATALYST
                    && JeiUniversalSpam.isNonMachineCategory(type, catTitle)) {
                continue;
            }
            // How-to-get dump: skip quest-book cats unless preferObtain=quest.
            // Cards still collect Quests last; pack-index loot must lead prose.
            // Title+uid: zh_cn 「任务」 and FTB uid without the word quest in the title.
            if (matchRole == RecipeIngredientRole.OUTPUT
                    && CraftPriority.isQuestCategory(catTitle, JeiCategoryCatalog.categoryUid(category))
                    && !"quest".equals(PackAiConfig.preferObtain())) {
                continue;
            }

            // Type catalysts (furnace/smelting): not in recipe layouts — limitFocus(CATALYST)
            // often returns 0; dump the category unfocused. Keep scan small for huge cats.
            int scanCap = matchRole == RecipeIngredientRole.CATALYST
                    ? Math.min(48, MAX_SCAN_PER_CAT)
                    : MAX_SCAN_PER_CAT;
            List<?> found;
            if (matchRole == RecipeIngredientRole.CATALYST) {
                // Unfocused + includeHidden: type-catalyst cats often have recipes JEI-hidden by packs.
                found = recipes.createRecipeLookup(type).includeHidden().get()
                        .limit(scanCap + 1L).toList();
            } else if (focus != null) {
                found = recipes.createRecipeLookup(type).limitFocus(List.of(focus)).includeHidden().get()
                        .limit(scanCap + 1L).toList();
            } else {
                found = recipes.createRecipeLookup(type).get().limit(scanCap + 1L).toList();
            }
            boolean hitCap = found.size() > scanCap;
            if (hitCap) {
                found = found.subList(0, scanCap);
            }

            LinkedHashSet<String> unique = new LinkedHashSet<>();
            Map<String, Integer> outIdCounts = new HashMap<>();
            int spamOut = 0;
            int spam = 0;
            int useful = 0;
            List<ItemStack> typeCats = JeiRecipeCards.recipeTypeCatalysts(recipes, type, 2);
            if (focus == null || matchRole == RecipeIngredientRole.CATALYST) {
                // Ensure workstation name on I/O lines (type catalyst / icon-only).
                typeCats = JeiRecipeCards.mergeItemStacksById(List.of(focusStack.copy()), typeCats, 2);
            }
            for (Object recipe : found) {
                try {
                    JeiRecipeLayoutCollector.CollectedLayout layout = JeiRecipeLayoutCollector.collect(cat, recipe, ingredients);
                    // CATALYST: type-level workstation — do not require layout catalyst slots.
                    if (focus != null
                            && matchRole != RecipeIngredientRole.CATALYST
                            && !JeiFocusMatch.roleMatchesFocus(layout, focusStack, matchRole, recipe)) {
                        continue;
                    }
                    if (PackAiConfig.hideUpgradeRecipes()
                            && JeiFocusMatch.focusAppearsAsInputAndOutput(layout, focusStack)) {
                        continue;
                    }
                    if (involvesSpamItem(layout)) {
                        spam++;
                        bumpOutIds(outIdCounts, layout);
                        continue;
                    }
                    unique.add(formatRecipe(recipe, layout, ingredients, catTitle, lang, focusStack, typeCats));
                    useful++;
                    bumpOutIds(outIdCounts, layout);
                } catch (Exception e) {
                    unique.add(catTitle);
                    useful++;
                }
            }

            for (Map.Entry<String, Integer> entry : outIdCounts.entrySet()) {
                if (JeiUniversalSpam.isSpamItemId(entry.getKey())) {
                    spamOut += entry.getValue();
                }
            }

            if (found.size() >= UNIVERSAL_MIN_RAW
                    && spamOut * 100 >= found.size() * UNIVERSAL_SAME_OUT_PCT) {
                totals[1] += found.size();
                section.append(ReplyLang.jeiSkipped(lang, catTitle, found.size(), skipLabel));
                continue;
            }

            String dominant = dominantKey(outIdCounts);
            int dominantCount = dominant == null ? 0 : outIdCounts.getOrDefault(dominant, 0);
            if (found.size() >= UNIVERSAL_MIN_RAW
                    && dominant != null
                    && JeiUniversalSpam.isSpamItemId(dominant)
                    && dominantCount * 100 >= found.size() * UNIVERSAL_SAME_OUT_PCT) {
                totals[1] += found.size();
                section.append(ReplyLang.jeiSkipped(lang, catTitle, found.size(), dominant));
                continue;
            }

            totals[0] += useful;
            totals[1] += spam;
            if (useful == 0) {
                if (spam > 0) {
                    section.append(ReplyLang.jeiSkippedGeneric(lang, catTitle, spam));
                }
                continue;
            }

            anyUseful = true;
            includedCats.add(Plainify.stripMcFormat(catTitle) + "(" + useful + ")");
            String header = ReplyLang.jeiCatCount(
                    lang, catTitle, useful, unique.size() != useful ? unique.size() : null, spam, hitCap, scanCap);
            if (header.endsWith("\n")) {
                header = header.substring(0, header.length() - 1);
            }
            section.append(header).append("：\n");
            int omitted = Math.max(0, unique.size() - MAX_LISTED_PER_CAT);
            String more = omitted > 0 ? ReplyLang.jeiCatMore(lang, omitted, catTitle) : null;
            for (String detail : capListedDetails(new ArrayList<>(unique), MAX_LISTED_PER_CAT, more)) {
                section.append("  - ").append(detail).append('\n');
            }
        }

        if (!includedCats.isEmpty()) {
            PackAiMod.LOGGER.debug("JEI {} cats: {}", title, String.join(", ", includedCats));
        }

        if (anyUseful || section.length() > 0) {
            sb.append(title).append("：\n").append(section);
        }
    }

    private static boolean involvesSpamItem(JeiRecipeLayoutCollector.CollectedLayout layout) {
        for (RecipeIngredientRole role : List.of(
                RecipeIngredientRole.INPUT,
                RecipeIngredientRole.OUTPUT,
                RecipeIngredientRole.CATALYST)) {
            for (ItemStack stack : layout.itemStacks(role)) {
                ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
                if (key != null && JeiUniversalSpam.isSpamItemId(key.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void bumpOutIds(Map<String, Integer> counts, JeiRecipeLayoutCollector.CollectedLayout layout) {
        for (ItemStack stack : layout.itemStacks(RecipeIngredientRole.OUTPUT)) {
            ResourceLocation key = Registry.ITEM.getKey(stack.getItem());
            if (key == null) {
                continue;
            }
            counts.merge(key.toString(), 1, Integer::sum);
        }
    }

    private static String dominantKey(Map<String, Integer> counts) {
        String best = null;
        int n = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > n) {
                n = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    private static String formatRecipe(
            Object recipe,
            JeiRecipeLayoutCollector.CollectedLayout layout,
            IIngredientManager ingredients,
            String catTitle,
            String lang,
            ItemStack focusStack,
            List<ItemStack> typeCatalysts
    ) {
        int inputSlots = 0;
        for (JeiRecipeLayoutCollector.CollectedSlot slot : layout.slots(RecipeIngredientRole.INPUT)) {
            if (!layout.itemsInSlot(slot).isEmpty()) {
                inputSlots++;
            }
        }
        int maxIn = inputSlots > 9 ? MAX_INPUT_LABELS_LARGE : MAX_INPUT_LABELS_SMALL;
        List<String> inputs = labelsFromRecipeOrLayout(
                recipe, layout, RecipeIngredientRole.INPUT, maxIn, focusStack, inputSlots > 9);
        List<String> outputs = labels(layout.itemStacksOnePerSlot(RecipeIngredientRole.OUTPUT, focusStack), 4);
        addUniqueLabels(outputs, extraOutputLabels(layout, ingredients, RecipeIngredientRole.OUTPUT, 4), 8);
        List<ItemStack> catStacks = JeiRecipeCards.mergeItemStacksById(
                layout.itemStacksOnePerSlot(RecipeIngredientRole.CATALYST, focusStack),
                typeCatalysts,
                2);
        List<String> catalysts = labels(catStacks, 2);
        String join = ReplyLang.sourceJoin(lang);
        String in = inputs.isEmpty() ? ReplyLang.jeiNoMats(lang) : String.join(join, inputs);
        String out = outputs.isEmpty() ? ReplyLang.jeiNoOut(lang) : String.join(join, outputs);
        String prefix = inputSlots > 9 ? ("[" + inputSlots + " slots] ") : "";
        if (!catalysts.isEmpty()) {
            return prefix + ReplyLang.jeiMachineLine(lang, String.join(join, catalysts), in, out);
        }
        return prefix + in + " → " + out;
    }

    private static List<String> labelsFromRecipeOrLayout(
            Object recipe,
            JeiRecipeLayoutCollector.CollectedLayout layout,
            RecipeIngredientRole role,
            int max,
            ItemStack prefer,
            boolean keepCounts
    ) {
        List<Ingredient> crafting = role == RecipeIngredientRole.INPUT ? craftingIngredients(recipe) : null;
        if (crafting != null && !crafting.isEmpty() && !keepCounts) {
            LinkedHashSet<String> uniq = new LinkedHashSet<>();
            String lang = ReplyLang.current();
            for (Ingredient ingredient : crafting) {
                if (uniq.size() >= max) {
                    break;
                }
                if (ingredient == null || ingredient.isEmpty()) {
                    continue;
                }
                String label = IngredientReqHints.labelForIngredient(ingredient, lang, prefer);
                if (!label.isEmpty()) {
                    uniq.add(label);
                }
            }
            if (!uniq.isEmpty()) {
                return new ArrayList<>(uniq);
            }
        }
        // Layout path: one label per JEI slot; large grids keep multiplicity as Name×N.
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        String lang = ReplyLang.current();
        for (JeiRecipeLayoutCollector.CollectedSlot slot : layout.slots(role)) {
            List<ItemStack> alts = layout.itemsInSlot(slot);
            if (alts.isEmpty()) {
                continue;
            }
            String label = IngredientReqHints.labelForAlternatives(alts, prefer, lang);
            if (label.isEmpty()) {
                continue;
            }
            if (keepCounts) {
                counts.merge(label, 1, Integer::sum);
            } else if (counts.size() < max && !counts.containsKey(label)) {
                counts.put(label, 1);
            }
            if (!keepCounts && counts.size() >= max) {
                break;
            }
        }
        if (!counts.isEmpty()) {
            return formatCountedLabels(counts, max);
        }
        return labels(layout.itemStacksOnePerSlot(role, prefer), max);
    }

    static List<String> formatCountedLabels(LinkedHashMap<String, Integer> counts, int max) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (out.size() >= max) {
                break;
            }
            int n = e.getValue() == null ? 1 : e.getValue();
            out.add(n > 1 ? e.getKey() + "×" + n : e.getKey());
        }
        return out;
    }

    private static List<Ingredient> craftingIngredients(Object recipe) {
        if (!(recipe instanceof CraftingRecipe crafting)) {
            return null;
        }
        try {
            var ingredients = crafting.getIngredients();
            if (ingredients == null || ingredients.isEmpty()) {
                return null;
            }
            return List.copyOf(ingredients);
        } catch (Exception ignored) {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<String> extraOutputLabels(
            JeiRecipeLayoutCollector.CollectedLayout layout,
            IIngredientManager manager,
            RecipeIngredientRole role,
            int max
    ) {
        List<String> names = new ArrayList<>();
        if (layout == null || max <= 0) {
            return names;
        }
        for (FluidStack fluid : layout.fluidsOnePerSlot(role)) {
            if (names.size() >= max) {
                break;
            }
            if (fluid == null || fluid.isEmpty()) {
                continue;
            }
            try {
                String n = Plainify.stripMcFormat(fluid.getDisplayName().getString());
                if (n != null && !n.isBlank()) {
                    names.add(n);
                }
            } catch (Throwable ignored) {
                // skip broken fluid label
            }
        }
        if (manager == null) {
            return names;
        }
        for (JeiRecipeLayoutCollector.CollectedIngredient typed : layout.othersOnePerSlot(role)) {
            if (names.size() >= max) {
                break;
            }
            if (typed.type() == null || typed.ingredient() == null) {
                continue;
            }
            try {
                IIngredientHelper helper = manager.getIngredientHelper((IIngredientType) typed.type());
                Object ingredient = typed.ingredient();
                if (!helper.isValidIngredient(ingredient)) {
                    continue;
                }
                String n = Plainify.stripMcFormat(String.valueOf(helper.getDisplayName(ingredient)));
                if (n.isBlank() || "null".equalsIgnoreCase(n)) {
                    continue;
                }
                names.add(n);
            } catch (Throwable ignored) {
                // skip unknown ingredient types
            }
        }
        return names;
    }

    private static void addUniqueLabels(List<String> dest, List<String> more, int max) {
        if (dest == null || more == null) {
            return;
        }
        for (String s : more) {
            if (dest.size() >= max) {
                return;
            }
            if (s != null && !s.isBlank() && !dest.contains(s)) {
                dest.add(s);
            }
        }
    }

    private static List<String> labels(List<ItemStack> stacks, int max) {
        Set<String> uniq = new LinkedHashSet<>();
        String lang = ReplyLang.current();
        IngredientReqHints.ExtrasMode mode =
                IngredientReqHints.modeForPolicy(PackAiConfig.ingredientNbtPolicy(), true);
        for (ItemStack stack : stacks) {
            if (uniq.size() >= max) {
                break;
            }
            if (stack != null && !stack.isEmpty()) {
                uniq.add(IngredientReqHints.richLabel(stack, lang, mode));
            }
        }
        return new ArrayList<>(uniq);
    }
}
