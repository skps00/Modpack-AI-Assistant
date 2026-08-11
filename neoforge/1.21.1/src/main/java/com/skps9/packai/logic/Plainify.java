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
    /** Legacy MC color/format codes; Font.split interprets these and leaks color across the line. */
    private static final Pattern MC_FORMAT = Pattern.compile(
            "(?i)§#[0-9a-f]{6}|§[0-9a-fk-or]|[&][0-9a-fk-or]");
    private static final Pattern ITEM = Pattern.compile("['\"]([a-z0-9_.:/#-]+)['\"]", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM_ID = Pattern.compile(
            "\\b([a-z0-9_]+:[a-z0-9_./-]+)\\b", Pattern.CASE_INSENSITIVE);
    /** {@code gateway:ns:path} — path may contain {@code /}; protect before ITEM_ID strip. */
    private static final Pattern GATEWAY_REF = Pattern.compile(
            "(?i)\\bgateway:([a-z0-9_]+:[a-z0-9_./-]+)\\b");
    private static final Pattern LOOT_TO_GATEWAY = Pattern.compile(
            "(?i)^item:([a-z0-9_]+:[a-z0-9_./-]+)\\s+-\\[loot\\]->\\s+gateway:([a-z0-9_]+:[a-z0-9_./-]+)$");
    private static final Pattern REWARD_STACK = Pattern.compile(
            "(?i)^gateway:([a-z0-9_]+:[a-z0-9_./-]+)\\s+-\\[reward_stack\\]->\\s+item:([a-z0-9_]+:[a-z0-9_./-]+)$");
    private static final Pattern REWARD_LOOT = Pattern.compile(
            "(?i)^gateway:([a-z0-9_]+:[a-z0-9_./-]+)\\s+-\\[reward_loot\\]->\\s+(.+)$");
    private static final Pattern LOOT_TO_TABLE = Pattern.compile(
            "(?i)^item:([a-z0-9_]+:[a-z0-9_./-]+)\\s+-\\[loot\\]->\\s+table:([a-z0-9_]+:[a-z0-9_./-]+)$");
    private static final Pattern LOOT_TO_ENTITY = Pattern.compile(
            "(?i)^item:([a-z0-9_]+:[a-z0-9_./-]+)\\s+-\\[loot\\]->\\s+entity:([a-z0-9_]+:[a-z0-9_./-]+)$");
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
            return ReplyLang.unknownItem(ReplyLang.current());
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
            return ReplyLang.unknownItem(ReplyLang.current());
        }
        return s;
    }

    /**
     * Humanize a raw graph-fact edge for LLM / acquire prompts.
     * Edge-kind aware: {@code gateway:} rewards ≠ entity drops; keeps gateway id intact.
     */
    public static String humanizeGraphFact(String fact) {
        if (fact == null || fact.isBlank()) {
            return "";
        }
        String f = fact.trim();
        String lang = ReplyLang.current();
        Matcher m = LOOT_TO_GATEWAY.matcher(f);
        if (m.matches()) {
            return ReplyLang.gatewayRewardObtain(lang, m.group(2));
        }
        m = REWARD_STACK.matcher(f);
        if (m.matches()) {
            return ReplyLang.gatewayRewardStack(lang, m.group(1), displayName(m.group(2)));
        }
        m = REWARD_LOOT.matcher(f);
        if (m.matches()) {
            return ReplyLang.gatewayRewardLoot(lang, m.group(1), humanizeTextProtectingGateways(m.group(2)));
        }
        m = LOOT_TO_TABLE.matcher(f);
        if (m.matches()) {
            return ReplyLang.lootTableObtain(lang, m.group(2));
        }
        m = LOOT_TO_ENTITY.matcher(f);
        if (m.matches()) {
            return ReplyLang.entityLootObtain(lang, m.group(2), displayName(m.group(2)));
        }
        return humanizeTextProtectingGateways(f.replace("-[", " → ").replace("]->", " "));
    }

    /** Replace any item ids embedded in text with readable names. */
    public static String humanizeText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return humanizeTextProtectingGateways(text);
    }

    /**
     * Preserve {@code gateway:ns:path} before ITEM_ID stripping so path leaves
     * (any token) are not mistaken for standalone mob/item names.
     */
    private static String humanizeTextProtectingGateways(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        List<String> gateways = new ArrayList<>();
        Matcher gm = GATEWAY_REF.matcher(text);
        StringBuilder protectedBuf = new StringBuilder();
        while (gm.find()) {
            gateways.add(gm.group(1));
            gm.appendReplacement(
                    protectedBuf, Matcher.quoteReplacement("\u0001GW" + (gateways.size() - 1) + "\u0001"));
        }
        gm.appendTail(protectedBuf);

        Matcher m = ITEM_ID.matcher(protectedBuf.toString());
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(displayName(m.group(1))));
        }
        m.appendTail(sb);
        String lang = ReplyLang.current();
        String out = sb.toString()
                .replaceAll("(?i)\\bkubejs/[\\w./-]+", ReplyLang.packScript(lang))
                .replaceAll("(?i)\\bconfig/[\\w./-]+", ReplyLang.packConfig(lang))
                .replaceAll("\\{[^}]{0,80}\\}", "")
                .trim();
        for (int i = 0; i < gateways.size(); i++) {
            out = out.replace("\u0001GW" + i + "\u0001", ReplyLang.gatewayIdLabel(lang, gateways.get(i)));
        }
        return out;
    }

    /**
     * Remove {@code §6}/{@code &a} style codes so UI color args are not overridden.
     */
    public static String stripMcFormat(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return MC_FORMAT.matcher(text).replaceAll("");
    }

    /**
     * Strip markdown / emoji / format codes so answers render with the screen's color.
     */
    public static String forMinecraftUi(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String s = stripMcFormat(text.replace("\r\n", "\n").replace('\r', '\n'));
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
                + "\n\n" + ReplyLang.sourceHeader(ReplyLang.current())
                + ReplyLang.labelLocalRecipes(ReplyLang.current())
                + ReplyLang.notePackSpecific(ReplyLang.current());
    }

    public static String friendlyOffline(List<String> sources, String question) {
        return ReplyLang.friendlyOffline(ReplyLang.current(),
                question == null || question.isBlank() ? "" : humanizeText(question));
    }

    private static String one(String text) {
        String lang = ReplyLang.current();
        Matcher m = SHAPED.matcher(text);
        if (m.find()) {
            String out = displayName(clean(m.group(1)));
            Map<String, String> keys = keyMap(m.group(3));
            Map<String, Integer> mats = matsFromPattern(m.group(2), keys);
            return ReplyLang.shapedRecipe(lang, out, fmt(mats));
        }
        m = SHAPELESS.matcher(text);
        if (m.find()) {
            String out = displayName(clean(m.group(1)));
            Map<String, Integer> mats = new LinkedHashMap<>();
            Matcher im = ITEM.matcher(m.group(2));
            while (im.find()) {
                mats.merge(displayName(im.group(1)), 1, Integer::sum);
            }
            return ReplyLang.shapelessRecipe(lang, out, fmt(mats));
        }
        m = REMOVE.matcher(text);
        if (m.find()) {
            return ReplyLang.removedRecipe(lang);
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
        String lang = ReplyLang.current();
        if (mats.isEmpty()) {
            return ReplyLang.patternFallback(lang);
        }
        List<String> bits = new ArrayList<>();
        mats.forEach((k, v) -> bits.add(ReplyLang.quote(lang, displayName(k)) + "×" + v));
        return String.join(ReplyLang.sourceJoin(lang), bits);
    }
}
