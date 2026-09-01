package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Post-LLM scrub: strip PURPOSE / prompt section tags echoed into the player answer.
 * Also strips Pack AI tooltip overlay chrome before it enters {@code [PURPOSE]}.
 * Keeps intentional UI markers ({@code [[item:]]} / {@code [[recipe:]]} / {@code {{item:}}} /
 * {@code {{RECIPE}}}) for {@link RecipeEmbed}.
 * {@code [[recipe_cards:on|off]]} is scrubbed in {@link RecipeCardsMode#scrubMarker} /
 * {@link AskResult#withRecipeCards}.
 */
public final class AskReplyScrub {
    /**
     * PURPOSE / fact headers injected into prompts — never player-facing.
     * Matches {@code [SCROLL_EFFECT]}, {@code [PURPOSE]}, etc. (optional spaces).
     */
    private static final Pattern PROMPT_SECTION_TAG = Pattern.compile(
            "\\[\\s*(?:SCROLL_[A-Z0-9_]+|PURPOSE|GUIDE|VARIANT|AS_INGREDIENT|CONTAINED|CONSUME_USE|TOOL_BUILD|TETRA_USE|WORLDGEN)\\s*\\]",
            Pattern.CASE_INSENSITIVE);

    /**
     * Lone How-to-get header with no obtain prose before the next section.
     * Optional {@code 1.} prefix — models number 怎么来 then skip the empty body.
     * INPUT as-ingredient cards live in other parts — they do not fill this header.
     */
    private static final String HOW_TO_GET_LABEL =
            "(?:怎么来|怎么來|怎麼来|怎麼來|How to get|取得方式|获取方式|獲取方式|取得方法|How to obtain)";

    /** Optional {@code ##} / {@code 1.} / wrapping {@code 【】} or {@code []}. */
    private static final String HOW_TO_GET_HEAD_PREFIX =
            "(?:#{1,3}[ \\t]*)?(?:\\d+[.)][ \\t]*)?(?:[【\\[])?" + HOW_TO_GET_LABEL + "(?:[】\\]])?";

    private static final Pattern EMPTY_HOW_TO_GET = Pattern.compile(
            "(?im)^[ \\t]*" + HOW_TO_GET_HEAD_PREFIX
                    + "[ \\t]*[:：]?[ \\t]*\\r?\\n(?:[ \\t]*\\r?\\n)*"
                    + "(?=[ \\t]*(?:#{1,3}[ \\t]*)?(?:\\d+[.)][ \\t]*)?(?:怎么用|怎麼用|How to use|作为材料|作為材料)"
                    + "|【来源】|【來源】|\\[Sources\\]|\\z)");

    private static final Pattern HOW_TO_GET_HEAD = Pattern.compile(
            "(?im)^[ \\t]*" + HOW_TO_GET_HEAD_PREFIX + "(?:[:：]|\\s|\\z)");

    private static final Pattern AS_MATERIAL_HEAD = Pattern.compile(
            "(?im)^[ \\t]*(?:##[ \\t]*)?(?:\\d+[.)][ \\t]*)?(?:作为材料|作為材料)");

    private static final Pattern HOW_TO_USE_HEAD = Pattern.compile(
            "(?im)^[ \\t]*(?:##[ \\t]*)?(?:\\d+[.)][ \\t]*)?(?:怎么用|怎麼用|How to use)(?:[:：]|\\s|\\z)");

    private static final Pattern ITEM_TITLE_LINE = Pattern.compile("(?m)^\\[\\[item:[^\\]]+]][^\\n]*\\n");

    /** Line-start step number only — not "魔源消耗 9999". */
    private static final Pattern LINE_START_NUM = Pattern.compile("(?m)^[ \\t]*(\\d+)[.)][ \\t]+");

    /**
     * DeepSeek DSML tool-call dump ({@code <|DSML|>} or spaced {@code < | DSML | | tool_calls>}).
     * Inner parameter values (item ids) go away with the block — not player prose.
     */
    private static final Pattern DSML_TOOL_CALLS_BLOCK = Pattern.compile(
            "(?is)<\\s*\\|\\s*DSML\\s*\\|\\s*(?:>\\s*)?(?:\\|\\s*)?tool_calls\\s*>"
                    + ".*?"
                    + "</\\s*\\|\\s*DSML\\s*\\|\\s*(?:>\\s*)?(?:\\|\\s*)?tool_calls\\s*>");

    private static final Pattern DSML_INVOKE_BLOCK = Pattern.compile(
            "(?is)<\\s*\\|\\s*DSML\\s*\\|\\s*(?:>\\s*)?(?:\\|\\s*)?invoke\\b[^>]*>"
                    + ".*?"
                    + "</\\s*\\|\\s*DSML\\s*\\|\\s*(?:>\\s*)?(?:\\|\\s*)?invoke\\s*>");

    private static final Pattern GENERIC_TOOL_XML = Pattern.compile(
            "(?is)<\\s*tool_calls?\\b[^>]*>.*?</\\s*tool_calls?\\s*>"
                    + "|<\\s*function_calls?\\b[^>]*>.*?</\\s*function_calls?\\s*>"
                    + "|<\\|tool_call_begin\\|>.*?<\\|tool_call_end\\|>"
                    + "|<\\|tool_calls_section_begin\\|>.*?<\\|tool_calls_section_end\\|>");

    private static final Pattern LEFTOVER_TOOL_TOKEN = Pattern.compile(
            "(?i)</?\\s*\\|\\s*DSML\\s*\\|[^>]*>"
                    + "|</?\\|DSML\\|>"
                    + "|<\\|tool_call(?:s)?_(?:begin|end)\\|>"
                    + "|<\\|tool_calls_section_(?:begin|end)\\|>"
                    + "|</?\\s*invoke\\b[^>]*>"
                    + "|</?\\s*parameter\\b[^>]*>"
                    + "|</?\\s*tool_calls?\\b[^>]*>"
                    + "|</?\\s*function_calls?\\b[^>]*>");

    private static final Pattern CARD_ONLY_MARKERS = Pattern.compile(
            "\\[\\[recipe_card:\\d+]]|\\{\\{RECIPE}}");

    /**
     * Pure section-title line: optional {@code 1.} prefix, known label, then optional
     * whitespace / single colon / whitespace / EOL only. Prose like {@code 如果不知道怎么来…}
     * or {@code Usage in combat is limited to tools.} does not match (non-whitespace after the label).
     */
    private static final Pattern PURE_SECTION_HEADER = Pattern.compile(
            "^[ \\t]*(?:\\d+[.)][ \\t]*)?"
                    + "(怎么来|怎样来|怎么來|怎樣來|怎麼来|怎麼來|怎么用|怎麼用|用途|作为材料|作為材料|How to get|How to use|Usage)"
                    + "[ \\t]*[:：]?[ \\t]*$",
            Pattern.CASE_INSENSITIVE);

    private AskReplyScrub() {}

    /**
     * Remove leaked prompt section tags and model tool-call XML (DSML / tool_call).
     * Safe to run before {@link RecipeEmbed}
     * (does not touch recipe/item UI markers). Does not trim — callers tidy whitespace.
     */
    /**
     * Drop duplicate section headers (e.g. second {@code 怎么来} after {@code 怎样来}).
     * First occurrence of each section kind is kept; later pure title lines with the same kind are removed.
     */
    public static String stripDuplicateSectionHeaders(String reply) {
        if (reply == null || reply.isEmpty()) {
            return reply == null ? "" : reply;
        }
        String[] lines = reply.split("\\R", -1);
        Set<String> seen = new HashSet<>();
        List<String> kept = new ArrayList<>(lines.length);
        for (String line : lines) {
            Matcher m = PURE_SECTION_HEADER.matcher(line);
            if (m.matches()) {
                String key = canonicalSectionKey(m.group(1));
                if (seen.contains(key)) {
                    continue;
                }
                seen.add(key);
            }
            kept.add(line);
        }
        return String.join("\n", kept);
    }

    static String canonicalSectionKey(String label) {
        if (label == null || label.isEmpty()) {
            return "";
        }
        String t = label.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        if ("怎么来".equals(t) || "怎样来".equals(t) || "怎么來".equals(t) || "怎樣來".equals(t)
                || "怎麼来".equals(t) || "怎麼來".equals(t) || "how to get".equals(lower)) {
            return "how_to_get";
        }
        if ("怎么用".equals(t) || "怎麼用".equals(t) || "how to use".equals(lower) || "usage".equals(lower)) {
            return "how_to_use";
        }
        if ("用途".equals(t)) {
            return "purpose";
        }
        if ("作为材料".equals(t) || "作為材料".equals(t)) {
            return "as_material";
        }
        return lower;
    }

    public static String scrubPromptEcho(String answer) {
        if (answer == null || answer.isEmpty()) {
            return "";
        }
        String t = unescapeLiteralNewlines(answer);
        t = scrubLeakedToolXml(t);
        t = PROMPT_SECTION_TAG.matcher(t).replaceAll("");
        t = stripFactChrome(t);
        return tidyNewlines(t);
    }

    /**
     * Empty {@code 怎么来} heading: fill loot/JEI-info facts, or a pack-miss line.
     * Inserts a how-to-get block when the model skipped it and started at {@code 2. 作为材料}.
     * Does not substitute INPUT (JEI U) cards. When obtain cards exist, keep/insert the
     * heading so {@link RecipeEmbed} can park them there.
     */
    public static String ensureHowToGetBody(
            String answer, String obtainFacts, boolean hasObtainCards, String missLine
    ) {
        if (answer == null || answer.isEmpty()) {
            return answer == null ? "" : answer;
        }
        String out = unescapeLiteralNewlines(answer);
        String fill = playerObtainFill(obtainFacts);
        if (fill.isEmpty() && !hasObtainCards) {
            fill = missLine == null ? "" : unescapeLiteralNewlines(missLine).trim();
        }
        Matcher m = EMPTY_HOW_TO_GET.matcher(out);
        if (m.find()) {
            if (!fill.isEmpty()) {
                String heading = m.group().stripTrailing();
                int nl = heading.indexOf('\n');
                if (nl >= 0) {
                    heading = heading.substring(0, nl).stripTrailing();
                }
                out = m.replaceFirst(Matcher.quoteReplacement(heading + "\n" + fill + "\n"));
            }
        } else if (!HOW_TO_GET_HEAD.matcher(out).find() && (!fill.isEmpty() || hasObtainCards)) {
            String heading = howToGetInsertHeading(out);
            String block = fill.isEmpty() ? heading + "\n" : heading + "\n" + fill + "\n";
            int at = insertHowToGetAt(out);
            out = out.substring(0, at) + block + out.substring(at);
        }
        out = reorderHowToGetBeforeMaterials(out);
        out = fixOrphanLeadingList(out);
        out = collapseDuplicateHowToGet(out);
        return tidyNewlines(stripFactChrome(out));
    }

    /**
     * Literal {@code \n} / {@code \r\n} / {@code \r} → real newlines. Skips {@code [[…]]} / {@code {{…}}}.
     */
    public static String unescapeLiteralNewlines(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        StringBuilder sb = new StringBuilder(text.length());
        int marker = -1;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (marker < 0 && c == '[' && i + 1 < text.length() && text.charAt(i + 1) == '[') {
                marker = 0;
                sb.append("[[");
                i += 2;
                continue;
            }
            if (marker < 0 && c == '{' && i + 1 < text.length() && text.charAt(i + 1) == '{') {
                marker = 1;
                sb.append("{{");
                i += 2;
                continue;
            }
            if (marker == 0 && c == ']' && i + 1 < text.length() && text.charAt(i + 1) == ']') {
                marker = -1;
                sb.append("]]");
                i += 2;
                continue;
            }
            if (marker == 1 && c == '}' && i + 1 < text.length() && text.charAt(i + 1) == '}') {
                marker = -1;
                sb.append("}}");
                i += 2;
                continue;
            }
            if (marker < 0 && c == '\\' && i + 1 < text.length()) {
                char n = text.charAt(i + 1);
                if (n == 'r' && i + 3 < text.length() && text.charAt(i + 2) == '\\' && text.charAt(i + 3) == 'n') {
                    sb.append('\n');
                    i += 4;
                    continue;
                }
                if (n == 'n' || n == 'r') {
                    sb.append('\n');
                    i += 2;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    static String tidyNewlines(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        return text.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n");
    }

    static String playerObtainFill(String obtainFacts) {
        if (obtainFacts == null || obtainFacts.isBlank()) {
            return "";
        }
        return stripFactChrome(unescapeLiteralNewlines(obtainFacts)).trim();
    }

    static String stripFactChrome(String answer) {
        if (answer == null || answer.isEmpty()) {
            return answer == null ? "" : answer;
        }
        String[] lines = answer.split("\\R", -1);
        List<String> keep = new ArrayList<>(lines.length);
        for (String raw : lines) {
            if (isFactChromeLine(raw)) {
                continue;
            }
            keep.add(raw);
        }
        return String.join("\n", keep);
    }

    static boolean isFactChromeLine(String line) {
        if (line == null) {
            return false;
        }
        String t = line.trim();
        if (t.isEmpty()) {
            return false;
        }
        if (t.contains("【本地获取】") || t.contains("【本地獲取】")) {
            return true;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        if (lower.contains("[local acquire]")) {
            return true;
        }
        if (lower.startsWith("jei_info_acquire:") || lower.startsWith("jei_info_use:")) {
            return true;
        }
        return isQuotedLatinDumpTitle(t);
    }

    static boolean isQuotedLatinDumpTitle(String t) {
        String inner = unwrapDumpQuotes(t);
        if (inner == null || inner.isEmpty() || inner.length() > 80) {
            return false;
        }
        boolean letter = false;
        for (int cp : inner.codePoints().toArray()) {
            if (Character.isLetter(cp)) {
                if (Character.UnicodeScript.of(cp) != Character.UnicodeScript.LATIN) {
                    return false;
                }
                letter = true;
                continue;
            }
            if (Character.isDigit(cp) || cp == ' ' || cp == '_' || cp == '-') {
                continue;
            }
            return false;
        }
        return letter;
    }

    static String unwrapDumpQuotes(String t) {
        if (t == null || t.length() < 2) {
            return null;
        }
        char a = t.charAt(0);
        char b = t.charAt(t.length() - 1);
        if ((a == '"' && b == '"') || (a == '\'' && b == '\'')
                || (a == '“' && b == '”') || (a == '「' && b == '」')) {
            return t.substring(1, t.length() - 1).trim();
        }
        return null;
    }

    /**
     * Two how-to-get blocks: keep the numbered human one, drop raw FACT dump.
     */
    static String collapseDuplicateHowToGet(String answer) {
        if (answer == null || answer.isEmpty()) {
            return answer == null ? "" : answer;
        }
        List<int[]> spans = howToGetSpans(answer);
        if (spans.size() < 2) {
            return answer;
        }
        int keep = 0;
        int best = Integer.MIN_VALUE;
        for (int i = 0; i < spans.size(); i++) {
            int[] sp = spans.get(i);
            int score = scoreHowToGetSection(answer.substring(sp[0], sp[1]));
            if (score > best) {
                best = score;
                keep = i;
            }
        }
        StringBuilder sb = new StringBuilder(answer.length());
        int last = 0;
        for (int i = 0; i < spans.size(); i++) {
            int[] sp = spans.get(i);
            sb.append(answer, last, sp[0]);
            if (i == keep) {
                sb.append(answer, sp[0], sp[1]);
            }
            last = sp[1];
        }
        sb.append(answer, last, answer.length());
        return sb.toString();
    }

    static List<int[]> howToGetSpans(String answer) {
        List<int[]> spans = new ArrayList<>();
        Matcher m = HOW_TO_GET_HEAD.matcher(answer);
        int from = 0;
        while (from < answer.length() && m.find(from)) {
            int start = m.start();
            int end = sectionEnd(answer, start);
            if (end <= start) {
                break;
            }
            spans.add(new int[] {start, end});
            from = Math.max(start + 1, end);
        }
        return spans;
    }

    static int scoreHowToGetSection(String section) {
        int nl = section.indexOf('\n');
        String body = nl < 0 ? "" : section.substring(nl + 1);
        String cleaned = stripFactChrome(body).trim();
        int score = 0;
        if (cleaned.isEmpty()) {
            score -= 8;
        }
        if (LINE_START_NUM.matcher(body).find()) {
            score += 12;
        }
        String b = body.toLowerCase(Locale.ROOT);
        if (b.contains("jei") || body.contains("按 R") || body.contains("按R") || body.contains("按 r")) {
            score += 5;
        }
        if (isDumpHeavy(body)) {
            score -= 20;
        }
        score += Math.min(cleaned.length() / 8, 6);
        return score;
    }

    static boolean isDumpHeavy(String body) {
        if (body.contains("【本地获取】") || body.contains("【本地獲取】")
                || body.toLowerCase(Locale.ROOT).contains("[local acquire]")) {
            return true;
        }
        int n = 0;
        int chrome = 0;
        for (String line : body.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            n++;
            if (isFactChromeLine(line)) {
                chrome++;
            }
        }
        return n > 0 && chrome * 2 >= n;
    }

    static String howToGetInsertHeading(String answer) {
        boolean han = hasHan(answer);
        String label = han ? "怎么来：" : "How to get:";
        Matcher as = AS_MATERIAL_HEAD.matcher(answer == null ? "" : answer);
        boolean numbered = as.find() && LINE_START_NUM.matcher(as.group()).find();
        if (!numbered) {
            Matcher first = LINE_START_NUM.matcher(answer == null ? "" : answer);
            numbered = first.find() && Integer.parseInt(first.group(1)) > 1;
        }
        return numbered ? "1. " + label : label;
    }

    static int insertHowToGetAt(String answer) {
        if (answer == null || answer.isEmpty()) {
            return 0;
        }
        Matcher as = AS_MATERIAL_HEAD.matcher(answer);
        if (as.find()) {
            return as.start();
        }
        Matcher title = ITEM_TITLE_LINE.matcher(answer);
        if (title.find()) {
            String before = answer.substring(0, title.start());
            if (before.isBlank()) {
                return title.end();
            }
        }
        return 0;
    }

    /**
     * Model wrote {@code 2. 作为材料} then {@code 3. 取得方式} — put obtain first so
     * {@link #fixOrphanLeadingList} can number 1=取得 2=材料.
     */
    static String reorderHowToGetBeforeMaterials(String answer) {
        if (answer == null || answer.isEmpty()) {
            return answer == null ? "" : answer;
        }
        Matcher mat = AS_MATERIAL_HEAD.matcher(answer);
        Matcher get = HOW_TO_GET_HEAD.matcher(answer);
        if (!mat.find() || !get.find()) {
            return answer;
        }
        if (get.start() < mat.start()) {
            return answer;
        }
        int matStart = mat.start();
        int getStart = get.start();
        int matEnd = sectionEnd(answer, matStart);
        int getEnd = sectionEnd(answer, getStart);
        if (matEnd > getStart) {
            matEnd = getStart;
        }
        String before = answer.substring(0, matStart);
        String matBlock = answer.substring(matStart, matEnd);
        String mid = answer.substring(matEnd, getStart);
        String getBlock = answer.substring(getStart, getEnd);
        String after = answer.substring(getEnd);
        return before + getBlock + mid + matBlock + after;
    }

    static int sectionEnd(String answer, int headingStart) {
        int next = answer.length();
        next = Math.min(next, findHeadingAfter(HOW_TO_GET_HEAD, answer, headingStart));
        next = Math.min(next, findHeadingAfter(AS_MATERIAL_HEAD, answer, headingStart));
        next = Math.min(next, findHeadingAfter(HOW_TO_USE_HEAD, answer, headingStart));
        return next;
    }

    private static int findHeadingAfter(Pattern p, String s, int from) {
        Matcher m = p.matcher(s);
        if (from + 1 < s.length() && m.find(from + 1)) {
            return m.start();
        }
        return Integer.MAX_VALUE;
    }

    static boolean hasHan(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        return s.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }

    /**
     * First visible step must not be {@code 2.} with no {@code 1.}.
     * Shifts a leading consecutive run (2. 3. …) down so it starts at 1.
     */
    static String fixOrphanLeadingList(String answer) {
        if (answer == null || answer.isEmpty()) {
            return answer == null ? "" : answer;
        }
        int i = 0;
        while (i < answer.length()) {
            int nl = answer.indexOf('\n', i);
            String line = nl < 0 ? answer.substring(i) : answer.substring(i, nl);
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("[[item:")) {
                i = nl < 0 ? answer.length() : nl + 1;
                continue;
            }
            Matcher num = LINE_START_NUM.matcher(line);
            if (!num.find()) {
                return answer;
            }
            int startN = Integer.parseInt(num.group(1));
            if (startN <= 1) {
                return answer;
            }
            return shiftLeadingList(answer, startN);
        }
        return answer;
    }

    static String shiftLeadingList(String answer, int startN) {
        int delta = startN - 1;
        Matcher m = LINE_START_NUM.matcher(answer);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        int expect = startN;
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n != expect) {
                break;
            }
            sb.append(answer, last, m.start());
            String g = m.group();
            String ns = String.valueOf(n);
            int digitAt = g.indexOf(ns);
            sb.append(g, 0, digitAt).append(n - delta).append(g.substring(digitAt + ns.length()));
            last = m.end();
            expect++;
        }
        sb.append(answer, last, answer.length());
        return sb.toString();
    }

    /** Strip DSML / {@code <tool_call>} dumps. Leaves {@code [[item:]]} / {@code [[recipe:]]}. */
    public static String scrubLeakedToolXml(String answer) {
        if (answer == null || answer.isEmpty()) {
            return "";
        }
        String t = DSML_TOOL_CALLS_BLOCK.matcher(answer).replaceAll("");
        t = DSML_INVOKE_BLOCK.matcher(t).replaceAll("");
        t = GENERIC_TOOL_XML.matcher(t).replaceAll("");
        t = LEFTOVER_TOOL_TOKEN.matcher(t).replaceAll("");
        return t;
    }

    /**
     * True when the player would see no prose: blank, or only recipe-card markers
     * (UI cards are not an answer). {@code [[item:]]} / {@code [[recipe:]]} count as visible.
     */
    public static boolean isVisiblyEmpty(String answer) {
        if (answer == null || answer.isBlank()) {
            return true;
        }
        String t = CARD_ONLY_MARKERS.matcher(answer).replaceAll("");
        return t.isBlank();
    }

    /**
     * Display body: scrubbed LLM prose, or joined FACT lines when the model dumped
     * tool XML / card markers and nothing else.
     */
    public static String proseOrFacts(String llmAnswer, List<String> facts) {
        return proseOrFacts(llmAnswer, facts, "");
    }

    /**
     * Display body: scrubbed LLM prose, or joined FACT lines when the model dumped
     * tool XML / card markers and nothing else. {@code fallback} if those are empty too.
     */
    public static String proseOrFacts(String llmAnswer, List<String> facts, String fallback) {
        String scrubbed = scrubPromptEcho(llmAnswer);
        if (!isVisiblyEmpty(scrubbed)) {
            return scrubbed;
        }
        if (facts != null && !facts.isEmpty()) {
            String joined = scrubPromptEcho(String.join("\n\n", facts));
            if (!isVisiblyEmpty(joined)) {
                return joined;
            }
        }
        return fallback == null ? "" : fallback;
    }

    /**
     * Drop Pack AI GUI overlay / keybind chrome from captured item tooltips
     * before they enter {@code [PURPOSE]}. Keeps real lore, stats, mod use text.
     * null returns empty string; blank input returned unchanged.
     */
    public static String scrubPackAiTooltipChrome(String tooltip) {
        if (tooltip == null || tooltip.isBlank()) {
            return tooltip == null ? "" : tooltip;
        }
        String[] lines = tooltip.split("\\R", -1);
        List<String> keep = new ArrayList<>(lines.length);
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || isPackAiTooltipChromeLine(line)) {
                continue;
            }
            keep.add(line);
        }
        return String.join("\n", keep);
    }

    static boolean isPackAiTooltipChromeLine(String line) {
        if (line.length() >= 2) {
            boolean allBars = true;
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) != '|') {
                    allBars = false;
                    break;
                }
            }
            if (allBars) {
                return true;
            }
        }
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.contains("packai.screen.") || lower.contains("packai.tooltip.")) {
            return true;
        }
        if (lower.contains("ask pack ai") || lower.contains("clears multi-select")) {
            return true;
        }
        if (line.contains("单独询问") || line.contains("單獨詢問")
                || line.contains("清除多选") || line.contains("清除多選")
                || line.contains("来用 Pack AI") || line.contains("來用 Pack AI")) {
            return true;
        }
        if (line.contains("AI 正在思考") || lower.contains("ai is thinking")) {
            return true;
        }
        return false;
    }
}
