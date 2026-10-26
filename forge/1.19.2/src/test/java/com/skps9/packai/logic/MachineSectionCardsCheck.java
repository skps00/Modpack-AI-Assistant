package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.skps9.packai.client.jei.IngredientReqHints;
import com.skps9.packai.client.jei.IngredientReqHints.ExtrasMode;
import com.skps9.packai.client.jei.JeiRecipeCards;
import com.skps9.packai.client.service.AskService;
import com.skps9.packai.config.PackAiConfig;
import com.skps9.packai.logic.ReplyLang;

import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Machine section cards + tooltip KEEP_ONLY (work order machine_cards). Run with -ea. */
public final class MachineSectionCardsCheck {
    private MachineSectionCardsCheck() {}

    public static void main(String[] args) throws Exception {
        tooltipGradeRejected();
        tooltipOreRejected();
        refineRequirementKept();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        bindConfig();
        try {
            stationCardsHarness();
            stationCardsNoJei();
            stationCardsNonBlockProcessor();
            stationCardsNonBlockItemNoCategories();
            stationCardsIconMatchFilter();
            stationCardsTitleFallbackFilter();
            stationCardsCatalystLayoutGate();
            catchAllInputUsesSkipped();
            catchAllUseNoteText();
            genericUseNotesDedupeAndCap();
            jeiLookupRecordsGenericUseNotes();
            catchAllUseNotesMentionGateAndCap();
            catchAllUseNotesCapAndDedupe();
            askServiceSyntheticMachineHeader();
            insertMarkersBeforeSources();
        } finally {
            JeiRecipeCards.resetStationCardsHarnessForTest();
        }
        System.out.println("MachineSectionCardsCheck OK");
    }

    private static void tooltipGradeRejected() {
        assert !IngredientReqHints.tooltipLooksLikeRequirement("Grade: not graded");
        assert !IngredientReqHints.wouldKeepTooltipExtra("Grade: not graded", ExtrasMode.KEEP_ONLY)
                : "Grade tooltip must not become material req";
    }

    private static void tooltipOreRejected() {
        assert IngredientReqHints.tooltipExtraRejected("Ore: forge:inert_crystal")
                : "Ore tooltip with registry id must be rejected";
    }

    private static void refineRequirementKept() {
        assert IngredientReqHints.tooltipLooksLikeRequirement("refine≥3");
        assert IngredientReqHints.tooltipExtraRejected("refine≥3") == false;
    }

    private static void stationCardsHarness() {
        JeiRecipeCards.stationCardsHarness = (machine, max) -> {
            List<RecipeCard> cards = new ArrayList<>();
            cards.add(stubCard("Enrichment Chamber", new Object()));
            cards.add(stubCard("Combiner", new Object()));
            cards.add(stubCard("Crusher", new Object()));
            if (cards.size() > max) {
                return List.copyOf(cards.subList(0, max));
            }
            return cards;
        };
        try {
            List<RecipeCard> out = JeiRecipeCards.stationCards(ItemStack.EMPTY, 2);
            assert out.size() <= 2 : out.size();
            Set<String> titles = new HashSet<>();
            for (RecipeCard c : out) {
                assert c != null && !c.isEmpty() : c;
                assert c.jeiLayout() != null : c.categoryTitle();
                assert titles.add(c.categoryTitle()) : "duplicate title " + c.categoryTitle();
            }
        } finally {
            JeiRecipeCards.resetStationCardsHarnessForTest();
        }
    }

    private static void stationCardsNoJei() {
        JeiRecipeCards.resetStationCardsHarnessForTest();
        List<RecipeCard> out = JeiRecipeCards.stationCards(ItemStack.EMPTY, 2);
        assert out != null && out.isEmpty() : out;
    }

    private static void stationCardsNonBlockProcessor() {
        ItemStack sandPaperLike = new ItemStack(Items.PAPER);
        JeiRecipeCards.stationCardsHarness = (machine, max) -> {
            if (machine == null || machine.isEmpty()) {
                return List.of();
            }
            List<RecipeCard> cards = new ArrayList<>();
            cards.add(stubCard("Sandpaper Polishing", new Object()));
            if (cards.size() > max) {
                return List.copyOf(cards.subList(0, max));
            }
            return cards;
        };
        try {
            List<RecipeCard> out = JeiRecipeCards.stationCards(sandPaperLike, 2);
            assert out != null && !out.isEmpty() : out;
            assert out.size() <= 2 : out.size();
        } finally {
            JeiRecipeCards.resetStationCardsHarnessForTest();
        }
    }

