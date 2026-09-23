package com.github.tacowasa059.multiscreenxray.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Shared parser for block and entity selectors used by config and UI. */
public final class SelectorRule {
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private SelectorRule() {
    }

    public static String normalize(String raw) {
        if (raw == null) return null;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return null;
        if (value.equals("*")) return value;
        boolean tag = value.charAt(0) == '#';
        String id = tag ? value.substring(1) : value;
        if (!id.contains(":")) id = "minecraft:" + id;
        if (!IDENTIFIER.matcher(id).matches()) return null;
        return tag ? "#" + id : id;
    }

    public static List<String> normalizeAll(Iterable<String> input) {
        Set<String> unique = new LinkedHashSet<>();
        if (input != null) {
            for (String raw : input) {
                String normalized = normalize(raw);
                if (normalized != null) unique.add(normalized);
            }
        }
        return new ArrayList<>(unique);
    }

    public static List<String> parseCsv(String input) {
        if (input == null || input.isBlank()) return new ArrayList<>();
        return normalizeAll(List.of(input.split("[,\\n]")));
    }
}
