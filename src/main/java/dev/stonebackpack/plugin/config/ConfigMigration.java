package dev.stonebackpack.plugin.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.function.Consumer;

/**
 * One structural change to a versioned file. {@code version} is the file's version after
 * this step; pending steps run once each, in ascending order, before missing options are
 * added.
 */
public record ConfigMigration(int version, String description, Consumer<ConfigurationSection> action) {

    public static ConfigMigration rename(int version, String from, String to) {
        return new ConfigMigration(version, "moved '" + from + "' to '" + to + "'", config -> move(config, from, to));
    }

    public static ConfigMigration remove(int version, String path) {
        return new ConfigMigration(version, "removed '" + path + "'", config -> config.set(path, null));
    }

    // Keeps the server's value and comments. Skips if the old option is gone
    // or the new one already exists, so a partly edited file stays safe.
    private static void move(ConfigurationSection config, String from, String to) {
        if (!config.contains(from) || config.contains(to)) {
            return;
        }
        ConfigurationSection source = config.getConfigurationSection(from);
        if (source != null) {
            ConfigurationSection target = config.createSection(to);
            for (String key : source.getKeys(true)) {
                if (source.isConfigurationSection(key)) {
                    target.createSection(key);
                } else {
                    target.set(key, source.get(key));
                }
                target.setComments(key, source.getComments(key));
                target.setInlineComments(key, source.getInlineComments(key));
            }
        } else {
            config.set(to, config.get(from));
        }
        config.setComments(to, config.getComments(from));
        config.setInlineComments(to, config.getInlineComments(from));
        config.set(from, null);
    }
}
