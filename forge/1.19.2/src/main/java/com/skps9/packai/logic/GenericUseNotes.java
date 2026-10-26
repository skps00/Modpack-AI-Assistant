package com.skps9.packai.logic;

import java.util.ArrayList;
import java.util.List;

/** Catch-all / cosmetic INPUT uses collected from JEI lookup and recipe-card scans. */
public final class GenericUseNotes {
    private static final int MAX_NOTES = 4;

    public enum Kind {
        ANY_ITEM,
        COSMETIC
    }

    public record Note(String categoryTitle, String outputLabel, Kind kind) {}

    private static final List<Note> NOTES = new ArrayList<>();

    private GenericUseNotes() {}

    public static synchronized void clear() {
        NOTES.clear();
    }

    public static synchronized void add(String categoryTitle, String outputLabel, Kind kind) {
        if (categoryTitle == null || categoryTitle.isBlank() || kind == null) {
            return;
        }
        String cat = categoryTitle.trim();
        String out = outputLabel == null ? "" : outputLabel.trim();
        if (kind == Kind.ANY_ITEM && out.isEmpty()) {
            return;
        }
        for (Note n : NOTES) {
            if (n.categoryTitle().equals(cat) && n.kind() == kind) {
                return;
            }
        }
        if (NOTES.size() >= MAX_NOTES) {
            return;
        }
        NOTES.add(new Note(cat, out, kind));
    }

    public static synchronized List<Note> snapshot() {
        return List.copyOf(NOTES);
    }

    /** Headless tests only. */
    public static synchronized void addForTest(String categoryTitle, String outputLabel, Kind kind) {
        add(categoryTitle, outputLabel, kind);
    }
}
