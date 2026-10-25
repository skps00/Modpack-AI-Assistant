package com.skps9.packai.logic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.skps9.packai.config.PackAiConfig;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Focus-item JEI catalog prefetch at ask start. Run with -ea. */
public final class AskFocusPrefetchCheck {
    private AskFocusPrefetchCheck() {}

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        bindConfig();
        try {
            outputAndUsesMerge();
            duplicateRecipeId();
            jeiExceptionNoChange();
            negativeControlDisabled();
        } finally {
            AskFocusPrefetch.resetJeiForTest();
            AskFocusPrefetch.setEnabledForTest(true);
            AskFocusPrefetch.lastPlacements.clear();
        }
        System.out.println("AskFocusPrefetchCheck OK");
    }

    private static void outputAndUsesMerge() {
        String itemId = "minecraft:egg";
        RecipeCard craft = RecipeCard.flow(
                "Crafting",
                List.of(new ItemStack(Items.HONEYCOMB)),
                List.of(),
                List.of(new ItemStack(Items.EGG)),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ).withSourceItemId(itemId).withFocusRole(RecipeCard.FocusRole.OUTPUT);
        RecipeCard incubation = RecipeCard.flow(
                "Incubation · BaBee Incubator",
                List.of(new ItemStack(Items.HONEYCOMB)),
                List.of(),
                List.of(new ItemStack(Items.DIAMOND)),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ).withSourceItemId(itemId).withFocusRole(RecipeCard.FocusRole.OUTPUT);
        RecipeCard uses = RecipeCard.flow(
                "Crafting",
                List.of(new ItemStack(Items.EGG)),
                List.of(),
                List.of(new ItemStack(Items.STICK)),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ).withSourceItemId(itemId).withFocusRole(RecipeCard.FocusRole.INPUT);

        AskFocusPrefetch.setJeiForTest((stack, maxOut, maxIn, deadline) -> {
            if (maxIn == 0) {
                return List.of(craft, incubation);
            }
            return List.of(uses);
        });

        AskLoopState loop = AskLoopState.start("how to get", itemId, List.of(), System.currentTimeMillis() + 60_000L);
        loop.setLang("en_us");
        AskToolEnv env = new AskToolEnv(ItemStack.EMPTY, null, null, List.of(), ItemRef.NONE);
        env.catalogCards = List.of();

        AskFocusPrefetch.run(loop, itemId, env, loop.deadlineMs());

        assert loop.catalogCards().size() == 3 : loop.catalogCards();
        assert AskFocusPrefetch.lastPlacements.size() == 3 : AskFocusPrefetch.lastPlacements;
        for (String p : AskFocusPrefetch.lastPlacements) {
            assert AskFocusPrefetch.PLACEMENT.equals(p) : p;
        }
        assert loop.recipeCardLines().size() == 3 : loop.recipeCardLines();
        System.out.println("outputAndUsesMerge OK");
    }

    private static void duplicateRecipeId() {
        String itemId = "minecraft:stone";
        RecipeCard a = RecipeCard.flow(
                "Same Cat",
                List.of(new ItemStack(Items.DIRT)),
                List.of(),
                List.of(new ItemStack(Items.STONE)),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ).withSourceItemId(itemId).withFocusRole(RecipeCard.FocusRole.OUTPUT);
        RecipeCard b = RecipeCard.flow(
                "Same Cat",
                List.of(new ItemStack(Items.DIRT)),
                List.of(),
                List.of(new ItemStack(Items.STONE)),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        ).withSourceItemId(itemId).withFocusRole(RecipeCard.FocusRole.OUTPUT);

        AskFocusPrefetch.setJeiForTest((stack, maxOut, maxIn, deadline) -> {
            if (maxIn == 0) {
                return List.of(a, b);
            }
            return List.of();
        });

        AskLoopState loop = AskLoopState.start("q", itemId, List.of(), System.currentTimeMillis() + 60_000L);
        AskToolEnv env = new AskToolEnv(ItemStack.EMPTY, null, null, List.of(), ItemRef.NONE);
        AskFocusPrefetch.run(loop, itemId, env, loop.deadlineMs());
        assert loop.catalogCards().size() == 1 : loop.catalogCards();
        System.out.println("duplicateRecipeId OK");
    }

    private static void jeiExceptionNoChange() {
        String itemId = "minecraft:apple";
        RecipeCard existing = RecipeCard.flow(
                "Keep",
                List.of(),
                List.of(),
                List.of(new ItemStack(Items.APPLE)),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        AskFocusPrefetch.setJeiForTest((stack, maxOut, maxIn, deadline) -> {
            throw new RuntimeException("jei down");
        });

        AskLoopState loop = AskLoopState.start("q", itemId, List.of(), System.currentTimeMillis() + 60_000L);
        loop.setCatalogCards(List.of(existing));
        AskToolEnv env = new AskToolEnv(ItemStack.EMPTY, null, null, List.of(), ItemRef.NONE);
        env.catalogCards = loop.catalogCards();

        AskFocusPrefetch.run(loop, itemId, env, loop.deadlineMs());
        assert loop.catalogCards().size() == 1 : loop.catalogCards();
        assert "Keep".equals(loop.catalogCards().get(0).categoryTitle()) : loop.catalogCards();
        System.out.println("jeiExceptionNoChange OK");
    }

    private static void negativeControlDisabled() {
        String itemId = "minecraft:bread";
        AskFocusPrefetch.setEnabledForTest(false);
        AskFocusPrefetch.setJeiForTest((stack, maxOut, maxIn, deadline) -> {
            List<RecipeCard> lots = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                lots.add(RecipeCard.flow(
                        "Cat" + i,
                        List.of(),
                        List.of(),
                        List.of(new ItemStack(Items.BREAD)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()).withFocusRole(RecipeCard.FocusRole.OUTPUT));
            }
            return lots;
        });
        AskLoopState loop = AskLoopState.start("q", itemId, List.of(), System.currentTimeMillis() + 60_000L);
        AskToolEnv env = new AskToolEnv(ItemStack.EMPTY, null, null, List.of(), ItemRef.NONE);
        AskFocusPrefetch.run(loop, itemId, env, loop.deadlineMs());
        assert loop.catalogCards().isEmpty() : loop.catalogCards();
        System.out.println("negativeControlDisabled OK");
    }

    private static void bindConfig() throws Exception {
        Path toml = Path.of(System.getProperty("java.io.tmpdir"), "packai-focus-prefetch-check.toml");
        Files.createDirectories(toml.getParent());
        CommentedFileConfig cfg = CommentedFileConfig.builder(toml).sync().autosave().build();
        cfg.load();
        PackAiConfig.SPEC.setConfig(cfg);
    }
}
