package com.github.tacowasa059.multiscreenxray.mixin;

import com.github.tacowasa059.multiscreenxray.client.render.XrayOverlayPass;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private int multiscreenxray$fullBrightEntity(int packedLight) {
        return XrayOverlayPass.active() ? LightTexture.FULL_BRIGHT : packedLight;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void multiscreenxray$filterEntity(E entity, double x, double y, double z,
            float rotation, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, CallbackInfo callback) {
        if (XrayOverlayPass.active() && !XrayOverlayPass.visible(entity)) callback.cancel();
    }
}
