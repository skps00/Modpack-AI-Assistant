package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tetra modular tool parts / improvements from flat item NBT (no Tetra maven dep).
 * Slot {@code double/head_left} → module id; {@code <module>_material} → variant;
 * {@code slot:improvement} → int. Socket gems are module+material, not int improvements.
 * {@link Part#itemId()} is a registry id when material JSON lists {@code material.items};
 * empty when none (do not fake). Mirrored by {@code tests/check_tetra_tool_build.py}.
 */
public final class ToolBuildFacts {
    public static final String HEADER = "[TOOL_BUILD]";
    public static final String UNPARSED = "this NBT not parsed";
    /** ponytail: cap prompt size. Upgrade: per-slot pages. */
    static final int MAX_PARTS = 12;
    static final int MAX_MODS = 16;

    private static final Set<String> SKIP_KEYS = Set.of(
            "damage",
            "hideflags",
            "honing_progress",
            "honing_available",
            "id",
            "repaircount",
            "enchantments",
            "attributemodifiers",
            "display",
            "enchantmentmapping",
            "custommodeldata");

    /**
     * One installed slot. {@code name} = I18n if present (never invented).
     * {@code itemId} = {@code ns:path} from Tetra material JSON {@code material.items[0]} when
     * that array exists; empty otherwise (GUI must skip icon).
     */
    public record Part(String slot, String moduleId, String materialId, String name, String itemId) {
        public Part {
            slot = nz(slot);
            moduleId = nz(moduleId);
            materialId = nz(materialId);
            name = nz(name);
            itemId = nz(itemId);
        }

        public Part(String slot, String moduleId, String materialId) {
            this(slot, moduleId, materialId, "", "");
        }

        public Part withMeta(String displayName, String registryItemId) {
            return new Part(slot, moduleId, materialId, displayName, registryItemId);
        }
    }

    public record Improvement(String key, int level, String name, String itemId) {
        public Improvement {
            key = nz(key);
            name = nz(name);
            itemId = nz(itemId);
        }

        public Improvement(String key, int level) {
            this(key, level, "", "");
        }

        public Improvement withMeta(String displayName, String registryItemId) {
            return new Improvement(key, level, displayName, registryItemId);
        }
    }

    public record Scan(List<Part> parts, List<Improvement> mods, String rawSource) {
        public Scan {
            parts = parts == null ? List.of() : List.copyOf(parts);
            mods = mods == null ? List.of() : List.copyOf(mods);
            rawSource = nz(rawSource);
        }

        public boolean isEmpty() {
            return parts.isEmpty() && mods.isEmpty();
        }
    }

    private ToolBuildFacts() {}

    /** {@code tetra:modular_*} excluding scrolls. */
    public static boolean looksLikeTetraModularItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        String id = itemId.trim().toLowerCase(Locale.ROOT);
        int c = id.indexOf(':');
        String ns = c < 0 ? "" : id.substring(0, c);
        String path = c < 0 ? id : id.substring(c + 1);
        if (!"tetra".equals(ns)) {
            return false;
        }
        if (path.contains("scroll")) {
            return false;
        }
        return path.startsWith("modular_");
    }

    /**
     * Parse Tetra-style flat NBT maps. Nested compounds should already be flattened
     * ({@code id} → slot string, {@code module_material}, {@code slot:improvement}).
     */
    public static Scan parse(Map<String, String> strings, Map<String, Integer> ints) {
        Map<String, String> slots = new LinkedHashMap<>();
        Map<String, String> materials = new LinkedHashMap<>();
        if (strings != null) {
            for (Map.Entry<String, String> e : strings.entrySet()) {
                String key = e.getKey();
                String val = e.getValue();
                if (key == null || val == null || val.isBlank() || skipKey(key)) {
                    continue;
                }
                if (looksLikeUuid(val)) {
                    continue;
                }
                String k = key.trim();
                String v = val.trim();
                if (isMaterialKey(k)) {
                    materials.put(k, v);
                    continue;
                }
                if (isSlotKey(k) && looksLikeModuleId(v)) {
                    slots.put(k, v);
                }
            }
        }
        List<Part> parts = new ArrayList<>();
        for (Map.Entry<String, String> e : slots.entrySet()) {
            if (parts.size() >= MAX_PARTS) {
                break;
            }
            String slot = e.getKey();
            String module = e.getValue();
            String mat = materials.getOrDefault(module + "_material", "");
            if (mat.isBlank()) {
                mat = materials.getOrDefault(slot + "_material", "");
            }
            parts.add(new Part(slot, module, mat));
        }
        List<Improvement> mods = new ArrayList<>();
        if (ints != null) {
            for (Map.Entry<String, Integer> e : ints.entrySet()) {
                if (mods.size() >= MAX_MODS) {
                    break;
                }
                String key = e.getKey();
                Integer level = e.getValue();
                if (key == null || level == null || skipKey(key) || !isImprovementKey(key)) {
                    continue;
                }
                mods.add(new Improvement(key.trim(), level));
            }
        }
        String source = parts.isEmpty() && mods.isEmpty() ? "" : "tetra_flat_nbt";
        return new Scan(parts, mods, source);
    }

    public static String format(Scan scan) {
        if (scan == null || scan.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        for (Part p : scan.parts()) {
            String kind = isSocketPart(p) ? "socket" : "part";
            String line = kind + " " + p.slot() + ": " + p.moduleId();
            if (!p.materialId().isBlank()) {
                line = line + " material " + p.materialId();
            }
            if (!p.name().isBlank()) {
                line = line + " name " + p.name();
            }
            if (!p.itemId().isBlank()) {
                line = line + " item " + p.itemId();
            }
            lines.add(line);
        }
        for (Improvement m : scan.mods()) {
            String line = "improvement " + m.key() + " " + m.level();
            if (!m.name().isBlank()) {
                line = line + " name " + m.name();
            }
            if (!m.itemId().isBlank()) {
                line = line + " item " + m.itemId();
            }
            lines.add(line);
        }
        return String.join("\n", lines);
    }

    public static String unparsedBlock() {
        return HEADER + "\n" + UNPARSED;
    }

    /** Socket = Tetra minor socket module / socket material id, not a guess from tooltip. */
    static boolean isSocketPart(Part p) {
        if (p == null) {
            return false;
        }
        return containsSocket(p.slot()) || containsSocket(p.moduleId()) || containsSocket(p.materialId());
    }

    static boolean containsSocket(String s) {
        return s != null && s.toLowerCase(Locale.ROOT).contains("socket");
    }

    static boolean skipKey(String key) {
        return SKIP_KEYS.contains(key.trim().toLowerCase(Locale.ROOT));
    }

    static boolean isMaterialKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return k.endsWith("_material") && k.contains("/");
    }

    static boolean isSlotKey(String key) {
        if (key.indexOf('/') < 0 || key.indexOf(':') >= 0) {
            return false;
        }
        String k = key.toLowerCase(Locale.ROOT);
        if (k.endsWith("_material") || k.contains("_tweak") || k.endsWith("settle_progress")) {
            return false;
        }
        return true;
    }

    static boolean isImprovementKey(String key) {
        int colon = key.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        String k = key.toLowerCase(Locale.ROOT);
        if (k.contains("_tweak:") || k.endsWith("settle_progress")) {
            return false;
        }
        return key.indexOf('/') >= 0 && key.indexOf('/') < colon;
    }

    static boolean looksLikeModuleId(String val) {
        return val.indexOf('/') >= 0 && val.indexOf(' ') < 0 && val.length() < 96;
    }

    static boolean looksLikeUuid(String val) {
        String v = val.trim();
        if (v.length() != 36) {
            return false;
        }
        int dashes = 0;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '-') {
                dashes++;
            } else if (!isHex(c)) {
                return false;
            }
        }
        return dashes == 4;
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
