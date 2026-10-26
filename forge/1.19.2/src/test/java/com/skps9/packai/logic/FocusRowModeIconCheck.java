package com.skps9.packai.logic;

import com.skps9.packai.client.focus.FocusTarget;
import com.skps9.packai.client.gui.FocusLineMode;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Focus row: block/entity mode icon replaces held pick icon (work order 18). */
public final class FocusRowModeIconCheck {
    private FocusRowModeIconCheck() {}

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ItemStack stoneStack = new ItemStack(Items.STONE);
        assert FocusLineMode.pickRowFocus("block", stoneStack).isEmpty()
                : "block mode must omit held pick icon";
        assert FocusLineMode.pickRowFocus("entity", stoneStack).isEmpty()
                : "entity mode must omit held pick icon";
        assert FocusLineMode.pickRowFocus("auto", stoneStack) == stoneStack : "auto keeps held stack";
        assert FocusLineMode.pickRowFocus("item", stoneStack) == stoneStack : "item mode keeps held stack";
        assert FocusLineMode.askHeldItem("block", stoneStack).isEmpty()
                : "block mode must not send held item to ask";
        assert FocusLineMode.askHeldItem("entity", stoneStack).isEmpty()
                : "entity mode must not send held item to ask";
        assert FocusLineMode.askHeldItem("auto", stoneStack) == stoneStack : "auto sends held stack to ask";
        assert FocusLineMode.askHeldItem("item", stoneStack) == stoneStack : "item mode sends held stack to ask";
        assert FocusLineMode.askHeldItem("", stoneStack) == stoneStack : "blank focusOnly treats as auto for ask";
        modeAwareOffNoModeIcon();
        System.out.println("FocusRowModeIconCheck OK");
    }

    private static void modeAwareOffNoModeIcon() {
        FocusTarget stone = new FocusTarget(
                FocusTarget.Kind.BLOCK,
                "Stone",
                "minecraft:stone",
                -1,
                2.5,
                FocusTarget.State.OK,
                null,
                null,
                BlockPos.ZERO);
        FocusLineMode.modeAware = false;
        try {
            ItemStack icon = FocusLineMode.modeIconStack(
                    null, "block", false, ItemStack.EMPTY, stone);
            assert icon.isEmpty() : "modeAware=false must not show mode icon";
        } finally {
            FocusLineMode.modeAware = true;
        }
    }
}
