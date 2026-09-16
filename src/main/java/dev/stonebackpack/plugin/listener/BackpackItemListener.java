package dev.stonebackpack.plugin.listener;

import dev.stonebackpack.plugin.StoneBackpack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class BackpackItemListener implements Listener {
    private final StoneBackpack plugin;

    public BackpackItemListener(StoneBackpack plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!plugin.getItemManager().isBackpackItem(event.getItem())) {
            return;
        }

        event.setCancelled(true);
        var player = event.getPlayer();

        if (!plugin.getWorldRestrictionManager().isAllowed(player.getWorld())) {
            plugin.getMessageManager().send(player, "general.world-blocked", null);
            return;
        }
        if (!player.hasPermission("stonebackpack.use")) {
            plugin.getMessageManager().send(player, "general.no-permission", null);
            return;
        }

        plugin.getBackpackManager().open(player, player);
    }
}

