package com.github.tacowasa059.multiscreenxray.mixin;

import com.github.tacowasa059.multiscreenxray.client.render.XrayOverlayPass;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumMap;
import java.util.List;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = {"prepareChunkRenders", "prepareChunkRendersIndirect"}, at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$skipTerrain(Matrix4fc terrainMatrix, boolean respectTranslucentOrder,
            CallbackInfoReturnable<ChunkSectionsToRender> callback) {
        if (!XrayOverlayPass.active()) return;
        EnumMap<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> emptyLayers =
                new EnumMap<>(ChunkSectionLayer.class);
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            emptyLayers.put(layer, List.of());
        }
        GpuBufferSlice terrainTransform = RenderSystem.getDynamicUniforms()
                .writeTerrainTransform(terrainMatrix, 1, 1);
        callback.setReturnValue(new ChunkSectionsToRender.DrawSeparate(
                terrainTransform, emptyLayers, 0, new GpuBufferSlice[0]));
    }

    @Inject(method = "addSkyPass", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$skipSky(FrameGraphBuilder graph, CameraRenderState camera,
            GpuBufferSlice fogBuffer, CallbackInfo callback) {
        if (XrayOverlayPass.active()) callback.cancel();
    }

    @Inject(method = "compileSections", at = @At("HEAD"), cancellable = true)
    private void multiscreenxray$reuseChunks(CameraRenderState camera, CallbackInfo callback) {
        if (XrayOverlayPass.active()) callback.cancel();
    }
}