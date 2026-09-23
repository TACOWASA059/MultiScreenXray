package com.github.tacowasa059.multiscreenxray.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.github.tacowasa059.multiscreenxray.config.XrayProfile;
import net.minecraft.client.Minecraft;

/** Renders vanilla entities, first-person hand, and HUD into a transparent texture. */
public final class XrayOverlayRenderer implements AutoCloseable {
    private final Minecraft minecraft;
    private final RenderTarget target;
    private final XrayProfile profile;

    public XrayOverlayRenderer(Minecraft minecraft, XrayProfile profile) {
        this.minecraft = minecraft;
        this.profile = profile.copy();
        this.target = new TextureTarget("MultiScreen X-ray overlay",
                minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight(), true);
    }

    public Frame render(int width, int height) {
        if (target.width != width || target.height != height) {
            target.resize(width, height);
        }
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                target.getColorTexture(), 0, target.getDepthTexture(), 1.0);
        double worldFov;
        try (XrayOverlayPass pass = XrayOverlayPass.enter(target, profile)) {
            minecraft.gameRenderer.renderLevel(minecraft.getDeltaTracker());
            worldFov = pass.worldFov();
        }
        if (!Double.isFinite(worldFov) || worldFov <= 0.0) {
            worldFov = minecraft.options.fov().get();
        }
        return new Frame(GlTextureAccess.id(target.getColorTexture()), (float) worldFov);
    }

    @Override
    public void close() {
        target.destroyBuffers();
    }

    public record Frame(int textureId, float worldFov) { }
}
