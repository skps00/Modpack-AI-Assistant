package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Pure Tetra schematic JSON → PURPOSE ({@code [SCROLL_UNLOCK]} / {@code [SCROLL_MATERIALS]}).
 * Folder refs ({@code ns:category/}) expanded to pick-one {@code install_items} by {@link TetraSchematicLookup}.
 * Mirrored by {@code tests/check_tetra_schematic_facts.py}.
 */
public final class TetraSchematicText {
    public static final String SCROLL_UNLOCK_HEADER = "[SCROLL_UNLOCK]";
    public static final String SCROLL_MATERIALS_HEADER = "[SCROLL_MATERIALS]";

    static final int MAX_RESOURCES = 4;
    static final int MAX_UNLOCK_LINES = 10;
    static final int MAX_MATERIAL_LINES = 8;
    static final int MAX_MATS = 4;
    static final int MAX_EFFECT_TOKENS = 6;
    /**
     * Cap unique install item examples under folder materials (OR / pick one).
     * Overflow keeps folder ref: {@code … +N more in tetra:battery/}.
     */
    static final int MAX_INSTALL_ITEMS = 8;
    /** Prefix: alternatives — workbench accepts any one, not the whole list. */
    public static final String INSTALL_ITEMS_PREFIX = "install_items (pick one / 任選其一): ";
    /** PURPOSE line when outcomes need no workbench material. */
    public static final String NONE_MATERIALS_LINE = "none (no material required)";
    /** RecipeExtra.softId for title-only none strip (UI draws label, not gas blob). */
    public static final String MATERIAL_NONE_SOFT_ID = "packai:label";

    /** Parsed {@code mod:id} + count from install_items / outcome material lines. */
    public record InstallItemSpec(String id, int count) {
        public InstallItemSpec {
            id = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
            count = Math.max(1, count);
        }

        public boolean isPresent() {
            return !id.isBlank();
        }
    }

    private TetraSchematicText() {}

