package com.skps9.packai.logic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Obtain path item labels: readable path fallback + unknown_items log. */
public final class ObtainLabelFallbackCheck {
    private ObtainLabelFallbackCheck() {}

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        unknownIdReadablePath();
        knownIdUsesOfficialName();
        negativeControlUnknownItemLiteral();
        unknownIdLogged();
        System.out.println("ObtainLabelFallbackCheck OK");
    }

    private static void unknownIdReadablePath() {
        String lang = "zh_tw";
        String unk = ReplyLang.tr(lang, "packai.reply.unknown_item");
        OfficialDisplay.lookup = id -> "";
        try {
            String label = ObtainPathText.itemLabelForItem(Items.STICK, lang);
            assert !label.equals(unk) : label;
            assert !label.contains(":") : label;
            assert label.equals("Stick") || label.toLowerCase().contains("stick") : label;
            String cmd = ObtainPathText.itemLabelForItem(Items.COMMAND_BLOCK, lang);
            assert !cmd.equals(unk) : cmd;
            assert !cmd.contains(":") : cmd;
            assert cmd.startsWith("Command block") : cmd;
        } finally {
            OfficialDisplay.resetLookup();
        }
        System.out.println("unknownIdReadablePath OK");
    }

    private static void knownIdUsesOfficialName() {
        String lang = "en_us";
        OfficialDisplay.lookup = id -> "Iron Ingot";
        try {
            String label = ObtainPathText.itemLabelForItem(Items.IRON_INGOT, lang);
            assert "Iron Ingot".equals(label) : label;
        } finally {
            OfficialDisplay.resetLookup();
        }
        System.out.println("knownIdUsesOfficialName OK");
    }

    private static void negativeControlUnknownItemLiteral() {
        String lang = "zh_tw";
        String unk = ReplyLang.tr(lang, "packai.reply.unknown_item");
        String forced = ObtainPathText.harnessItemLabelOrUnknown(Items.STICK, lang);
        assert unk.equals(forced) : "simulated old unknown_item path: " + forced;
        System.out.println("negativeControlUnknownItemLiteral OK");
    }

    private static void unknownIdLogged() throws Exception {
        Path game = Files.createTempDirectory("packai-obtain-label");
        try {
            ObtainPathText.bindUnknownItemLog(game);
            OfficialDisplay.lookup = id -> "";
            try {
                ObtainPathText.itemLabelForItem(Items.STICK, "en_us");
            } finally {
                OfficialDisplay.resetLookup();
            }
            List<String> lines = Files.readAllLines(
                    UnknownItemLog.logFile(game), StandardCharsets.UTF_8);
            assert !lines.isEmpty() : "expected unknown_items row";
            assert lines.get(0).contains("minecraft:stick") : lines.get(0);
        } finally {
            ObtainPathText.bindUnknownItemLog(null);
            try {
                Files.walk(game).sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                        // temp cleanup
                    }
                });
            } catch (Exception ignored) {
                // temp cleanup
            }
        }
        System.out.println("unknownIdLogged OK");
    }
}
