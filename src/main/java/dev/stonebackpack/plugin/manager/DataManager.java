package dev.stonebackpack.plugin.manager;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class DataManager {
    public record BackpackData(int rows, ItemStack[] contents) {
    }

    private final Plugin plugin;
    private final File playerDataFolder;

    private final ConcurrentHashMap<UUID, Object> saveLocks = new ConcurrentHashMap<>();

    public DataManager(Plugin plugin) {
        this.plugin = plugin;
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
    }

    public void init() {
        if (!playerDataFolder.exists() && playerDataFolder.mkdirs()) {
            plugin.getLogger().info("Created playerdata folder.");
        }
    }

    public boolean hasData(UUID playerId) {
        return fileFor(playerId).exists();
    }

    private File fileFor(UUID playerId) {
        return new File(playerDataFolder, playerId + ".yml");
    }

    // Blocking disk read (YamlConfiguration.loadConfiguration parses the whole
    // file synchronously). Kept as the low-level primitive; callers on a hot
    // path (opening a backpack) must use loadAsync below instead of calling
    // this directly from the main thread, since at 250+ players a burst of
    // simultaneous opens (e.g. right after restart) would otherwise queue up
    // disk reads on the main thread and cause visible tick stalls.
    public BackpackData load(UUID playerId) {
        File file = fileFor(playerId);
        if (!file.exists()) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int rows = Math.max(1, Math.min(6, yaml.getInt("rows", 3)));
        List<?> rawItems = yaml.getList("items");
        ItemStack[] contents = new ItemStack[rows * 9];
        if (rawItems != null) {
            for (int i = 0; i < contents.length && i < rawItems.size(); i++) {
                Object element = rawItems.get(i);
                if (element instanceof ItemStack itemStack) {
                    contents[i] = itemStack;
                }
            }
        }
        return new BackpackData(rows, contents);
    }

    // Runs the disk read on Paper's shared async worker pool (also the
    // correct primitive on Folia, unlike Bukkit.getScheduler().runTaskAsynchronously)
    // and hands the result back on whatever thread the callback specifies -
    // the caller (BackpackManager) is responsible for hopping back to the
    // player's own scheduler before touching any Bukkit API with the result.
    public void loadAsync(UUID playerId, Consumer<BackpackData> callback) {
        Bukkit.getAsyncScheduler().runNow(plugin, task -> callback.accept(load(playerId)));
    }

    public void save(UUID playerId, int rows, ItemStack[] contents) {
        Object lock = saveLocks.computeIfAbsent(playerId, id -> new Object());
        synchronized (lock) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("rows", rows);
            yaml.set("items", Arrays.asList(contents));
            try {
                yaml.save(fileFor(playerId));
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not save backpack for " + playerId + ": " + exception.getMessage());
            }
        }
    }
}


