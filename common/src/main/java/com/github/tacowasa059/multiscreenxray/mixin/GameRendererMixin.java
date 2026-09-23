package com.github.tacowasa059.multiscreenxray.mixin;

import com.github.tacowasa059.multiscreenxray.client.render.XrayOverlayPass;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.joml.Matrix4f;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getProjectionMatrix", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$matchWindowProjection(double fieldOfView,
            CallbackInfoReturnable<Matrix4f> callback) {
        if (XrayOverlayPass.active()) {
            XrayOverlayPass.captureWorldFov(fieldOfView);
            callback.setReturnValue(new Matrix4f().perspective((float) Math.toRadians(fieldOfView),
                    XrayOverlayPass.aspectRatio(), 0.05f, 256.0f));
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$filterHand(PoseStack poseStack, Camera camera,
            float partialTick, CallbackInfo callback) {
        if (XrayOverlayPass.active() && !XrayOverlayPass.showHand()) callback.cancel();
    }
}
