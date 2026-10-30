package dev.stonebackpack.plugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLifecycleTest extends PluginTestBase {

    private File dataFile(String path) {
        return new File(plugin.getDataFolder(), path);
    }

    @Test
    void reloadUpdatesAnOldConfigBeforeItsValuesAreRead() throws IOException {
        File file = dataFile("config.yml");
        Files.writeString(file.toPath(), """
                # Mein Server
                language: en
                command:
                  cooldown-seconds: 7
                update-checker:
                  enabled: false
                """, StandardCharsets.UTF_8);

        plugin.reload();

        assertEquals(7, plugin.getConfigManager().getCommandCooldownSeconds());
        assertTrue(plugin.getConfigManager().isBackupsEnabled());
        assertEquals(60, plugin.getConfigManager().getBackupIntervalSeconds());
        assertEquals(3, plugin.getConfigManager().getDefaultRows());
        String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(text.startsWith("# Mein Server\nlanguage: en\n"), text);
        assertTrue(text.contains("  cooldown-seconds: 7\n"), text);
        assertTrue(text.contains("config-version: 1"), text);
        assertTrue(dataFile("config.yml.bak").exists());
    }

    @Test
    void reloadAddsNewMessagesAndKeepsEditedOnes() throws IOException {
        File file = dataFile("languages/en/messages.yml");
        Files.writeString(file.toPath(), """
                prefix: "&7[Mein Rucksack] "
                general:
                  no-permission: "&cNein!"
                """, StandardCharsets.UTF_8);

        plugin.reload();

        YamlConfiguration after = YamlConfiguration.loadConfiguration(file);
        assertEquals("&7[Mein Rucksack] ", after.getString("prefix"));
        assertEquals("&cNein!", after.getString("general.no-permission"));
        assertTrue(after.contains("backups.gui.copy-name"));
        assertTrue(after.contains("general.load-failed"));
    }

    @Test
    void ownTranslationGetsMissingTextsInEnglish() throws IOException {
        File file = dataFile("languages/fr/messages.yml");
        file.getParentFile().mkdirs();
        Files.writeString(file.toPath(), """
                general:
                  no-permission: "&cNon!"
                """, StandardCharsets.UTF_8);

        setConfig("language", "fr");

        assertEquals("fr", plugin.getMessageManager().getActiveLanguage());
        YamlConfiguration french = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration english = YamlConfiguration.loadConfiguration(dataFile("languages/en/messages.yml"));
        assertEquals("&cNon!", french.getString("general.no-permission"));
        assertEquals(english.getString("general.player-only"), french.getString("general.player-only"));
    }
}
