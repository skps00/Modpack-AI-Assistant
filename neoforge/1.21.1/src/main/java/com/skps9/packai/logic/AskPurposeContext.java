package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * Splits item <em>purpose</em> (what it does / how you use it) from JEI-U
 * <em>as ingredient</em> (recipes that consume it). Guide-book facts use {@link #GUIDE_HEADER}.
 */
public final class AskPurposeContext {
    public static final String PURPOSE_HEADER = "[PURPOSE]";
    public static final String GUIDE_HEADER = "[GUIDE]";
    public static final String AS_INGREDIENT_HEADER = "[AS_INGREDIENT]";

    /** Cap listed item abilities so odd mod registries cannot flood the prompt. */
    static final int MAX_TOOL_ACTIONS = 16;
    /** Cap listed food / potion effect specs. */
    static final int MAX_FOOD_EFFECTS = 8;
    /** Cap MAINHAND attribute-modifier entries in PURPOSE. */
    static final int MAX_ATTR_MODS = 6;

    private AskPurposeContext() {}

    /** Graph edges that describe function / interaction — not craft-input lists. */
    public static boolean isPurposeGraphFact(String gf) {
        if (gf == null || gf.isBlank()) {
            return false;
        }
        return gf.contains("-[desc]->")
                || gf.contains("-[score]->")
                || gf.contains("-[triggers]->")
                || gf.contains("-[on:")
                || gf.contains("-[right_click]->")
                || gf.contains("-[right_click_use]->")
                || gf.contains("-[right_click_as_block]->")
                || gf.contains("-[script_use]->")
                || gf.contains("-[consume_item]->");
    }

    /**
     * Build LLM purpose block. Empty string when nothing useful.
     *
     * @param tooltip      full focus-item tooltip (may be multi-line)
     * @param purposeLines already-humanized interact / desc lines
     */
    public static String buildPurposeBlock(String tooltip, List<String> purposeLines) {
        return buildPurposeBlock(tooltip, purposeLines, null);
    }

    /**
     * @param guideFacts bare Patchouli／GuideME text (no header), or already starts with {@link #GUIDE_HEADER}
     */
    public static String buildPurposeBlock(String tooltip, List<String> purposeLines, String guideFacts) {
        String guide = normalizeGuide(guideFacts);
        StringBuilder out = new StringBuilder();
        // Mechanics from [GUIDE] before tooltip flavor when both exist.
        if (!guide.isEmpty()) {
            out.append(guide);
        }
        List<String> body = new ArrayList<>();
        String cleaned = AskReplyScrub.scrubPackAiTooltipChrome(tooltip);
        if (cleaned != null && !cleaned.isBlank()) {
            body.add(cleaned.trim());
        }
        if (purposeLines != null) {
            for (String line : purposeLines) {
                if (line != null && !line.isBlank()) {
                    body.add(line.trim());
                }
            }
        }
        if (!body.isEmpty()) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(PURPOSE_HEADER).append('\n').append(String.join("\n", body));
        }
        return out.toString();
    }

    /** Merge tooltip + fuel／item-ability／food lines for {@code user.purpose} tooltip slot. */
    public static String withItemBehavior(String tooltip, List<String> behaviorLines) {
        List<String> parts = new ArrayList<>();
        String cleaned = AskReplyScrub.scrubPackAiTooltipChrome(tooltip);
        if (cleaned != null && !cleaned.isBlank()) {
            parts.add(cleaned.trim());
        }
        if (behaviorLines != null) {
            for (String line : behaviorLines) {
                if (line != null && !line.isBlank()) {
                    parts.add(line.trim());
                }
            }
        }
        return String.join("\n", parts);
    }

    /**
     * Live NeoForge facts: furnace burn time + ItemAbilities + edible/drinkable FoodProperties
     * + potion contents + MAINHAND AttributeModifiers. Soft-fails to empty list if APIs throw.
     * Does <em>not</em> decompile mod jars — custom {@code finishUsingItem} stays invisible unless
     * FoodProperties / potion components / tooltip / guides expose it.
     */
    public static List<String> itemBehaviorLines(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(5);
        try {
            int burn = stack.getBurnTime(RecipeType.SMELTING);
            if (burn > 0) {
                out.add(formatFuelLine(burn));
            }
        } catch (Throwable ignored) {
            // soft-fail: missing remaps / exotic stacks
        }
        try {
            // Ensure stock ItemAbilities are registered before iterating the map.
            ItemAbilities.AXE_DIG.name();
            List<String> names = new ArrayList<>();
            for (ItemAbility ability : ItemAbility.getActions()) {
                if (ability == null) {
                    continue;
                }
                if (stack.canPerformAction(ability)) {
                    names.add(ability.name());
                }
            }
            String line = formatToolActionsLine(names);
            if (!line.isEmpty()) {
                out.add(line);
            }
        } catch (Throwable ignored) {
            // soft-fail
        }
        try {
            String food = foodUseLine(stack);
            if (!food.isEmpty()) {
                out.add(food);
                if (!food.contains("effects:")) {
                    out.add(formatFoodEffectsGapLine());
                }
            }
        } catch (Throwable ignored) {
            // soft-fail
        }
        try {
            String potion = potionContentsLine(stack);
            if (!potion.isEmpty()) {
                out.add(potion);
            }
        } catch (Throwable ignored) {
            // soft-fail
        }
        try {
            String attrs = attributeModifiersLine(stack);
            if (!attrs.isEmpty()) {
                out.add(attrs);
            }
        } catch (Throwable ignored) {
            // soft-fail
        }
        try {
            if (stack.is(Items.MILK_BUCKET)) {
                out.add("Vanilla milk: clears all status effects on drink");
            }
        } catch (Throwable ignored) {
            // soft-fail
        }
        return out;
    }

    /** Read UseAnim + FoodProperties into {@link #formatFoodUseLine}. Soft-fails inside. */
    static String foodUseLine(ItemStack stack) {
        UseAnim anim = UseAnim.NONE;
        try {
            anim = stack.getUseAnimation();
        } catch (Throwable ignored) {
            // soft-fail
        }
        FoodProperties food = null;
        try {
            food = stack.getFoodProperties(null);
        } catch (Throwable ignored) {
            // soft-fail
        }
        boolean drink = anim == UseAnim.DRINK;
        boolean eat = anim == UseAnim.EAT;
        if (food == null && !drink && !eat) {
            return "";
        }
        String kind = drink ? "drink" : "eat";
        int nutrition = 0;
        float saturation = 0f;
        boolean always = false;
        boolean fast = false;
        List<String> effects = new ArrayList<>();
        if (food != null) {
            nutrition = food.nutrition();
            saturation = food.saturation();
            always = food.canAlwaysEat();
            // Vanilla default eatSeconds is 1.6f; faster = "fast eat".
            try {
                fast = food.eatSeconds() < 1.6f;
            } catch (Throwable ignored) {
                // soft-fail: older FoodProperties shape
            }
            for (FoodProperties.PossibleEffect pe : food.effects()) {
                if (pe == null) {
                    continue;
                }
                MobEffectInstance inst = pe.effect();
                float chance = pe.probability();
                String spec = formatEffectSpec(inst, chance);
                if (!spec.isEmpty()) {
                    effects.add(spec);
                }
                if (effects.size() >= MAX_FOOD_EFFECTS) {
                    break;
                }
            }
        }
        return formatFoodUseLine(kind, nutrition, saturation, always, fast, effects);
    }

    static String formatEffectSpec(MobEffectInstance inst, float chance) {
        if (inst == null) {
            return "";
        }
        Holder<MobEffect> holder = inst.getEffect();
        ResourceLocation key = holder == null ? null : BuiltInRegistries.MOB_EFFECT.getKey(holder.value());
        String id = key != null
                ? key.toString()
                : (holder == null ? "unknown" : holder.getRegisteredName());
        int pct = Math.round(Math.max(0f, Math.min(1f, chance)) * 100f);
        return id + "@" + inst.getAmplifier() + " " + inst.getDuration() + "t (" + pct + "%)";
    }

    /**
     * Honest gap when Drinkable/Edible is known but FoodProperties lists no status effects.
     * Custom mod {@code finishUsingItem} is not visible at Ask-time.
     */
    public static String formatFoodEffectsGapLine() {
        return "Effects not in FoodProperties; check item tooltip / quest book / mod docs"
                + " (custom finishUsing not readable at Ask-time)";
    }

    /** PotionContents component effects. Empty when none. */
    static String potionContentsLine(ItemStack stack) {
        PotionContents contents;
        try {
            contents = stack.get(DataComponents.POTION_CONTENTS);
        } catch (Throwable ignored) {
            return "";
        }
        if (contents == null) {
            return "";
        }
        List<String> specs = new ArrayList<>();
        try {
            for (MobEffectInstance inst : contents.getAllEffects()) {
                if (inst == null) {
                    continue;
                }
                String spec = formatEffectSpec(inst, 1f);
                if (!spec.isEmpty()) {
                    specs.add(spec);
                }
                if (specs.size() >= MAX_FOOD_EFFECTS) {
                    break;
                }
            }
        } catch (Throwable ignored) {
            return "";
        }
        if (specs.isEmpty()) {
            return "";
        }
        String potionId = "";
        try {
            Holder<Potion> holder = contents.potion().orElse(null);
            if (holder != null) {
                ResourceLocation key = BuiltInRegistries.POTION.getKey(holder.value());
                if (key != null && !"minecraft:empty".equals(key.toString())) {
                    potionId = key.toString();
                }
            }
        } catch (Throwable ignored) {
            // soft-fail name only
        }
        StringBuilder sb = new StringBuilder("Potion contents");
        if (!potionId.isEmpty()) {
            sb.append(": ").append(potionId);
        }
        sb.append("; effects: ").append(String.join(", ", specs));
        return sb.toString();
    }

    /** MAINHAND AttributeModifiers only — empty when none / soft-fail. */
    static String attributeModifiersLine(ItemStack stack) {
        ItemAttributeModifiers mods;
        try {
            mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        } catch (Throwable ignored) {
            return "";
        }
        if (mods == null || mods.modifiers().isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        int seen = 0;
        for (ItemAttributeModifiers.Entry entry : mods.modifiers()) {
            if (entry == null) {
                continue;
            }
            seen++;
            EquipmentSlotGroup slot = entry.slot();
            if (slot != EquipmentSlotGroup.ANY
                    && slot != EquipmentSlotGroup.MAINHAND
                    && slot != EquipmentSlotGroup.HAND) {
                continue;
            }
            Holder<Attribute> attr = entry.attribute();
            AttributeModifier mod = entry.modifier();
            if (attr == null || mod == null) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.ATTRIBUTE.getKey(attr.value());
            String id = key != null ? key.toString() : attr.getRegisteredName();
            parts.add(id + " " + formatAttrAmount(mod));
            if (parts.size() >= MAX_ATTR_MODS) {
                break;
            }
        }
        if (parts.isEmpty()) {
            return "";
        }
        String joined = String.join(", ", parts);
        if (seen > parts.size()) {
            joined = joined + ", …";
        }
        return "Attribute modifiers (MAINHAND): " + joined;
    }

    static String formatAttrAmount(AttributeModifier mod) {
        double amount = mod.amount();
        String s = trimTrailingZeros(String.format(Locale.ROOT, "%.3f", amount));
        AttributeModifier.Operation op = mod.operation();
        if (op == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
            return "*base " + s;
        }
        if (op == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
            return "*total " + s;
        }
        return (amount >= 0 ? "+" : "") + s;
    }

    static String trimTrailingZeros(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && s.charAt(end - 1) == '.') {
            end--;
        }
        return end > 0 ? s.substring(0, end) : "0";
    }

    /** {@code Furnace fuel: 1600 ticks (~80s)} — empty when {@code burnTicks <= 0}. */
    public static String formatFuelLine(int burnTicks) {
        if (burnTicks <= 0) {
            return "";
        }
        int seconds = burnTicks / 20;
        return "Furnace fuel: " + burnTicks + " ticks (~" + seconds + "s)";
    }

    /**
     * {@code Drinkable food: nutrition 0, saturation 0.0; effects: …} or
     * {@code Drinkable (hold right-click to drink)} when UseAnim only.
     */
    public static String formatFoodUseLine(
            String useKind,
            int nutrition,
            float saturation,
            boolean alwaysEdible,
            boolean fastEat,
            List<String> effectSpecs
    ) {
        List<String> specs = new ArrayList<>();
        if (effectSpecs != null) {
            for (String s : effectSpecs) {
                if (s != null && !s.isBlank()) {
                    specs.add(s.trim());
                }
            }
        }
        boolean hasStats =
                nutrition > 0
                        || saturation > 0f
                        || alwaysEdible
                        || fastEat
                        || !specs.isEmpty();
        String kind = useKind == null || useKind.isBlank()
                ? ""
                : useKind.trim().toLowerCase(Locale.ROOT);
        if (!hasStats && kind.isEmpty()) {
            return "";
        }
        if (!hasStats) {
            if ("drink".equals(kind)) {
                return "Drinkable (hold right-click to drink)";
            }
            if ("eat".equals(kind)) {
                return "Edible (hold right-click to eat)";
            }
            return "Consumable (hold right-click)";
        }
        String verb = "drink".equals(kind) ? "Drinkable" : "Edible";
        StringBuilder sb = new StringBuilder();
        sb.append(verb)
                .append(" food: nutrition ")
                .append(nutrition)
                .append(", saturation ")
                .append(formatSaturation(saturation));
        if (alwaysEdible) {
            sb.append("; always edible");
        }
        if (fastEat) {
            sb.append("; fast eat");
        }
        if (!specs.isEmpty()) {
            boolean truncated = specs.size() > MAX_FOOD_EFFECTS;
            if (truncated) {
                specs = new ArrayList<>(specs.subList(0, MAX_FOOD_EFFECTS));
            }
            sb.append("; effects: ").append(String.join(", ", specs));
            if (truncated) {
                sb.append(", …");
            }
        }
        return sb.toString();
    }

    static String formatSaturation(float saturation) {
        return trimTrailingZeros(String.format(Locale.ROOT, "%.3f", saturation));
    }

    /** {@code Tool actions: axe_dig, shovel_dig} — empty when no names. */
    public static String formatToolActionsLine(List<String> actionNames) {
        if (actionNames == null || actionNames.isEmpty()) {
            return "";
        }
        List<String> sorted = new ArrayList<>();
        for (String n : actionNames) {
            if (n != null && !n.isBlank()) {
                sorted.add(n.trim());
            }
        }
        if (sorted.isEmpty()) {
            return "";
        }
        Collections.sort(sorted);
        boolean truncated = sorted.size() > MAX_TOOL_ACTIONS;
        if (truncated) {
            sorted = new ArrayList<>(sorted.subList(0, MAX_TOOL_ACTIONS));
        }
        String joined = String.join(", ", sorted);
        if (truncated) {
            joined = joined + ", …";
        }
        return "Tool actions: " + joined;
    }

    private static String normalizeGuide(String guideFacts) {
        if (guideFacts == null || guideFacts.isBlank()) {
            return "";
        }
        String g = guideFacts.trim();
        if (g.startsWith(GUIDE_HEADER)) {
            return g;
        }
        return GUIDE_HEADER + "\n" + g;
    }

    /**
     * Peel JEI-U {@link #AS_INGREDIENT_HEADER} out of a full JEI dump so purpose asks
     * can list how-to-use before craft-input uses.
     *
     * @param jeiText               full JEI summarize text (recipes + uses + catalyst)
     * @param catalystSectionTitle  localized catalyst section title (may be blank)
     * @return {@code [0]}=get body without as-ingredient, {@code [1]}=as-ingredient section
     */
    public static String[] splitGetAndAsIngredient(String jeiText, String catalystSectionTitle) {
        if (jeiText == null || jeiText.isBlank()) {
            return new String[] {"", ""};
        }
        int u = indexOfHeaderLine(jeiText, AS_INGREDIENT_HEADER);
        if (u < 0) {
            return new String[] {jeiText.trim(), ""};
        }
        String before = jeiText.substring(0, u).stripTrailing();
        String fromU = jeiText.substring(u);
        int cut = -1;
        if (catalystSectionTitle != null && !catalystSectionTitle.isBlank()) {
            cut = indexOfHeaderLine(fromU, catalystSectionTitle.trim());
        }
        String uses;
        String after;
        if (cut > 0) {
            uses = fromU.substring(0, cut).stripTrailing();
            after = fromU.substring(cut).strip();
        } else {
            uses = fromU.strip();
            after = "";
        }
        String get = before;
        if (!after.isBlank()) {
            get = get.isBlank() ? after : get + "\n" + after;
        }
        return new String[] {get.strip(), uses};
    }

    /**
     * True when the JEI get-slice has real obtain/craft lines — not header, preference,
     * recipe-card hint, or skip-only chrome. {@code role=input} as-ingredient is not obtain.
     */
    public static boolean hasObtainRecipeBody(String getBody) {
        if (getBody == null || getBody.isBlank()) {
            return false;
        }
        for (String line : getBody.split("\n", -1)) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith("- ")) {
                return true;
            }
            if (t.contains("role=output") || t.contains("role=quest")) {
                return true;
            }
            if (t.contains(" → ") || t.contains(" -> ") || t.contains("→")) {
                return true;
            }
        }
        return false;
    }

    /** Index of a line that starts with {@code header} (start of text or after {@code \n}). */
    static int indexOfHeaderLine(String text, String header) {
        if (text == null || header == null || header.isEmpty()) {
            return -1;
        }
        if (text.startsWith(header)) {
            return 0;
        }
        int i = text.indexOf("\n" + header);
        return i < 0 ? -1 : i + 1;
    }
}