    private static void stationCardsNonBlockItemNoCategories() {
        JeiRecipeCards.resetStationCardsHarnessForTest();
        List<RecipeCard> out = JeiRecipeCards.stationCards(new ItemStack(Items.PAPER), 2);
        assert out != null && out.isEmpty() : out;
    }

    private static void stationCardsIconMatchFilter() {
        List<String> cats = List.of("mekanism:energized_smelter", "minecraft:furnace");
        List<String> narrowed = JeiRecipeCards.preferIconMatched(cats, "mekanism:energized_smelter"::equals);
        assert narrowed.size() == 1 && narrowed.get(0).equals("mekanism:energized_smelter") : narrowed;
        List<String> fallback = JeiRecipeCards.preferIconMatched(cats, c -> false);
        assert fallback.size() == 2 : fallback;
        JeiRecipeCards.stationCardsHarness = (machine, max) -> {
            List<RecipeCard> cards = new ArrayList<>();
            cards.add(stubCard("Only match", new Object()));
            return cards.size() > max ? List.copyOf(cards.subList(0, max)) : cards;
        };
        try {
            List<RecipeCard> out = JeiRecipeCards.stationCards(new ItemStack(Items.PAPER), 2);
            assert out.size() == 1 : out.size();
        } finally {
            JeiRecipeCards.resetStationCardsHarnessForTest();
        }
    }

    private static void stationCardsTitleFallbackFilter() {
        List<String> cats = List.of("Sandpaper Polishing", "Bottler");
        List<String> narrowed = JeiRecipeCards.preferTitleMatched(cats, "Sand Paper", s -> s);
        assert narrowed.size() == 1 && narrowed.get(0).equals("Sandpaper Polishing") : narrowed;
        List<String> noIconAllKept = JeiRecipeCards.preferIconMatched(cats, c -> false);
        assert noIconAllKept.size() == 2 : noIconAllKept;
        List<String> afterTitle = JeiRecipeCards.preferTitleMatched(noIconAllKept, "Sand Paper", s -> s);
        assert afterTitle.size() == 1 && afterTitle.get(0).equals("Sandpaper Polishing") : afterTitle;
    }

    /** Bottler-style: category hit via generic catalyst focus, layout catalyst is unrelated item. */
    private static void stationCardsCatalystLayoutGate() {
        ItemStack waterWheel = new ItemStack(Items.OAK_PLANKS);
        waterWheel.setHoverName(Component.literal("Large Water Wheel"));
        List<ItemStack> bottlerLayoutCats = List.of(new ItemStack(Items.GLASS_BOTTLE));
        assert !JeiRecipeCards.stationCatalystLayoutGate(
                bottlerLayoutCats, waterWheel, false, "Bottler")
                : "Bottler title must not exempt large water wheel";
        assert !JeiRecipeCards.stationCatalystLayoutGate(bottlerLayoutCats, waterWheel, false)
                : "must reject when focus not in layout CATALYST";
        assert JeiRecipeCards.stationCatalystLayoutGate(bottlerLayoutCats, waterWheel, true)
                : "category icon match exempts layout catalyst check";
        ItemStack furnace = new ItemStack(Items.FURNACE);
        assert JeiRecipeCards.stationCatalystLayoutGate(List.of(new ItemStack(Items.FURNACE)), furnace, false);
        ItemStack sandPaper = new ItemStack(Items.PAPER);
        sandPaper.setHoverName(Component.literal("Sand Paper"));
        assert JeiRecipeCards.stationCatalystLayoutGate(
                List.of(), sandPaper, false, "Sandpaper Polishing")
                : "title overlap must exempt when layout has no catalyst and icon mismatches";
    }

    private static void bindConfig() throws Exception {
        Path toml = Path.of(System.getProperty("java.io.tmpdir"), "packai-machine-section-cards-check.toml");
        Files.createDirectories(toml.getParent());
        Files.deleteIfExists(toml);
        CommentedFileConfig cfg = CommentedFileConfig.builder(toml).sync().autosave().build();
        cfg.load();
        PackAiConfig.SPEC.setConfig(cfg);
    }

