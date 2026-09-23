package com.github.tacowasa059.multiscreenxray.mixin;

import com.github.tacowasa059.multiscreenxray.client.render.XrayOverlayPass;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Skips debug gizmos while the world is rendered into an extra X-ray target. */
@Mixin(DebugRenderer.class)
public abstract class DebugRendererMixin {
    @Inject(method = "emitGizmos", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$skipDebugGizmos(
            Frustum frustum, double cameraX, double cameraY, double cameraZ,
            float partialTick, CallbackInfo callback) {
        if (XrayOverlayPass.active()) callback.cancel();
    }
}
