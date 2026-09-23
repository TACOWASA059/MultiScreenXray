package com.github.tacowasa059.multiscreenxray.mixin;

import com.github.tacowasa059.multiscreenxray.client.render.XrayOverlayPass;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.LevelRenderer;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import org.joml.Matrix4fc;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "prepareChunkRenders", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$skipTerrain(Matrix4fc frustumMatrix,
            double cameraX, double cameraY, double cameraZ,
            CallbackInfoReturnable<ChunkSectionsToRender> callback) {
        if (!XrayOverlayPass.active()) return;
        EnumMap<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> emptyLayers =
                new EnumMap<>(ChunkSectionLayer.class);
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) emptyLayers.put(layer, new ArrayList<>());
        callback.setReturnValue(new ChunkSectionsToRender(
                Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView(),
                emptyLayers, 0, new GpuBufferSlice[0]));
    }

    @Inject(method = "addSkyPass", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$skipSky(FrameGraphBuilder graph, Camera camera,
            GpuBufferSlice fogBuffer, CallbackInfo callback) {
        if (XrayOverlayPass.active()) callback.cancel();
    }

    @Inject(method = "addCloudsPass", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$skipClouds(FrameGraphBuilder graph, CloudStatus status, Vec3 cameraPosition,
            long gameTime, float partialTick, int color, float height, CallbackInfo callback) {
        if (XrayOverlayPass.active()) callback.cancel();
    }

    @Inject(method = "addWeatherPass", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$skipWeather(FrameGraphBuilder graph, GpuBufferSlice fogBuffer,
            CallbackInfo callback) {
        if (XrayOverlayPass.active()) callback.cancel();
    }

    @Inject(method = "compileSections", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$reuseChunks(Camera camera, CallbackInfo callback) {
        if (XrayOverlayPass.active()) callback.cancel();
    }
}
