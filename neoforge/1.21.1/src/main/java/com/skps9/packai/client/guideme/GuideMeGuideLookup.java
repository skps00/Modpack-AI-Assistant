package com.skps9.packai.client.guideme;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;

import com.skps9.packai.compat.GuideMeBridge;
import com.skps9.packai.logic.GuideMePageScan;
import com.skps9.packai.logic.ReplyLang;

/**
 * Resolve GuideME page text for a focus stack: live ItemIndex when present,
 * else scan {@code guides/**\/*.md} from the resource manager (frontmatter item_ids).
 */
public final class GuideMeGuideLookup {
    private GuideMeGuideLookup() {}

    public static String lookup(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String fromApi = GuideMeBridge.lookupGuideText(stack);
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
                    "guides",
                    loc -> {
                        String p = loc.getPath();
                        return p != null && p.endsWith(".md");
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
            int langRank = GuideMePageScan.langRank(loc.getPath(), lang);
            try (var in = e.getValue().open();
                    var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                String md = readAll(reader);
                int score = GuideMePageScan.matchScore(md, itemId);
                if (score <= 0) {
                    continue;
                }
                String body = GuideMePageScan.extractPlainText(md);
                if (body.isBlank()) {
                    continue;
                }
                hits.add(new Hit(score, langRank, body));
            } catch (Throwable ignored) {
                // skip bad page
            }
        }
        hits.sort(Comparator.comparingInt(Hit::score).reversed()
                .thenComparingInt(Hit::langRank));
        List<String> bodies = new ArrayList<>();
        for (Hit h : hits) {
            bodies.add(h.body());
        }
        return GuideMePageScan.joinCapped(
                bodies, GuideMePageScan.DEFAULT_MAX_ENTRIES, GuideMePageScan.DEFAULT_MAX_CHARS);
    }

    private static String readAll(InputStreamReader reader) throws java.io.IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = reader.read(buf)) >= 0) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }
}
