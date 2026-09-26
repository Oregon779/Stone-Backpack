package dev.stonebackpack.plugin.manager;

import dev.stonebackpack.plugin.StoneBackpack;
import dev.stonebackpack.plugin.model.BackpackHolder;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class BackpackManager {
    private final StoneBackpack plugin;
    private final Map<UUID, Inventory> openInventories = new ConcurrentHashMap<>();

    // Coalesces concurrent open() calls for the same, not-yet-loaded owner
    // into a single disk read. Without this, two players opening the same
    // uncached backpack in the same instant (e.g. an admin inspecting the
    // moment the owner also logs in) would each kick off their own async
    // load, build two independent Inventory objects, and whichever write
    // wins last would silently discard the other viewer's edits.
    private final Map<UUID, List<Consumer<Inventory>>> pendingLoads = new ConcurrentHashMap<>();

    // Caches the legacy/hex-to-MiniMessage conversion of the title template,
    // which is otherwise identical work redone on every single backpack
    // open. Recomputed only when the raw template in config.yml changes.
    private volatile String titleTemplateRaw;
    private volatile String titleTemplateConverted;

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

    /**
     * Opens {@code target}'s backpack for {@code viewer}. The first-ever
     * open for a given owner in a session reads their file off the main
     * thread (see DataManager.loadAsync) - at 250+ concurrent players a
     * burst of simultaneous /backpack uses (e.g. right after a restart)
     * would otherwise queue up disk reads on the main thread and cause a
     * visible tick stall. Every subsequent open for the same owner is a
     * pure in-memory cache hit and stays fully synchronous.
     */
    public void open(Player viewer, OfflinePlayer target) {
        UUID ownerId = target.getUniqueId();
        String ownerName = target.getName() != null ? target.getName() : "Player";

        Inventory cached = openInventories.get(ownerId);
        if (cached != null) {
            growIfNeededAndShow(viewer, target, ownerId, ownerName, cached);
            return;
        }

        Consumer<Inventory> onLoaded = inventory -> {
            if (!viewer.isOnline()) {
                return;
            }
            // Hop back to the viewer's own scheduler before touching any
            // Bukkit/player API - required on Folia (the viewer may live on
            // a different region thread than the one the async load
            // finished on), and effectively a same-tick no-op on Paper.
            viewer.getScheduler().run(plugin, task -> growIfNeededAndShow(viewer, target, ownerId, ownerName, inventory), null);
        };

        List<Consumer<Inventory>> waiters = new CopyOnWriteArrayList<>();
        List<Consumer<Inventory>> existing = pendingLoads.putIfAbsent(ownerId, waiters);
        if (existing != null) {
            existing.add(onLoaded);
            return;
        }
        waiters.add(onLoaded);

        plugin.getDataManager().loadAsync(ownerId, stored -> {
            int rows;
            if (target instanceof Player onlineTarget) {
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
            List<Consumer<Inventory>> callbacks = pendingLoads.remove(ownerId);
            if (callbacks != null) {
                for (Consumer<Inventory> callback : callbacks) {
                    callback.accept(inventory);
                }
            }
        });
    }

    public void openInspect(Player viewer, OfflinePlayer target) {
        open(viewer, target);
    }

    private void growIfNeededAndShow(Player viewer, OfflinePlayer target, UUID ownerId, String ownerName, Inventory inventory) {
        if (target instanceof Player onlineTarget) {
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
        String converted = titleTemplateConverted;
        if (converted == null || !raw.equals(titleTemplateRaw)) {
            converted = plugin.getMessageManager().toMiniMessageSyntax(raw);
            titleTemplateRaw = raw;
            titleTemplateConverted = converted;
        }
        return converted.replace("{player}", ownerName);
    }

    private Inventory growInventory(Inventory old, UUID ownerId, String ownerName, int newRows) {
        Inventory grown = createInventory(ownerId, ownerName, newRows);
        grown.setContents(fit(old.getContents(), grown.getSize()));
        for (org.bukkit.entity.HumanEntity viewer : Set.copyOf(old.getViewers())) {
            viewer.closeInventory();
        }
        return grown;
    }

    private ItemStack[] fit(ItemStack[] contents, int size) {
        ItemStack[] result = new ItemStack[size];
        System.arraycopy(contents, 0, result, 0, Math.min(contents.length, size));
        return result;
    }

    /**
     * Snapshots the inventory synchronously (required - reading live
     * Inventory contents off the owning thread is unsafe) but performs the
     * actual disk write on Paper's async worker pool, so the write latency
     * never shows up as main-thread time even under heavy concurrent saves.
     */
    public void save(UUID ownerId) {
        Inventory inventory = openInventories.get(ownerId);
        if (inventory == null) {
            return;
        }
        int rows = inventory.getSize() / 9;
        ItemStack[] snapshot = inventory.getContents().clone();
        Bukkit.getAsyncScheduler().runNow(plugin, task -> plugin.getDataManager().save(ownerId, rows, snapshot));
    }

    public void saveAndMaybeUnload(UUID ownerId) {
        save(ownerId);
        Inventory inventory = openInventories.get(ownerId);
        if (inventory != null && inventory.getViewers().isEmpty()) {
            openInventories.remove(ownerId);
            plugin.getBackupManager().rememberUnloaded(ownerId, deepCopy(inventory));
        }
    }

    /**
     * Same per-owner thread dispatch as saveAll(), but hands {@code sink} a
     * deep copy: getContents() returns live mirrors of the slot items, which
     * must not be read from the async pool while the owner keeps editing.
     * Returns the owners that were dispatched.
     */
    public Set<UUID> snapshotLoaded(BiConsumer<UUID, DataManager.BackpackData> sink) {
        Set<UUID> owners = Set.copyOf(openInventories.keySet());
        for (UUID ownerId : owners) {
            Player online = Bukkit.getPlayer(ownerId);
            if (online != null) {
                online.getScheduler().run(plugin, task -> copyAndDispatch(ownerId, sink), null);
            } else {
                copyAndDispatch(ownerId, sink);
            }
        }
        return owners;
    }

    private void copyAndDispatch(UUID ownerId, BiConsumer<UUID, DataManager.BackpackData> sink) {
        Inventory inventory = openInventories.get(ownerId);
        if (inventory == null) {
            return;
        }
        DataManager.BackpackData copy = deepCopy(inventory);
        Bukkit.getAsyncScheduler().runNow(plugin, task -> sink.accept(ownerId, copy));
    }

    private static DataManager.BackpackData deepCopy(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return new DataManager.BackpackData(inventory.getSize() / 9, copy);
    }

    /**
     * Used by the autosave timer. For each owner, the (cheap) snapshot read
     * is dispatched to that specific player's own scheduler when they're
     * online - the correct thread to touch their objects on Folia, and a
     * same-thread no-op on regular Paper - before the disk write itself is
     * handed to the async pool. With 250+ players each carrying an open
     * backpack, writing dozens of YAML files synchronously back-to-back on
     * a single thread every autosave tick would otherwise be a guaranteed,
     * periodic lag spike.
     */
    public void saveAll() {
        for (UUID ownerId : openInventories.keySet()) {
            org.bukkit.entity.Player online = Bukkit.getPlayer(ownerId);
            if (online != null) {
                online.getScheduler().run(plugin, task -> snapshotAndDispatch(ownerId), null);
            } else {
                snapshotAndDispatch(ownerId);
            }
        }
    }

    private void snapshotAndDispatch(UUID ownerId) {
        Inventory inventory = openInventories.get(ownerId);
        if (inventory == null) {
            return;
        }
        int rows = inventory.getSize() / 9;
        ItemStack[] snapshot = inventory.getContents().clone();
        Bukkit.getAsyncScheduler().runNow(plugin, task -> plugin.getDataManager().save(ownerId, rows, snapshot));
    }

    /**
     * Used only on plugin/server shutdown. Deliberately fully synchronous
     * and scheduler-free: once onDisable() has started, newly scheduled
     * async/region tasks are not guaranteed to still run, so dispatching
     * the final save the same way as the runtime autosave risks silently
     * losing whatever was in an open backpack. A brief blocking write here
     * is the safe trade-off, since the server is going down anyway.
     */
    public void saveAllBlocking() {
        for (UUID ownerId : openInventories.keySet()) {
            Inventory inventory = openInventories.get(ownerId);
            if (inventory == null) {
                continue;
            }
            int rows = inventory.getSize() / 9;
            ItemStack[] snapshot = inventory.getContents().clone();
            plugin.getDataManager().save(ownerId, rows, snapshot);
        }
    }

    public boolean isBlocked(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        return plugin.getConfigManager().getBlockedItems().contains(type);
    }
}
