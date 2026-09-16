package dev.stonebackpack.plugin.command;

import dev.stonebackpack.plugin.StoneBackpack;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BackpackCommand implements CommandExecutor, TabCompleter {
    private final StoneBackpack plugin;

    public BackpackCommand(StoneBackpack plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessageManager().send(sender, "general.player-only", null);
            return true;
        }

        if (!plugin.getWorldRestrictionManager().isAllowed(player.getWorld())) {
            plugin.getMessageManager().send(player, "general.world-blocked", null);
            return true;
        }

        boolean openingOther = args.length > 0;
        if (openingOther) {
            if (!player.hasPermission("stonebackpack.others") && !player.hasPermission("stonebackpack.admin")) {
                plugin.getMessageManager().send(player, "general.no-permission", null);
                return true;
            }
        } else if (!player.hasPermission("stonebackpack.use")) {
            plugin.getMessageManager().send(player, "general.no-permission", null);
            return true;
        }

        long remaining = plugin.getCooldownManager().getRemainingSeconds(player.getUniqueId(), plugin.getConfigManager().getCommandCooldownSeconds());
        if (remaining > 0 && !player.hasPermission("stonebackpack.bypass.cooldown") && !player.hasPermission("stonebackpack.admin")) {
            plugin.getMessageManager().send(player, "backpack.cooldown", Map.of("seconds", String.valueOf(remaining)));
            return true;
        }

        OfflinePlayer target = player;
        if (openingOther) {
            target = PlayerLookup.resolve(args[0]);
            if (target == null) {
                plugin.getMessageManager().send(player, "general.player-not-found", Map.of("player", args[0]));
                return true;
            }
        }

        plugin.getCooldownManager().trigger(player.getUniqueId());
        plugin.getBackpackManager().open(player, target);

        if (openingOther) {
            plugin.getMessageManager().send(player, "backpack.opened-other", Map.of("player", target.getName() != null ? target.getName() : args[0]));
        }
        return true;
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        if (!sender.hasPermission("stonebackpack.others") && !sender.hasPermission("stonebackpack.admin")) {
            return List.of();
        }
        String partial = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
    }
}

