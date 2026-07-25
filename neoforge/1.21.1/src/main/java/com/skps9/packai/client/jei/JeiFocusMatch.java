package com.skps9.packai.client.jei;

import java.util.Locale;

import com.skps9.packai.logic.Plainify;

import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Match JEI recipe outputs to a focused stack (SlashBlade-style same id / different NBT).
 */
public final class JeiFocusMatch {
    private JeiFocusMatch() {}

    public static boolean outputMatchesFocus(IIngredientSupplier supplier, ItemStack focus) {
        return roleMatchesFocus(supplier, focus, RecipeIngredientRole.OUTPUT);
    }

    /**
     * True if any ingredient in {@code role} matches focus by same components, else by
     * display name when the focus name is distinctive.
     */
    public static boolean roleMatchesFocus(
            IIngredientSupplier supplier, ItemStack focus, RecipeIngredientRole role
    ) {
        if (supplier == null || focus == null || focus.isEmpty() || role == null) {
            return true;
        }
        String focusName = normName(focus.getHoverName().getString());
        String focusId = itemId(focus);
        boolean nameUseful = nameUseful(focusName, focusId);

        boolean any = false;
        for (ITypedIngredient<?> typed : supplier.getIngredients(role)) {
            var opt = typed.getItemStack();
            if (opt.isEmpty() || opt.get().isEmpty()) {
                continue;
            }
            any = true;
            ItemStack stack = opt.get();
            if (ItemStack.isSameItemSameComponents(stack, focus)) {
                return true;
            }
            if (nameUseful && focusName.equals(normName(stack.getHoverName().getString()))) {
                return true;
            }
        }
        // No item ingredients in this role — keep
        return !any;
    }

    public static boolean sameRegistryId(ItemStack stack, String id) {
        if (stack == null || stack.isEmpty() || id == null || id.isBlank()) {
            return false;
        }
        return id.trim().equalsIgnoreCase(itemId(stack));
    }

    private static boolean nameUseful(String name, String id) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        if (id != null) {
            String path = id;
            int c = id.indexOf(':');
            if (c >= 0) {
                path = id.substring(c + 1);
            }
            if (n.equals(path.toLowerCase(Locale.ROOT)) || n.equals(id.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        // Generic fallback names are useless for NBT variants
        return !"slashblade".equals(n) && !"item".equals(n);
    }

    private static String normName(String raw) {
        return Plainify.stripMcFormat(raw == null ? "" : raw).trim();
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }
}
