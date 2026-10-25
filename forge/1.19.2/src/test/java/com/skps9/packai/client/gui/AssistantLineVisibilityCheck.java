package com.skps9.packai.client.gui;

/**
 * Headless geometry for assistant chat line visibility and held-strip icon placement (work order AY).
 */
public final class AssistantLineVisibilityCheck {
    private static final int CARD_H = 168;
    private static final int TEXT_H = 12;

    private AssistantLineVisibilityCheck() {}

    public static void main(String[] args) {
        checkPartialRecipeCardVisible();
        checkRecipeCardFullyOutside();
        checkTextLinePartiallyVisibleStillHidden();
        checkHeldStripIconClampedAboveDisclaimer();
        checkNegativeControlLegacyCardVisibility();
        System.out.println("AssistantLineVisibilityCheck OK");
    }

    private static void checkPartialRecipeCardVisible() {
        int chatTop = 0;
        int contentBottom = 120;
        int y = chatTop;
        assert AiAssistantScreen.chatLineDrawable(y, CARD_H, chatTop, contentBottom, true)
                : "168px card top-aligned in 120px viewport must be drawable";
    }

    private static void checkRecipeCardFullyOutside() {
        int chatTop = 0;
        int contentBottom = 120;
        assert !AiAssistantScreen.chatLineDrawable(200, CARD_H, chatTop, contentBottom, true)
                : "card below viewport must not draw";
        assert !AiAssistantScreen.chatLineDrawable(-200, CARD_H, chatTop, contentBottom, true)
                : "card above viewport must not draw";
    }

    private static void checkTextLinePartiallyVisibleStillHidden() {
        int chatTop = 0;
        int contentBottom = 120;
        int y = chatTop;
        assert !AiAssistantScreen.chatLineDrawable(y, TEXT_H + 200, chatTop, contentBottom, false)
                : "text line must stay all-or-nothing when taller than viewport";
        int peekY = contentBottom - 4;
        assert !AiAssistantScreen.chatLineDrawable(peekY, TEXT_H, chatTop, contentBottom, false)
                : "text line with bottom past contentBottom must not draw";
    }

    private static void checkHeldStripIconClampedAboveDisclaimer() {
        int chatContentBottom = 400;
        int maxTop = AiAssistantScreen.heldStripMaxIconTopY(chatContentBottom);
        int focusRowY = chatContentBottom + 50;
        int rowY = AiAssistantScreen.heldStripIconRowY(focusRowY, chatContentBottom);
        assert rowY >= 0 : "row must be drawable after clamp";
        assert rowY - 1 <= maxTop : "icon top must not intrude disclaimer band";
        assert rowY == maxTop + 1 : "expected clamp to max icon top: " + rowY;
    }

    private static void checkNegativeControlLegacyCardVisibility() {
        int chatTop = 0;
        int contentBottom = 120;
        int y = chatTop;
        assert !AiAssistantScreen.chatLineDrawableLegacyAllOrNothing(y, CARD_H, chatTop, contentBottom)
                : "legacy all-or-nothing must reject partial card (negative control)";
    }
}
