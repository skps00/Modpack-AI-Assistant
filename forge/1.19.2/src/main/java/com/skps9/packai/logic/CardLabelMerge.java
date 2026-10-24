package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CardLabelMerge {
    public record Entry(String id, String label) {}

    private CardLabelMerge() {}

    /**
     * Merge by (id, label); same display name with different ids stay separate.
     * Output matches {@link RenderRecipeCardsAskTool#briefFromFreq}: {@code +} joins, {@code ×N}, {@code +…}.
     */
    public static String merge(List<Entry> entries, Set<String> skipIds, int maxKinds) {
        if (maxKinds <= 0 || entries == null || entries.isEmpty()) {
            return "";
        }
        record Key(String id, String label) {}
        LinkedHashMap<Key, Integer> freq = new LinkedHashMap<>();
        for (Entry e : entries) {
            if (e == null) {
                continue;
            }
            String id = e.id() == null ? "" : e.id().trim();
            if (id.isBlank()) {
                continue;
            }
            if (shouldSkip(id, e.label(), skipIds)) {
                continue;
            }
            Key key = new Key(id, e.label() == null ? "" : e.label());
            freq.merge(key, 1, Integer::sum);
        }
        if (freq.isEmpty()) {
            return "";
        }
        ArrayList<Map.Entry<Key, Integer>> materials = new ArrayList<>(freq.entrySet());
        int totalKinds = materials.size();
        int show = Math.min(maxKinds, totalKinds);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < show; i++) {
            var e = materials.get(i);
            if (b.length() > 0) {
                b.append('+');
            }
            String label = e.getKey().label();
            int n = e.getValue() == null ? 1 : e.getValue();
            b.append(labelWithCount(label, n));
        }
        if (totalKinds > maxKinds) {
            b.append("+…");
        }
        return b.toString();
    }

    static String labelWithCount(String label, int n) {
        if (label == null) {
            label = "";
        }
        if (n <= 1) {
            return label;
        }
        int open = label.lastIndexOf(" (");
        if (label.endsWith(")") && open >= 0) {
            return label.substring(0, open) + "×" + n + label.substring(open);
        }
        return label + "×" + n;
    }

    private static boolean shouldSkip(String id, String label, Set<String> skipIds) {
        if (skipIds == null || skipIds.isEmpty()) {
            return false;
        }
        for (String skip : skipIds) {
            if (skip == null || skip.isBlank()) {
                continue;
            }
            if (id.equalsIgnoreCase(skip)) {
                return true;
            }
            if (label != null && label.equalsIgnoreCase(skip)) {
                return true;
            }
        }
        return false;
    }
}
