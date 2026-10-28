package com.skps9.packai.client.jei;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skps9.packai.PackAiMod;
import com.skps9.packai.logic.ReplyLang;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.loading.FMLPaths;

/** JEI category → extra required blocks (data-driven via {@code machine_requirements.json}). */
public final class MachineRequirements {
    private static volatile Map<String, List<String>> CACHE;

    private MachineRequirements() {}

    public static void invalidate() {
        CACHE = null;
    }

    static List<String> requiredIdsFor(String catUid) {
        if (catUid == null) {
            return List.of();
        }
        Map<String, List<String>> map = CACHE;
        if (map == null) {
            map = loadRequirements();
            CACHE = map;
        }
        List<String> ids = map.get(catUid.toLowerCase(Locale.ROOT));
        return ids == null ? List.of() : ids;
    }

    static List<String> notesFor(String catUid) {
        try {
            List<String> ids = requiredIdsFor(catUid);
            if (ids.isEmpty()) {
                return List.of();
            }
            String lang = ReplyLang.current();
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            List<String> out = new ArrayList<>();
            for (String id : ids) {
                if (id == null || id.isBlank()) {
                    continue;
                }
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl == null || !Registry.ITEM.containsKey(rl)) {
                    PackAiMod.LOGGER.warn("[packai] machine_requirements bad item id {}", id);
                    continue;
                }
                ItemStack stack = new ItemStack(Registry.ITEM.get(rl));
                if (stack.isEmpty()) {
                    continue;
                }
                String note = ReplyLang.tr(lang, "packai.reply.also_needs", stack.getHoverName().getString());
                if (note == null || note.isBlank() || seen.contains(note)) {
                    continue;
                }
                seen.add(note);
                out.add(note);
                if (out.size() >= 2) {
                    break;
                }
            }
            return List.copyOf(out);
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static Map<String, List<String>> loadRequirements() {
        try {
            String text = null;
            try {
                Path config = FMLPaths.GAMEDIR.get().resolve("config/packai/machine_requirements.json");
                if (Files.isRegularFile(config)) {
                    text = Files.readString(config, StandardCharsets.UTF_8);
                }
            } catch (Throwable ignored) {
                text = null;
            }
            if (text == null) {
                try (InputStream in = MachineRequirements.class.getClassLoader()
                        .getResourceAsStream("assets/packai/machine_requirements.json")) {
                    if (in == null) {
                        return Map.of();
                    }
                    text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            return parseRequirements(text);
        } catch (Throwable t) {
            PackAiMod.LOGGER.debug("machine_requirements load failed: {}", t.toString());
            return Map.of();
        }
    }

    private static Map<String, List<String>> parseRequirements(String text) {
        JsonElement root = JsonParser.parseString(text);
        if (!root.isJsonObject()) {
            return Map.of();
        }
        JsonObject obj = root.getAsJsonObject();
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("_")) {
                continue;
            }
            key = key.toLowerCase(Locale.ROOT);
            JsonElement val = entry.getValue();
            if (val == null || !val.isJsonArray()) {
                continue;
            }
            JsonArray arr = val.getAsJsonArray();
            List<String> ids = new ArrayList<>();
            for (JsonElement el : arr) {
                if (el != null && el.isJsonPrimitive()) {
                    ids.add(el.getAsString());
                }
            }
            out.put(key, List.copyOf(ids));
        }
        return out;
    }
}
