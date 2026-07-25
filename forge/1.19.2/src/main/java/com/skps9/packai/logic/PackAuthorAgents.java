package com.skps9.packai.logic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import com.skps9.packai.PackAiMod;

/**
 * Load optional pack-author guidance ({@code AGENTS.md}) into the LLM system prompt.
 * Similar idea to Cursor/repo agent files, but for in-game Pack AI customization.
 */
public final class PackAuthorAgents {
    /** Soft cap so one file cannot blow the token budget. */
    public static final int MAX_CHARS = 4000;

    private static final List<String> RELATIVE_CANDIDATES = List.of(
            "config/packai/AGENTS.md",
            "config/packai/agents.md",
            "kubejs/packai/AGENTS.md",
            "kubejs/packai/agents.md",
            "packai/AGENTS.md");

    private static volatile String cachedText = "";
    private static volatile String cachedRel = "";

    private PackAuthorAgents() {}

    /** Reload from {@code gameDir}; call on index warmup. */
    public static synchronized void reload(Path gameDir) {
        cachedText = "";
        cachedRel = "";
        if (gameDir == null) {
            return;
        }
        for (String rel : RELATIVE_CANDIDATES) {
            Path path = gameDir.resolve(rel);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try {
                String raw = Files.readString(path, StandardCharsets.UTF_8);
                String cleaned = sanitize(raw);
                if (cleaned.isBlank()) {
                    continue;
                }
                if (cleaned.length() > MAX_CHARS) {
                    cleaned = cleaned.substring(0, MAX_CHARS).trim() + "\n…";
                    PackAiMod.LOGGER.info("Pack author AGENTS.md truncated to {} chars ({})", MAX_CHARS, rel);
                }
                cachedText = cleaned;
                cachedRel = rel;
                PackAiMod.LOGGER.info("Loaded pack author agents from {}", rel);
                return;
            } catch (IOException e) {
                PackAiMod.LOGGER.debug("Skip pack agents {}: {}", rel, e.toString());
            }
        }
    }

    /** Relative path that was loaded, or empty. */
    public static String loadedPath() {
        return cachedRel;
    }

    /** Sanitized body only (no wrapper). */
    public static String rawText() {
        return cachedText;
    }

    /**
     * System-prompt fragment; empty when no file. Conflicts still lose to JEI / local facts.
     */
    public static String systemAddon(String replyLang) {
        String body = cachedText;
        if (body == null || body.isBlank()) {
            return "";
        }
        return ReplyLang.packAuthorAgentsLead(replyLang) + body + "\n";
    }

    static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.replace("\u0000", "").replace("\r\n", "\n").replace('\r', '\n');
        // Strip UTF-8 BOM
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
            s = s.substring(1);
        }
        return s.trim();
    }

    /** Whether a relative path is a recognized agents filename (for tests / docs). */
    public static boolean isAgentsFileName(String rel) {
        if (rel == null || rel.isBlank()) {
            return false;
        }
        String n = rel.replace('\\', '/').toLowerCase(Locale.ROOT);
        for (String c : RELATIVE_CANDIDATES) {
            if (c.toLowerCase(Locale.ROOT).equals(n)) {
                return true;
            }
        }
        return n.endsWith("/agents.md") && (n.contains("packai/") || n.startsWith("packai/"));
    }
}
