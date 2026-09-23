package com.github.tacowasa059.multiscreenxray.mixin;

import com.github.tacowasa059.multiscreenxray.client.window.MultiWindowManager;
import com.github.tacowasa059.multiscreenxray.client.render.XrayOverlayPass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$overlayTarget(CallbackInfoReturnable<RenderTarget> callback) {
        if (XrayOverlayPass.active()) {
            callback.setReturnValue(XrayOverlayPass.target());
        }
    }

    @Inject(method = "runTick", at = @At("TAIL"))
    private void multiscreenxray$afterFrame(boolean renderLevel, CallbackInfo callback) {
        MultiWindowManager.afterFrame((Minecraft) (Object) this, renderLevel);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void multiscreenxray$closeWindow(CallbackInfo callback) {
        MultiWindowManager.closeAll();
    }
}

