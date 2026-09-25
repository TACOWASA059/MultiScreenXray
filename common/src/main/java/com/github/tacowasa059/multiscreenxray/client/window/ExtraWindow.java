package com.github.tacowasa059.multiscreenxray.client.window;

import com.github.tacowasa059.multiscreenxray.client.render.GlTextureAccess;
import com.github.tacowasa059.multiscreenxray.client.render.OverlayBlitter;
import com.github.tacowasa059.multiscreenxray.client.render.XrayOverlayRenderer;
import com.github.tacowasa059.multiscreenxray.client.render.XrayWorldRenderer;
import com.github.tacowasa059.multiscreenxray.config.ConfigManager;
import com.github.tacowasa059.multiscreenxray.config.XrayConfig;
import com.github.tacowasa059.multiscreenxray.config.XrayProfile;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.SurfaceException;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWWindowFocusCallbackI;
import org.lwjgl.glfw.GLFWWindowPosCallbackI;
import org.lwjgl.glfw.GLFWWindowSizeCallbackI;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLCapabilities;
import org.slf4j.Logger;

/** One GLFW window with its own saved profile and renderer. */
final class ExtraWindow implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Minecraft minecraft;
    private final int number;
    private final boolean openGl;
    private long handle;
    private GLCapabilities capabilities;
    private int glBlitFramebuffer;
    private GpuSurface surface;
    private RenderTarget target;
    private int configuredWidth;
    private int configuredHeight;
    private boolean surfaceInvalid = true;
    private XrayWorldRenderer xrayRenderer;
    private XrayOverlayRenderer overlayRenderer;
    private OverlayBlitter overlayBlitter;
    private XrayProfile profile;
    private long profileRevision = Long.MIN_VALUE;
    private boolean rendererInvalid = true;
    private long lastTitleUpdate;
    private boolean geometryDirty;
    private long geometryChangedAt;
    private GLFWWindowFocusCallbackI focusCallback;
    private GLFWWindowPosCallbackI positionCallback;
    private GLFWWindowSizeCallbackI sizeCallback;
    private volatile boolean returnFocusRequested;
    private volatile boolean waitForTitlebarRelease;

    ExtraWindow(Minecraft minecraft, int number) {
        this.minecraft = minecraft;
        this.number = number;
        this.openGl = RenderSystem.getDevice().getDeviceInfo().backendName().equalsIgnoreCase("OpenGL");
        refreshProfile();
        createWindow();
        LOGGER.info("Opened MultiScreen X-ray window {} with GLFW handle {} on {}",
                number, handle, RenderSystem.getDevice().getDeviceInfo().backendName());
    }

    int number() { return number; }
    long handle() { return handle; }
    boolean shouldClose() { return handle == 0L || GLFW.glfwWindowShouldClose(handle); }
    void invalidateProfile() { profileRevision = Long.MIN_VALUE; }

    XrayOverlayRenderer.Frame renderOverlay() {
        handleFocusReturn();
        refreshProfile();
        if (overlayRenderer == null) overlayRenderer = new XrayOverlayRenderer(minecraft, profile);
        int[] width = new int[1];
        int[] height = new int[1];
        GLFW.glfwGetFramebufferSize(handle, width, height);
        return overlayRenderer.render(Math.max(1, width[0]), Math.max(1, height[0]));
    }

    void idle() {
        if (handle == 0L) return;
        handleFocusReturn();
        saveGeometryIfDue();
    }

    private void handleFocusReturn() {
        if (waitForTitlebarRelease
                && GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_RELEASE) {
            waitForTitlebarRelease = false;
            returnFocusRequested = true;
        }
        if (returnFocusRequested) {
            returnFocusRequested = false;
            GLFW.glfwFocusWindow(minecraft.getWindow().handle());
        }
    }

    void render(XrayOverlayRenderer.Frame overlayFrame) {
        if (handle == 0L) return;
        saveGeometryIfDue();
        refreshProfile();
        if (rendererInvalid) {
            if (xrayRenderer != null) xrayRenderer.close();
            XrayConfig config = ConfigManager.get();
            xrayRenderer = new XrayWorldRenderer(profile, config.scanRadiusChunks, config.verticalRadius);
            rendererInvalid = false;
        }

        int[] width = new int[1];
        int[] height = new int[1];
        GLFW.glfwGetFramebufferSize(handle, width, height);
        if (width[0] <= 0 || height[0] <= 0) return;
        ensureTarget(width[0], height[0]);
        xrayRenderer.render(minecraft, target, overlayFrame.worldFov());
        overlayBlitter.draw(target, overlayFrame.textureView());
        updateTitle();

        if (openGl) {
            presentOpenGl(width[0], height[0]);
        } else {
            presentSurface(width[0], height[0]);
        }
    }

    private void ensureTarget(int width, int height) {
        if (target == null) {
            target = new TextureTarget("MultiScreen X-ray window " + number,
                    width, height, true, GpuFormat.RGBA8_UNORM);
        } else if (target.width != width || target.height != height) {
            target.resize(width, height);
        }
    }

    private void presentOpenGl(int width, int height) {
        long mainHandle = minecraft.getWindow().handle();
        GLCapabilities mainCapabilities = GL.getCapabilities();
        try {
            GLFW.glfwMakeContextCurrent(handle);
            GL.setCapabilities(capabilities);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, glBlitFramebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, GlTextureAccess.id(target.getColorTexture()), 0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL30.glBlitFramebuffer(0, 0, target.width, target.height,
                    0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
            GLFW.glfwSwapBuffers(handle);
        } finally {
            GLFW.glfwMakeContextCurrent(mainHandle);
            GL.setCapabilities(mainCapabilities);
        }
    }

    private void presentSurface(int width, int height) {
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
            GLFW.glfwSetWindowTitle(handle, "MultiScreen X-ray " + number + " - " + profile.name
                    + " | blocks: " + xrayRenderer.oreCount()
                    + (xrayRenderer.initialScanInProgress() ? " | scanning" : ""));
            lastTitleUpdate = now;
        }
    }

    private void createWindow() {
        long mainHandle = minecraft.getWindow().handle();
        GLCapabilities mainCapabilities = openGl ? GL.getCapabilities() : null;
        GLFW.glfwDefaultWindowHints();
        if (openGl) {
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_API);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        } else {
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
        }
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        long newHandle = GLFW.glfwCreateWindow(profile.windowWidth, profile.windowHeight,
                "MultiScreen X-ray " + number + " - " + profile.name, 0L, openGl ? mainHandle : 0L);
        if (newHandle == 0L) throw new IllegalStateException("Could not create X-ray window " + number);

        try {
            if (profile.windowX != Integer.MIN_VALUE && profile.windowY != Integer.MIN_VALUE) {
                GLFW.glfwSetWindowPos(newHandle, profile.windowX, profile.windowY);
            }
            if (openGl) {
                GLFW.glfwMakeContextCurrent(newHandle);
                capabilities = GL.createCapabilities();
                glBlitFramebuffer = GL30.glGenFramebuffers();
                GLFW.glfwSwapInterval(0);
                GLFW.glfwMakeContextCurrent(mainHandle);
                GL.setCapabilities(mainCapabilities);
            }
            handle = newHandle;
            if (!openGl) surface = RenderSystem.getDevice().createSurface(newHandle);
            overlayBlitter = new OverlayBlitter();
            installWindowCallbacks(newHandle, mainHandle);
        } catch (RuntimeException exception) {
            if (openGl) {
                GLFW.glfwMakeContextCurrent(mainHandle);
                GL.setCapabilities(mainCapabilities);
            }
            GLFW.glfwDestroyWindow(newHandle);
            throw exception;
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

        if (openGl && glBlitFramebuffer != 0) {
            long mainHandle = minecraft.getWindow().handle();
            GLCapabilities mainCapabilities = GL.getCapabilities();
            GLFW.glfwMakeContextCurrent(handle);
            GL.setCapabilities(capabilities);
            GL30.glDeleteFramebuffers(glBlitFramebuffer);
            GLFW.glfwMakeContextCurrent(mainHandle);
            GL.setCapabilities(mainCapabilities);
        }
        Callbacks.glfwFreeCallbacks(handle);
        GLFW.glfwDestroyWindow(handle);
        handle = 0L;
        capabilities = null;
    }

    private void rememberWindowGeometry() {
        if (handle == 0L || GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE) return;
        int[] x = new int[1];
        int[] y = new int[1];
        int[] width = new int[1];
        int[] height = new int[1];
        GLFW.glfwGetWindowPos(handle, x, y);
        GLFW.glfwGetWindowSize(handle, width, height);
        XrayProfile saved = ConfigManager.get().screen(number - 1);
        saved.windowX = x[0];
        saved.windowY = y[0];
        saved.windowWidth = Math.max(320, width[0]);
        saved.windowHeight = Math.max(240, height[0]);
        ConfigManager.save();
        geometryDirty = false;
    }

    private void installWindowCallbacks(long window, long mainWindow) {
        focusCallback = (ignored, focused) -> {
            if (!focused || mainWindow == 0L) {
                returnFocusRequested = false;
                waitForTitlebarRelease = false;
                return;
            }
            double[] cursorX = new double[1];
            double[] cursorY = new double[1];
            int[] width = new int[1];
            int[] height = new int[1];
            GLFW.glfwGetCursorPos(window, cursorX, cursorY);
            GLFW.glfwGetWindowSize(window, width, height);
            boolean insideContent = cursorX[0] >= 0.0 && cursorY[0] >= 0.0
                    && cursorX[0] < width[0] && cursorY[0] < height[0];
            returnFocusRequested = insideContent;
            waitForTitlebarRelease = !insideContent;
        };
        positionCallback = (ignored, x, y) -> markGeometryChanged();
        sizeCallback = (ignored, width, height) -> {
            markGeometryChanged();
            surfaceInvalid = true;
        };
        GLFW.glfwSetWindowFocusCallback(window, focusCallback);
        GLFW.glfwSetWindowPosCallback(window, positionCallback);
        GLFW.glfwSetWindowSizeCallback(window, sizeCallback);
    }

    private void markGeometryChanged() {
        geometryDirty = true;
        geometryChangedAt = System.nanoTime();
    }

    private void saveGeometryIfDue() {
        if (geometryDirty
                && GLFW.glfwGetWindowAttrib(handle, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_FALSE
                && System.nanoTime() - geometryChangedAt > 500_000_000L) {
            rememberWindowGeometry();
        }
    }
}
