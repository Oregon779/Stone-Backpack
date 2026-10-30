package dev.stonebackpack.plugin.manager;

import dev.stonebackpack.plugin.model.BackpackLayout;
import dev.stonebackpack.plugin.util.AtomicFiles;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

public class DataManager {
    /** {@code contents} holds all pages one after another, {@code rows * 9} slots each. */
    public record BackpackData(int rows, int pages, ItemStack[] contents) {
        public ItemStack[] page(int index) {
            int size = rows * 9;
            return Arrays.copyOfRange(contents, index * size, (index + 1) * size);
        }
    }

    public record Backup(long timestamp, BackpackData data) {
    }

    private final Plugin plugin;
    private final File playerDataFolder;
    private final File backupFolder;

    // Dedicated threads instead of Paper's async scheduler, whose pool starts a
    // new thread for every task that finds no idle one - an autosave over 300
    // backpacks could otherwise spawn hundreds of threads at once. Being a
    // single FIFO thread also guarantees that a load queued after a save of
    // the same player reads what that save wrote.
    private final ExecutorService dataIo = Executors.newSingleThreadExecutor(daemonThreads("StoneBackpack Data IO"));
    // Kept apart so a full backup run never delays a player opening their backpack.
    private final ExecutorService backupIo = Executors.newSingleThreadExecutor(daemonThreads("StoneBackpack Backup IO"));

    public DataManager(Plugin plugin) {
        this.plugin = plugin;
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
        this.backupFolder = new File(plugin.getDataFolder(), "backups");
    }

    public void init() {
        if (!playerDataFolder.exists() && playerDataFolder.mkdirs()) {
            plugin.getLogger().info("Created playerdata folder.");
        }
        if (!backupFolder.exists() && backupFolder.mkdirs()) {
            plugin.getLogger().info("Created backups folder.");
        }
    }

    public boolean hasData(UUID playerId) {
        return fileFor(playerId).exists();
    }

    private File fileFor(UUID playerId) {
        return new File(playerDataFolder, playerId + ".yml");
    }

    private File backupFileFor(UUID playerId) {
        return new File(backupFolder, playerId + ".yml");
    }

    /**
     * Reads the backpack on the data IO thread. {@code onLoaded} receives null
     * when the player has no backpack yet (or its file was corrupt and has been
     * moved aside). {@code onFailure} is called instead when the file exists
     * but can't be read, so the caller never replaces unreadable data with an
     * empty backpack.
     */
    public void loadAsync(UUID playerId, Consumer<BackpackData> onLoaded, Consumer<Exception> onFailure) {
        submit(dataIo, () -> {
            BackpackData data;
            try {
                data = readPlayerData(playerId);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not load the backpack of " + playerId + ".", exception);
                onFailure.accept(exception);
                return;
            }
            onLoaded.accept(data);
        });
    }

    private BackpackData readPlayerData(UUID playerId) throws IOException {
        File file = fileFor(playerId);
        if (!file.exists()) {
            return null;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (InvalidConfigurationException exception) {
            File aside = new File(file.getParentFile(), file.getName() + ".corrupt-" + System.currentTimeMillis());
            if (!file.renameTo(aside)) {
                throw new IOException("Backpack file is corrupt and could not be moved aside: " + file, exception);
            }
            plugin.getLogger().severe("The backpack file of " + playerId + " was corrupt and has been moved to "
                    + aside.getName() + "; the player starts with an empty backpack. Its latest backup can be restored"
                    + " with /stonebackpack backups. Cause: " + exception.getMessage());
            return null;
        }
        return readContents(yaml);
    }

    // Files from before pages existed have no page count and hold one page.
    // The page count never cuts off stored items, even when edited down.
    private BackpackData readContents(YamlConfiguration yaml) {
        int rows = Math.max(1, Math.min(6, yaml.getInt("rows", 3)));
        int size = rows * 9;
        List<?> rawItems = yaml.getList("items");
        int stored = rawItems == null ? 0 : rawItems.size();
        int declaredPages = Math.max(1, Math.min(BackpackLayout.MAX_PAGES, yaml.getInt("pages", 1)));
        int pages = Math.max(declaredPages, (stored + size - 1) / size);
        ItemStack[] contents = new ItemStack[pages * size];
        for (int i = 0; i < stored; i++) {
            if (rawItems.get(i) instanceof ItemStack itemStack) {
                contents[i] = itemStack;
            }
        }
        return new BackpackData(rows, pages, contents);
    }

    /**
     * {@code data} must be a copy the caller no longer touches: it is
     * serialized later on the data IO thread.
     */
    public void saveAsync(UUID playerId, BackpackData data) {
        submit(dataIo, () -> writeLogged(fileFor(playerId), data, null, true, "the backpack of " + playerId));
    }

    public void saveBackupAsync(UUID playerId, BackpackData data) {
        submit(backupIo, () -> writeLogged(backupFileFor(playerId), data, System.currentTimeMillis(), false,
                "the backpack backup of " + playerId));
    }

    /**
     * Backs up players whose backpack isn't loaded by copying their saved file
     * as text with a fresh timestamp, instead of deserializing and
     * re-serializing every item of every player on each run.
     */
    public void backupSavedDataAsync(Collection<UUID> playerIds) {
        submit(backupIo, () -> {
            for (UUID playerId : playerIds) {
                try {
                    String saved = Files.readString(fileFor(playerId).toPath(), StandardCharsets.UTF_8);
                    AtomicFiles.write(backupFileFor(playerId).toPath(),
                            "timestamp: " + System.currentTimeMillis() + "\n" + saved, false);
                } catch (NoSuchFileException ignored) {
                    // Never used their backpack, nothing to back up.
                } catch (Exception exception) {
                    plugin.getLogger().log(Level.WARNING, "Could not back up the backpack of " + playerId + ".", exception);
                }
            }
        });
    }

    public void loadBackupAsync(UUID playerId, Consumer<Backup> callback) {
        submit(backupIo, () -> callback.accept(readBackup(playerId)));
    }

    private Backup readBackup(UUID playerId) {
        File file = backupFileFor(playerId);
        if (!file.exists()) {
            return null;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not read the backpack backup of " + playerId + ".", exception);
            return null;
        }
        return new Backup(yaml.getLong("timestamp", file.lastModified()), readContents(yaml));
    }

    private void writeLogged(File target, BackpackData data, Long timestamp, boolean sync, String description) {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            if (timestamp != null) {
                yaml.set("timestamp", timestamp);
            }
            // Page 1 comes first in "items", where versions before pages read it.
            yaml.set("rows", data.rows());
            yaml.set("pages", data.pages());
            yaml.set("items", Arrays.asList(data.contents()));
            AtomicFiles.write(target.toPath(), yaml.saveToString(), sync);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + description + ".", exception);
        }
    }

    // Anything submitted after shutdown() (a straggling event during disable)
    // still gets written, just on the calling thread.
    private static void submit(ExecutorService executor, Runnable task) {
        if (executor.isShutdown()) {
            task.run();
        } else {
            executor.execute(task);
        }
    }

    public void awaitIdle() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        submit(dataIo, latch::countDown);
        submit(backupIo, latch::countDown);
        latch.await(30, TimeUnit.SECONDS);
    }

    public void shutdown() {
        dataIo.shutdown();
        backupIo.shutdown();
        try {
            if (!dataIo.awaitTermination(30, TimeUnit.SECONDS)) {
                plugin.getLogger().severe("Timed out waiting for backpack saves to finish; recent changes may be lost.");
            }
            backupIo.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory daemonThreads(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
