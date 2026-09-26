package dev.stonebackpack.plugin.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import dev.stonebackpack.plugin.StoneBackpack;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class UpdateChecker implements Listener {
    private static final String MODRINTH_PROJECT_SLUG = "stone-backpack";
    private static final String MODRINTH_PROJECT_URL = "https://modrinth.com/project/stone-backpack";

    private final StoneBackpack plugin;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private ScheduledTask task;

    private volatile String latestKnownVersion;
    private volatile int versionsBehind = -1;

    public UpdateChecker(StoneBackpack plugin) {
        this.plugin = plugin;
    }

    /**
     * Uses Paper's async scheduler instead of Bukkit.getScheduler().runTaskTimerAsynchronously():
     * the latter is a Folia no-op/incompatible legacy API, while the async
     * scheduler is the correct primitive on both Paper and Folia for pure
     * background work with no world/player state involved (an HTTP call).
     */
    public void start() {
        stop();
        if (!plugin.getConfigManager().isUpdateCheckerEnabled()) {
            plugin.getLogger().info("Update checker: disabled in config.yml (update-checker.enabled: false).");
            return;
        }
        long intervalMinutes = plugin.getConfigManager().getUpdateCheckIntervalMinutes();
        task = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduled -> check(),
                5L, intervalMinutes * 60L, TimeUnit.SECONDS);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        stop();
        httpClient.shutdownNow();
    }

    public void checkNow(Runnable onDone) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> {
            check();
            if (onDone != null) {
                onDone.run();
            }
        });
    }

    private void check() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_SLUG + "/version"))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "StonePlugins/StoneBackpack update-checker")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                plugin.getLogger().warning("Update checker: Modrinth responded with status " + response.statusCode()
                        + " for project '" + MODRINTH_PROJECT_SLUG + "'.");
                return;
            }

            JsonArray versions = JsonParser.parseString(response.body()).getAsJsonArray();
            if (versions.isEmpty()) {
                plugin.getLogger().info("Update checker: project '" + MODRINTH_PROJECT_SLUG + "' found, but no version uploaded yet.");
                return;
            }

            String current = plugin.getDescription().getVersion();
            String newest = versions.get(0).getAsJsonObject().get("version_number").getAsString();

            if (isNewer(newest, current)) {
                latestKnownVersion = newest;
                versionsBehind = countVersionsBehind(versions, current);
                plugin.getLogger().info("Update checker: a new version is available: " + newest + " (you're on "
                        + current + ", " + behindText() + " behind). Get it at " + MODRINTH_PROJECT_URL);
                // Permission checks aren't thread-safe, so the loop runs on the server thread.
                Bukkit.getGlobalRegionScheduler().execute(plugin, () -> notifyOnlineEligiblePlayers(newest, current));
            } else {
                latestKnownVersion = null;
                versionsBehind = -1;
                plugin.getLogger().info("Update checker: no new version available.");
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Update checker: check failed (" + exception.getClass().getSimpleName()
                    + (exception.getMessage() != null ? ": " + exception.getMessage() : "") + ")");
        }
    }

    private String behindText() {
        return versionsBehind < 0 ? "an unknown number of versions" : versionsBehind + " version(s)";
    }

    private int countVersionsBehind(JsonArray versions, String current) {
        for (int i = 0; i < versions.size(); i++) {
            String versionNumber = versions.get(i).getAsJsonObject().get("version_number").getAsString();
            if (versionNumber.equals(current)) {
                return i;
            }
        }
        return -1;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // PlayerJoinEvent already runs on that player's own thread, so no
        // extra scheduler hop is needed here.
        Player player = event.getPlayer();
        String newest = latestKnownVersion;
        if (newest == null || !isEligible(player)) {
            return;
        }
        notifyPlayer(player, newest, plugin.getDescription().getVersion());
    }

    private boolean isEligible(Player player) {
        return player.isOp() || player.hasPermission("stonebackpack.admin");
    }

    private void notifyOnlineEligiblePlayers(String newest, String current) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isEligible(player)) {
                notifyPlayer(player, newest, current);
            }
        }
    }

    private void notifyPlayer(Player player, String newest, String current) {
        plugin.getMessageManager().sendRaw(player, "update.available", Map.of(
                "version", newest,
                "current", current,
                "behind", versionsBehind < 0
                        ? plugin.getMessageManager().raw("update.versions-behind-unknown", null)
                        : plugin.getMessageManager().raw("update.versions-behind", Map.of("count", String.valueOf(versionsBehind)))
        ));
    }

    public void notifyIfOutdated(CommandSender sender) {
        String newest = latestKnownVersion;
        if (newest == null) {
            return;
        }
        if (sender instanceof Player player) {
            player.getScheduler().run(plugin, task -> doNotifyIfOutdated(sender, newest), null);
        } else {
            doNotifyIfOutdated(sender, newest);
        }
    }

    private void doNotifyIfOutdated(CommandSender sender, String newest) {
        String current = plugin.getDescription().getVersion();
        plugin.getMessageManager().sendRaw(sender, "update.available", Map.of(
                "version", newest,
                "current", current,
                "behind", versionsBehind < 0
                        ? plugin.getMessageManager().raw("update.versions-behind-unknown", null)
                        : plugin.getMessageManager().raw("update.versions-behind", Map.of("count", String.valueOf(versionsBehind)))
        ));
    }

    private boolean isNewer(String remote, String current) {
        try {
            String[] remoteParts = remote.split("\\.");
            String[] currentParts = current.split("\\.");
            int length = Math.max(remoteParts.length, currentParts.length);
            for (int i = 0; i < length; i++) {
                int r = i < remoteParts.length ? parsePart(remoteParts[i]) : 0;
                int c = i < currentParts.length ? parsePart(currentParts[i]) : 0;
                if (r != c) {
                    return r > c;
                }
            }
            return false;
        } catch (Exception exception) {
            return false;
        }
    }

    private int parsePart(String part) {
        StringBuilder digits = new StringBuilder();
        for (char c : part.toCharArray()) {
            if (!Character.isDigit(c)) {
                break;
            }
            digits.append(c);
        }
        return digits.isEmpty() ? 0 : Integer.parseInt(digits.toString());
    }
}
