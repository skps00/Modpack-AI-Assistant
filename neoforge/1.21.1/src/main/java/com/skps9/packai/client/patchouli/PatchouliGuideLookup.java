package com.skps9.packai.client.patchouli;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;

import com.skps9.packai.compat.PatchouliBridge;
import com.skps9.packai.logic.PatchouliEntryScan;
import com.skps9.packai.logic.ReplyLang;

/**
 * Resolve Patchouli guide text for a focus stack: live book mappings when present,
 * else scan {@code patchouli_books/**\/entries/*.json} from the resource manager.
 */
public final class PatchouliGuideLookup {
    private PatchouliGuideLookup() {}

    public static String lookup(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String fromApi = PatchouliBridge.lookupGuideText(stack);
        if (fromApi != null && !fromApi.isBlank()) {
            return fromApi;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) {
            return "";
        }
        return scanResources(key.toString());
    }

    public static String scanResources(String itemId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null || itemId == null || itemId.isBlank()) {
            return "";
        }
        String lang = "en_us";
        try {
            lang = ReplyLang.normalize(ReplyLang.current());
        } catch (Throwable ignored) {
            // keep en_us
        }
        Map<ResourceLocation, Resource> found;
        try {
            found = mc.getResourceManager().listResources(
                    "patchouli_books",
                    loc -> {
                        String p = loc.getPath();
                        return p.contains("/entries/") && p.endsWith(".json");
                    });
        } catch (Throwable t) {
            return "";
        }
        if (found == null || found.isEmpty()) {
            return "";
        }
        record Hit(int score, int langRank, String body) {}
        List<Hit> hits = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> e : found.entrySet()) {
            ResourceLocation loc = e.getKey();
            int langRank = langRank(loc.getPath(), lang);
            try (var in = e.getValue().open();
                    var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                int score = PatchouliEntryScan.matchScore(obj, itemId);
                if (score <= 0) {
                    continue;
                }
                String body = PatchouliEntryScan.extractPlainText(obj);
                if (body.isBlank()) {
                    continue;
                }
                hits.add(new Hit(score, langRank, body));
            } catch (Throwable ignored) {
                // skip bad entry json
            }
        }
        hits.sort(Comparator.comparingInt(Hit::score).reversed()
                .thenComparingInt(Hit::langRank));
        List<String> bodies = new ArrayList<>();
        for (Hit h : hits) {
            bodies.add(h.body());
        }
        return PatchouliEntryScan.joinCapped(
                bodies, PatchouliEntryScan.DEFAULT_MAX_ENTRIES, PatchouliEntryScan.DEFAULT_MAX_CHARS);
    }

    private static int langRank(String path, String preferredLang) {
        String p = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        String[] parts = p.split("/");
        String folderLang = null;
        for (int i = 0; i + 1 < parts.length; i++) {
            if ("entries".equals(parts[i + 1]) && i > 0) {
                folderLang = parts[i];
                break;
            }
        }
        if (folderLang == null) {
            return 2;
        }
        if (folderLang.equals(preferredLang)) {
            return 0;
        }
        if ("en_us".equals(folderLang)) {
            return 1;
        }
        return 3;
    }
}
