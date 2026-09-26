package dev.stonebackpack.plugin.manager;

import dev.stonebackpack.plugin.config.ConfigUpdater;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageManager {
    private static final String[] SHIPPED_LANGUAGES = {"en", "de"};

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"),
            Map.entry('f', "white"), Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"), Map.entry('o', "italic"),
            Map.entry('r', "reset")
    );

    private final Plugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private YamlConfiguration messages;
    private String activeLanguage;

    // Every message string in messages.yml is static until the next reload,
    // so the & / hex -> MiniMessage conversion (a regex pass plus a
    // character-by-character scan) is done exactly once per key here,
    // instead of on every single message sent. At 250+ players triggering
    // frequent command feedback, join messages, and update-checker pings,
    // that conversion work would otherwise be repeated thousands of times
    // for text that never changes between reloads.
    private volatile Map<String, String> convertedCache = Map.of();

    public MessageManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load(String language) {
        for (String shipped : SHIPPED_LANGUAGES) {
            ensureLanguageFile(shipped);
        }

        String resolved = language == null || language.isBlank() ? "en" : language.trim().toLowerCase();
        File file = languageFile(resolved);
        if (!file.exists()) {
            plugin.getLogger().warning("No messages.yml found for language '" + resolved + "', falling back to 'en'.");
            resolved = "en";
            file = languageFile(resolved);
        }

        try {
            ConfigUpdater.UpdateResult result = ConfigUpdater.update(plugin, "languages/" + resolved + "/messages.yml", file);
            if (result.updated()) {
                plugin.getLogger().info("Added " + result.addedKeys() + " new message(s) to languages/" + resolved + "/messages.yml, existing text was kept.");
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not update messages.yml for language '" + resolved + "': " + exception.getMessage());
        }

        messages = YamlConfiguration.loadConfiguration(file);
        activeLanguage = resolved;
        rebuildConvertedCache();
    }

    private void rebuildConvertedCache() {
        Map<String, String> cache = new HashMap<>();
        for (String key : messages.getKeys(true)) {
            if (messages.isString(key)) {
                cache.put(key, legacyToMiniMessage(messages.getString(key)));
            }
        }
        convertedCache = cache;
    }

    public void reload(String language) {
        load(language);
    }

    public String getActiveLanguage() {
        return activeLanguage;
    }

    private void ensureLanguageFile(String language) {
        File file = languageFile(language);
        if (!file.exists()) {
            plugin.saveResource("languages/" + language + "/messages.yml", false);
        }
    }

    private File languageFile(String language) {
        return new File(plugin.getDataFolder(), "languages/" + language + "/messages.yml");
    }

    public String raw(String path, Map<String, String> placeholders) {
        String value = messages.getString(path);
        if (value == null) {
            return path;
        }
        return substitute(value, placeholders);
    }

    private String substitute(String template, Map<String, String> placeholders) {
        if (placeholders == null) {
            return template;
        }
        String value = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return value;
    }

    public Component component(String path, Map<String, String> placeholders) {
        String template = convertedCache.getOrDefault(path, path);
        return parseConverted(substituteEscaped(template, placeholders));
    }

    // Values are inserted into already-converted MiniMessage, so tags in them
    // (a typed player name, a version string from Modrinth) must be escaped
    // or they would be rendered, click events included.
    private String substituteEscaped(String template, Map<String, String> placeholders) {
        if (placeholders == null) {
            return template;
        }
        String value = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", miniMessage.escapeTags(entry.getValue()));
        }
        return value;
    }

    public Component parse(String text) {
        return miniMessage.deserialize(legacyToMiniMessage(text));
    }

    public String toMiniMessageSyntax(String text) {
        return legacyToMiniMessage(text);
    }

    public Component parseConverted(String miniMessageSyntax) {
        return miniMessage.deserialize(miniMessageSyntax);
    }

    private String legacyToMiniMessage(String text) {
        Matcher hexMatcher = HEX_PATTERN.matcher(text);
        StringBuilder afterHex = new StringBuilder();
        while (hexMatcher.find()) {
            hexMatcher.appendReplacement(afterHex, "<#" + hexMatcher.group(1) + ">");
        }
        hexMatcher.appendTail(afterHex);

        StringBuilder result = new StringBuilder();
        String value = afterHex.toString();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '&' && i + 1 < value.length()) {
                String tag = LEGACY_TAGS.get(Character.toLowerCase(value.charAt(i + 1)));
                if (tag != null) {
                    result.append('<').append(tag).append('>');
                    i++;
                    continue;
                }
            }
            result.append(c);
        }
        return result.toString();
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        String prefixTemplate = convertedCache.getOrDefault("prefix", "");
        String bodyTemplate = convertedCache.getOrDefault(path, path);
        String combined = prefixTemplate + substituteEscaped(bodyTemplate, placeholders);
        sender.sendMessage(parseConverted(combined));
    }

    public void sendRaw(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(component(path, placeholders));
    }

    public void sendPlain(CommandSender sender, String text) {
        sender.sendMessage(parse(text));
    }
}
