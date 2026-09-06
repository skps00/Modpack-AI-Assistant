package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits assistant answer text around recipe / item markers so the UI can interleave
 * JEI recipe cards and inline item icons. Markers are never shown to the player.
 *
 * <pre>
 * {{RECIPE}} / {{RECIPE:n}} / {{RECIPE:mod:id}}
 * [[recipe:mod:id]] / [[recipe:n]] / [[recipe_card:n]]
 * [[item:mod:id]] / {{item:mod:id}} / {item:mod:id}
 * optional count: {{item:mod:id×64}} / {{item:mod:id x64}}
 * optional flat SNBT: {{item:gateways:gate_pearl{gateway:"ns:path"}}}
 *   (compound without nested braces; count may follow SNBT)
 * </pre>
 *
 * Multi-output asks do <b>not</b> rely on fuzzy paragraph→name matching: when cards
 * cover ≥2 distinct sectionKeys, the UI builds per-item sections from
 * {@code [[item:id]]} blocks (strict) or preamble + title+cards (no orphan fill).
 */
public final class RecipeEmbed {
    /**
     * Registry ref after recipe/item: {@code ns:path} or multi-segment
     * ({@code mod:ns:path} when LLM copies prompt placeholder {@code mod:id}).
     */
    private static final String REGISTRY_REF = "[a-z0-9_]+(?::[a-z0-9_./-]+)+";
    /** Flat SNBT compound (no nested braces) after registry id. */
    private static final String FLAT_SNBT = "\\{[^}]*\\}";
    /**
     * Index, bare RECIPE, or registry id after RECIPE.
     * Prefer {@code [[recipe_card:n]]} (guide interleave); legacy {@code [[recipe:]]} kept.
     * {@code recipe(?:_card)?} does not match {@code [[recipe_cards:on|off]]} (underscore+s).
     */
    private static final Pattern RECIPE_MARKER = Pattern.compile(
            "(?:\\{\\{\\s*RECIPE(?:\\s*:\\s*(\\d+|" + REGISTRY_REF + "))?\\s*\\}\\}"
                    + "|\\[\\[\\s*recipe(?:_card)?\\s*:\\s*(\\d+|" + REGISTRY_REF + ")\\s*\\]\\])",
            Pattern.CASE_INSENSITIVE);
    /**
     * Groups per form: id, snbt?, count?.
     * [[ 1,2,3 ]] | {{ 4,5,6 }} | { 7,8,9 }.
     */
    private static final Pattern ITEM_MARKER = Pattern.compile(
            "(?:\\[\\[\\s*item\\s*:\\s*(" + REGISTRY_REF + ")(" + FLAT_SNBT + ")?(?:\\s*[×xX*]\\s*(\\d+))?\\s*\\]\\]"
                    + "|\\{\\{\\s*item\\s*:\\s*(" + REGISTRY_REF + ")(" + FLAT_SNBT + ")?(?:\\s*[×xX*]\\s*(\\d+))?\\s*\\}\\}"
                    + "|\\{\\s*item\\s*:\\s*(" + REGISTRY_REF + ")(" + FLAT_SNBT + ")?(?:\\s*[×xX*]\\s*(\\d+))?\\s*\\})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_MARKER = Pattern.compile(
            RECIPE_MARKER.pattern() + "|" + ITEM_MARKER.pattern(),
            Pattern.CASE_INSENSITIVE);
    /** Catch-all: never leave raw {@code [[recipe(_card):…]]} / {@code {{RECIPE…}}} in chat text. */
    private static final Pattern ORPHAN_RECIPE_MARK = Pattern.compile(
            "(?i)\\[\\[\\s*recipe(?:_card)?\\s*:[^\\]]*\\]\\]|\\{\\{\\s*RECIPE(?:\\s*:[^}]*)?\\s*\\}\\}");
    /** Prefer {@link ReplySources#HEADER} so zh_cn 【来源】 is not missed. */
    private static final Pattern SOURCES = ReplySources.HEADER;
    /** Line-start how-to-use heading (purpose_first). LLM may still put cards before this. */
    private static final Pattern HOW_TO_USE_HEAD = Pattern.compile(
            "(?im)^(?:#{1,3}[ \\t]*)?(?:怎么用|怎麼用|怎样用|怎樣用|用途|how to use)(?:[ \\t]*[:：].*)?$");
    private static final Pattern HOW_TO_GET_HEAD = Pattern.compile(
            "(?im)^(?:#{1,3}[ \\t]*)?(?:怎么来|怎么來|怎麼来|怎麼來|怎样来|怎樣來|怎么取得|怎么获得|怎样取得|怎样获得|怎麼取得|怎麼獲得|怎樣取得|怎樣獲得|How to get|How to obtain|取得方式|获取方式|取得方法|獲取方式)(?:[ \\t]*[:：].*)?$");
    /** Upgrade / 強化 section — same heading tier as GET/USE (R5 emission interleave). */
    private static final Pattern HOW_TO_UPGRADE_HEAD = Pattern.compile(
            "(?im)^(?:#{1,3}[ \\t]*)?(?:强化|強化|升级|升級|Upgrade|How to upgrade)(?:[ \\t]*[:：].*)?$");
    /** Numbered step line ({@code 1.} / {@code 2)}) for emission card anchors. */
    private static final Pattern NUMBERED_STEP_LINE = Pattern.compile(
            "^[ \\t]*\\d+[.)][ \\t]+\\S");
    /**
     * R5.3 optional AI card ref — distinct from forbidden {@code [[recipe_card:N]]}.
     * ASCII token; stripped from player-visible text after resolve.
     */
    private static final Pattern CARD_REF_TOKEN = Pattern.compile(
            "\\[card:(\\d+)\\]", Pattern.CASE_INSENSITIVE);

    public enum Kind {
        TEXT,
        CARD,
        ITEM
    }

    /**
     * One display chunk: plain text, a recipe-card index, or an item registry id
     * ({@link Kind#ITEM} → {@code text} holds {@code mod:id}; {@code itemCount} ≥ 1).
     */
    public record Part(Kind kind, String text, int cardIndex, int itemCount) {
        public static Part text(String text) {
            return new Part(Kind.TEXT, text == null ? "" : text, -1, 0);
        }

        public static Part card(int index) {
            return new Part(Kind.CARD, "", index, 0);
        }

        public static Part item(String id) {
            return item(id, 1);
        }

        public static Part item(String id, int count) {
            return new Part(
                    Kind.ITEM,
                    id == null ? "" : id.toLowerCase(Locale.ROOT),
                    -1,
                    Math.max(1, count));
        }

