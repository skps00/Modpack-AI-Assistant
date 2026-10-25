package com.skps9.packai.client.gui;

import java.util.List;
import java.util.function.ToIntFunction;

import com.skps9.packai.client.service.AskService;
import com.skps9.packai.logic.ItemRef;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Headless geometry for focus-row pick icons merged into the target row (work order BA),
 * plus mode-icon dedupe when pick strip already shows the same stack (work order BD).
 */
public final class FocusRowIconsCheck {
    private static final int PANEL_LEFT = 40;
    private static final int SEG_W = SegmentedFocusControl.DEFAULT_WIDTH;
    /** Stand-in for {@code Font::width}: 6px per glyph. */
    private static final ToIntFunction<String> FONT = s -> s.length() * 6;

    private FocusRowIconsCheck() {}

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        int wideAvail = AiAssistantScreen.focusRowIconsAvailableWidth(PANEL_LEFT, 400, SEG_W);
        checkZeroItems(wideAvail);
        checkOneItem(wideAvail);
        checkThreeItems(wideAvail);
        checkFiveItemsThreeIconsPlusN(wideAvail);
        checkNarrowShowsOneIcon();
        checkNegativeControlNarrowWithoutClamp();
        checkModeIconDedupeSameAsSinglePick(wideAvail);
        checkModeIconDedupeWhenTargetIsSecondPick(wideAvail);
        checkModeIconShownWhenNbtDiffers(wideAvail);
        checkModeIconOnlyWhenNoPicks(wideAvail);
        checkNegativeControlModeIconDedupeDisabled(wideAvail);
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

    private static void checkModeIconDedupeSameAsSinglePick(int avail) {
        ItemStack bee = new ItemStack(Items.BEE_SPAWN_EGG);
        ItemRef pick = AskService.fromStack(bee);
        List<ItemRef> pending = List.of(pick);
        boolean inPending = AiAssistantScreen.focusStackInPending(bee, pending);
        assert inPending : "focus matches pending";
        int visible = iconCount(bee, bee, pending, inPending, avail);
        assert visible == 1 : "1 pick = target → one icon on row";
        assert !AiAssistantScreen.showFocusRowModeIcon(bee, bee, pending, inPending, 1)
                : "mode icon skipped";
        int anchorX = 100;
        int segX = 400;
        FocusLineMode.TextLayout layout =
                FocusLineMode.textLayout(anchorX, segX, false);
        assert layout.textX() == anchorX : "no gap when mode icon omitted";
    }

    private static void checkModeIconDedupeWhenTargetIsSecondPick(int avail) {
        ItemStack a = new ItemStack(Items.IRON_INGOT);
        ItemStack b = new ItemStack(Items.GOLD_INGOT);
        ItemRef refA = AskService.fromStack(a);
        ItemRef refB = AskService.fromStack(b);
        List<ItemRef> pending = List.of(refA, refB);
        boolean inPending = AiAssistantScreen.focusStackInPending(b, pending);
        assert inPending : "target is second pick";
        int visible = iconCount(b, b, pending, inPending, avail);
        assert visible == 2 : "two picks, target dupes second → still 2 icons";
    }

    private static void checkModeIconShownWhenNbtDiffers(int avail) {
        ItemStack pickStack = tagged(Items.IRON_SWORD, "variant", "a");
        ItemStack modeStack = tagged(Items.IRON_SWORD, "variant", "b");
        ItemRef pick = AskService.fromStack(pickStack);
        List<ItemRef> pending = List.of(pick);
        boolean inPending = AiAssistantScreen.focusStackInPending(modeStack, pending);
        int visible = iconCount(modeStack, modeStack, pending, inPending, avail);
        assert visible == 2 : "pick + distinct target → 2 icons";
        assert AiAssistantScreen.showFocusRowModeIcon(modeStack, modeStack, pending, inPending, 1)
                : "mode icon shown";
    }

    private static void checkModeIconOnlyWhenNoPicks(int avail) {
        ItemStack target = new ItemStack(Items.DIAMOND);
        int visible = iconCount(target, target, List.of(), false, avail);
        assert visible == 1 : "no picks + target → one mode icon";
        assert AiAssistantScreen.showFocusRowModeIcon(target, target, List.of(), false, 0)
                : "mode icon alone";
    }

    private static void checkNegativeControlModeIconDedupeDisabled(int avail) {
        ItemStack bee = new ItemStack(Items.BEE_SPAWN_EGG);
        ItemRef pick = AskService.fromStack(bee);
        List<ItemRef> pending = List.of(pick);
        boolean inPending = AiAssistantScreen.focusStackInPending(bee, pending);
        AiAssistantScreen.dedupeModeIconWithPickIcons = false;
        try {
            int visible = iconCount(bee, bee, pending, inPending, avail);
            assert visible == 2 : "dedupe off → pick + mode icon";
        } finally {
            AiAssistantScreen.dedupeModeIconWithPickIcons = true;
        }
    }

    private static int iconCount(
            ItemStack modeIcon,
            ItemStack focus,
            List<ItemRef> pending,
            boolean focusInPending,
            int avail) {
        return AiAssistantScreen.focusRowVisibleIconCount(
                modeIcon, focus, pending, focusInPending, avail, 0);
    }

    private static ItemStack tagged(net.minecraft.world.item.Item item, String key, String value) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = new CompoundTag();
        tag.putString(key, value);
        stack.setTag(tag);
        return stack;
    }
}
