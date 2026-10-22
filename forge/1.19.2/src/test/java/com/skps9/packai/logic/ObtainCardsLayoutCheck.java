package com.skps9.packai.logic;

import java.util.List;

import com.skps9.packai.client.gui.CardListLayout;
import com.skps9.packai.client.gui.CardListLayout.Card;

/**
 * Headless checks for {@link CardListLayout}.
 */
public final class ObtainCardsLayoutCheck {
    private ObtainCardsLayoutCheck() {}

    public static void main(String[] args) {
        cardHeightCases();
        layoutTops();
        maxScrollCase();
        nextIndexCases();
        snapTopConsistency();
        System.out.println("ObtainCardsLayoutCheck OK");
    }

    static void cardHeightCases() {
        if (CardListLayout.cardHeight(0) != 66) {
            fail("cardHeight(0)");
        }
        if (CardListLayout.cardHeight(3) != 108) {
            fail("cardHeight(3)");
        }
        System.out.println("cardHeightCases OK");
    }

    static void layoutTops() {
        int[] rows = {0, 2, 0};
        List<Card> cards = CardListLayout.layout(3, rows, 0);
        if (cards.size() != 3) {
            fail("layout size");
        }
        if (cards.get(0).top() != 0 || cards.get(0).height() != 66) {
            fail("card0");
        }
        int top1 = 66 + CardListLayout.CARD_GAP;
        if (cards.get(1).top() != top1 || cards.get(1).height() != CardListLayout.cardHeight(2)) {
            fail("card1");
        }
        int top2 = top1 + cards.get(1).height() + CardListLayout.CARD_GAP;
        if (cards.get(2).top() != top2 || cards.get(2).height() != 66) {
            fail("card2");
        }
        System.out.println("layoutTops OK");
    }

    static void maxScrollCase() {
        int[] rows = {0, 2, 0};
        int total = 66 + CardListLayout.CARD_GAP + CardListLayout.cardHeight(2)
                + CardListLayout.CARD_GAP + 66;
        if (total != 246) {
            fail("total height expected 246 got " + total);
        }
        int max = CardListLayout.maxScroll(3, rows, 120);
        if (max != 126) {
            fail("maxScroll expected 126 got " + max);
        }
        System.out.println("maxScrollCase OK");
    }

    static void nextIndexCases() {
        List<Card> cards = CardListLayout.layout(3, new int[] {0, 0, 0}, 0);
        if (CardListLayout.nextIndex(cards, 0, 1) != 1) {
            fail("next +1 from 0");
        }
        if (CardListLayout.nextIndex(cards, 2, 1) != 2) {
            fail("next +1 from last");
        }
        if (CardListLayout.nextIndex(cards, 0, -1) != 0) {
            fail("next -1 from 0");
        }
        System.out.println("nextIndexCases OK");
    }

    static void snapTopConsistency() {
        int scrollY = 40;
        List<Card> atZero = CardListLayout.layout(3, new int[] {0, 2, 0}, 0);
        List<Card> scrolled = CardListLayout.layout(3, new int[] {0, 2, 0}, scrollY);
        int snap = CardListLayout.snapTop(atZero, 1);
        int expectedTopAtZero = atZero.get(1).top();
        if (snap != expectedTopAtZero) {
            fail("snapTop vs card top at zero scroll");
        }
        if (scrolled.get(1).top() + scrollY != expectedTopAtZero) {
            fail("scrolled top + scrollY consistency");
        }
        System.out.println("snapTopConsistency OK");
    }

    private static void fail(String msg) {
        System.out.println("FAIL: " + msg);
        throw new AssertionError(msg);
    }
}
