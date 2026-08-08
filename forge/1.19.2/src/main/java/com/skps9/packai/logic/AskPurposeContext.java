package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Pair;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

/**
 * Splits item <em>purpose</em> (what it does / how you use it) from JEI-U
 * <em>as ingredient</em> (recipes that consume it). Guide-book facts use {@link #GUIDE_HEADER}.
 */
public final class AskPurposeContext {
    public static final String PURPOSE_HEADER = "[PURPOSE]";
    public static final String GUIDE_HEADER = "[GUIDE]";
    public static final String AS_INGREDIENT_HEADER = "[AS_INGREDIENT]";

    /** Cap listed tool actions so odd mod registries cannot flood the prompt. */
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
                || gf.contains("-[right_click_as_block]->");
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
     * @param guideFacts bare Patchouli／guide text (no header), or already starts with {@link #GUIDE_HEADER}
     */
    public static String buildPurposeBlock(String tooltip, List<String> purposeLines, String guideFacts) {
        List<String> body = new ArrayList<>();
        if (tooltip != null && !tooltip.isBlank()) {
            body.add(tooltip.trim());
        }
        if (purposeLines != null) {
            for (String line : purposeLines) {
                if (line != null && !line.isBlank()) {
                    body.add(line.trim());
                }
            }
        }
        StringBuilder out = new StringBuilder();
        if (!body.isEmpty()) {
            out.append(PURPOSE_HEADER).append('\n').append(String.join("\n", body));
        }
        String guide = normalizeGuide(guideFacts);
        if (!guide.isEmpty()) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(guide);
        }
        return out.toString();
    }

    /** Merge tooltip + fuel／tool-action／food lines for {@code user.purpose} tooltip slot. */
    public static String withItemBehavior(String tooltip, List<String> behaviorLines) {
        List<String> parts = new ArrayList<>();
        if (tooltip != null && !tooltip.isBlank()) {
            parts.add(tooltip.trim());
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
     * Live Forge facts: furnace burn time + ToolActions + edible/drinkable FoodProperties
     * + potion contents + MAINHAND AttributeModifiers. Soft-fails to empty list if APIs throw.
     * Does <em>not</em> decompile mod jars — custom {@code finishUsingItem} stays invisible unless
     * FoodProperties / potion NBT / tooltip / guides expose it.
     */
    public static List<String> itemBehaviorLines(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(5);
        try {
            int burn = ForgeHooks.getBurnTime(stack, RecipeType.SMELTING);
            if (burn > 0) {
                out.add(formatFuelLine(burn));
            }
        } catch (Throwable ignored) {
            // soft-fail: missing remaps / exotic stacks
        }
        try {
            // Ensure stock ToolActions are registered before iterating the map.
            ToolActions.AXE_DIG.name();
            List<String> names = new ArrayList<>();
            for (ToolAction action : ToolAction.getActions()) {
                if (action == null) {
                    continue;
                }
                if (stack.canPerformAction(action)) {
                    names.add(action.name());
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
            try {
                food = stack.getItem().getFoodProperties();
            } catch (Throwable ignored2) {
                // soft-fail
            }
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
            nutrition = food.getNutrition();
            saturation = food.getSaturationModifier();
            always = food.canAlwaysEat();
            fast = food.isFastFood();
            for (Pair<MobEffectInstance, Float> pair : food.getEffects()) {
                if (pair == null || pair.getFirst() == null) {
                    continue;
                }
                float chance = pair.getSecond() == null ? 1f : pair.getSecond();
                String spec = formatEffectSpec(pair.getFirst(), chance);
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
        MobEffect effect = inst.getEffect();
        ResourceLocation key = effect == null ? null : Registry.MOB_EFFECT.getKey(effect);
        String id = key != null ? key.toString() : (effect == null ? "unknown" : effect.getDescriptionId());
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

    /** Potion bottle / tipped NBT effects via {@link PotionUtils}. Empty when none. */
    static String potionContentsLine(ItemStack stack) {
        List<MobEffectInstance> effects;
        try {
            effects = PotionUtils.getMobEffects(stack);
        } catch (Throwable ignored) {
            return "";
        }
        if (effects == null || effects.isEmpty()) {
            return "";
        }
        List<String> specs = new ArrayList<>();
        for (MobEffectInstance inst : effects) {
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
        if (specs.isEmpty()) {
            return "";
        }
        String potionId = "";
        try {
            Potion potion = PotionUtils.getPotion(stack);
            ResourceLocation key = potion == null ? null : Registry.POTION.getKey(potion);
            if (key != null && !"minecraft:empty".equals(key.toString())) {
                potionId = key.toString();
            }
        } catch (Throwable ignored) {
            // soft-fail name only
        }
        boolean truncated = effects.size() > specs.size();
        StringBuilder sb = new StringBuilder("Potion contents");
        if (!potionId.isEmpty()) {
            sb.append(": ").append(potionId);
        }
        sb.append("; effects: ").append(String.join(", ", specs));
        if (truncated) {
            sb.append(", …");
        }
        return sb.toString();
    }

    /** MAINHAND AttributeModifiers only — empty when none / soft-fail. */
    static String attributeModifiersLine(ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map;
        try {
            map = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        } catch (Throwable ignored) {
            return "";
        }
        if (map == null || map.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (var entry : map.entries()) {
            Attribute attr = entry.getKey();
            AttributeModifier mod = entry.getValue();
            if (attr == null || mod == null) {
                continue;
            }
            ResourceLocation key = Registry.ATTRIBUTE.getKey(attr);
            String id = key != null ? key.toString() : attr.getDescriptionId();
            parts.add(id + " " + formatAttrAmount(mod));
            if (parts.size() >= MAX_ATTR_MODS) {
                break;
            }
        }
        if (parts.isEmpty()) {
            return "";
        }
        String joined = String.join(", ", parts);
        if (map.size() > parts.size()) {
            joined = joined + ", …";
        }
        return "Attribute modifiers (MAINHAND): " + joined;
    }

    static String formatAttrAmount(AttributeModifier mod) {
        double amount = mod.getAmount();
        String s = trimTrailingZeros(String.format(Locale.ROOT, "%.3f", amount));
        AttributeModifier.Operation op = mod.getOperation();
        if (op == AttributeModifier.Operation.MULTIPLY_BASE) {
            return "*base " + s;
        }
        if (op == AttributeModifier.Operation.MULTIPLY_TOTAL) {
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
        // Trim trailing zeros for prompt brevity (0.1 not 0.100000).
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
}
