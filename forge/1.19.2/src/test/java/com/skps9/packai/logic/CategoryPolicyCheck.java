package com.skps9.packai.logic;

import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** {@link RecipeCategoryPolicy} uid rules + mirror drop. Run with -ea. */
public final class CategoryPolicyCheck {
    private CategoryPolicyCheck() {}

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try {
            nonObtainExact();
            nonObtainMycelialPrefix();
            mirrorExactAndMinecolonies();
            dropMirroredPrefersNonMirror();
            dropMirroredKeepsMirrorOnly();
            negativeControlMirrorOnlyDropped();
            infoOnlyUidRules();
            dropInfoWhenOthersPrefersNonInfo();
            dropInfoWhenOthersKeepsInfoOnly();
            negativeControlInfoOnlyDropped();
        } finally {
            RecipeCategoryPolicy.harnessClearUids();
        }
        System.out.println("CategoryPolicyCheck OK");
    }

    private static void nonObtainExact() {
        assert RecipeCategoryPolicy.isNonObtain("minecraft:anvil");
        assert !RecipeCategoryPolicy.isNonObtain("minecraft:crafting");
        assert !RecipeCategoryPolicy.isNonObtain("jei:information");
        assert !RecipeCategoryPolicy.isNonObtain("jeresources:worldgen");
        System.out.println("nonObtainExact OK");
    }

    private static void infoOnlyUidRules() {
        assert RecipeCategoryPolicy.isInfoOnly("jei:information");
        assert !RecipeCategoryPolicy.isInfoOnly("jeresources:worldgen");
        assert !RecipeCategoryPolicy.isInfoOnly("minecraft:crafting");
        System.out.println("infoOnlyUidRules OK");
    }

    private static void nonObtainMycelialPrefix() {
        assert RecipeCategoryPolicy.isNonObtain("industrialforegoing:mycelial_furnace");
        assert !RecipeCategoryPolicy.isNonObtain("industrialforegoing:plant_gatherer");
        System.out.println("nonObtainMycelialPrefix OK");
    }

    private static void mirrorExactAndMinecolonies() {
        assert RecipeCategoryPolicy.isMirror("create:automatic_shaped");
        assert !RecipeCategoryPolicy.isMirror("create:mechanical_press");
        assert RecipeCategoryPolicy.isMirror("minecolonies:baker");
        System.out.println("mirrorExactAndMinecolonies OK");
    }

    private static void dropMirroredPrefersNonMirror() {
        RecipeCard nonMirror = bindUid(
                RecipeCard.flow("Crafting", List.of(), List.of(), List.of(new ItemStack(Items.DIRT)),
                        List.of(), List.of(), List.of(), List.of())
                        .withSourceItemId("minecraft:dirt")
                        .withFocusRole(RecipeCard.FocusRole.OUTPUT),
                "minecraft:crafting");
        RecipeCard mirror = bindUid(
                RecipeCard.flow("Auto", List.of(), List.of(), List.of(new ItemStack(Items.DIRT)),
                        List.of(), List.of(), List.of(), List.of())
                        .withSourceItemId("minecraft:dirt")
                        .withFocusRole(RecipeCard.FocusRole.OUTPUT),
                "create:automatic_shaped");
        List<RecipeCard> out = RecipeCategoryPolicy.dropMirrored(List.of(nonMirror, mirror));
        assert out.size() == 1 : out;
        assert out.get(0) == nonMirror : out;
        System.out.println("dropMirroredPrefersNonMirror OK");
    }

    private static void dropMirroredKeepsMirrorOnly() {
        RecipeCard mirror = bindUid(
                RecipeCard.flow("Auto", List.of(), List.of(), List.of(new ItemStack(Items.STONE)),
                        List.of(), List.of(), List.of(), List.of())
                        .withSourceItemId("minecraft:stone")
                        .withFocusRole(RecipeCard.FocusRole.OUTPUT),
                "create:automatic_shaped");
        List<RecipeCard> out = RecipeCategoryPolicy.dropMirrored(List.of(mirror));
        assert out.size() == 1 : out;
        System.out.println("dropMirroredKeepsMirrorOnly OK");
    }

    private static void negativeControlMirrorOnlyDropped() {
        boolean prev = RecipeCategoryPolicy.keepMirrorWhenOnlySource;
        RecipeCategoryPolicy.keepMirrorWhenOnlySource = false;
        try {
            RecipeCard mirror = bindUid(
                    RecipeCard.flow("Auto", List.of(), List.of(), List.of(new ItemStack(Items.COBBLESTONE)),
                            List.of(), List.of(), List.of(), List.of())
                            .withSourceItemId("minecraft:cobblestone")
                            .withFocusRole(RecipeCard.FocusRole.OUTPUT),
                    "create:automatic_shaped");
            List<RecipeCard> out = RecipeCategoryPolicy.dropMirrored(List.of(mirror));
            assert out.isEmpty() : "negative control: mirror-only must clear when flag off";
        } finally {
            RecipeCategoryPolicy.keepMirrorWhenOnlySource = prev;
        }
        RecipeCategoryPolicy.keepMirrorWhenOnlySource = false;
        try {
            RecipeCard mirror = bindUid(
                    RecipeCard.flow("Auto", List.of(), List.of(), List.of(new ItemStack(Items.GRAVEL)),
                            List.of(), List.of(), List.of(), List.of())
                            .withSourceItemId("minecraft:gravel")
                            .withFocusRole(RecipeCard.FocusRole.OUTPUT),
                    "create:automatic_shaped");
            List<RecipeCard> out = RecipeCategoryPolicy.dropMirrored(List.of(mirror));
            assert out.size() == 1 : "negative control wiring must fail when flag off";
            throw new AssertionError("negative control did not fail");
        } catch (AssertionError expected) {
            // expected
        } finally {
            RecipeCategoryPolicy.keepMirrorWhenOnlySource = prev;
        }
        System.out.println("negativeControlMirrorOnlyDropped OK");
    }

    private static void dropInfoWhenOthersPrefersNonInfo() {
        RecipeCard crafting = bindUid(
                RecipeCard.flow("Crafting", List.of(), List.of(), List.of(new ItemStack(Items.DIRT)),
                        List.of(), List.of(), List.of(), List.of())
                        .withSourceItemId("minecraft:dirt")
                        .withFocusRole(RecipeCard.FocusRole.OUTPUT),
                "minecraft:crafting");
        RecipeCard info = bindUid(
                RecipeCard.flow("Info", List.of(), List.of(), List.of(new ItemStack(Items.DIRT)),
                        List.of(), List.of(), List.of(), List.of())
                        .withSourceItemId("minecraft:dirt")
                        .withFocusRole(RecipeCard.FocusRole.OUTPUT),
                "jei:information");
        List<RecipeCard> out = RecipeCategoryPolicy.dropInfoWhenOthers(List.of(crafting, info));
        assert out.size() == 1 : out;
        assert out.get(0) == crafting : out;
        System.out.println("dropInfoWhenOthersPrefersNonInfo OK");
    }

    private static void dropInfoWhenOthersKeepsInfoOnly() {
        RecipeCard info = bindUid(
                RecipeCard.flow("Info", List.of(), List.of(), List.of(new ItemStack(Items.STONE)),
                        List.of(), List.of(), List.of(), List.of())
                        .withSourceItemId("minecraft:stone")
                        .withFocusRole(RecipeCard.FocusRole.OUTPUT),
                "jei:information");
        List<RecipeCard> out = RecipeCategoryPolicy.dropInfoWhenOthers(List.of(info));
        assert out.size() == 1 : out;
        System.out.println("dropInfoWhenOthersKeepsInfoOnly OK");
    }

    private static void negativeControlInfoOnlyDropped() {
        boolean prev = RecipeCategoryPolicy.keepInfoWhenOnlySource;
        RecipeCategoryPolicy.keepInfoWhenOnlySource = false;
        try {
            RecipeCard info = bindUid(
                    RecipeCard.flow("Info", List.of(), List.of(), List.of(new ItemStack(Items.COBBLESTONE)),
                            List.of(), List.of(), List.of(), List.of())
                            .withSourceItemId("minecraft:cobblestone")
                            .withFocusRole(RecipeCard.FocusRole.OUTPUT),
                    "jei:information");
            List<RecipeCard> out = RecipeCategoryPolicy.dropInfoWhenOthers(List.of(info));
            assert out.isEmpty() : "negative control: info-only must clear when flag off";
        } finally {
            RecipeCategoryPolicy.keepInfoWhenOnlySource = prev;
        }
        RecipeCategoryPolicy.keepInfoWhenOnlySource = false;
        try {
            RecipeCard info = bindUid(
                    RecipeCard.flow("Info", List.of(), List.of(), List.of(new ItemStack(Items.GRAVEL)),
                            List.of(), List.of(), List.of(), List.of())
                            .withSourceItemId("minecraft:gravel")
                            .withFocusRole(RecipeCard.FocusRole.OUTPUT),
                    "jei:information");
            List<RecipeCard> out = RecipeCategoryPolicy.dropInfoWhenOthers(List.of(info));
            assert out.size() == 1 : "negative control wiring must fail when flag off";
            throw new AssertionError("negative control did not fail");
        } catch (AssertionError expected) {
            // expected
        } finally {
            RecipeCategoryPolicy.keepInfoWhenOnlySource = prev;
        }
        System.out.println("negativeControlInfoOnlyDropped OK");
    }

    private static RecipeCard bindUid(RecipeCard card, String uid) {
        RecipeCategoryPolicy.harnessBindUid(card, uid);
        return card;
    }
}
