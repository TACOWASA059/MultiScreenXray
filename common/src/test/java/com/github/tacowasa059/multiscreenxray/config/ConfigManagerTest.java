package com.github.tacowasa059.multiscreenxray.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {
    @TempDir
    Path directory;

    @Test
    void savesAndReloadsIndependentScreensAndWindowGeometry() {
        XrayConfig config = ConfigManager.load(directory);
        config.windowCount = 3;
        config.normalize();
        XrayProfile third = config.screen(2);
        third.name = "Underground bases";
        third.blocks = SelectorRule.parseCsv("minecraft:chest, #minecraft:diamond_ores");
        third.entities = SelectorRule.parseCsv("minecraft:player, minecraft:item");
        third.windowX = 123;
        third.windowY = 234;
        third.windowWidth = 1280;
        third.windowHeight = 720;
        ConfigManager.changed();

        XrayConfig reloaded = ConfigManager.load(directory);
        assertEquals(3, reloaded.windowCount);
        assertEquals("Underground bases", reloaded.screen(2).name);
        assertEquals(2, reloaded.screen(2).blocks.size());
        assertEquals(123, reloaded.screen(2).windowX);
        assertEquals(234, reloaded.screen(2).windowY);
        assertEquals(1280, reloaded.screen(2).windowWidth);
        assertEquals(720, reloaded.screen(2).windowHeight);
    }

    @Test
    void replacesMalformedJsonWithUsableDefaults() throws Exception {
        Path file = directory.resolve("multiscreenxray.json");
        Files.writeString(file, "{ definitely not json");

        XrayConfig config = ConfigManager.load(directory);

        assertEquals(1, config.windowCount);
        assertTrue(config.screen(0).blocks.size() > 5);
        assertTrue(Files.readString(file).contains("\"windowCount\": 1"));
    }
}
