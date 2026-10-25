package com.skps9.packai.logic;

/**
 * Card-reference validator: out-of-range refs dropped, in-range refs and plain numbers kept.
 * Run with -ea (gradle {@code runCardRefValidatorCheck}).
 */
public final class CardRefValidatorCheck {
    private CardRefValidatorCheck() {}

    /** Observed bug shape: 4 cards rendered, body cites a fifth. */
    private static final String CASE_A_BODY =
            "Smelt the dust in a furnace (card 5) and you get an ingot.";

    public static void main(String[] args) {
        outOfRangeParenRefRemoved();
        inRangeParenRefKept();
        ingredientNumbersUntouched();
        bracketRefRemoved();
        rangeRefs();
        noCardsStripsEveryRef();
        nonReferencesUntouched();
        noDoubleSpaceNoOrphanBracket();
        idempotentAndNullSafe();
        negativeControlDisabledStripFailsCaseA();
        System.out.println("CardRefValidatorCheck OK");
    }

    /** (a) 4 cards + '(card 5)' -> reference removed, sentence intact. */
    private static void outOfRangeParenRefRemoved() {
        String out = CardRefValidator.stripOutOfRangeCardRefs(CASE_A_BODY, 4);
        assert !out.contains("card 5") : out;
        assert !out.contains("(card") : out;
        assert out.equals("Smelt the dust in a furnace and you get an ingot.") : out;
        System.out.println("outOfRangeParenRefRemoved OK");
    }

    /** (b) 4 cards + '(card 3)' -> unchanged. */
    private static void inRangeParenRefKept() {
        String body = "Place the block (card 3) to finish the multiblock.";
        String out = CardRefValidator.stripOutOfRangeCardRefs(body, 4);
        assert out.equals(body) : out;
        String bare = "Place the block card 3 to finish the multiblock.";
        assert CardRefValidator.stripOutOfRangeCardRefs(bare, 4).equals(bare) : "bare ref 3 of 4";
        System.out.println("inRangeParenRefKept OK");
    }

    /** (c) '4 Ender Chest + 4 Hellshelf and card 2' with 4 cards: numbers untouched, ref kept. */
    private static void ingredientNumbersUntouched() {
        String body = "4 Ender Chest + 4 Hellshelf and card 2 makes the frame.";
        String kept = CardRefValidator.stripOutOfRangeCardRefs(body, 4);
        assert kept.equals(body) : kept;
        // Same body with a single card: only 'card 2' goes, ingredient counts stay.
        String cut = CardRefValidator.stripOutOfRangeCardRefs(body, 1);
        assert !cut.contains("card 2") : cut;
        assert cut.startsWith("4 Ender Chest + 4 Hellshelf and") : cut;
        assert cut.contains("makes the frame.") : cut;
        assert cut.equals("4 Ender Chest + 4 Hellshelf and makes the frame.") : cut;
        // Digits are never the target on their own.
        String numbersOnly = "4 Ender Chest + 4 Hellshelf, 3x speed, 1.5 reach";
        assert CardRefValidator.stripOutOfRangeCardRefs(numbersOnly, 0).equals(numbersOnly) : "numbers";
        System.out.println("ingredientNumbersUntouched OK");
    }

    /** (d) '[card:9]' with 2 cards -> removed. */
    private static void bracketRefRemoved() {
        String body = "See [card:9] for the layout.";
        String out = CardRefValidator.stripOutOfRangeCardRefs(body, 2);
        assert !out.contains("[card:9]") : out;
        assert out.equals("See for the layout.") : out;
        String lineStart = "[card:9] Smelt it in a furnace.";
        assert CardRefValidator.stripOutOfRangeCardRefs(lineStart, 2)
                .equals("Smelt it in a furnace.") : "line-start ref";
        String twoLines = "Lead line.\n[card:9] smelt it.\nTail line.";
        String twoLinesOut = CardRefValidator.stripOutOfRangeCardRefs(twoLines, 2);
        assert twoLinesOut.equals("Lead line.\nsmelt it.\nTail line.") : twoLinesOut;
        System.out.println("bracketRefRemoved OK");
    }

    /** Ranges: highest number decides; hyphen / en dash / em dash / tilde separators. */
    private static void rangeRefs() {
        String over = "Open (cards 3-5) for the steps.";
        assert CardRefValidator.stripOutOfRangeCardRefs(over, 4).equals("Open for the steps.")
                : CardRefValidator.stripOutOfRangeCardRefs(over, 4);
        String inside = "Open (cards 1-4) for the steps.";
        assert CardRefValidator.stripOutOfRangeCardRefs(inside, 4).equals(inside) : "range within cards";
        String bareEnDash = "Open cards 2\u20135 for the steps.";
        assert CardRefValidator.stripOutOfRangeCardRefs(bareEnDash, 4).equals("Open for the steps.")
                : CardRefValidator.stripOutOfRangeCardRefs(bareEnDash, 4);
        String bracketRange = "Check [card:1-9] now.";
        assert CardRefValidator.stripOutOfRangeCardRefs(bracketRange, 4).equals("Check now.")
                : CardRefValidator.stripOutOfRangeCardRefs(bracketRange, 4);
        System.out.println("rangeRefs OK");
    }

