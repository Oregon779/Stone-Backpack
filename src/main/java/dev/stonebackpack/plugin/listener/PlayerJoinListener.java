package dev.stonebackpack.plugin.listener;

import dev.stonebackpack.plugin.StoneBackpack;
import dev.stonebackpack.plugin.command.PlayerLookup;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;

public class PlayerJoinListener implements Listener {
    private final StoneBackpack plugin;
    private final NamespacedKey receivedItemKey;

    public PlayerJoinListener(StoneBackpack plugin) {
        this.plugin = plugin;
        this.receivedItemKey = new NamespacedKey(plugin, "received_backpack_item");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();

        // Keeps the O(1) name->UUID lookup cache current without ever
        // needing to re-scan Bukkit.getOfflinePlayers() again - see
        // PlayerLookup for why that scan matters at scale.
        PlayerLookup.recordJoin(player);

        // The flag matters because a player who never opens the backpack has no
        // data file, and would otherwise get another item on every join.
        if (plugin.getConfigManager().isItemEnabled() && plugin.getConfigManager().isGiveOnFirstJoin()
                && !player.getPersistentDataContainer().has(receivedItemKey)
                && !plugin.getDataManager().hasData(player.getUniqueId())) {
            player.getScheduler().runDelayed(plugin, task -> {
                player.getPersistentDataContainer().set(receivedItemKey, PersistentDataType.BOOLEAN, true);
                plugin.getItemManager().give(player, plugin.getItemManager().createBackpackItem());
                plugin.getMessageManager().send(player, "backpack.received-item", null);
            }, null, 20L);
        }
    }
}