        public boolean isCard() {
            return kind == Kind.CARD;
        }

        public boolean isItem() {
            return kind == Kind.ITEM;
        }
    }

    private RecipeEmbed() {}

    /**
     * Plan UI segments for {@code answer} given available cards (0-based).
     * Multi-output → UI sections per primary output (not a trailing card dump).
     * Single-output / no cards → markers, else cards after first paragraph.
     */
    public static List<Part> parts(String answer, List<RecipeCard> cards) {
        int cardCount = cards == null ? 0 : cards.size();
        return parts(answer, cardCount, cards);
    }

    public static List<Part> parts(String answer, int cardCount) {
        return parts(answer, cardCount, null);
    }

    public static List<Part> parts(String answer, int cardCount, List<RecipeCard> cards) {
        String raw = answer == null ? "" : answer;
        if (cardCount <= 0 && !ITEM_MARKER.matcher(raw).find()) {
            String cleaned = stripMarkers(raw).trim();
            return cleaned.isEmpty() ? List.of() : splitItemsOnly(cleaned);
        }

        List<Part> planned;
        // Explicit recipe / recipe_card markers win (guide interleave) — even multi-select.
        if (RECIPE_MARKER.matcher(raw).find()) {
            planned = fromMarkers(raw, cardCount, cards);
        } else if (cards != null && distinctSectionKeyCount(cards) >= 2) {
            // ponytail: multi-item Ask without recipe markers — section by sourceItemId
            planned = sectionByOutputs(raw, cards);
        } else {
            Matcher any = ANY_MARKER.matcher(raw);
            if (!any.find()) {
                planned = expandItemMarkersInTextParts(fallback(raw, cardCount, cards));
            } else {
                planned = fromMarkers(raw, cardCount, cards);
            }
        }
        return coalescePurposeFirstCards(planned, cards);
    }

