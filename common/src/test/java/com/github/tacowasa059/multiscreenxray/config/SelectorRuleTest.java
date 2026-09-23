package com.github.tacowasa059.multiscreenxray.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SelectorRuleTest {
    @Test
    void normalizesIdsTagsAndWildcard() {
        assertEquals("minecraft:diamond_ore", SelectorRule.normalize(" Diamond_Ore "));
        assertEquals("#minecraft:diamond_ores", SelectorRule.normalize("#diamond_ores"));
        assertEquals("modid:deep/ore", SelectorRule.normalize("MODID:deep/ore"));
        assertEquals("*", SelectorRule.normalize(" * "));
    }

    @Test
    void rejectsInvalidSelectorsAndDeduplicatesCsv() {
        assertNull(SelectorRule.normalize("bad selector"));
        assertEquals(List.of("minecraft:diamond_ore", "#minecraft:diamond_ores"),
                SelectorRule.parseCsv("diamond_ore, minecraft:diamond_ore, #diamond_ores, bad selector"));
    }
}