    /** Path after optional {@code ns:} (e.g. {@code hone/gild_1}, {@code energy_bottle}). */
    static String keyPath(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return "";
        }
        String s = rawKey.trim();
        int colon = s.indexOf(':');
        return colon >= 0 ? s.substring(colon + 1).trim() : s;
    }

    /**
     * Whether a datapack resource path ({@code schematics/...} without {@code .json}) matches a scroll key.
     */
    static boolean resourceMatchesKey(String resourcePath, String rawKey) {
        if (resourcePath == null || resourcePath.isBlank() || rawKey == null || rawKey.isBlank()) {
            return false;
        }
        String path = resourcePath.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (path.endsWith(".json")) {
            path = path.substring(0, path.length() - 5);
        }
        if (path.startsWith("data/")) {
            int schem = path.indexOf("/schematics/");
            if (schem >= 0) {
                path = "schematics/" + path.substring(schem + "/schematics/".length());
            }
        }
        int schemIdx = path.indexOf("schematics/");
        if (schemIdx > 0) {
            path = path.substring(schemIdx);
        }
        String kp = keyPath(rawKey).toLowerCase(Locale.ROOT);
        if (kp.isBlank()) {
            return false;
        }
        String under = kp.replace('/', '_');
        int slash = path.lastIndexOf('/');
        String file = slash >= 0 ? path.substring(slash + 1) : path;

        if (path.equals("schematics/" + kp) || path.endsWith("/" + kp)) {
            return true;
        }
        if (path.equals("schematics/" + under) || path.endsWith("/" + under)) {
            return true;
        }
        if (!kp.contains("/") && file.equals(kp)) {
            return true;
        }
        int firstSlash = kp.indexOf('/');
        if (firstSlash > 0) {
            String head = kp.substring(0, firstSlash);
            if (file.equals(head)) {
                return true;
            }
        }
        return false;
    }

    /** Build PURPOSE block from loaded schematic JSON bodies. */
    static String purposeFromLoaded(String rawKey, List<String> resourceIds, List<String> jsonBodies) {
        if (rawKey == null || rawKey.isBlank()) {
            return "";
        }
        String key = rawKey.trim();
        if (jsonBodies == null || jsonBodies.isEmpty()) {
            return SCROLL_UNLOCK_HEADER + "\nschematic:" + key + " (json unknown)";
        }
        LinkedHashSet<String> unlock = new LinkedHashSet<>();
        LinkedHashSet<String> materials = new LinkedHashSet<>();
        MatState matState = new MatState();
        unlock.add("schematic:" + key);
        int n = Math.min(
                Math.min(resourceIds == null ? 0 : resourceIds.size(), jsonBodies.size()),
                MAX_RESOURCES);
        for (int i = 0; i < n; i++) {
            String rid = resourceIds != null && i < resourceIds.size() ? resourceIds.get(i) : "";
            appendFromJson(rid, jsonBodies.get(i), unlock, materials, matState);
        }
        if (!matState.anyMaterialLine && (matState.sawOutcome || matState.slotCountZero)
                && unlock.size() > 1) {
            materials.add(NONE_MATERIALS_LINE);
        }
        if (unlock.size() <= 1 && materials.isEmpty()) {
            return SCROLL_UNLOCK_HEADER + "\nschematic:" + key + " (json unknown)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(SCROLL_UNLOCK_HEADER);
        int u = 0;
        for (String line : unlock) {
            if (u >= MAX_UNLOCK_LINES) {
                break;
            }
            sb.append('\n').append(line);
            u++;
        }
        if (!materials.isEmpty()) {
            sb.append('\n').append(SCROLL_MATERIALS_HEADER);
            int c = 0;
            for (String line : materials) {
                if (c >= MAX_MATERIAL_LINES) {
                    break;
                }
                sb.append('\n').append(line);
                c++;
            }
        }
        return sb.toString();
    }

    /** Tracks whether outcomes listed any install material. */
    static final class MatState {
        boolean sawOutcome;
        boolean anyMaterialLine;
        boolean slotCountZero;
    }

    static void appendFromJson(
            String resourceId,
            String json,
            LinkedHashSet<String> unlock,
            LinkedHashSet<String> materials
    ) {
        appendFromJson(resourceId, json, unlock, materials, new MatState());
    }

    static void appendFromJson(
            String resourceId,
            String json,
            LinkedHashSet<String> unlock,
            LinkedHashSet<String> materials,
            MatState matState
    ) {
        if (json == null || json.isBlank()) {
            return;
        }
        JsonObject root;
        try {
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject()) {
                return;
            }
            root = el.getAsJsonObject();
        } catch (Exception e) {
            return;
        }
        MatState state = matState == null ? new MatState() : matState;
        if (resourceId != null && !resourceId.isBlank()) {
            unlock.add("resource:" + resourceId.trim());
        }
        String slots = joinStringArray(root.get("slots"), 6);
        if (!slots.isBlank()) {
            unlock.add("slots:" + slots);
        }
        String locked = extractLockedKey(root.get("requirement"));
        if (!locked.isBlank()) {
            unlock.add("locked:" + locked);
        }
        String effect = formatTranslationEffects(root.get("translation"));
        if (!effect.isBlank()) {
            unlock.add("effect:" + effect);
        }
        if (root.has("materialSlotCount") && root.get("materialSlotCount").isJsonPrimitive()) {
            try {
                if (root.get("materialSlotCount").getAsInt() == 0) {
                    state.slotCountZero = true;
                }
            } catch (Exception ignored) {
                // soft
            }
        }
        String applicable = joinStringArray(root.get("applicableMaterials"), MAX_MATS);
        if (!applicable.isBlank()) {
            materials.add("applicable:" + applicable);
            state.anyMaterialLine = true;
        }
        JsonElement outs = root.get("outcomes");
        if (!(outs instanceof JsonArray arr) || arr.isEmpty()) {
            return;
        }
        for (JsonElement oe : arr) {
            if (materials.size() >= MAX_MATERIAL_LINES && unlock.size() >= MAX_UNLOCK_LINES) {
                return;
            }
            if (!oe.isJsonObject()) {
                continue;
            }
            JsonObject o = oe.getAsJsonObject();
            state.sawOutcome = true;
            String module = str(o, "moduleKey");
            String variant = str(o, "moduleVariant");
            String improvs = formatImprovements(o.get("improvements"));
            if (!module.isBlank()) {
                unlock.add("module:" + module + (variant.isBlank() ? "" : " variant:" + variant));
            }
            if (!improvs.isBlank()) {
                unlock.add("improvement:" + improvs);
            }
            String mats = formatMaterials(o);
            if (!mats.isBlank()) {
                state.anyMaterialLine = true;
                String prefix = module.isBlank() ? "" : module + " -> ";
                materials.add(prefix + mats);
            }
            String tools = formatRequiredTools(o.get("requiredTools"));
            if (!tools.isBlank()) {
                materials.add("tools:" + tools);
            }
        }
    }

    /** Compact translation.primaryAttributes / effects etc. (material scale factors — copy as listed). */
    static String formatTranslationEffects(JsonElement el) {
        if (el == null || !el.isJsonObject()) {
            return "";
        }
        JsonObject t = el.getAsJsonObject();
        List<String> parts = new ArrayList<>();
        for (String field : List.of(
                "primaryAttributes", "secondaryAttributes", "primaryEffects", "secondaryEffects", "tools"
        )) {
            appendMapTokens(t.get(field), parts);
            if (parts.size() >= MAX_EFFECT_TOKENS) {
                break;
            }
        }
        for (String scalar : List.of("durability", "integrity", "magicCapacity")) {
            if (parts.size() >= MAX_EFFECT_TOKENS) {
                break;
            }
            if (t.has(scalar) && t.get(scalar).isJsonPrimitive()) {
                parts.add(scalar + "=" + t.get(scalar).getAsString());
            }
        }
        return parts.isEmpty() ? "" : String.join("; ", parts);
    }

    private static void appendMapTokens(JsonElement el, List<String> parts) {
        if (el == null || !el.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
            if (parts.size() >= MAX_EFFECT_TOKENS) {
                return;
            }
            String k = e.getKey();
            if (k == null || k.isBlank() || e.getValue() == null || !e.getValue().isJsonPrimitive()) {
                continue;
            }
            parts.add(k + "=" + e.getValue().getAsString());
        }
    }

    static String formatMaterials(JsonObject o) {
        LinkedHashSet<String> mats = new LinkedHashSet<>();
        JsonElement materials = o.get("materials");
        if (materials instanceof JsonArray arr) {
            for (JsonElement e : arr) {
                if (mats.size() >= MAX_MATS) {
                    break;
                }
                if (e.isJsonPrimitive()) {
                    String s = e.getAsString();
                    if (s != null && !s.isBlank()) {
                        mats.add(s.trim());
                    }
                }
            }
        }
        JsonElement material = o.get("material");
        if (material != null && material.isJsonObject()) {
            JsonObject m = material.getAsJsonObject();
            String tag = str(m, "tag");
            if (!tag.isBlank()) {
                mats.add("#" + tag);
            }
            JsonElement items = m.get("items");
            if (items instanceof JsonArray arr) {
                for (JsonElement e : arr) {
                    if (mats.size() >= MAX_MATS) {
                        break;
                    }
                    if (e.isJsonPrimitive()) {
                        String s = e.getAsString();
                        if (s != null && !s.isBlank()) {
                            mats.add(s.trim());
                        }
                    }
                }
            }
            String count = str(m, "count");
            if (!count.isBlank() && !mats.isEmpty()) {
                String first = mats.iterator().next();
                mats.remove(first);
                LinkedHashSet<String> rebuilt = new LinkedHashSet<>();
                rebuilt.add(first + " x" + count);
                rebuilt.addAll(mats);
                mats = rebuilt;
            }
        }
        return mats.isEmpty() ? "" : String.join(", ", mats);
    }

    static String formatImprovements(JsonElement el) {
        if (el == null || !el.isJsonObject()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (var e : el.getAsJsonObject().entrySet()) {
            if (parts.size() >= 4) {
                break;
            }
            String k = e.getKey();
            if (k == null || k.isBlank()) {
                continue;
            }
            String v = e.getValue() != null && e.getValue().isJsonPrimitive()
                    ? e.getValue().getAsString()
                    : String.valueOf(e.getValue());
            parts.add(k + ":" + v);
        }
        return String.join(",", parts);
    }

    static String formatRequiredTools(JsonElement el) {
        if (el == null || !el.isJsonObject()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (var e : el.getAsJsonObject().entrySet()) {
            if (parts.size() >= 3) {
                break;
            }
            String k = e.getKey();
            if (k == null || k.isBlank()) {
                continue;
            }
            String v = e.getValue() != null && e.getValue().isJsonPrimitive()
                    ? e.getValue().getAsString()
                    : String.valueOf(e.getValue());
            parts.add(k + "=" + v);
        }
        return String.join(",", parts);
    }

    /** Walk locked / and-requirement trees for {@code tetra:locked} key. */
    static String extractLockedKey(JsonElement req) {
        if (req == null || !req.isJsonObject()) {
            return "";
        }
        JsonObject o = req.getAsJsonObject();
        String type = str(o, "type").toLowerCase(Locale.ROOT);
        if (type.endsWith("locked") || "tetra:locked".equals(type)) {
            return str(o, "key");
        }
        JsonElement inner = o.get("requirement");
        if (inner != null) {
            String nested = extractLockedKey(inner);
            if (!nested.isBlank()) {
                return nested;
            }
        }
        JsonElement arr = o.get("requirements");
        if (arr instanceof JsonArray list) {
            for (JsonElement e : list) {
                String nested = extractLockedKey(e);
                if (!nested.isBlank()) {
                    return nested;
                }
            }
        }
        return "";
    }

    /**
     * True when a schematic's locked requirement key matches a scroll schematic id
     * ({@code tetra:terra} ≡ {@code terra}).
     */
    static boolean lockedKeyMatches(String lockedKey, String scrollKey) {
        if (lockedKey == null || lockedKey.isBlank() || scrollKey == null || scrollKey.isBlank()) {
            return false;
        }
        String a = lockedKey.trim().toLowerCase(Locale.ROOT);
        String b = scrollKey.trim().toLowerCase(Locale.ROOT);
        if (a.equals(b)) {
            return true;
        }
        return keyPath(a).equalsIgnoreCase(keyPath(b));
    }

    /** Parse schematic JSON root → locked requirement key (empty if none). */
    static String lockedKeyFromSchematicJson(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject()) {
                return "";
            }
            return extractLockedKey(el.getAsJsonObject().get("requirement"));
        } catch (Exception e) {
            return "";
        }
    }

    static String joinStringArray(JsonElement el, int max) {
        if (!(el instanceof JsonArray arr) || arr.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (JsonElement e : arr) {
            if (parts.size() >= max) {
                break;
            }
            if (e.isJsonPrimitive()) {
                String s = e.getAsString();
                if (s != null && !s.isBlank()) {
                    parts.add(s.trim());
                }
            }
        }
        return String.join(",", parts);
    }

    private static String str(JsonObject o, String key) {
        if (o == null || key == null || !o.has(key)) {
            return "";
        }
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) {
            return "";
        }
        String s = e.getAsString();
        return s == null ? "" : s.trim();
    }

    /** True if token is a Tetra material folder ref ({@code ns:category/}). */
    static boolean isMaterialFolderRef(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String t = token.trim();
        int colon = t.indexOf(':');
        return colon > 0 && t.endsWith("/") && colon < t.length() - 1;
    }

    /** {@code tetra:battery/} → {@code battery}. */
    static String materialFolderCategory(String folderRef) {
        if (!isMaterialFolderRef(folderRef)) {
            return "";
        }
        String path = keyPath(folderRef.trim());
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /** Unique folder refs under {@code [SCROLL_MATERIALS]} (keeps {@code tetra:battery/} lines). */
    static List<String> materialFolderRefs(String purpose) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (purpose == null || purpose.isBlank()) {
            return List.of();
        }
        boolean inMats = false;
        for (String line : purpose.split("\n", -1)) {
            String trimmed = line.trim();
            if (SCROLL_MATERIALS_HEADER.equals(trimmed)) {
                inMats = true;
                continue;
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inMats = false;
                continue;
            }
            if (!inMats || trimmed.isBlank()) {
                continue;
            }
            String body = trimmed;
            int arrow = body.indexOf(" -> ");
            if (arrow >= 0) {
                body = body.substring(arrow + 4);
            }
            if (body.startsWith("applicable:")
                    || body.startsWith("tools:")
                    || body.startsWith("install_items")) {
                continue;
            }
            for (String part : body.split(",")) {
                String p = part.trim();
                if (isMaterialFolderRef(p)) {
                    out.add(p);
                }
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * Parse one materials/*.json body → item ids; optional {@code count} on first item as {@code id xN}.
     */
    static List<String> itemsFromMaterialJson(String json) {
        List<String> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        JsonObject root;
        try {
            JsonElement el = JsonParser.parseString(json);
            if (!el.isJsonObject()) {
                return out;
            }
            root = el.getAsJsonObject();
        } catch (Exception e) {
            return out;
        }
        JsonElement material = root.get("material");
        if (material == null || !material.isJsonObject()) {
            return out;
        }
        JsonObject m = material.getAsJsonObject();
        JsonElement items = m.get("items");
        if (!(items instanceof JsonArray arr) || arr.isEmpty()) {
            return out;
        }
        for (JsonElement e : arr) {
            if (!e.isJsonPrimitive()) {
                continue;
            }
            String s = e.getAsString();
            if (s != null && !s.isBlank()) {
                out.add(s.trim());
            }
        }
        if (out.isEmpty()) {
            return out;
        }
        String count = str(m, "count");
        if (!count.isBlank()) {
            String first = out.get(0);
            out.set(0, first + " x" + count);
        }
        return out;
    }

    /** Bare registry id without trailing {@code xN}. */
    static String bareItemId(String item) {
        if (item == null || item.isBlank()) {
            return "";
        }
        String s = item.trim();
        int x = s.lastIndexOf(" x");
        if (x > 0) {
            String maybe = s.substring(x + 2).trim();
            if (!maybe.isEmpty() && maybe.chars().allMatch(Character::isDigit)) {
                return s.substring(0, x).trim();
            }
        }
        return s;
    }

    /** Merge parsed material items into dest (unique by bare id, insertion order). */
    static void mergeInstallItems(List<String> fromJson, LinkedHashSet<String> dest) {
        if (fromJson == null || dest == null) {
            return;
        }
        LinkedHashSet<String> seenBare = new LinkedHashSet<>();
        for (String existing : dest) {
            String b = bareItemId(existing);
            if (!b.isBlank()) {
                seenBare.add(b);
            }
        }
        for (String item : fromJson) {
            if (item == null || item.isBlank()) {
                continue;
            }
            String bare = bareItemId(item);
            if (bare.isBlank() || seenBare.contains(bare)) {
                continue;
            }
            seenBare.add(bare);
            dest.add(item.trim());
        }
    }

    /** {@code install_items (pick one / 任選其一): a, b, … +N more in folder/} — cap {@link #MAX_INSTALL_ITEMS}. */
    static String formatInstallItemsLine(Iterable<String> items) {
        return formatInstallItemsLine(items, List.of());
    }

    static String formatInstallItemsLine(Iterable<String> items, List<String> folderRefs) {
        if (items == null) {
            return "";
        }
        List<String> list = new ArrayList<>();
        for (String s : items) {
            if (s != null && !s.isBlank()) {
                list.add(s.trim());
            }
        }
        if (list.isEmpty()) {
            return "";
        }
        int shown = Math.min(list.size(), MAX_INSTALL_ITEMS);
        StringBuilder sb = new StringBuilder(INSTALL_ITEMS_PREFIX);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(list.get(i));
        }
        int more = list.size() - shown;
        if (more > 0) {
            sb.append(", … +").append(more).append(" more");
            String folder = firstFolderRef(folderRefs);
            if (!folder.isBlank()) {
                sb.append(" in ").append(folder);
            }
        }
        return sb.toString();
    }

    private static String firstFolderRef(List<String> folderRefs) {
        if (folderRefs == null || folderRefs.isEmpty()) {
            return "";
        }
        for (String r : folderRefs) {
            if (r != null && !r.isBlank()) {
                return r.trim();
            }
        }
        return "";
    }

    /** Append install_items line under materials (keep folder ref lines). */
    static String withInstallItemsLine(String purpose, String installLine) {
        if (purpose == null || purpose.isBlank()) {
            return purpose == null ? "" : purpose;
        }
        if (installLine == null || installLine.isBlank()) {
            return purpose;
        }
        if (purpose.contains("install_items")) {
            return purpose;
        }
        if (!purpose.contains(SCROLL_MATERIALS_HEADER)) {
            return purpose + "\n" + SCROLL_MATERIALS_HEADER + "\n" + installLine;
        }
        return purpose + "\n" + installLine;
    }

    /** True when PURPOSE has a {@code [SCROLL_MATERIALS]} block. */
    public static boolean hasScrollMaterials(String purpose) {
        return purpose != null && purpose.contains(SCROLL_MATERIALS_HEADER);
    }

    /** True when materials block says no material required. */
    public static boolean saysNoMaterials(String purpose) {
        if (!hasScrollMaterials(purpose)) {
            return false;
        }
        for (String line : scrollMaterialBodyLines(purpose)) {
            String t = line.trim().toLowerCase(Locale.ROOT);
            if (t.equals(NONE_MATERIALS_LINE) || t.equals("none") || t.startsWith("none (")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prefer {@code install_items:} tokens; else outcome material item ids under SCROLL_MATERIALS.
     * Skips folder refs, tools/, applicable/, none.
     */
    public static List<InstallItemSpec> installItemSpecsFromPurpose(String purpose) {
        if (!hasScrollMaterials(purpose)) {
            return List.of();
        }
        List<String> body = scrollMaterialBodyLines(purpose);
        for (String line : body) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("install_items")) {
                int colon = trimmed.indexOf(':');
                String rhs = colon >= 0 ? trimmed.substring(colon + 1).trim() : "";
                List<InstallItemSpec> fromInstall = parseItemSpecList(rhs);
                if (!fromInstall.isEmpty()) {
                    return fromInstall;
                }
            }
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<InstallItemSpec> out = new ArrayList<>();
        for (String line : body) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || isMaterialMetaLine(trimmed)) {
                continue;
            }
            String rhs = trimmed;
            int arrow = trimmed.indexOf("->");
            if (arrow >= 0) {
                rhs = trimmed.substring(arrow + 2).trim();
            }
            for (InstallItemSpec spec : parseItemSpecList(rhs)) {
                if (!spec.isPresent() || !seen.add(spec.id())) {
                    continue;
                }
                out.add(spec);
                if (out.size() >= MAX_INSTALL_ITEMS) {
                    return List.copyOf(out);
                }
            }
        }
        return List.copyOf(out);
    }

    /** Format UI marker: {@code {{item:mod:id}}} or {@code {{item:mod:id×N}}}. */
    public static String formatItemMarker(String id, int count) {
        if (id == null || id.isBlank()) {
            return "";
        }
        String clean = id.trim().toLowerCase(Locale.ROOT);
        int n = Math.max(1, count);
        if (n <= 1) {
            return "{{item:" + clean + "}}";
        }
        return "{{item:" + clean + "×" + n + "}}";
    }

    /**
     * One chat line for workbench materials (markers or none text). Empty when no SCROLL_MATERIALS
     * or unresolved folder-only (no install specs).
     */
    public static String inlineMaterialsLine(String purpose, String title, String noneLabel) {
        if (!hasScrollMaterials(purpose)) {
            return "";
        }
        String head = title == null || title.isBlank() ? "" : title.trim() + "：";
        if (saysNoMaterials(purpose)) {
            String none = noneLabel == null || noneLabel.isBlank() ? NONE_MATERIALS_LINE : noneLabel.trim();
            return head + none;
        }
        List<InstallItemSpec> specs = installItemSpecsFromPurpose(purpose);
        if (specs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(head);
        for (int i = 0; i < specs.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            InstallItemSpec spec = specs.get(i);
            sb.append(formatItemMarker(spec.id(), spec.count()));
        }
        return sb.toString();
    }

    /**
     * Insert materials line into assistant answer (before sources / after first paragraph).
     * Skips if already injected or no materials block to show.
     */
    public static String injectInlineMaterials(String answer, String purpose, String title, String noneLabel) {
        String block = inlineMaterialsLine(purpose, title, noneLabel);
        if (block.isBlank()) {
            return answer == null ? "" : answer;
        }
        String raw = answer == null ? "" : answer;
        if (raw.contains("{{item:") || raw.contains(block)) {
            return raw;
        }
        // none-only: still inject once (no {{item:}} to detect)
        if (saysNoMaterials(purpose) && (raw.contains(NONE_MATERIALS_LINE)
                || (noneLabel != null && !noneLabel.isBlank() && raw.contains(noneLabel.trim())))) {
            return raw;
        }
        int sourcesAt = -1;
        Matcher sm = ReplySources.HEADER.matcher(raw);
        if (sm.find()) {
            sourcesAt = sm.start();
        }
        if (sourcesAt >= 0) {
            String before = raw.substring(0, sourcesAt).replaceAll("\\s+$", "");
            String after = raw.substring(sourcesAt);
            if (before.isEmpty()) {
                return block + "\n\n" + after;
            }
            return before + "\n\n" + block + "\n\n" + after;
        }
        int split = raw.indexOf("\n\n");
        if (split < 0) {
            split = raw.indexOf('\n');
        }
        if (split < 0) {
            if (raw.isBlank()) {
                return block;
            }
            return raw + "\n\n" + block;
        }
        String before = raw.substring(0, split).replaceAll("\\s+$", "");
        String after = raw.substring(split).replaceAll("^\\s+", "");
        if (before.isEmpty()) {
            return block + (after.isEmpty() ? "" : "\n\n" + after);
        }
        if (after.isEmpty()) {
            return before + "\n\n" + block;
        }
        return before + "\n\n" + block + "\n\n" + after;
    }

    private static boolean isMaterialMetaLine(String trimmed) {
        String t = trimmed.toLowerCase(Locale.ROOT);
        return t.startsWith("tools:")
                || t.startsWith("applicable:")
                || t.startsWith("install_items")
                || t.equals(NONE_MATERIALS_LINE)
                || t.equals("none")
                || t.startsWith("none (")
                || isMaterialFolderRef(trimmed);
    }

    static List<String> scrollMaterialBodyLines(String purpose) {
        List<String> out = new ArrayList<>();
        if (purpose == null || purpose.isBlank()) {
            return out;
        }
        boolean inMats = false;
        for (String line : purpose.split("\n", -1)) {
            String trimmed = line.trim();
            if (SCROLL_MATERIALS_HEADER.equals(trimmed)) {
                inMats = true;
                continue;
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inMats = false;
                continue;
            }
            if (inMats && !trimmed.isBlank()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    /**
     * Parse {@code mod:id, mod:id2 x3, … +N more in folder/} into specs.
     * Drops overflow ellipsis tokens.
     */
    static List<InstallItemSpec> parseItemSpecList(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<InstallItemSpec> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String raw : csv.split(",")) {
            String tok = raw.trim();
            if (tok.isBlank() || tok.startsWith("…") || tok.startsWith("...")) {
                continue;
            }
            // "… +15 more in tetra:battery/" already skipped; also skip bare folder refs
            if (isMaterialFolderRef(tok)) {
                continue;
            }
            InstallItemSpec spec = parseOneItemSpec(tok);
            if (!spec.isPresent() || !seen.add(spec.id())) {
                continue;
            }
            out.add(spec);
            if (out.size() >= MAX_INSTALL_ITEMS) {
                break;
            }
        }
        return List.copyOf(out);
    }

    /** {@code minecraft:gold_nugget x3} / {@code minecraft:iron_ingot}. */
    static InstallItemSpec parseOneItemSpec(String token) {
        if (token == null || token.isBlank()) {
            return new InstallItemSpec("", 1);
        }
        String t = token.trim();
        int x = t.toLowerCase(Locale.ROOT).lastIndexOf(" x");
        String id = t;
        int count = 1;
        if (x > 0 && x + 2 < t.length()) {
            String num = t.substring(x + 2).trim();
            if (num.matches("\\d{1,4}")) {
                id = t.substring(0, x).trim();
                try {
                    count = Integer.parseInt(num);
                } catch (NumberFormatException ignored) {
                    count = 1;
                }
            }
        }
        if (!id.contains(":") || id.contains(" ") || id.startsWith("#")) {
            return new InstallItemSpec("", 1);
        }
        return new InstallItemSpec(id, count);
    }
}
