package com.skps9.packai.client;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.logic.QuestGuide;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

/**
 * Opens FTB Quests book to a quest via client command (no Mixins / soft dep).
 */
public final class QuestBookOpener {
    private QuestBookOpener() {}

    public static void open(QuestGuide.Hit hit) {
        if (hit == null) {
            return;
        }
        String system = hit.system() == null ? "" : hit.system();
        String id = hit.questId() == null ? "" : hit.questId().trim();
        open(system, id);
    }

    /** Open by quest id (chat click / {@code /packai quest}). */
    public static void openById(String questId) {
        open("ftbquests", questId);
    }

    public static void open(String system, String questId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        String sys = system == null ? "" : system;
        if ("heracles".equals(sys)) {
            mc.setScreen(null);
            mc.player.displayClientMessage(Component.translatable("packai.status.open_heracles_manual"), false);
            return;
        }
        if (!ModList.get().isLoaded("ftbquests")) {
            mc.player.displayClientMessage(Component.translatable("packai.status.no_quest_mod"), false);
            return;
        }
        String id = questId == null ? "" : questId.trim();
        String cmd = id.isEmpty() ? "ftbquests open_book" : "ftbquests open_book " + id;
        PackAiMod.LOGGER.info("Opening quest book: {}", cmd);
        mc.setScreen(null);
        mc.player.connection.sendCommand(cmd);
    }
}