    /** IC2 Recycler-style catch-all INPUT slot (36k+ alts); large tag (472 planks) is not catch-all. */
    private static void catchAllInputUsesSkipped() {
        int min = PackAiConfig.catchAllUseMinAlts();
        assert min == 2048 : "catchAllUseMinAlts default " + min;
        assert !JeiRecipeCards.catchAllInputSlot(472) : "472-plank tag must not be catch-all";
        assert JeiRecipeCards.catchAllInputSlot(36331);
        assert !JeiRecipeCards.catchAllInputSlot(1);
        assert !JeiRecipeCards.catchAllInputSlot(1500, 2000)
                : "ratio 0.75 must not be catch-all";
        PackAiConfig.CATCH_ALL_USE_MIN_ALTS.set(900);
        try {
            assert JeiRecipeCards.catchAllInputSlot(950, 1000) : "ratio 0.95 with floor met";
        } finally {
            PackAiConfig.CATCH_ALL_USE_MIN_ALTS.set(2048);
        }
        assert JeiRecipeCards.catchAllInputSlot(3000, 0)
                : "registered unknown → absolute floor only";
        assert !JeiRecipeCards.catchAllInputSlot(1000, 0);
        GenericUseNotes.clear();
        assert GenericUseNotes.snapshot().isEmpty();
    }

    private static void catchAllUseNoteText() {
        String note = ReplyLang.catchAllUseNote("zh_tw", "IC2 回收機", "廢料");
        assert note.contains("IC2 回收機") : note;
        assert note.contains("廢料") : note;
        assert note.contains("任何物品") : note;
        assert note.contains("不另作顯示") : note;
        String cosmetic = ReplyLang.cosmeticUseNote("zh_tw", "Framed Blocks");
        assert cosmetic.contains("Framed Blocks") : cosmetic;
        assert cosmetic.contains("外觀") : cosmetic;
        assert cosmetic.contains("不另作顯示") : cosmetic;
        String en = ReplyLang.catchAllUseNote("en_us", "Recycler", "Scrap");
        assert en.contains("no card is shown") : en;
        String body = "1. step\n\n[card:1]\n\n【來源】\n- JEI";
        GenericUseNotes.clear();
        assert AskService.appendCatchAllUseNotes(body, "zh_tw").equals(body);
        GenericUseNotes.addForTest("IC2 回收機", "廢料", GenericUseNotes.Kind.ANY_ITEM);
        PackAiConfig.setShowGenericUseNotes(false);
        try {
            assert AskService.appendCatchAllUseNotes(body, "zh_tw").equals(body);
        } finally {
            PackAiConfig.setShowGenericUseNotes(true);
        }
    }

    private static void genericUseNotesDedupeAndCap() {
        GenericUseNotes.clear();
        GenericUseNotes.addForTest("A", "out1", GenericUseNotes.Kind.ANY_ITEM);
        GenericUseNotes.addForTest("A", "out2", GenericUseNotes.Kind.ANY_ITEM);
        assert GenericUseNotes.snapshot().size() == 1 : GenericUseNotes.snapshot();
        GenericUseNotes.addForTest("B", "out", GenericUseNotes.Kind.ANY_ITEM);
        GenericUseNotes.addForTest("C", "", GenericUseNotes.Kind.COSMETIC);
        GenericUseNotes.addForTest("D", "", GenericUseNotes.Kind.COSMETIC);
        GenericUseNotes.addForTest("E", "out", GenericUseNotes.Kind.ANY_ITEM);
        assert GenericUseNotes.snapshot().size() == 4 : GenericUseNotes.snapshot();
        GenericUseNotes.addForTest("F", "out", GenericUseNotes.Kind.ANY_ITEM);
        assert GenericUseNotes.snapshot().size() == 4 : GenericUseNotes.snapshot();
    }

    private static void jeiLookupRecordsGenericUseNotes() throws Exception {
        Path jei = Path.of("src/main/java/com/skps9/packai/client/jei/JeiLookup.java");
        if (!Files.isRegularFile(jei)) {
            jei = Path.of("forge/1.19.2/src/main/java/com/skps9/packai/client/jei/JeiLookup.java");
        }
        String src = Files.readString(jei);
        assert src.contains("GenericUseNotes.add") : "JeiLookup must record generic use notes";
    }

