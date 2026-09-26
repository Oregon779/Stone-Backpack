package dev.stonebackpack.plugin.listener;

import dev.stonebackpack.plugin.StoneBackpack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {
    private final StoneBackpack plugin;

    public PlayerQuitListener(StoneBackpack plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getBackpackManager().handleQuit(event.getPlayer());
        plugin.getCooldownManager().clear(event.getPlayer().getUniqueId());
    }
}
