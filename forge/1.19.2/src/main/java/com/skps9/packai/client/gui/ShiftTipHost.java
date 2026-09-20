package com.skps9.packai.client.gui;

import java.util.List;

import net.minecraft.util.FormattedCharSequence;

/**
 * Optional Shift-held full tooltip (Settings V2 desc panel). Painted inside
 * {@link WidgetCompat#renderHoveredTips} so C-0 post-super allowlist stays intact.
 */
public interface ShiftTipHost {
    /** Extra tip lines when Shift is held; empty/null = none. */
    List<FormattedCharSequence> shiftTipLines(int mouseX, int mouseY);
}
