package com.skps9.packai.logic;

import java.util.List;

import com.skps9.packai.client.routes.RouteStoreCodec;

import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/**
 * Harness for {@link RecipeIo}: output chain, fluid I/O, Mek-style inputs, fail-soft.
 */
public final class ObtainFluidChainCheck {
    private ObtainFluidChainCheck() {}

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        caseOutputItemsWhenResultEmpty();
        caseFluidResultsOutput();
        caseInputRepresentations();
        caseFluidIngredientsMatchingFluids();
        caseUnrepresentableChemical();
        caseFailSoftAllThrow();

        caseThermalEdgeWithFluidRequirement();
        casePureFluidResultProducesFluidEdge();
        caseChocolateCyclePicksNonMeltingProducer();
        caseTeaMultiFluidListed();
        caseBadRecipeDoesNotBreakGraph();
        caseStepFluidSourcesWiring();
        caseStepDtoFluidSourcesRoundTrip();

        System.out.println("ObtainFluidChainCheck OK");
    }

    private static void caseOutputItemsWhenResultEmpty() {
        ThermalStyleOut recipe = new ThermalStyleOut();
        List<RecipeIo.ItemOut> outs = RecipeIo.outputs(recipe);
        if (outs.size() != 1) {
            fail("outputItems size=" + outs.size());
        }
        if (!Items.IRON_INGOT.equals(outs.get(0).stack().getItem())) {
            fail("output item=" + outs.get(0).stack().getItem());
        }
        if (!"getOutputItems".equals(outs.get(0).accessor())) {
            fail("accessor=" + outs.get(0).accessor());
        }
    }

    private static void caseFluidResultsOutput() {
        FluidOutRecipe recipe = new FluidOutRecipe();
        List<RecipeIo.FluidOut> fluids = RecipeIo.fluidOutputs(recipe);
        if (fluids.size() != 1) {
            fail("fluidOutputs size=" + fluids.size());
        }
        RecipeIo.FluidOut f = fluids.get(0);
        if (!"minecraft:water".equals(f.fluidId())) {
            fail("fluidId=" + f.fluidId());
        }
        if (f.amountMb() != 250) {
            fail("amount=" + f.amountMb());
        }
        if (!"getFluidResults".equals(f.accessor())) {
            fail("fluid accessor=" + f.accessor());
        }
    }

    private static void caseInputRepresentations() {
        MekStyleIn recipe = new MekStyleIn();
        List<List<net.minecraft.world.item.Item>> groups = RecipeIo.inputGroups(recipe);
        if (groups.size() != 1) {
            fail("input groups=" + groups.size());
        }
        if (!groups.get(0).contains(Items.GOLD_INGOT)) {
            fail("group=" + groups.get(0));
        }
    }

    private static void caseFluidIngredientsMatchingFluids() {
        ElectroStyleFluid recipe = new ElectroStyleFluid();
        List<RecipeIo.FluidOut> fluids = RecipeIo.fluidInputs(recipe);
        if (fluids.size() != 1) {
            fail("fluidInputs size=" + fluids.size());
        }
        if (!"minecraft:lava".equals(fluids.get(0).fluidId())) {
            fail("fluidId=" + fluids.get(0).fluidId());
        }
        if (!"getFluidIngredients".equals(RecipeIo.lastFluidAccessor())) {
            fail("lastFluidAccessor=" + RecipeIo.lastFluidAccessor());
        }
    }

    private static void caseUnrepresentableChemical() {
        if (RecipeIo.hasUnrepresentableInputs(new PlainRecipe())) {
            fail("plain should not be unrepresentable");
        }
        if (!RecipeIo.hasUnrepresentableInputs(new ChemicalRecipe())) {
            fail("chemical recipe should be unrepresentable");
        }
    }

    private static void caseThermalEdgeWithFluidRequirement() {
        ObtainGraph graph = ObtainGraph.build(List.of(new CrystallizerApatiteRecipe()));
        List<ObtainGraph.Edge> edges = graph.edgesFor(Items.EMERALD);
        if (edges.isEmpty()) {
            fail("crystallizer should produce item edge");
        }
        ObtainGraph.Edge edge = edges.get(0);
        if (edge.fluids().isEmpty()) {
            fail("edge.fluids empty");
        }
        boolean water500 = false;
        for (ObtainGraph.FluidReq fr : edge.fluids()) {
            if ("minecraft:water".equals(fr.fluidId()) && fr.amountMb() == 500) {
                water500 = true;
            }
        }
        if (!water500) {
            fail("expected water 500 mB on edge, got " + edge.fluids());
        }
    }

    private static void casePureFluidResultProducesFluidEdge() {
        ObtainGraph graph = ObtainGraph.build(List.of(new FluidOutRecipe()));
        List<ObtainGraph.FluidEdge> producers = graph.fluidProducers().get("minecraft:water");
        if (producers == null || producers.isEmpty()) {
            fail("expected FluidEdge for water");
        }
        if (graph.edgesFor(Items.IRON_INGOT).size() > 0) {
            // FluidOutRecipe has no item output
        }
    }

    private static void caseChocolateCyclePicksNonMeltingProducer() {
        Item bar = Items.COOKIE;
        ObtainGraph graph = ObtainGraph.build(List.of(
                new EarlyMeltRecipe(),
                new ChocolateMixingRecipe(),
                new ChocolateMeltingRecipe()));
        String fluidId = "minecraft:water";
        ResourceLocation chocolate = new ResourceLocation("create", "mixing/chocolate");
        for (int i = 0; i < 20; i++) {
            ObtainGraph.FluidEdge picked = graph.pickFluidProducer(fluidId, bar);
            if (picked == null) {
                fail("no producer picked");
            }
            if (!chocolate.equals(picked.recipeId())) {
                fail("wrong producer at i=" + i + " id=" + picked.recipeId());
            }
        }

        ObtainGraph.FluidEdge orderFirst = graph.pickFluidProducer(fluidId, Items.STICK);
        ResourceLocation earlyMelt = new ResourceLocation("create", "mixing/aaaa_melt");
        if (orderFirst == null || !earlyMelt.equals(orderFirst.recipeId())) {
            fail("control: expected order-first producer " + earlyMelt + ", got "
                    + (orderFirst == null ? "null" : orderFirst.recipeId()));
        }

        if (graph.pickFluidProducer("forge:nomilk", Items.COOKIE) != null) {
            fail("expected null for fluid with no producers");
        }
    }

    private static void caseTeaMultiFluidListed() {
        ObtainGraph graph = ObtainGraph.build(List.of(
                new TeaWaterProducer(),
                new TeaLavaProducer(),
                new TeaConsumerRecipe()));
        List<ObtainGraph.Edge> edges = graph.edgesFor(Items.HONEY_BOTTLE);
        if (edges.isEmpty()) {
            fail("tea consumer edge missing");
        }
        ObtainGraph.Edge edge = edges.get(0);
        if (edge.fluids().size() != 2) {
            fail("expected 2 fluid reqs, got " + edge.fluids().size());
        }
        boolean water = false;
        boolean second = false;
        for (ObtainGraph.FluidReq fr : edge.fluids()) {
            if ("minecraft:water".equals(fr.fluidId())) {
                water = true;
            }
            if ("minecraft:lava".equals(fr.fluidId())) {
                second = true;
            }
        }
        if (!water || !second) {
            fail("fluids=" + edge.fluids());
        }
        List<ObtainGraph.FluidEdge> waterProd = graph.fluidProducers().get("minecraft:water");
        List<ObtainGraph.FluidEdge> lavaProd = graph.fluidProducers().get("minecraft:lava");
        if (waterProd == null || waterProd.isEmpty() || waterProd.get(0).amountMb() != 500) {
            fail("water producer amount");
        }
        if (lavaProd == null || lavaProd.isEmpty() || lavaProd.get(0).amountMb() != 500) {
            fail("lava producer amount");
        }
    }

    private static void caseBadRecipeDoesNotBreakGraph() {
        ObtainGraph graph = null;
        try {
            graph = ObtainGraph.build(List.of(new ThrowAllRecipe(), new ThermalStyleOut()));
        } catch (Throwable t) {
            fail("graph build threw " + t);
        }
        if (graph == null) {
            fail("graph is null");
        }
        if (graph.edgeCount() < 1) {
            fail("expected at least one edge from good recipe");
        }
    }

    private static void caseStepFluidSourcesWiring() {
        ObtainGraph withProducer = ObtainGraph.build(List.of(new FluidOutRecipe(), new FluidConsumerRecipe()));
        List<ObtainPaths.Path> paths = ObtainPaths.find(withProducer, Items.GLASS_BOTTLE);
        if (paths.isEmpty()) {
            fail("expected path with producer");
        }
        ObtainPaths.Step step = paths.get(0).steps().get(0);
        if (step.fluidSources() == null) {
            fail("fluidSources must not be null");
        }
        if (step.fluidSources().isEmpty()) {
            fail("expected non-empty fluidSources when producer exists");
        }

        ObtainGraph noProducer = ObtainGraph.build(List.of(new FluidConsumerRecipe()));
        List<ObtainPaths.Path> paths2 = ObtainPaths.find(noProducer, Items.GLASS_BOTTLE);
        if (paths2.isEmpty()) {
            fail("expected path without fluid producer");
        }
        if (!paths2.get(0).steps().get(0).fluidSources().isEmpty()) {
            fail("expected empty fluidSources when no producer");
        }
    }

    private static void caseStepDtoFluidSourcesRoundTrip() {
        RouteStoreCodec.FluidSourceDto src =
                new RouteStoreCodec.FluidSourceDto(
                        "minecraft:water",
                        "create:mixing/tea",
                        "create:mixing",
                        "Mixer",
                        500,
                        List.of("minecraft:sugar"));
        RouteStoreCodec.StepDto step = new RouteStoreCodec.StepDto(
                "create:mixing/tea",
                "create:mixing",
                "minecraft:honey_bottle",
                "Mixer",
                List.of("minecraft:sugar"),
                List.of(new RouteStoreCodec.FluidDto("minecraft:water", 500)),
                List.of(src));
        RouteStoreCodec.SavedRoute route = new RouteStoreCodec.SavedRoute(
                "minecraft:honey_bottle", "2026-01-01T00:00:00Z", true, "test", List.of(step));
        String json = RouteStoreCodec.encode(List.of(route));
        List<RouteStoreCodec.SavedRoute> decoded = RouteStoreCodec.decode(json);
        if (decoded.size() != 1 || decoded.get(0).steps().size() != 1) {
            fail("round-trip route size");
        }
        RouteStoreCodec.StepDto back = decoded.get(0).steps().get(0);
        if (back.fluidSources() == null || back.fluidSources().size() != 1) {
            fail("fluidSources round-trip");
        }
        RouteStoreCodec.FluidSourceDto backSrc = back.fluidSources().get(0);
        if (!"minecraft:water".equals(backSrc.fluidId()) || backSrc.amountMb() != 500) {
            fail("fluidSource fields");
        }

        String legacy =
                "[{\"itemId\":\"minecraft:stick\",\"createdAt\":\"x\",\"complete\":true,\"modsStamp\":\"t\","
                        + "\"steps\":[{\"recipeId\":\"a\",\"typeId\":\"b\",\"output\":\"minecraft:stick\","
                        + "\"actionLabel\":null,\"inputs\":[],\"fluids\":[]}]}]";
        List<RouteStoreCodec.SavedRoute> legacyRoutes = RouteStoreCodec.decode(legacy);
        if (legacyRoutes.isEmpty()) {
            fail("legacy decode");
        }
        if (!legacyRoutes.get(0).steps().get(0).fluidSources().isEmpty()) {
            fail("legacy fluidSources should be empty");
        }
    }

    private static void caseFailSoftAllThrow() {
        ThrowAllRecipe recipe = new ThrowAllRecipe();
        try {
            if (!RecipeIo.outputs(recipe).isEmpty()) {
                fail("outputs should be empty");
            }
            if (!RecipeIo.inputGroups(recipe).isEmpty()) {
                fail("inputGroups should be empty");
            }
            if (!RecipeIo.fluidInputs(recipe).isEmpty()) {
                fail("fluidInputs should be empty");
            }
            if (RecipeIo.hasUnrepresentableInputs(recipe)) {
                fail("hasUnrepresentableInputs should be false");
            }
        } catch (Throwable t) {
            fail("threw " + t);
        }
    }

    private static class StubRecipe implements Recipe<Container> {
        private final ResourceLocation id = new ResourceLocation("packai", "stub");

        @Override
        public boolean matches(Container container, Level level) {
            return false;
        }

        @Override
        public ItemStack assemble(Container container) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean canCraftInDimensions(int w, int h) {
            return false;
        }

        @Override
        public ItemStack getResultItem() {
            return ItemStack.EMPTY;
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return null;
        }

        @Override
        public RecipeType<?> getType() {
            return RecipeType.CRAFTING;
        }

        @Override
        public NonNullList<Ingredient> getIngredients() {
            return NonNullList.create();
        }
    }

    private static final class ThermalStyleOut extends StubRecipe {
        @Override
        public ItemStack getResultItem() {
            return ItemStack.EMPTY;
        }

        @Override
        public NonNullList<Ingredient> getIngredients() {
            return NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.RAW_IRON));
        }

        public List<ItemStack> getOutputItems() {
            return List.of(new ItemStack(Items.IRON_INGOT));
        }
    }

    private static final class FluidOutRecipe extends StubRecipe {
        public List<FluidStack> getFluidResults() {
            return List.of(new FluidStack(Fluids.WATER, 250));
        }
    }

    private static final class RepIngredient {
        List<ItemStack> getRepresentations() {
            return List.of(new ItemStack(Items.GOLD_INGOT));
        }
    }

    private static final class MekStyleIn extends StubRecipe {
        public RepIngredient getInput() {
            return new RepIngredient();
        }
    }

    private static final class ElectroStyleFluid extends StubRecipe {
        public List<MatchingFluidsIngredient> getFluidIngredients() {
            return List.of(new MatchingFluidsIngredient(Fluids.LAVA, 1000));
        }
    }

    private static final class PlainRecipe extends StubRecipe {}

    private static final class ChemicalRecipe extends StubRecipe {
        public Object getChemicalInput() {
            return new Object();
        }
    }

    private static final class CrystallizerApatiteRecipe extends StubRecipe {
        @Override
        public ResourceLocation getId() {
            return new ResourceLocation("thermal", "crystallizer_apatite");
        }

        @Override
        public ItemStack getResultItem() {
            return ItemStack.EMPTY;
        }

        public List<ItemStack> getOutputItems() {
            return List.of(new ItemStack(Items.EMERALD));
        }

        public List<MatchingFluidsIngredient> getFluidIngredients() {
            return List.of(new MatchingFluidsIngredient(Fluids.WATER, 500));
        }
    }

    private static final class MatchingFluidsIngredient {
        private final net.minecraft.world.level.material.Fluid fluid;
        private final int amount;

        MatchingFluidsIngredient(net.minecraft.world.level.material.Fluid fluid, int amount) {
            this.fluid = fluid;
            this.amount = amount;
        }

        FluidStack[] getMatchingFluids() {
            return new FluidStack[] {new FluidStack(fluid, amount)};
        }
    }


    private static final class EarlyMeltRecipe extends StubRecipe {
        @Override
        public ResourceLocation getId() {
            return new ResourceLocation("create", "mixing/aaaa_melt");
        }

        @Override
        public NonNullList<Ingredient> getIngredients() {
            return NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.COOKIE));
        }

        public List<FluidStack> getFluidResults() {
            return List.of(new FluidStack(Fluids.WATER, 250));
        }
    }

    private static final class ChocolateMixingRecipe extends StubRecipe {
        @Override
        public ResourceLocation getId() {
            return new ResourceLocation("create", "mixing/chocolate");
        }

        @Override
        public NonNullList<Ingredient> getIngredients() {
            return NonNullList.of(
                    Ingredient.EMPTY,
                    Ingredient.of(Items.SUGAR),
                    Ingredient.of(Items.COCOA_BEANS),
                    Ingredient.of(Items.MILK_BUCKET));
        }

        public List<FluidStack> getFluidResults() {
            return List.of(new FluidStack(Fluids.WATER, 250));
        }
    }

    private static final class ChocolateMeltingRecipe extends StubRecipe {
        @Override
        public ResourceLocation getId() {
            return new ResourceLocation("create", "mixing/chocolate_melting");
        }

        @Override
        public NonNullList<Ingredient> getIngredients() {
            return NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.COCOA_BEANS));
        }

        public List<FluidStack> getFluidResults() {
            return List.of(new FluidStack(Fluids.WATER, 250));
        }
    }

    private static final class TeaWaterProducer extends StubRecipe {
        @Override
        public ResourceLocation getId() {
            return new ResourceLocation("create", "mixing/tea_water");
        }

        public List<FluidStack> getFluidResults() {
            return List.of(new FluidStack(Fluids.WATER, 500));
        }
    }

    private static final class TeaLavaProducer extends StubRecipe {
        @Override
        public ResourceLocation getId() {
            return new ResourceLocation("create", "mixing/tea_lava");
        }

        public List<FluidStack> getFluidResults() {
            return List.of(new FluidStack(Fluids.LAVA, 500));
        }
    }

    private static final class TeaConsumerRecipe extends StubRecipe {
        @Override
        public ResourceLocation getId() {
            return new ResourceLocation("create", "mixing/tea");
        }

        @Override
        public ItemStack getResultItem() {
            return new ItemStack(Items.HONEY_BOTTLE);
        }

        public List<MatchingFluidsIngredient> getFluidIngredients() {
            return List.of(
                    new MatchingFluidsIngredient(Fluids.WATER, 500),
                    new MatchingFluidsIngredient(Fluids.LAVA, 500));
        }
    }

    private static final class FluidConsumerRecipe extends StubRecipe {
        @Override
        public ResourceLocation getId() {
            return new ResourceLocation("packai", "fluid_consumer");
        }

        @Override
        public ItemStack getResultItem() {
            return new ItemStack(Items.GLASS_BOTTLE);
        }

        public List<MatchingFluidsIngredient> getFluidIngredients() {
            return List.of(new MatchingFluidsIngredient(Fluids.WATER, 100));
        }
    }

    private static final class ThrowAllRecipe extends StubRecipe {
        @Override
        public ItemStack getResultItem() {
            throw new RuntimeException("boom");
        }

        @Override
        public NonNullList<Ingredient> getIngredients() {
            throw new RuntimeException("boom");
        }

        public Object getOutputItems() {
            throw new RuntimeException("boom");
        }

        public Object getFluidIngredients() {
            throw new RuntimeException("boom");
        }

        public Object getChemicalInput() {
            throw new RuntimeException("boom");
        }
    }
}
