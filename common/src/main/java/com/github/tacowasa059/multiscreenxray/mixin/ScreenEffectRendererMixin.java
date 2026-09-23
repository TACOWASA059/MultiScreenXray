package com.github.tacowasa059.multiscreenxray.mixin;

import com.github.tacowasa059.multiscreenxray.client.render.XrayOverlayPass;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
    @Inject(method = "renderScreenEffect", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$skipFullScreenBlockEffect(
            boolean sleeping, float partialTick, SubmitNodeCollector collector, CallbackInfo callback) {
        if (XrayOverlayPass.active()) {
            callback.cancel();
        }
    }
}
