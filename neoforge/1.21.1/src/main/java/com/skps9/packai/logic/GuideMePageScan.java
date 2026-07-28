package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GuideME markdown helpers — no GuideME classes.
 * Match focus item via YAML frontmatter {@code item_ids}; strip MD/MDX to plain text.
 */
public final class GuideMePageScan {
    public static final int DEFAULT_MAX_ENTRIES = 2;
    public static final int DEFAULT_MAX_CHARS = 3000;

    private static final Pattern FRONTMATTER = Pattern.compile(
            "^---\\r?\\n(.*?)\\r?\\n---\\r?\\n?", Pattern.DOTALL);
    private static final Pattern ITEM_IDS_BLOCK = Pattern.compile(
            "(?m)^item_ids:\\s*\\r?\\n((?:^[ \\t]*-[ \\t]*.+\\r?\\n?)+)");
    private static final Pattern ITEM_ID_LINE = Pattern.compile("^[ \\t]*-[ \\t]*(.+)$", Pattern.MULTILINE);
    private static final Pattern NAV_TITLE = Pattern.compile(
            "(?m)^navigation:\\s*\\r?\\n(?:^[ \\t]+.+$\\r?\\n)*?^[ \\t]+title:\\s*[\"']?([^\"'\\r\\n]+)[\"']?");
    private static final Pattern MDX_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern MD_LINK = Pattern.compile("\\[([^\\]]+)\\]\\([^)]*\\)");
    private static final Pattern MD_IMAGE = Pattern.compile("!\\[[^\\]]*\\]\\([^)]*\\)");
    private static final Pattern MD_HEADING = Pattern.compile("(?m)^#{1,6}\\s+");
    private static final Pattern MD_EMPHASIS = Pattern.compile("[*_]{1,3}([^*_]+)[*_]{1,3}");
    private static final Pattern MD_CODE = Pattern.compile("`([^`]+)`");

    private GuideMePageScan() {}

    /** Bare registry id without NBT / damage suffix. */
    public static String normalizeItemKey(String raw) {
        return PatchouliEntryScan.normalizeItemKey(raw);
    }

    public static boolean idMentions(String raw, String itemId) {
        return PatchouliEntryScan.idMentions(raw, itemId);
    }

    /** True when frontmatter {@code item_ids} lists the focus item. */
    public static boolean referencesItem(String markdown, String itemId) {
        if (markdown == null || itemId == null || itemId.isBlank()) {
            return false;
        }
        for (String id : itemIdsFromFrontmatter(markdown)) {
            if (idMentions(id, itemId)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> itemIdsFromFrontmatter(String markdown) {
        List<String> out = new ArrayList<>();
        String fm = frontmatterBody(markdown);
        if (fm.isEmpty()) {
            return out;
        }
        Matcher block = ITEM_IDS_BLOCK.matcher(fm);
        if (!block.find()) {
            return out;
        }
        Matcher lines = ITEM_ID_LINE.matcher(block.group(1));
        while (lines.find()) {
            String raw = lines.group(1).trim();
            if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
                raw = raw.substring(1, raw.length() - 1);
            } else if (raw.startsWith("'") && raw.endsWith("'") && raw.length() >= 2) {
                raw = raw.substring(1, raw.length() - 1);
            }
            if (!raw.isBlank()) {
                out.add(raw.trim());
            }
        }
        return out;
    }

    public static String navigationTitle(String markdown) {
        String fm = frontmatterBody(markdown);
        if (fm.isEmpty()) {
            return "";
        }
        Matcher m = NAV_TITLE.matcher(fm);
        return m.find() ? m.group(1).trim() : "";
    }

    /** Title + body plain text; MDX tags／markdown markup stripped. */
    public static String extractPlainText(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        String title = navigationTitle(markdown);
        if (!title.isBlank()) {
            parts.add(title);
        }
        String body = stripFrontmatter(markdown);
        String plain = stripMarkdown(body);
        if (!plain.isBlank()) {
            parts.add(plain);
        }
        return String.join("\n", parts).trim();
    }

    public static String stripFrontmatter(String markdown) {
        if (markdown == null) {
            return "";
        }
        Matcher m = FRONTMATTER.matcher(markdown);
        if (m.find()) {
            return markdown.substring(m.end());
        }
        return markdown;
    }

    public static String stripMarkdown(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw;
        s = MD_IMAGE.matcher(s).replaceAll("");
        s = MD_LINK.matcher(s).replaceAll("$1");
        s = MDX_TAG.matcher(s).replaceAll("");
        s = MD_HEADING.matcher(s).replaceAll("");
        s = MD_EMPHASIS.matcher(s).replaceAll("$1");
        s = MD_CODE.matcher(s).replaceAll("$1");
        s = s.replace("\r\n", "\n").replace('\r', '\n');
        s = s.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    public static String joinCapped(List<String> entryBodies, int maxEntries, int maxChars) {
        return PatchouliEntryScan.joinCapped(entryBodies, maxEntries, maxChars);
    }

    /** Score: item_ids hit = 3; else 0. */
    public static int matchScore(String markdown, String itemId) {
        return referencesItem(markdown, itemId) ? 3 : 0;
    }

    private static String frontmatterBody(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        Matcher m = FRONTMATTER.matcher(markdown);
        return m.find() ? m.group(1) : "";
    }

    /** Prefer matching lang folder segment under {@code guides/}. */
    public static int langRank(String path, String preferredLang) {
        if (path == null) {
            return 3;
        }
        String p = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        String want = preferredLang == null ? "" : preferredLang.toLowerCase(Locale.ROOT);
        if (!want.isEmpty() && p.contains("/" + want + "/")) {
            return 0;
        }
        if (p.contains("/en_us/")) {
            return 1;
        }
        return 2;
    }
}
