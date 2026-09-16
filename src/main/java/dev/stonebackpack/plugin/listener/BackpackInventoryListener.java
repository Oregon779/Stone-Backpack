package dev.stonebackpack.plugin.listener;

import dev.stonebackpack.plugin.StoneBackpack;
import dev.stonebackpack.plugin.model.BackpackHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public class BackpackInventoryListener implements Listener {
    private final StoneBackpack plugin;

    public BackpackInventoryListener(StoneBackpack plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BackpackHolder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (player.hasPermission("stonebackpack.bypass.blocklist") || player.hasPermission("stonebackpack.admin")) {
            return;
        }

        boolean clickedTop = event.getClickedInventory() != null && event.getClickedInventory().equals(event.getInventory());
        ItemStack incoming = incomingItem(event, clickedTop);

        if (isDisallowed(incoming)) {
            event.setCancelled(true);
            plugin.getMessageManager().send(player, "backpack.item-blocked", null);
        }
    }

    private ItemStack incomingItem(InventoryClickEvent event, boolean clickedTop) {
        if (!clickedTop) {
            return event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY ? event.getCurrentItem() : null;
        }
        return switch (event.getAction()) {
            case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR -> event.getCursor();
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                int hotbarButton = event.getHotbarButton();
                yield hotbarButton >= 0 ? event.getWhoClicked().getInventory().getItem(hotbarButton) : null;
            }
            default -> null;
        };
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof BackpackHolder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (player.hasPermission("stonebackpack.bypass.blocklist") || player.hasPermission("stonebackpack.admin")) {
            return;
        }

        int topSize = event.getInventory().getSize();
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (touchesTop && isDisallowed(event.getOldCursor())) {
            event.setCancelled(true);
            plugin.getMessageManager().send(player, "backpack.item-blocked", null);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BackpackHolder holder)) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            player.playSound(player.getLocation(), plugin.getConfigManager().getCloseSound(),
                    plugin.getConfigManager().getSoundVolume(), plugin.getConfigManager().getSoundPitch());
        }
        if (plugin.getConfigManager().isSaveOnClose()) {
            plugin.getBackpackManager().saveAndMaybeUnload(holder.getOwnerId());
        }
    }

    private boolean isDisallowed(ItemStack item) {
        if (item == null) {
            return false;
        }
        return plugin.getBackpackManager().isBlocked(item) || plugin.getItemManager().isBackpackItem(item);
    }
}

