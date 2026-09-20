package com.skps9.packai.client.gui;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Plan A S3: tipLines null guard + editBoxNoTip compile smoke. Headless {@code mc==null}
 * ⇒ split path returns empty; source-level null guard is the real red/green (python greps).
 */
public final class WidgetCompatTipCheck {
    private WidgetCompatTipCheck() {}

    public static void main(String[] args) {
        List<FormattedCharSequence> nullTip = WidgetCompat.tipLines((Component) null);
        assert nullTip != null && nullTip.isEmpty() : "null tip must be empty list";
        // Compile-level: factory exists; do not construct TipEditBox (needs font).
        assert WidgetCompat.class.getDeclaredMethods().length > 0;
        try {
            WidgetCompat.class.getMethod(
                    "editBoxNoTip",
                    int.class,
                    int.class,
                    int.class,
                    int.class,
                    Component.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("editBoxNoTip missing", e);
        }
        System.out.println("WidgetCompatTipCheck OK");
    }
}
