package dev.stonebackpack.plugin.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigUpdater {
    private static final Pattern KEY_LINE = Pattern.compile("^(\\s*)([^\\s:#'\"][^:]*):(?:\\s(.*))?$");

    private ConfigUpdater() {
    }

    public record UpdateResult(boolean updated, int addedKeys) {
    }

    public static UpdateResult update(org.bukkit.plugin.Plugin plugin, String resourcePath, File targetFile) throws IOException {
        List<String> defaultLines = readResourceLines(plugin, resourcePath);
        YamlConfiguration existing = YamlConfiguration.loadConfiguration(targetFile);

        List<String> merged = new ArrayList<>(defaultLines.size());
        Deque<String> pathStack = new ArrayDeque<>();
        Deque<Integer> indentStack = new ArrayDeque<>();
        int addedKeys = 0;

        for (String line : defaultLines) {
            Matcher matcher = KEY_LINE.matcher(line);
            if (line.isBlank() || line.trim().startsWith("#") || !matcher.matches()) {
                merged.add(line);
                continue;
            }

            int indent = matcher.group(1).length();
            String key = matcher.group(2).trim();
            String value = matcher.group(3) == null ? "" : matcher.group(3).trim();

            while (!indentStack.isEmpty() && indentStack.peek() >= indent) {
                indentStack.pop();
                pathStack.pop();
            }

            String path = pathStack.isEmpty() ? key : String.join(".", reversedCopy(pathStack)) + "." + key;

            if (value.isEmpty()) {
                merged.add(line);
                indentStack.push(indent);
                pathStack.push(key);
                continue;
            }

            if (existing.isSet(path) && !(existing.get(path) instanceof org.bukkit.configuration.ConfigurationSection)) {
                Object existingValue = existing.get(path);
                merged.add(matcher.group(1) + key + ": " + serialize(existingValue));
            } else {
                merged.add(line);
                addedKeys++;
            }
        }

        if (addedKeys == 0) {
            return new UpdateResult(false, 0);
        }

        Files.write(targetFile.toPath(), merged, StandardCharsets.UTF_8);
        return new UpdateResult(true, addedKeys);
    }

    private static List<String> reversedCopy(Deque<String> stack) {
        List<String> list = new ArrayList<>(stack);
        java.util.Collections.reverse(list);
        return list;
    }

    private static List<String> readResourceLines(org.bukkit.plugin.Plugin plugin, String resourcePath) throws IOException {
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) {
                throw new IOException("Bundled default resource not found: " + resourcePath);
            }
            List<String> lines = new ArrayList<>();
            try (var reader = new java.io.BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            return lines;
        }
    }

    private static String serialize(Object value) {
        if (value instanceof String string) {
            String escaped = string.replace("\\", "\\\\").replace("\"", "\\\"");
            return "\"" + escaped + "\"";
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                Object element = list.get(i);
                if (element instanceof String string) {
                    builder.append('"').append(string.replace("\"", "\\\"")).append('"');
                } else {
                    builder.append(element);
                }
            }
            return builder.append("]").toString();
        }
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }
}

