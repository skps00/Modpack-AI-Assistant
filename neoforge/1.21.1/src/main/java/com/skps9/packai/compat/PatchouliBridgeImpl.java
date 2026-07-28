package com.skps9.packai.compat;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mojang.datafixers.util.Pair;

import net.minecraft.world.item.ItemStack;

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
 */
public final class PatchouliBridgeImpl {
    private PatchouliBridgeImpl() {}

    public static String lookupGuideText(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        Set<BookEntry> hits = new LinkedHashSet<>();
        try {
            for (Book book : BookRegistry.INSTANCE.books.values()) {
                if (book == null) {
                    continue;
                }
                // Extension books share target contents — skip to avoid duplicate hits.
                try {
                    var ext = book.getClass().getField("isExtension").getBoolean(book);
                    if (ext) {
                        continue;
                    }
                } catch (Throwable ignored) {
                    // field may be inaccessible / renamed — still try getContents
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
            if (v instanceof IVariable iv) {
                String s = iv.asString();
                return s == null ? "" : s;
            }
        } catch (Throwable ignored) {
            // ponytail: reflection on protected IVariable — upgrade if Patchouli exposes getter
        }
        return "";
    }
}
