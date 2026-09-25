package com.github.tacowasa059.multiscreenxray.neoforge.mixin;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.GpuBackend;
import net.neoforged.fml.loading.EarlyLoadingScreenController;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

@Mixin(Window.class)
public abstract class VulkanEarlyWindowMixin {
    @Inject(method = "createGlfwWindow", at = @At("HEAD"))
    private static void multiscreenxray$replaceOpenGlEarlyWindowForVulkan(
            int width,
            int height,
            String title,
            long monitor,
            GpuBackend backend,
            CallbackInfoReturnable<Long> cir
    ) {
        if (!"Vulkan".equalsIgnoreCase(backend.getName())) {
            return;
        }

        EarlyLoadingScreenController earlyWindow = EarlyLoadingScreenController.current();
        if (earlyWindow == null) {
            return;
        }

        long handle = earlyWindow.takeOverGlfwWindow();
        if (handle != 0L) {
            // NeoForge renders the early window on its own thread. takeOverGlfwWindow
            // moves the context to Minecraft's render thread, but LWJGL capabilities
            // are thread-local and must be installed here before its GL resources close.
            GL.createCapabilities();
        }
        clearEarlyWindowProvider();
        closeEarlyWindow(earlyWindow);

        if (handle != 0L) {
            GLFW.glfwMakeContextCurrent(0L);
            GL.setCapabilities(null);
            GLFW.glfwDestroyWindow(handle);
        }
    }

    private static void clearEarlyWindowProvider() {
        try {
            Class<?> handler = Class.forName("net.neoforged.fml.loading.ImmediateWindowHandler");
            Field provider = handler.getDeclaredField("provider");
            provider.setAccessible(true);
            provider.set(null, null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not release NeoForge's OpenGL early window for Vulkan", exception);
        }
    }

    private static void closeEarlyWindow(EarlyLoadingScreenController earlyWindow) {
        try {
            earlyWindow.getClass().getMethod("close").invoke(earlyWindow);
        } catch (NoSuchMethodException ignored) {
            // A custom early-window provider may not own resources that need closing.
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Could not close NeoForge's OpenGL early window for Vulkan", exception);
        }
    }
}