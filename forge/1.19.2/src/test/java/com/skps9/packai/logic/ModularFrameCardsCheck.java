package com.skps9.packai.logic;

/**
 * Headless B11: ModularFrameCards pure core + AskLoopState skip-auto-emit.
 * Run with -ea. No AskToolEnv／RecipeCard（ItemStack bootstrap）.
 */
public final class ModularFrameCardsCheck {
    private ModularFrameCardsCheck() {}

    private static final String FOCUS = "tetra:modular_sword";

    public static void main(String[] args) {
        dropCoreCases();
        skipAutoEmit();
        crossLayerSharedCore();
        // Negative: blank drop never kills; substring must not match
        assert !ModularFrameCards.shouldDropFrameCard("", FOCUS, false, false) : "blank drop";
        assert !ModularFrameCards.shouldDropFrameCard(null, FOCUS, false, false) : "null drop";
        assert !ModularFrameCards.shouldDropFrameCard(FOCUS, "tetra:modular", false, false)
                : "substring must not drop";
        keepCardCases();
        System.out.println("ModularFrameCardsCheck OK");
    }

    /** S3 ①–⑥ as pure shouldDropFrameCard table. */
    static void dropCoreCases() {
        // ① modular focus + frame-shaped → drop
        assert ModularFrameCards.shouldDropFrameCard(FOCUS, FOCUS, false, false) : "① frame";
        // ② modular + non-frame card → keep
        assert !ModularFrameCards.shouldDropFrameCard(FOCUS, "minecraft:stick", false, false) : "② legit";
        // ③ non-modular focus (blank drop) + same card → keep (overkill negative)
        assert !ModularFrameCards.shouldDropFrameCard("", FOCUS, false, false) : "③ non-modular";
        // ④ [frame, legit, frame, legit] → drop/keep dense intent
        boolean[] wantDrop = {true, false, true, false};
        String[] outs = {FOCUS, "ftbquests:coin", FOCUS, "minecraft:netherite_ingot"};
        int keep = 0;
        for (int i = 0; i < outs.length; i++) {
            boolean drop = ModularFrameCards.shouldDropFrameCard(FOCUS, outs[i], false, false);
            assert drop == wantDrop[i] : "④ i=" + i;
            if (!drop) {
                keep++;
            }
        }
        assert keep == 2 : "④ keep=" + keep;
        // ⑤ input-use with output==focus → keep
        assert !ModularFrameCards.shouldDropFrameCard(FOCUS, FOCUS, true, false) : "⑤ inputUse";
        // ⑥ trailing optional (maintenance/upgrade) → keep
        assert !ModularFrameCards.shouldDropFrameCard(FOCUS, FOCUS, false, true) : "⑥ trailing";
        System.out.println("dropCoreCases OK");
    }

    static void skipAutoEmit() {
        AskLoopState loop = AskLoopState.start("q", FOCUS, java.util.List.of(), 0L);
        assert !loop.shouldSkipAutoEmit() : "fresh";
        loop.noteSuppressedFrameOffer();
        assert loop.shouldSkipAutoEmit() : "suppressed+empty emissions";
        assert loop.suppressedFrameOffers() == 1 : "count";
        AskLoopState loop2 = AskLoopState.start("q", FOCUS, java.util.List.of(), 0L);
        assert !loop2.shouldSkipAutoEmit() : "zero suppress";
        System.out.println("skipAutoEmit OK");
    }

    /** S4: shared core is the only drop predicate both layers must call (pin via python). */
    static void crossLayerSharedCore() {
        record Row(String drop, String out, boolean in, boolean trail, boolean dropWanted) {}
        Row[] rows = {
                new Row(FOCUS, FOCUS, false, false, true),
                new Row(FOCUS, "a:b", false, false, false),
                new Row(FOCUS, FOCUS, true, false, false),
                new Row(FOCUS, FOCUS, false, true, false),
                new Row("", FOCUS, false, false, false),
                new Row(FOCUS, FOCUS.toUpperCase(java.util.Locale.ROOT), false, false, true),
        };
        for (Row r : rows) {
            boolean d = ModularFrameCards.shouldDropFrameCard(r.drop, r.out, r.in, r.trail);
            assert d == r.dropWanted : r;
        }
        System.out.println("crossLayerSharedCore OK");
    }

