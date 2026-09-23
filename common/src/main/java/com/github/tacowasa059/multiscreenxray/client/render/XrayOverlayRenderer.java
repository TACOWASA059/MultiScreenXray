package com.github.tacowasa059.multiscreenxray.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.github.tacowasa059.multiscreenxray.config.XrayProfile;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

/** Renders vanilla entities, first-person hand, and HUD into a transparent texture. */
public final class XrayOverlayRenderer implements AutoCloseable {
    private final Minecraft minecraft;
    private final RenderTarget target;
    private final XrayProfile profile;

    public XrayOverlayRenderer(Minecraft minecraft, XrayProfile profile) {
        this.minecraft = minecraft;
        this.profile = profile.copy();
        this.target = new TextureTarget(minecraft.getWindow().getWidth(),
                minecraft.getWindow().getHeight(), true, Minecraft.ON_OSX);
        target.setClearColor(0f, 0f, 0f, 0f);
    }

    public Frame render(int width, int height) {
        if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
            target.setClearColor(0f, 0f, 0f, 0f);
        }
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        double worldFov;
        try (XrayOverlayPass pass = XrayOverlayPass.enter(target, profile)) {
            minecraft.getProfiler().push("xray_overlay");
            try {
                minecraft.gameRenderer.renderLevel(minecraft.getFrameTime(), Util.getNanos(), new PoseStack());
            } finally {
                minecraft.getProfiler().pop();
            }
            worldFov = pass.worldFov();
        } finally {
            target.unbindWrite();
        }
        if (!Double.isFinite(worldFov) || worldFov <= 0.0) {
            worldFov = minecraft.options.fov().get();
        }
        return new Frame(target.getColorTextureId(), (float) worldFov);
    }

    @Override
    public void close() {
        target.destroyBuffers();
    }

    public record Frame(int textureId, float worldFov) { }
}
