package com.github.tacowasa059.multiscreenxray.client.render;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;

/** Resolves the OpenGL texture behind vanilla and loader validation wrappers. */
final class GlTextureAccess {
    private GlTextureAccess() { }

    static int id(GpuTexture texture) {
        GpuTexture current = texture;
        for (int depth = 0; depth < 4; depth++) {
            if (current instanceof GlTexture glTexture) return glTexture.glId();
            try {
                Object unwrapped = current.getClass().getMethod("getRealTexture").invoke(current);
                if (!(unwrapped instanceof GpuTexture realTexture) || realTexture == current) break;
                current = realTexture;
            } catch (ReflectiveOperationException exception) {
                break;
            }
        }
        throw new IllegalStateException("Unsupported GPU texture implementation: " + current.getClass().getName());
    }
}
