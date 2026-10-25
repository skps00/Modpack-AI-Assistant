package com.skps9.packai.client.jei;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
            tickBudgetSixMsPerTick();
            forceBuildCompletesSynthetic();
            negativeControlForceBuildDisabled();
            refocusTaggedSampleHits();
            refocusSixCategoryCap();
            refocusSkipsEmptySample();
            negativeControlRefocusDisabled();
            refocusNamespaceOrderPrefersSameMod();
            refocusNamespaceOrderMultipleSameModByRecipeCount();
            refocusNamespaceOrderNoMatchFallsBackToRecipeCount();
            negativeControlRefocusNamespaceDisabled();
            refocusStopsAfterFirstHit();
        } finally {
            JeiCategoryIndex.resetForTest();
        }
        System.out.println("JeiCategoryIndexCheck OK");
    }

    private static void twoCategoriesTwoCards() {
        JeiCategoryIndex.harnessSetRefocusEnabled(false);
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
        JeiCategoryIndex.harnessSetRefocusEnabled(false);
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
        JeiCategoryIndex.harnessSetRefocusEnabled(false);
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
        JeiCategoryIndex.harnessSetRefocusEnabled(false);
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
        JeiCategoryIndex.harnessSetRefocusEnabled(false);
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
        JeiCategoryIndex.harnessSetRefocusEnabled(true);
        System.out.println("negativeControlIndexDisabled OK");
    }

    private static void tickBudgetSixMsPerTick() {
        assert JeiCategoryIndex.tickBudgetNanos() == 6_000_000L
                : "TICK_BUDGET_NS=" + JeiCategoryIndex.tickBudgetNanos();
        JeiCategoryIndex.harnessInstallSyntheticBuild(10);
        JeiCategoryIndex.harnessUseAdvancingNanoClock(2_000_000L);
        JeiCategoryIndex.INSTANCE.tick();
        assert JeiCategoryIndex.harnessRecipesIndexed() == 3
                : "expected 3 recipes in 6ms budget, got " + JeiCategoryIndex.harnessRecipesIndexed();
        assert JeiCategoryIndex.harnessBuilding() : "expected still building";
        JeiCategoryIndex.INSTANCE.tick();
        assert JeiCategoryIndex.harnessRecipesIndexed() == 6 : JeiCategoryIndex.harnessRecipesIndexed();
        System.out.println("tickBudgetSixMsPerTick OK");
    }

    private static void forceBuildCompletesSynthetic() {
        JeiCategoryIndex.harnessInstallSyntheticBuild(12);
        JeiCategoryIndex.harnessRunForceBuildDrainForTest();
        assert JeiCategoryIndex.INSTANCE.ready() : "expected ready after force drain";
        assert JeiCategoryIndex.harnessRecipesIndexed() == 12 : JeiCategoryIndex.harnessRecipesIndexed();
        System.out.println("forceBuildCompletesSynthetic OK");
    }

    private static void negativeControlForceBuildDisabled() {
        JeiCategoryIndex.harnessInstallSyntheticBuild(8);
        JeiCategoryIndex.harnessSetForceBuildUnlimited(false);
        JeiCategoryIndex.harnessRunForceBuildDrainForTest();
        assert !JeiCategoryIndex.INSTANCE.ready() : "one-shot complete should FAIL when force drain disabled";
        assert JeiCategoryIndex.harnessBuilding() : "expected still building";
        assert JeiCategoryIndex.harnessRecipesIndexed() == 0 : JeiCategoryIndex.harnessRecipesIndexed();
        System.out.println("negativeControlForceBuildDisabled OK");
    }

    private static void refocusTaggedSampleHits() {
        Item item = Items.EGG;
        ResourceLocation craft = new ResourceLocation("packai_test", "craft_swap");
        ResourceLocation incubation = new ResourceLocation("packai_test", "incubation");
        LinkedHashSet<ResourceLocation> cats = new LinkedHashSet<>();
        cats.add(craft);
        cats.add(incubation);
        ItemStack tagged = new ItemStack(Items.EGG);
        tagged.getOrCreateTag().putString("variant", "configurable_bee");
        ItemStack plain = new ItemStack(Items.EGG);
        Map<ResourceLocation, ItemStack> samples = new HashMap<>();
        samples.put(craft, plain);
        samples.put(incubation, tagged);
        Map<ResourceLocation, Integer> counts = Map.of(craft, 500, incubation, 3);
        JeiCategoryIndex.harnessInstallReady(Map.of(item, cats), Map.of(item, samples), counts);
        JeiCategoryIndex.harnessSetRefocusScanner((sample, uid, role, cap, deadline) -> {
            if (uid.equals(incubation) && sample.hasTag()) {
                return List.of(RecipeCard.flow(
                        "Incubation",
                        List.of(new ItemStack(Items.HONEYCOMB)),
                        List.of(),
                        List.of(sample.copy()),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()));
            }
            return List.of();
        });
        List<RecipeCard> got = JeiCategoryIndex.cardsViaIndex(item, "output", 8);
        assert got.size() == 1 : got;
        assert "Incubation".equals(got.get(0).categoryTitle()) : got.get(0).categoryTitle();
        System.out.println("refocusTaggedSampleHits OK");
    }

    private static void refocusSixCategoryCap() {
        Item item = Items.DIAMOND;
        LinkedHashSet<ResourceLocation> cats = new LinkedHashSet<>();
        Map<ResourceLocation, ItemStack> samples = new HashMap<>();
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        for (int i = 0; i < 7; i++) {
            ResourceLocation uid = new ResourceLocation("packai_test", "cat_" + i);
            cats.add(uid);
            samples.put(uid, new ItemStack(item));
            counts.put(uid, i);
        }
        JeiCategoryIndex.harnessInstallReady(Map.of(item, cats), Map.of(item, samples), counts);
        AtomicInteger scans = new AtomicInteger();
        JeiCategoryIndex.harnessSetRefocusScanner((sample, uid, role, cap, deadline) -> {
            scans.incrementAndGet();
            return List.of();
        });
        JeiCategoryIndex.cardsViaIndex(item, "output", 4);
        assert scans.get() == 6 : "expected 6 refocus scans, got " + scans.get();
        System.out.println("refocusSixCategoryCap OK");
    }

    private static void refocusSkipsEmptySample() {
        Item item = Items.GOLD_INGOT;
        ResourceLocation withSample = new ResourceLocation("packai_test", "has_sample");
        ResourceLocation emptySample = new ResourceLocation("packai_test", "no_sample");
        LinkedHashSet<ResourceLocation> cats = new LinkedHashSet<>();
        cats.add(withSample);
        cats.add(emptySample);
        Map<ResourceLocation, ItemStack> samples = new HashMap<>();
        samples.put(withSample, new ItemStack(item));
        samples.put(emptySample, ItemStack.EMPTY);
        JeiCategoryIndex.harnessInstallReady(
                Map.of(item, cats), Map.of(item, samples), Map.of(withSample, 1, emptySample, 1));
        JeiCategoryIndex.harnessSetRefocusScanner((sample, uid, role, cap, deadline) -> {
            if (uid.equals(withSample)) {
                return List.of(RecipeCard.flow(
                        "Has",
                        List.of(),
                        List.of(),
                        List.of(new ItemStack(item)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()));
            }
            throw new RuntimeException("should not scan empty-sample category");
        });
        List<RecipeCard> got = JeiCategoryIndex.cardsViaIndex(item, "output", 3);
        assert got.size() == 1 : got;
        System.out.println("refocusSkipsEmptySample OK");
    }

    private static void negativeControlRefocusDisabled() {
        Item item = Items.EGG;
        ResourceLocation incubation = new ResourceLocation("packai_test", "incubation");
        ItemStack tagged = new ItemStack(Items.EGG);
        tagged.getOrCreateTag().putString("variant", "configurable_bee");
        JeiCategoryIndex.harnessInstallReady(
                Map.of(item, new LinkedHashSet<>(List.of(incubation))),
                Map.of(item, Map.of(incubation, tagged)),
                Map.of(incubation, 1));
        JeiCategoryIndex.harnessSetRefocusScanner((sample, uid, role, cap, deadline) -> {
            if (sample.hasTag()) {
                return List.of(RecipeCard.flow(
                        "Incubation",
                        List.of(),
                        List.of(),
                        List.of(sample.copy()),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()));
            }
            return List.of();
        });
        JeiCategoryIndex.harnessSetRefocusEnabled(false);
        JeiCategoryIndex.harnessSetCardSupplier((it, uids, role, cap) -> List.of());
        List<RecipeCard> disabled = JeiCategoryIndex.cardsViaIndex(item, "output", 3);
        assert disabled.isEmpty() : "tagged refocus should FAIL when refocus disabled: " + disabled;
        JeiCategoryIndex.harnessSetRefocusEnabled(true);
        List<RecipeCard> enabled = JeiCategoryIndex.cardsViaIndex(item, "output", 3);
        assert enabled.size() == 1 : enabled;
        System.out.println("negativeControlRefocusDisabled OK");
    }

    private static void refocusNamespaceOrderPrefersSameMod() {
        Item item = Items.DIAMOND;
        ResourceLocation barX = new ResourceLocation("bar", "x");
        ResourceLocation fooY = new ResourceLocation("foo", "y");
        LinkedHashSet<ResourceLocation> cats = new LinkedHashSet<>();
        cats.add(barX);
        cats.add(fooY);
        JeiCategoryIndex.harnessInstallReady(
                Map.of(item, cats),
                Map.of(item, Map.of(barX, new ItemStack(item), fooY, new ItemStack(item))),
                Map.of(barX, 1, fooY, 500));
        JeiCategoryIndex.harnessSetRefocusNamespaceFirst(true);
        List<ResourceLocation> order = JeiCategoryIndex.harnessRefocusCategoryOrder("foo:gem", cats);
        assert order.get(0).equals(fooY) : "expected foo:y first, got " + order;
        List<ResourceLocation> scanOrder = new ArrayList<>();
        JeiCategoryIndex.harnessSetRefocusScanner((sample, uid, role, cap, deadline) -> {
            scanOrder.add(uid);
            if (uid.equals(fooY)) {
                return List.of(RecipeCard.flow(
                        "FooY",
                        List.of(),
                        List.of(),
                        List.of(new ItemStack(item)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()));
            }
            return List.of();
        });
        JeiCategoryIndex.harnessSetRefocusItemId(item, "foo:gem");
        JeiCategoryIndex.cardsViaIndex(item, "output", 4);
        assert scanOrder.size() == 1 && scanOrder.get(0).equals(fooY) : scanOrder;
        JeiCategoryIndex.harnessSetRefocusItemId(null, null);
        System.out.println("refocusNamespaceOrderPrefersSameMod OK");
    }

    private static void refocusNamespaceOrderMultipleSameModByRecipeCount() {
        ResourceLocation fooA = new ResourceLocation("foo", "a");
        ResourceLocation fooB = new ResourceLocation("foo", "b");
        LinkedHashSet<ResourceLocation> cats = new LinkedHashSet<>();
        cats.add(fooA);
        cats.add(fooB);
        JeiCategoryIndex.harnessInstallReady(
                Map.of(),
                Map.of(),
                Map.of(fooA, 10, fooB, 5));
        List<ResourceLocation> order = JeiCategoryIndex.harnessRefocusCategoryOrder("foo:item", cats);
        assert order.get(0).equals(fooB) && order.get(1).equals(fooA) : order;
        System.out.println("refocusNamespaceOrderMultipleSameModByRecipeCount OK");
    }

    private static void refocusNamespaceOrderNoMatchFallsBackToRecipeCount() {
        ResourceLocation barA = new ResourceLocation("bar", "a");
        ResourceLocation barB = new ResourceLocation("bar", "b");
        LinkedHashSet<ResourceLocation> cats = new LinkedHashSet<>();
        cats.add(barA);
        cats.add(barB);
        JeiCategoryIndex.harnessInstallReady(
                Map.of(),
                Map.of(),
                Map.of(barA, 100, barB, 2));
        List<ResourceLocation> order = JeiCategoryIndex.harnessRefocusCategoryOrder("minecraft:egg", cats);
        assert order.get(0).equals(barB) && order.get(1).equals(barA) : order;
        System.out.println("refocusNamespaceOrderNoMatchFallsBackToRecipeCount OK");
    }

    private static void negativeControlRefocusNamespaceDisabled() {
        Item item = Items.EMERALD;
        ResourceLocation barX = new ResourceLocation("bar", "x");
        ResourceLocation fooY = new ResourceLocation("foo", "y");
        LinkedHashSet<ResourceLocation> cats = new LinkedHashSet<>();
        cats.add(barX);
        cats.add(fooY);
        JeiCategoryIndex.harnessInstallReady(
                Map.of(item, cats),
                Map.of(item, Map.of(barX, new ItemStack(item), fooY, new ItemStack(item))),
                Map.of(barX, 1, fooY, 500));
        JeiCategoryIndex.harnessSetRefocusNamespaceFirst(false);
        List<ResourceLocation> order = JeiCategoryIndex.harnessRefocusCategoryOrder("foo:gem", cats);
        assert order.get(0).equals(barX) : "namespace-off should pick smallest recipe count first: " + order;
        JeiCategoryIndex.harnessSetRefocusNamespaceFirst(true);
        System.out.println("negativeControlRefocusNamespaceDisabled OK");
    }

    private static void refocusStopsAfterFirstHit() {
        Item item = Items.IRON_INGOT;
        LinkedHashSet<ResourceLocation> cats = new LinkedHashSet<>();
        Map<ResourceLocation, ItemStack> samples = new HashMap<>();
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        for (int i = 0; i < 4; i++) {
            ResourceLocation uid = new ResourceLocation("packai_test", "hit_" + i);
            cats.add(uid);
            samples.put(uid, new ItemStack(item));
            counts.put(uid, i);
        }
        JeiCategoryIndex.harnessInstallReady(Map.of(item, cats), Map.of(item, samples), counts);
        AtomicInteger scans = new AtomicInteger();
        JeiCategoryIndex.harnessSetRefocusScanner((sample, uid, role, cap, deadline) -> {
            scans.incrementAndGet();
            return List.of(RecipeCard.flow(
                    uid.toString(),
                    List.of(),
                    List.of(),
                    List.of(new ItemStack(item)),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()));
        });
        JeiCategoryIndex.cardsViaIndex(item, "output", 8);
        assert scans.get() == 1 : "expected stop after first refocus hit, got scans=" + scans.get();
        System.out.println("refocusStopsAfterFirstHit OK");
    }
}
