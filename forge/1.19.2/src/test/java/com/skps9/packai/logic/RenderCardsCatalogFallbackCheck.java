package com.skps9.packai.logic;

import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Catalog fallback when JEI render scan is empty. Run with -ea. */
public final class RenderCardsCatalogFallbackCheck {
    private RenderCardsCatalogFallbackCheck() {}

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        catalogHitWhenScanEmpty();
        missWhenCatalogEmpty();
        scanNonemptySkipsCatalog();
        negativeControlNoFallback();
        System.out.println("RenderCardsCatalogFallbackCheck OK");
    }

    private static void catalogHitWhenScanEmpty() {
        String itemId = "productivebees:spawn_egg_configurable_bee";
        RecipeCard incubation = RecipeCard.flow(
                "Incubation · BaBee Incubator",
                List.of(),
                List.of(),
                List.of(new ItemStack(Items.EGG)),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ).withSourceItemId(itemId);
        AskToolEnv env = new AskToolEnv(
                ItemStack.EMPTY, null, null, List.of(), ItemRef.NONE);
        env.catalogCards = List.of(incubation);

        RenderRecipeCardsAskTool.RenderMatchResult r =
                RenderRecipeCardsAskTool.resolveScanOrCatalogFallback(
                        List.of(), itemId, "output", "", env);
        assert r.catalogFallback() : "expected catalog fallback";
        assert r.matched().size() == 1 : r.matched();
        assert "Incubation · BaBee Incubator".equals(r.matched().get(0).categoryTitle()) : r.matched();
        System.out.println("catalogHitWhenScanEmpty OK");
    }

    private static void missWhenCatalogEmpty() {
        AskToolEnv env = new AskToolEnv(
                ItemStack.EMPTY, null, null, List.of(), ItemRef.NONE);
        env.catalogCards = List.of();
        RenderRecipeCardsAskTool.RenderMatchResult r =
                RenderRecipeCardsAskTool.resolveScanOrCatalogFallback(
                        List.of(), "minecraft:stick", "output", "", env);
        assert r.matched().isEmpty() : r.matched();
        assert !r.catalogFallback() : r;
        System.out.println("missWhenCatalogEmpty OK");
    }

    private static void scanNonemptySkipsCatalog() {
        RecipeCard scanCard = RecipeCard.flow(
                "Crafting",
                List.of(),
                List.of(),
                List.of(new ItemStack(Items.STICK)),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        RecipeCard catalogOnly = RecipeCard.flow(
                "Other",
                List.of(),
                List.of(),
                List.of(new ItemStack(Items.APPLE)),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        AskToolEnv env = new AskToolEnv(
                ItemStack.EMPTY, null, null, List.of(), ItemRef.NONE);
        env.catalogCards = List.of(catalogOnly);

        RenderRecipeCardsAskTool.RenderMatchResult r =
                RenderRecipeCardsAskTool.resolveScanOrCatalogFallback(
                        List.of(scanCard), "minecraft:stick", "output", "", env);
        assert !r.catalogFallback() : r;
        assert r.matched().size() == 1 : r.matched();
        assert "Crafting".equals(r.matched().get(0).categoryTitle()) : r.matched();
        System.out.println("scanNonemptySkipsCatalog OK");
    }

    private static void negativeControlNoFallback() {
        String itemId = "minecraft:egg";
        RenderRecipeCardsAskTool.RenderMatchResult disabled =
                RenderRecipeCardsAskTool.harnessResolveWithoutCatalogFallback(
                        List.of(), itemId, "output", "");
        assert disabled.matched().isEmpty() : "negative control: without fallback scan stays empty";
        System.out.println("negativeControlNoFallback OK");
    }
}
