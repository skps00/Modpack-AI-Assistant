package com.skps9.packai.client.jei;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.logic.ItemResolver;
import com.skps9.packai.logic.Plainify;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Resolve item icons by in-game display name (SlashBlade-style same-id / different NBT).
 */
public final class SuggestIcons {
    private SuggestIcons() {}

    /**
     * Prefer JEI ingredient whose hover name matches {@code displayName};
     * optionally restrict to {@code registryId}. Falls back to bare {@link ItemResolver#stackFromId}.
     */
    public static ItemStack resolve(String registryId, String displayName) {
        String name = displayName == null ? "" : displayName.trim();
        if (!name.isEmpty()) {
            ItemStack byName = findByDisplayName(name, registryId);
            if (!byName.isEmpty()) {
                return byName;
            }
        }
        return ItemResolver.stackFromId(registryId);
    }

    /** Resolve a stored suggestion ref {@code mod:id} or {@code mod:id|顯示名}. */
    public static ItemStack resolveRef(String ref) {
        return resolve(ItemResolver.idPart(ref), ItemResolver.namePart(ref));
    }

    public static String labelFor(String ref, ItemStack stack) {
        String hint = ItemResolver.namePart(ref);
        if (hint != null && !hint.isBlank()) {
            return Plainify.stripMcFormat(hint.trim());
        }
        if (stack != null && !stack.isEmpty()) {
            return Plainify.stripMcFormat(stack.getHoverName().getString());
        }
        return ItemResolver.idPart(ref);
    }

    private static ItemStack findByDisplayName(String displayName, String registryId) {
        if (!ModList.get().isLoaded("jei")) {
            return matchRegistryDefaults(displayName, registryId);
        }
        try {
            Optional<IJeiRuntime> opt = PackAiJeiPlugin.runtime();
            if (opt.isEmpty()) {
                return matchRegistryDefaults(displayName, registryId);
            }
            Collection<ItemStack> all = opt.get().getIngredientManager().getAllIngredients(VanillaTypes.ITEM_STACK);
            String want = norm(displayName);
            String wantId = registryId == null ? "" : registryId.trim().toLowerCase(Locale.ROOT);
            ItemStack idAndName = ItemStack.EMPTY;
            ItemStack nameOnly = ItemStack.EMPTY;
            for (ItemStack stack : all) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                if (!norm(stack.getHoverName().getString()).equals(want)) {
                    continue;
                }
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
                String id = key == null ? "" : key.toString();
                if (!wantId.isEmpty() && wantId.equals(id)) {
                    idAndName = stack.copy();
                    break;
                }
                if (nameOnly.isEmpty()) {
                    nameOnly = stack.copy();
                }
            }
            if (!idAndName.isEmpty()) {
                return idAndName;
            }
            if (!nameOnly.isEmpty()) {
                return nameOnly;
            }
        } catch (NoClassDefFoundError | Exception e) {
            PackAiMod.LOGGER.debug("SuggestIcons JEI lookup failed: {}", e.toString());
        }
        return matchRegistryDefaults(displayName, registryId);
    }

    /** Last resort: default stacks whose hover name matches (no NBT variants). */
    private static ItemStack matchRegistryDefaults(String displayName, String registryId) {
        String want = norm(displayName);
        if (!want.isEmpty() && registryId != null && !registryId.isBlank()) {
            ItemStack one = ItemResolver.stackFromId(registryId);
            if (!one.isEmpty() && norm(one.getHoverName().getString()).equals(want)) {
                return one;
            }
        }
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            ItemStack stack = new ItemStack(entry.getValue());
            if (stack.isEmpty()) {
                continue;
            }
            if (norm(stack.getHoverName().getString()).equals(want)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static String norm(String s) {
        if (s == null) {
            return "";
        }
        return Plainify.stripMcFormat(s).trim().replace('「', '"').replace('」', '"').toLowerCase(Locale.ROOT);
    }
}
