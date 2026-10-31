package dev.stonebackpack.plugin.menu;

import dev.stonebackpack.plugin.StoneBackpack;
import dev.stonebackpack.plugin.manager.DataManager.StoredItems;
import dev.stonebackpack.plugin.manager.DataManager.StoredSummary;
import dev.stonebackpack.plugin.model.StorageMenuHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin menu for the items that were taken out of backpacks because their
 * owners lost pages. Every change goes through DataManager first and the
 * items are only handed out once it confirmed the removal, so a stale or
 * double click can never give an item twice.
 */
public class StorageMenu implements Listener {
    private static final int SIZE = 54;
    private static final int PAGE_SIZE = 45;

    private static final int LIST_PREVIOUS = 45;
    private static final int LIST_CLOSE = 49;
    private static final int LIST_NEXT = 53;

    private static final int VIEW_BACK = 45;
    private static final int VIEW_PREVIOUS = 47;
    private static final int VIEW_INFO = 49;
    private static final int VIEW_NEXT = 51;
    private static final int VIEW_GIVE = 52;
    private static final int VIEW_TAKE_ALL = 53;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final StoneBackpack plugin;

    public StorageMenu(StoneBackpack plugin) {
        this.plugin = plugin;
    }

    public void openPlayerList(Player admin, int requestedPage) {
        plugin.getDataManager().listStoredAsync(entries -> onMainThread(() -> {
            if (admin.isOnline()) {
                showPlayerList(admin, entries, requestedPage);
            }
        }));
    }

