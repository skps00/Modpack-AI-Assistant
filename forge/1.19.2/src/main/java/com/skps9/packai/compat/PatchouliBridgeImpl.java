package com.skps9.packai.compat;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.mojang.datafixers.util.Pair;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.skps9.packai.logic.GuidebookPins;
import com.skps9.packai.logic.PatchouliEntryScan;

import vazkii.patchouli.api.IVariable;
import vazkii.patchouli.client.book.BookContents;
import vazkii.patchouli.client.book.BookEntry;
import vazkii.patchouli.client.book.BookPage;
import vazkii.patchouli.client.book.page.abstr.PageWithText;
import vazkii.patchouli.common.book.Book;
import vazkii.patchouli.common.book.BookRegistry;

/**
 * Patchouli client book lookup. Loaded only via {@link PatchouliBridge} Class.forName.
 * Extension books skipped. Ask path: only when guidebook index misses.
 */
public final class PatchouliBridgeImpl {
    private PatchouliBridgeImpl() {}

    public static String lookupGuideText(ItemStack stack) {
        return lookupGuideText(stack, GuidebookPins.SCOPE_ANY_MOD, null);
    }

    public static String lookupGuideText(ItemStack stack, String guidebookScope, String itemNs) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String scope = GuidebookPins.normalizeScope(guidebookScope);
        Set<BookEntry> hits = new LinkedHashSet<>();
        try {
            for (Book book : BookRegistry.INSTANCE.books.values()) {
                if (book == null) {
                    continue;
                }
                try {
                    var ext = book.getClass().getField("isExtension").getBoolean(book);
                    if (ext) {
                        continue; // policy: skip extension books
                    }
                } catch (Throwable ignored) {
                    // continue
                }
                String bookNs = bookNamespace(book);
                if (GuidebookPins.SCOPE_SAME_MOD.equals(scope)
                        && itemNs != null
                        && !itemNs.isBlank()
                        && !bookNs.isEmpty()
                        && !bookNs.equalsIgnoreCase(itemNs)) {
                    // same_mod: require mappable bookNs == itemNs; unmapped → keep (Patchouli matched stack)
                    continue;
                }
                BookContents contents = book.getContents();
                if (contents == null || contents.isErrored()) {
                    continue;
                }
                Pair<BookEntry, Integer> mapped = contents.getEntryForStack(stack);
                if (mapped != null && mapped.getFirst() != null) {
                    hits.add(mapped.getFirst());
                }
            }
        } catch (Throwable t) {
            return "";
        }
        List<String> bodies = new ArrayList<>();
        for (BookEntry entry : hits) {
            String body = textFromEntry(entry);
            if (body != null && !body.isBlank()) {
                bodies.add(body);
            }
        }
        return PatchouliEntryScan.joinCapped(
                bodies, PatchouliEntryScan.DEFAULT_MAX_ENTRIES, PatchouliEntryScan.DEFAULT_MAX_CHARS);
    }

    private static String bookNamespace(Book book) {
        try {
            Object id = book.getClass().getField("id").get(book);
            if (id instanceof ResourceLocation rl) {
                return rl.getNamespace().toLowerCase(Locale.ROOT);
            }
            if (id != null) {
                String s = id.toString();
                int colon = s.indexOf(':');
                if (colon > 0) {
                    return s.substring(0, colon).toLowerCase(Locale.ROOT);
                }
            }
        } catch (Throwable ignored) {
            // unmapped
        }
        return "";
    }

    private static String textFromEntry(BookEntry entry) {
        List<String> parts = new ArrayList<>();
        try {
            String name = entry.getName().getString();
            if (name != null && !name.isBlank()) {
                parts.add(name.trim());
            }
            for (Object pageObj : entry.getPages()) {
                if (!(pageObj instanceof BookPage page)) {
                    continue;
                }
                if (!(page instanceof PageWithText)) {
                    continue;
                }
                String text = readPageText((PageWithText) page);
                if (text != null && !text.isBlank()) {
                    parts.add(PatchouliEntryScan.stripMacros(text));
                }
            }
        } catch (Throwable t) {
            return "";
        }
        return String.join("\n", parts).trim();
    }

    private static String readPageText(PageWithText page) {
        try {
            Field f = PageWithText.class.getDeclaredField("text");
            f.setAccessible(true);
            Object v = f.get(page);
            if (!(v instanceof IVariable iv)) {
                return "";
            }
            // Patchouli UI resolves via as(Component); asString() leaves raw lang keys
            // (e.g. ars_nouveau.page.scryers_oculus) — LLM then invents decoration.
            try {
                Object comp = iv.as(net.minecraft.network.chat.Component.class);
                if (comp instanceof net.minecraft.network.chat.Component c) {
                    String s = c.getString();
                    if (s != null && !s.isBlank()) {
                        return GuidebookPins.resolveDisplayString(s.trim());
                    }
                }
            } catch (Throwable ignored) {
                // fall through to asString + I18n
            }
            String raw = iv.asString();
            return GuidebookPins.resolveDisplayString(raw == null ? "" : raw);
        } catch (Throwable ignored) {
            // ponytail: reflection on protected IVariable — upgrade if Patchouli exposes getter
        }
        return "";
    }
}
