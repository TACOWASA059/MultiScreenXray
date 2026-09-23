package com.github.tacowasa059.multiscreenxray.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Built-in starting points. Applying a preset always creates independent lists. */
public final class XrayPresets {
    private static final Map<String, XrayProfile> PRESETS = createPresets();

    private XrayPresets() {
    }

    public static List<String> names() {
        return List.copyOf(PRESETS.keySet());
    }

    public static XrayProfile create(String id) {
        XrayProfile source = PRESETS.getOrDefault(id, PRESETS.get("ores"));
        return source.copy();
    }

    public static void apply(XrayProfile target, String id) {
        XrayProfile source = create(id);
        target.name = source.name;
        target.preset = source.preset;
        target.blocks = new ArrayList<>(source.blocks);
        target.entities = new ArrayList<>(source.entities);
        target.showOutlines = source.showOutlines;
        target.showFluids = source.showFluids;
        target.showHand = source.showHand;
        target.outlineAlpha = source.outlineAlpha;
        target.brightness = source.brightness;
    }

    private static Map<String, XrayProfile> createPresets() {
        Map<String, XrayProfile> result = new LinkedHashMap<>();
        result.put("ores", profile("Ores", "ores", List.of(
                "#minecraft:coal_ores", "#minecraft:iron_ores", "#minecraft:copper_ores",
                "#minecraft:gold_ores", "#minecraft:redstone_ores", "#minecraft:lapis_ores",
                "#minecraft:diamond_ores", "#minecraft:emerald_ores",
                "minecraft:nether_quartz_ore", "minecraft:nether_gold_ore", "minecraft:ancient_debris"),
                List.of("*")));
        result.put("valuables", profile("Valuables", "valuables", List.of(
                "#minecraft:diamond_ores", "#minecraft:emerald_ores", "#minecraft:gold_ores",
                "minecraft:ancient_debris", "minecraft:chest", "minecraft:ender_chest",
                "minecraft:spawner"), List.of("minecraft:player", "minecraft:item")));
        result.put("nether", profile("Nether", "nether", List.of(
                "minecraft:ancient_debris", "minecraft:nether_quartz_ore", "minecraft:nether_gold_ore",
                "minecraft:chest", "minecraft:spawner"), List.of(
                "minecraft:player", "minecraft:wither_skeleton", "minecraft:blaze",
                "minecraft:piglin_brute", "minecraft:ghast")));
        result.put("structures", profile("Structures", "structures", List.of(
                "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "minecraft:spawner",
                "minecraft:end_portal_frame", "minecraft:reinforced_deepslate", "minecraft:lodestone"),
                List.of("minecraft:player", "minecraft:item", "minecraft:item_frame", "minecraft:armor_stand")));
        result.put("entities", profile("Entities", "entities", List.of(), List.of("*")));
        return result;
    }

    private static XrayProfile profile(String name, String preset, List<String> blocks, List<String> entities) {
        XrayProfile result = new XrayProfile();
        result.name = name;
        result.preset = preset;
        result.blocks = new ArrayList<>(blocks);
        result.entities = new ArrayList<>(entities);
        return result;
    }
}
