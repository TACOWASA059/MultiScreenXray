package com.github.tacowasa059.multiscreenxray.client.window;

import com.github.tacowasa059.multiscreenxray.client.render.OverlayBlitter;
import com.github.tacowasa059.multiscreenxray.client.render.XrayOverlayRenderer;
import com.github.tacowasa059.multiscreenxray.client.render.XrayWorldRenderer;
import com.github.tacowasa059.multiscreenxray.config.ConfigManager;
import com.github.tacowasa059.multiscreenxray.config.XrayConfig;
import com.github.tacowasa059.multiscreenxray.config.XrayProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWWindowFocusCallbackI;
import org.lwjgl.glfw.GLFWWindowPosCallbackI;
import org.lwjgl.glfw.GLFWWindowSizeCallbackI;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/** One GLFW window with its own saved profile and renderer. */
final class ExtraWindow implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Minecraft minecraft;
    private final int number;
    private long handle;
    private GLCapabilities capabilities;
    private XrayWorldRenderer xrayRenderer;
    private XrayOverlayRenderer overlayRenderer;
    private OverlayBlitter overlayBlitter;
    private XrayProfile profile;
    private long profileRevision = Long.MIN_VALUE;
    private boolean rendererInvalid = true;
    private final long openedAt = System.nanoTime();
    private long lastTitleUpdate;
    private boolean debugCaptured;
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
        refreshProfile();
        createWindow();
        LOGGER.info("Opened MultiScreen X-ray window {} with GLFW handle {}", number, handle);
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
        long mainHandle = minecraft.getWindow().handle();
        GLCapabilities mainCapabilities = GL.getCapabilities();
        try {
            GLFW.glfwMakeContextCurrent(handle);
            GL.setCapabilities(capabilities);
            if (rendererInvalid) {
                if (xrayRenderer != null) xrayRenderer.close();
                XrayConfig config = ConfigManager.get();
                xrayRenderer = new XrayWorldRenderer(profile, config.scanRadiusChunks, config.verticalRadius);
                rendererInvalid = false;
            }
            int[] width = new int[1];
            int[] height = new int[1];
            GLFW.glfwGetFramebufferSize(handle, width, height);
            if (width[0] > 0 && height[0] > 0) {
                xrayRenderer.render(minecraft, width[0], height[0], overlayFrame.worldFov());
                overlayBlitter.draw(overlayFrame.textureId());
                updateTitle();
                captureForTest(width[0], height[0]);
                GLFW.glfwSwapBuffers(handle);
            }
        } finally {
            GLFW.glfwMakeContextCurrent(mainHandle);
            GL.setCapabilities(mainCapabilities);
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
        GLCapabilities mainCapabilities = GL.getCapabilities();
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,
                GLFW.glfwGetWindowAttrib(mainHandle, GLFW.GLFW_CONTEXT_VERSION_MAJOR));
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,
                GLFW.glfwGetWindowAttrib(mainHandle, GLFW.GLFW_CONTEXT_VERSION_MINOR));
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,
                GLFW.glfwGetWindowAttrib(mainHandle, GLFW.GLFW_OPENGL_PROFILE));
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT,
                GLFW.glfwGetWindowAttrib(mainHandle, GLFW.GLFW_OPENGL_FORWARD_COMPAT));
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        long newHandle = GLFW.glfwCreateWindow(profile.windowWidth, profile.windowHeight,
                "MultiScreen X-ray " + number + " - " + profile.name, 0L, mainHandle);
        if (newHandle == 0L) throw new IllegalStateException("Could not create X-ray window " + number);
        if (profile.windowX != Integer.MIN_VALUE && profile.windowY != Integer.MIN_VALUE)
            GLFW.glfwSetWindowPos(newHandle, profile.windowX, profile.windowY);
        try {
            GLFW.glfwMakeContextCurrent(newHandle);
            capabilities = GL.createCapabilities();
            XrayConfig config = ConfigManager.get();
            xrayRenderer = new XrayWorldRenderer(profile, config.scanRadiusChunks, config.verticalRadius);
            overlayBlitter = new OverlayBlitter();
            rendererInvalid = false;
            GLFW.glfwSwapInterval(0);
            handle = newHandle;
            installWindowCallbacks(newHandle, mainHandle);
        } catch (RuntimeException exception) {
            GLFW.glfwDestroyWindow(newHandle);
            capabilities = null;
            throw exception;
        } finally {
            GLFW.glfwMakeContextCurrent(mainHandle);
            GL.setCapabilities(mainCapabilities);
        }
    }

    @Override
    public void close() {
        if (handle == 0L) return;
        LOGGER.info("Closing MultiScreen X-ray window {}", number);
        rememberWindowGeometry();
        if (overlayRenderer != null) {
            overlayRenderer.close();
            overlayRenderer = null;
        }
        long previousContext = GLFW.glfwGetCurrentContext();
        GLCapabilities previousCapabilities = previousContext == 0L ? null : GL.getCapabilities();
        try {
            GLFW.glfwMakeContextCurrent(handle);
            GL.setCapabilities(capabilities);
            if (xrayRenderer != null) xrayRenderer.close();
            if (overlayBlitter != null) overlayBlitter.close();
        } finally {
            GLFW.glfwMakeContextCurrent(previousContext);
            GL.setCapabilities(previousCapabilities);
            Callbacks.glfwFreeCallbacks(handle);
            GLFW.glfwDestroyWindow(handle);
            handle = 0L;
            capabilities = null;
        }
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
        sizeCallback = (ignored, width, height) -> markGeometryChanged();
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

    private void captureForTest(int width, int height) {
        long now = System.nanoTime();
        if (debugCaptured || !("1".equals(System.getenv("MSXRAY_CAPTURE"))
                || Boolean.getBoolean("multiscreenxray.capture"))
                || now - openedAt <= 10_000_000_000L) return;
        debugCaptured = true;
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int offset = (y * width + x) * 4;
            int red = pixels.get(offset) & 255;
            int green = pixels.get(offset + 1) & 255;
            int blue = pixels.get(offset + 2) & 255;
            int alpha = pixels.get(offset + 3) & 255;
            image.setRGB(x, height - 1 - y, alpha << 24 | red << 16 | green << 8 | blue);
        }
        Path path = minecraft.gameDirectory.toPath().resolve("screenshots")
                .resolve("multiscreenxray-window-" + number + ".png");
        try {
            Files.createDirectories(path.getParent());
            ImageIO.write(image, "png", path.toFile());
            LOGGER.info("Captured X-ray window {} at {}", number, path);
        } catch (IOException exception) {
            LOGGER.error("Could not capture X-ray window {}", number, exception);
        }
    }
}
