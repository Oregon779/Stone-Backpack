package dev.stonebackpack.plugin.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a typed player name to an {@link OfflinePlayer}, online or not.
 * <p>
 * Backed by an in-memory name -> UUID cache instead of scanning
 * Bukkit.getOfflinePlayers() on every lookup: on a long-running server with
 * a large historical player base, that array can hold thousands of entries,
 * and re-iterating it on every /backpack &lt;name&gt;, /stonebackpack give
 * or /stonebackpack inspect command turns an O(1) permission check into an
 * O(n) scan on the main thread. The cache is warmed once asynchronously at
 * startup and kept current incrementally via {@link #recordJoin}, so no
 * lookup after that ever re-scans the full player history.
 */
public final class PlayerLookup {
    private static final Map<String, UUID> NAME_TO_UUID = new ConcurrentHashMap<>();
    private static volatile boolean warmed = false;

    private PlayerLookup() {
    }

    public static void warmUpAsync(Plugin plugin) {
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            for (OfflinePlayer candidate : Bukkit.getOfflinePlayers()) {
                String name = candidate.getName();
                if (name != null) {
                    NAME_TO_UUID.putIfAbsent(name.toLowerCase(Locale.ROOT), candidate.getUniqueId());
                }
            }
            warmed = true;
        });
    }

    public static void recordJoin(Player player) {
        NAME_TO_UUID.put(player.getName().toLowerCase(Locale.ROOT), player.getUniqueId());
    }

    static OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        UUID cached = NAME_TO_UUID.get(name.toLowerCase(Locale.ROOT));
        if (cached != null) {
            return Bukkit.getOfflinePlayer(cached);
        }
        if (!warmed) {
            // Warm-up hasn't finished yet (e.g. a command runs in the first
            // instant after startup) - fall back to a direct scan just this
            // once rather than reporting a false "player not found".
            for (OfflinePlayer candidate : Bukkit.getOfflinePlayers()) {
                if (name.equalsIgnoreCase(candidate.getName())) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
