package dev.stonebackpack.plugin.menu;

import dev.stonebackpack.plugin.StoneBackpack;
import dev.stonebackpack.plugin.manager.DataManager;
import dev.stonebackpack.plugin.model.BackupMenuHolder;
import dev.stonebackpack.plugin.model.BackupMenuHolder.Target;
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

public class BackupMenu implements Listener {
    private static final int SIZE = 54;
    private static final int PAGE_SIZE = 45;

    private static final int LIST_PREVIOUS = 45;
    private static final int LIST_CLOSE = 49;
    private static final int LIST_NEXT = 53;

    private static final int VIEW_BACK = 45;
    private static final int VIEW_PREVIOUS = 47;
    private static final int VIEW_INFO = 49;
    private static final int VIEW_NEXT = 51;
    private static final int VIEW_COPY = 53;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final StoneBackpack plugin;

    public BackupMenu(StoneBackpack plugin) {
        this.plugin = plugin;
    }

    public void openPlayerList(Player admin, int requestedPage) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int pageCount = pageCount(online.size());
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        List<Player> onPage = online.subList(page * PAGE_SIZE, Math.min(online.size(), (page + 1) * PAGE_SIZE));

        List<Target> targets = onPage.stream().map(player -> new Target(player.getUniqueId(), player.getName())).toList();
        BackupMenuHolder holder = BackupMenuHolder.playerList(page, pageCount, targets);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, text("backups.gui.players-title", Map.of(
                "page", String.valueOf(page + 1), "pages", String.valueOf(pageCount))));
        holder.setInventory(inventory);

        for (int i = 0; i < onPage.size(); i++) {
            inventory.setItem(i, playerHead(onPage.get(i)));
        }
        fillBottomRow(inventory);
        if (page > 0) {
            inventory.setItem(LIST_PREVIOUS, button(Material.ARROW, "backups.gui.previous-page"));
        }
        inventory.setItem(LIST_CLOSE, button(Material.BARRIER, "backups.gui.close"));
        if (page < pageCount - 1) {
            inventory.setItem(LIST_NEXT, button(Material.ARROW, "backups.gui.next-page"));
        }
        admin.openInventory(inventory);
    }

    private void openBackup(Player admin, Target target, DataManager.Backup backup, int returnPage, int requestedPage) {
        ItemStack[] contents = backup.data().contents();
        int pageCount = pageCount(contents.length);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));

        BackupMenuHolder holder = BackupMenuHolder.backup(target, backup, page, pageCount, returnPage);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, text("backups.gui.backup-title", Map.of(
                "player", target.name(), "page", String.valueOf(page + 1), "pages", String.valueOf(pageCount))));
        holder.setInventory(inventory);

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < contents.length; i++) {
            inventory.setItem(i, contents[start + i]);
        }
        fillBottomRow(inventory);
        inventory.setItem(VIEW_BACK, button(Material.OAK_DOOR, "backups.gui.back"));
        if (page > 0) {
            inventory.setItem(VIEW_PREVIOUS, button(Material.ARROW, "backups.gui.previous-page"));
        }
        inventory.setItem(VIEW_INFO, infoItem(backup));
        if (page < pageCount - 1) {
            inventory.setItem(VIEW_NEXT, button(Material.ARROW, "backups.gui.next-page"));
        }
        ItemStack copy = button(Material.SHULKER_BOX, "backups.gui.copy-name");
        copy.editMeta(meta -> meta.lore(List.of(text("backups.gui.copy-lore", null))));
        inventory.setItem(VIEW_COPY, copy);
        admin.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BackupMenuHolder holder)) {
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
        // unsafe, so the resulting navigation runs on the admin's next tick.
        admin.getScheduler().run(plugin, task -> handleClick(admin, holder, slot), null);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof BackupMenuHolder) {
            event.setCancelled(true);
        }
    }

    private boolean isShowing(Player admin, BackupMenuHolder holder) {
        return admin.getOpenInventory().getTopInventory().getHolder() == holder;
    }

    private void handleClick(Player admin, BackupMenuHolder holder, int slot) {
        if (!isShowing(admin, holder)) {
            return;
        }
        if (!admin.hasPermission("stonebackpack.admin") && !admin.isOp()) {
            admin.closeInventory();
            return;
        }
        switch (holder.getView()) {
            case PLAYERS -> handlePlayerListClick(admin, holder, slot);
            case BACKUP -> handleBackupClick(admin, holder, slot);
        }
    }

    private void handlePlayerListClick(Player admin, BackupMenuHolder holder, int slot) {
        Target target = holder.targetAt(slot);
        if (target != null) {
            plugin.getDataManager().loadBackupAsync(target.id(), backup -> admin.getScheduler().run(plugin, task -> {
                if (!isShowing(admin, holder)) {
                    return;
                }
                if (backup == null) {
                    plugin.getMessageManager().send(admin, "backups.no-backup", Map.of("player", target.name()));
                } else {
                    openBackup(admin, target, backup, holder.getPage(), 0);
                }
            }, null));
        } else if (slot == LIST_PREVIOUS && holder.getPage() > 0) {
            openPlayerList(admin, holder.getPage() - 1);
        } else if (slot == LIST_NEXT && holder.getPage() < holder.getPageCount() - 1) {
            openPlayerList(admin, holder.getPage() + 1);
        } else if (slot == LIST_CLOSE) {
            admin.closeInventory();
        }
    }

    private void handleBackupClick(Player admin, BackupMenuHolder holder, int slot) {
        if (slot == VIEW_BACK) {
            openPlayerList(admin, holder.getReturnPage());
        } else if (slot == VIEW_PREVIOUS && holder.getPage() > 0) {
            openBackup(admin, holder.getTarget(), holder.getBackup(), holder.getReturnPage(), holder.getPage() - 1);
        } else if (slot == VIEW_NEXT && holder.getPage() < holder.getPageCount() - 1) {
            openBackup(admin, holder.getTarget(), holder.getBackup(), holder.getReturnPage(), holder.getPage() + 1);
        } else if (slot == VIEW_COPY) {
            copyToAdmin(admin, holder);
        }
    }

    private void copyToAdmin(Player admin, BackupMenuHolder holder) {
        Target target = holder.getTarget();
        ItemStack[] contents = holder.getBackup().data().contents();
        List<ItemStack> restoreItems = plugin.getBackupManager().createRestoreItems(contents, target.name());
        if (restoreItems.isEmpty()) {
            plugin.getMessageManager().send(admin, "backups.empty", Map.of("player", target.name()));
            return;
        }
        // Closing first means a quick second click can't hand out a second copy.
        admin.closeInventory();
        for (ItemStack item : restoreItems) {
            plugin.getItemManager().give(admin, item);
        }
        int stacks = usedSlots(contents);
        plugin.getMessageManager().send(admin, "backups.copied", Map.of(
                "player", target.name(), "stacks", String.valueOf(stacks)));
        plugin.getLogger().info(admin.getName() + " copied the backpack backup of " + target.name()
                + " (" + stacks + " stacks, saved " + TIME_FORMAT.format(Instant.ofEpochMilli(holder.getBackup().timestamp())) + ").");
    }

    private ItemStack playerHead(Player player) {
        ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> {
            meta.setPlayerProfile(player.getPlayerProfile());
            meta.displayName(text("backups.gui.player-name", Map.of("player", player.getName())));
            meta.lore(List.of(text("backups.gui.player-lore", null)));
        });
        return head;
    }

    private ItemStack infoItem(DataManager.Backup backup) {
        ItemStack[] contents = backup.data().contents();
        ItemStack info = button(Material.CLOCK, "backups.gui.info-name");
        info.editMeta(meta -> meta.lore(List.of(
                text("backups.gui.info-time", Map.of("time", TIME_FORMAT.format(Instant.ofEpochMilli(backup.timestamp())))),
                text("backups.gui.info-age", Map.of("age", formatAge(System.currentTimeMillis() - backup.timestamp()))),
                text("backups.gui.info-slots", Map.of(
                        "used", String.valueOf(usedSlots(contents)), "slots", String.valueOf(contents.length)))
        )));
        return info;
    }

    private ItemStack button(Material material, String namePath) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> meta.displayName(text(namePath, null)));
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

    private static int pageCount(int entries) {
        return Math.max(1, (entries + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private static int usedSlots(ItemStack[] contents) {
        int used = 0;
        for (ItemStack item : contents) {
            if (item != null && !item.isEmpty()) {
                used++;
            }
        }
        return used;
    }

    private static String formatAge(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        }
        return seconds + "s";
    }
}
