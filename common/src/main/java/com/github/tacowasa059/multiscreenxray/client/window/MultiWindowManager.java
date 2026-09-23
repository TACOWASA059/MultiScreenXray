package com.github.tacowasa059.multiscreenxray.client.window;

import com.github.tacowasa059.multiscreenxray.client.MultiScreenXrayKeyMappings;
import com.github.tacowasa059.multiscreenxray.client.screen.XraySettingsScreen;
import com.github.tacowasa059.multiscreenxray.config.ConfigManager;
import com.github.tacowasa059.multiscreenxray.config.XrayConfig;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Coordinates up to eight independently configured X-ray windows. */
public final class MultiWindowManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<ExtraWindow> WINDOWS = new ArrayList<>();
    private static boolean addKeyDown;
    private static boolean settingsKeyDown;
    private static boolean configured;
    private static int desiredWindows = 1;
    private static long configuredAt;
    private static boolean testScreenOpened;
    private static boolean testScreenCaptured;
    private static boolean testWorldCaptured;
    private static int visualTestStage;

    private MultiWindowManager() { }

    public static void afterFrame(Minecraft minecraft, boolean rendered) {
        if (minecraft.level == null) {
            closeAll();
            configured = false;
            addKeyDown = false;
            settingsKeyDown = false;
            return;
        }
        if (!configured) {
            configured = true;
            ConfigManager.load(minecraft.gameDirectory.toPath().resolve("config"));
            desiredWindows = ConfigManager.get().windowCount;
            reconcile(minecraft);
            configuredAt = System.nanoTime();
        }
        boolean removed = false;
        for (int index = WINDOWS.size() - 1; index >= 0; index--) {
            ExtraWindow window = WINDOWS.get(index);
            if (window.shouldClose()) {
                window.close();
                WINDOWS.remove(index);
                removed = true;
            }
        }
        if (removed) {
            desiredWindows = WINDOWS.size();
            ConfigManager.get().windowCount = desiredWindows;
            ConfigManager.changed();
        }
        boolean addPressed = keyPressedInExtraWindow(GLFW.GLFW_KEY_F8);
        if (MultiScreenXrayKeyMappings.CHANGE_WINDOW_COUNT.consumeClick()
                || addPressed && !addKeyDown)
            setWindowCount(minecraft, desiredWindows + (shiftPressedInAnyWindow(minecraft) ? -1 : 1));
        addKeyDown = addPressed;

        boolean settingsPressed = keyPressedInExtraWindow(GLFW.GLFW_KEY_F9);
        if (MultiScreenXrayKeyMappings.OPEN_SETTINGS.consumeClick()
                || settingsPressed && !settingsKeyDown) {
            GLFW.glfwFocusWindow(minecraft.getWindow().getWindow());
            minecraft.setScreen(new XraySettingsScreen());
        }
        settingsKeyDown = settingsPressed;

        boolean pauseMenuOpen = minecraft.screen != null && minecraft.screen.isPauseScreen();
        if (rendered && !pauseMenuOpen) for (ExtraWindow window : WINDOWS) {
            try {
                var overlayFrame = window.renderOverlay();
                GL11.glFinish();
                window.render(overlayFrame);
            } catch (RuntimeException exception) {
                LOGGER.error("X-ray window {} render failed", window.number(), exception);
            }
        } else if (pauseMenuOpen) {
            for (ExtraWindow window : WINDOWS) window.idle();
        }
        captureWorldForTest(minecraft, rendered);
        runVisualTestCapture(minecraft, rendered);
    }

    public static int windowCount() { return desiredWindows; }

    public static void applyConfig(Minecraft minecraft) {
        desiredWindows = ConfigManager.get().windowCount;
        for (ExtraWindow window : WINDOWS) window.invalidateProfile();
        reconcile(minecraft);
    }

    public static void setWindowCount(Minecraft minecraft, int count) {
        desiredWindows = Math.max(0, Math.min(XrayConfig.MAX_WINDOWS, count));
        ConfigManager.get().windowCount = desiredWindows;
        ConfigManager.changed();
        reconcile(minecraft);
    }

    private static void reconcile(Minecraft minecraft) {
        while (WINDOWS.size() < desiredWindows) WINDOWS.add(createWindow(minecraft));
        while (WINDOWS.size() > desiredWindows) WINDOWS.remove(WINDOWS.size() - 1).close();
    }

    private static ExtraWindow createWindow(Minecraft minecraft) {
        int number = 1;
        while (find(number) != null) number++;
        return new ExtraWindow(minecraft, number);
    }

    private static ExtraWindow find(int number) {
        for (ExtraWindow window : WINDOWS) if (window.number() == number) return window;
        return null;
    }

    public static void closeAll() {
        for (ExtraWindow window : WINDOWS) window.close();
        WINDOWS.clear();
    }

    private static boolean keyPressedInExtraWindow(int key) {
        for (ExtraWindow window : WINDOWS)
            if (GLFW.glfwGetKey(window.handle(), key) == GLFW.GLFW_PRESS) return true;
        return false;
    }

    private static boolean shiftPressedInAnyWindow(Minecraft minecraft) {
        if (shiftPressed(minecraft.getWindow().getWindow())) return true;
        for (ExtraWindow window : WINDOWS) if (shiftPressed(window.handle())) return true;
        return false;
    }

    private static boolean shiftPressed(long handle) {
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private static void runVisualTestCapture(Minecraft minecraft, boolean rendered) {
        String mode = System.getenv("MSXRAY_TEST_SCREEN");
        if (mode == null || mode.isBlank() || configuredAt == 0L) return;
        long elapsed = System.nanoTime() - configuredAt;
        if (mode.equalsIgnoreCase("all")) {
            if (visualTestStage == 0 && elapsed > 4_000_000_000L) {
                minecraft.setScreen(new XraySettingsScreen());
                visualTestStage = 1;
            } else if (visualTestStage == 1 && rendered && elapsed > 6_000_000_000L) {
                captureVisualScreen(minecraft, "settings");
                visualTestStage = 2;
            } else if (visualTestStage == 2 && elapsed > 7_000_000_000L) {
                minecraft.setScreen(XraySettingsScreen.blockLibrary(0));
                visualTestStage = 3;
            } else if (visualTestStage == 3 && rendered && elapsed > 9_000_000_000L) {
                captureVisualScreen(minecraft, "blocks");
                visualTestStage = 4;
            } else if (visualTestStage == 4 && elapsed > 10_000_000_000L) {
                minecraft.setScreen(XraySettingsScreen.entityLibrary(0));
                visualTestStage = 5;
            } else if (visualTestStage == 5 && rendered && elapsed > 12_000_000_000L) {
                captureVisualScreen(minecraft, "entities");
                visualTestStage = 6;
            }
            return;
        }
        if (!testScreenOpened && elapsed > 5_000_000_000L) {
            testScreenOpened = true;
            if (mode.equalsIgnoreCase("blocks")) minecraft.setScreen(XraySettingsScreen.blockLibrary(0));
            else if (mode.equalsIgnoreCase("entities")) minecraft.setScreen(XraySettingsScreen.entityLibrary(0));
            else minecraft.setScreen(new XraySettingsScreen());
        }
        if (!testScreenCaptured && testScreenOpened && rendered && elapsed > 7_000_000_000L) {
            testScreenCaptured = true;
            captureVisualScreen(minecraft, mode.toLowerCase());
        }
    }

    private static void captureWorldForTest(Minecraft minecraft, boolean rendered) {
        if (testWorldCaptured || !rendered || minecraft.screen != null
                || !("1".equals(System.getenv("MSXRAY_CAPTURE"))
                    || Boolean.getBoolean("multiscreenxray.capture")) || configuredAt == 0L
                || System.nanoTime() - configuredAt <= 10_000_000_000L) return;
        testWorldCaptured = true;
        captureVisualScreen(minecraft, "world");
    }

    private static void captureVisualScreen(Minecraft minecraft, String name) {
        Path output = minecraft.gameDirectory.toPath().resolve("screenshots")
                .resolve("multiscreenxray-gui-" + name + ".png");
        try {
            Files.createDirectories(output.getParent());
            try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                image.writeToFile(output);
            }
            LOGGER.info("Captured MultiScreen X-ray GUI at {}", output);
        } catch (IOException exception) {
            LOGGER.error("Could not capture MultiScreen X-ray GUI", exception);
        }
    }

}
