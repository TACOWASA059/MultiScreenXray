package com.github.tacowasa059.multiscreenxray.client.window;

import com.github.tacowasa059.multiscreenxray.client.MultiScreenXrayKeyMappings;
import com.github.tacowasa059.multiscreenxray.client.screen.XraySettingsScreen;
import com.github.tacowasa059.multiscreenxray.config.ConfigManager;
import com.github.tacowasa059.multiscreenxray.config.XrayConfig;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_Event;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Coordinates up to eight independently configured X-ray windows. */
public final class MultiWindowManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<ExtraWindow> WINDOWS = new ArrayList<>();
    private static final Set<Integer> FAILED_WINDOWS = new HashSet<>();
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
                FAILED_WINDOWS.remove(window.number());
                removed = true;
            }
        }
        if (removed) {
            desiredWindows = WINDOWS.size();
            ConfigManager.get().windowCount = desiredWindows;
            ConfigManager.changed();
        }
        boolean addPressed = InputConstants.isKeyDown(InputConstants.KEY_F8);
        if (MultiScreenXrayKeyMappings.CHANGE_WINDOW_COUNT.consumeClick()
                || addPressed && !addKeyDown)
            setWindowCount(minecraft, desiredWindows + (shiftPressedInAnyWindow(minecraft) ? -1 : 1));
        addKeyDown = addPressed;

        boolean settingsPressed = InputConstants.isKeyDown(InputConstants.KEY_F9);
        if (MultiScreenXrayKeyMappings.OPEN_SETTINGS.consumeClick()
                || settingsPressed && !settingsKeyDown) {
            SDLVideo.SDL_RaiseWindow(minecraft.getWindow().handle());
            minecraft.gui.setScreen(new XraySettingsScreen());
        }
        settingsKeyDown = settingsPressed;

        boolean pauseMenuOpen = minecraft.gui.screen() != null && minecraft.gui.screen().isPauseScreen();
        if (rendered && !pauseMenuOpen) for (ExtraWindow window : WINDOWS) {
            try {
                var overlayFrame = window.renderOverlay();
                window.render(overlayFrame);
                FAILED_WINDOWS.remove(window.number());
            } catch (RuntimeException exception) {
                if (FAILED_WINDOWS.add(window.number()))
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
        while (WINDOWS.size() > desiredWindows) {
            ExtraWindow removed = WINDOWS.remove(WINDOWS.size() - 1);
            removed.close();
            FAILED_WINDOWS.remove(removed.number());
        }
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
        FAILED_WINDOWS.clear();
    }

    private static boolean shiftPressedInAnyWindow(Minecraft minecraft) {
        return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
    }

    /** Consumes SDL window events belonging to an additional X-ray window. */
    public static boolean handleWindowEvent(SDL_Event event) {
        long handle = SDLEvents.SDL_GetWindowFromEvent(event);
        if (handle == 0L) return false;
        for (ExtraWindow window : WINDOWS) {
            if (window.handle() == handle) {
                window.handleEvent(event);
                return true;
            }
        }
        return false;
    }

    private static void runVisualTestCapture(Minecraft minecraft, boolean rendered) {
        String mode = System.getenv("MSXRAY_TEST_SCREEN");
        if (mode == null || mode.isBlank() || configuredAt == 0L) return;
        long elapsed = System.nanoTime() - configuredAt;
        if (mode.equalsIgnoreCase("all")) {
            if (visualTestStage == 0 && elapsed > 4_000_000_000L) {
                minecraft.gui.setScreen(new XraySettingsScreen());
                visualTestStage = 1;
            } else if (visualTestStage == 1 && rendered && elapsed > 6_000_000_000L) {
                captureVisualScreen(minecraft, "settings");
                visualTestStage = 2;
            } else if (visualTestStage == 2 && elapsed > 7_000_000_000L) {
                minecraft.gui.setScreen(XraySettingsScreen.blockLibrary(0));
                visualTestStage = 3;
            } else if (visualTestStage == 3 && rendered && elapsed > 9_000_000_000L) {
                captureVisualScreen(minecraft, "blocks");
                visualTestStage = 4;
            } else if (visualTestStage == 4 && elapsed > 10_000_000_000L) {
                minecraft.gui.setScreen(XraySettingsScreen.entityLibrary(0));
                visualTestStage = 5;
            } else if (visualTestStage == 5 && rendered && elapsed > 12_000_000_000L) {
                captureVisualScreen(minecraft, "entities");
                visualTestStage = 6;
            }
            return;
        }
        if (!testScreenOpened && elapsed > 5_000_000_000L) {
            testScreenOpened = true;
            if (mode.equalsIgnoreCase("blocks")) minecraft.gui.setScreen(XraySettingsScreen.blockLibrary(0));
            else if (mode.equalsIgnoreCase("entities")) minecraft.gui.setScreen(XraySettingsScreen.entityLibrary(0));
            else minecraft.gui.setScreen(new XraySettingsScreen());
        }
        if (!testScreenCaptured && testScreenOpened && rendered && elapsed > 7_000_000_000L) {
            testScreenCaptured = true;
            captureVisualScreen(minecraft, mode.toLowerCase());
        }
    }

    private static void captureWorldForTest(Minecraft minecraft, boolean rendered) {
        if (testWorldCaptured || !rendered || minecraft.gui.screen() != null
                || !("1".equals(System.getenv("MSXRAY_CAPTURE"))
                    || Boolean.getBoolean("multiscreenxray.capture")) || configuredAt == 0L
                || System.nanoTime() - configuredAt <= 10_000_000_000L) return;
        testWorldCaptured = true;
        captureVisualScreen(minecraft, "world");
    }

    private static void captureVisualScreen(Minecraft minecraft, String name) {
        Path output = minecraft.gameDirectory.toPath().resolve("screenshots")
                .resolve("multiscreenxray-gui-" + name + ".png");
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                Files.createDirectories(output.getParent());
                image.writeToFile(output);
                LOGGER.info("Captured MultiScreen X-ray GUI at {}", output);
            } catch (IOException exception) {
                LOGGER.error("Could not capture MultiScreen X-ray GUI", exception);
            }
        });
    }

}
