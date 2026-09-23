package com.github.tacowasa059.multiscreenxray.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XrayConfigTest {
    @Test
    void clampsRangesAndCreatesAProfileForEveryWindow() {
        XrayConfig config = new XrayConfig();
        config.windowCount = 99;
        config.scanRadiusChunks = -4;
        config.verticalRadius = 900;
        config.normalize();
        assertEquals(XrayConfig.MAX_WINDOWS, config.windowCount);
        assertEquals(XrayConfig.MAX_WINDOWS, config.screens.size());
        assertEquals(1, config.scanRadiusChunks);
        assertEquals(256, config.verticalRadius);
    }

    @Test
    void zeroWindowsKeepsAnEditableSavedProfile() {
        XrayConfig config = new XrayConfig();
        config.windowCount = -1;
        config.normalize();
        assertEquals(0, config.windowCount);
        assertEquals(1, config.screens.size());
    }

    @Test
    void presetCopiesDoNotShareMutableLists() {
        XrayProfile first = XrayPresets.create("ores");
        XrayProfile second = XrayPresets.create("ores");
        assertNotSame(first.blocks, second.blocks);
        first.blocks.clear();
        assertTrue(second.blocks.size() > 5);
    }

    @Test
    void clampsSavedWindowSizeToUsableBounds() {
        XrayProfile profile = new XrayProfile();
        profile.windowWidth = 12;
        profile.windowHeight = 99999;
        profile.normalize(0);
        assertEquals(320, profile.windowWidth);
        assertEquals(4320, profile.windowHeight);
    }
}
