package com.skps9.packai.logic;

import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

/** Recipe-type / toast-based machine labels for routes and obtain text (no invented names). */
public final class MachineLabels {
    private MachineLabels() {}

    /**
     * Null when nothing factual to show. Order: non-blank {@code actionLabel}, then
     * {@code typeId} (vanilla lang keys or mod type path title-case).
     */
    @Nullable
    public static String resolve(
            @Nullable String actionLabel, @Nullable ResourceLocation typeId, String lang) {
        if (actionLabel != null && !actionLabel.isBlank()) {
            return actionLabel.trim();
        }
        if (typeId == null) {
            return null;
        }
        String ns = typeId.getNamespace();
        String path = typeId.getPath();
        if ("minecraft".equals(ns)) {
            if (path.startsWith("crafting")) {
                return ReplyLang.tr(lang, "packai.machine.crafting_table");
            }
            switch (path) {
                case "smelting":
                    return ReplyLang.tr(lang, "packai.machine.furnace");
                case "blasting":
                    return ReplyLang.tr(lang, "packai.machine.blast_furnace");
                case "smoking":
                    return ReplyLang.tr(lang, "packai.machine.smoker");
                case "campfire_cooking":
                    return ReplyLang.tr(lang, "packai.machine.campfire");
                default:
                    break;
            }
        }
        return titleCasePath(path);
    }

    private static String titleCasePath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String[] parts = path.split("[_/]");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
