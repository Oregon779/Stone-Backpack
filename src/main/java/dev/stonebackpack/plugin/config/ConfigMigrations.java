package dev.stonebackpack.plugin.config;

import java.util.List;

public final class ConfigMigrations {
    /**
     * Steps for config.yml. Only needed when an option is renamed, moved or removed - new
     * options are added automatically from the bundled config.yml. To add one, raise
     * {@code config-version} in the bundled config.yml and append a step with that version,
     * for example {@code ConfigMigration.rename(2, "command.cooldown-seconds", "command.cooldown")}.
     */
    public static final List<ConfigMigration> CONFIG = List.of();

    private ConfigMigrations() {
    }
}