    private static void catchAllUseNotesMentionGateAndCap() {
        GenericUseNotes.clear();
        GenericUseNotes.addForTest("Framing Saw", "out", GenericUseNotes.Kind.COSMETIC);
        GenericUseNotes.addForTest("Recycler", "Scrap", GenericUseNotes.Kind.ANY_ITEM);
        String noMention = "Stone is used for building.";
        assert AskService.appendCatchAllUseNotes(noMention, "en_us").equals(noMention)
                : "unmentioned categories must not add notes";

        GenericUseNotes.clear();
        GenericUseNotes.addForTest("Recycler", "Scrap", GenericUseNotes.Kind.ANY_ITEM);
        GenericUseNotes.addForTest("OtherMachine", "out", GenericUseNotes.Kind.ANY_ITEM);
        String oneMention = "The Recycler can process scrap.";
        String withOne = AskService.appendCatchAllUseNotes(oneMention, "en_us");
        assert withOne.startsWith(oneMention + "\n\n") : withOne;
        String blockOne = withOne.substring(oneMention.length() + 2);
        assert blockOne.split("\n").length == 1 : blockOne;
        assert blockOne.contains("Recycler") : blockOne;
        assert !blockOne.contains("OtherMachine") : blockOne;
    }

    private static void catchAllUseNotesCapAndDedupe() {
        GenericUseNotes.clear();
        GenericUseNotes.addForTest("AlphaCat", "out1", GenericUseNotes.Kind.ANY_ITEM);
        GenericUseNotes.addForTest("AlphaCat", "out2", GenericUseNotes.Kind.ANY_ITEM);
        GenericUseNotes.addForTest("BetaCat", "out", GenericUseNotes.Kind.ANY_ITEM);
        GenericUseNotes.addForTest("GammaCat", "out", GenericUseNotes.Kind.COSMETIC);
        GenericUseNotes.addForTest("DeltaCat", "out", GenericUseNotes.Kind.COSMETIC);
        String body = "AlphaCat BetaCat GammaCat DeltaCat mentioned here.";
        String patched = AskService.appendCatchAllUseNotes(body, "en_us");
        assert patched.startsWith(body + "\n\n") : patched;
        String block = patched.substring(body.length() + 2);
        String[] lines = block.split("\n");
        assert lines.length == 2 : "cap 2 lines, got " + lines.length + ": " + block;
        assert block.contains("AlphaCat") && block.contains("BetaCat") : block;
        assert !block.contains("GammaCat") && !block.contains("DeltaCat") : block;
    }

    private static void askServiceSyntheticMachineHeader() {
        ItemStack focus = new ItemStack(Items.PAPER);
        String focusLabel = focus.getHoverName().getString();
        String hdr = AskService.machineSectionHeaderForNonBlockStationCards(focus, "zh_tw");
        assert hdr.contains("【機器】") : hdr;
        assert hdr.contains(focusLabel) : "header must contain focus display name: " + hdr;
        assert hdr.contains("自動化") || hdr.contains("漏斗") : hdr;
        assert !hdr.contains("JEI：") && !hdr.contains("JEI:") : hdr;
        var merged = AskService.mergeMachineStationCards(
                "body\n\n【来源】\n- JEI",
                List.of(stubCard("craft", new Object())),
                List.of(stubCard("polish", new Object())),
                true,
                true);
        assert merged.cards().size() == 2 : merged.cards().size();
        assert merged.body().contains("[card:2]") : merged.body();
    }

    private static void insertMarkersBeforeSources() {
        String body = "1. step\n\n【来源】\n- JEI";
        String patched = AskService.insertStationCardMarkers(body, 1, 2, true);
        assert patched.contains("[card:2]") && patched.contains("[card:3]") : patched;
        assert patched.indexOf("[card:2]") < patched.indexOf("【来源】") : patched;
        List<RecipeCard> merged = AskService.mergeMachineStationCards(
                patched,
                List.of(stubCard("a", new Object())),
                List.of(stubCard("M", new Object()), stubCard("N", new Object())),
                true,
                true).cards();
        assert merged.size() == 3 : merged.size();
    }

    private static RecipeCard stubCard(String title, Object layout) {
        return RecipeCard.flow(
                        title,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(new RecipeExtra("stub", 0, 0, "")),
                        List.of())
                .withJeiLayout(layout);
    }
}
