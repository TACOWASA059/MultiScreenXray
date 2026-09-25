package com.github.tacowasa059.multiscreenxray.client.window;

import com.github.tacowasa059.multiscreenxray.client.render.OverlayBlitter;
import com.github.tacowasa059.multiscreenxray.client.render.XrayOverlayRenderer;
import com.github.tacowasa059.multiscreenxray.client.render.XrayWorldRenderer;
import com.github.tacowasa059.multiscreenxray.config.ConfigManager;
import com.github.tacowasa059.multiscreenxray.config.XrayConfig;
import com.github.tacowasa059.multiscreenxray.config.XrayProfile;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.api.device.SurfaceException;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

import java.nio.IntBuffer;

/** One SDL window with its own saved profile and renderer. */
final class ExtraWindow implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Minecraft minecraft;
    private final int number;
    private long handle;
    private GpuSurface surface;
    private RenderTarget target;
    private int configuredWidth;
    private int configuredHeight;
    private boolean surfaceInvalid = true;
    private boolean closeRequested;
    private boolean iconified;
    private XrayWorldRenderer xrayRenderer;
    private XrayOverlayRenderer overlayRenderer;
    private OverlayBlitter overlayBlitter;
    private XrayProfile profile;
    private long profileRevision = Long.MIN_VALUE;
    private boolean rendererInvalid = true;
    private long lastTitleUpdate;
    private boolean geometryDirty;
    private long geometryChangedAt;

    ExtraWindow(Minecraft minecraft, int number) {
        this.minecraft = minecraft;
        this.number = number;
        refreshProfile();
        createWindow();
        LOGGER.info("Opened MultiScreen X-ray window {} with SDL handle {} on {}",
                number, handle, RenderSystem.getDevice().getDeviceInfo().backendName());
    }

    int number() { return number; }
    long handle() { return handle; }
    boolean shouldClose() { return handle == 0L || closeRequested; }
    void invalidateProfile() { profileRevision = Long.MIN_VALUE; }

    XrayOverlayRenderer.Frame renderOverlay() {
        refreshProfile();
        if (overlayRenderer == null) overlayRenderer = new XrayOverlayRenderer(minecraft, profile);
        Size size = framebufferSize();
        return overlayRenderer.render(size.width(), size.height());
    }

    void idle() {
        if (handle != 0L) saveGeometryIfDue();
    }

    void render(XrayOverlayRenderer.Frame overlayFrame) {
        if (handle == 0L || isIconified()) return;
        saveGeometryIfDue();
        refreshProfile();
        if (rendererInvalid) {
            if (xrayRenderer != null) xrayRenderer.close();
            XrayConfig config = ConfigManager.get();
            xrayRenderer = new XrayWorldRenderer(profile, config.scanRadiusChunks, config.verticalRadius);
            rendererInvalid = false;
        }

        Size size = framebufferSize();
        if (size.width() <= 0 || size.height() <= 0) return;
        ensureTarget(size.width(), size.height());
        xrayRenderer.render(minecraft, target, overlayFrame.worldFov());
        overlayBlitter.draw(target, overlayFrame.textureView());
        updateTitle();
        present(size.width(), size.height());
    }

    void handleEvent(SDL_Event event) {
        switch (event.type()) {
            case SDLEvents.SDL_EVENT_WINDOW_CLOSE_REQUESTED -> closeRequested = true;
            case SDLEvents.SDL_EVENT_WINDOW_MOVED -> markGeometryChanged();
            case SDLEvents.SDL_EVENT_WINDOW_RESIZED -> {
                markGeometryChanged();
                surfaceInvalid = true;
            }
            case SDLEvents.SDL_EVENT_WINDOW_PIXEL_SIZE_CHANGED -> surfaceInvalid = true;
            case SDLEvents.SDL_EVENT_WINDOW_FOCUS_GAINED -> {
                SDLVideo.SDL_SetWindowFocusable(handle, false);
                SDLVideo.SDL_RaiseWindow(minecraft.getWindow().handle());
            }
            case SDLEvents.SDL_EVENT_WINDOW_MINIMIZED -> iconified = true;
            case SDLEvents.SDL_EVENT_WINDOW_MAXIMIZED, SDLEvents.SDL_EVENT_WINDOW_RESTORED -> {
                iconified = false;
                surfaceInvalid = true;
            }
            default -> { }
        }
    }

    private void ensureTarget(int width, int height) {
        if (target == null) {
            target = new TextureTarget("MultiScreen X-ray window " + number,
                    width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height);
        }
    }

    private void present(int width, int height) {
        try {
            if (surfaceInvalid || configuredWidth != width || configuredHeight != height || surface.isSuboptimal()) {
                GpuSurface.PresentMode mode = GpuSurface.PresentMode.getSupportedVsyncMode(
                        surface.supportedPresentModes(), false);
                surface.configure(new GpuSurface.Configuration(width, height, mode));
                configuredWidth = width;
                configuredHeight = height;
                surfaceInvalid = false;
            }
            surface.acquireNextTexture();
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            surface.blitFromTexture(encoder, target.getColorTextureView());
            encoder.submit();
            surface.present();
        } catch (SurfaceException exception) {
            surfaceInvalid = true;
            LOGGER.warn("Could not present MultiScreen X-ray window {}", number, exception);
        }
    }

    private void refreshProfile() {
        long revision = ConfigManager.revision();
        if (revision == profileRevision) return;
        profile = ConfigManager.get().screen(number - 1).copy();
        profileRevision = revision;
        rendererInvalid = true;
        if (overlayRenderer != null) {
            overlayRenderer.close();
            overlayRenderer = null;
        }
    }

    private void updateTitle() {
        long now = System.nanoTime();
        if (now - lastTitleUpdate > 1_000_000_000L) {
            SDLVideo.SDL_SetWindowTitle(handle, "MultiScreen X-ray " + number + " - " + profile.name
                    + " | blocks: " + xrayRenderer.oreCount()
                    + (xrayRenderer.initialScanInProgress() ? " | scanning" : ""));
            lastTitleUpdate = now;
        }
    }

    private void createWindow() {
        String backend = RenderSystem.getDevice().getDeviceInfo().backendName();
        long graphicsFlag = backend.equalsIgnoreCase("Vulkan")
                ? SDLVideo.SDL_WINDOW_VULKAN : SDLVideo.SDL_WINDOW_OPENGL;
        long flags = graphicsFlag | SDLVideo.SDL_WINDOW_RESIZABLE
                | SDLVideo.SDL_WINDOW_HIGH_PIXEL_DENSITY | SDLVideo.SDL_WINDOW_NOT_FOCUSABLE;
        long newHandle = SDLVideo.SDL_CreateWindow("MultiScreen X-ray " + number + " - " + profile.name,
                profile.windowWidth, profile.windowHeight, flags);
        if (newHandle == 0L) {
            throw new IllegalStateException("Could not create X-ray window " + number + ": " + SDLError.SDL_GetError());
        }

        try {
            if (!SDLVideo.SDL_SetWindowFocusable(newHandle, false)) {
                throw new IllegalStateException("Could not disable X-ray window focus: " + SDLError.SDL_GetError());
            }
            SDLVideo.SDL_SetWindowMinimumSize(newHandle, 320, 240);
            if (profile.windowX != Integer.MIN_VALUE && profile.windowY != Integer.MIN_VALUE) {
                SDLVideo.SDL_SetWindowPosition(newHandle, profile.windowX, profile.windowY);
            }
            handle = newHandle;
            surface = RenderSystem.getDevice().createSurface(newHandle, this::isIconified);
            overlayBlitter = new OverlayBlitter();
        } catch (RuntimeException exception) {
            SDLVideo.SDL_DestroyWindow(newHandle);
            handle = 0L;
            throw exception;
        }
    }

    private boolean isIconified() {
        return iconified || handle != 0L
                && (SDLVideo.SDL_GetWindowFlags(handle) & SDLVideo.SDL_WINDOW_MINIMIZED) != 0L;
    }

    private Size framebufferSize() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            if (!SDLVideo.SDL_GetWindowSizeInPixels(handle, width, height)) {
                throw new IllegalStateException("Could not query X-ray window size: " + SDLError.SDL_GetError());
            }
            return new Size(Math.max(1, width.get(0)), Math.max(1, height.get(0)));
        }
    }

    @Override
    public void close() {
        if (handle == 0L) return;
        LOGGER.info("Closing MultiScreen X-ray window {}", number);
        rememberWindowGeometry();
        if (overlayRenderer != null) overlayRenderer.close();
        if (xrayRenderer != null) xrayRenderer.close();
        if (overlayBlitter != null) overlayBlitter.close();
        if (target != null) target.destroyBuffers();
        if (surface != null) surface.close();
        SDLVideo.SDL_DestroyWindow(handle);
        handle = 0L;
    }

    private void rememberWindowGeometry() {
        if (handle == 0L || isIconified()) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer x = stack.mallocInt(1);
            IntBuffer y = stack.mallocInt(1);
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            if (!SDLVideo.SDL_GetWindowPosition(handle, x, y)
                    || !SDLVideo.SDL_GetWindowSize(handle, width, height)) return;
            XrayProfile saved = ConfigManager.get().screen(number - 1);
            saved.windowX = x.get(0);
            saved.windowY = y.get(0);
            saved.windowWidth = Math.max(320, width.get(0));
            saved.windowHeight = Math.max(240, height.get(0));
            ConfigManager.save();
            geometryDirty = false;
        }
    }

    private void markGeometryChanged() {
        geometryDirty = true;
        geometryChangedAt = System.nanoTime();
    }

    private void saveGeometryIfDue() {
        if (geometryDirty && !isIconified()
                && System.nanoTime() - geometryChangedAt > 500_000_000L) {
            rememberWindowGeometry();
        }
    }

    private record Size(int width, int height) { }
}