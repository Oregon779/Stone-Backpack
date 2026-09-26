package dev.stonebackpack.plugin.manager;

import dev.stonebackpack.plugin.StoneBackpack;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BackupManager {
    private static final int SHULKER_SLOTS = 27;

    private final StoneBackpack plugin;

    // Backpacks are unloaded as soon as their last viewer closes them, so one
    // opened and closed between two backup runs would never be seen by
    // snapshotLoaded(). Its final state waits here for the next run instead.
    private final Map<UUID, DataManager.BackpackData> unloadedSinceLastRun = new ConcurrentHashMap<>();
    private ScheduledTask task;

    public BackupManager(StoneBackpack plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfigManager().isBackupsEnabled()) {
            unloadedSinceLastRun.clear();
            return;
        }
        long periodTicks = plugin.getConfigManager().getBackupIntervalSeconds() * 20L;
        task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduled -> backupAll(), periodTicks, periodTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void rememberUnloaded(UUID ownerId, DataManager.BackpackData data) {
        if (plugin.getConfigManager().isBackupsEnabled()) {
            unloadedSinceLastRun.put(ownerId, data);
        }
    }

    private void backupAll() {
        DataManager dataManager = plugin.getDataManager();
        Set<UUID> loaded = plugin.getBackpackManager().snapshotLoaded(dataManager::saveBackup);
        for (UUID ownerId : unloadedSinceLastRun.keySet()) {
            DataManager.BackpackData data = unloadedSinceLastRun.remove(ownerId);
            // Reopened since it was unloaded: the snapshot above is newer.
            if (data != null && !loaded.contains(ownerId)) {
                Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> dataManager.saveBackup(ownerId, data));
            }
        }
    }

    /**
     * Packs the backup's items into shulker boxes of 27. Shulker boxes found in
     * the backup (only possible with the blocklist bypass) are returned loose,
     * since nested shulker boxes get flagged as illegal items by many servers.
     */
    public List<ItemStack> createRestoreItems(ItemStack[] contents, String ownerName) {
        List<ItemStack> packable = new ArrayList<>();
        List<ItemStack> loose = new ArrayList<>();
        for (ItemStack item : contents) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            if (Tag.SHULKER_BOXES.isTagged(item.getType())) {
                loose.add(item.clone());
            } else {
                packable.add(item.clone());
            }
        }

        List<ItemStack> result = new ArrayList<>();
        int boxes = (packable.size() + SHULKER_SLOTS - 1) / SHULKER_SLOTS;
        for (int i = 0; i < boxes; i++) {
            List<ItemStack> chunk = packable.subList(i * SHULKER_SLOTS, Math.min(packable.size(), (i + 1) * SHULKER_SLOTS));
            ItemStack box = ItemStack.of(Material.SHULKER_BOX);
            Map<String, String> placeholders = Map.of(
                    "player", ownerName,
                    "index", String.valueOf(i + 1),
                    "total", String.valueOf(boxes));
            box.editMeta(meta -> meta.displayName(plugin.getMessageManager()
                    .component("backups.shulker-name", placeholders)
                    .decoration(TextDecoration.ITALIC, false)));
            box.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(chunk));
            result.add(box);
        }
        result.addAll(loose);
        return result;
    }
}
