package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.Tags;

/** Best-effort anvil repair materials via Item#isValidRepairItem (Tier/Armor ingredients + tags). */
public final class AnvilRepairHint {
    private AnvilRepairHint() {}

    /** Best-effort anvil repair materials for a stack (game rule: Item#isValidRepairItem). */
    public static List<String> repairMaterials(ItemStack stack, String replyLang) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) {
            return List.of();
        }
        ItemStack damaged = stack.copy();
        if (damaged.getDamageValue() <= 0) {
            damaged.setDamageValue(Math.max(1, damaged.getMaxDamage() / 2));
        }
        LinkedHashSet<String> hits = new LinkedHashSet<>();
        LinkedHashSet<String> seenIds = new LinkedHashSet<>();
        for (Ingredient candidate : candidates()) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            for (ItemStack mat : candidate.getItems()) {
                if (mat == null || mat.isEmpty()) {
                    continue;
                }
                String id = mat.getItem().getDescriptionId();
                if (id != null && !seenIds.add(id)) {
                    continue;
                }
                if (damaged.getItem().isValidRepairItem(damaged, mat)) {
                    hits.add(displayName(mat, replyLang));
                    break;
                }
            }
            if (hits.size() >= 8) {
                break;
            }
        }
        return List.copyOf(hits);
    }

    private static String displayName(ItemStack mat, String replyLang) {
        try {
            String name = mat.getHoverName().getString();
            if (name != null && !name.isBlank()) {
                return name;
            }
        } catch (Throwable ignored) {
            // fall through
        }
        if (replyLang != null && replyLang.contains("zh")) {
            return mat.getItem().getDescriptionId();
        }
        return mat.getItem().getDescriptionId();
    }

    private static List<Ingredient> candidates() {
        List<Ingredient> out = new ArrayList<>();
        add(out, Tiers.WOOD.getRepairIngredient());
        add(out, Tiers.STONE.getRepairIngredient());
        add(out, Tiers.IRON.getRepairIngredient());
        add(out, Tiers.GOLD.getRepairIngredient());
        add(out, Tiers.DIAMOND.getRepairIngredient());
        add(out, Tiers.NETHERITE.getRepairIngredient());
        add(out, ArmorMaterials.LEATHER.getRepairIngredient());
        add(out, ArmorMaterials.CHAIN.getRepairIngredient());
        add(out, ArmorMaterials.IRON.getRepairIngredient());
        add(out, ArmorMaterials.GOLD.getRepairIngredient());
        add(out, ArmorMaterials.DIAMOND.getRepairIngredient());
        add(out, ArmorMaterials.NETHERITE.getRepairIngredient());
        add(out, ArmorMaterials.TURTLE.getRepairIngredient());
        add(out, Ingredient.of(ItemTags.PLANKS));
        try {
            add(out, Ingredient.of(Tags.Items.INGOTS));
        } catch (Throwable ignored) {
            // tag not bound yet
        }
        return out;
    }

    private static void add(List<Ingredient> out, Ingredient ing) {
        if (ing != null && !ing.isEmpty()) {
            out.add(ing);
        }
    }
}
