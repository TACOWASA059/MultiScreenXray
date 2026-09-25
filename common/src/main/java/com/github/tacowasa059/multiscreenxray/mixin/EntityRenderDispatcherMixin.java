package com.github.tacowasa059.multiscreenxray.mixin;

import com.github.tacowasa059.multiscreenxray.client.render.XrayOverlayPass;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void multiscreenxray$filterEntity(E entity, Frustum frustum,
            double cameraX, double cameraY, double cameraZ, CallbackInfoReturnable<Boolean> callback) {
        if (XrayOverlayPass.active() && !XrayOverlayPass.visible(entity)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private <E extends Entity> void multiscreenxray$fullBrightEntity(E entity, float partialTick,
            CallbackInfoReturnable<EntityRenderState> callback) {
        if (XrayOverlayPass.active() && callback.getReturnValue() != null) {
            callback.getReturnValue().lightCoords = LightCoordsUtil.FULL_BRIGHT;
        }
    }
}
