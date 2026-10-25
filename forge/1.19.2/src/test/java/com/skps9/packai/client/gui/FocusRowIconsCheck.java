package com.skps9.packai.client.gui;

import java.util.function.ToIntFunction;

/**
 * Headless geometry for focus-row pick icons merged into the target row (work order BA).
 */
public final class FocusRowIconsCheck {
    private static final int PANEL_LEFT = 40;
    private static final int SEG_W = SegmentedFocusControl.DEFAULT_WIDTH;
    /** Stand-in for {@code Font::width}: 6px per glyph. */
    private static final ToIntFunction<String> FONT = s -> s.length() * 6;

    private FocusRowIconsCheck() {}

    public static void main(String[] args) {
        int wideAvail = AiAssistantScreen.focusRowIconsAvailableWidth(PANEL_LEFT, 400, SEG_W);
        checkZeroItems(wideAvail);
        checkOneItem(wideAvail);
        checkThreeItems(wideAvail);
        checkFiveItemsThreeIconsPlusN(wideAvail);
        checkNarrowShowsOneIcon();
        checkNegativeControlNarrowWithoutClamp();
        System.out.println("FocusRowIconsCheck OK");
    }

    private static void checkZeroItems(int avail) {
        AiAssistantScreen.FocusRowIconPlan plan =
                AiAssistantScreen.planFocusRowIcons(0, avail, 0, true);
        assert plan.iconCount() == 0 : "0 items";
        assert plan.overflowN() == 0 : "0 overflow";
        assert plan.contentWidth() == 0 : "0 width";
    }

    private static void checkOneItem(int avail) {
        AiAssistantScreen.FocusRowIconPlan plan =
                AiAssistantScreen.planFocusRowIcons(1, avail, 0, true);
        assert plan.iconCount() == 1 : "1 icon";
        assert plan.overflowN() == 0 : "no +N";
        assert !plan.showOverflow() : "no overflow label";
        assert plan.contentWidth() == 16 : "one 16px icon";
    }

    private static void checkThreeItems(int avail) {
        AiAssistantScreen.FocusRowIconPlan plan =
                AiAssistantScreen.planFocusRowIcons(3, avail, 0, true);
        assert plan.iconCount() == 3 : "3 icons";
        assert plan.overflowN() == 0 : "no +N";
        assert plan.contentWidth() == 3 * 16 + 2 * 2 : "three icons with gap 2";
    }

    private static void checkFiveItemsThreeIconsPlusN(int avail) {
        int overflow = 5 - 3;
        int plusW = FONT.applyAsInt(AiAssistantScreen.focusRowOverflowLabel(overflow));
        AiAssistantScreen.FocusRowIconPlan plan =
                AiAssistantScreen.planFocusRowIcons(5, avail, plusW, true);
        assert plan.iconCount() == 3 : "5 items → 3 icons";
        assert plan.overflowN() == overflow : "N = total - 3 (+2 for 5 items)";
        assert plan.showOverflow() : "+N visible";
        int expected = 3 * 16 + 2 * 2 + 2 + plusW;
        assert plan.contentWidth() == expected : "width with +N";
    }

    private static void checkNarrowShowsOneIcon() {
        int avail = 16;
        int plusW = FONT.applyAsInt(AiAssistantScreen.focusRowOverflowLabel(2));
        AiAssistantScreen.FocusRowIconPlan plan =
                AiAssistantScreen.planFocusRowIcons(5, avail, plusW, true);
        assert plan.iconCount() == 1 : "narrow → 1 icon";
        assert plan.contentWidth() <= avail : "must not exceed available width";
        assert plan.contentWidth() == 16 : "single icon only";
        int maxRight = PANEL_LEFT + avail;
        assert PANEL_LEFT + plan.contentWidth() <= maxRight : "within panel budget";
    }

    private static void checkNegativeControlNarrowWithoutClamp() {
        int avail = 16;
        int plusW = FONT.applyAsInt(AiAssistantScreen.focusRowOverflowLabel(2));
        AiAssistantScreen.FocusRowIconPlan clamped =
                AiAssistantScreen.planFocusRowIcons(5, avail, plusW, true);
        AiAssistantScreen.FocusRowIconPlan unclamped =
                AiAssistantScreen.planFocusRowIcons(5, avail, plusW, false);
        assert clamped.iconCount() == 1 : "clamped narrow";
        assert unclamped.iconCount() == 3 : "unclamped keeps 3 icons";
        assert unclamped.contentWidth() > avail : "unclamped must fail narrow budget";
    }
}
