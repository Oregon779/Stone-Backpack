package dev.stonebackpack.plugin.manager;

import dev.stonebackpack.plugin.StoneBackpack;
import dev.stonebackpack.plugin.model.BackpackHolder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * All state here is only touched on the server thread: disk reads finish on
 * the data IO thread and hop back through the global region scheduler before
 * any inventory or player API is used.
 */
public class BackpackManager {
    // A full autosave or backup run is spread over this many ticks, so
    // snapshotting hundreds of backpacks never lands in a single tick.
    private static final int SPREAD_TICKS = 20;

    private final StoneBackpack plugin;

    // Cached while the owner is online (or someone is viewing it), so repeat
    // opens in a session never touch the disk.
    private final Map<UUID, Inventory> openInventories = new HashMap<>();

    // Coalesces opens of the same not-yet-loaded backpack into one disk read,
    // so two viewers can never end up with two independent inventories whose
    // saves overwrite each other.
    private final Map<UUID, List<Player>> pendingLoads = new HashMap<>();

    // Caches the legacy/hex-to-MiniMessage conversion of the title template.
    private String titleTemplateRaw;
    private String titleTemplateConverted;

    public BackpackManager(StoneBackpack plugin) {
        this.plugin = plugin;
    }

    public int getAllowedRows(Player player) {
        for (Map.Entry<String, Integer> entry : plugin.getConfigManager().getSizePermissions().entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                return entry.getValue();
            }
        }
        return plugin.getConfigManager().getDefaultRows();
    }

    public void open(Player viewer, OfflinePlayer target) {
        UUID ownerId = target.getUniqueId();
        String ownerName = target.getName() != null ? target.getName() : "Player";

        Inventory cached = openInventories.get(ownerId);
        if (cached != null) {
            show(viewer, target, ownerId, ownerName, cached);
            return;
        }

        List<Player> waiters = pendingLoads.get(ownerId);
        if (waiters != null) {
            waiters.add(viewer);
            return;
        }
        waiters = new ArrayList<>();
        waiters.add(viewer);
        pendingLoads.put(ownerId, waiters);

        plugin.getDataManager().loadAsync(ownerId,
                stored -> Bukkit.getGlobalRegionScheduler().execute(plugin, () -> finishLoad(target, ownerId, ownerName, stored)),
                error -> Bukkit.getGlobalRegionScheduler().execute(plugin, () -> failLoad(ownerId)));
    }

    public void openInspect(Player viewer, OfflinePlayer target) {
        open(viewer, target);
    }

    private void finishLoad(OfflinePlayer target, UUID ownerId, String ownerName, DataManager.BackpackData stored) {
        List<Player> waiters = pendingLoads.remove(ownerId);
        List<Player> viewers = waiters == null ? List.of() : waiters.stream().filter(Player::isOnline).toList();
        if (viewers.isEmpty()) {
            // Everyone who asked for it left while it loaded; caching it now
            // would keep it in memory with nobody to ever unload it.
            return;
        }

        int rows;
        if (target instanceof Player onlineTarget && onlineTarget.isOnline()) {
            int allowed = getAllowedRows(onlineTarget);
            rows = stored != null ? Math.max(stored.rows(), allowed) : allowed;
        } else {
            rows = stored != null ? stored.rows() : plugin.getConfigManager().getDefaultRows();
        }
        Inventory inventory = createInventory(ownerId, ownerName, rows);
        if (stored != null) {
            inventory.setContents(fit(stored.contents(), inventory.getSize()));
        }
        openInventories.put(ownerId, inventory);
        for (Player viewer : viewers) {
            show(viewer, target, ownerId, ownerName, openInventories.get(ownerId));
        }
    }

    private void failLoad(UUID ownerId) {
        List<Player> waiters = pendingLoads.remove(ownerId);
        if (waiters == null) {
            return;
        }
        for (Player viewer : waiters) {
            if (viewer.isOnline()) {
                plugin.getMessageManager().send(viewer, "general.load-failed", null);
            }
        }
    }

    private void show(Player viewer, OfflinePlayer target, UUID ownerId, String ownerName, Inventory inventory) {
        if (target instanceof Player onlineTarget && onlineTarget.isOnline()) {
            int allowedRows = getAllowedRows(onlineTarget);
            if (allowedRows * 9 > inventory.getSize()) {
                inventory = growInventory(inventory, ownerId, ownerName, allowedRows);
                openInventories.put(ownerId, inventory);
            }
        }
        viewer.openInventory(inventory);
        viewer.playSound(viewer.getLocation(), plugin.getConfigManager().getOpenSound(),
                plugin.getConfigManager().getSoundVolume(), plugin.getConfigManager().getSoundPitch());
    }

    private Inventory createInventory(UUID ownerId, String ownerName, int rows) {
        BackpackHolder holder = new BackpackHolder(ownerId);
        Component title = plugin.getMessageManager().parseConverted(resolveTitle(ownerName));
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inventory);
        return inventory;
    }

    private String resolveTitle(String ownerName) {
        String raw = plugin.getConfigManager().getBackpackTitleTemplate();
        if (titleTemplateConverted == null || !raw.equals(titleTemplateRaw)) {
            titleTemplateConverted = plugin.getMessageManager().toMiniMessageSyntax(raw);
            titleTemplateRaw = raw;
        }
        return titleTemplateConverted.replace("{player}", ownerName);
    }

    private Inventory growInventory(Inventory old, UUID ownerId, String ownerName, int newRows) {
        Inventory grown = createInventory(ownerId, ownerName, newRows);
        grown.setContents(fit(old.getContents(), grown.getSize()));
        for (HumanEntity viewer : Set.copyOf(old.getViewers())) {
            viewer.closeInventory();
        }
        return grown;
    }

    private ItemStack[] fit(ItemStack[] contents, int size) {
        ItemStack[] result = new ItemStack[size];
        System.arraycopy(contents, 0, result, 0, Math.min(contents.length, size));
        return result;
    }

    public void save(UUID ownerId) {
        Inventory inventory = openInventories.get(ownerId);
        if (inventory != null) {
            plugin.getDataManager().saveAsync(ownerId, deepCopy(inventory));
        }
    }

    public void handleClose(UUID ownerId, Player closer) {
        if (!plugin.getConfigManager().isSaveOnClose()) {
            return;
        }
        Inventory inventory = openInventories.get(ownerId);
        if (inventory == null) {
            return;
        }
        if (Bukkit.getPlayer(ownerId) == null && onlyViewedBy(inventory, closer)) {
            unload(ownerId, inventory);
        } else {
            save(ownerId);
        }
    }

    public void handleQuit(Player owner) {
        if (!plugin.getConfigManager().isSaveOnQuit()) {
            return;
        }
        UUID ownerId = owner.getUniqueId();
        Inventory inventory = openInventories.get(ownerId);
        if (inventory == null) {
            return;
        }
        if (onlyViewedBy(inventory, owner)) {
            unload(ownerId, inventory);
        } else {
            save(ownerId);
        }
    }

    // During InventoryCloseEvent the closing player is still listed as a viewer.
    private static boolean onlyViewedBy(Inventory inventory, Player player) {
        for (HumanEntity viewer : inventory.getViewers()) {
            if (!viewer.getUniqueId().equals(player.getUniqueId())) {
                return false;
            }
        }
        return true;
    }

    private void unload(UUID ownerId, Inventory inventory) {
        openInventories.remove(ownerId);
        plugin.getDataManager().saveAsync(ownerId, deepCopy(inventory));
        plugin.getBackupManager().rememberUnloaded(ownerId, deepCopy(inventory));
    }

    /**
     * Autosave. Also unloads backpacks whose owner is offline and that nobody
     * is viewing any more, e.g. left behind while save-on-close or
     * save-on-quit is disabled.
     */
    public void saveAll() {
        forEachLoadedSpread(ownerId -> {
            Inventory inventory = openInventories.get(ownerId);
            if (inventory == null) {
                return;
            }
            if (Bukkit.getPlayer(ownerId) == null && inventory.getViewers().isEmpty()) {
                unload(ownerId, inventory);
            } else {
                plugin.getDataManager().saveAsync(ownerId, deepCopy(inventory));
            }
        });
    }

    /**
     * Hands {@code sink} a deep copy of every loaded backpack, spread over
     * the next ticks. Returns the owners that will be handled.
     */
    public Set<UUID> snapshotLoaded(BiConsumer<UUID, DataManager.BackpackData> sink) {
        return forEachLoadedSpread(ownerId -> {
            Inventory inventory = openInventories.get(ownerId);
            if (inventory != null) {
                sink.accept(ownerId, deepCopy(inventory));
            }
        });
    }

    private Set<UUID> forEachLoadedSpread(Consumer<UUID> action) {
        Set<UUID> owners = Set.copyOf(openInventories.keySet());
        int index = 0;
        for (UUID ownerId : owners) {
            long delay = 1 + (index++ % SPREAD_TICKS);
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> action.accept(ownerId), delay);
        }
        return owners;
    }

    // Only for shutdown, after every open view was closed: queues a final
    // save of everything still loaded, which DataManager.shutdown() waits for.
    public void saveAllNow() {
        for (Map.Entry<UUID, Inventory> entry : openInventories.entrySet()) {
            plugin.getDataManager().saveAsync(entry.getKey(), deepCopy(entry.getValue()));
        }
    }

    // getContents() returns live mirrors of the slot items, which must not be
    // serialized on another thread while players keep editing them.
    private static DataManager.BackpackData deepCopy(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return new DataManager.BackpackData(inventory.getSize() / 9, copy);
    }

    public boolean isBlocked(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        return plugin.getConfigManager().getBlockedItems().contains(type);
    }
}
