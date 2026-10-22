package com.skps9.packai.client.gui;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class MachineIcons {
    private static final Map<ResourceLocation, ResourceLocation> TYPE_TO_ITEM = new HashMap<>();

    static {
        map("minecraft", "smelting", "minecraft", "furnace");
        map("minecraft", "blasting", "minecraft", "blast_furnace");
        map("minecraft", "smoking", "minecraft", "smoker");
        map("minecraft", "campfire_cooking", "minecraft", "campfire");
        map("minecraft", "crafting", "minecraft", "crafting_table");
        map("minecraft", "crafting_shaped", "minecraft", "crafting_table");
        map("minecraft", "crafting_shapeless", "minecraft", "crafting_table");
        map("minecraft", "stonecutting", "minecraft", "stonecutter");
        map("minecraft", "smithing", "minecraft", "smithing_table");

        map("create", "pressing", "create", "mechanical_press");
        map("create", "mixing", "create", "mechanical_mixer");
        map("create", "milling", "create", "millstone");
        map("create", "cutting", "create", "mechanical_saw");
        map("create", "sawing", "create", "mechanical_saw");
        map("create", "filling", "create", "spout");
        map("create", "compacting", "create", "mechanical_press");
        map("create", "deploying", "create", "deployer");

        map("thermal", "smelter", "thermal", "machine_smelter");
        map("thermal", "pulverizer", "thermal", "machine_pulverizer");
        map("thermal", "centrifuge", "thermal", "machine_centrifuge");
        map("thermal", "crucible", "thermal", "machine_crucible");
        map("thermal", "press", "thermal", "machine_press");

        map("mekanism", "chemical_reactor", "mekanism", "chemical_reactor");
        map("mekanism", "enrichment_chamber", "mekanism", "enrichment_chamber");
        map("mekanism", "metallurgic_infuser", "mekanism", "metallurgic_infuser");
        map("mekanism", "electrolytic_separator", "mekanism", "electrolytic_separator");

        map("immersiveengineering", "metal_press", "immersiveengineering", "metal_press");
        map("immersiveengineering", "coke_oven", "immersiveengineering", "coke_oven");
        map("immersiveengineering", "blast_furnace", "immersiveengineering", "blast_furnace");

        map("ae2", "inscriber", "ae2", "inscriber");

        map("botania", "runic_altar", "botania", "runic_altar");
        map("botania", "mana_pool", "botania", "mana_pool");

        map("farmersdelight", "cooking", "farmersdelight", "cooking_pot");
        map("farmersdelight", "cutting", "farmersdelight", "cutting_board");
    }

    private MachineIcons() {}

    private static void map(String typeNs, String typePath, String itemNs, String itemPath) {
        TYPE_TO_ITEM.put(new ResourceLocation(typeNs, typePath), new ResourceLocation(itemNs, itemPath));
    }

    public static @Nullable ItemStack machineFor(@Nullable ResourceLocation typeId) {
        if (typeId == null) {
            return null;
        }
        ResourceLocation itemId = resolveItemId(typeId);
        if (itemId == null) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null) {
            return null;
        }
        ItemStack stack = new ItemStack(item);
        if (stack.isEmpty()) {
            return null;
        }
        return stack;
    }

    private static @Nullable ResourceLocation resolveItemId(ResourceLocation typeId) {
        ResourceLocation direct = TYPE_TO_ITEM.get(typeId);
        if (direct != null) {
            return direct;
        }
        String ns = typeId.getNamespace();
        String path = typeId.getPath();
        if (path.startsWith("crafting")) {
            return new ResourceLocation("minecraft", "crafting_table");
        }
        if (path.endsWith("smelting")) {
            return new ResourceLocation("minecraft", "furnace");
        }
        if (path.endsWith("blasting")) {
            return new ResourceLocation("minecraft", "blast_furnace");
        }
        if (path.endsWith("smoking")) {
            return new ResourceLocation("minecraft", "smoker");
        }
        if (path.endsWith("cutting")) {
            return TYPE_TO_ITEM.get(new ResourceLocation(ns, "cutting"));
        }
        if (path.contains("pressing")) {
            return new ResourceLocation("create", "mechanical_press");
        }
        if (path.contains("mixing")) {
            return new ResourceLocation("create", "mechanical_mixer");
        }
        return null;
    }
}
