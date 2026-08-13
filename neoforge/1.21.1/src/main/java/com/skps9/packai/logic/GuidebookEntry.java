package com.skps9.packai.logic;

import java.util.List;
import java.util.Locale;

/**
 * One Patchouli／datapack guidebook entry — structured for index + Ask {@code [GUIDE]} pins.
 * Advisory narrative only; never overrides recipe／quest／unlock FACT.
 * Phase B: category + links + titleTokens.
 */
public record GuidebookEntry(
        String bookNs,
        String bookId,
        String entryId,
        String lang,
        String title,
        String textClip,
        List<String> linkedItems,
        String sourcePath,
        String categoryId,
        List<String> linksOut,
        List<String> linksIn,
        List<String> titleTokens
) {
    public GuidebookEntry {
        bookNs = nz(bookNs).toLowerCase(Locale.ROOT);
        bookId = nz(bookId);
        entryId = nz(entryId);
        lang = nz(lang).toLowerCase(Locale.ROOT);
        title = title == null ? "" : title;
        textClip = textClip == null ? "" : textClip;
        linkedItems = linkedItems == null ? List.of() : List.copyOf(linkedItems);
        sourcePath = sourcePath == null ? "" : sourcePath;
        categoryId = categoryId == null ? "" : categoryId.trim();
        linksOut = linksOut == null ? List.of() : List.copyOf(linksOut);
        linksIn = linksIn == null ? List.of() : List.copyOf(linksIn);
        titleTokens = titleTokens == null ? List.of() : List.copyOf(titleTokens);
    }

    /** Stable id for inverted map / dedupe: {@code bookNs/bookId/entryId}. */
    public String stableKey() {
        return bookNs + "/" + bookId + "/" + entryId;
    }

    /** Ask pin line prefix: {@code bookId/entryId | title}. */
    public String pinHeader() {
        String left = bookId.isEmpty() ? entryId : bookId + "/" + entryId;
        if (title == null || title.isBlank()) {
            return left;
        }
        return left + " | " + title.trim();
    }

    /** Copy with replaced backlinks (build-time enrich). */
    public GuidebookEntry withLinksIn(List<String> in) {
        return new GuidebookEntry(
                bookNs, bookId, entryId, lang, title, textClip, linkedItems, sourcePath,
                categoryId, linksOut, in, titleTokens);
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }
}
