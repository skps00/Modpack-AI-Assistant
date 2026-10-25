package com.skps9.packai.client.gui;

import com.skps9.packai.client.focus.FocusTarget;
import com.skps9.packai.logic.ItemResolver;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Mode-aware focus row keys (work order BB) + mode icon layout (BC).
 */
public final class FocusLineModeCheck {
    private static final int ANCHOR_X = 40;
    private static final int SEG_X = 360;

    private FocusLineModeCheck() {}

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        checkItemMode();
        checkBlockMode();
        checkEntityMode();
        checkNegativeControlIgnoresMode();
        checkItemModeIconLayout();
        checkBlockModeIconLayout();
        checkNegativeControlUnselectedIcon();
        System.out.println("FocusLineModeCheck OK");
    }

    private static void checkItemMode() {
        FocusLineMode.Line withItem =
                FocusLineMode.resolve("auto", true, "Bee Egg", FocusTarget.none());
        assert "packai.focus.item".equals(withItem.key()) : withItem.key();
        assert withItem.args().length == 1 && "Bee Egg".equals(withItem.args()[0]);

        FocusLineMode.Line noItem =
                FocusLineMode.resolve("auto", false, "", FocusTarget.none());
        assert "packai.focus.no_item".equals(noItem.key()) : noItem.key();
        assert noItem.args().length == 0;
    }

    private static void checkBlockMode() {
        FocusTarget stone = manualBlock("Stone", "minecraft:stone", 2.5);
        FocusLineMode.Line ok = FocusLineMode.resolve("block", false, "", stone);
        assert "packai.focus.block".equals(ok.key()) : ok.key();
        assert ok.args().length == 1 && "Stone".equals(ok.args()[0]);

        FocusLineMode.Line none = FocusLineMode.resolve("block", false, "", FocusTarget.none());
        assert "packai.focus.no_block".equals(none.key()) : none.key();
    }

    private static void checkEntityMode() {
        FocusTarget cow = manualEntity("Cow", 9, 3.2, FocusTarget.State.OK);
        FocusLineMode.Line live = FocusLineMode.resolve("entity", false, "", cow);
        assert "packai.focus.current".equals(live.key()) : live.key();
        assert live.args().length == 3;
        assert "Cow".equals(live.args()[0]);
        assert "9".equals(live.args()[1]);
        assert "3.2".equals(live.args()[2]);

        FocusTarget gone = FocusTarget.gone("Cow", 9, 1.0);
        FocusLineMode.Line goneLine = FocusLineMode.resolve("entity", false, "", gone);
        assert "packai.focus.entity_gone".equals(goneLine.key()) : goneLine.key();

        FocusLineMode.Line noEntity =
                FocusLineMode.resolve("entity", false, "", FocusTarget.none());
        assert "packai.focus.no_entity".equals(noEntity.key()) : noEntity.key();
    }

    private static FocusTarget manualBlock(String label, String id, double dist) {
        return new FocusTarget(
                FocusTarget.Kind.BLOCK,
                label,
                id,
                -1,
                dist,
                FocusTarget.State.OK,
                null,
                null,
                net.minecraft.core.BlockPos.ZERO);
    }

    private static FocusTarget manualEntity(String label, int id, double dist, FocusTarget.State state) {
        return new FocusTarget(
                FocusTarget.Kind.ENTITY,
                label,
                "",
                id,
                dist,
                state,
                null,
                null,
                null);
    }

    private static void checkNegativeControlIgnoresMode() {
        FocusLineMode.modeAware = false;
        try {
            FocusLineMode.Line legacy =
                    FocusLineMode.resolve("auto", false, "", FocusTarget.none());
            assert !"packai.focus.no_item".equals(legacy.key())
                    : "negative control FAILED: without mode branch item-empty must not use no_item";
            assert "packai.focus.none".equals(legacy.key()) : legacy.key();
        } finally {
            FocusLineMode.modeAware = true;
        }
    }

    private static void checkItemModeIconLayout() {
        ItemStack beeEgg = new ItemStack(Items.BEE_SPAWN_EGG);
        assert FocusLineMode.showModeIcon(null, "auto", true, beeEgg, FocusTarget.none())
                : "item + stack → icon";
        FocusLineMode.TextLayout withIcon =
                FocusLineMode.textLayout(ANCHOR_X, SEG_X, true);
        assert withIcon.textX() == ANCHOR_X + FocusLineMode.MODE_ICON_TEXT_SHIFT : withIcon.textX();

        assert !FocusLineMode.showModeIcon(null, "auto", false, ItemStack.EMPTY, FocusTarget.none())
                : "empty item → no icon";
        FocusLineMode.TextLayout bare =
                FocusLineMode.textLayout(ANCHOR_X, SEG_X, false);
        assert bare.textX() == ANCHOR_X : bare.textX();
    }

    private static void checkBlockModeIconLayout() {
        FocusTarget stone = manualBlock("Stone", "minecraft:stone", 2.5);
        assert FocusLineMode.showModeIcon(null, "block", false, ItemStack.EMPTY, stone)
                : "block + id → icon";
        FocusLineMode.Line ok = FocusLineMode.resolve("block", false, "", stone);
        assert "packai.focus.block".equals(ok.key()) : ok.key();

        FocusTarget badId = manualBlock("Mystery", "packai:__no_such_block__", 1.0);
        assert !FocusLineMode.showModeIcon(null, "block", false, ItemStack.EMPTY, badId)
                : "unknown block id → no icon";
        FocusLineMode.Line stillText = FocusLineMode.resolve("block", false, "", badId);
        assert "packai.focus.block".equals(stillText.key()) : stillText.key();
        assert ItemResolver.stackFromId("packai:__no_such_block__").isEmpty() : "bad id empty stack";
    }

    private static void checkNegativeControlUnselectedIcon() {
        FocusLineMode.omitIconWhenUnselected = false;
        try {
            boolean icon = FocusLineMode.showModeIcon(
                    null, "auto", false, ItemStack.EMPTY, FocusTarget.none());
            assert icon : "negative control FAILED: without unselected guard empty item must show icon";
        } finally {
            FocusLineMode.omitIconWhenUnselected = true;
        }
    }
}
