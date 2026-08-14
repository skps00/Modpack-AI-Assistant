package com.skps9.packai.logic;

import java.util.List;

/** Index miss → API pin path. Run with -ea. No Minecraft / Patchouli. */
public final class PatchouliApiFallbackCheck {
    private PatchouliApiFallbackCheck() {}

    public static void main(String[] args) {
        indexWins();
        apiOnMiss();
        bothBlank();
        apiPinFormat();
        System.out.println("PatchouliApiFallbackCheck OK");
    }

    private static void indexWins() {
        assert "idx".equals(GuidebookPins.preferIndexThenApi("idx", "api"));
        assert "idx".equals(GuidebookPins.preferIndexThenApi(" idx ", "api"));
    }

    private static void apiOnMiss() {
        assert "api".equals(GuidebookPins.preferIndexThenApi("", "api"));
        assert "api".equals(GuidebookPins.preferIndexThenApi("  ", " api "));
        assert "api".equals(GuidebookPins.preferIndexThenApi(null, "api"));
    }

    private static void bothBlank() {
        assert GuidebookPins.preferIndexThenApi("", "").isEmpty();
        assert GuidebookPins.preferIndexThenApi(null, null).isEmpty();
        assert GuidebookPins.preferIndexThenApi("  ", "  ").isEmpty();
    }

    private static void apiPinFormat() {
        GuidebookEntry e = GuidebookPins.apiFallbackEntry(
                "goety",
                "black_book",
                "cursed_ingot",
                "Cursed Ingot",
                "Smelt at the shade.",
                "goety:cursed_ingot");
        assert "patchouli:api".equals(e.sourcePath()) : e.sourcePath();
        assert "cursed_ingot".equals(e.entryId()) : e.entryId();
        assert e.linkedItems().contains("goety:cursed_ingot") : e.linkedItems();
        String pin = GuidebookPins.formatPins(List.of(e), "goety:cursed_ingot");
        assert pin.contains("Cursed Ingot") : pin;
        assert pin.contains("Smelt at the shade") : pin;
        assert pin.contains("black_book") : pin;
        assert GuidebookPins.preferIndexThenApi("", pin).equals(pin);
        assert GuidebookPins.preferIndexThenApi("disk-hit", pin).equals("disk-hit");
    }
}
