package com.skps9.packai.client.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure layout math for obtain-route card list (no Minecraft dependencies).
 */
public final class CardListLayout {
    public static final int CARD_GAP = 10;
    public static final int PITCH_MIN = 14;

    private CardListLayout() {}

    public record Card(int stepIndex, int top, int height) {}

    /** Visible card body height for {@code requirementRows} requirement lines. */
    public static int cardHeight(int requirementRows) {
        return 66 + PITCH_MIN * Math.max(0, requirementRows);
    }

    public static List<Card> layout(int cardCount, int[] reqRows, int scrollY) {
        List<Card> out = new ArrayList<>(cardCount);
        int y = 0;
        for (int i = 0; i < cardCount; i++) {
            int rows = reqRows != null && i < reqRows.length ? reqRows[i] : 0;
            int h = cardHeight(rows);
            out.add(new Card(i, y - scrollY, h));
            y += h;
            if (i < cardCount - 1) {
                y += CARD_GAP;
            }
        }
        return out;
    }

    public static int contentHeight(int cardCount, int[] reqRows) {
        if (cardCount <= 0) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < cardCount; i++) {
            int rows = reqRows != null && i < reqRows.length ? reqRows[i] : 0;
            total += cardHeight(rows);
            if (i < cardCount - 1) {
                total += CARD_GAP;
            }
        }
        return total;
    }

    public static int maxScroll(int cardCount, int[] reqRows, int viewportH) {
        return Math.max(0, contentHeight(cardCount, reqRows) - viewportH);
    }

    public static int nextIndex(int cardCount, int currentIndex, int direction) {
        if (cardCount <= 0) {
            return 0;
        }
        int last = cardCount - 1;
        if (direction > 0) {
            return Math.min(last, currentIndex + 1);
        }
        if (direction < 0) {
            return Math.max(0, currentIndex - 1);
        }
        return Math.max(0, Math.min(last, currentIndex));
    }

    public static int nextIndex(List<Card> cards, int currentIndex, int direction) {
        return nextIndex(cards != null ? cards.size() : 0, currentIndex, direction);
    }

    /** Scroll offset that places {@code index} at the top of the viewport (layout used {@code scrollY = 0}). */
    public static int snapTop(List<Card> cards, int index) {
        if (cards == null || cards.isEmpty() || index <= 0) {
            return 0;
        }
        int i = Math.min(index, cards.size() - 1);
        Card c = cards.get(i);
        if (c.top() >= 0) {
            return c.top();
        }
        int scroll = 0;
        for (int j = 0; j < i; j++) {
            scroll += cards.get(j).height();
            if (j < cards.size() - 1) {
                scroll += CARD_GAP;
            }
        }
        return scroll;
    }

    /** Largest index whose snap offset does not exceed {@code maxScrollY}. */
    public static int maxIndexForScroll(List<Card> cards, int maxScrollY) {
        if (cards == null || cards.isEmpty()) {
            return 0;
        }
        int best = 0;
        for (int i = 0; i < cards.size(); i++) {
            if (snapTop(cards, i) <= maxScrollY) {
                best = i;
            }
        }
        return best;
    }
}
