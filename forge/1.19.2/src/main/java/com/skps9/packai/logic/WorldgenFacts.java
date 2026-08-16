package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Minecraft-free worldgen JSON parser. Missing fields are omitted — never invent Y / vein /
 * XYZ / wiki coords.
 */
public final class WorldgenFacts {
    public static final String HEADER = "[WORLDGEN]";

    public enum Kind {
        BIOME,
        STRUCTURE,
        STRUCTURE_SET,
        CONFIGURED,
        PLACED,
        TAG,
        MODIFIER
    }

    public record Biome(String id, List<String> placedFeatures) {}

    public record Structure(String id, String biomes) {}

    public record StructureSet(String id, List<String> structures, Integer spacing, Integer separation) {}

    public record Configured(String id, String type, Integer size) {}

    public record Placed(String id, String configuredId, Integer count, String countRange, String heightRange) {}

    public record Modifier(String id, String biomes, List<String> features) {}

    private WorldgenFacts() {}

    public static boolean looksLikeQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String t = query.toLowerCase(Locale.ROOT);
        return containsLatinToken(t, "biome") || containsLatinToken(t, "structure")
                || containsLatinToken(t, "village") || containsLatinToken(t, "mansion")
                || containsLatinToken(t, "stronghold") || containsLatinToken(t, "monument")
                || containsLatinToken(t, "ore") || containsLatinToken(t, "ores")
                || containsLatinToken(t, "vein") || containsLatinToken(t, "veins")
                || containsLatinToken(t, "geode") || containsLatinToken(t, "worldgen")
                || containsLatinToken(t, "spawn")
                || t.contains("生态") || t.contains("生態") || t.contains("群系")
                || t.contains("结构") || t.contains("結構") || t.contains("村庄") || t.contains("村莊")
                || t.contains("矿") || t.contains("礦") || t.contains("矿脉") || t.contains("礦脈")
                || t.contains("林地") || t.contains("要塞") || t.contains("古迹") || t.contains("古蹟")
                || t.contains("哪里挖") || t.contains("哪裡挖");
    }

    /** Latin token: not a substring of a longer letter-run (more/store/despawn). */
    static boolean containsLatinToken(String lower, String token) {
        if (lower == null || token == null || token.isEmpty()) {
            return false;
        }
        int from = 0;
        while (from <= lower.length() - token.length()) {
            int i = lower.indexOf(token, from);
            if (i < 0) {
                return false;
            }
            boolean leftOk = i == 0 || !isAsciiLetter(lower.charAt(i - 1));
            int end = i + token.length();
            boolean rightOk = end == lower.length() || !isAsciiLetter(lower.charAt(end));
            if (leftOk && rightOk) {
                return true;
            }
            from = i + 1;
        }
        return false;
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    public static String missLine(String query, String lang) {
        String q = query == null ? "" : query.trim();
        String code = lang == null ? "" : lang.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (code.isEmpty() || code.startsWith("zh")) {
            return HEADER + " 此包未索引到 " + q + " 的 worldgen";
        }
        return HEADER + " this pack has no indexed worldgen for: " + q;
    }

    public static Kind kindFromPath(String path) {
        String after = afterNamespace(path);
        if (after == null) {
            return null;
        }
        if (after.startsWith("tags/worldgen/") && after.endsWith(".json")) {
            return Kind.TAG;
        }
        if (after.startsWith("worldgen/structure_set/") && after.endsWith(".json")) {
            return Kind.STRUCTURE_SET;
        }
        if (after.startsWith("worldgen/structure/") && after.endsWith(".json")) {
            return Kind.STRUCTURE;
        }
        if (after.startsWith("worldgen/configured_feature/") && after.endsWith(".json")) {
            return Kind.CONFIGURED;
        }
        if (after.startsWith("worldgen/placed_feature/") && after.endsWith(".json")) {
            return Kind.PLACED;
        }
        if (after.startsWith("worldgen/biome/") && after.endsWith(".json")) {
            return Kind.BIOME;
        }
        if (isBiomeModifierAfter(after) && after.endsWith(".json")) {
            return Kind.MODIFIER;
        }
        return null;
    }

    public static boolean isWorldgenPath(String path) {
        return kindFromPath(path) != null;
    }

    /** {@code ns:path} or {@code #ns:path} for tags. Empty when path is not worldgen. */
    public static String idFromPath(String path) {
        Kind kind = kindFromPath(path);
        if (kind == null) {
            return "";
        }
        String after = afterNamespace(path);
        String ns = namespaceOf(path);
        if (after == null || ns.isEmpty()) {
            return "";
        }
        String stem;
        if (kind == Kind.TAG) {
            String rest = after.substring("tags/worldgen/".length());
            int slash = rest.indexOf('/');
            if (slash <= 0 || slash >= rest.length() - 1) {
                return "";
            }
            stem = rest.substring(slash + 1);
        } else if (kind == Kind.MODIFIER) {
            stem = modifierStem(after);
        } else {
            String folder = switch (kind) {
                case BIOME -> "worldgen/biome/";
                case STRUCTURE -> "worldgen/structure/";
                case STRUCTURE_SET -> "worldgen/structure_set/";
                case CONFIGURED -> "worldgen/configured_feature/";
                case PLACED -> "worldgen/placed_feature/";
                default -> "";
            };
            if (folder.isEmpty() || !after.startsWith(folder)) {
                return "";
            }
            stem = after.substring(folder.length());
        }
        if (!stem.endsWith(".json") || stem.length() <= 5) {
            return "";
        }
        stem = stem.substring(0, stem.length() - 5);
        if (stem.isEmpty()) {
            return "";
        }
        String id = ns + ":" + stem;
        return kind == Kind.TAG ? "#" + id : id;
    }

    public static void ingest(Store store, String dataPath, String json, boolean overwrite) {
        if (store == null || json == null || json.isBlank()) {
            return;
        }
        Kind kind = kindFromPath(dataPath);
        String id = idFromPath(dataPath);
        if (kind == null || id.isEmpty()) {
            return;
        }
        JsonObject root = parseObject(json);
        if (root == null) {
            return;
        }
        switch (kind) {
            case BIOME -> store.putBiome(id, parseBiomeFeatures(root), overwrite);
            case STRUCTURE -> store.putStructure(id, parseBiomesField(root), overwrite);
            case STRUCTURE_SET -> store.putStructureSet(parseStructureSet(id, root), overwrite);
            case CONFIGURED -> store.putConfigured(parseConfigured(id, root), overwrite);
            case PLACED -> store.putPlaced(parsePlaced(id, root), overwrite);
            case TAG -> store.putTag(id, parseTagValues(root), overwrite);
            case MODIFIER -> {
                Modifier m = parseModifier(id, root);
                if (m != null) {
                    store.putModifier(m, overwrite);
                }
            }
        }
    }

    static List<String> parseBiomeFeatures(JsonObject root) {
        List<String> out = new ArrayList<>();
        collectIds(root.get("features"), out, 64);
        return out;
    }

    static String parseBiomesField(JsonObject root) {
        if (root == null || !root.has("biomes")) {
            return null;
        }
        JsonElement el = root.get("biomes");
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            return normalizeId(el.getAsString());
        }
        if (el.isJsonArray()) {
            List<String> ids = new ArrayList<>();
            collectIds(el, ids, 32);
            return ids.isEmpty() ? null : String.join(",", ids);
        }
        return null;
    }

    static StructureSet parseStructureSet(String id, JsonObject root) {
        List<String> structs = new ArrayList<>();
        if (root.has("structures") && root.get("structures").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("structures")) {
                if (!el.isJsonObject()) {
                    continue;
                }
                String s = stringOrNull(el.getAsJsonObject(), "structure");
                if (s != null) {
                    structs.add(normalizeId(s));
                }
            }
        }
        Integer spacing = null;
        Integer separation = null;
        if (root.has("placement") && root.get("placement").isJsonObject()) {
            JsonObject p = root.getAsJsonObject("placement");
            spacing = intOrNull(p, "spacing");
            separation = intOrNull(p, "separation");
        }
        return new StructureSet(id, List.copyOf(structs), spacing, separation);
    }

    static Configured parseConfigured(String id, JsonObject root) {
        String type = stringOrNull(root, "type");
        Integer size = null;
        if (root.has("config") && root.get("config").isJsonObject()) {
            size = intOrNull(root.getAsJsonObject("config"), "size");
        }
        return new Configured(id, type, size);
    }

    static Placed parsePlaced(String id, JsonObject root) {
        String configured = null;
        if (root.has("feature") && root.get("feature").isJsonPrimitive()
                && root.get("feature").getAsJsonPrimitive().isString()) {
            configured = normalizeId(root.get("feature").getAsString());
        }
        Integer count = null;
        String countRange = null;
        String heightRange = null;
        if (root.has("placement") && root.get("placement").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("placement")) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject mod = el.getAsJsonObject();
                String type = stringOrNull(mod, "type");
                if (isShortType(type, "count") && mod.has("count")) {
                    CountVal cv = parseCountVal(mod.get("count"));
                    if (cv != null) {
                        count = cv.exact;
                        countRange = cv.range;
                    }
                } else if (isShortType(type, "height_range") && heightRange == null) {
                    heightRange = parseHeightRange(mod);
                }
            }
        }
        return new Placed(id, configured, count, countRange, heightRange);
    }

    static Modifier parseModifier(String id, JsonObject root) {
        String type = stringOrNull(root, "type");
        if (type == null || !type.toLowerCase(Locale.ROOT).contains("add_features")) {
            return null;
        }
        String biomes = parseBiomesField(root);
        if (biomes == null) {
            biomes = stringOrNull(root, "biomes");
            if (biomes != null) {
                biomes = normalizeId(biomes);
            }
        }
        List<String> features = new ArrayList<>();
        collectIds(root.get("features"), features, 32);
        if (biomes == null && features.isEmpty()) {
            return null;
        }
        return new Modifier(id, biomes, List.copyOf(features));
    }

    static List<String> parseTagValues(JsonObject root) {
        List<String> out = new ArrayList<>();
        if (root == null || !root.has("values") || !root.get("values").isJsonArray()) {
            return out;
        }
        for (JsonElement el : root.getAsJsonArray("values")) {
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                String s = normalizeId(el.getAsString());
                if (s != null) {
                    out.add(s);
                }
            } else if (el.isJsonObject()) {
                String s = stringOrNull(el.getAsJsonObject(), "id");
                if (s != null) {
                    out.add(normalizeId(s));
                }
            }
        }
        return out;
    }

    static boolean matches(String query, String id) {
        if (query == null || id == null) {
            return false;
        }
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) {
            return false;
        }
        String idn = id.toLowerCase(Locale.ROOT);
        if (idn.equals(q)) {
            return true;
        }
        String path = idn.startsWith("#") ? idn.substring(1) : idn;
        int colon = path.indexOf(':');
        String stem = colon >= 0 ? path.substring(colon + 1) : path;
        if (stem.equals(q) || idn.endsWith(":" + q) || stem.endsWith("/" + q)) {
            return true;
        }
        String last = lastSegment(stem);
        if (last.equals(q)) {
            return true;
        }
        String qh = q.replace('_', ' ').replace('-', ' ').replace('/', ' ').trim();
        String lastH = last.replace('_', ' ').replace('-', ' ');
        String stemH = stem.replace('/', ' ').replace('_', ' ').replace('-', ' ');
        if (qh.length() < 2) {
            return false;
        }
        if (lastH.equals(qh) || lastH.contains(qh) || (qh.contains(lastH) && lastH.length() >= 3)) {
            return true;
        }
        if (stemH.contains(qh)) {
            return true;
        }
        boolean any = false;
        for (String tok : qh.split("\\s+")) {
            if (tok.length() < 2) {
                continue;
            }
            any = true;
            if (!stemH.contains(tok) && !last.contains(tok) && !idn.contains(tok)) {
                return false;
            }
        }
        return any;
    }

    static String lastSegment(String stem) {
        if (stem == null || stem.isEmpty()) {
            return "";
        }
        int slash = stem.lastIndexOf('/');
        return slash >= 0 ? stem.substring(slash + 1) : stem;
    }

    static String formatBiome(Biome b) {
        StringBuilder sb = new StringBuilder(HEADER).append(" biome ").append(b.id());
        if (b.placedFeatures() != null && !b.placedFeatures().isEmpty()) {
            sb.append(" placed_feature=").append(joinCap(b.placedFeatures(), 12));
        }
        return sb.toString();
    }

    static String formatStructure(Structure s) {
        StringBuilder sb = new StringBuilder(HEADER).append(" structure ").append(s.id());
        if (s.biomes() != null && !s.biomes().isBlank()) {
            sb.append(" biomes=").append(s.biomes());
        }
        return sb.toString();
    }

    static String formatStructureSet(StructureSet s) {
        StringBuilder sb = new StringBuilder(HEADER).append(" structure_set ").append(s.id());
        if (s.structures() != null && !s.structures().isEmpty()) {
            sb.append(" contains=").append(joinCap(s.structures(), 12));
        }
        if (s.spacing() != null) {
            sb.append(" spacing=").append(s.spacing());
        }
        if (s.separation() != null) {
            sb.append(" separation=").append(s.separation());
        }
        return sb.toString();
    }

    static String formatConfigured(Configured c) {
        StringBuilder sb = new StringBuilder(HEADER).append(" configured_feature ").append(c.id());
        if (c.type() != null && !c.type().isBlank()) {
            sb.append(" type=").append(c.type());
        }
        if (c.size() != null) {
            sb.append(" size=").append(c.size());
        }
        return sb.toString();
    }

    static String formatPlaced(Placed p) {
        StringBuilder sb = new StringBuilder(HEADER).append(" placed_feature ").append(p.id());
        if (p.configuredId() != null && !p.configuredId().isBlank()) {
            sb.append(" configured=").append(p.configuredId());
        }
        if (p.count() != null) {
            sb.append(" count=").append(p.count());
        } else if (p.countRange() != null && !p.countRange().isBlank()) {
            sb.append(" count=").append(p.countRange());
        }
        if (p.heightRange() != null && !p.heightRange().isBlank()) {
            sb.append(" height_range=").append(p.heightRange());
        }
        return sb.toString();
    }

    static String formatModifier(Modifier m) {
        StringBuilder sb = new StringBuilder(HEADER).append(" biome_modifier");
        if (m.biomes() != null && !m.biomes().isBlank()) {
            sb.append(" biomes=").append(m.biomes());
        }
        if (m.features() != null && !m.features().isEmpty()) {
            sb.append(" features=").append(joinCap(m.features(), 12));
        }
        return sb.toString();
    }

    static String formatTag(String id, List<String> values) {
        StringBuilder sb = new StringBuilder(HEADER).append(" tag ").append(id);
        if (values != null && !values.isEmpty()) {
            sb.append(" = ").append(joinCap(values, 16));
        }
        return sb.toString();
    }

    public static final class Store {
        final Map<String, Biome> biomes = new LinkedHashMap<>();
        final Map<String, Structure> structures = new LinkedHashMap<>();
        final Map<String, StructureSet> structureSets = new LinkedHashMap<>();
        final Map<String, Configured> configured = new LinkedHashMap<>();
        final Map<String, Placed> placed = new LinkedHashMap<>();
        final Map<String, Modifier> modifiers = new LinkedHashMap<>();
        final Map<String, List<String>> tags = new LinkedHashMap<>();

        void putBiome(String id, List<String> features, boolean overwrite) {
            if (id == null || id.isEmpty()) {
                return;
            }
            if (!overwrite && biomes.containsKey(id)) {
                return;
            }
            biomes.put(id, new Biome(id, List.copyOf(features == null ? List.of() : features)));
        }

        void putStructure(String id, String biomesField, boolean overwrite) {
            if (id == null || id.isEmpty()) {
                return;
            }
            if (!overwrite && structures.containsKey(id)) {
                return;
            }
            structures.put(id, new Structure(id, biomesField));
        }

        void putStructureSet(StructureSet s, boolean overwrite) {
            if (s == null || s.id() == null) {
                return;
            }
            if (!overwrite && structureSets.containsKey(s.id())) {
                return;
            }
            structureSets.put(s.id(), s);
        }

        void putConfigured(Configured c, boolean overwrite) {
            if (c == null || c.id() == null) {
                return;
            }
            if (!overwrite && configured.containsKey(c.id())) {
                return;
            }
            configured.put(c.id(), c);
        }

        void putPlaced(Placed p, boolean overwrite) {
            if (p == null || p.id() == null) {
                return;
            }
            if (!overwrite && placed.containsKey(p.id())) {
                return;
            }
            placed.put(p.id(), p);
        }

        void putModifier(Modifier m, boolean overwrite) {
            if (m == null || m.id() == null) {
                return;
            }
            if (!overwrite && modifiers.containsKey(m.id())) {
                return;
            }
            modifiers.put(m.id(), m);
        }

        void putTag(String id, List<String> values, boolean overwrite) {
            if (id == null || id.isEmpty()) {
                return;
            }
            if (!overwrite && tags.containsKey(id)) {
                return;
            }
            tags.put(id, List.copyOf(values == null ? List.of() : values));
        }

        public List<String> formatMatches(String query, int maxLines) {
            LinkedHashSet<String> lines = new LinkedHashSet<>();
            Set<String> hitBiomes = new LinkedHashSet<>();
            Set<String> hitPlaced = new LinkedHashSet<>();
            Set<String> hitConfigured = new LinkedHashSet<>();
            Set<String> hitStructures = new LinkedHashSet<>();
            Set<String> hitTags = new LinkedHashSet<>();

            for (Biome b : biomes.values()) {
                if (matches(query, b.id())) {
                    lines.add(formatBiome(b));
                    hitBiomes.add(b.id());
                    hitPlaced.addAll(b.placedFeatures());
                }
            }
            for (Structure s : structures.values()) {
                if (matches(query, s.id())) {
                    lines.add(formatStructure(s));
                    hitStructures.add(s.id());
                    if (s.biomes() != null && s.biomes().startsWith("#")) {
                        hitTags.add(s.biomes());
                    }
                }
            }
            for (StructureSet s : structureSets.values()) {
                if (matches(query, s.id())) {
                    lines.add(formatStructureSet(s));
                    hitStructures.addAll(s.structures());
                }
            }
            for (Configured c : configured.values()) {
                if (matches(query, c.id())) {
                    lines.add(formatConfigured(c));
                    hitConfigured.add(c.id());
                }
            }
            for (Placed p : placed.values()) {
                if (matches(query, p.id())
                        || (p.configuredId() != null && matches(query, p.configuredId()))) {
                    lines.add(formatPlaced(p));
                    hitPlaced.add(p.id());
                    if (p.configuredId() != null) {
                        hitConfigured.add(p.configuredId());
                    }
                }
            }
            for (Modifier m : modifiers.values()) {
                if ((m.biomes() != null && matches(query, m.biomes()))
                        || featureListMatches(m.features(), query)
                        || matches(query, m.id())) {
                    lines.add(formatModifier(m));
                    if (m.features() != null) {
                        hitPlaced.addAll(m.features());
                    }
                }
            }
            for (Map.Entry<String, List<String>> e : tags.entrySet()) {
                if (matches(query, e.getKey()) || idListMatches(e.getValue(), query)) {
                    lines.add(formatTag(e.getKey(), e.getValue()));
                    hitTags.add(e.getKey());
                }
            }

            for (String biomeId : hitBiomes) {
                for (Structure s : structures.values()) {
                    if (structureTouchesBiome(s, biomeId)) {
                        lines.add(formatStructure(s));
                    }
                }
                for (Modifier m : modifiers.values()) {
                    if (biomeId.equals(m.biomes())
                            || (m.biomes() != null && m.biomes().startsWith("#")
                                    && tagContains(m.biomes(), biomeId))) {
                        lines.add(formatModifier(m));
                    }
                }
            }
            for (String sid : hitStructures) {
                Structure s = structures.get(sid);
                if (s != null) {
                    lines.add(formatStructure(s));
                    if (s.biomes() != null && s.biomes().startsWith("#") && tags.containsKey(s.biomes())) {
                        lines.add(formatTag(s.biomes(), tags.get(s.biomes())));
                    }
                }
                for (StructureSet set : structureSets.values()) {
                    if (set.structures().contains(sid)) {
                        lines.add(formatStructureSet(set));
                    }
                }
            }
            for (String pf : hitPlaced) {
                Placed p = placed.get(pf);
                if (p != null) {
                    lines.add(formatPlaced(p));
                }
                for (Biome b : biomes.values()) {
                    if (b.placedFeatures().contains(pf)) {
                        lines.add(HEADER + " placed_feature " + pf + " in biome " + b.id());
                    }
                }
                for (Modifier m : modifiers.values()) {
                    if (m.features() != null && m.features().contains(pf) && m.biomes() != null) {
                        lines.add(HEADER + " placed_feature " + pf + " in biome " + m.biomes());
                    }
                }
            }
            for (String cid : hitConfigured) {
                Configured c = configured.get(cid);
                if (c != null) {
                    lines.add(formatConfigured(c));
                }
            }
            for (String tagId : hitTags) {
                List<String> vals = tags.get(tagId);
                if (vals != null) {
                    lines.add(formatTag(tagId, vals));
                }
            }

            List<String> out = new ArrayList<>();
            for (String line : lines) {
                if (out.size() >= maxLines) {
                    break;
                }
                out.add(line);
            }
            return out;
        }

        private boolean structureTouchesBiome(Structure s, String biomeId) {
            if (s.biomes() == null) {
                return false;
            }
            if (s.biomes().equals(biomeId)) {
                return true;
            }
            if (s.biomes().contains(",")) {
                for (String part : s.biomes().split(",")) {
                    if (biomeId.equals(part.trim())) {
                        return true;
                    }
                }
            }
            return s.biomes().startsWith("#") && tagContains(s.biomes(), biomeId);
        }

        private boolean tagContains(String tagId, String member) {
            List<String> vals = tags.get(tagId);
            return vals != null && vals.contains(member);
        }
    }

    private static boolean featureListMatches(List<String> features, String query) {
        return idListMatches(features, query);
    }

    private static boolean idListMatches(List<String> ids, String query) {
        if (ids == null) {
            return false;
        }
        for (String id : ids) {
            if (matches(query, id)) {
                return true;
            }
        }
        return false;
    }

    private static String joinCap(List<String> ids, int cap) {
        int n = Math.min(ids.size(), cap);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    private record CountVal(Integer exact, String range) {}

    private static CountVal parseCountVal(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return null;
        }
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
            return new CountVal(el.getAsInt(), null);
        }
        if (!el.isJsonObject()) {
            return null;
        }
        JsonObject o = el.getAsJsonObject();
        JsonObject src = o;
        if (o.has("value") && o.get("value").isJsonObject()) {
            src = o.getAsJsonObject("value");
        }
        Integer min = intOrNull(src, "min_inclusive");
        Integer max = intOrNull(src, "max_inclusive");
        if (min != null && max != null) {
            return new CountVal(null, min + ".." + max);
        }
        if (min != null) {
            return new CountVal(min, null);
        }
        if (max != null) {
            return new CountVal(max, null);
        }
        return null;
    }

    private static String parseHeightRange(JsonObject placementMod) {
        if (!placementMod.has("height") || !placementMod.get("height").isJsonObject()) {
            return null;
        }
        JsonObject h = placementMod.getAsJsonObject("height");
        String min = verticalAnchor(h.get("min_inclusive"));
        String max = verticalAnchor(h.get("max_inclusive"));
        if (min == null && max == null) {
            return null;
        }
        if (min != null && max != null) {
            return min + ".." + max;
        }
        return min != null ? min : max;
    }

    private static String verticalAnchor(JsonElement el) {
        if (el == null || !el.isJsonObject()) {
            return null;
        }
        JsonObject o = el.getAsJsonObject();
        if (isNumber(o.get("absolute"))) {
            return "absolute " + num(o.get("absolute"));
        }
        if (isNumber(o.get("above_bottom"))) {
            return "above_bottom " + num(o.get("above_bottom"));
        }
        if (isNumber(o.get("below_top"))) {
            return "below_top " + num(o.get("below_top"));
        }
        return null;
    }

    private static void collectIds(JsonElement el, List<String> out, int cap) {
        if (el == null || el.isJsonNull() || out.size() >= cap) {
            return;
        }
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            String s = normalizeId(el.getAsString());
            if (s != null && !out.contains(s)) {
                out.add(s);
            }
            return;
        }
        if (el.isJsonArray()) {
            for (JsonElement x : el.getAsJsonArray()) {
                collectIds(x, out, cap);
                if (out.size() >= cap) {
                    return;
                }
            }
            return;
        }
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("feature")) {
                collectIds(o.get("feature"), out, cap);
            } else if (o.has("id") && !o.has("type")) {
                collectIds(o.get("id"), out, cap);
            }
        }
    }

    static String normalizeId(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        boolean tag = s.startsWith("#");
        if (tag) {
            s = s.substring(1);
        }
        s = s.toLowerCase(Locale.ROOT);
        return tag ? "#" + s : s;
    }

    private static boolean isShortType(String type, String want) {
        if (type == null || want == null) {
            return false;
        }
        String t = type.toLowerCase(Locale.ROOT);
        int colon = t.indexOf(':');
        String shortT = colon >= 0 ? t.substring(colon + 1) : t;
        return want.equals(shortT);
    }

    private static JsonObject parseObject(String json) {
        try {
            JsonElement el = JsonParser.parseString(json);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String stringOrNull(JsonObject o, String key) {
        if (o == null || !o.has(key) || !o.get(key).isJsonPrimitive()
                || !o.get(key).getAsJsonPrimitive().isString()) {
            return null;
        }
        String s = o.get(key).getAsString();
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static Integer intOrNull(JsonObject o, String key) {
        if (o == null || !isNumber(o.get(key))) {
            return null;
        }
        return o.get(key).getAsInt();
    }

    private static boolean isNumber(JsonElement el) {
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber();
    }

    private static String num(JsonElement el) {
        if (el.getAsJsonPrimitive().getAsNumber() instanceof Integer
                || el.getAsDouble() == el.getAsInt()) {
            return Integer.toString(el.getAsInt());
        }
        return el.getAsNumber().toString();
    }

    private static String afterNamespace(String path) {
        String rest = dataRest(path);
        if (rest == null) {
            return null;
        }
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash >= rest.length() - 1) {
            return null;
        }
        return rest.substring(slash + 1);
    }

    private static String namespaceOf(String path) {
        String rest = dataRest(path);
        if (rest == null) {
            return "";
        }
        int slash = rest.indexOf('/');
        return slash <= 0 ? "" : rest.substring(0, slash);
    }

    private static String dataRest(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String p = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        int data = p.startsWith("data/") ? 0 : p.indexOf("/data/");
        if (data < 0) {
            return null;
        }
        int start = data == 0 ? "data/".length() : data + "/data/".length();
        if (start >= p.length()) {
            return null;
        }
        return p.substring(start);
    }

    private static boolean isBiomeModifierAfter(String after) {
        return after.startsWith("forge/biome_modifier/")
                || after.startsWith("neoforge/biome_modifier/")
                || after.startsWith("biome_modifier/")
                || after.startsWith("biome_modifiers/");
    }

    private static String modifierStem(String after) {
        String[] prefixes = {
            "forge/biome_modifier/",
            "neoforge/biome_modifier/",
            "biome_modifier/",
            "biome_modifiers/"
        };
        for (String pre : prefixes) {
            if (after.startsWith(pre)) {
                return after.substring(pre.length());
            }
        }
        return "";
    }
}
