package com.skps9.packai.client;

import java.util.List;

import com.skps9.packai.logic.QuestGuide;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/**
 * In-game alerts when an ask finishes while the Pack AI screen is closed.
 */
public final class ReplyNotifier {
    private ReplyNotifier() {}

    /** Toast + chat; quest lines are clickable to open FTB book. */
    public static void alertReplyReady(List<QuestGuide.Hit> quests) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Component title = Component.translatable("packai.status.reply_ready_title");
        Component body = Component.translatable("packai.status.reply_ready_body");
        SystemToast.addOrUpdate(
                mc.getToasts(),
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                title,
                body);
        mc.player.displayClientMessage(
                Component.translatable("packai.status.reply_ready_chat").withStyle(ChatFormatting.GREEN),
                false);

        if (quests == null || quests.isEmpty()) {
            return;
        }
        mc.player.displayClientMessage(
                Component.translatable("packai.status.reply_quests_hint").withStyle(ChatFormatting.GRAY),
                false);
        int n = Math.min(3, quests.size());
        for (int i = 0; i < n; i++) {
            QuestGuide.Hit hit = quests.get(i);
            String qid = hit.questId() == null ? "" : hit.questId().trim();
            String label = QuestGuide.displayTitle(hit);
            if (label.length() > 40) {
                label = label.substring(0, 40) + "…";
            }
            if (qid.isEmpty()) {
                mc.player.displayClientMessage(
                        Component.literal(" • " + label).withStyle(ChatFormatting.AQUA), false);
                continue;
            }
            String cmd = "/packai quest " + qid;
            Component link = Component.literal(" • " + label).withStyle(style -> style
                    .withColor(ChatFormatting.AQUA)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                    .withHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("packai.status.open_quest_hover"))));
            mc.player.displayClientMessage(link, false);
        }
    }
}
