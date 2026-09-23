package com.github.tacowasa059.multiscreenxray.config;

import java.util.ArrayList;
import java.util.List;

/** Persisted settings for one additional X-ray window. */
public final class XrayProfile {
    public String name = "Ores";
    public String preset = "ores";
    public List<String> blocks = new ArrayList<>();
    public List<String> entities = new ArrayList<>();
    public boolean showOutlines = true;
    public boolean showFluids = true;
    public boolean showHand = true;
    public float outlineAlpha = 0.38f;
    public float brightness = 1.05f;
    public int windowX = Integer.MIN_VALUE;
    public int windowY = Integer.MIN_VALUE;
    public int windowWidth = 960;
    public int windowHeight = 540;

    public XrayProfile() {
    }

    public XrayProfile copy() {
        XrayProfile result = new XrayProfile();
        result.name = name;
        result.preset = preset;
        result.blocks = new ArrayList<>(blocks);
        result.entities = new ArrayList<>(entities);
        result.showOutlines = showOutlines;
        result.showFluids = showFluids;
        result.showHand = showHand;
        result.outlineAlpha = outlineAlpha;
        result.brightness = brightness;
        result.windowX = windowX;
        result.windowY = windowY;
        result.windowWidth = windowWidth;
        result.windowHeight = windowHeight;
        return result;
    }

    public void normalize(int index) {
        if (name == null || name.isBlank()) name = "Screen " + (index + 1);
        name = name.strip();
        if (name.length() > 40) name = name.substring(0, 40);
        if (preset == null || preset.isBlank()) preset = "custom";
        blocks = SelectorRule.normalizeAll(blocks);
        entities = SelectorRule.normalizeAll(entities);
        outlineAlpha = clamp(outlineAlpha, 0.05f, 1.0f);
        brightness = clamp(brightness, 0.35f, 1.5f);
        windowWidth = Math.max(320, Math.min(7680, windowWidth));
        windowHeight = Math.max(240, Math.min(4320, windowHeight));
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
