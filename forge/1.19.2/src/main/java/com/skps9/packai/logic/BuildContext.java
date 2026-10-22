package com.skps9.packai.logic;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skps9.packai.config.PackAiConfig;

import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Client-only graph-build snapshot. {@link #clientDefaults()} is the only place that may read
 * {@link ReplyLang}, {@link PackAiConfig}, {@link FMLPaths}, or {@link ObtainGraph.FluidSources}.
 */
public record BuildContext(String langCode, Set<String> naturalFluids, boolean census, Path gameDir) {

    public BuildContext {
        Objects.requireNonNull(langCode, "langCode");
        naturalFluids = naturalFluids != null ? Set.copyOf(naturalFluids) : Set.of();
    }

    /** Client thread only — snapshots live config / lang / fluid listing for production and bench. */
    public static BuildContext clientDefaults() {
        return new BuildContext(
                ReplyLang.current(),
                ObtainGraph.FluidSources.naturalIds(),
                PackAiConfig.obtainCensus(),
                FMLPaths.GAMEDIR.get());
    }

    /** Headless harness: fixed lang, classpath fluid list, no census, no game dir; never touches Minecraft. */
    public static BuildContext headless() {
        return new BuildContext("en_us", loadClasspathNaturalFluids(), false, null);
    }

    private static Set<String> loadClasspathNaturalFluids() {
        try (InputStream in =
                BuildContext.class.getClassLoader().getResourceAsStream("assets/packai/fluid_sources.json")) {
            if (in == null) {
                return Set.of();
            }
            JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            Set<String> parsed = parseNaturalFromJson(root);
            return parsed != null ? Set.copyOf(parsed) : Set.of();
        } catch (Throwable ignored) {
            return Set.of();
        }
    }

    private static Set<String> parseNaturalFromJson(JsonElement root) {
        if (root == null || !root.isJsonObject()) {
            return null;
        }
        JsonObject o = root.getAsJsonObject();
        if (!o.has("natural") || !o.get("natural").isJsonArray()) {
            return null;
        }
        Set<String> out = new LinkedHashSet<>();
        JsonArray arr = o.getAsJsonArray("natural");
        for (JsonElement el : arr) {
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                String id = el.getAsString();
                if (id != null && !id.isBlank()) {
                    out.add(id);
                }
            }
        }
        return out;
    }
}
