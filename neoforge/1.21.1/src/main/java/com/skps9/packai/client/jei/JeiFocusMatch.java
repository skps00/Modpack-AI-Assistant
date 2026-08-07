package com.skps9.packai.client.jei;

import java.util.List;
import java.util.Locale;

import com.skps9.packai.logic.ItemVariantKeys;
import com.skps9.packai.logic.Plainify;

import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Match JEI recipe outputs/inputs to a focused stack (same id / NBT / tag ingredients).
 */
public final class JeiFocusMatch {
    private JeiFocusMatch() {}

    public static boolean outputMatchesFocus(IIngredientSupplier supplier, ItemStack focus) {
        return roleMatchesFocus(supplier, focus, RecipeIngredientRole.OUTPUT, null);
    }

    public static boolean roleMatchesFocus(
            IIngredientSupplier supplier, ItemStack focus, RecipeIngredientRole role
    ) {
        return roleMatchesFocus(supplier, focus, role, null);
    }

    /**
     * True if any ingredient in {@code role} matches focus by same components, else by
     * display name / variant tokens when the focus name or schematic is distinctive,
     * or crafting {@link Ingredient#test} accepts focus (tag slots that only show oak
     * while focus is spruce planks).
     */
    public static boolean roleMatchesFocus(
            IIngredientSupplier supplier, ItemStack focus, RecipeIngredientRole role, Object recipe
    ) {
        if (focus == null || focus.isEmpty() || role == null) {
            return true;
        }
        String focusName = normName(focus.getHoverName().getString());
        String focusId = itemId(focus);
        boolean nameUseful = nameUseful(focusName, focusId);
        boolean hasVariant = ItemVariantKeys.hasVariantKeys(focus);
        List<String> prefer = ItemVariantKeys.preferTokens(focus);

        boolean any = false;
        if (supplier != null) {
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
                String stackName = normName(stack.getHoverName().getString());
                // Sibling NBT variant with a different useful name → never accept as same focus.
                if (stack.is(focus.getItem())
                        && nameUseful
                        && nameUseful(stackName, focusId)
                        && !focusName.equals(stackName)
                        && !stackMentionsPrefer(stack, stackName, prefer)) {
                    continue;
                }
                // Same item type OK for R-recipes only when focus name is generic
                // (Surgery Box samples). Distinctive names / VARIANT require name,
                // schematic token, or overlapping schematic — else variants collide.
                if (role == RecipeIngredientRole.OUTPUT && stack.is(focus.getItem())) {
                    if (hasVariant) {
                        if (focusName.equals(stackName)
                                || stackMentionsPrefer(stack, stackName, prefer)
                                || schematicsOverlap(stack, focus)) {
                            return true;
                        }
                        continue;
                    }
                    if (!nameUseful || focusName.equals(stackName)) {
                        return true;
                    }
                }
                if (nameUseful && focusName.equals(stackName)) {
                    return true;
                }
                if (hasVariant && stackMentionsPrefer(stack, stackName, prefer)) {
                    return true;
                }
            }
        }

