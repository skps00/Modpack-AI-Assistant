package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.world.item.ItemStack;

/**
 * Official in-game display names for Ask facts / prompts.
 * Never invents a name by splitting registry path tokens (that caused 「暗鋼閃電」).
 */
public final class OfficialDisplay {
    static final String NO_OFFICIAL = "（無官方名）";
    static final String PEER_HEADER = "同 tag 其他成員:";
    /**
     * One or more {@code ns:path} colon segments so {@code item:mod:id} is one hit;
     * {@link #resolveAnnotatable} then picks the longest valid suffix.
     */
    private static final Pattern ITEM_ID = Pattern.compile(
            "#?[a-z0-9_]+(?::[a-z0-9_./-]+)+", Pattern.CASE_INSENSITIVE);
    static final int PEER_CAP = 8;
    /** Fact-line labels / junk tokens, not mod ids (P4). */
    private static final Set<String> NS_DENY = Set.of(
            "item",
            "source",
            "tier",
            "note",
            "file",
            "mechanic",
            "count",
            "via",
            "held",
            "gets",
            "entity",
            "table",
            "gateway",
            "structure",
            "dimension",
            "from",
            "src",
            "rel");
    /**
     * Raw translation-key shaped labels (e.g. {@code item.chestcavity.cud}) are not real names.
     * Prefix list case-insensitive; require at least one further dot-separated segment.
     */
    private static final Pattern TRANSLATION_KEY = Pattern.compile(
            "(?i)^(item|block|entity|fluid|itemgroup|gui|tooltip|jei|packai|effect|enchantment"
                    + "|biome|dimension|attribute|advancement|death|container|subtitles|key|options"
                    + "|stat|commands|gamerule|tutorial|resourcepack|pack|screen|menu|narration|chat"
                    + "|mount|painting|particle|sound|recipe|instrument|banner_pattern|cat_variant"
                    + "|frog_variant|wolf_variant)\\.[a-z0-9_.]+$");

    /** Test hook — default = hover name via {@link ItemResolver#stackFromId}. */
    static volatile Function<String, String> lookup = OfficialDisplay::hoverLookup;

    private OfficialDisplay() {}

    static void resetLookup() {
        lookup = OfficialDisplay::hoverLookup;
    }

