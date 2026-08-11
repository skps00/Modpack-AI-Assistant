package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Resolve registry ids from text; disambiguate same display names.
 */
public final class ItemResolver {
    private static final Pattern ID = Pattern.compile(
            "\\b([a-z0-9_]+:[a-z0-9_./-]+)\\b", Pattern.CASE_INSENSITIVE);
    /**
     * Hidden LLM marker. Each entry is {@code mod:id} or {@code mod:id|顯示名稱}
     * (name distinguishes SlashBlade-style same-id variants).
     */
    private static final Pattern MARKER = Pattern.compile(
            "<!--\\s*packai:items=([^>]+)\\s*-->", Pattern.CASE_INSENSITIVE);

    private ItemResolver() {}

    public record ResolvedItem(String id, String displayName, boolean ambiguous) {}

    /** Strip hidden item marker from answer shown to player. */
    public static String stripMarker(String answer) {
        if (answer == null) {
            return "";
        }
        return MARKER.matcher(answer).replaceAll("").trim();
    }

    /**
     * Parse suggestion refs from marker + inline {@code mod:id} in answer.
     * Each ref is {@code mod:id} or {@code mod:id|顯示名}.
     * <p>
     * Dedupe by registry id: prefer {@code id|name} over bare {@code id}; keep distinct
     * named variants (SlashBlade-style). Bare id scan runs on marker-stripped prose so the
     * marker body does not re-add the same id.
     */
    public static List<String> extractIds(String answer) {
        LinkedHashMap<String, String> refs = new LinkedHashMap<>();
        if (answer == null) {
            return List.of();
        }
        Matcher mm = MARKER.matcher(answer);
        if (mm.find()) {
            for (String part : mm.group(1).split("[,;]+")) {
                addSuggestionRef(refs, normalizeRef(part));
            }
        }
        // ponytail: scan prose only — marker text itself matches ID regex and duplicated chips.
        String prose = MARKER.matcher(answer).replaceAll(" ");
        Matcher im = ID.matcher(prose);
        while (im.find()) {
            String cand = im.group(1).toLowerCase(Locale.ROOT);
            if (isValidId(cand) && Registry.ITEM.containsKey(new ResourceLocation(cand))) {
                addSuggestionRef(refs, cand);
            }
        }
        return List.copyOf(refs.values());
    }

    /**
     * Insert suggestion ref; named variants keyed {@code id|name}, bare id skipped when any
     * same-id entry already present.
     */
    static void addSuggestionRef(LinkedHashMap<String, String> refs, String ref) {
        if (ref == null || ref.isBlank() || refs == null) {
            return;
        }
        String id = idPart(ref);
        if (id.isEmpty()) {
            return;
        }
        String name = namePart(ref);
        if (name != null) {
            refs.remove(id);
            String key = id + "|" + name;
            refs.putIfAbsent(key, id + "|" + name);
            return;
        }
        for (String existing : refs.values()) {
            if (id.equals(idPart(existing))) {
                return;
            }
        }
        refs.put(id, id);
    }

    /** Registry id part of a suggestion ref. */
    public static String idPart(String ref) {
        if (ref == null || ref.isBlank()) {
            return "";
        }
        int bar = ref.indexOf('|');
        String id = bar < 0 ? ref.trim() : ref.substring(0, bar).trim();
        return id.toLowerCase(Locale.ROOT);
    }

    /** Optional display-name hint after {@code |}, or null. */
    public static String namePart(String ref) {
        if (ref == null) {
            return null;
        }
        int bar = ref.indexOf('|');
        if (bar < 0 || bar >= ref.length() - 1) {
            return null;
        }
        String name = ref.substring(bar + 1).trim();
        return name.isEmpty() ? null : name;
    }

    public static List<ResolvedItem> resolveIds(List<String> ids) {
        List<ResolvedItem> out = new ArrayList<>();
        Map<String, Integer> nameCounts = new LinkedHashMap<>();
        for (String ref : ids) {
            ItemStack stack = stackFromId(idPart(ref));
            if (stack.isEmpty()) {
                continue;
            }
            String name = namePart(ref);
            if (name == null || name.isBlank()) {
                name = stack.getHoverName().getString();
            }
            nameCounts.merge(name, 1, Integer::sum);
        }
        for (String ref : ids) {
            String id = idPart(ref);
            ItemStack stack = stackFromId(id);
            if (stack.isEmpty()) {
                continue;
            }
            String name = namePart(ref);
            if (name == null || name.isBlank()) {
                name = stack.getHoverName().getString();
            }
            boolean amb = nameCounts.getOrDefault(name, 0) > 1;
            out.add(new ResolvedItem(id, name, amb));
        }
        return out;
    }

    /** First item id mentioned in question, or empty. */
    public static Optional<String> idInQuestion(String question) {
        if (question == null) {
            return Optional.empty();
        }
        Matcher m = ID.matcher(question);
        while (m.find()) {
            String cand = m.group(1).toLowerCase(Locale.ROOT);
            if (isValidId(cand) && Registry.ITEM.containsKey(new ResourceLocation(cand))) {
                return Optional.of(cand);
            }
        }
        return Optional.empty();
    }

    public static ItemStack stackFromId(String id) {
        if (id == null || id.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            String raw = id.trim();
            String bare = raw;
            String snbt = null;
            int brace = raw.indexOf('{');
            if (brace > 0) {
                bare = raw.substring(0, brace).trim();
                snbt = raw.substring(brace).trim();
            }
            ResourceLocation rl = new ResourceLocation(bare);
            Item item = Registry.ITEM.get(rl);
            if (item == null || item == Items.AIR) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = new ItemStack(item);
            if (snbt != null && !snbt.isEmpty()) {
                try {
                    stack.setTag(net.minecraft.nbt.TagParser.parseTag(snbt));
                } catch (Exception ignored) {
                    // keep bare stack when SNBT invalid
                }
            }
            return stack;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /** Registry id without trailing flat SNBT ({@code id{…}} → {@code id}). */
    public static String bareRegistryId(String embedRef) {
        if (embedRef == null || embedRef.isBlank()) {
            return "";
        }
        String raw = embedRef.trim();
        int brace = raw.indexOf('{');
        if (brace > 0) {
            raw = raw.substring(0, brace);
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    /**
     * Prefer InvPick/pin sample (keeps NBT). Bare {@link #stackFromId} loses Weapon Master /
     * SlashBlade stats — only use when no sample was stored.
     */
    public static ItemStack stackFromRef(ItemRef ref) {
        if (ref == null || !ref.isPresent()) {
            return ItemStack.EMPTY;
        }
        if (ref.hasSample()) {
            return ref.sample().copy();
        }
        return stackFromId(ref.id());
    }

    private static String normalizeRef(String raw) {
        if (raw == null) {
            return null;
        }
        String part = raw.trim();
        if (part.isEmpty()) {
            return null;
        }
        int bar = part.indexOf('|');
        String id = (bar < 0 ? part : part.substring(0, bar)).trim().toLowerCase(Locale.ROOT);
        String name = bar < 0 ? null : part.substring(bar + 1).trim();
        if (!isValidId(id)) {
            return null;
        }
        if (!Registry.ITEM.containsKey(new ResourceLocation(id))) {
            return null;
        }
        if (name == null || name.isEmpty()) {
            return id;
        }
        return id + "|" + name;
    }

    private static boolean isValidId(String s) {
        return s != null && s.contains(":") && !s.contains(" ") && !s.contains("|");
    }
}
