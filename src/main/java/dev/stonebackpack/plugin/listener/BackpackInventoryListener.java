package dev.stonebackpack.plugin.listener;

import dev.stonebackpack.plugin.StoneBackpack;
import dev.stonebackpack.plugin.model.BackpackHolder;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

public class BackpackInventoryListener implements Listener {
    // A double click would also turn the page a second time.
    private static final Set<ClickType> TURN_CLICKS = EnumSet.of(
            ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT);

    private final StoneBackpack plugin;

    public BackpackInventoryListener(StoneBackpack plugin) {
        this.plugin = plugin;
    }

    // getHolder(false) throughout: plain getHolder() builds a full block-state
    // snapshot for chests and other block inventories, on every click anywhere
    // on the server.
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof BackpackHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean clickedTop = event.getClickedInventory() != null && event.getClickedInventory().equals(event.getInventory());
        if (clickedTop && holder.isButtonSlot(event.getSlot())) {
            // Nothing can be taken from or put onto a page button, by anyone.
            event.setCancelled(true);
            if (TURN_CLICKS.contains(event.getClick())) {
                boolean forward = holder.isNextButton(event.getSlot());
                // Opening another inventory from inside InventoryClickEvent is
                // unsafe, so the page turns on the player's next tick.
                player.getScheduler().run(plugin, task -> plugin.getBackpackManager().turnPage(player, holder, forward), null);
            }
            return;
        }
        ItemStack incoming = incomingItem(event, clickedTop);
        if (holder.isOverLimit() && !player.hasPermission("stonebackpack.admin")) {
            if (incoming != null && !incoming.isEmpty()) {
                event.setCancelled(true);
                plugin.getMessageManager().send(player, "backpack.page-over-limit", null);
            }
            return;
        }
        if (player.hasPermission("stonebackpack.bypass.blocklist") || player.hasPermission("stonebackpack.admin")) {
            return;
        }
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
            // PLACE_FROM_BUNDLE takes an item out of the bundle on the cursor;
            // the *_INTO_BUNDLE pickups put the cursor into a bundle stored in
            // the backpack. Either way the cursor's content ends up inside.
            case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR,
                 PLACE_FROM_BUNDLE, PICKUP_ALL_INTO_BUNDLE, PICKUP_SOME_INTO_BUNDLE -> event.getCursor();
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                int hotbarButton = event.getHotbarButton();
                yield hotbarButton >= 0 ? event.getWhoClicked().getInventory().getItem(hotbarButton) : null;
            }
            default -> null;
        };
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof BackpackHolder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int topSize = event.getInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize && holder.isButtonSlot(slot))) {
            event.setCancelled(true);
            return;
        }
        boolean touchesTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (touchesTop && holder.isOverLimit() && !player.hasPermission("stonebackpack.admin")) {
            event.setCancelled(true);
            plugin.getMessageManager().send(player, "backpack.page-over-limit", null);
            return;
        }
        if (player.hasPermission("stonebackpack.bypass.blocklist") || player.hasPermission("stonebackpack.admin")) {
            return;
        }

        if (touchesTop && isDisallowed(event.getOldCursor())) {
            event.setCancelled(true);
            plugin.getMessageManager().send(player, "backpack.item-blocked", null);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof BackpackHolder holder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (plugin.getBackpackManager().isTurningPage(player)) {
            return;
        }
        player.playSound(player.getLocation(), plugin.getConfigManager().getCloseSound(),
                plugin.getConfigManager().getSoundVolume(), plugin.getConfigManager().getSoundPitch());
        plugin.getBackpackManager().handleClose(holder.getOwnerId(), player);
    }

    // A bundle holding a blocked item would otherwise smuggle it in.
    private boolean isDisallowed(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        if (plugin.getBackpackManager().isBlocked(item) || plugin.getItemManager().isBackpackItem(item)) {
            return true;
        }
        BundleContents bundle = item.getData(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStack content : bundle.contents()) {
                if (isDisallowed(content)) {
                    return true;
                }
            }
        }
        return false;
    }
}
