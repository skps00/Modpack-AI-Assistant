package com.skps9.packai.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skps9.packai.client.jei.JeiReqNotes;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * While {@link JeiReqNotes#capturing()}, record Font draws (JEI XP / time / stress text).
 */
@Mixin(Font.class)
public class FontDrawCaptureMixin {
    @Inject(
            method = "draw(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/network/chat/Component;FFI)I",
            at = @At("HEAD")
    )
    private void packai$captureComponent(
            PoseStack pose, Component text, float x, float y, int color, CallbackInfoReturnable<Integer> cir
    ) {
        if (JeiReqNotes.capturing()) {
            JeiReqNotes.offerDrawnText(text);
        }
    }

    @Inject(
            method = "draw(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/lang/String;FFI)I",
            at = @At("HEAD")
    )
    private void packai$captureString(
            PoseStack pose, String text, float x, float y, int color, CallbackInfoReturnable<Integer> cir
    ) {
        if (JeiReqNotes.capturing()) {
            JeiReqNotes.offerDrawnText(text);
        }
    }
}
