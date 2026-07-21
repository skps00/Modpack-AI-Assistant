package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort recipe → plain Chinese. Never show raw code, paths, or item ids to the player.
 */
public final class Plainify {
    private static final Pattern ITEM = Pattern.compile("['\"]([a-z0-9_.:/#-]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM_ID = Pattern.compile(
            "\\b([a-z0-9_]+:[a-z0-9_./-]+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHAPED = Pattern.compile(
            "event\\.shaped\\(\\s*([^,\\n]+)\\s*,\\s*\\[([\\s\\S]*?)\\]\\s*,\\s*\\{([\\s\\S]*?)\\}\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SHAPELESS = Pattern.compile(
            "event\\.shapeless\\(\\s*([^,\\n]+)\\s*,\\s*\\[([\\s\\S]*?)\\]\\s*\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REMOVE = Pattern.compile(
            "event\\.remove\\(\\s*\\{([^}]*)\\}\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    private Plainify() {}

    /**
     * Turn {@code mod:item_name} into a short readable label (no namespace / no code look).
     */
    public static String displayName(String idOrRaw) {
        if (idOrRaw == null || idOrRaw.isBlank()) {
            return "（未知物品）";
        }
        String s = idOrRaw.trim().replace("'", "").replace("\"", "");
        Matcher m = ITEM_ID.matcher(s);
        if (m.find()) {
            s = m.group(1);
        }
        int colon = s.indexOf(':');
        if (colon > 0) {
            s = s.substring(colon + 1);
        }
        int slash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('.'));
        if (slash >= 0 && slash < s.length() - 1) {
            s = s.substring(slash + 1);
        }
        s = s.replace('_', ' ').replace('-', ' ').trim();
        if (s.isEmpty()) {
            return "（未知物品）";
        }
        return s;
    }

