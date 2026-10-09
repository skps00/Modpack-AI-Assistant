package com.skps9.packai.client.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Live keybind snapshot from {@code Minecraft.options.keyMappings} (SRG {@code f_92059_}).
 * Prefer that public array over {@code KeyMapping.ALL}, which is private on 1.19.2.
 */
public final class KeybindReader {
    public record Row(
            String label,
            String rawKey,
            String keyDisplay,
            boolean unbound,
            String namespace,
            boolean conflict
    ) {}

    private KeybindReader() {}

    /** Immutable snapshot; empty on null client/options or any failure — never throws. */
    public static List<Row> snapshot() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.options == null) {
                return List.of();
            }
            KeyMapping[] mappings = mc.options.keyMappings;
            if (mappings == null) {
                return List.of();
            }
            List<Row> draft = new ArrayList<>();
            Map<String, Integer> boundCounts = new HashMap<>();
            for (KeyMapping km : mappings) {
                if (km == null) {
                    continue;
                }
                String rawKey = km.getName();
                if (rawKey == null) {
                    rawKey = "";
                }
                String label;
                try {
                    label = Component.translatable(rawKey).getString();
                } catch (Throwable t) {
                    label = rawKey;
                }
                if (label == null) {
                    label = "";
                }
                String keyDisplay;
                try {
                    keyDisplay = km.getTranslatedKeyMessage().getString();
                } catch (Throwable t) {
                    keyDisplay = "";
                }
                if (keyDisplay == null) {
                    keyDisplay = "";
                }
                boolean unbound = km.isUnbound();
                String namespace = namespaceOf(rawKey);
                if (!unbound && !keyDisplay.isBlank()) {
                    boundCounts.merge(keyDisplay, 1, Integer::sum);
                }
                draft.add(new Row(label, rawKey, keyDisplay, unbound, namespace, false));
            }
            Set<String> conflictKeys = new HashSet<>();
            for (Map.Entry<String, Integer> e : boundCounts.entrySet()) {
                if (e.getValue() != null && e.getValue() >= 2) {
                    conflictKeys.add(e.getKey());
                }
            }
            List<Row> out = new ArrayList<>(draft.size());
            for (Row r : draft) {
                boolean conflict = !r.unbound() && conflictKeys.contains(r.keyDisplay());
                out.add(new Row(r.label(), r.rawKey(), r.keyDisplay(), r.unbound(), r.namespace(), conflict));
            }
            return Collections.unmodifiableList(out);
        } catch (Throwable t) {
            return List.of();
        }
    }

    /** Second segment of {@code key.<ns>.…} (e.g. {@code key.jade.toggle} → {@code jade}). */
    static String namespaceOf(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return "";
        }
        String[] parts = rawKey.split("\\.", 3);
        return parts.length >= 2 ? parts[1] : "";
    }
}
