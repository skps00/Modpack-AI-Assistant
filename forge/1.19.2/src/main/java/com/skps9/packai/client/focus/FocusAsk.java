package com.skps9.packai.client.focus;

import com.skps9.packai.client.chat.ChatSession;
import com.skps9.packai.client.gui.AiAssistantScreen;
import com.skps9.packai.config.PackAiConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared world crosshair ask. Keybind and autotest both enter here.
 */
public final class FocusAsk {
    private FocusAsk() {}

    /**
     * Probe the crosshair and ask about the winner.
     *
     * @param key {@code focus} or {@code think}
     */
    public static void askFromWorld(Minecraft mc, String key) {
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }
        if (ChatSession.isBusy()) {
            return;
        }
        double range = PackAiConfig.entityFocusRange();
        FocusTarget t = FocusProbe.probe(mc, range);
        FocusTrace.emitResolved(t, range, "auto", "world", key);
        switch (t.kind()) {
            case BLOCK -> {
                BlockState state = mc.level.getBlockState(t.blockPos());
                if (state.getBlock().asItem() != Items.AIR) {
                    AiAssistantScreen.openAndAskAboutBlock(state);
                } else {
                    toast(mc, "packai.focus.block_no_item", state.getBlock().getName());
                }
            }
            case ENTITY -> {
                Entity e = mc.level.getEntity(t.entityId());
                if (e != null) {
                    AiAssistantScreen.openAndAskAboutEntity(e);
                }
            }
            case NONE -> toast(mc, "packai.focus.none");
            case ITEM -> {
            }
        }
    }

    private static void toast(Minecraft mc, String langKey, Object... args) {
        SystemToast.addOrUpdate(
                mc.getToasts(),
                SystemToast.SystemToastIds.TUTORIAL_HINT,
                Component.translatable("packai.screen.title"),
                Component.translatable(langKey, args));
    }
}