    /** cardsOut <= 0 strips every card reference, whichever shape. */
    private static void noCardsStripsEveryRef() {
        String body = "Use (card 1) and card 2, then [card:3].";
        String out = CardRefValidator.stripOutOfRangeCardRefs(body, 0);
        assert !out.contains("card") : out;
        assert out.equals("Use and, then.") : out;
        String negative = "Use (card 1).";
        assert CardRefValidator.stripOutOfRangeCardRefs(negative, -3).equals("Use.") : "negative cardsOut";
        System.out.println("noCardsStripsEveryRef OK");
    }

    /** Words that merely end in 'card' or carry numbers are not references. */
    private static void nonReferencesUntouched() {
        String body = "Discard 5 items, postcard 5 times, use 3x speed and 1.5 reach.";
        assert CardRefValidator.stripOutOfRangeCardRefs(body, 0).equals(body) : "lookalikes";
        String cardOneDotFive = "The card 1.5 is a joke, cardigan 4 too.";
        assert CardRefValidator.stripOutOfRangeCardRefs(cardOneDotFive, 0).equals(cardOneDotFive)
                : "decimal / cardigan";
        System.out.println("nonReferencesUntouched OK");
    }

    /** Cleanup contract: no doubled spaces, no empty brackets, no space before punctuation. */
    private static void noDoubleSpaceNoOrphanBracket() {
        String[] bodies = {
            CASE_A_BODY,
            "See [card:9] for the layout.",
            "Use (card 1) and card 2, then [card:3].",
            "Open (cards 3-5) for the steps.",
            "(card 9) Smelt it.",
            "Look at (see card 9), then craft.",
        };
        for (String body : bodies) {
            String out = CardRefValidator.stripOutOfRangeCardRefs(body, 1);
            assert !out.contains("  ") : body + " -> " + out;
            assert !out.contains("()") : body + " -> " + out;
            assert !out.contains("[]") : body + " -> " + out;
            assert !out.contains(" .") : body + " -> " + out;
            assert !out.contains(",,") : body + " -> " + out;
            assert !out.startsWith(" ") : body + " -> " + out;
        }
        String trailing = "Smelt it (card 9).";
        assert CardRefValidator.stripOutOfRangeCardRefs(trailing, 1).equals("Smelt it.") : "trailing ref";
        String beforeComma = "Smelt it (card 9), then wait.";
        assert CardRefValidator.stripOutOfRangeCardRefs(beforeComma, 1).equals("Smelt it, then wait.")
                : CardRefValidator.stripOutOfRangeCardRefs(beforeComma, 1);
        System.out.println("noDoubleSpaceNoOrphanBracket OK");
    }

    private static void idempotentAndNullSafe() {
        String once = CardRefValidator.stripOutOfRangeCardRefs(CASE_A_BODY, 4);
        String twice = CardRefValidator.stripOutOfRangeCardRefs(once, 4);
        assert once.equals(twice) : "must be idempotent";
        assert CardRefValidator.stripOutOfRangeCardRefs(null, 4).isEmpty() : "null body";
        assert CardRefValidator.stripOutOfRangeCardRefs("", 4).isEmpty() : "empty body";
        assert CardRefValidator.stripOutOfRangeCardRefs("No refs here.", 0).equals("No refs here.")
                : "ref-free body";
        System.out.println("idempotentAndNullSafe OK");
    }

    /** (e) Negative control: with the strip disabled, case (a) must keep '(card 5)' and fail. */
    private static void negativeControlDisabledStripFailsCaseA() {
        String guarded = CardRefValidator.stripOutOfRangeCardRefs(CASE_A_BODY, 4);
        assert !guarded.contains("(card 5)") : guarded;
        CardRefValidator.testDisableStrip = true;
        try {
            String unguarded = CardRefValidator.stripOutOfRangeCardRefs(CASE_A_BODY, 4);
            if (!unguarded.contains("(card 5)")) {
                throw new AssertionError("negative control FAILED: strip disabled but ref still gone");
            }
            assert unguarded.equals(CASE_A_BODY) : unguarded;
            if (unguarded.equals(guarded)) {
                throw new AssertionError("negative control FAILED: strip had no effect on case (a)");
            }
            System.out.println("negativeControlDisabledStripFailsCaseA OK");
        } finally {
            CardRefValidator.testDisableStrip = false;
        }
    }
}
