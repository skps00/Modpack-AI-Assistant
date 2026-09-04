package com.skps9.packai.logic;

import java.nio.file.Path;
import java.util.List;

import net.minecraft.world.item.ItemStack;

/** Live Minecraft context for {@link com.skps9.packai.api.AskTool} adapters. Bound via {@link AskToolLoop#bindEnv}. */
public final class AskToolEnv {
    public final ItemStack stack;
    public final PackIndex index;
    public final Path gameDir;
    public final List<String> scanners;
    public final ItemRef held;
    /** Set by JeiLookup when a Pass 2 station template was used. */
    public boolean jeiStationTemplate;
    public String purposeTooltip = "";
    public List<String> recipeCardLines = List.of();

    public AskToolEnv(ItemStack stack, PackIndex index, Path gameDir, List<String> scanners, ItemRef held) {
        this.stack = stack == null ? ItemStack.EMPTY : stack;
        this.index = index;
        this.gameDir = gameDir;
        this.scanners = scanners == null ? List.of() : scanners;
        this.held = held == null ? ItemRef.NONE : held;
    }

    public static AskToolEnv current() {
        Object env = AskToolLoop.env();
        return env instanceof AskToolEnv e ? e : null;
    }
}
