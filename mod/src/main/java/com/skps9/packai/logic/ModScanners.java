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
        Set<String> present = new LinkedHashSet<>();
        for (String m : modIds) {
            String lower = m.toLowerCase(Locale.ROOT);
            if (!NOISE.contains(lower)) {
                present.add(lower);
            }
        }
        List<String> focus = new ArrayList<>();
        if (heldItemId != null && heldItemId.contains(":")) {
            String ns = heldItemId.substring(0, heldItemId.indexOf(':')).toLowerCase(Locale.ROOT);
            if (present.contains(ns)) {
                focus.add(ns);
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

    public static boolean isNoise(String modId) {
        return NOISE.contains(modId.toLowerCase(Locale.ROOT));
    }
}
