package com.github.tacowasa059.multiscreenxray.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class MultiScreenXrayKeyMappings {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("multiscreenxray", "main"));

    public static final KeyMapping CHANGE_WINDOW_COUNT = new KeyMapping(
            "key.multiscreenxray.change_window_count",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            CATEGORY);

    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.multiscreenxray.open_settings",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            CATEGORY);

    private MultiScreenXrayKeyMappings() { }
}
