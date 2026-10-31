package dev.stonebackpack.plugin.manager;

import dev.stonebackpack.plugin.StoneBackpack;
import dev.stonebackpack.plugin.model.BackpackHolder;
import dev.stonebackpack.plugin.model.BackpackLayout;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

    // The page limit of a backpack loaded while its owner was offline, whose
    // permissions are unknown: every stored page stays.
    private static final int NO_LIMIT = Integer.MAX_VALUE;

    private final StoneBackpack plugin;

    // One inventory per page, all with the same number of rows. Pages from
    // pageLimit on only exist because the items don't fit on fewer.
    private record Backpack(int rows, int pageLimit, List<Inventory> pages) {
        boolean hasViewers() {
            for (Inventory page : pages) {
                if (!page.getViewers().isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        boolean isShowing(HumanEntity viewer) {
            return pages.contains(viewer.getOpenInventory().getTopInventory());
        }
    }

    // Cached while the owner is online (or someone is viewing it), so repeat
    // opens in a session never touch the disk.
    private final Map<UUID, Backpack> loaded = new HashMap<>();

    // Coalesces opens of the same not-yet-loaded backpack into one disk read,
    // so two viewers can never end up with two independent inventories whose
    // saves overwrite each other.
    private final Map<UUID, List<Player>> pendingLoads = new HashMap<>();

    // Viewers switching to another page: closing the old page is no real close.
    private final Set<UUID> turningPage = new HashSet<>();

    private final Template titleTemplate = new Template();
    private final Template pageTitleTemplate = new Template();

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

    public int getAllowedPages(Player player) {
        for (Map.Entry<String, Integer> entry : plugin.getConfigManager().getPagePermissions().entrySet()) {
            if (player.hasPermission(entry.getKey())) {
                return entry.getValue();
            }
        }
        return plugin.getConfigManager().getDefaultPages();
    }

    public void open(Player viewer, OfflinePlayer target) {
        UUID ownerId = target.getUniqueId();
        String ownerName = target.getName() != null ? target.getName() : "Player";

        Backpack cached = loaded.get(ownerId);
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
        int limit;
        int pages;
        if (target instanceof Player onlineTarget && onlineTarget.isOnline()) {
            int allowed = getAllowedRows(onlineTarget);
            rows = stored != null ? Math.max(stored.rows(), allowed) : allowed;
            limit = getAllowedPages(onlineTarget);
            pages = limit;
        } else {
            rows = stored != null ? stored.rows() : plugin.getConfigManager().getDefaultRows();
            limit = NO_LIMIT;
            pages = stored != null ? stored.pages() : plugin.getConfigManager().getDefaultPages();
        }
        List<ItemStack[]> storedPages = new ArrayList<>();
        if (stored != null) {
            for (int page = 0; page < stored.pages(); page++) {
                storedPages.add(stored.page(page));
            }
        }
        loaded.put(ownerId, build(ownerId, ownerName, rows, pages, limit, storedPages));
        for (Player viewer : viewers) {
            show(viewer, target, ownerId, ownerName, loaded.get(ownerId));
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

    private void show(Player viewer, OfflinePlayer target, UUID ownerId, String ownerName, Backpack backpack) {
        // The page count follows the owner's permission both ways; rows only grow.
        if (target instanceof Player onlineTarget && onlineTarget.isOnline()) {
            int allowedRows = getAllowedRows(onlineTarget);
            int allowedPages = getAllowedPages(onlineTarget);
            if (allowedRows > backpack.rows() || allowedPages != backpack.pageLimit() || canDropPages(backpack)) {
                backpack = rebuild(backpack, ownerId, ownerName, Math.max(allowedRows, backpack.rows()), allowedPages);
                loaded.put(ownerId, backpack);
            }
        }
        viewer.openInventory(backpack.pages().getFirst());
        playSound(viewer, plugin.getConfigManager().getOpenSound());
    }

    private Backpack build(UUID ownerId, String ownerName, int rows, int pageCount, int limit, List<ItemStack[]> storedPages) {
        List<ItemStack[]> laidOut = BackpackLayout.arrange(storedPages, rows, pageCount);
        int count = laidOut.size();
        List<Inventory> pages = new ArrayList<>(count);
        for (int page = 0; page < count; page++) {
            BackpackHolder holder = new BackpackHolder(ownerId, page, count, page >= limit);
            Inventory inventory = Bukkit.createInventory(holder, rows * 9, title(ownerName, page, count));
            holder.setInventory(inventory);
            inventory.setContents(laidOut.get(page));
            ItemManager items = plugin.getItemManager();
            if (BackpackLayout.hasPrevious(page)) {
                inventory.setItem(BackpackLayout.previousSlot(rows), items.createPageButton("backpack.previous-page", page, count));
            }
            if (BackpackLayout.hasNext(page, count)) {
                inventory.setItem(BackpackLayout.nextSlot(rows), items.createPageButton("backpack.next-page", page + 2, count));
            }
            pages.add(inventory);
        }
        return new Backpack(rows, limit, List.copyOf(pages));
    }

    // True when pages above the limit hold items that now fit on fewer pages.
    private boolean canDropPages(Backpack backpack) {
        if (backpack.pages().size() <= backpack.pageLimit()) {
            return false;
        }
        List<ItemStack[]> contents = new ArrayList<>();
        for (int page = 0; page < backpack.pages().size(); page++) {
            contents.add(withoutButtons(backpack, page, false));
        }
        int needed = BackpackLayout.pagesFor(BackpackLayout.countItems(contents), backpack.rows());
        return Math.max(backpack.pageLimit(), needed) < backpack.pages().size();
    }

    private Backpack rebuild(Backpack old, UUID ownerId, String ownerName, int rows, int limit) {
        for (Inventory page : old.pages()) {
            for (HumanEntity viewer : Set.copyOf(page.getViewers())) {
                viewer.closeInventory();
            }
        }
        List<ItemStack[]> contents = new ArrayList<>();
        for (int page = 0; page < old.pages().size(); page++) {
            contents.add(withoutButtons(old, page, false));
        }
        return build(ownerId, ownerName, rows, limit, limit, contents);
    }

    private Component title(String ownerName, int page, int pageCount) {
        String title = titleTemplate.converted(plugin.getConfigManager().getBackpackTitleTemplate());
        if (pageCount > 1) {
            title += pageTitleTemplate.converted(plugin.getConfigManager().getPageTitleTemplate());
        }
        return plugin.getMessageManager().parseConverted(title
                .replace("{player}", ownerName)
                .replace("{page}", String.valueOf(page + 1))
                .replace("{pages}", String.valueOf(pageCount)));
    }

    // Caches the legacy/hex-to-MiniMessage conversion of a configured template.
    private final class Template {
        private String raw;
        private String converted;

        String converted(String current) {
            if (converted == null || !current.equals(raw)) {
                converted = plugin.getMessageManager().toMiniMessageSyntax(current);
                raw = current;
            }
            return converted;
        }
    }

    public boolean isTurningPage(HumanEntity viewer) {
        return turningPage.contains(viewer.getUniqueId());
    }

    /**
     * Switches {@code viewer} from the page of {@code holder} to the next or
     * previous one. An item on their cursor comes along to the new page.
     */
    public void turnPage(Player viewer, BackpackHolder holder, boolean forward) {
        Backpack backpack = loaded.get(holder.getOwnerId());
        if (backpack == null || !backpack.pages().contains(holder.getInventory())
                || !holder.getInventory().equals(viewer.getOpenInventory().getTopInventory())) {
            return;
        }
        int target = holder.getPage() + (forward ? 1 : -1);
        if (target < 0 || target >= backpack.pages().size()) {
            return;
        }
        Inventory next = backpack.pages().get(target);

        // Kept off the cursor while switching, or closing the old page would
        // put it into the player's inventory or drop it.
        ItemStack cursor = viewer.getItemOnCursor();
        viewer.setItemOnCursor(null);
        turningPage.add(viewer.getUniqueId());
        try {
            viewer.openInventory(next);
        } finally {
            turningPage.remove(viewer.getUniqueId());
        }

        boolean stillViewing = backpack.isShowing(viewer);
        if (!cursor.isEmpty()) {
            if (stillViewing) {
                viewer.setItemOnCursor(cursor);
            } else {
                plugin.getItemManager().give(viewer, cursor);
            }
        }
        if (!stillViewing) {
            // Another plugin stopped the new page from opening, so this
            // became a plain close that the close event did not handle.
            handleClose(holder.getOwnerId(), viewer);
        } else if (next.equals(viewer.getOpenInventory().getTopInventory())) {
            playSound(viewer, plugin.getConfigManager().getPageSound());
        }
    }

    private void playSound(Player viewer, Sound sound) {
        viewer.playSound(viewer.getLocation(), sound,
                plugin.getConfigManager().getSoundVolume(), plugin.getConfigManager().getSoundPitch());
    }

    public void save(UUID ownerId) {
        Backpack backpack = loaded.get(ownerId);
        if (backpack != null) {
            plugin.getDataManager().saveAsync(ownerId, deepCopy(backpack));
        }
    }

    public void handleClose(UUID ownerId, Player closer) {
        if (!plugin.getConfigManager().isSaveOnClose()) {
            return;
        }
        Backpack backpack = loaded.get(ownerId);
        if (backpack == null) {
            return;
        }
        if (Bukkit.getPlayer(ownerId) == null && onlyViewedBy(backpack, closer)) {
            unload(ownerId, backpack);
        } else {
            save(ownerId);
        }
    }

    public void handleQuit(Player owner) {
        if (!plugin.getConfigManager().isSaveOnQuit()) {
            return;
        }
        UUID ownerId = owner.getUniqueId();
        Backpack backpack = loaded.get(ownerId);
        if (backpack == null) {
            return;
        }
        if (onlyViewedBy(backpack, owner)) {
            unload(ownerId, backpack);
        } else {
            save(ownerId);
        }
    }

    // During InventoryCloseEvent the closing player is still listed as a viewer.
    private static boolean onlyViewedBy(Backpack backpack, Player player) {
        for (Inventory page : backpack.pages()) {
            for (HumanEntity viewer : page.getViewers()) {
                if (!viewer.getUniqueId().equals(player.getUniqueId())) {
                    return false;
                }
            }
        }
        return true;
    }

    private void unload(UUID ownerId, Backpack backpack) {
        loaded.remove(ownerId);
        plugin.getDataManager().saveAsync(ownerId, deepCopy(backpack));
        plugin.getBackupManager().rememberUnloaded(ownerId, deepCopy(backpack));
    }

    /**
     * Autosave. Also unloads backpacks whose owner is offline and that nobody
     * is viewing any more, e.g. left behind while save-on-close or
     * save-on-quit is disabled.
     */
    public void saveAll() {
        forEachLoadedSpread(ownerId -> {
            Backpack backpack = loaded.get(ownerId);
            if (backpack == null) {
                return;
            }
            if (Bukkit.getPlayer(ownerId) == null && !backpack.hasViewers()) {
                unload(ownerId, backpack);
            } else {
                plugin.getDataManager().saveAsync(ownerId, deepCopy(backpack));
            }
        });
    }

    /**
     * Hands {@code sink} a deep copy of every loaded backpack, spread over
     * the next ticks. Returns the owners that will be handled.
     */
    public Set<UUID> snapshotLoaded(BiConsumer<UUID, DataManager.BackpackData> sink) {
        return forEachLoadedSpread(ownerId -> {
            Backpack backpack = loaded.get(ownerId);
            if (backpack != null) {
                sink.accept(ownerId, deepCopy(backpack));
            }
        });
    }

    private Set<UUID> forEachLoadedSpread(Consumer<UUID> action) {
        Set<UUID> owners = Set.copyOf(loaded.keySet());
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
        for (Map.Entry<UUID, Backpack> entry : loaded.entrySet()) {
            plugin.getDataManager().saveAsync(entry.getKey(), deepCopy(entry.getValue()));
        }
    }

    // getContents() returns live mirrors of the slot items, which must not be
    // serialized on another thread while players keep editing them.
    private DataManager.BackpackData deepCopy(Backpack backpack) {
        int size = backpack.rows() * 9;
        ItemStack[] copy = new ItemStack[size * backpack.pages().size()];
        for (int page = 0; page < backpack.pages().size(); page++) {
            System.arraycopy(withoutButtons(backpack, page, true), 0, copy, page * size, size);
        }
        return new DataManager.BackpackData(backpack.rows(), backpack.pages().size(), copy);
    }

    // Only the two button slots are checked, and only for the button itself:
    // a real item that somehow got there is kept and moved on the next build.
    private ItemStack[] withoutButtons(Backpack backpack, int page, boolean cloneItems) {
        ItemStack[] contents = backpack.pages().get(page).getContents();
        int pageCount = backpack.pages().size();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null) {
                continue;
            }
            if (BackpackLayout.isButtonSlot(slot, page, pageCount, backpack.rows())
                    && plugin.getItemManager().isPageButton(item)) {
                contents[slot] = null;
            } else if (cloneItems) {
                contents[slot] = item.clone();
            }
        }
        return contents;
    }

    public boolean isBlocked(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        return plugin.getConfigManager().getBlockedItems().contains(type);
    }
}
