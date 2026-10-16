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
        FocusTarget t = FocusProbe.probe(mc, PackAiConfig.entityFocusRange());
        askResolved(mc, t, "auto", "world", key);
    }

    /**
     * Same ask path as {@link #askFromWorld}, for a target already resolved (panel cache or only= filter).
     *
     * @param only {@code auto} | {@code block} | {@code entity}
     * @param way  {@code world} | {@code panel}
     * @param key  {@code focus} | {@code think}
     */
    public static void askResolved(Minecraft mc, FocusTarget t, String only, String way, String key) {
        if (mc == null || mc.player == null || mc.level == null || t == null) {
            return;
        }
        if (ChatSession.isBusy()) {
            return;
        }
        double range = PackAiConfig.entityFocusRange();
        FocusTrace.emitResolved(t, range, only, way, key);
        switch (t.kind()) {
            case BLOCK -> {
                if (t.blockPos() == null) {
                    toast(mc, "packai.focus.none");
                    return;
                }
                BlockState state = mc.level.getBlockState(t.blockPos());
                if (state.getBlock().asItem() != Items.AIR) {
                    AiAssistantScreen.openAndAskAboutBlock(state);
                } else {
                    toast(mc, "packai.focus.block_no_item", state.getBlock().getName());
                }
            }
            case ENTITY -> {
                if (t.state() == FocusTarget.State.GONE) {
                    toast(mc, "packai.focus.entity_gone");
                } else if (t.state() == FocusTarget.State.TOO_FAR) {
                    toast(mc, "packai.focus.too_far");
                } else {
                    Entity e = mc.level.getEntity(t.entityId());
                    if (e != null) {
                        AiAssistantScreen.openAndAskAboutEntity(e);
                    }
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