    /**
     * Keep-1. Negative control: exact card is last, not first. If {@code isStandardKeepCard}
     * always returned false, fallback would keep tag F0 and the EXACT assert would fail.
     */
    static void keepCardCases() {
        String out = "tetra:modular_double";
        String oak = "minecraft:oak_planks";
        String stick = "minecraft:stick";
        java.util.List<String> need = java.util.List.of(oak, stick);
        assert ModularFrameCards.isStandardKeepCard(out, java.util.List.of(oak, stick), out, need) : "hit";
        assert ModularFrameCards.isStandardKeepCard(
                out.toUpperCase(java.util.Locale.ROOT),
                java.util.List.of(oak.toUpperCase(java.util.Locale.ROOT), stick),
                out, need) : "case";
        assert !ModularFrameCards.isStandardKeepCard(out, java.util.List.of(oak), out, need) : "missing input";
        assert !ModularFrameCards.isStandardKeepCard(out, need, "", need) : "blank keep out";
        assert !ModularFrameCards.isStandardKeepCard(out, need, null, need) : "null keep out";
        assert !ModularFrameCards.isStandardKeepCard(
                out, java.util.List.of(oak + "_extra", stick), out, need) : "substring";

        record Row(String id, java.util.List<String> ins, boolean pin, String tag) {}
        java.util.ArrayList<Row> twenty = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            java.util.List<String> ins = i == 19 ? need : java.util.List.of(oak);
            twenty.add(new Row(out, ins, false, i == 19 ? "EXACT" : "F" + i));
        }
        ModularFrameCards.KeepResult<Row> exact = ModularFrameCards.keepOnlyStandardRecipeCard(
                twenty, Row::id, Row::ins, Row::pin, out, need);
        assert exact.dropped() == 19 : "dropped=" + exact.dropped();
        assert !exact.fallback() : "exact must not fallback";
        assert exact.kept().size() == 1 && "EXACT".equals(exact.kept().get(0).tag()) : "exact card";

        java.util.List<Row> noExact = java.util.List.of(
                new Row(out, java.util.List.of("a:b"), false, "FIRST"),
                new Row(out, java.util.List.of("c:d"), false, "SECOND"));
        ModularFrameCards.KeepResult<Row> fb = ModularFrameCards.keepOnlyStandardRecipeCard(
                noExact, Row::id, Row::ins, Row::pin, out, need);
        assert fb.fallback() : "fallback flag";
        assert fb.dropped() == 1 : "fallback dropped";
        assert fb.kept().size() == 1 && "FIRST".equals(fb.kept().get(0).tag()) : "fallback first";

        java.util.List<Row> uses = java.util.List.of(
                new Row("mrqx_extra_pack:mystery_craftsmanship", java.util.List.of("x:y"), false, "USE"));
        ModularFrameCards.KeepResult<Row> none = ModularFrameCards.keepOnlyStandardRecipeCard(
                uses, Row::id, Row::ins, Row::pin, out, need);
        assert none.dropped() == 0 && !none.fallback() : "no frame dropped";
        assert none.kept().size() == 1 && "USE".equals(none.kept().get(0).tag()) : "uses kept";

        java.util.List<Row> pinned = java.util.List.of(
                new Row(out, java.util.List.of(oak), true, "PIN"),
                new Row(out, java.util.List.of("nope"), false, "DROP"),
                new Row(out, need, false, "EXACT"),
                new Row("minecraft:stick", java.util.List.of("z"), false, "OTHER"));
        ModularFrameCards.KeepResult<Row> pinR = ModularFrameCards.keepOnlyStandardRecipeCard(
                pinned, Row::id, Row::ins, Row::pin, out, need);
        boolean sawPin = false;
        boolean sawDrop = false;
        for (Row r : pinR.kept()) {
            if ("PIN".equals(r.tag())) {
                sawPin = true;
            }
            if ("DROP".equals(r.tag())) {
                sawDrop = true;
            }
        }
        assert sawPin : "mustKeep dropped";
        assert !sawDrop : "non-keep frame survived";
        assert pinR.kept().size() == 3 : "pin size " + pinR.kept().size();

        ModularFrameCards.KeepResult<Row> blank = ModularFrameCards.keepOnlyStandardRecipeCard(
                twenty, Row::id, Row::ins, Row::pin, "  ", need);
        assert blank.dropped() == 0 && !blank.fallback() && blank.kept().size() == 20 : "blank out";
        ModularFrameCards.KeepResult<Row> nullOut = ModularFrameCards.keepOnlyStandardRecipeCard(
                twenty, Row::id, Row::ins, Row::pin, null, need);
        assert nullOut.dropped() == 0 && !nullOut.fallback() && nullOut.kept().size() == 20 : "null out";
        System.out.println("keepCardCases OK");
    }
}