    private static String hoverLookup(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        try {
            String bare = stripHash(id);
            ItemStack stack = ItemResolver.stackFromId(bare);
            if (stack == null || stack.isEmpty()) {
                return "";
            }
            return Plainify.stripMcFormat(stack.getHoverName().getString()).trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Official hover / lang name, or empty when unresolved. Never path-token invents. */
    public static String officialName(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        String key = stripHash(id).toLowerCase(Locale.ROOT).trim();
        if (key.isEmpty()) {
            return "";
        }
        String n = lookup.apply(key);
        if (n == null) {
            return "";
        }
        n = n.trim();
        if (n.isEmpty() || isTranslationKeyShaped(n)) {
            return "";
        }
        return n;
    }

    /**
     * True when label has no whitespace, no CJK, and matches a known translation-key prefix.
     * Used so {@code item.foo.bar} is not treated as an official display name.
     */
    static boolean isTranslationKeyShaped(String label) {
        if (label == null || label.isEmpty()) {
            return false;
        }
        for (int i = 0; i < label.length(); i++) {
            int cp = label.codePointAt(i);
            if (Character.isWhitespace(cp) || isCjk(cp)) {
                return false;
            }
            if (Character.charCount(cp) > 1) {
                i++;
            }
        }
        return TRANSLATION_KEY.matcher(label).matches();
    }

    private static boolean isCjk(int cp) {
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    /**
     * Facts form: {@code 官方名（ns:id）} when known; {@code ns:id（無官方名）} when not.
     * Tags ({@code #ns:path}) stay raw.
     * Note: {@code NO_OFFICIAL} branch is now test-only — production {@link #annotate}
     * / {@link #collectPeers} skip empty officialName at call sites (P5/P5b).
     */
    public static String labeled(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        String raw = id.trim();
        if (raw.startsWith("#")) {
            return raw.toLowerCase(Locale.ROOT);
        }
        String bare = raw.toLowerCase(Locale.ROOT);
        String name = officialName(bare);
        if (name.isEmpty()) {
            return bare + NO_OFFICIAL;
        }
        return name + "（" + bare + "）";
    }

    /** Rewrite annotatable registry ids in text with {@link #labeled}. */
    public static String annotate(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        Matcher m = ITEM_ID.matcher(text);
        StringBuilder sb = new StringBuilder(text.length() + 32);
        int last = 0;
        while (m.find()) {
            String hit = m.group();
            if (hit.startsWith("#") || insideItemMarker(text, m.start())) {
                continue;
            }
            ResolvedId resolved = resolveAnnotatable(hit, m.start());
            if (resolved == null || alreadyLabeled(text, resolved.absStart, resolved.absEnd)) {
                continue;
            }
            // P5b: no official name → leave original token untouched (do not emit NO_OFFICIAL).
            if (!hasOfficialName(resolved.id)) {
                continue;
            }
            sb.append(text, last, resolved.absStart);
            sb.append(labeled(resolved.id));
            last = resolved.absEnd;
        }
        sb.append(text, last, text.length());
        return sb.toString();
    }

    /**
     * Annotate ids in mechanic facts; append a peer section for non-focus item ids
     * (same tag / same script / same quest clip) so they are not mixed into the ask target.
     */
    public static List<String> enrichFacts(List<String> facts, String focusId) {
        if (facts == null || facts.isEmpty()) {
            return List.of();
        }
        String want = focusId == null ? "" : stripHash(focusId).toLowerCase(Locale.ROOT).trim();
        LinkedHashSet<String> peers = new LinkedHashSet<>();
        List<String> out = new ArrayList<>(facts.size() + 1);
        for (String fact : facts) {
            if (fact == null || fact.isBlank()) {
                continue;
            }
            collectPeers(fact, want, peers);
            out.add(annotate(fact));
        }
        if (!peers.isEmpty()) {
            StringBuilder peerLine = new StringBuilder(PEER_HEADER);
            int n = 0;
            for (String p : peers) {
                if (n >= PEER_CAP) {
                    break;
                }
                peerLine.append(' ').append(labeled(p)).append(';');
                n++;
            }
            out.add(peerLine.toString());
        }
        return List.copyOf(out);
    }

    private static void collectPeers(String text, String want, LinkedHashSet<String> peers) {
        Matcher m = ITEM_ID.matcher(text);
        while (m.find()) {
            if (insideItemMarker(text, m.start())) {
                continue;
            }
            ResolvedId resolved = resolveAnnotatable(m.group(), m.start());
            if (resolved == null) {
                continue;
            }
            String bare = stripHash(resolved.id).toLowerCase(Locale.ROOT);
            if (bare.isEmpty() || bare.equals(want)) {
                continue;
            }
            // P5: only peer ids with a real official name (same predicate as annotate).
            if (!hasOfficialName(bare)) {
                continue;
            }
            peers.add(bare);
        }
    }

    /** Shared P5/P5b gate for annotate + collectPeers. */
    private static boolean hasOfficialName(String id) {
        return !officialName(id).isEmpty();
    }

    /**
     * Longest annotatable {@code ns:path} suffix of a multi-colon hit.
     * Fact labels ({@code item}/{@code source}/…) are refused as namespace.
     */
    private static ResolvedId resolveAnnotatable(String hit, int matchStart) {
        if (hit == null || hit.isBlank() || hit.startsWith("#")) {
            return null;
        }
        String bare = hit.toLowerCase(Locale.ROOT);
        String[] segs = bare.split(":", -1);
        if (segs.length < 2) {
            return null;
        }
        // Always the longest trailing ns:path (skips leading fact labels like item:).
        int startSeg = segs.length - 2;
        String ns = segs[startSeg];
        String path = segs[startSeg + 1];
        String id = ns + ":" + path;
        if (!annotatable(id)) {
            return null;
        }
        int localStart = 0;
        for (int i = 0; i < startSeg; i++) {
            localStart += segs[i].length() + 1;
        }
        return new ResolvedId(id, matchStart + localStart, matchStart + hit.length());
    }

    private static boolean annotatableNamespace(String ns) {
        if (ns == null || ns.isEmpty()) {
            return false;
        }
        return !NS_DENY.contains(ns);
    }

    private static boolean annotatable(String id) {
        if (id == null || id.isBlank() || id.startsWith("#")) {
            return false;
        }
        String bare = id.toLowerCase(Locale.ROOT);
        int colon = bare.indexOf(':');
        if (colon <= 0 || colon >= bare.length() - 1) {
            return false;
        }
        String ns = bare.substring(0, colon);
        String path = bare.substring(colon + 1);
        if (!annotatableNamespace(ns)) {
            return false;
        }
        // P3: namespace segment must not contain '/'.
        if (ns.contains("/")) {
            return false;
        }
        // P2: id always has exactly two segments by construction, but loop kept so the
        // file-extension ban on every :-separated segment stays explicit.
        for (String seg : bare.split(":", -1)) {
            if (seg.contains(".js") || seg.contains(".snbt") || seg.contains(".json")) {
                return false;
            }
        }
        if (path.startsWith("kubejs/") || path.startsWith("ftbquests/")) {
            return false;
        }
        // Consume path only until whitespace / ( / （ / , — path itself has no those chars
        // because ITEM_ID already stops; still reject empty.
        return !path.isEmpty();
    }

    /** Skip ids inside {@code {{item:…}}} / {@code [[item:…]]} UI markers. */
    private static boolean insideItemMarker(String text, int start) {
        if (text == null || start <= 0) {
            return false;
        }
        // Look back a short window for {{ or [[ before this match.
        int from = Math.max(0, start - 8);
        String pre = text.substring(from, start);
        return pre.contains("{{") || pre.contains("[[") || pre.endsWith("{") || pre.endsWith("[");
    }

    /** Skip if already inside a fullwidth paren label …（ns:id）. */
    private static boolean alreadyLabeled(String text, int start, int end) {
        return (start > 0 && text.charAt(start - 1) == '（')
                || (end < text.length() && text.charAt(end) == '）');
    }

    private static String stripHash(String id) {
        if (id == null) {
            return "";
        }
        String s = id.trim();
        return s.startsWith("#") ? s.substring(1) : s;
    }

    private record ResolvedId(String id, int absStart, int absEnd) {}
}
