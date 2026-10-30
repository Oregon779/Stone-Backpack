package dev.stonebackpack.plugin.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigUpdaterTest {
    private static final String DEFAULTS = """
            # Header

            # Language of all texts
            language: en

            features:
              example:
                # Turns the example on
                enabled: true
                mode: fast
              other: 1

            # Backups section
            backups:
              enabled: true
              interval-seconds: 60
            """;

    @TempDir
    Path dir;

    private final List<String> log = new ArrayList<>();
    private final Logger logger = Logger.getAnonymousLogger();

    @BeforeEach
    void captureLog() {
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                log.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    private Path file(String content) throws IOException {
        Path path = dir.resolve("config.yml");
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private ConfigUpdater.Result update(String defaults, Path path, ConfigUpdater.Options options) throws IOException {
        return ConfigUpdater.update(defaults, path.toFile(), "config.yml", options, logger);
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void addsMissingNestedOptionWithItsCommentAndKeepsEveryOtherLine() throws IOException {
        Path path = file("""
                # my own header
                language: de   # german please

                features:
                  example:
                    mode: slow
                  other: 5
                custom-key: keep me

                # My backups note
                backups:
                  enabled: false
                  interval-seconds: 120
                """);

        ConfigUpdater.Result result = update(DEFAULTS, path, ConfigUpdater.Options.plain());

        assertEquals(List.of("features.example.enabled"), result.addedPaths());
        assertEquals("""
                # my own header
                language: de   # german please

                features:
                  example:
                    # Turns the example on
                    enabled: true
                    mode: slow
                  other: 5
                custom-key: keep me

                # My backups note
                backups:
                  enabled: false
                  interval-seconds: 120
                """, read(path));
    }

    @Test
    void addsMissingSectionsAtTheirDefaultPosition() throws IOException {
        Path path = file("""
                language: de

                backups:
                  enabled: false
                """);

        ConfigUpdater.Result result = update(DEFAULTS, path, ConfigUpdater.Options.plain());

        assertEquals(List.of("features", "backups.interval-seconds"), result.addedPaths());
        assertEquals("""
                language: de

                features:
                  example:
                    # Turns the example on
                    enabled: true
                    mode: fast
                  other: 1

                backups:
                  enabled: false
                  interval-seconds: 60
                """, read(path));
    }

    @Test
    void followsTheServersIndentation() throws IOException {
        Path path = file("""
                language: en
                features:
                    example:
                        mode: slow
                    other: 5
                backups:
                    enabled: true
                    interval-seconds: 60
                """);

        update(DEFAULTS, path, ConfigUpdater.Options.plain());

        assertEquals("""
                language: en
                features:
                    example:
                        # Turns the example on
                        enabled: true
                        mode: slow
                    other: 5
                backups:
                    enabled: true
                    interval-seconds: 60
                """, read(path));
    }

    @Test
    void neverChangesExistingValuesOfAnyType() throws IOException {
        String original = """
                language: "Größe \\"quoted\\" & <red>text</red> \\\\ backslash"
                features:
                  example:
                    enabled: false
                    mode: [fast, "slow mode", 3]
                  other:
                    - 1
                    - two
                backups:
                  enabled: 'yes'
                """;
        Path path = file(original);
        YamlConfiguration before = YamlConfiguration.loadConfiguration(path.toFile());

        ConfigUpdater.Result result = update(DEFAULTS, path, ConfigUpdater.Options.plain());

        assertEquals(List.of("backups.interval-seconds"), result.addedPaths());
        assertEquals(original + "  interval-seconds: 60\n", read(path));
        YamlConfiguration after = YamlConfiguration.loadConfiguration(path.toFile());
        for (String key : before.getKeys(true)) {
            if (!before.isConfigurationSection(key)) {
                assertEquals(before.get(key), after.get(key), key);
            }
        }
    }

    @Test
    void fileWithOnlyCommentsKeepsThemAndGetsTheDefaultBelow() throws IOException {
        Path path = file("# just notes\n");

        update(DEFAULTS, path, ConfigUpdater.Options.plain());

        assertEquals("# just notes\n\n" + DEFAULTS, read(path));
    }

    @Test
    void keepsAPlainValueWhereTheDefaultHasASection() throws IOException {
        String original = """
                language: en
                features: disabled
                backups:
                  enabled: true
                  interval-seconds: 60
                """;
        Path path = file(original);

        ConfigUpdater.Result result = update(DEFAULTS, path, ConfigUpdater.Options.plain());

        assertFalse(result.changed());
        assertEquals(original, read(path));
    }

    @Test
    void secondRunChangesNothingAndBackupHoldsThePreviousFile() throws IOException {
        String original = "language: de\n";
        Path path = file(original);

        assertTrue(update(DEFAULTS, path, ConfigUpdater.Options.plain()).changed());
        String afterFirst = read(path);
        assertEquals(original, read(dir.resolve("config.yml.bak")));

        Files.delete(dir.resolve("config.yml.bak"));
        assertFalse(update(DEFAULTS, path, ConfigUpdater.Options.plain()).changed());
        assertEquals(afterFirst, read(path));
        assertFalse(Files.exists(dir.resolve("config.yml.bak")));
        assertFalse(Files.exists(dir.resolve("config.yml.tmp")));
    }

    @Test
    void entriesRemovedFromAUserManagedSectionStayRemoved() throws IOException {
        String defaults = """
                sizes:
                  1: size.small
                  2: size.medium
                  3: size.large
                other: true
                """;
        ConfigUpdater.Options options = new ConfigUpdater.Options(null, List.of(), Set.of("sizes"));

        Path path = file("""
                sizes:
                  2: rank.vip
                """);
        update(defaults, path, options);
        assertEquals("""
                sizes:
                  2: rank.vip
                other: true
                """, read(path));

        Path empty = file("other: false\n");
        update(defaults, empty, options);
        assertEquals("""
                sizes:
                  1: size.small
                  2: size.medium
                  3: size.large
                other: false
                """, read(empty));
    }

    @Test
    void brokenFileIsLeftUntouched() throws IOException {
        String broken = """
                language: en
                features:
                  example: [unclosed
                """;
        Path path = file(broken);

        ConfigUpdater.Result result = update(DEFAULTS, path, ConfigUpdater.Options.plain());

        assertFalse(result.changed());
        assertEquals(broken, read(path));
        assertTrue(log.stream().anyMatch(message -> message.contains("could not be read") && message.contains("line")), log.toString());
    }

    @Test
    void flowStyleSectionFallsBackAndStillKeepsValuesAndComments() throws IOException {
        Path path = file("""
                # keep this comment
                language: de
                features: {example: {mode: slow}, other: 5}
                backups:
                  enabled: false
                """);

        ConfigUpdater.Result result = update(DEFAULTS, path, ConfigUpdater.Options.plain());

        assertTrue(result.changed());
        YamlConfiguration after = YamlConfiguration.loadConfiguration(path.toFile());
        assertEquals("de", after.getString("language"));
        assertEquals("slow", after.getString("features.example.mode"));
        assertEquals(5, after.getInt("features.other"));
        assertTrue(after.getBoolean("features.example.enabled"));
        assertFalse(after.getBoolean("backups.enabled"));
        assertEquals(60, after.getInt("backups.interval-seconds"));
        assertTrue(read(path).contains("# keep this comment"));
        assertTrue(log.stream().anyMatch(message -> message.contains("standard layout")), log.toString());
    }

    @Test
    void migrationsRunOnceInOrderAndKeepTheServersValues() throws IOException {
        String defaults = """
                command:
                  cooldown: 2
                new-option: true
                config-version: 3
                """;
        List<ConfigMigration> steps = List.of(
                ConfigMigration.remove(3, "obsolete"),
                ConfigMigration.rename(2, "command.cooldown-seconds", "command.cooldown"));
        ConfigUpdater.Options options = new ConfigUpdater.Options("config-version", steps, Set.of());
        Path path = file("""
                command:
                  # my cooldown
                  cooldown-seconds: 7
                obsolete: 1
                config-version: 1
                """);

        ConfigUpdater.Result result = update(defaults, path, options);

        assertEquals(List.of(2, 3), result.appliedMigrations().stream().map(ConfigMigration::version).toList());
        assertEquals(List.of("new-option"), result.addedPaths());
        YamlConfiguration after = YamlConfiguration.loadConfiguration(path.toFile());
        assertEquals(7, after.getInt("command.cooldown"));
        assertFalse(after.contains("command.cooldown-seconds"));
        assertFalse(after.contains("obsolete"));
        assertEquals(3, after.getInt("config-version"));
        assertTrue(read(path).contains("# my cooldown"));

        String migrated = read(path);
        assertFalse(update(defaults, path, options).changed());
        assertEquals(migrated, read(path));
    }

    @Test
    void versionBumpWithoutStepsOnlyTouchesTheVersionLine() throws IOException {
        String defaults = """
                language: en
                config-version: 2
                """;
        Path path = file("""
                # mine
                language:   de
                config-version: 1   # do not touch
                """);

        update(defaults, path, new ConfigUpdater.Options("config-version", List.of(), Set.of()));

        assertEquals("""
                # mine
                language:   de
                config-version: 2   # do not touch
                """, read(path));
    }

    @Test
    void fileFromANewerPluginVersionIsLeftUntouched() throws IOException {
        String original = "language: de\nconfig-version: 9\n";
        Path path = file(original);

        ConfigUpdater.Result result = update("language: en\nother: 1\nconfig-version: 2\n", path,
                new ConfigUpdater.Options("config-version", List.of(), Set.of()));

        assertFalse(result.changed());
        assertEquals(original, read(path));
        assertTrue(log.stream().anyMatch(message -> message.contains("newer version")), log.toString());
    }

    @Test
    void keepsWindowsLineEndingsAndAMissingFinalNewline() throws IOException {
        Path path = file("language: de\r\nfeatures:\r\n  example:\r\n    mode: slow\r\n  other: 5\r\nbackups:\r\n  enabled: true\r\n  interval-seconds: 60");

        update(DEFAULTS, path, ConfigUpdater.Options.plain());

        assertEquals("language: de\r\nfeatures:\r\n  example:\r\n    # Turns the example on\r\n    enabled: true\r\n"
                + "    mode: slow\r\n  other: 5\r\nbackups:\r\n  enabled: true\r\n  interval-seconds: 60", read(path));
    }

    @Test
    void emptyFileReceivesTheWholeDefault() throws IOException {
        Path path = file("");

        update(DEFAULTS, path, ConfigUpdater.Options.plain());

        assertEquals(DEFAULTS, read(path));
    }

    @Test
    void realConfigFrom143KeepsCustomValuesAndGetsEveryNewOption() throws IOException {
        String bundled;
        try (InputStream stream = getClass().getResourceAsStream("/config.yml")) {
            bundled = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        // The config.yml shipped with 1.4.3, as a server owner customized it.
        Path path = file("""
                language: de

                backpack:
                  title: "&6Rucksack von {player}"

                  default-rows: 2

                  size-permissions:
                    2: rank.member
                    6: rank.vip

                  blocked-items: ["SHULKER_BOX", "TNT"]

                  item:
                    enabled: true
                    material: CHEST
                    name: "&6&lBackpack"
                    lore: ["&7Rechtsklick!"]
                    custom-model-data: 1001
                    give-on-first-join: true

                  sounds:
                    open: BLOCK_CHEST_OPEN
                    close: BLOCK_CHEST_CLOSE
                    volume: 0.5
                    pitch: 1.0

                command:
                  cooldown-seconds: 5

                worlds:
                  mode: BLACKLIST
                  list: [pvp]

                data:
                  autosave-interval-minutes: 10
                  save-on-close: true
                  save-on-quit: true

                update-checker:
                  enabled: false
                  check-interval-minutes: 60
                """);
        YamlConfiguration before = YamlConfiguration.loadConfiguration(path.toFile());

        ConfigUpdater.Result result = update(bundled, path, new ConfigUpdater.Options(
                "config-version", ConfigMigrations.CONFIG, Set.of("backpack.size-permissions", "backpack.page-permissions")));

        assertEquals(List.of("backpack.default-pages", "backpack.page-permissions", "backpack.page-title",
                "backpack.page-button-material", "backpack.sounds.page", "backups", "config-version"), result.addedPaths());
        YamlConfiguration after = YamlConfiguration.loadConfiguration(path.toFile());
        for (String key : before.getKeys(true)) {
            if (!before.isConfigurationSection(key)) {
                assertEquals(before.get(key), after.get(key), key);
            }
        }
        assertEquals(List.of("2", "6"), List.copyOf(after.getConfigurationSection("backpack.size-permissions").getKeys(false)));
        assertTrue(after.getBoolean("backups.enabled"));
        assertEquals(60, after.getInt("backups.interval-seconds"));
        assertEquals(1, after.getInt("config-version"));
        String text = read(path);
        assertTrue(text.contains("# Regular backups of every online player's backpack"), text);
        assertEquals(9, after.getConfigurationSection("backpack.page-permissions").getKeys(false).size());
        assertTrue(text.contains("    6: rank.vip\n\n  # Pages (1-10) for players without a page permission.\n  default-pages: 1\n"), text);
        assertTrue(text.contains("    close: BLOCK_CHEST_CLOSE\n    page: ITEM_BOOK_PAGE_TURN\n    volume: 0.5\n"), text);
        assertTrue(text.contains("  title: \"&6Rucksack von {player}\"\n"), text);
        assertTrue(text.contains("  blocked-items: [\"SHULKER_BOX\", \"TNT\"]\n"), text);
    }
}
