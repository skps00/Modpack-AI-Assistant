package com.skps9.packai.client;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import com.skps9.packai.PackAiMod;
import com.skps9.packai.logic.QuestGuide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.ArgumentSignatures;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraftforge.fml.ModList;

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
        if (!trySendCommand(mc.player, cmd)) {
            mc.player.displayClientMessage(
                    Component.translatable("packai.status.quest_cmd_fallback", "/" + cmd), false);
        }
    }

    private static boolean trySendCommand(LocalPlayer player, String command) {
        if (player == null || command == null || command.isBlank()) {
            return false;
        }
        try {
            boolean signable = player.commandHasSignableArguments(command);
            if (!signable && player.commandUnsigned(command)) {
                return true;
            }
            player.commandSigned(command, null);
            return true;
        } catch (Throwable directError) {
            PackAiMod.LOGGER.debug("Quest open direct command path failed: {}", directError.toString());
        }

        try {
            Method unsigned = LocalPlayer.class.getDeclaredMethod("commandUnsigned", String.class);
            unsigned.setAccessible(true);
            Object result = unsigned.invoke(player, command);
            if (!(result instanceof Boolean b) || b.booleanValue()) {
                return true;
            }
        } catch (ReflectiveOperationException ignored) {
            // try next path
        }
        try {
            Method signed = LocalPlayer.class.getDeclaredMethod("commandSigned", String.class, Component.class);
            signed.setAccessible(true);
            signed.invoke(player, command, null);
            return true;
        } catch (ReflectiveOperationException ignored) {
            // try next path
        }
        try {
            Method legacy = LocalPlayer.class.getDeclaredMethod("sendCommand", String.class, Component.class);
            legacy.setAccessible(true);
            legacy.invoke(player, command, null);
            return true;
        } catch (ReflectiveOperationException ignored) {
            // try packet path
        }
        return trySendPacket(command);
    }

    private static boolean trySendPacket(String command) {
        try {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection == null) {
                return false;
            }
            LastSeenMessages.Update update = new LastSeenMessages.Update(LastSeenMessages.EMPTY, Optional.empty());
            ServerboundChatCommandPacket packet = new ServerboundChatCommandPacket(
                    command,
                    Instant.now(),
                    ThreadLocalRandom.current().nextLong(),
                    ArgumentSignatures.EMPTY,
                    false,
                    update);
            connection.send(packet);
            return true;
        } catch (Throwable packetError) {
            PackAiMod.LOGGER.debug("Quest open packet path failed: {}", packetError.toString());
            return false;
        }
    }
}
