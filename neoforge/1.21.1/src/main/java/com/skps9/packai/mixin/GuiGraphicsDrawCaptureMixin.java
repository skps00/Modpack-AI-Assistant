package com.skps9.packai.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.skps9.packai.client.jei.JeiReqNotes;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * While {@link JeiReqNotes#capturing()}, record GuiGraphics string draws (JEI overlay text).
 */
@Mixin(GuiGraphics.class)
public class GuiGraphicsDrawCaptureMixin {
    @Inject(
            method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I",
            at = @At("HEAD")
    )
    private void packai$captureComponent(
            Font font, Component text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir
    ) {
        if (JeiReqNotes.capturing()) {
            JeiReqNotes.offerDrawnText(text);
        }
    }

    @Inject(
            method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I",
            at = @At("HEAD")
    )
    private void packai$captureString(
            Font font, String text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir
    ) {
        if (JeiReqNotes.capturing()) {
            JeiReqNotes.offerDrawnText(text);
        }
    }
}