    private void showPlayerList(Player admin, List<StoredSummary> entries, int requestedPage) {
        if (entries.isEmpty()) {
            Inventory top = admin.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder(false) instanceof StorageMenuHolder) {
                admin.closeInventory();
            }
            plugin.getMessageManager().send(admin, "storage.none", null);
            return;
        }
        List<StoredSummary> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingLong(StoredSummary::updated).reversed());
        int pageCount = pageCount(sorted.size());
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        List<StoredSummary> onPage = sorted.subList(page * PAGE_SIZE, Math.min(sorted.size(), (page + 1) * PAGE_SIZE));

        StorageMenuHolder holder = StorageMenuHolder.playerList(page, pageCount, List.copyOf(onPage));
        Inventory inventory = Bukkit.createInventory(holder, SIZE, text("storage.gui.players-title", Map.of(
                "page", String.valueOf(page + 1), "pages", String.valueOf(pageCount))));
        holder.setInventory(inventory);
        for (int i = 0; i < onPage.size(); i++) {
            inventory.setItem(i, playerHead(onPage.get(i)));
        }
        fillBottomRow(inventory);
        if (page > 0) {
            inventory.setItem(LIST_PREVIOUS, button(Material.ARROW, "backups.gui.previous-page", null));
        }
        inventory.setItem(LIST_CLOSE, button(Material.BARRIER, "backups.gui.close", null));
        if (page < pageCount - 1) {
            inventory.setItem(LIST_NEXT, button(Material.ARROW, "backups.gui.next-page", null));
        }
        admin.openInventory(inventory);
    }

    private void showItems(Player admin, UUID targetId, StoredItems stored, int requestedPage, int returnPage) {
        List<ItemStack> items = stored.items();
        int pageCount = pageCount(items.size());
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        StorageMenuHolder holder = StorageMenuHolder.items(targetId, stored.name(), items, page, pageCount, returnPage);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, text("storage.gui.items-title", Map.of(
                "player", stored.name(), "page", String.valueOf(page + 1), "pages", String.valueOf(pageCount))));
        holder.setInventory(inventory);

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < items.size(); i++) {
            inventory.setItem(i, items.get(start + i));
        }
        fillBottomRow(inventory);
        inventory.setItem(VIEW_BACK, button(Material.OAK_DOOR, "backups.gui.back", null));
        if (page > 0) {
            inventory.setItem(VIEW_PREVIOUS, button(Material.ARROW, "backups.gui.previous-page", null));
        }
        inventory.setItem(VIEW_INFO, infoItem(stored));
        if (page < pageCount - 1) {
            inventory.setItem(VIEW_NEXT, button(Material.ARROW, "backups.gui.next-page", null));
        }
        Map<String, String> player = Map.of("player", stored.name());
        ItemStack give = Bukkit.getPlayer(targetId) != null
                ? button(Material.LIME_DYE, "storage.gui.give-name", player)
                : button(Material.GRAY_DYE, "storage.gui.offline-name", player);
        give.editMeta(meta -> meta.lore(List.of(text("storage.gui.give-lore", null))));
        inventory.setItem(VIEW_GIVE, give);
        ItemStack takeAll = button(Material.SHULKER_BOX, "storage.gui.take-all-name", null);
        takeAll.editMeta(meta -> meta.lore(List.of(text("storage.gui.take-all-lore", null))));
        inventory.setItem(VIEW_TAKE_ALL, takeAll);
        admin.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof StorageMenuHolder holder)) {
            return;
        }
        // Everything is cancelled, including clicks in the admin's own inventory:
        // a double click there would otherwise collect matching items out of the menu.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin)
                || !event.getInventory().equals(event.getClickedInventory())
                || event.getClick() == ClickType.DOUBLE_CLICK) {
            return;
        }
        int slot = event.getSlot();
        // Opening or closing inventories from inside InventoryClickEvent is
        // unsafe, so the resulting action runs on the admin's next tick.
        admin.getScheduler().run(plugin, task -> handleClick(admin, holder, slot), null);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof StorageMenuHolder) {
            event.setCancelled(true);
        }
    }

    private boolean isShowing(Player admin, StorageMenuHolder holder) {
        return holder.getInventory().equals(admin.getOpenInventory().getTopInventory());
    }

    private void handleClick(Player admin, StorageMenuHolder holder, int slot) {
        if (!isShowing(admin, holder)) {
            return;
        }
        if (!admin.hasPermission("stonebackpack.admin") && !admin.isOp()) {
            admin.closeInventory();
            return;
        }
        switch (holder.getView()) {
            case PLAYERS -> handlePlayerListClick(admin, holder, slot);
            case ITEMS -> handleItemsClick(admin, holder, slot);
        }
    }

    private void handlePlayerListClick(Player admin, StorageMenuHolder holder, int slot) {
        StoredSummary entry = holder.entryAt(slot);
        if (entry != null) {
            plugin.getDataManager().loadStoredAsync(entry.playerId(), stored -> onMainThread(() -> {
                if (!isShowing(admin, holder)) {
                    return;
                }
                if (stored == null || stored.items().isEmpty()) {
                    plugin.getMessageManager().send(admin, "storage.none-player", Map.of("player", entry.name()));
                    openPlayerList(admin, holder.getPage());
                } else {
                    showItems(admin, entry.playerId(), stored, 0, holder.getPage());
                }
            }));
        } else if (slot == LIST_PREVIOUS && holder.getPage() > 0) {
            openPlayerList(admin, holder.getPage() - 1);
        } else if (slot == LIST_NEXT && holder.getPage() < holder.getPageCount() - 1) {
            openPlayerList(admin, holder.getPage() + 1);
        } else if (slot == LIST_CLOSE) {
            admin.closeInventory();
        }
    }

    private void handleItemsClick(Player admin, StorageMenuHolder holder, int slot) {
        int index = holder.getPage() * PAGE_SIZE + slot;
        if (slot < PAGE_SIZE && index < holder.getItems().size()) {
            takeOne(admin, holder, index);
        } else if (slot == VIEW_BACK) {
            openPlayerList(admin, holder.getReturnPage());
        } else if (slot == VIEW_PREVIOUS && holder.getPage() > 0) {
            reopen(admin, holder, holder.getPage() - 1);
        } else if (slot == VIEW_NEXT && holder.getPage() < holder.getPageCount() - 1) {
            reopen(admin, holder, holder.getPage() + 1);
        } else if (slot == VIEW_GIVE) {
            giveToOwner(admin, holder);
        } else if (slot == VIEW_TAKE_ALL) {
            takeAll(admin, holder);
        }
    }

    private void reopen(Player admin, StorageMenuHolder holder, int page) {
        showItems(admin, holder.getTargetId(), new StoredItems(holder.getTargetName(), 0, holder.getItems()), page,
                holder.getReturnPage());
    }

    private void takeOne(Player admin, StorageMenuHolder holder, int index) {
        UUID ownerId = holder.getTargetId();
        String name = holder.getTargetName();
        plugin.getDataManager().takeStoredAsync(ownerId, index, holder.getItems().get(index), (taken, remaining) -> onMainThread(() -> {
            if (taken == null) {
                plugin.getMessageManager().send(admin, "storage.changed", Map.of("player", name));
            } else if (!admin.isOnline()) {
                plugin.getDataManager().addStoredAsync(ownerId, name, List.of(taken));
                return;
            } else {
                plugin.getItemManager().give(admin, taken);
                plugin.getMessageManager().send(admin, "storage.taken", Map.of(
                        "player", name, "amount", String.valueOf(taken.getAmount()), "item", itemName(taken)));
                plugin.getLogger().info(admin.getName() + " took " + taken.getAmount() + "x " + taken.getType()
                        + " from the item storage of " + name + ".");
            }
            if (isShowing(admin, holder)) {
                showRemaining(admin, holder, remaining);
            }
        }));
    }

    private void takeAll(Player admin, StorageMenuHolder holder) {
        UUID ownerId = holder.getTargetId();
        String name = holder.getTargetName();
        // Closing first means a quick second click can't start a second take.
        admin.closeInventory();
        plugin.getDataManager().takeAllStoredAsync(ownerId, items -> onMainThread(() -> {
            if (items.isEmpty()) {
                plugin.getMessageManager().send(admin, "storage.none-player", Map.of("player", name));
                return;
            }
            if (!admin.isOnline()) {
                plugin.getDataManager().addStoredAsync(ownerId, name, items);
                return;
            }
            for (ItemStack box : plugin.getBackupManager().packIntoShulkerBoxes(items, "storage.shulker-name", name)) {
                plugin.getItemManager().give(admin, box);
            }
            plugin.getMessageManager().send(admin, "storage.taken-all", Map.of(
                    "player", name, "stacks", String.valueOf(items.size())));
            plugin.getLogger().info(admin.getName() + " took all " + items.size() + " item stack(s) from the item storage of "
                    + name + " as shulker boxes.");
        }));
    }

    // Straight into the owner's inventory; whatever doesn't fit stays stored.
    private void giveToOwner(Player admin, StorageMenuHolder holder) {
        UUID ownerId = holder.getTargetId();
        String name = holder.getTargetName();
        if (Bukkit.getPlayer(ownerId) == null) {
            plugin.getMessageManager().send(admin, "storage.player-offline", Map.of("player", name));
            return;
        }
        plugin.getDataManager().takeAllStoredAsync(ownerId, items -> onMainThread(() -> {
            if (items.isEmpty()) {
                plugin.getMessageManager().send(admin, "storage.none-player", Map.of("player", name));
                return;
            }
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null) {
                plugin.getDataManager().addStoredAsync(ownerId, name, items);
                plugin.getMessageManager().send(admin, "storage.player-offline", Map.of("player", name));
                return;
            }
            List<ItemStack> kept = new ArrayList<>();
            for (ItemStack item : items) {
                kept.addAll(owner.getInventory().addItem(item.clone()).values());
            }
            int given = items.size() - kept.size();
            if (!kept.isEmpty()) {
                plugin.getDataManager().addStoredAsync(ownerId, name, kept);
                plugin.getMessageManager().send(admin, "storage.given-partly", Map.of(
                        "player", name, "given", String.valueOf(given), "kept", String.valueOf(kept.size())));
            } else {
                plugin.getMessageManager().send(admin, "storage.given", Map.of("player", name, "stacks", String.valueOf(given)));
            }
            if (given > 0) {
                plugin.getMessageManager().send(owner, "storage.received", Map.of("stacks", String.valueOf(given)));
            }
            plugin.getLogger().info(admin.getName() + " gave " + given + " item stack(s) from the item storage back to "
                    + name + (kept.isEmpty() ? "." : "; " + kept.size() + " didn't fit and stay stored."));
            if (admin.isOnline() && isShowing(admin, holder)) {
                plugin.getDataManager().loadStoredAsync(ownerId, stored -> onMainThread(() -> {
                    if (isShowing(admin, holder)) {
                        showRemaining(admin, holder, stored);
                    }
                }));
            }
        }));
    }

    private void showRemaining(Player admin, StorageMenuHolder holder, StoredItems remaining) {
        if (remaining == null || remaining.items().isEmpty()) {
            openPlayerList(admin, holder.getReturnPage());
        } else {
            showItems(admin, holder.getTargetId(), remaining, holder.getPage(), holder.getReturnPage());
        }
    }

    // DataManager callbacks run on its IO thread. The global region scheduler
    // always runs the task, unlike a player's scheduler once they have left.
    private void onMainThread(Runnable action) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, action);
    }

    private ItemStack playerHead(StoredSummary entry) {
        ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> {
            meta.setPlayerProfile(Bukkit.createProfile(entry.playerId(), entry.name()));
            meta.displayName(text("storage.gui.player-name", Map.of("player", entry.name())));
            meta.lore(List.of(
                    text("storage.gui.player-stacks", Map.of("stacks", String.valueOf(entry.stacks()))),
                    text("storage.gui.player-updated", Map.of("time", TIME_FORMAT.format(Instant.ofEpochMilli(entry.updated())))),
                    text("storage.gui.player-lore", null)));
        });
        return head;
    }

    private ItemStack infoItem(StoredItems stored) {
        ItemStack info = button(Material.CLOCK, "storage.gui.info-name", null);
        info.editMeta(meta -> meta.lore(List.of(
                text("storage.gui.info-stacks", Map.of("stacks", String.valueOf(stored.items().size()))),
                text("storage.gui.info-hint", null))));
        return info;
    }

    private ItemStack button(Material material, String namePath, Map<String, String> placeholders) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> meta.displayName(text(namePath, placeholders)));
        return item;
    }

    private void fillBottomRow(Inventory inventory) {
        ItemStack filler = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        filler.editMeta(meta -> meta.displayName(Component.text(" ")));
        for (int slot = PAGE_SIZE; slot < SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private Component text(String path, Map<String, String> placeholders) {
        return plugin.getMessageManager().component(path, placeholders).decoration(TextDecoration.ITALIC, false);
    }

    private static String itemName(ItemStack item) {
        return item.getType().getKey().getKey().replace('_', ' ');
    }

    private static int pageCount(int entries) {
        return Math.max(1, (entries + PAGE_SIZE - 1) / PAGE_SIZE);
    }
}
