package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.skps9.packai.PackAiMod;

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
     * Prompt/fact section names — never player-facing.
     * Single source for {@link #PROMPT_SECTION_TAG} and {@link #BARE_INTERNAL_SECTION}.
     */
    public static final List<String> INTERNAL_SECTION_TOKENS = List.of(
            "PURPOSE",
            "GUIDE",
            "VARIANT",
            "AS_INGREDIENT",
            "CONTAINED",
            "CONSUME_USE",
            "TOOL_BUILD",
            "TETRA_USE",
            "WORLDGEN");

    /** Extra bare names compiled into both patterns with {@link #INTERNAL_SECTION_TOKENS}. */
    private static final List<String> EXTRA_BARE_SECTION_TOKENS = List.of("RECIPE_CARDS");

    /**
     * {@code role=} primary values (hyphen or underscore suffix is a modifier,
     * e.g. {@code quest-as-obtain}, {@code quest_task}).
     * Footer maps these to {@code packai.label.role.*}.
     */
    public static final List<String> INTERNAL_ROLE_VALUES = List.of(
            "output", "input", "quest", "uses", "upgrade", "maintenance");

    /** {@code SCROLL_EFFECT}, {@code SCROLL_MATERIAL}, … */
    private static final String SCROLL_SECTION_REGEX = "SCROLL_[A-Z0-9_]+";

    /**
     * PURPOSE / fact headers injected into prompts — never player-facing.
     * Matches {@code [SCROLL_EFFECT]}, {@code [PURPOSE]}, etc. (optional spaces).
     */
    private static final Pattern PROMPT_SECTION_TAG = Pattern.compile(
            "\\[\\s*(?:" + internalSectionAlternation() + ")\\s*\\]",
            Pattern.CASE_INSENSITIVE);

    /**
     * Lone How-to-get header with no obtain prose before the next section.
     * Optional {@code 1.} prefix — models number 怎么来 then skip the empty body.
     * INPUT as-ingredient cards live in other parts — they do not fill this header.
     */
    private static final String HOW_TO_GET_LABEL =
            "(?:怎么来|怎么來|怎麼来|怎麼來|怎样来|怎樣來|How to get|取得方式|获取方式|獲取方式|取得方法|How to obtain)";

    /** Optional {@code ##} / {@code 1.} / wrapping {@code 【】} or {@code []}. */
    private static final String HOW_TO_GET_HEAD_PREFIX =
            "(?:#{1,3}[ \\t]*)?(?:\\d+[.)][ \\t]*)?(?:[【\\[])?" + HOW_TO_GET_LABEL + "(?:[】\\]])?";

    private static final Pattern EMPTY_HOW_TO_GET = Pattern.compile(
            "(?im)^[ \\t]*" + HOW_TO_GET_HEAD_PREFIX
                    + "[ \\t]*[:：]?[ \\t]*\\r?\\n(?:[ \\t]*\\r?\\n)*"
                    + "(?=[ \\t]*(?:#{1,3}[ \\t]*)?(?:\\d+[.)][ \\t]*)?(?:怎么用|怎麼用|怎样用|怎樣用|How to use|作为材料|作為材料)"
                    + "|【来源】|【來源】|\\[Sources\\]|\\z)");

    private static final Pattern HOW_TO_GET_HEAD = Pattern.compile(
            "(?im)^[ \\t]*" + HOW_TO_GET_HEAD_PREFIX + "(?:[:：]|\\s|\\z)");

    private static final Pattern AS_MATERIAL_HEAD = Pattern.compile(
            "(?im)^[ \\t]*(?:##[ \\t]*)?(?:\\d+[.)][ \\t]*)?(?:作为材料|作為材料)");

    private static final Pattern HOW_TO_USE_HEAD = Pattern.compile(
            "(?im)^[ \\t]*(?:##[ \\t]*)?(?:\\d+[.)][ \\t]*)?(?:怎么用|怎麼用|怎样用|怎樣用|用途|How to use)(?:[:：]|\\s|\\z)");

    /** Upgrade / 強化 — same heading tier as GET/USE (R5). */
    private static final Pattern HOW_TO_UPGRADE_HEAD = Pattern.compile(
            "(?im)^[ \\t]*(?:##[ \\t]*)?(?:\\d+[.)][ \\t]*)?(?:强化|強化|升级|升級|Upgrade|How to upgrade)(?:[:：]|\\s|\\z)");

    private static final Pattern ITEM_TITLE_LINE = Pattern.compile("(?m)^\\[\\[item:[^\\]]+]][^\\n]*\\n");

    /** Line-start step number only — not "魔源消耗 9999". */
    private static final Pattern LINE_START_NUM = Pattern.compile("(?m)^[ \\t]*(\\d+)[.)][ \\t]+");

    /** ASCII |, fullwidth \uFF5C, broken bar \u00A6, box-drawing \u2502. */
    private static final String DSML_PIPE = "[\\|\\uFF5C\\u00A6\\u2502]";

    /** One pipe OR a run of pipes — the model emits both single and DOUBLED fullwidth U+FF5C. */
    private static final String DSML_PIPE_RUN = "[\\|\\uFF5C\\u00A6\\u2502]{1,4}";

    /**
     * DeepSeek DSML tool-call dump ({@code <|DSML|>} or spaced {@code < | DSML | | tool_calls>}).
     * Also fullwidth pipe {@code \uFF5C}. Inner parameter values go away with the block.
     */
    private static final Pattern DSML_TOOL_CALLS_BLOCK = Pattern.compile(
            "(?is)<\\s*" + DSML_PIPE_RUN + "\\s*DSML\\s*" + DSML_PIPE_RUN
                    + "\\s*(?:>\\s*)?(?:" + DSML_PIPE_RUN + "\\s*)?(?:tool_)?calls?\\s*>"
                    + ".*?"
                    + "</\\s*" + DSML_PIPE_RUN + "\\s*DSML\\s*" + DSML_PIPE_RUN
                    + "\\s*(?:>\\s*)?(?:" + DSML_PIPE_RUN + "\\s*)?(?:tool_)?calls?\\s*>");

    private static final Pattern DSML_INVOKE_BLOCK = Pattern.compile(
            "(?is)<\\s*" + DSML_PIPE_RUN + "\\s*DSML\\s*" + DSML_PIPE_RUN
                    + "\\s*(?:>\\s*)?(?:" + DSML_PIPE_RUN + "\\s*)?invoke\\b[^>]*>"
                    + ".*?"
                    + "</\\s*" + DSML_PIPE_RUN + "\\s*DSML\\s*" + DSML_PIPE_RUN
                    + "\\s*(?:>\\s*)?(?:" + DSML_PIPE_RUN + "\\s*)?invoke\\s*>");

    private static final Pattern GENERIC_TOOL_XML = Pattern.compile(
            "(?is)<\\s*tool_calls?\\b[^>]*>.*?</\\s*tool_calls?\\s*>"
                    + "|<\\s*function_calls?\\b[^>]*>.*?</\\s*function_calls?\\s*>"
                    + "|<" + DSML_PIPE_RUN + "tool_call_begin" + DSML_PIPE_RUN + ">.*?<"
                    + DSML_PIPE_RUN + "tool_call_end" + DSML_PIPE_RUN + ">"
                    + "|<" + DSML_PIPE_RUN + "tool_calls_section_begin" + DSML_PIPE_RUN + ">.*?<"
                    + DSML_PIPE_RUN + "tool_calls_section_end" + DSML_PIPE_RUN + ">");

    private static final Pattern LEFTOVER_TOOL_TOKEN = Pattern.compile(
            "(?i)</?[^<>]*DSML[^<>]*>"
                    + "|</?\\s*" + DSML_PIPE_RUN + "\\s*DSML\\s*" + DSML_PIPE_RUN + "[^>]*>"
                    + "|</?" + DSML_PIPE_RUN + "DSML" + DSML_PIPE_RUN + ">"
                    + "|<" + DSML_PIPE_RUN + "tool_call(?:s)?_(?:begin|end)" + DSML_PIPE_RUN + ">"
                    + "|<" + DSML_PIPE_RUN + "tool_calls_section_(?:begin|end)" + DSML_PIPE_RUN + ">"
                    + "|</?\\s*invoke\\b[^>]*>"
                    + "|</?\\s*parameter\\b[^>]*>"
                    + "|</?\\s*(?:tool_)?calls?\\b[^>]*>"
                    + "|</?\\s*function_calls?\\b[^>]*>");

    /**
     * {@code [[tools]]} + JSON object (AskToolLoop hop / budget-exhausted leak).
     * Start match only — body removed via brace balance (nested {@code calls}/{@code args}).
     */
    private static final Pattern TOOLS_JSON_START = Pattern.compile("(?i)\\[\\[tools\\]\\]\\s*\\{");

    private static final Pattern CARD_ONLY_MARKERS = Pattern.compile(
            "\\[\\[recipe_card:\\d+]]|\\{\\{RECIPE}}");

    private static final String[] PLAYER_UNSAFE_MARKERS = {
            "render_recipe_cards",
            "[RECIPE_CARDS]",
            "【JEI",
            "注意：JEI",
            "已完整扫描",
            "已完整掃描",
            "推荐合成",
            "推荐取得",
            "role=",
            "必须",
            "禁止",
            "不要用",
            "请明说",
            "DSML",
            "<invoke",
            "<tool_calls",
            "[[tools]]{"
    };

    /**
     * Catalog/tool field echo ({@code role=output／input}). Must not reach the player,
     * including the 【來源】 line. {@code PLAYER_UNSAFE_MARKERS} only filters FACT fallback.
     * Case semantics: {@code (?i)} so {@code Role=} matches. Half- or full-width equals.
     * Values {@code [A-Za-z0-9_-]+}; extra values via {@code 、／,|｜/} or whitespace,
     * but a following {@code role=} starts a new assignment.
     */
    private static final Pattern ROLE_EQ_TOKEN = Pattern.compile(
            "(?i)\\brole\\s*[=＝]\\s*(?:[A-Za-z0-9_\\-]+(?:(?:\\s*[、／,|｜/]\\s*|\\s+)(?!role\\b)[A-Za-z0-9_\\-]+)*)?");

    private static final Pattern ROLE_EQ_START = Pattern.compile("(?i)role\\s*[=＝]");

    private static final Pattern ROLE_VALUE_SEP = Pattern.compile("[、／,|｜/\\s]+");

    /**
     * Bare internal section names with required colon. Bracket tags are a separate strip.
     * {@code PURPOSE IS CLEAR:} does not match (colon not on the token).
     * Bare {@code GUIDE} in {@code in-game GUIDE} does not match.
     */
    private static final Pattern BARE_INTERNAL_SECTION = Pattern.compile(
            "(?<![A-Za-z])(?:" + internalSectionAlternation() + ")(?![A-Za-z])\\s*[:：]\\s*");

    /**
     * Footer-gate sibling: same colon requirement as {@link #BARE_INTERNAL_SECTION}.
     */
    private static final Pattern BARE_INTERNAL_SECTION_COLON = Pattern.compile(
            "(?<![A-Za-z])(?:" + internalSectionAlternation() + ")(?![A-Za-z])\\s*[:：]");

    /** {@code [PURPOSE]} */
    private static final Pattern TAG_SQUARE = Pattern.compile(
            "\\[\\s*(" + internalSectionAlternation() + ")\\s*\\]",
            Pattern.CASE_INSENSITIVE);

    /** {@code 【PURPOSE】} */
    private static final Pattern TAG_CJK = Pattern.compile(
            "【\\s*(" + internalSectionAlternation() + ")\\s*】",
            Pattern.CASE_INSENSITIVE);

    /** {@code （PURPOSE）} */
    private static final Pattern TAG_FW_PAREN = Pattern.compile(
            "（\\s*(" + internalSectionAlternation() + ")\\s*）",
            Pattern.CASE_INSENSITIVE);

    /** {@code (PURPOSE)} */
    private static final Pattern TAG_PAREN = Pattern.compile(
            "\\(\\s*(" + internalSectionAlternation() + ")\\s*\\)",
            Pattern.CASE_INSENSITIVE);

    /** Token + required colon. */
    private static final Pattern TRANSLATE_COLON_TOKEN = Pattern.compile(
            "(?<![A-Za-z])(" + internalSectionAlternation() + ")(?![A-Za-z])(\\s*[:：])");

    /**
     * Empty bracket pairs after token strip. Spaces/tabs/ideographic space only — no newline
     * (must not join lines). {@code [[item:]]} / {@code {{item:}}} keep inner content so they
     * do not match.
     */
    private static final Pattern EMPTY_BRACKETS = Pattern.compile(
            "[（(][ \\t\\u3000]*[）)]"
                    + "|\\[[ \\t\\u3000]*\\]"
                    + "|【[ \\t\\u3000]*】"
                    + "|\\{[ \\t\\u3000]*\\}");

    /**
     * Machine suggestion marker — same shape as {@link ItemResolver} MARKER (intact + damaged).
     * Kept local so scrub does not class-load ItemResolver / Minecraft registry.
     */
    private static final Pattern PACKAI_ITEMS_MARKER = Pattern.compile(
            "<!-{1,2}\\s*packai:items=([^>]+?)\\s*-{1,2}>", Pattern.CASE_INSENSITIVE);

    /**
     * Duplicate leftover separators (optional space between copies).
     * ASCII {@code /} omitted — collapsing {@code //} would break {@code https://}.
     * ASCII {@code -} omitted — collapsing {@code --} broke {@code <!--packai:items=…-->}.
     */
    private static final Pattern DUP_SEPARATORS = Pattern.compile(
            "([、，,／|;；·:：])(?:[ \\t\\u3000]*\\1)+");

    /**
     * Line-start orphan seps. Not {@code -} (markdown lists), {@code :} ({@code ns:path} /
     * headings), or ASCII {@code /} ({@code /give}).
     */
    private static final Pattern LEADING_ORPHAN_SEP = Pattern.compile("(?m)^[ \\t]*[、，,／|;；·]+");

    /** Line-end orphan seps. Same exclusions as {@link #LEADING_ORPHAN_SEP}. */
    private static final Pattern TRAILING_ORPHAN_SEP = Pattern.compile("(?m)[、，,／|;；·]+[ \\t]*$");

    /** Open paren left at EOL after strip (space-only tail). */
    private static final Pattern HALF_ORPHAN_OPEN = Pattern.compile("(?m)[（(][ \\t\\u3000]*$");

    /** Close paren left at BOL after strip. */
    private static final Pattern HALF_ORPHAN_CLOSE = Pattern.compile("(?m)^[ \\t\\u3000]*[）)]");

    private static final Pattern SPACE_BEFORE_CLOSE = Pattern.compile("[ \\t]+([、，,）)])");

    private static final Pattern SPACE_AFTER_OPEN = Pattern.compile("([（(])[ \\t]+");

    private static final Pattern MULTISPACE = Pattern.compile("[ \\t]{2,}");

    private static final Pattern TRAILING_SPACE = Pattern.compile("(?m)[ \\t]+$");

    /**
     * Pure section-title line: optional {@code 1.} prefix, known label, then optional
     * whitespace / single colon / whitespace / EOL only. Prose like {@code 如果不知道怎么来…}
     * or {@code Usage in combat is limited to tools.} does not match (non-whitespace after the label).
     */
    private static final Pattern PURE_SECTION_HEADER = Pattern.compile(
            "^[ \\t]*(?:\\d+[.)][ \\t]*)?"
                    + "(怎么来|怎样来|怎么來|怎樣來|怎麼来|怎麼來|怎么用|怎麼用|怎样用|怎樣用|用途|作为材料|作為材料|强化|強化|升级|升級|How to get|How to use|How to upgrade|Upgrade|Usage)"
                    + "[ \\t]*[:：]?[ \\t]*$",
            Pattern.CASE_INSENSITIVE);

    private AskReplyScrub() {}

    private static String internalSectionAlternation() {
        List<String> toks = new ArrayList<>(INTERNAL_SECTION_TOKENS.size() + EXTRA_BARE_SECTION_TOKENS.size());
        toks.addAll(INTERNAL_SECTION_TOKENS);
        toks.addAll(EXTRA_BARE_SECTION_TOKENS);
        toks.sort((a, b) -> Integer.compare(b.length(), a.length()));
        StringBuilder sb = new StringBuilder(SCROLL_SECTION_REGEX);
        for (String t : toks) {
            sb.append('|').append(Pattern.quote(t));
        }
        return sb.toString();
    }

    /**
     * Footer/source-line leak detector: {@code role=}/{@code role＝}, {@code [TOKEN]},
     * {@code 【TOKEN】}, {@code （TOKEN）}/{@code (TOKEN)}, bare token+colon,
     * {@code render_recipe_cards}. Bare {@code GUIDE} in {@code in-game GUIDE} is not a leak.
     */
    public static boolean hasInternalSourceLeak(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (ROLE_EQ_TOKEN.matcher(text).find()) {
            return true;
        }
        if (text.contains("render_recipe_cards")) {
            return true;
        }
        if (PROMPT_SECTION_TAG.matcher(text).find()) {
            return true;
        }
        if (TAG_CJK.matcher(text).find()) {
            return true;
        }
        if (TAG_FW_PAREN.matcher(text).find() || TAG_PAREN.matcher(text).find()) {
            return true;
        }
        return BARE_INTERNAL_SECTION_COLON.matcher(text).find();
    }

    /**
     * Footer-only: structural list render (split → per-item tokens → rejoin).
     * Missing lang key → strip that piece (fail-closed) + one debug log per distinct token.
     * Body paths must keep stripping, not call this.
     */
    public static String translateInternalTokens(String footer, String replyLang) {
        return renderSourcesFooter(footer, replyLang);
    }

    /**
     * Split the 【來源】/【Sources】 body on list seps (not inside brackets/{@code {}}),
     * translate each item, collapse brackets/label dupes, drop junk, dedupe, locale-join.
     */
    static String renderSourcesFooter(String footer, String replyLang) {
        if (footer == null || footer.isEmpty()) {
            return footer == null ? "" : footer;
        }
        Matcher hm = ReplySources.HEADER.matcher(footer);
        String header;
        String body;
        if (hm.find() && hm.start() == 0) {
            int end = hm.end();
            while (end < footer.length()) {
                char c = footer.charAt(end);
                if (c == ' ' || c == '\t') {
                    end++;
                } else {
                    break;
                }
            }
            header = footer.substring(0, end);
            body = footer.substring(end);
        } else {
            header = "";
            body = footer;
        }
        List<String> rawItems = splitTopLevel(body);
        Set<String> logged = new HashSet<>();
        List<String> out = new ArrayList<>();
        boolean mutated = false;
        for (String raw : rawItems) {
            String it = raw.trim();
            if (it.isEmpty()) {
                mutated = true;
                continue;
            }
            String next = replaceInternalTokens(it, replyLang, logged);
            next = normaliseBrackets(next, replyLang);
            next = collapseLabelDuplication(next, replyLang);
            next = stripReplyDebris(next).trim();
            if (!next.equals(it)) {
                mutated = true;
            }
            if (next.isEmpty() || isPureJunk(next)) {
                mutated = true;
                continue;
            }
            out.add(next);
        }
        List<String> deduped = dedupePreserveOrder(out);
        if (deduped.size() != out.size()) {
            mutated = true;
        }
        if (deduped.isEmpty()) {
            return "";
        }
        if (!mutated) {
            return footer;
        }
        String sep = ReplyLang.sourceJoin(replyLang);
        if (sep == null || sep.isEmpty()) {
            sep = "en_us".equals(ReplyLang.bundleLang(replyLang)) ? ", " : "、";
        }
        return header + String.join(sep, deduped);
    }

    /**
     * Top-level split on {@code 、,／/｜|;；・} / newlines, plus whitespace immediately
     * before {@code role=}. Does not split inside {@code ()（）[]【】{}}.
     */
    static List<String> splitTopLevel(String text) {
        List<String> items = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return items;
        }
        StringBuilder cur = new StringBuilder();
        int round = 0;
        int square = 0;
        int cjk = 0;
        int curly = 0;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            boolean nested = round > 0 || square > 0 || cjk > 0 || curly > 0;
            if (!nested && isListPunct(c)) {
                flushItem(items, cur);
                i = skipSepRun(text, i);
                continue;
            }
            if (!nested && isHorizWs(c)) {
                int j = i;
                while (j < n && isHorizWs(text.charAt(j))) {
                    j++;
                }
                if (j < n && startsRoleEq(text, j)) {
                    flushItem(items, cur);
                    i = j;
                    continue;
                }
            }
            if (c == '(' || c == '（') {
                round++;
            } else if ((c == ')' || c == '）') && round > 0) {
                round--;
            } else if (c == '[') {
                square++;
            } else if (c == ']' && square > 0) {
                square--;
            } else if (c == '【') {
                cjk++;
            } else if (c == '】' && cjk > 0) {
                cjk--;
            } else if (c == '{') {
                curly++;
            } else if (c == '}' && curly > 0) {
                curly--;
            }
            cur.append(c);
            i++;
        }
        flushItem(items, cur);
        return items;
    }

    private static boolean isListPunct(char c) {
        return c == '、' || c == ',' || c == '／' || c == '/'
                || c == '｜' || c == '|' || c == '；' || c == ';' || c == '・'
                || c == '\n' || c == '\r';
    }

    private static boolean isHorizWs(char c) {
        return c == ' ' || c == '\t' || c == '\u3000';
    }

    private static boolean startsRoleEq(String text, int i) {
        Matcher m = ROLE_EQ_START.matcher(text);
        return m.find(i) && m.start() == i;
    }

    private static int skipSepRun(String text, int i) {
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (isListPunct(c) || isHorizWs(c)) {
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    private static void flushItem(List<String> items, StringBuilder cur) {
        if (cur.length() > 0) {
            items.add(cur.toString());
            cur.setLength(0);
        }
    }

    private static String replaceInternalTokens(String text, String replyLang, Set<String> logged) {
        String t = replaceRoleEq(text, replyLang, logged);
        t = replaceTaggedTokens(t, replyLang, logged);
        t = replaceColonTokens(t, replyLang, logged);
        t = replaceExactItemToken(t, replyLang, logged);
        return t;
    }

    private static String replaceRoleEq(String text, String replyLang, Set<String> logged) {
        Matcher m = ROLE_EQ_TOKEN.matcher(text);
        StringBuilder sb = new StringBuilder();
        String join = ReplyLang.sourceJoin(replyLang);
        if (join == null || join.isEmpty()) {
            join = "、";
        }
        while (m.find()) {
            String raw = m.group();
            int eq = indexOfRoleEq(raw);
            String rest = eq < 0 ? "" : raw.substring(eq + 1).trim();
            List<String> labels = new ArrayList<>();
            if (!rest.isEmpty()) {
                for (String part : ROLE_VALUE_SEP.split(rest)) {
                    if (part.isEmpty()) {
                        continue;
                    }
                    String lab = roleLabel(part, replyLang, logged);
                    if (lab != null) {
                        labels.add(lab);
                    }
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(String.join(join, labels)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static int indexOfRoleEq(String raw) {
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '=' || c == '＝') {
                return i;
            }
        }
        return -1;
    }

    private static String replaceTaggedTokens(String text, String replyLang, Set<String> logged) {
        String t = text;
        for (int i = 0; i < 8; i++) {
            String prev = t;
            t = replaceTagPattern(TAG_SQUARE, t, replyLang, logged);
            t = replaceTagPattern(TAG_CJK, t, replyLang, logged);
            t = replaceTagPattern(TAG_FW_PAREN, t, replyLang, logged);
            t = replaceTagPattern(TAG_PAREN, t, replyLang, logged);
            if (t.equals(prev)) {
                break;
            }
        }
        return t;
    }

    private static String replaceTagPattern(Pattern p, String text, String replyLang, Set<String> logged) {
        Matcher m = p.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String label = srcLabel(m.group(1), replyLang, logged);
            String repl = label == null ? "" : wrapSrcLabel(label, replyLang);
            m.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Square / CJK / paren tags → locale parens. */
    private static String wrapSrcLabel(String label, String replyLang) {
        return "en_us".equals(ReplyLang.bundleLang(replyLang)) ? "(" + label + ")" : "（" + label + "）";
    }

    private static String replaceColonTokens(String text, String replyLang, Set<String> logged) {
        Matcher m = TRANSLATE_COLON_TOKEN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String label = srcLabel(m.group(1), replyLang, logged);
            String repl = label == null ? "" : label + m.group(2);
            m.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Whole-item leftover: a lone section token or role value. */
    private static String replaceExactItemToken(String text, String replyLang, Set<String> logged) {
        String t = text.trim();
        if (t.isEmpty()) {
            return text;
        }
        if (isSectionTokenName(t)) {
            String lab = srcLabel(t, replyLang, logged);
            return lab == null ? "" : lab;
        }
        if (looksLikeRoleValue(t)) {
            String lab = roleLabel(t, replyLang, logged);
            return lab == null ? "" : lab;
        }
        return text;
    }

    private static boolean isSectionTokenName(String t) {
        String u = t.trim().toUpperCase(Locale.ROOT);
        if (u.startsWith("SCROLL_") && u.length() > 7) {
            return true;
        }
        for (String tok : INTERNAL_SECTION_TOKENS) {
            if (tok.equals(u)) {
                return true;
            }
        }
        for (String tok : EXTRA_BARE_SECTION_TOKENS) {
            if (tok.equals(u)) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeRoleValue(String t) {
        if (t == null || t.isEmpty()) {
            return false;
        }
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (!(c >= 'A' && c <= 'Z') && !(c >= 'a' && c <= 'z')
                    && !(c >= '0' && c <= '9') && c != '_' && c != '-') {
                return false;
            }
        }
        String v = t.toLowerCase(Locale.ROOT);
        return INTERNAL_ROLE_VALUES.contains(rolePrimary(v));
    }

    private static String rolePrimary(String v) {
        int cut = -1;
        int dash = v.indexOf('-');
        int us = v.indexOf('_');
        if (dash >= 0) {
            cut = dash;
        }
        if (us >= 0 && (cut < 0 || us < cut)) {
            cut = us;
        }
        return cut < 0 ? v : v.substring(0, cut);
    }

    static String normaliseBrackets(String text, String replyLang) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String t = text.trim();
        if (t.startsWith("[[") || t.startsWith("{{")) {
            return t;
        }
        for (int i = 0; i < 8; i++) {
            String prev = t;
            t = peelOnce(t, replyLang);
            if (t.equals(prev)) {
                break;
            }
        }
        if (isKnownSrcLabel(t, replyLang)) {
            return wrapSrcLabel(t, replyLang);
        }
        return t;
    }

    private static String peelOnce(String t, String replyLang) {
        if (t.startsWith("[[") || t.startsWith("{{")) {
            return t;
        }
        String inner = fullyWrappedInner(t);
        if (inner == null) {
            return t;
        }
        String trimmed = inner.trim();
        if (fullyWrappedInner(trimmed) != null) {
            return trimmed;
        }
        if (isKnownSrcLabel(trimmed, replyLang)) {
            return wrapSrcLabel(trimmed, replyLang);
        }
        return t;
    }

    /** Inner text if {@code t} is one matched wrapper pair covering the whole string. */
    private static String fullyWrappedInner(String t) {
        if (t == null || t.length() < 2) {
            return null;
        }
        char o = t.charAt(0);
        char c;
        if (o == '（') {
            c = '）';
        } else if (o == '(') {
            c = ')';
        } else if (o == '[') {
            c = ']';
        } else if (o == '【') {
            c = '】';
        } else {
            return null;
        }
        if (t.charAt(t.length() - 1) != c) {
            return null;
        }
        int d = 0;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == o) {
                d++;
            } else if (ch == c) {
                d--;
                if (d == 0 && i < t.length() - 1) {
                    return null;
                }
            }
        }
        return d == 0 ? t.substring(1, t.length() - 1) : null;
    }

    private static boolean isKnownSrcLabel(String text, String replyLang) {
        String s = text == null ? "" : text.trim();
        if (s.isEmpty()) {
            return false;
        }
        for (String tok : INTERNAL_SECTION_TOKENS) {
            String lab = ReplyLang.lookupLabel(replyLang, srcLabelKey(tok));
            if (s.equals(lab)) {
                return true;
            }
        }
        for (String tok : EXTRA_BARE_SECTION_TOKENS) {
            String lab = ReplyLang.lookupLabel(replyLang, srcLabelKey(tok));
            if (s.equals(lab)) {
                return true;
            }
        }
        String scroll = ReplyLang.lookupLabel(replyLang, "packai.label.src.scroll");
        return scroll != null && s.equals(scroll);
    }

    static String collapseLabelDuplication(String text, String replyLang) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String t = text;
        List<String> labels = knownLabelsLongestFirst(replyLang);
        for (String L : labels) {
            t = t.replace(L + "（" + L + "：", L + "（");
            t = t.replace(L + "（" + L + ":", L + "（");
            t = t.replace(L + "(" + L + "：", L + "(");
            t = t.replace(L + "(" + L + ":", L + "(");
            t = t.replace(L + "（" + L + "）", L);
            t = t.replace(L + "(" + L + ")", L);
        }
        return t;
    }

    private static List<String> knownLabelsLongestFirst(String replyLang) {
        List<String> labels = new ArrayList<>();
        for (String tok : INTERNAL_SECTION_TOKENS) {
            addLabel(labels, ReplyLang.lookupLabel(replyLang, srcLabelKey(tok)));
        }
        for (String tok : EXTRA_BARE_SECTION_TOKENS) {
            addLabel(labels, ReplyLang.lookupLabel(replyLang, srcLabelKey(tok)));
        }
        addLabel(labels, ReplyLang.lookupLabel(replyLang, "packai.label.src.scroll"));
        for (String r : INTERNAL_ROLE_VALUES) {
            addLabel(labels, ReplyLang.lookupLabel(replyLang, "packai.label.role." + r));
        }
        labels.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return labels;
    }

    private static void addLabel(List<String> labels, String lab) {
        if (lab != null && !lab.isEmpty() && !labels.contains(lab)) {
            labels.add(lab);
        }
    }

    private static boolean isPureJunk(String s) {
        if (s == null) {
            return true;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return true;
        }
        String u = t.replaceAll("(?i)role\\s*[=＝]\\s*", "");
        u = u.replaceAll("[=＝｜|,;；、，／/·・\\s]+", "");
        return u.isEmpty();
    }

    private static List<String> dedupePreserveOrder(List<String> items) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String it : items) {
            if (set.add(it)) {
                out.add(it);
            }
        }
        return out;
    }

    static String srcLabelKey(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }
        String u = token.trim().toUpperCase(Locale.ROOT);
        if (u.startsWith("SCROLL_")) {
            return "packai.label.src.scroll";
        }
        return "packai.label.src." + u.toLowerCase(Locale.ROOT);
    }

    private static String srcLabel(String token, String replyLang, Set<String> logged) {
        String key = srcLabelKey(token);
        if (key.isEmpty()) {
            return null;
        }
        String hit = ReplyLang.lookupLabel(replyLang, key);
        if (hit == null) {
            logUnlabeled(token, logged);
            return null;
        }
        return hit;
    }

    private static String roleLabel(String raw, String replyLang, Set<String> logged) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        String primary = rolePrimary(v);
        if (!INTERNAL_ROLE_VALUES.contains(primary)) {
            logUnlabeled("role=" + raw.trim(), logged);
            return null;
        }
        String hit = ReplyLang.lookupLabel(replyLang, "packai.label.role." + primary);
        if (hit == null) {
            logUnlabeled("role=" + primary, logged);
            return null;
        }
        return hit;
    }

    private static void logUnlabeled(String token, Set<String> logged) {
        if (token == null || logged == null || !logged.add(token)) {
            return;
        }
        PackAiMod.LOGGER.debug("Pack AI unlabeled source token stripped: {}", token);
    }

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
        if ("怎么用".equals(t) || "怎麼用".equals(t) || "怎样用".equals(t) || "怎樣用".equals(t)
                || "how to use".equals(lower) || "usage".equals(lower)) {
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

    /**
     * Remove leaked prompt section tags and model tool-call XML (DSML / tool_call).
     * Safe to run before {@link RecipeEmbed}
     * (does not touch recipe/item UI markers). Does not trim — callers tidy whitespace.
     */
    public static String scrubPromptEcho(String answer) {
        if (answer == null || answer.isEmpty()) {
            return "";
        }
        String t = unescapeLiteralNewlines(answer);
        // L1: strip machine marker before debris collapse (DUP used to eat <!-- --> dashes).
        t = PACKAI_ITEMS_MARKER.matcher(t).replaceAll("");
        t = scrubLeakedToolXml(t);
        int footerAt = -1;
        Matcher src = ReplySources.HEADER.matcher(t);
        if (src.find()) {
            footerAt = src.start();
        }
        String body = footerAt >= 0 ? t.substring(0, footerAt) : t;
        String footer = footerAt >= 0 ? t.substring(footerAt) : "";
        body = PROMPT_SECTION_TAG.matcher(body).replaceAll("");
        body = stripFactChrome(body);
        body = scrubInternalFieldEcho(body);
        t = body + footer;
        t = tidyNewlines(t);
        if (leftoverToolMarkup(t)) {
            return "";
        }
        return t;
    }

    /**
     * Fail-closed: leftover DSML / {@code <invoke} / {@code <tool_calls} after strip.
     * No wide {@code [A-Z_]{3,}} bracket-tag regex — that would kill {@code [ Shift ]} tooltips.
     */
    static boolean leftoverToolMarkup(String t) {
        if (t == null || t.isEmpty()) {
            return false;
        }
        return t.contains("DSML") || t.contains("<invoke") || t.contains("<tool_calls");
    }

    /**
     * Replace how-to-get section body with {@code fill} (heading kept).
     * Fail-open: empty inputs or missing heading → return answer unchanged.
     * End bound = earliest of anchored use/upgrade/as-material heads or sources header.
     */
    public static String replaceHowToGetBody(String answer, String fill) {
        if (answer == null || answer.isEmpty() || fill == null || fill.isEmpty()) {
            return answer == null ? "" : answer;
        }
        Matcher head = HOW_TO_GET_HEAD.matcher(answer);
        if (!head.find()) {
            return answer;
        }
        int bodyStart = head.end();
        int bodyEnd = answer.length();
        bodyEnd = Math.min(bodyEnd, findAnchoredStart(HOW_TO_USE_HEAD, answer, bodyStart));
        bodyEnd = Math.min(bodyEnd, findAnchoredStart(HOW_TO_UPGRADE_HEAD, answer, bodyStart));
        bodyEnd = Math.min(bodyEnd, findAnchoredStart(AS_MATERIAL_HEAD, answer, bodyStart));
        bodyEnd = Math.min(bodyEnd, findAnchoredStart(ReplySources.HEADER, answer, bodyStart));
        String before = answer.substring(0, bodyStart);
        String after = answer.substring(bodyEnd);
        return tidyNewlines(before + fill + "\n" + after);
    }

    /** Earliest match start of {@code p} at/after {@code from}, or {@link Integer#MAX_VALUE}. */
    private static int findAnchoredStart(Pattern p, String s, int from) {
        if (s == null || from >= s.length()) {
            return Integer.MAX_VALUE;
        }
        Matcher m = p.matcher(s);
        if (m.find(from)) {
            return m.start();
        }
        return Integer.MAX_VALUE;
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

    /**
     * Strip {@code role=}/{@code role＝} / bracket tags / token+colon echoes; keep surrounding prose.
     * Bare {@code PURPOSE} without a colon is kept ({@code PURPOSE IS CLEAR:}).
     * Then drop empty brackets and leftover separators. Used for the player body.
     * 【來源】 footer is translated by {@link #translateInternalTokens}, not this method.
     */
    static String scrubInternalFieldEcho(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String t = ROLE_EQ_TOKEN.matcher(text).replaceAll("");
        t = PROMPT_SECTION_TAG.matcher(t).replaceAll("");
        t = TAG_CJK.matcher(t).replaceAll("");
        t = TAG_FW_PAREN.matcher(t).replaceAll("");
        t = TAG_PAREN.matcher(t).replaceAll("");
        t = BARE_INTERNAL_SECTION.matcher(t).replaceAll("");
        return stripReplyDebris(t);
    }

    /**
     * Empty brackets, leftover/duplicate separators, extra space. After token strip only.
     * ponytail: bounded loop; ceiling = 8 nested empty wrappers; upgrade = parser.
     */
    private static String stripReplyDebris(String text) {
        String t = text;
        for (int i = 0; i < 8; i++) {
            String prev = t;
            t = EMPTY_BRACKETS.matcher(t).replaceAll("");
            t = DUP_SEPARATORS.matcher(t).replaceAll("$1");
            t = LEADING_ORPHAN_SEP.matcher(t).replaceAll("");
            t = TRAILING_ORPHAN_SEP.matcher(t).replaceAll("");
            t = HALF_ORPHAN_OPEN.matcher(t).replaceAll("");
            t = HALF_ORPHAN_CLOSE.matcher(t).replaceAll("");
            if (t.equals(prev)) {
                break;
            }
        }
        t = SPACE_BEFORE_CLOSE.matcher(t).replaceAll("$1");
        t = SPACE_AFTER_OPEN.matcher(t).replaceAll("$1");
        t = MULTISPACE.matcher(t).replaceAll(" ");
        t = TRAILING_SPACE.matcher(t).replaceAll("");
        return t;
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
        next = Math.min(next, findHeadingAfter(HOW_TO_UPGRADE_HEAD, answer, headingStart));
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

    /** Strip DSML / {@code <tool_call>} dumps and leaked {@code [[tools]]} JSON. Leaves {@code [[item:]]} / {@code [[recipe:]]}. */
    public static String scrubLeakedToolXml(String answer) {
        if (answer == null || answer.isEmpty()) {
            return "";
        }
        String t = DSML_TOOL_CALLS_BLOCK.matcher(answer).replaceAll("");
        t = DSML_INVOKE_BLOCK.matcher(t).replaceAll("");
        t = GENERIC_TOOL_XML.matcher(t).replaceAll("");
        t = LEFTOVER_TOOL_TOKEN.matcher(t).replaceAll("");
        t = scrubToolsJsonMarker(t);
        return dropResidualDsmlLines(t);
    }

    /** Last-resort: a line still containing the literal DSML token is leaked markup — drop the whole line. */
    private static String dropResidualDsmlLines(String text) {
        if (text == null || text.isEmpty() || text.indexOf("DSML") < 0) {
            return text == null ? "" : text;
        }
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder(text.length());
        boolean first = true;
        for (String line : lines) {
            if (line.contains("DSML")) {
                continue;
            }
            if (!first) {
                sb.append('\n');
            }
            first = false;
            sb.append(line);
        }
        return sb.toString();
    }

    /** Remove {@code [[tools]] {...}} blocks (nested braces via depth count). */
    static String scrubToolsJsonMarker(String answer) {
        if (answer == null || answer.isEmpty()) {
            return answer == null ? "" : answer;
        }
        Matcher m = TOOLS_JSON_START.matcher(answer);
        if (!m.find()) {
            return answer;
        }
        StringBuilder sb = new StringBuilder(answer.length());
        int last = 0;
        m.reset();
        while (m.find()) {
            if (m.start() < last) {
                continue;  // nested/overlapping marker already consumed by brace-depth skip
            }
            sb.append(answer, last, m.start());
            int braceAt = m.end() - 1;
            int depth = 0;
            int end = -1;
            for (int i = braceAt; i < answer.length(); i++) {
                char c = answer.charAt(i);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        end = i;
                        break;
                    }
                }
            }
            if (end < 0) {
                // Unbalanced — drop marker+`{` prefix only; leave rest for player visibility tradeoff.
                last = m.end();
            } else {
                last = end + 1;
            }
        }
        sb.append(answer, last, answer.length());
        return sb.toString();
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

    /** True when the line may be shown to the player (no model-facing markers). {@code role=} is case-insensitive. */
    public static boolean isPlayerSafeLine(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }
        for (String marker : PLAYER_UNSAFE_MARKERS) {
            if ("role=".equals(marker)) {
                if (line.toLowerCase(Locale.ROOT).contains("role=")) {
                    return false;
                }
            } else if (line.contains(marker)) {
                return false;
            }
        }
        return true;
    }

    /** Player-visible fallback facts. Fail-closed: drop any line carrying model-facing text. */
    @SafeVarargs
    public static List<String> playerSafeFacts(List<String>... groups) {
        List<String> out = new ArrayList<>();
        if (groups == null) {
            return out;
        }
        for (List<String> group : groups) {
            if (group == null) {
                continue;
            }
            for (String raw : group) {
                if (raw == null || raw.isEmpty()) {
                    continue;
                }
                for (String line : raw.split("\\R", -1)) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    if (isPlayerSafeLine(line)) {
                        out.add(line);
                    }
                }
            }
        }
        return out;
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

    /** 只刪「去前後空白後整行 ==」其中一條 lang 模板句；其餘一律唔動。 */
    public static String stripLangMissLine(String body, String lang) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        String miss = ReplyLang.askMissAcquirePlayer(lang);
        String unknown = ReplyLang.obtainUnknown(lang);
        boolean missOk = miss != null && !miss.isBlank();
        boolean unkOk = unknown != null && !unknown.isBlank();
        if (!missOk && !unkOk) {
            return body;
        }
        StringBuilder out = new StringBuilder(body.length());
        boolean dropped = false;
        int i = 0;
        int n = body.length();
        while (i <= n) {
            int lineEnd = i;
            while (lineEnd < n) {
                char c = body.charAt(lineEnd);
                if (c == '\n' || c == '\r') {
                    break;
                }
                lineEnd++;
            }
            String line = body.substring(i, lineEnd);
            String trimmed = line.strip();
            boolean hit = (missOk && trimmed.equals(miss)) || (unkOk && trimmed.equals(unknown));
            int next;
            if (lineEnd >= n) {
                next = n + 1;
            } else if (body.charAt(lineEnd) == '\r'
                    && lineEnd + 1 < n
                    && body.charAt(lineEnd + 1) == '\n') {
                next = lineEnd + 2;
            } else {
                next = lineEnd + 1;
            }
            if (hit) {
                dropped = true;
                // R2 LOW: 命中刪行時，若下一行係空行（只含空格／tab／全角空白）就一併吞掉一個，
                // 令「miss 句前後各一空行」樣本唔會留低兩個相連空行。
                next = skipOneBlankLine(body, next, n);
            } else {
                out.append(line);
                if (next <= n) {
                    out.append(body, lineEnd, next);
                }
            }
            if (next > n) {
                break;
            }
            i = next;
        }
        return dropped ? out.toString() : body;
    }

    /**
     * 由 {@code from} 開始：若成行係空行（空格／tab／\u3000）→ 回傳該行之後嘅 index；
     * 否則原樣回傳 {@code from}。
     */
    private static int skipOneBlankLine(String body, int from, int n) {
        int end = from;
        while (end < n) {
            char c = body.charAt(end);
            if (c == '\n' || c == '\r') {
                break;
            }
            if (c != ' ' && c != '\t' && c != '\u3000') {
                return from;
            }
            end++;
        }
        if (end >= n) {
            return from;
        }
        if (body.charAt(end) == '\r' && end + 1 < n && body.charAt(end + 1) == '\n') {
            return end + 2;
        }
        return end + 1;
    }
}
