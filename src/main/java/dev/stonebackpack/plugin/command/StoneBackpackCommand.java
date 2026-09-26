package dev.stonebackpack.plugin.command;

import dev.stonebackpack.plugin.StoneBackpack;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StoneBackpackCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("help", "reload", "give", "inspect", "backups", "checkupdate");

    private final StoneBackpack plugin;

    public StoneBackpackCommand(StoneBackpack plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "give" -> handleGive(sender, args);
            case "inspect" -> handleInspect(sender, args);
            case "backups" -> handleBackups(sender);
            case "checkupdate" -> handleCheckUpdate(sender);
            case "help" -> sendHelp(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        if (!hasAdmin(sender)) {
            plugin.getMessageManager().send(sender, "general.no-permission", null);
            return;
        }
        plugin.reload();
        plugin.getMessageManager().send(sender, "general.reload-success", null);
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            plugin.getMessageManager().send(sender, "general.no-permission", null);
            return;
        }
        if (args.length < 2) {
            plugin.getMessageManager().sendPlain(sender, "&cUsage: /stonebackpack give <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.getMessageManager().send(sender, "general.player-not-found", Map.of("player", args[1]));
            return;
        }
        plugin.getItemManager().give(target, plugin.getItemManager().createBackpackItem());
        plugin.getMessageManager().send(sender, "backpack.gave-item", Map.of("player", target.getName()));
        if (!target.equals(sender)) {
            plugin.getMessageManager().send(target, "backpack.received-item", null);
        }
    }

    private void handleInspect(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            plugin.getMessageManager().send(sender, "general.no-permission", null);
            return;
        }
        if (!(sender instanceof Player admin)) {
            plugin.getMessageManager().send(sender, "general.player-only", null);
            return;
        }
        if (args.length < 2) {
            plugin.getMessageManager().sendPlain(sender, "&cUsage: /stonebackpack inspect <player>");
            return;
        }
        org.bukkit.OfflinePlayer target = PlayerLookup.resolve(args[1]);
        if (target == null) {
            plugin.getMessageManager().send(sender, "general.player-not-found", Map.of("player", args[1]));
            return;
        }
        plugin.getBackpackManager().openInspect(admin, target);
        plugin.getMessageManager().send(admin, "backpack.inspecting", Map.of("player", target.getName() != null ? target.getName() : args[1]));
    }

    private void handleBackups(CommandSender sender) {
        if (!hasAdmin(sender)) {
            plugin.getMessageManager().send(sender, "general.no-permission", null);
            return;
        }
        if (!(sender instanceof Player admin)) {
            plugin.getMessageManager().send(sender, "general.player-only", null);
            return;
        }
        plugin.getBackupMenu().openPlayerList(admin, 0);
    }

    private void handleCheckUpdate(CommandSender sender) {
        if (!hasAdmin(sender)) {
            plugin.getMessageManager().send(sender, "general.no-permission", null);
            return;
        }
        plugin.getMessageManager().send(sender, "update.check-triggered", null);
        plugin.getUpdateChecker().checkNow(() -> plugin.getUpdateChecker().notifyIfOutdated(sender));
    }

    private void sendHelp(CommandSender sender) {
        boolean admin = hasAdmin(sender);
        plugin.getMessageManager().sendRaw(sender, "help.header", null);
        if (sender.hasPermission("stonebackpack.use") || admin) {
            plugin.getMessageManager().sendRaw(sender, "help.backpack", null);
        }
        if (sender.hasPermission("stonebackpack.others") || admin) {
            plugin.getMessageManager().sendRaw(sender, "help.backpack-other", null);
        }
        if (admin) {
            plugin.getMessageManager().sendRaw(sender, "help.give", null);
            plugin.getMessageManager().sendRaw(sender, "help.inspect", null);
            plugin.getMessageManager().sendRaw(sender, "help.backups", null);
            plugin.getMessageManager().sendRaw(sender, "help.reload", null);
            plugin.getMessageManager().sendRaw(sender, "help.checkupdate", null);
        }
        plugin.getMessageManager().sendRaw(sender, "help.help", null);
    }

    private boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission("stonebackpack.admin") || sender.isOp();
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        boolean admin = hasAdmin(sender);
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> matches = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (!admin && !sub.equals("help")) {
                    continue;
                }
                if (sub.startsWith(partial)) {
                    matches.add(sub);
                }
            }
            return matches;
        }
        if (admin && args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("inspect"))) {
            String partial = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}

