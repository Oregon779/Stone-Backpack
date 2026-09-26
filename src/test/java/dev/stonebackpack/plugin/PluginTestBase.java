package dev.stonebackpack.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class PluginTestBase {
    protected TestServer server;
    protected StoneBackpack plugin;

    @BeforeEach
    void startServer() {
        server = MockBukkit.mock(new TestServer());
        plugin = MockBukkit.load(StoneBackpack.class);
        editConfig(yaml -> {
            yaml.set("update-checker.enabled", false);
            yaml.set("command.cooldown-seconds", 0);
        });
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    protected void setConfig(String path, Object value) {
        editConfig(yaml -> yaml.set(path, value));
    }

    protected void editConfig(java.util.function.Consumer<YamlConfiguration> editor) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        editor.accept(yaml);
        try {
            yaml.save(file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        plugin.reload();
    }

    /** Lets queued disk IO finish and runs the ticks that apply its results. */
    protected void settle() {
        for (int round = 0; round < 3; round++) {
            try {
                plugin.getDataManager().awaitIdle();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            server.getScheduler().performTicks(25);
        }
    }

    protected Inventory openOwnBackpack(PlayerMock player) {
        player.performCommand("backpack");
        settle();
        return player.getOpenInventory().getTopInventory();
    }

    protected File playerDataFile(UUID playerId) {
        return new File(plugin.getDataFolder(), "playerdata/" + playerId + ".yml");
    }

    protected File backupFile(UUID playerId) {
        return new File(plugin.getDataFolder(), "backups/" + playerId + ".yml");
    }

    protected static ItemStack savedItem(File file, int slot) {
        List<?> items = YamlConfiguration.loadConfiguration(file).getList("items");
        return items == null || slot >= items.size() ? null : (ItemStack) items.get(slot);
    }

    protected static List<String> drainMessages(PlayerMock player) {
        List<String> messages = new ArrayList<>();
        Component message;
        while ((message = player.nextComponentMessage()) != null) {
            messages.add(PlainTextComponentSerializer.plainText().serialize(message));
        }
        return messages;
    }
}
