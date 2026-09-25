package com.github.tacowasa059.multiscreenxray.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.github.tacowasa059.multiscreenxray.config.XrayProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Renders vanilla entities, first-person hand, and HUD into a transparent texture. */
public final class XrayOverlayRenderer implements AutoCloseable {
    private final Minecraft minecraft;
    private final RenderTarget target;
    private final XrayProfile profile;

    public XrayOverlayRenderer(Minecraft minecraft, XrayProfile profile) {
        this.minecraft = minecraft;
        this.profile = profile.copy();
        this.target = new TextureTarget("MultiScreen X-ray overlay",
                minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight(), true,
                GpuFormat.RGBA8_UNORM);
    }

    public Frame render(int width, int height) {
        if (target.width != width || target.height != height) {
            target.resize(width, height);
        }
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                target.getColorTexture(), new Vector4f(0.0f), target.getDepthTexture(), 0.0);
        double worldFov;
        CameraRenderState cameraState = minecraft.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        WindowRenderState windowState = minecraft.gameRenderer.gameRenderState().windowRenderState;
        Matrix4f previousProjection = new Matrix4f(cameraState.projectionMatrix);
        int previousWidth = windowState.width;
        int previousHeight = windowState.height;
        try (XrayOverlayPass pass = XrayOverlayPass.enter(target, profile)) {
            float fov = minecraft.gameRenderer.mainCamera().getFov();
            XrayOverlayPass.captureWorldFov(fov);
            cameraState.projectionMatrix.set(new Matrix4f().perspective(
                    (float) Math.toRadians(fov), (float) width / height, cameraState.depthFar,
                    0.05f, RenderSystem.getDevice().getDeviceInfo().isZZeroToOne()));
            windowState.width = width;
            windowState.height = height;
            extractSelectedEntities();
            minecraft.gameRenderer.renderLevel(minecraft.getDeltaTracker());
            worldFov = pass.worldFov();
        } finally {
            cameraState.projectionMatrix.set(previousProjection);
            windowState.width = previousWidth;
            windowState.height = previousHeight;
        }
        if (!Double.isFinite(worldFov) || worldFov <= 0.0) {
            worldFov = minecraft.options.fov().get();
        }
        return new Frame(target.getColorTextureView(), (float) worldFov);
    }

    private void extractSelectedEntities() {
        if (minecraft.level == null) return;

        LevelRenderState levelState = minecraft.gameRenderer.gameRenderState().levelRenderState;
        levelState.entityRenderStates.clear();
        Entity cameraEntity = minecraft.gameRenderer.mainCamera().entity();
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity == cameraEntity || !XrayOverlayPass.visible(entity)) continue;
            float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(
                    !minecraft.level.tickRateManager().isEntityFrozen(entity));
            levelState.entityRenderStates.add(
                    minecraft.levelRenderer.entityRenderDispatcher().extractEntity(entity, partialTick));
        }
        levelState.lastEntityRenderStateCount = levelState.entityRenderStates.size();
    }

    @Override
    public void close() {
        target.destroyBuffers();
    }

    public record Frame(GpuTextureView textureView, float worldFov) { }
}
