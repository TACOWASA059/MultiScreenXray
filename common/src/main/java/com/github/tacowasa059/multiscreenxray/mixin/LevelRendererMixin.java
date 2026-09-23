package com.github.tacowasa059.multiscreenxray.mixin;

import com.github.tacowasa059.multiscreenxray.client.render.XrayOverlayPass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V"))
    private void multiscreenxray$restoreOverlayViewport(RenderTarget target, boolean setViewport) {
        target.bindWrite(setViewport || XrayOverlayPass.active());
    }

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/FogRenderer;levelFogColor()V"))
    private void multiscreenxray$transparentClear() {
        if (XrayOverlayPass.active()) {
            RenderSystem.clearColor(0f, 0f, 0f, 0f);
        } else {
            FogRenderer.levelFogColor();
        }
    }

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"))
    private void multiscreenxray$preserveTransparentBackground(int mask, boolean getError) {
        RenderSystem.clear(XrayOverlayPass.active() ? GL11.GL_DEPTH_BUFFER_BIT : mask, getError);
    }

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$skipSky(PoseStack poseStack, Matrix4f projectionMatrix,
            float partialTick, Camera camera, boolean isFoggy, Runnable skyFogSetup, CallbackInfo callback) {
        if (XrayOverlayPass.active()) callback.cancel();
    }

    @Inject(method = "renderChunkLayer", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$skipTerrain(RenderType renderType, PoseStack poseStack,
            double camX, double camY, double camZ, Matrix4f projectionMatrix, CallbackInfo callback) {
        if (XrayOverlayPass.active()) callback.cancel();
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$skipClouds(PoseStack poseStack, Matrix4f projectionMatrix,
            float partialTick, double camX, double camY, double camZ, CallbackInfo callback) {
        if (XrayOverlayPass.active()) callback.cancel();
    }

    @Inject(method = "setupRender", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$reuseCulling(Camera camera, Frustum frustum,
            boolean capturedFrustum, boolean spectator, CallbackInfo callback) {
        if (XrayOverlayPass.active()) callback.cancel();
    }

    @Inject(method = "compileChunks", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$reuseChunks(Camera camera, CallbackInfo callback) {
        if (XrayOverlayPass.active()) callback.cancel();
    }
}
