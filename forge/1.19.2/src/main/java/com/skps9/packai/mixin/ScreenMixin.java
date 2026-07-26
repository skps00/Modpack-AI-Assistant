package com.skps9.packai.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.skps9.packai.client.context.TooltipCapture;

import net.minecraft.client.gui.screens.Screen;

/**
 * While Pack AI captures item tooltips, pretend Shift/Ctrl/Alt are held
 * so mod "hold key for details" lines are included.
 */
@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "hasShiftDown", at = @At("HEAD"), cancellable = true)
    private static void packai$forceShift(CallbackInfoReturnable<Boolean> cir) {
        if (TooltipCapture.forceExpanded()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "hasControlDown", at = @At("HEAD"), cancellable = true)
    private static void packai$forceControl(CallbackInfoReturnable<Boolean> cir) {
        if (TooltipCapture.forceExpanded()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "hasAltDown", at = @At("HEAD"), cancellable = true)
    private static void packai$forceAlt(CallbackInfoReturnable<Boolean> cir) {
        if (TooltipCapture.forceExpanded()) {
            cir.setReturnValue(true);
        }
    }
}
