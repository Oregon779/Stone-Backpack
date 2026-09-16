package dev.stonebackpack.plugin.listener;

import dev.stonebackpack.plugin.StoneBackpack;
import dev.stonebackpack.plugin.command.PlayerLookup;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {
    private final StoneBackpack plugin;

    public PlayerJoinListener(StoneBackpack plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();

        // Keeps the O(1) name->UUID lookup cache current without ever
        // needing to re-scan Bukkit.getOfflinePlayers() again - see
        // PlayerLookup for why that scan matters at scale.
        PlayerLookup.recordJoin(player);

        if (plugin.getConfigManager().isItemEnabled() && plugin.getConfigManager().isGiveOnFirstJoin()
                && !plugin.getDataManager().hasData(player.getUniqueId())) {
            // Entity scheduler instead of BukkitRunnable: the correct,
            // Folia-safe way to run a delayed task tied to a specific
            // player, and a plain same-thread delay on regular Paper.
            player.getScheduler().runDelayed(plugin, task -> {
                plugin.getItemManager().give(player, plugin.getItemManager().createBackpackItem());
                plugin.getMessageManager().send(player, "backpack.received-item", null);
            }, null, 20L);
        }
    }
}
