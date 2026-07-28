package com.skps9.packai.compat;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.skps9.packai.logic.GuideMePageScan;

import guideme.Guide;
import guideme.Guides;
import guideme.PageAnchor;
import guideme.compiler.Frontmatter;
import guideme.compiler.FrontmatterNavigation;
import guideme.compiler.ParsedGuidePage;
import guideme.indices.ItemIndex;

/**
 * GuideME live ItemIndex lookup. Loaded only via {@link GuideMeBridge} Class.forName.
 */
public final class GuideMeBridgeImpl {
    private GuideMeBridgeImpl() {}

    public static String lookupGuideText(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) {
            return "";
        }
        Set<ResourceLocation> seenPages = new LinkedHashSet<>();
        List<String> bodies = new ArrayList<>();
        try {
            for (Guide guide : Guides.getAll()) {
                if (guide == null) {
                    continue;
                }
                ItemIndex index;
                try {
                    index = guide.getIndex(ItemIndex.class);
                } catch (Throwable t) {
                    continue;
                }
                if (index == null) {
                    continue;
                }
                PageAnchor anchor = index.get(itemId);
                if (anchor == null || anchor.pageId() == null) {
                    continue;
                }
                if (!seenPages.add(anchor.pageId())) {
                    continue;
                }
                ParsedGuidePage page = guide.getParsedPage(anchor.pageId());
                if (page == null) {
                    continue;
                }
                String body = textFromPage(page);
                if (body != null && !body.isBlank()) {
                    bodies.add(body);
                }
            }
        } catch (Throwable t) {
            return "";
        }
        return GuideMePageScan.joinCapped(
                bodies, GuideMePageScan.DEFAULT_MAX_ENTRIES, GuideMePageScan.DEFAULT_MAX_CHARS);
    }

    private static String textFromPage(ParsedGuidePage page) {
        List<String> parts = new ArrayList<>();
        try {
            Frontmatter fm = page.getFrontmatter();
            if (fm != null) {
                FrontmatterNavigation nav = fm.navigationEntry();
                if (nav != null && nav.title() != null && !nav.title().isBlank()) {
                    parts.add(nav.title().trim());
                }
            }
            String source = readSource(page);
            String plain = GuideMePageScan.stripMarkdown(GuideMePageScan.stripFrontmatter(source));
            if (!plain.isBlank()) {
                parts.add(plain);
            }
        } catch (Throwable t) {
            return "";
        }
        return String.join("\n", parts).trim();
    }

    private static String readSource(ParsedGuidePage page) {
        try {
            // ponytail: ParsedGuidePage.source is package-private — no public getter
            Field f = ParsedGuidePage.class.getDeclaredField("source");
            f.setAccessible(true);
            Object v = f.get(page);
            return v instanceof String s ? s : "";
        } catch (Throwable ignored) {
            return "";
        }
    }
}