    /** Replace any item ids embedded in text with readable names. */
    public static String humanizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher m = ITEM_ID.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(displayName(m.group(1))));
        }
        m.appendTail(sb);
        return sb.toString()
                .replaceAll("(?i)\\bkubejs/[\\w./-]+", "整合包腳本")
                .replaceAll("(?i)\\bconfig/[\\w./-]+", "整合包設定")
                .replaceAll("\\{[^}]{0,80}\\}", "")
                .trim();
    }

    /**
     * Strip markdown / emoji so answers render in Minecraft's default font.
     */
    public static String forMinecraftUi(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String s = text.replace("\r\n", "\n").replace('\r', '\n');
        s = s.replaceAll("(?m)^#{1,6}\\s*", "");
        s = s.replaceAll("(?m)^---+\\s*$", "");
        s = s.replaceAll("\\*\\*([^*\\n]+)\\*\\*", "$1");
        s = s.replaceAll("__([^_\\n]+)__", "$1");
        s = s.replaceAll("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)", "$1");
        s = s.replaceAll("`([^`\\n]+)`", "$1");
        s = s.replace("→", "->").replace("⇒", "->");

        StringBuilder out = new StringBuilder(s.length());
        int[] cps = s.codePoints().toArray();
        for (int cp : cps) {
            if (isMinecraftSafeChar(cp)) {
                out.appendCodePoint(cp);
            }
        }
        return out.toString()
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    /** Default MC font: ASCII + Latin + CJK; no emoji / most symbol planes. */
    private static boolean isMinecraftSafeChar(int cp) {
        if (cp == '\n' || cp == '\t') {
            return true;
        }
        if (cp >= 0x20 && cp <= 0x7E) {
            return true;
        }
        if (cp >= 0xA0 && cp <= 0x024F) {
            return true;
        }
        if (cp >= 0x3000 && cp <= 0x30FF) {
            return true;
        }
        if (cp >= 0x3400 && cp <= 0x4DBF) {
            return true;
        }
        if (cp >= 0x4E00 && cp <= 0x9FFF) {
            return true;
        }
        if (cp >= 0xF900 && cp <= 0xFAFF) {
            return true;
        }
        if (cp >= 0xFF00 && cp <= 0xFFEF) {
            return true;
        }
        // common punctuation Minecraft often has
        return cp == 0x2013 || cp == 0x2014 || cp == 0x2018 || cp == 0x2019
                || cp == 0x201C || cp == 0x201D || cp == 0x2026 || cp == 0x00B7;
    }

    public static String plainify(List<String> snippets, List<String> sources) {
        List<String> parts = new ArrayList<>();
        for (String snip : snippets) {
            String body = snip;
            if (body.startsWith("// file:") && body.contains("\n")) {
                body = body.substring(body.indexOf('\n') + 1);
            }
            String p = one(body);
            if (p != null) {
                parts.add(p);
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join("\n\n", parts)
                + "\n\n【來源】整合包本地配方"
                + "\n【注意】此為本包設定，可能與通用 wiki 不同。";
    }

    public static String friendlyOffline(List<String> sources, String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("目前無法用 AI 詳細說明（離線或未設定模型）。\n");
        sb.append("建議：打開任務書查看相關任務，或用 JEI／EMI 查手上物品的配方。\n");
        if (question != null && !question.isBlank()) {
            sb.append("你的問題：").append(humanizeText(question));
        }
        return sb.toString();
    }

    private static String one(String text) {
        Matcher m = SHAPED.matcher(text);
        if (m.find()) {
            String out = displayName(clean(m.group(1)));
            Map<String, String> keys = keyMap(m.group(3));
            Map<String, Integer> mats = matsFromPattern(m.group(2), keys);
            return "【作法】用工作台（有序）合成「" + out + "」"
                    + "\n【材料】" + fmt(mats)
                    + "\n【步驟】1. 打開工作台 2. 依配方擺放 3. 取出「" + out + "」";
        }
        m = SHAPELESS.matcher(text);
        if (m.find()) {
            String out = displayName(clean(m.group(1)));
            Map<String, Integer> mats = new LinkedHashMap<>();
            Matcher im = ITEM.matcher(m.group(2));
            while (im.find()) {
                String id = im.group(1);
                mats.merge(id, 1, Integer::sum);
            }
            return "【作法】用工作台（無序）合成「" + out + "」"
                    + "\n【材料】" + fmt(mats)
                    + "\n【步驟】1. 打開工作台 2. 放入材料 3. 取出「" + out + "」";
        }
        m = REMOVE.matcher(text);
        if (m.find()) {
            return "【作法】整合包已移除或封鎖某些原版／模組配方"
                    + "\n【步驟】請改看任務書，或用 JEI／EMI 查還有哪些可用作法";
        }
        return null;
    }

    private static Map<String, String> keyMap(String blob) {
        Map<String, String> map = new LinkedHashMap<>();
        Matcher m = Pattern.compile("([A-Za-z0-9])\\s*:\\s*(?:Item\\.of\\()?['\"]([^'\"]+)['\"]")
                .matcher(blob);
        while (m.find()) {
            map.put(m.group(1), m.group(2));
        }
        return map;
    }

    private static Map<String, Integer> matsFromPattern(String pattern, Map<String, String> keys) {
        Map<String, Integer> mats = new LinkedHashMap<>();
        for (String line : pattern.split(",")) {
            String s = line.trim().replace("'", "").replace("\"", "");
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == ' ' || c == '.' || c == '_') {
                    continue;
                }
                String name = keys.getOrDefault(String.valueOf(c), String.valueOf(c));
                mats.merge(name, 1, Integer::sum);
            }
        }
        return mats;
    }

    private static String clean(String raw) {
        Matcher m = ITEM.matcher(raw);
        if (m.find()) {
            return m.group(1);
        }
        return raw.trim().replace("'", "").replace("\"", "");
    }

    private static String fmt(Map<String, Integer> mats) {
        if (mats.isEmpty()) {
            return "（請用 JEI／EMI 對照配方格子）";
        }
        List<String> bits = new ArrayList<>();
        mats.forEach((k, v) -> bits.add("「" + displayName(k) + "」×" + v));
        return String.join("、", bits);
    }
}
