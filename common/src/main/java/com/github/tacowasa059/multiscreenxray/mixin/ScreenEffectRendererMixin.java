package com.github.tacowasa059.multiscreenxray.mixin;

import com.github.tacowasa059.multiscreenxray.client.render.XrayOverlayPass;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$skipFullScreenBlockEffect(
            float partialTick, SubmitNodeCollector collector, PlayerRenderState playerState,
            CameraRenderState cameraState, boolean hideGui, CallbackInfo callback) {
        if (XrayOverlayPass.active()) {
            callback.cancel();
        }
    }
}