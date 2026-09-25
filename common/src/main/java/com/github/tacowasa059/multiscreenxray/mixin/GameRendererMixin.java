package com.github.tacowasa059.multiscreenxray.mixin;

import com.github.tacowasa059.multiscreenxray.client.render.XrayOverlayPass;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @ModifyArg(
            method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V"),
            index = 5)
    private boolean multiscreenxray$skipOverlaySky(boolean shouldRenderSky) {
        return XrayOverlayPass.active() ? false : shouldRenderSky;
    }

    @ModifyExpressionValue(
            method = {"render3dHud", "renderItemInHand"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;"))
    private RenderTarget multiscreenxray$overlayDirectTarget(RenderTarget original) {
        return XrayOverlayPass.active() ? XrayOverlayPass.target() : original;
    }
    @Inject(method = "mainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$overlayTarget(CallbackInfoReturnable<RenderTarget> callback) {
        if (XrayOverlayPass.active()) {
            callback.setReturnValue(XrayOverlayPass.target());
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$filterHand(CameraRenderState cameraState, PlayerRenderState playerState,
            GpuTextureView depthTextureView, CallbackInfo callback) {
        if (XrayOverlayPass.active() && !XrayOverlayPass.showHand()) callback.cancel();
    }
}
