package com.github.tacowasa059.multiscreenxray.config;

import java.util.ArrayList;
import java.util.List;

/** Root configuration stored in config/multiscreenxray.json. */
public final class XrayConfig {
    public static final int MAX_WINDOWS = 8;

    public int windowCount = 1;
    public int scanRadiusChunks = 6;
    public int verticalRadius = 112;
    public List<XrayProfile> screens = new ArrayList<>();

    public XrayConfig() {
    }

    public static XrayConfig defaults() {
        XrayConfig config = new XrayConfig();
        config.screens.add(XrayPresets.create("ores"));
        return config;
    }

    public void normalize() {
        windowCount = Math.max(0, Math.min(MAX_WINDOWS, windowCount));
        scanRadiusChunks = Math.max(1, Math.min(12, scanRadiusChunks));
        verticalRadius = Math.max(16, Math.min(256, verticalRadius));
        if (screens == null) screens = new ArrayList<>();
        screens.removeIf(profile -> profile == null);
        while (screens.size() < Math.max(1, windowCount)) {
            XrayProfile added = XrayPresets.create(XrayPresets.names().get(screens.size() % XrayPresets.names().size()));
            added.name = "Screen " + (screens.size() + 1) + " - " + added.name;
            screens.add(added);
        }
        while (screens.size() > MAX_WINDOWS) screens.remove(screens.size() - 1);
        for (int index = 0; index < screens.size(); index++) screens.get(index).normalize(index);
    }

    public XrayProfile screen(int index) {
        normalize();
        return screens.get(Math.max(0, Math.min(index, screens.size() - 1)));
    }
}