    /** Remove all recipe and item markers from text. */
    public static String stripMarkers(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String t = ANY_MARKER.matcher(text).replaceAll("");
        t = ORPHAN_RECIPE_MARK.matcher(t).replaceAll("");
        return t.replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    /**
     * Prompt shows {@code [[recipe:mod:id]]}; models often emit {@code mod:ns:path}.
     * Strip the literal {@code mod:} prefix when another {@code :} remains.
     */
    /**
     * Normalize registry ref; preserve trailing flat SNBT ({@code id{…}}).
     * Strips mistaken {@code mod:} prompt prefix from the id segment only.
     */
    static String normalizeRegistryRef(String ref) {
        if (ref == null || ref.isBlank()) {
            return ref == null ? "" : ref.trim();
        }
        String raw = ref.trim();
        String snbt = "";
        int brace = raw.indexOf('{');
        if (brace > 0) {
            snbt = raw.substring(brace).toLowerCase(Locale.ROOT);
            raw = raw.substring(0, brace).trim();
        }
        String r = raw.toLowerCase(Locale.ROOT);
        if (r.matches("\\d+")) {
            return r;
        }
        if (r.startsWith("mod:")) {
            int second = r.indexOf(':', 4);
            if (second > 0) {
                r = r.substring(4);
            }
        }
        return r + snbt;
    }

    /** Id segment without flat SNBT (for section-key matching). */
    static String bareRegistryId(String ref) {
        if (ref == null || ref.isBlank()) {
            return "";
        }
        String r = ref.trim().toLowerCase(Locale.ROOT);
        int brace = r.indexOf('{');
        if (brace > 0) {
            r = r.substring(0, brace);
        }
        if (r.startsWith("mod:")) {
            int second = r.indexOf(':', 4);
            if (second > 0) {
                r = r.substring(4);
            }
        }
        return r;
    }

    /**
     * True when a card appears and later a non-sources text/item follows — i.e. not
     * "full wall then all cards".
     */
    static boolean hasInlineInterleave(List<Part> parts) {
        if (parts == null || parts.isEmpty()) {
            return false;
        }
        boolean sawCard = false;
        for (Part p : parts) {
            if (p.isCard()) {
                sawCard = true;
                continue;
            }
            if (!sawCard) {
                continue;
            }
            if (p.isItem()) {
                return true;
            }
            if (p.kind == Kind.TEXT) {
                String t = p.text() == null ? "" : p.text().trim();
                if (t.isEmpty()) {
                    continue;
                }
                // Ignore a trailing sources-only footer after the card dump.
                if (indexOfSources(t) == 0) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    private static int distinctSectionKeyCount(List<RecipeCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return 0;
        }
        LinkedHashMap<String, Boolean> seen = new LinkedHashMap<>();
        for (RecipeCard c : cards) {
            if (c == null) {
                continue;
            }
            String id = c.sectionKey();
            if (id == null || id.isEmpty()) {
                continue;
            }
            seen.put(id.toLowerCase(Locale.ROOT), Boolean.TRUE);
        }
        return seen.size();
    }

    private static LinkedHashMap<String, List<Integer>> groupCardIndices(List<RecipeCard> cards) {
        LinkedHashMap<String, List<Integer>> groups = new LinkedHashMap<>();
        if (cards == null) {
            return groups;
        }
        for (int i = 0; i < cards.size(); i++) {
            RecipeCard c = cards.get(i);
            if (c == null || c.isEmpty()) {
                continue;
            }
            // Prefer Ask sourceItemId so Quests/FLOW still sit under the selected item.
            String id = c.sectionKey();
            if (id == null || id.isEmpty()) {
                id = "__idx_" + i;
            } else {
                id = id.toLowerCase(Locale.ROOT);
            }
            groups.computeIfAbsent(id, k -> new ArrayList<>()).add(i);
        }
        return groups;
    }

    private static String displayNameOf(RecipeCard c) {
        if (c == null || c.outputs() == null || c.outputs().isEmpty()) {
            return "";
        }
        net.minecraft.world.item.ItemStack stack = c.outputs().get(0);
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return Plainify.stripMcFormat(stack.getHoverName().getString()).toLowerCase(Locale.ROOT);
    }

    /**
     * UI-driven: for each distinct sectionKey, item title → that item's text → cards.
     * Prefer strict {@code [[item:id]]} blocks matching selected keys; never fuzzy-fill
     * orphan paragraphs into empty sections (that caused axe text off-by-one).
     */
    private static List<Part> sectionByOutputs(String raw, List<RecipeCard> cards) {
        LinkedHashMap<String, List<Integer>> groups = groupCardIndices(cards);
        if (groups.isEmpty()) {
            return fallback(raw, cards == null ? 0 : cards.size(), cards);
        }

        int sourcesAt = indexOfSources(raw);
        String mainRaw = sourcesAt >= 0 ? raw.substring(0, sourcesAt) : raw;
        String sources = sourcesAt >= 0 ? raw.substring(sourcesAt).trim() : "";

        Map<String, String> names = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> e : groups.entrySet()) {
            String name = "";
            for (int idx : e.getValue()) {
                if (idx >= 0 && idx < cards.size()) {
                    name = displayNameOf(cards.get(idx));
                    if (!name.isEmpty()) {
                        break;
                    }
                }
            }
            names.put(e.getKey(), name);
        }

        LinkedHashMap<String, List<String>> buckets = new LinkedHashMap<>();
        for (String id : groups.keySet()) {
            buckets.put(id, new ArrayList<>());
        }
        List<String> preamble = new ArrayList<>();
        if (!fillBucketsByItemMarkers(mainRaw, groups, buckets, preamble)) {
            fillBucketsByLineStartNames(
                    stripMarkers(mainRaw).trim(), groups.keySet(), names, buckets, preamble);
        }

        List<Part> out = new ArrayList<>();
        if (!preamble.isEmpty()) {
            String joined = tidyChunk(String.join("\n\n", preamble), true, true);
            if (!joined.isEmpty()) {
                out.add(Part.text(joined));
            }
        }
        for (Map.Entry<String, List<Integer>> e : groups.entrySet()) {
            String id = e.getKey();
            if (!id.startsWith("__idx_")) {
                out.add(Part.item(id));
            }
            List<String> body = buckets.get(id);
            if (body != null && !body.isEmpty()) {
                out.add(Part.text(String.join("\n\n", body)));
            }
            for (int idx : e.getValue()) {
                out.add(Part.card(idx));
            }
        }
        if (!sources.isEmpty()) {
            out.add(Part.text(sources));
        }
        return mergeAdjacentText(out);
    }

    /**
     * Split {@code mainRaw} on {@code [[item:id]]} / {@code {{item:id}}} that match
     * {@code groups}. Text after a matched marker until the next marker belongs to that id.
     * @return true when ≥1 selected-item marker found
     */
    private static boolean fillBucketsByItemMarkers(
            String mainRaw,
            LinkedHashMap<String, List<Integer>> groups,
            LinkedHashMap<String, List<String>> buckets,
            List<String> preamble
    ) {
        if (mainRaw == null || mainRaw.isEmpty()) {
            return false;
        }
        Matcher m = ITEM_MARKER.matcher(mainRaw);
        boolean anySelected = false;
        while (m.find()) {
            String id = itemIdFromMatch(m);
            if (id != null && groups.containsKey(bareRegistryId(id))) {
                anySelected = true;
                break;
            }
        }
        if (!anySelected) {
            return false;
        }
        m.reset();
        int last = 0;
        String currentId = null;
        while (m.find()) {
            String before = tidyChunk(stripRecipeMarkersOnly(mainRaw.substring(last, m.start())), true, true);
            if (!before.isEmpty()) {
                if (currentId != null && buckets.containsKey(currentId)) {
                    buckets.get(currentId).add(before);
                } else {
                    preamble.add(before);
                }
            }
            String id = itemIdFromMatch(m);
            String bare = id == null ? "" : bareRegistryId(id);
            currentId = groups.containsKey(bare) ? bare : null;
            last = m.end();
        }
        String tail = tidyChunk(stripMarkers(mainRaw.substring(last)), true, true);
        if (!tail.isEmpty()) {
            if (currentId != null && buckets.containsKey(currentId)) {
                buckets.get(currentId).add(tail);
            } else {
                preamble.add(tail);
            }
        }
        return true;
    }

    /**
     * Split on display-name / registry-id at <b>line start</b> (longest needle wins).
     * Unmatched chunks → preamble only — never round-robin into empty sections.
     */
    private static void fillBucketsByLineStartNames(
            String main,
            Iterable<String> sectionIds,
            Map<String, String> names,
            Map<String, List<String>> buckets,
            List<String> preamble
    ) {
        if (main == null || main.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>();
        for (String id : sectionIds) {
            ids.add(id);
        }
        List<String[]> needles = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            needles.add(new String[] {String.valueOf(i), id.toLowerCase(Locale.ROOT)});
            String name = names.getOrDefault(id, "");
            if (name != null && name.length() >= 2) {
                needles.add(new String[] {String.valueOf(i), name.toLowerCase(Locale.ROOT)});
            }
        }
        needles.sort((a, b) -> Integer.compare(b[1].length(), a[1].length()));

        List<int[]> headers = new ArrayList<>();
        int offset = 0;
        String[] lines = main.split("\n", -1);
        for (int li = 0; li < lines.length; li++) {
            String line = lines[li];
            int leadWs = 0;
            while (leadWs < line.length() && Character.isWhitespace(line.charAt(leadWs))) {
                leadWs++;
            }
            String lower = line.substring(leadWs).toLowerCase(Locale.ROOT);
            int bestIdx = -1;
            int bestLen = 0;
            for (String[] n : needles) {
                String needle = n[1];
                if (needle.length() < bestLen) {
                    break;
                }
                if (lineStartsWithNeedle(lower, needle) && needle.length() > bestLen) {
                    bestLen = needle.length();
                    bestIdx = Integer.parseInt(n[0]);
                }
            }
            if (bestIdx >= 0) {
                int contentStart = offset + leadWs + bestLen;
                while (contentStart < offset + line.length()) {
                    char c = main.charAt(contentStart);
                    if (c == ' ' || c == '\t' || c == ':' || c == '：'
                            || c == '-' || c == '—' || c == '–') {
                        contentStart++;
                    } else {
                        break;
                    }
                }
                if (contentStart >= offset + line.length()) {
                    contentStart = offset + line.length();
                    if (contentStart < main.length() && main.charAt(contentStart) == '\n') {
                        contentStart++;
                    }
                }
                headers.add(new int[] {offset, contentStart, bestIdx});
            }
            offset += line.length();
            if (li < lines.length - 1) {
                offset++;
            }
        }
        if (!headers.isEmpty()) {
            if (headers.get(0)[0] > 0) {
                String lead = tidyChunk(main.substring(0, headers.get(0)[0]), true, true);
                if (!lead.isEmpty()) {
                    preamble.add(lead);
                }
            }
            for (int i = 0; i < headers.size(); i++) {
                int[] h = headers.get(i);
                String id = ids.get(h[2]);
                int end = i + 1 < headers.size() ? headers.get(i + 1)[0] : main.length();
                String chunk = tidyChunk(main.substring(h[1], end), true, true);
                if (!chunk.isEmpty() && buckets.containsKey(id)) {
                    buckets.get(id).add(chunk);
                }
            }
            return;
        }
        // No line-start headers — leave unmatched as preamble (do not fill empty sections).
        preamble.add(main);
    }

    private static boolean lineStartsWithNeedle(String lowerLine, String needle) {
        if (lowerLine.equals(needle)) {
            return true;
        }
        if (!lowerLine.startsWith(needle)) {
            return false;
        }
        char next = lowerLine.charAt(needle.length());
        return next == ' ' || next == '\t' || next == ':' || next == '：'
                || next == '-' || next == '—' || next == '–'
                || next == '(' || next == '（';
    }

    /** Strip recipe markers only so item-marker scan can keep section boundaries. */
    private static String stripRecipeMarkersOnly(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String t = RECIPE_MARKER.matcher(text).replaceAll("");
        t = ORPHAN_RECIPE_MARK.matcher(t).replaceAll("");
        return t.replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static List<Part> fromMarkers(String raw, int cardCount, List<RecipeCard> cards) {
        List<Part> out = new ArrayList<>();
        boolean[] used = new boolean[Math.max(0, cardCount)];
        int nextAuto = 0;
        // When LLM places [[recipe_card:N]], do not auto-dump cards on [[item:]] (breaks interleave).
        boolean explicitRecipeMarks = RECIPE_MARKER.matcher(raw).find();
        Matcher m = ANY_MARKER.matcher(raw);
        int last = 0;
        while (m.find()) {
            String before = tidyChunk(raw.substring(last, m.start()), true, true);
            if (!before.isEmpty()) {
                out.addAll(splitItemsInText(before));
            }
            String token = m.group();
            if (isItemToken(token)) {
                Matcher im = ITEM_MARKER.matcher(token);
                if (im.find()) {
                    String id = itemIdFromMatch(im);
                    if (id != null && !id.isBlank()) {
                        String cleanId = normalizeRegistryRef(id);
                        out.add(Part.item(cleanId, itemCountFromMatch(im)));
                        // Auto-place that item's unused cards here (LLM forgot [[recipe:]])
                        if (!explicitRecipeMarks) {
                            attachCardsForOutput(out, cards, cardCount, used, bareRegistryId(cleanId));
                        }
                    }
                }
            } else {
                String ref = firstNonNull(m.group(1), m.group(2));
                int idx = resolveCardIndex(ref, cardCount, cards, used, nextAuto);
                // Clamp: out-of-range / already used → skip (marker scrubbed from text already)
                if (idx >= 0 && idx < cardCount && !used[idx]) {
                    used[idx] = true;
                    if (ref == null || ref.isBlank() || ref.matches("\\d+")) {
                        nextAuto = Math.max(nextAuto, idx + 1);
                    }
                    out.add(Part.card(idx));
                } else if (idx == -2) {
                    while (nextAuto < cardCount && used[nextAuto]) {
                        nextAuto++;
                    }
                    if (nextAuto < cardCount) {
                        used[nextAuto] = true;
                        out.add(Part.card(nextAuto++));
                    }
                }
            }
            last = m.end();
        }
        String tail = tidyChunk(raw.substring(last), true, true);
        if (!tail.isEmpty()) {
            out.addAll(splitItemsInText(tail));
        }
        // Only dump leftovers when single-output / truly unmatched — multi goes sectionByOutputs upstream
        appendUnused(out, used, cards);
        return mergeAdjacentText(out);
    }

    /**
     * If the reply has a how-to-use heading, never leave recipe cards before it.
     * Use (input) cards gather after that section; obtain cards stay with 怎么来 when present.
     * Multi-item section layout is left alone.
     */
    static List<Part> coalescePurposeFirstCards(List<Part> parts, List<RecipeCard> cards) {
        if (parts == null || parts.isEmpty()) {
            return parts == null ? List.of() : parts;
        }
        if (cards != null && distinctSectionKeyCount(cards) >= 2) {
            return parts;
        }
        boolean anyCard = false;
        for (Part p : parts) {
            if (p.isCard()) {
                anyCard = true;
                break;
            }
        }
        if (!anyCard) {
            return parts;
        }
        int useAt = indexOfHeading(parts, HOW_TO_USE_HEAD);
        if (useAt < 0) {
            return parts;
        }
        // Normal GET-before-USE: leave obtain cards already interleaved inside GET section.
        // purpose_first (USE before GET): getAt >= useAt → no in-section skip → same as old move-all.
        int getAtOrig = indexOfHeading(parts, HOW_TO_GET_HEAD);
        List<Part> moved = new ArrayList<>();
        List<Part> rest = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            if (i < useAt && p.isCard()) {
                boolean inGetSection = getAtOrig >= 0 && getAtOrig < useAt
                        && i > getAtOrig && i < useAt;
                if (inGetSection && isObtainCard(p, cards)) {
                    rest.add(p);
                } else {
                    moved.add(p);
                }
            } else {
                rest.add(p);
            }
        }
        if (moved.isEmpty()) {
            return parts;
        }
        int getAt = indexOfHeading(rest, HOW_TO_GET_HEAD);
        List<Part> useCards = new ArrayList<>();
        List<Part> obtainCards = new ArrayList<>();
        for (Part c : moved) {
            if (getAt >= 0 && isObtainCard(c, cards)) {
                obtainCards.add(c);
            } else {
                useCards.add(c);
            }
        }
        int insertUse = insertUseCardsAt(rest);
        rest.addAll(insertUse, useCards);
        if (!obtainCards.isEmpty()) {
            int insertGet = indexOfHeading(rest, HOW_TO_GET_HEAD);
            int at = insertGet >= 0 ? insertGet + 1 : rest.size();
            rest.addAll(at, obtainCards);
        }
        return mergeAdjacentText(rest);
    }

    private static int indexOfHeading(List<Part> parts, Pattern head) {
        if (parts == null || head == null) {
            return -1;
        }
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            if (p == null || p.kind() != Kind.TEXT || p.text() == null) {
                continue;
            }
            if (head.matcher(p.text()).find()) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfSourcesPart(List<Part> parts) {
        if (parts == null) {
            return -1;
        }
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            if (p != null && p.kind() == Kind.TEXT && indexOfSources(p.text()) >= 0) {
                return i;
            }
        }
        return -1;
    }

    /** Insert index just before the peeled 【来源】/[Sources] TEXT part (or end). */
    public static int indexBeforeSources(List<Part> parts) {
        int at = indexOfSourcesPart(parts);
        return at >= 0 ? at : (parts == null ? 0 : parts.size());
    }

    /**
     * Peel {@code 【来源】}/{@code [Sources]} onto its own TEXT part so obtain-family
     * cards can sit in 怎么来 without landing after the footer.
     */
    public static void splitTrailingSources(List<Part> parts) {
        if (parts == null || parts.isEmpty()) {
            return;
        }
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            if (p == null || p.kind() != Kind.TEXT || p.text() == null) {
                continue;
            }
            int at = indexOfSources(p.text());
            if (at <= 0) {
                continue;
            }
            String before = p.text().substring(0, at).stripTrailing();
            String src = p.text().substring(at).trim();
            if (before.isEmpty()) {
                parts.set(i, Part.text(src));
                continue;
            }
            parts.set(i, Part.text(before));
            if (!src.isEmpty()) {
                parts.add(i + 1, Part.text(src));
                i++;
            }
        }
    }

    /**
     * AI {@code cardStrip} interleave (R5 / R5.2 / R5.3): peel sources → split TEXT into
     * per-step blocks → place cards cited by optional {@code [card:N]} (whitelist) after
     * that step (token stripped) → remaining cards via category/needle / section /
     * before-sources fallback. Sources footer always last.
     */
    public static List<Part> interleaveEmissionCards(String answer, List<RecipeCard> cards) {
        String raw = answer == null ? "" : answer;
        List<Part> parts = new ArrayList<>(parts(raw, List.of()));
        splitTrailingSources(parts);
        int srcAt = indexOfSourcesPart(parts);
        List<Part> sourceParts = new ArrayList<>();
        if (srcAt >= 0) {
            while (parts.size() > srcAt) {
                sourceParts.add(parts.remove(srcAt));
            }
        }
        List<Part> blocks = splitTextIntoStepBlocks(parts);
        int cardCount = cards == null ? 0 : cards.size();
        boolean[] placed = new boolean[cardCount];
        if (cardCount > 0) {
            placeEmissionCardsByRef(blocks, cards, placed);
            for (int i = 0; i < cardCount; i++) {
                if (placed[i]) {
                    continue;
                }
                RecipeCard c = cards.get(i);
                if (c == null || c.isEmpty()) {
                    continue;
                }
                int insertAt = findEmissionInsertIndex(blocks, c);
                if (insertAt < 0 || insertAt > blocks.size()) {
                    insertAt = blocks.size();
                }
                blocks.add(insertAt, Part.card(i));
            }
        }
        stripCardRefTokens(blocks);
        blocks.addAll(sourceParts);
        return blocks;
    }

    /**
     * R5.3: scan step text for {@code [card:N]}; whitelist N∈[1..cards.size()];
     * insert after that block; always strip token (unknown N → strip only, card stays
     * for needle/section fallback).
     */
    private static void placeEmissionCardsByRef(
            List<Part> blocks, List<RecipeCard> cards, boolean[] placed
    ) {
        if (blocks == null || cards == null || placed == null) {
            return;
        }
        for (int i = 0; i < blocks.size(); i++) {
            Part p = blocks.get(i);
            if (p == null || p.kind() != Kind.TEXT || p.text() == null || p.text().isEmpty()) {
                continue;
            }
            Matcher m = CARD_REF_TOKEN.matcher(p.text());
            if (!m.find()) {
                continue;
            }
            List<Integer> toPlace = new ArrayList<>();
            StringBuffer sb = new StringBuffer();
            m.reset();
            while (m.find()) {
                int n;
                try {
                    n = Integer.parseInt(m.group(1));
                } catch (NumberFormatException e) {
                    m.appendReplacement(sb, "");
                    continue;
                }
                int idx = n - 1;
                if (idx >= 0 && idx < cards.size() && !placed[idx]) {
                    RecipeCard c = cards.get(idx);
                    if (c != null && !c.isEmpty()) {
                        toPlace.add(idx);
                        placed[idx] = true;
                    }
                }
                m.appendReplacement(sb, "");
            }
            m.appendTail(sb);
            blocks.set(i, Part.text(sb.toString()));
            if (toPlace.isEmpty()) {
                continue;
            }
            int insertAt = skipCardsAfter(blocks, i + 1);
            for (int idx : toPlace) {
                blocks.add(insertAt, Part.card(idx));
                insertAt++;
            }
        }
    }

    /** Remove any leftover {@code [card:N]} from TEXT parts (invalid / already placed). */
    private static void stripCardRefTokens(List<Part> blocks) {
        if (blocks == null) {
            return;
        }
        for (int i = 0; i < blocks.size(); i++) {
            Part p = blocks.get(i);
            if (p == null || p.kind() != Kind.TEXT || p.text() == null || p.text().isEmpty()) {
                continue;
            }
            if (!CARD_REF_TOKEN.matcher(p.text()).find()) {
                continue;
            }
            blocks.set(i, Part.text(CARD_REF_TOKEN.matcher(p.text()).replaceAll("")));
        }
    }

    /**
     * R5.2: expand TEXT into per-step blocks — each block is a section heading or a
     * numbered step line plus following lines until the next step/section (ITEM kept).
     */
    private static List<Part> splitTextIntoStepBlocks(List<Part> parts) {
        List<Part> out = new ArrayList<>();
        if (parts == null) {
            return out;
        }
        for (Part p : parts) {
            if (p == null) {
                continue;
            }
            if (p.kind() != Kind.TEXT || p.text() == null || p.text().isEmpty()) {
                out.add(p);
                continue;
            }
            String[] lines = p.text().split("\\R", -1);
            StringBuilder cur = new StringBuilder();
            for (String line : lines) {
                boolean boundary = isEmissionSectionHeading(line) || isNumberedStepLine(line);
                if (boundary && cur.length() > 0) {
                    out.add(Part.text(cur.toString().stripTrailing()));
                    cur.setLength(0);
                }
                if (cur.length() > 0) {
                    cur.append('\n');
                }
                cur.append(line);
            }
            if (cur.length() > 0) {
                String t = cur.toString();
                if (!t.isBlank()) {
                    out.add(Part.text(t.stripTrailing()));
                }
            }
        }
        return out;
    }

    /**
     * @return index after the target block to insert a card, or {@code -1} → append
     *         before sources (caller uses {@code blocks.size()}).
     */
    private static int findEmissionInsertIndex(List<Part> blocks, RecipeCard card) {
        int wantSec = emissionSectionOf(card);
        if (wantSec < 0 || blocks == null || blocks.isEmpty()) {
            return -1;
        }
        List<String> needles = emissionMatchNeedles(card);
        int currentSec = -1;
        int matchedAfter = -1;
        int sectionLastAfter = -1;
        for (int i = 0; i < blocks.size(); i++) {
            Part p = blocks.get(i);
            if (p == null || p.isCard()) {
                continue;
            }
            if (p.kind() != Kind.TEXT) {
                if (currentSec == wantSec) {
                    sectionLastAfter = i + 1;
                }
                continue;
            }
            String text = p.text() == null ? "" : p.text();
            String first = firstLine(text);
            int st = emissionSectionTypeOf(first);
            if (st >= 0) {
                currentSec = st;
                if (currentSec == wantSec) {
                    sectionLastAfter = i + 1;
                }
                continue;
            }
            if (currentSec != wantSec) {
                continue;
            }
            sectionLastAfter = i + 1;
            // R5.2: first numbered step in section whose text contains category/machine needle
            if (matchedAfter < 0 && isNumberedStepLine(first)
                    && stepMatchesEmissionNeedles(text, needles)) {
                matchedAfter = i + 1;
            }
        }
        if (matchedAfter >= 0) {
            return skipCardsAfter(blocks, matchedAfter);
        }
        if (sectionLastAfter >= 0) {
            return skipCardsAfter(blocks, sectionLastAfter);
        }
        return -1;
    }

    /** Advance past consecutive CARD parts so same-step emissions keep call order. */
    private static int skipCardsAfter(List<Part> blocks, int at) {
        int i = at;
        while (i < blocks.size() && blocks.get(i) != null && blocks.get(i).isCard()) {
            i++;
        }
        return i;
    }

    /** 0=GET, 1=USE, 2=UPGRADE; −1 = dump before sources. */
    private static int emissionSectionOf(RecipeCard card) {
        if (card == null) {
            return -1;
        }
        if (card.isUpgrade()) {
            return 2;
        }
        if (card.isInputUse()) {
            return 1;
        }
        if (card.isMaintenance()) {
            return -1;
        }
        // output / quest → obtain section
        return 0;
    }

    /**
     * R5.2 needles: JEI {@code categoryTitle} (digest 機器=…), tokens, craft-step
     * aliases when category is crafting-like, plus catalyst display names.
     */
    private static List<String> emissionMatchNeedles(RecipeCard card) {
        if (card == null) {
            return List.of();
        }
        LinkedHashMap<String, Boolean> out = new LinkedHashMap<>();
        String cat = card.categoryTitle();
        if (cat != null && !cat.isBlank()) {
            String plain = Plainify.stripMcFormat(cat).trim().toLowerCase(Locale.ROOT);
            if (!plain.isEmpty()) {
                out.put(plain, Boolean.TRUE);
                for (String tok : plain.split("[\\s/|·•、,，:：\\-]+")) {
                    if (tok != null && tok.length() >= 2) {
                        out.put(tok, Boolean.TRUE);
                    }
                }
                if (isCraftLikeCategory(plain)) {
                    for (String a : CRAFT_STEP_ALIASES) {
                        out.put(a, Boolean.TRUE);
                    }
                }
            }
        }
        if (card.catalysts() != null) {
            for (net.minecraft.world.item.ItemStack s : card.catalysts()) {
                if (s == null || s.isEmpty()) {
                    continue;
                }
                String n = Plainify.stripMcFormat(s.getHoverName().getString())
                        .trim()
                        .toLowerCase(Locale.ROOT);
                if (n.length() >= 2) {
                    out.put(n, Boolean.TRUE);
                }
            }
        }
        return List.copyOf(out.keySet());
    }

    private static boolean isCraftLikeCategory(String plain) {
        if (plain == null || plain.isEmpty()) {
            return false;
        }
        return plain.contains("craft")
                || plain.contains("workbench")
                || plain.contains("合成")
                || plain.contains("工作台");
    }

    private static final String[] CRAFT_STEP_ALIASES = {
        "crafting",
        "crafting table",
        "workbench",
        "合成",
        "工作台",
        "有序合成",
        "无序合成",
        "無序合成"
    };

    private static boolean stepMatchesEmissionNeedles(String text, List<String> needles) {
        if (text == null || text.isEmpty() || needles == null || needles.isEmpty()) {
            return false;
        }
        String hay = text.toLowerCase(Locale.ROOT);
        for (String n : needles) {
            if (n != null && !n.isEmpty() && hay.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static int emissionSectionTypeOf(String line) {
        if (line == null) {
            return -1;
        }
        String t = line.trim();
        if (t.isEmpty()) {
            return -1;
        }
        if (HOW_TO_GET_HEAD.matcher(t).matches()) {
            return 0;
        }
        if (HOW_TO_USE_HEAD.matcher(t).matches()) {
            return 1;
        }
        if (HOW_TO_UPGRADE_HEAD.matcher(t).matches()) {
            return 2;
        }
        return -1;
    }

    private static boolean isEmissionSectionHeading(String line) {
        return emissionSectionTypeOf(line) >= 0;
    }

    private static boolean isNumberedStepLine(String line) {
        return line != null && NUMBERED_STEP_LINE.matcher(line).find();
    }

    private static String firstLine(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int n = text.indexOf('\n');
        return n < 0 ? text : text.substring(0, n);
    }

    /**
     * Index for an obtain-family card: after 怎么来 heading and its obtain cards,
     * else after the last recipe card before sources, else before sources / end.
     */
    public static int insertObtainClusterAt(List<Part> parts) {
        if (parts == null || parts.isEmpty()) {
            return 0;
        }
        int getAt = indexOfHeading(parts, HOW_TO_GET_HEAD);
        int srcAt = indexOfSourcesPart(parts);
        int limit = srcAt >= 0 ? srcAt : parts.size();
        if (getAt >= 0 && getAt < limit) {
            int at = getAt + 1;
            while (at < limit && parts.get(at) != null && parts.get(at).isCard()) {
                at++;
            }
            return at;
        }
        int lastCard = -1;
        for (int i = 0; i < limit; i++) {
            if (parts.get(i) != null && parts.get(i).isCard()) {
                lastCard = i;
            }
        }
        if (lastCard >= 0) {
            return lastCard + 1;
        }
        return limit;
    }

    /** End of how-to-use block in {@code rest}: first remaining card, 怎么来, 【来源】, or end. */
    private static int insertUseCardsAt(List<Part> rest) {
        int useAt = indexOfHeading(rest, HOW_TO_USE_HEAD);
        int getAt = indexOfHeading(rest, HOW_TO_GET_HEAD);
        int srcAt = indexOfSourcesPart(rest);
        int firstCard = -1;
        int from = useAt >= 0 ? useAt + 1 : 0;
        for (int i = from; i < rest.size(); i++) {
            if (rest.get(i).isCard()) {
                firstCard = i;
                break;
            }
        }
        int end = rest.size();
        if (getAt >= 0) {
            end = Math.min(end, getAt);
        }
        if (srcAt >= 0) {
            end = Math.min(end, srcAt);
        }
        if (firstCard >= 0) {
            end = Math.min(end, firstCard);
        }
        return end;
    }

    private static boolean isObtainCard(Part card, List<RecipeCard> cards) {
        if (card == null || !card.isCard() || cards == null) {
            return false;
        }
        int idx = card.cardIndex();
        if (idx < 0 || idx >= cards.size() || cards.get(idx) == null) {
            return false;
        }
        String role = cards.get(idx).promptRole();
        return "output".equals(role) || "quest".equals(role);
    }

    private static void attachCardsForOutput(
            List<Part> out, List<RecipeCard> cards, int cardCount, boolean[] used, String outputId
    ) {
        if (cards == null || outputId == null || outputId.isEmpty()) {
            return;
        }
        String want = outputId.toLowerCase(Locale.ROOT);
        for (int i = 0; i < cards.size() && i < cardCount; i++) {
            if (used[i]) {
                continue;
            }
            RecipeCard c = cards.get(i);
            if (c == null) {
                continue;
            }
            if (want.equals(c.sectionKey()) || want.equals(c.primaryOutputId())) {
                used[i] = true;
                out.add(Part.card(i));
            }
        }
    }

    private static int resolveCardIndex(
            String ref, int cardCount, List<RecipeCard> cards, boolean[] used, int nextAuto
    ) {
        if (ref == null || ref.isBlank()) {
            return -2; // signal auto
        }
        ref = ref.trim();
        if (ref.matches("\\d+")) {
            try {
                return Integer.parseInt(ref);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        String want = bareRegistryId(ref);
        if (cards != null) {
            for (int i = 0; i < cards.size() && i < cardCount; i++) {
                if (used[i]) {
                    continue;
                }
                RecipeCard c = cards.get(i);
                if (c != null && (want.equals(c.sectionKey()) || want.equals(c.primaryOutputId()))) {
                    return i;
                }
            }
            for (int i = 0; i < cards.size() && i < cardCount; i++) {
                RecipeCard c = cards.get(i);
                if (c != null && (want.equals(c.sectionKey()) || want.equals(c.primaryOutputId()))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean isItemToken(String token) {
        if (token == null) {
            return false;
        }
        String t = token.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return t.startsWith("[[item:") || t.startsWith("{{item:") || t.startsWith("{item:");
    }

    /**
     * Registry id plus optional flat SNBT from an item marker.
     * Example: {@code gateways:gate_pearl{gateway:"kubejs:pack/drowning"}}.
     */
    private static String itemIdFromMatch(Matcher m) {
        String id = firstNonNull(m.group(1), firstNonNull(m.group(4), m.group(7)));
        if (id == null || id.isBlank()) {
            return id;
        }
        String snbt = firstNonNull(m.group(2), firstNonNull(m.group(5), m.group(8)));
        if (snbt == null || snbt.isBlank()) {
            return id;
        }
        return id + snbt;
    }

    private static int itemCountFromMatch(Matcher m) {
        String c = firstNonNull(m.group(3), firstNonNull(m.group(6), m.group(9)));
        if (c == null || c.isBlank()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(c));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static String firstNonNull(String a, String b) {
        if (a != null) {
            return a;
        }
        return b;
    }

    private static List<Part> fallback(String raw, int cardCount, List<RecipeCard> cards) {
        int sourcesAt = indexOfSources(raw);
        String main = sourcesAt >= 0 ? raw.substring(0, sourcesAt) : raw;
        String sources = sourcesAt >= 0 ? raw.substring(sourcesAt) : "";
        if (cardCount <= 0) {
            List<Part> only = splitItemsInText(tidyChunk(main, true, true));
            if (!sources.isEmpty()) {
                only.add(Part.text(sources.trim()));
            }
            return only;
        }
        if (cardCount > 1 && cards != null && distinctSectionKeyCount(cards) >= 2) {
            return sectionByOutputs(raw, cards);
        }
        if (cardCount > 1 && cards != null) {
            List<Part> interleaved = fallbackByParagraphs(main, cardCount, cards);
            if (interleaved != null) {
                if (!sources.isEmpty()) {
                    interleaved.add(Part.text(sources.trim()));
                }
                return interleaved;
            }
        }
        int split = firstParagraphEnd(main);
        String before = tidyChunk(main.substring(0, split), true, true);
        String after = tidyChunk(main.substring(split), true, true);
        List<Part> out = new ArrayList<>();
        if (!before.isEmpty()) {
            out.addAll(splitItemsInText(before));
        }
        for (int i = 0; i < cardCount; i++) {
            out.add(Part.card(i));
        }
        StringBuilder rest = new StringBuilder();
        if (!after.isEmpty()) {
            rest.append(after);
        }
        if (!sources.isEmpty()) {
            if (!rest.isEmpty()) {
                rest.append("\n\n");
            }
            rest.append(sources.trim());
        }
        if (!rest.isEmpty()) {
            out.addAll(splitItemsInText(rest.toString()));
        }
        return out;
    }

    /**
     * When text has multiple paragraphs and cards for distinct outputs, insert each
     * unused card after the paragraph that mentions its output id or display name.
     * Returns null if not enough structure.
     */
    private static List<Part> fallbackByParagraphs(String main, int cardCount, List<RecipeCard> cards) {
        String[] paras = main.split("\\n\\n+");
        if (paras.length < 2) {
            return null;
        }
        boolean[] used = new boolean[cardCount];
        List<Part> out = new ArrayList<>();
        int placed = 0;
        for (String para : paras) {
            String chunk = tidyChunk(para, true, true);
            if (chunk.isEmpty()) {
                continue;
            }
            out.addAll(splitItemsInText(chunk));
            String lower = chunk.toLowerCase(Locale.ROOT);
            for (int i = 0; i < cardCount; i++) {
                if (used[i]) {
                    continue;
                }
                RecipeCard c = cards.get(i);
                if (c == null) {
                    continue;
                }
                String key = c.sectionKey();
                String id = c.primaryOutputId();
                String name = displayNameOf(c);
                boolean hit = (!key.isEmpty() && lower.contains(key))
                        || (!id.isEmpty() && lower.contains(id))
                        || (!name.isEmpty() && name.length() >= 2 && lower.contains(name));
                if (hit) {
                    used[i] = true;
                    out.add(Part.card(i));
                    placed++;
                }
            }
        }
        if (placed == 0) {
            return null;
        }
        appendUnused(out, used, cards);
        return mergeAdjacentText(out);
    }

    private static void appendUnused(List<Part> out, boolean[] used) {
        appendUnused(out, used, null);
    }

    /**
     * Place leftover cards under matching item sections (sourceItemId / primaryOutput).
     * Multi-select: never dump a trailing pile after all sections — attach or soft-match.
     */
    private static void appendUnused(List<Part> out, boolean[] used, List<RecipeCard> cards) {
        List<Integer> unused = new ArrayList<>();
        for (int i = 0; i < used.length; i++) {
            if (!used[i]) {
                unused.add(i);
            }
        }
        if (unused.isEmpty()) {
            return;
        }
        if (cards != null && distinctSectionKeyCount(cards) >= 2) {
            for (int idx : unused) {
                insertCardInSection(out, idx, cards);
                used[idx] = true;
            }
            return;
        }
        if (!out.isEmpty() && !out.get(out.size() - 1).isCard() && !out.get(out.size() - 1).isItem()) {
            Part last = out.remove(out.size() - 1);
            int src = indexOfSources(last.text());
            if (src >= 0) {
                String before = tidyChunk(last.text().substring(0, src), true, true);
                String after = last.text().substring(src).trim();
                if (!before.isEmpty()) {
                    out.add(Part.text(before));
                }
                for (int i : unused) {
                    out.add(Part.card(i));
                }
                if (!after.isEmpty()) {
                    out.add(Part.text(after));
                }
                return;
            }
            out.add(last);
        }
        for (int i : unused) {
            out.add(Part.card(i));
        }
    }

    /** Insert card after its item section (item → text → cards), before next item/sources. */
    private static void insertCardInSection(List<Part> out, int cardIdx, List<RecipeCard> cards) {
        if (cardIdx < 0 || cardIdx >= cards.size()) {
            return;
        }
        RecipeCard c = cards.get(cardIdx);
        String key = c == null || c.sectionKey() == null ? "" : c.sectionKey().toLowerCase(Locale.ROOT);
        int insertAt = -1;
        if (!key.isEmpty()) {
            for (int i = 0; i < out.size(); i++) {
                Part p = out.get(i);
                if (!p.isItem() || !key.equals(p.text())) {
                    continue;
                }
                insertAt = i + 1;
                while (insertAt < out.size()) {
                    Part n = out.get(insertAt);
                    if (n.isItem()) {
                        break;
                    }
                    if (n.kind == Kind.TEXT && indexOfSources(n.text()) == 0) {
                        break;
                    }
                    insertAt++;
                }
                break;
            }
        }
        if (insertAt < 0) {
            insertAt = out.size();
            for (int i = 0; i < out.size(); i++) {
                Part p = out.get(i);
                if (p.kind == Kind.TEXT && indexOfSources(p.text()) == 0) {
                    insertAt = i;
                    break;
                }
            }
        }
        out.add(Math.min(insertAt, out.size()), Part.card(cardIdx));
    }

    private static List<Part> expandItemMarkersInTextParts(List<Part> parts) {
        List<Part> out = new ArrayList<>();
        for (Part p : parts) {
            if (p.kind == Kind.TEXT) {
                out.addAll(splitItemsInText(p.text()));
            } else {
                out.add(p);
            }
        }
        return mergeAdjacentText(out);
    }

    private static List<Part> splitItemsOnly(String cleaned) {
        return mergeAdjacentText(splitItemsInText(cleaned));
    }

    private static List<Part> splitItemsInText(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<Part> out = new ArrayList<>();
        Matcher m = ITEM_MARKER.matcher(text);
        int last = 0;
        while (m.find()) {
            String before = text.substring(last, m.start());
            if (!before.isEmpty()) {
                out.add(Part.text(before));
            }
            String id = itemIdFromMatch(m);
            if (id != null && !id.isBlank()) {
                out.add(Part.item(normalizeRegistryRef(id), itemCountFromMatch(m)));
            }
            last = m.end();
        }
        String tail = text.substring(last);
        if (!tail.isEmpty()) {
            out.add(Part.text(tail));
        }
        if (out.isEmpty() && !text.isEmpty()) {
            out.add(Part.text(text));
        }
        return out;
    }

    private static List<Part> mergeAdjacentText(List<Part> parts) {
        List<Part> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (Part p : parts) {
            if (p.isCard() || p.isItem()) {
                flushText(out, buf);
                out.add(p);
            } else if (p.text() != null && !p.text().isEmpty()) {
                if (!buf.isEmpty()) {
                    buf.append('\n');
                }
                buf.append(p.text());
            }
        }
        flushText(out, buf);
        return out;
    }

    private static void flushText(List<Part> out, StringBuilder buf) {
        if (buf.isEmpty()) {
            return;
        }
        out.add(Part.text(buf.toString().trim()));
        buf.setLength(0);
    }

    private static int firstParagraphEnd(String main) {
        if (main == null || main.isEmpty()) {
            return 0;
        }
        int nn = main.indexOf("\n\n");
        if (nn >= 0) {
            return nn;
        }
        int n = main.indexOf('\n');
        if (n >= 0) {
            return n;
        }
        return main.length();
    }

    private static int indexOfSources(String text) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        Matcher m = SOURCES.matcher(text);
        return m.find() ? m.start() : -1;
    }

    private static String tidyChunk(String s, boolean trimStart, boolean trimEnd) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        // Belt: scrub PURPOSE tags if any slip past AskResult; strip orphan recipe marks.
        String t = AskReplyScrub.scrubPromptEcho(s);
        t = RecipeCardsMode.scrubMarker(t);
        t = ORPHAN_RECIPE_MARK.matcher(t).replaceAll("");
        t = t.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n");
        // Cheap: jam "foo 1. bar" → step on own line so UI can pad numbered steps.
        t = t.replaceAll("(?<=\\S)[ \\t]+(?=\\d+[.)][ \\t])", "\n");
        if (trimStart) {
            t = t.replaceAll("^\\s+", "");
        }
        if (trimEnd) {
            t = t.replaceAll("\\s+$", "");
        }
        return t;
    }
}
