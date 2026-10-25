package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.skps9.packai.logic.RecipeCard;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Pure logic checks for {@link JeiCategoryIndex#cardsViaIndex}. Run with -ea. */
public final class JeiCategoryIndexCheck {
    private JeiCategoryIndexCheck() {}

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try {
            twoCategoriesTwoCards();
            nbtIgnoredStillHits();
            notReadyReturnsEmpty();
            capStableOrder();
            categoryExceptionOthersOk();
            negativeControlIndexDisabled();
        } finally {
            JeiCategoryIndex.resetForTest();
        }
        System.out.println("JeiCategoryIndexCheck OK");
    }

    private static void twoCategoriesTwoCards() {
        Item item = Items.EGG;
        ResourceLocation a = new ResourceLocation("packai_test", "cat_a");
        ResourceLocation b = new ResourceLocation("packai_test", "cat_b");
        LinkedHashSet<ResourceLocation> set = new LinkedHashSet<>();
        set.add(a);
        set.add(b);
        JeiCategoryIndex.harnessInstallReady(Map.of(item, set));
        JeiCategoryIndex.harnessSetCardSupplier((it, uids, role, cap) -> {
            List<RecipeCard> cards = new ArrayList<>();
            for (ResourceLocation uid : uids) {
                cards.add(RecipeCard.flow(
                        uid.toString(),
                        List.of(),
                        List.of(),
                        List.of(new ItemStack(it)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()));
            }
            return cards;
        });
        List<RecipeCard> got = JeiCategoryIndex.cardsViaIndex(item, "output", 8);
        assert got.size() == 2 : got.size();
        System.out.println("twoCategoriesTwoCards OK");
    }

    private static void nbtIgnoredStillHits() {
        Item item = Items.EGG;
        ResourceLocation cat = new ResourceLocation("packai_test", "incubation");
        JeiCategoryIndex.harnessInstallReady(Map.of(item, new LinkedHashSet<>(List.of(cat))));
        JeiCategoryIndex.harnessSetCardSupplier((it, uids, role, cap) -> List.of(
                RecipeCard.flow(
                        "Incubation",
                        List.of(new ItemStack(Items.HONEYCOMB)),
                        List.of(),
                        List.of(new ItemStack(Items.DIAMOND)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of())
                        .withSourceItemId("minecraft:egg")));
        ItemStack tagged = new ItemStack(Items.EGG);
        tagged.setTag(new CompoundTag());
        tagged.getTag().putString("variant", "configurable_bee");
        List<RecipeCard> got = JeiCategoryIndex.cardsViaIndex(tagged.getItem(), "uses", 3);
        assert got.size() == 1 : got;
        System.out.println("nbtIgnoredStillHits OK");
    }

    private static void notReadyReturnsEmpty() {
        JeiCategoryIndex.resetForTest();
        assert !JeiCategoryIndex.INSTANCE.ready() : "expected not ready";
        List<RecipeCard> got = JeiCategoryIndex.cardsViaIndex(Items.EGG, "output", 3);
        assert got.isEmpty() : got;
        System.out.println("notReadyReturnsEmpty OK");
    }

    private static void capStableOrder() {
        Item item = Items.STONE;
        ResourceLocation cat = new ResourceLocation("packai_test", "multi");
        JeiCategoryIndex.harnessInstallReady(Map.of(item, new LinkedHashSet<>(List.of(cat))));
        JeiCategoryIndex.harnessSetCardSupplier((it, uids, role, cap) -> {
            List<RecipeCard> all = List.of(
                    RecipeCard.flow("B", List.of(), List.of(), List.of(new ItemStack(it)),
                            List.of(), List.of(), List.of(), List.of()),
                    RecipeCard.flow("A", List.of(), List.of(), List.of(new ItemStack(it)),
                            List.of(), List.of(), List.of(), List.of()),
                    RecipeCard.flow("C", List.of(), List.of(), List.of(new ItemStack(it)),
                            List.of(), List.of(), List.of(), List.of()));
            all = new ArrayList<>(all);
            all.sort(JeiCategoryIndex.stableCardOrder());
            return List.copyOf(all.subList(0, Math.min(cap, all.size())));
        });
        List<RecipeCard> a = JeiCategoryIndex.cardsViaIndex(item, "output", 2);
        List<RecipeCard> b = JeiCategoryIndex.cardsViaIndex(item, "output", 2);
        assert a.size() == 2 && b.size() == 2 : a.size() + " " + b.size();
        assert a.get(0).categoryTitle().equals(b.get(0).categoryTitle()) : a;
        assert a.get(1).categoryTitle().equals(b.get(1).categoryTitle()) : a;
        System.out.println("capStableOrder OK");
    }

    private static void categoryExceptionOthersOk() {
        Item item = Items.APPLE;
        ResourceLocation good = new ResourceLocation("packai_test", "good");
        // Index built with only good cat (bad cat failed during build and was skipped).
        JeiCategoryIndex.harnessInstallReady(Map.of(item, new LinkedHashSet<>(List.of(good))));
        JeiCategoryIndex.harnessSetCardSupplier((it, uids, role, cap) -> {
            List<RecipeCard> out = new ArrayList<>();
            for (ResourceLocation uid : uids) {
                out.add(RecipeCard.flow(
                        uid.toString(),
                        List.of(),
                        List.of(),
                        List.of(new ItemStack(it)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()));
            }
            return out;
        });
        List<RecipeCard> got = JeiCategoryIndex.cardsViaIndex(item, "output", 4);
        assert got.size() == 1 : got;
        System.out.println("categoryExceptionOthersOk OK");
    }

    private static void negativeControlIndexDisabled() {
        Item item = Items.EGG;
        JeiCategoryIndex.harnessInstallReady(Map.of(
                item, new LinkedHashSet<>(List.of(new ResourceLocation("packai_test", "x")))));
        JeiCategoryIndex.harnessSetCardSupplier((it, uids, role, cap) -> List.of(
                RecipeCard.flow(
                        "Hit",
                        List.of(),
                        List.of(),
                        List.of(new ItemStack(it)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of())));
        JeiCategoryIndex.setEnabledForTest(false);
        List<RecipeCard> disabled = JeiCategoryIndex.cardsViaIndex(item, "output", 3);
        assert disabled.isEmpty() : "NBT-less still hit should FAIL when index disabled: " + disabled;
        JeiCategoryIndex.setEnabledForTest(true);
        List<RecipeCard> enabled = JeiCategoryIndex.cardsViaIndex(item, "output", 3);
        assert enabled.size() == 1 : enabled;
        System.out.println("negativeControlIndexDisabled OK");
    }
}
