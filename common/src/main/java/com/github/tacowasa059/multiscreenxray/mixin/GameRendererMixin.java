package com.github.tacowasa059.multiscreenxray.mixin;

import com.github.tacowasa059.multiscreenxray.client.render.XrayOverlayPass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.joml.Matrix4fc;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @ModifyArg(
            method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"),
            index = 7)
    private boolean multiscreenxray$skipOverlaySky(boolean shouldRenderSky) {
        return XrayOverlayPass.active() ? false : shouldRenderSky;
    }

    @Inject(method = "mainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$overlayTarget(CallbackInfoReturnable<RenderTarget> callback) {
        if (XrayOverlayPass.active()) {
            callback.setReturnValue(XrayOverlayPass.target());
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$filterHand(CameraRenderState cameraState, float partialTick,
            Matrix4fc modelViewMatrix, CallbackInfo callback) {
        if (XrayOverlayPass.active() && !XrayOverlayPass.showHand()) callback.cancel();
    }
}
