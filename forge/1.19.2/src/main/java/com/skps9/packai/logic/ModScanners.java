package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Scanner enablement and focus mod helpers from loaded mod list. */
public final class ModScanners {
    private static final Set<String> NOISE = Set.of(
            "minecraft", "java", "neoforge", "forge", "fml", "mcp",
            "fabricloader", "fabric-api", "fabric", "quilt_loader", "mixinextras", "packai"
    );

    private ModScanners() {}

    public static List<String> active(List<String> modIds) {
        Set<String> present = new LinkedHashSet<>();
        for (String m : modIds) {
            present.add(m.toLowerCase(Locale.ROOT));
        }
        List<String> out = new ArrayList<>();
        if (present.contains("kubejs")) {
            out.add("kubejs");
        }
        if (present.contains("crafttweaker")) {
            out.add("crafttweaker");
        }
        if (present.contains("groovyscript")) {
            out.add("groovyscript");
        }
        if (present.contains("ftbquests") || present.contains("ftb_quests")) {
            out.add("ftbquests");
        }
        if (present.contains("heracles")) {
            out.add("heracles");
        }
        out.add("datapacks");
        return out;
    }

    public static List<String> focusMods(List<String> modIds, String heldItemId, String question) {
        return focusMods(modIds, heldItemId, question, List.of());
    }

    public static List<String> focusMods(
            List<String> modIds,
            String heldItemId,
            String question,
            List<String> extraItemIds
    ) {
        Set<String> present = new LinkedHashSet<>();
        for (String m : modIds) {
            String lower = m.toLowerCase(Locale.ROOT);
            if (!NOISE.contains(lower)) {
                present.add(lower);
            }
        }
        List<String> focus = new ArrayList<>();
        addNs(focus, present, heldItemId);
        if (extraItemIds != null) {
            for (String id : extraItemIds) {
                addNs(focus, present, id);
                if (focus.size() >= 6) {
                    break;
                }
            }
        }
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);
        for (String mid : present) {
            if (focus.size() >= 6) {
                break;
            }
            if (q.contains(mid) && !focus.contains(mid)) {
                focus.add(mid);
            }
        }
        if (focus.isEmpty()) {
            for (String mid : present) {
                focus.add(mid);
                if (focus.size() >= 4) {
                    break;
                }
            }
        }
        return focus;
    }

    private static void addNs(List<String> focus, Set<String> present, String itemId) {
        if (itemId == null || !itemId.contains(":") || focus.size() >= 6) {
            return;
        }
        String ns = itemId.substring(0, itemId.indexOf(':')).toLowerCase(Locale.ROOT);
        if (present.contains(ns) && !focus.contains(ns)) {
            focus.add(ns);
        }
    }

    public static boolean isNoise(String modId) {
        return NOISE.contains(modId.toLowerCase(Locale.ROOT));
    }

    /** True when {@code modId} appears in the loaded mod list (case-insensitive). */
    public static boolean hasMod(List<String> modIds, String modId) {
        if (modIds == null || modId == null || modId.isBlank()) {
            return false;
        }
        String want = modId.toLowerCase(Locale.ROOT);
        for (String m : modIds) {
            if (m != null && want.equals(m.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasAnyMod(List<String> modIds, String... modIdsToCheck) {
        for (String id : modIdsToCheck) {
            if (hasMod(modIds, id)) {
                return true;
            }
        }
        return false;
    }
}