        if (role == RecipeIngredientRole.INPUT && craftingInputsAccept(recipe, focus)) {
            return true;
        }
        // No item evidence — keep when empty role / no crafting ingredients to test.
        if (!any && !hasCraftingIngredients(recipe)) {
            return true;
        }
        return false;
    }

    /**
     * Recipe layout text (ingredient / output names + schematics) mentions focus
     * variant tokens or display-name pieces. True when no variant signal on focus.
     */
    public static boolean recipeMentionsVariant(IIngredientSupplier supplier, ItemStack focus) {
        List<String> prefer = ItemVariantKeys.preferTokens(focus);
        if (prefer.isEmpty() || !ItemVariantKeys.hasVariantKeys(focus)) {
            return true;
        }
        if (supplier == null) {
            return false;
        }
        StringBuilder blob = new StringBuilder();
        for (RecipeIngredientRole role : List.of(
                RecipeIngredientRole.INPUT,
                RecipeIngredientRole.OUTPUT,
                RecipeIngredientRole.CATALYST
        )) {
            for (ITypedIngredient<?> typed : supplier.getIngredients(role)) {
                var opt = typed.getItemStack();
                if (opt.isEmpty() || opt.get().isEmpty()) {
                    continue;
                }
                ItemStack stack = opt.get();
                blob.append(normName(stack.getHoverName().getString())).append(' ');
                for (String s : ItemVariantKeys.schematics(stack)) {
                    blob.append(s).append(' ');
                }
            }
        }
        String text = blob.toString();
        String focusName = normName(focus.getHoverName().getString());
        if (!focusName.isBlank() && text.toLowerCase(Locale.ROOT).contains(focusName.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return ItemVariantKeys.mentions(text, prefer);
    }

    /** Card item names/schematics mention prefer tokens (for soft-filter after collect). */
    public static boolean cardMentionsVariant(com.skps9.packai.logic.RecipeCard card, ItemStack focus) {
        List<String> prefer = ItemVariantKeys.preferTokens(focus);
        if (prefer.isEmpty() || !ItemVariantKeys.hasVariantKeys(focus)) {
            return true;
        }
        if (card == null) {
            return false;
        }
        StringBuilder blob = new StringBuilder();
        appendStacks(blob, card.inputs());
        appendStacks(blob, card.outputs());
        appendStacks(blob, card.catalysts());
        appendStacks(blob, card.grid());
        if (card.placedInputs() != null) {
            for (var p : card.placedInputs()) {
                if (p != null && p.stack() != null && !p.stack().isEmpty()) {
                    blob.append(normName(p.stack().getHoverName().getString())).append(' ');
                    for (String s : ItemVariantKeys.schematics(p.stack())) {
                        blob.append(s).append(' ');
                    }
                }
            }
        }
        if (card.categoryTitle() != null) {
            blob.append(card.categoryTitle()).append(' ');
        }
        String text = blob.toString();
        String focusName = normName(focus.getHoverName().getString());
        if (!focusName.isBlank() && text.toLowerCase(Locale.ROOT).contains(focusName.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return ItemVariantKeys.mentions(text, prefer);
    }

    public static boolean sameRegistryId(ItemStack stack, String id) {
        if (stack == null || stack.isEmpty() || id == null || id.isBlank()) {
            return false;
        }
        return id.trim().equalsIgnoreCase(itemId(stack));
    }

    /**
     * Upgrade-style JEI recipes: focus registry id appears as both INPUT and OUTPUT
     * (same item in, same item out — often with different NBT / level).
     */
    public static boolean focusAppearsAsInputAndOutput(IIngredientSupplier supplier, ItemStack focus) {
        if (supplier == null || focus == null || focus.isEmpty()) {
            return false;
        }
        String focusId = itemId(focus);
        if (focusId.isEmpty()) {
            return false;
        }
        return roleHasRegistryId(supplier, RecipeIngredientRole.INPUT, focusId)
                && roleHasRegistryId(supplier, RecipeIngredientRole.OUTPUT, focusId);
    }

    /**
     * True when crafting ingredients accept focus via {@link Ingredient#test}
     * (JEI tag slots may only display oak while focus is spruce).
     */
    public static boolean craftingInputsAccept(Object recipe, ItemStack focus) {
        CraftingRecipe crafting = asCrafting(recipe);
        if (crafting == null || focus == null || focus.isEmpty()) {
            return false;
        }
        try {
            for (Ingredient ingredient : crafting.getIngredients()) {
                if (ingredientMentions(ingredient, focus)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    public static boolean hasCraftingIngredients(Object recipe) {
        CraftingRecipe crafting = asCrafting(recipe);
        if (crafting == null) {
            return false;
        }
        try {
            var list = crafting.getIngredients();
            if (list == null || list.isEmpty()) {
                return false;
            }
            for (Ingredient ingredient : list) {
                if (ingredient != null && !ingredient.isEmpty()) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    public static boolean ingredientMentions(Ingredient ingredient, ItemStack focus) {
        if (ingredient == null || ingredient.isEmpty() || focus == null || focus.isEmpty()) {
            return false;
        }
        try {
            return ingredient.test(focus);
        } catch (Exception e) {
            return false;
        }
    }

    private static CraftingRecipe asCrafting(Object recipe) {
        if (recipe == null) {
            return null;
        }
        Object value = recipe;
        if (recipe instanceof RecipeHolder<?> holder) {
            value = holder.value();
        }
        return value instanceof CraftingRecipe crafting ? crafting : null;
    }

    private static void appendStacks(StringBuilder blob, List<ItemStack> stacks) {
        if (stacks == null) {
            return;
        }
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            blob.append(normName(stack.getHoverName().getString())).append(' ');
            for (String s : ItemVariantKeys.schematics(stack)) {
                blob.append(s).append(' ');
            }
        }
    }

    private static boolean stackMentionsPrefer(ItemStack stack, String stackName, List<String> prefer) {
        if (prefer == null || prefer.isEmpty()) {
            return false;
        }
        StringBuilder blob = new StringBuilder(stackName == null ? "" : stackName);
        blob.append(' ');
        for (String s : ItemVariantKeys.schematics(stack)) {
            blob.append(s).append(' ');
        }
        return ItemVariantKeys.mentions(blob.toString(), prefer);
    }

    private static boolean schematicsOverlap(ItemStack a, ItemStack b) {
        List<String> as = ItemVariantKeys.schematics(a);
        List<String> bs = ItemVariantKeys.schematics(b);
        if (as.isEmpty() || bs.isEmpty()) {
            return false;
        }
        return ItemVariantKeys.mentions(String.join(" ", as), ItemVariantKeys.preferTokens(b))
                || ItemVariantKeys.mentions(String.join(" ", bs), ItemVariantKeys.preferTokens(a));
    }

    private static boolean roleHasRegistryId(
            IIngredientSupplier supplier, RecipeIngredientRole role, String focusId
    ) {
        for (ITypedIngredient<?> typed : supplier.getIngredients(role)) {
            var opt = typed.getItemStack();
            if (opt.isEmpty() || opt.get().isEmpty()) {
                continue;
            }
            if (sameRegistryId(opt.get(), focusId)) {
                return true;
            }
        }
        return false;
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
