package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.skps9.packai.client.routes.RouteStoreCodec;
import com.skps9.packai.config.PackAiConfig;

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
import net.minecraftforge.registries.ForgeRegistries;

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

        caseRequirementEnergyNotMaterial();
        caseRequirementChemicalIncomplete();
        caseRequirementGasKind();
        caseRequirementReflectFailSoft();
        caseObtainCensusConfigGate();
        casePlainRecipeRequirementsEmpty();

        caseFluidProducerStepComplete();
        caseUnknownFluidIncompleteWhenFlipEnabled();
        caseNaturalFluidNoMissingLine();
        caseCompleteFlipThreshold();
        casePlainRecipeCompleteUnchanged();

        caseCacheRoundTrip();
        caseCacheFingerprintMismatch();
        caseCacheCorruptFileNoThrow();
        caseCacheRecipeCountStale();
        caseCacheWriteSizeCapSkips();

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

    private static void caseRequirementEnergyNotMaterial() {
        ObtainGraph graph = ObtainGraph.build(List.of(new EnergyCostRecipe()));
        List<ObtainGraph.Edge> edges = graph.edgesFor(Items.IRON_INGOT);
        if (edges.isEmpty()) {
            fail("energy recipe edge missing");
        }
        ObtainGraph.Edge edge = edges.get(0);
        if (edge.inputGroups().size() != 1 || !edge.inputGroups().get(0).contains(Items.RAW_IRON)) {
            fail("energy should not add input groups: " + edge.inputGroups());
        }
        boolean energy = false;
        for (ObtainGraph.Requirement r : edge.requirements()) {
            if ("energy".equals(r.kind()) && r.amount() == 4000) {
                energy = true;
            }
        }
        if (!energy) {
            fail("requirements=" + edge.requirements());
        }
        ObtainPaths.Path path =
                new ObtainPaths.Path(Items.IRON_INGOT, List.of(new ObtainPaths.Step(edge, edge.inputGroups())), true);
        List<ObtainPathText.Line> lines = ObtainPathText.render(List.of(path), "en_us", 8);
        String text = lines.get(0).text();
        if (!text.contains("Machine needs 4000 FE")) {
            fail("energy line missing: " + text);
        }
        List<ObtainPaths.Path> paths = ObtainPaths.find(graph, Items.IRON_INGOT);
        if (paths.isEmpty() || !paths.get(0).complete()) {
            fail("energy cost should not force incomplete path"
                    + " inputsPartial=" + edge.inputsPartial()
                    + " hasUnrepresentableInputs="
                    + RecipeIo.hasUnrepresentableInputs(new EnergyCostRecipe())
                    + " requirements=" + edge.requirements()
                    + " paths.size=" + paths.size()
                    + " steps="
                    + (paths.isEmpty() ? -1 : paths.get(0).steps().size()));
        }
    }

    private static void caseRequirementChemicalIncomplete() {
        ObtainGraph graph = ObtainGraph.build(List.of(new ChemicalInputRecipe()));
        List<ObtainGraph.Edge> edges = graph.edgesFor(Items.IRON_INGOT);
        if (edges.isEmpty()) {
            fail("chemical recipe edge missing");
        }
        ObtainGraph.Edge edge = edges.get(0);
        boolean chemical = false;
        for (ObtainGraph.Requirement r : edge.requirements()) {
            if ("chemical".equals(r.kind()) && r.amount() == 100) {
                chemical = true;
            }
        }
        if (!chemical) {
            fail("requirements=" + edge.requirements());
        }
        if (!edge.inputsPartial()) {
            fail("chemical should mark inputsPartial");
        }
        List<ObtainPaths.Path> paths = ObtainPaths.find(graph, Items.IRON_INGOT);
        if (paths.isEmpty() || paths.get(0).complete()) {
            fail("path should be incomplete");
        }
    }

    private static void caseRequirementGasKind() {
        ObtainGraph graph = ObtainGraph.build(List.of(new GasInputRecipe()));
        ObtainGraph.Edge edge = graph.edgesFor(Items.IRON_INGOT).get(0);
        boolean gas = false;
        for (ObtainGraph.Requirement r : edge.requirements()) {
            if ("gas".equals(r.kind())) {
                gas = true;
            }
        }
        if (!gas) {
            fail("requirements=" + edge.requirements());
        }
    }

    private static void caseRequirementReflectFailSoft() {
        ObtainGraph graph = ObtainGraph.build(List.of(new EnergyThrowRecipe(), new ThermalStyleOut()));
        if (graph.edgeCount() < 2) {
            fail("expected edges from both recipes");
        }
        List<ObtainGraph.Edge> iron = graph.edgesFor(Items.IRON_INGOT);
        if (iron.isEmpty()) {
            fail("good recipe edge missing");
        }
        boolean anyEnergy = false;
        for (ObtainGraph.Edge e : iron) {
            for (ObtainGraph.Requirement r : e.requirements()) {
                if ("energy".equals(r.kind())) {
                    anyEnergy = true;
                }
            }
        }
        if (anyEnergy) {
            fail("throwing energy accessor should yield no requirement");
        }
    }

    private static void caseObtainCensusConfigGate() {
        boolean prev = PackAiConfig.obtainCensus();
        try {
            RecipeIo.censusHarnessCalls = 0;
            PackAiConfig.setObtainCensus(false);
            ObtainGraph.build(List.of(new EnergyCostRecipe()));
            if (RecipeIo.censusHarnessCalls != 0) {
                fail("census ran with obtainCensus=false, calls=" + RecipeIo.censusHarnessCalls);
            }
        } finally {
            PackAiConfig.setObtainCensus(prev);
        }
    }

    private static void casePlainRecipeRequirementsEmpty() {
        ObtainGraph graph = ObtainGraph.build(List.of(new PlainRecipe()));
        if (!graph.edgesFor(Items.AIR).isEmpty()) {
            fail("plain recipe should not build edge without output");
        }
        List<ObtainGraph.Requirement> reqs = RecipeIo.requirements(new PlainRecipe());
        if (!reqs.isEmpty()) {
            fail("plain requirements=" + reqs);
        }
        ObtainGraph graph2 = ObtainGraph.build(List.of(new ThermalStyleOut()));
        ObtainGraph.Edge edge = graph2.edgesFor(Items.IRON_INGOT).get(0);
        if (!edge.requirements().isEmpty()) {
            fail("thermal stub requirements=" + edge.requirements());
        }
        if (edge.inputsPartial()) {
            fail("thermal stub should not be partial");
        }
    }

    private static void caseFluidProducerStepComplete() {
        ObtainGraph graph = ObtainGraph.build(List.of(new FluidOutRecipe(), new FluidConsumerRecipe()));
        List<ObtainPaths.Path> paths = ObtainPaths.find(graph, Items.GLASS_BOTTLE);
        if (paths.isEmpty()) {
            fail("expected path with producer");
        }
        ObtainPaths.Step step = paths.get(0).steps().get(0);
        if (step.fluidSources().isEmpty()) {
            fail("expected fluidSources when producer exists");
        }
        if (!step.complete(graph)) {
            fail("step with producer should be complete");
        }
    }

    private static void caseUnknownFluidIncompleteWhenFlipEnabled() {
        String alien = firstNonNaturalFluidId();
        if (alien == null) {
            fail("no non-natural fluid in registry");
        }
        List<Recipe<?>> recipes = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            recipes.add(new WaterOnlyConsumerRecipe(i));
        }
        recipes.add(new AlienFluidConsumerRecipe(alien));
        ObtainGraph graph = ObtainGraph.build(recipes);
        if (!graph.completeFlipEnabled()) {
            fail("expected flip enabled at 10% unresolved, ratio edges=" + graph.fluidEdges()
                    + " unresolved=" + graph.fluidUnresolvedEdges());
        }
        ObtainGraph.Edge edge = graph.edgesFor(Items.GLASS_BOTTLE).get(0);
        ObtainPaths.Step step = new ObtainPaths.Step(edge, edge.inputGroups(), List.of());
        if (step.complete(graph)) {
            fail("unknown fluid step should be incomplete when flip enabled");
        }
        ObtainPaths.Path path = new ObtainPaths.Path(Items.GLASS_BOTTLE, List.of(step), false);
        List<ObtainPathText.Line> lines = ObtainPathText.render(List.of(path), "en_us", 8);
        String text = lines.get(0).text();
        if (!text.contains("fluid source not catalogued")) {
            fail("expected missing fluid line: " + text);
        }
    }

    private static void caseNaturalFluidNoMissingLine() {
        ObtainGraph graph = ObtainGraph.build(List.of(new FluidConsumerRecipe()));
        if (!graph.completeFlipEnabled()) {
            fail("water-only graph should enable flip");
        }
        List<ObtainPaths.Path> paths = ObtainPaths.find(graph, Items.GLASS_BOTTLE);
        ObtainPaths.Step step = paths.get(0).steps().get(0);
        if (!step.complete(graph)) {
            fail("natural water should not block complete");
        }
        List<ObtainPathText.Line> lines = ObtainPathText.render(paths, "en_us", 8);
        String text = lines.get(0).text();
        if (text.contains("fluid source not catalogued")) {
            fail("natural fluid should not emit missing line: " + text);
        }
    }

    private static void caseCompleteFlipThreshold() {
        ObtainGraph resolved = ObtainGraph.build(List.of(new FluidConsumerRecipe()));
        if (!resolved.completeFlipEnabled() || resolved.fluidUnresolvedEdges() != 0) {
            fail("resolved graph flip=" + resolved.completeFlipEnabled()
                    + " unresolved=" + resolved.fluidUnresolvedEdges());
        }
        String alien = firstNonNaturalFluidId();
        if (alien == null) {
            fail("no non-natural fluid");
        }
        List<Recipe<?>> allUnresolved = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            allUnresolved.add(new AlienFluidConsumerRecipe(alien, i));
        }
        ObtainGraph bad = ObtainGraph.build(allUnresolved);
        if (bad.fluidEdges() < 1 || bad.fluidUnresolvedEdges() != bad.fluidEdges()) {
            fail("expected all fluid edges unresolved");
        }
        if (bad.completeFlipEnabled()) {
            fail("ratio 1.0 should disable flip");
        }
    }

    private static void caseCacheRoundTrip() {
        ObtainGraph original = ObtainGraph.build(List.of(new ThermalStyleOut(), new FluidOutRecipe()));
        String fp = ObtainGraphCache.joinFingerprint("stamp", "1.19.2", "43.2.0", "dp");
        var root = ObtainGraphCache.encodeGraph(original, fp, 2);
        ObtainGraph back = ObtainGraphCache.decodeGraph(root);
        if (back == null) {
            fail("decode null");
        }
        if (back.edgeCount() != original.edgeCount()) {
            fail("edgeCount " + back.edgeCount() + " vs " + original.edgeCount());
        }
        if (back.byOutput().size() != original.byOutput().size()) {
            fail("byOutput size");
        }
        ObtainGraph.Edge origEdge = original.edgesFor(Items.IRON_INGOT).get(0);
        ObtainGraph.Edge backEdge = back.edgesFor(Items.IRON_INGOT).get(0);
        if (!origEdge.recipeId().equals(backEdge.recipeId())) {
            fail("recipeId");
        }
        if (!origEdge.output().equals(backEdge.output()) || origEdge.outputCount() != backEdge.outputCount()) {
            fail("output");
        }
        if (!origEdge.fluids().equals(backEdge.fluids())) {
            fail("fluids");
        }
        if (origEdge.inputsPartial() != backEdge.inputsPartial()) {
            fail("inputsPartial");
        }
        if (!origEdge.requirements().equals(backEdge.requirements())) {
            fail("requirements");
        }
    }

    private static void caseCacheFingerprintMismatch() {
        String a = ObtainGraphCache.joinFingerprint("a", "1.19.2", "f", "dp");
        String b = ObtainGraphCache.joinFingerprint("b", "1.19.2", "f", "dp");
        if (ObtainGraphCache.fingerprintMatches(a, b)) {
            fail("modsStamp change must miss");
        }
        String c = ObtainGraphCache.joinFingerprint("a", "1.19.2", "f", "dp");
        String d = ObtainGraphCache.joinFingerprint("a", "1.20", "f", "dp");
        if (ObtainGraphCache.fingerprintMatches(c, d)) {
            fail("mc version change must miss");
        }
    }

    private static void caseCacheCorruptFileNoThrow() {
        try {
            Path tmp = Files.createTempFile("packai_obtain_cache", ".json");
            Files.writeString(tmp, "{\"schemaVersion\":1,\"fingerprint\":\"x\"", StandardCharsets.UTF_8);
            ObtainGraphCache.LoadResult r = ObtainGraphCache.tryLoad(tmp, "x", 1, ObtainGraphCache.MAX_CACHE_BYTES);
            if (r.kind() != ObtainGraphCache.LoadKind.CORRUPT) {
                fail("expected corrupt got " + r.kind());
            }
            Files.deleteIfExists(tmp);
        } catch (Throwable t) {
            fail("corrupt load threw " + t);
        }
    }

    private static void caseCacheRecipeCountStale() {
        ObtainGraph graph = ObtainGraph.build(List.of(new ThermalStyleOut()));
        String fp = ObtainGraphCache.joinFingerprint("s", "1.19.2", "f", "dp");
        try {
            Path tmp = Files.createTempFile("packai_obtain_cache_stale", ".json");
            ObtainGraphCache.tryWrite(tmp, graph, fp, 99, ObtainGraphCache.MAX_CACHE_BYTES);
            ObtainGraphCache.LoadResult r = ObtainGraphCache.tryLoad(tmp, fp, 1, ObtainGraphCache.MAX_CACHE_BYTES);
            if (r.kind() != ObtainGraphCache.LoadKind.STALE) {
                fail("expected stale got " + r.kind());
            }
            Files.deleteIfExists(tmp);
        } catch (Throwable t) {
            fail("stale load threw " + t);
        }
    }

    private static void caseCacheWriteSizeCapSkips() {
        List<Recipe<?>> many = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            many.add(new IndexedThermalOut(i));
        }
        ObtainGraph big = ObtainGraph.build(many);
        String fp = ObtainGraphCache.joinFingerprint("s", "1.19.2", "f", "dp");
        try {
            Path dir = Files.createTempDirectory("packai_obtain_cache_cap");
            Path file = dir.resolve("cache.json");
            ObtainGraphCache.tryWrite(file, big, fp, many.size(), 1024);
            if (Files.exists(file)) {
                fail("size cap should not write file");
            }
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        } catch (Throwable t) {
            fail("cap write threw " + t);
        }
    }

    private static void casePlainRecipeCompleteUnchanged() {
        ObtainGraph graph = ObtainGraph.build(List.of(new ThermalStyleOut()));
        List<ObtainPaths.Path> paths = ObtainPaths.find(graph, Items.IRON_INGOT);
        if (paths.isEmpty() || !paths.get(0).complete()) {
            fail("plain item recipe path should stay complete");
        }
        ObtainGraph.Edge edge = paths.get(0).steps().get(0).edge();
        ObtainPaths.Step step = new ObtainPaths.Step(edge, edge.inputGroups(), List.of());
        if (!step.complete(graph)) {
            fail("plain step should be complete");
        }
        if (!step.complete()) {
            fail("plain step.complete() without graph should stay true");
        }
    }

    private static String firstNonNaturalFluidId() {
        for (var entry : ForgeRegistries.FLUIDS.getEntries()) {
            if (entry.getValue() == null || entry.getValue() == Fluids.EMPTY) {
                continue;
            }
            String id = entry.getKey().location().toString();
            if (!ObtainGraph.FluidSources.isNaturalFluid(id)) {
                return id;
            }
        }
        return null;
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

    private static final class EnergyCostRecipe extends ThermalStyleOut {
        public int getEnergy() {
            return 4000;
        }
    }

    private static final class EnergyThrowRecipe extends ThermalStyleOut {
        @Override
        public ResourceLocation getId() {
            return new ResourceLocation("create", "energy_throw");
        }

        public int getEnergy() {
            throw new RuntimeException("boom");
        }
    }

    private static final class ChemicalStackStub {
        int getAmount() {
            return 100;
        }

        String getType() {
            return "mekanism:oxygen";
        }
    }

    private static final class ChemicalInputRecipe extends ThermalStyleOut {
        public ChemicalStackStub getChemicalInput() {
            return new ChemicalStackStub();
        }
    }

    private static final class GasStackStub {
        int getAmount() {
            return 50;
        }

        String getType() {
            return "mekanism:hydrogen";
        }
    }

    private static final class GasInputRecipe extends ThermalStyleOut {
        public GasStackStub getGasInput() {
            return new GasStackStub();
        }
    }

    private static final class IndexedThermalOut extends StubRecipe {
        private final int idx;

        IndexedThermalOut(int idx) {
            this.idx = idx;
        }

        @Override
        public ResourceLocation getId() {
            return new ResourceLocation("packai", "thermal_out_" + idx);
        }

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

    private static class ThermalStyleOut extends StubRecipe {
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

    private static final class WaterOnlyConsumerRecipe extends StubRecipe {
        private final int idx;

        WaterOnlyConsumerRecipe(int idx) {
            this.idx = idx;
        }

        @Override
        public ResourceLocation getId() {
            return new ResourceLocation("packai", "water_only_" + idx);
        }

        @Override
        public ItemStack getResultItem() {
            return new ItemStack(Items.PAPER, 1);
        }

        public List<MatchingFluidsIngredient> getFluidIngredients() {
            return List.of(new MatchingFluidsIngredient(Fluids.WATER, 100));
        }
    }

    private static final class AlienFluidConsumerRecipe extends StubRecipe {
        private final String fluidId;
        private final int idx;

        AlienFluidConsumerRecipe(String fluidId) {
            this(fluidId, 0);
        }

        AlienFluidConsumerRecipe(String fluidId, int idx) {
            this.fluidId = fluidId;
            this.idx = idx;
        }

        @Override
        public ResourceLocation getId() {
            return new ResourceLocation("packai", "alien_" + idx);
        }

        @Override
        public ItemStack getResultItem() {
            return idx == 0 ? new ItemStack(Items.GLASS_BOTTLE) : new ItemStack(Items.PAPER);
        }

        public List<AlienFluidIngredient> getFluidIngredients() {
            return List.of(new AlienFluidIngredient(fluidId, 100));
        }
    }

    private static final class AlienFluidIngredient {
        private final String fluidId;
        private final int amount;

        AlienFluidIngredient(String fluidId, int amount) {
            this.fluidId = fluidId;
            this.amount = amount;
        }

        FluidStack[] getMatchingFluids() {
            var fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(fluidId));
            if (fluid == null) {
                return new FluidStack[0];
            }
            return new FluidStack[] {new FluidStack(fluid, amount)};
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
