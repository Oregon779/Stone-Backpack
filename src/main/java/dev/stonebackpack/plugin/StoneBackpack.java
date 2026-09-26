package dev.stonebackpack.plugin;

import dev.stonebackpack.plugin.command.BackpackCommand;
import dev.stonebackpack.plugin.command.PlayerLookup;
import dev.stonebackpack.plugin.command.StoneBackpackCommand;
import dev.stonebackpack.plugin.listener.BackpackInventoryListener;
import dev.stonebackpack.plugin.listener.BackpackItemListener;
import dev.stonebackpack.plugin.listener.PlayerJoinListener;
import dev.stonebackpack.plugin.listener.PlayerQuitListener;
import dev.stonebackpack.plugin.manager.BackpackManager;
import dev.stonebackpack.plugin.manager.BackupManager;
import dev.stonebackpack.plugin.manager.ConfigManager;
import dev.stonebackpack.plugin.manager.CooldownManager;
import dev.stonebackpack.plugin.manager.DataManager;
import dev.stonebackpack.plugin.manager.ItemManager;
import dev.stonebackpack.plugin.manager.MessageManager;
import dev.stonebackpack.plugin.manager.UpdateChecker;
import dev.stonebackpack.plugin.manager.WorldRestrictionManager;
import dev.stonebackpack.plugin.menu.BackupMenu;
import dev.stonebackpack.plugin.model.BackpackHolder;
import dev.stonebackpack.plugin.model.BackupMenuHolder;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.java.JavaPlugin;

public class StoneBackpack extends JavaPlugin {
    private ConfigManager configManager;
    private MessageManager messageManager;
    private DataManager dataManager;
    private CooldownManager cooldownManager;
    private WorldRestrictionManager worldRestrictionManager;
    private ItemManager itemManager;
    private BackpackManager backpackManager;
    private BackupManager backupManager;
    private BackupMenu backupMenu;
    private UpdateChecker updateChecker;
    private ScheduledTask autosaveTask;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.load();

        messageManager = new MessageManager(this);
        messageManager.load(configManager.getLanguage());

        dataManager = new DataManager(this);
        dataManager.init();
        cooldownManager = new CooldownManager();
        worldRestrictionManager = new WorldRestrictionManager(configManager);
        itemManager = new ItemManager(this);
        backpackManager = new BackpackManager(this);
        backupManager = new BackupManager(this);
        backupMenu = new BackupMenu(this);

        // Warms the O(1) player-name lookup cache off the main thread at
        // startup instead of letting the first /backpack <name>,
        // /stonebackpack give or /stonebackpack inspect command pay for an
        // O(n) scan over the server's entire historical player list.
        PlayerLookup.warmUpAsync(this);

        registerCommand("backpack", new BackpackCommand(this));
        registerCommand("stonebackpack", new StoneBackpackCommand(this));

        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BackpackItemListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BackpackInventoryListener(this), this);
        Bukkit.getPluginManager().registerEvents(backupMenu, this);

        startAutosaveTask();
        backupManager.start();
        updateChecker = new UpdateChecker(this);
        Bukkit.getPluginManager().registerEvents(updateChecker, this);
        updateChecker.start();

        getLogger().info("StoneBackpack v" + getDescription().getVersion() + " enabled (language: " + configManager.getLanguage() + ").");
    }

    @Override
    public void onDisable() {
        if (updateChecker != null) {
            updateChecker.shutdown();
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (backupManager != null) {
            backupManager.stop();
        }
        if (backpackManager != null) {
            // After a /reload, a still-open view would belong to the old plugin
            // instance: nothing protects or saves it any more, so items taken
            // out of it would also still be in the saved file (a dupe).
            closePluginViews();
            backpackManager.saveAllNow();
        }
        if (dataManager != null) {
            dataManager.shutdown();
        }
        getLogger().info("StoneBackpack has been disabled.");
    }

    private void closePluginViews() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryView view = player.getOpenInventory();
            if (view.getType() == InventoryType.CRAFTING || view.getType() == InventoryType.CREATIVE) {
                continue;
            }
            InventoryHolder holder = view.getTopInventory().getHolder(false);
            if (holder instanceof BackpackHolder || holder instanceof BackupMenuHolder) {
                player.closeInventory();
            }
        }
    }

    public void reload() {
        configManager.reload();
        messageManager.reload(configManager.getLanguage());
        startAutosaveTask();
        backupManager.start();
        updateChecker.start();
    }

    /**
     * The global region scheduler (not Bukkit.getScheduler().runTaskTimer)
     * is the Folia-safe primitive for a periodic task that isn't tied to
     * any single player or location - BackpackManager.saveAll() itself
     * dispatches the actual per-player work onto the correct thread once
     * this timer fires.
     */
    private void startAutosaveTask() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        long autosaveSeconds = configManager.getAutosaveIntervalMinutes() * 60L;
        autosaveTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this,
                task -> backpackManager.saveAll(), autosaveSeconds * 20L, autosaveSeconds * 20L);
    }

    private void registerCommand(String name, Object executorAndCompleter) {
        var command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml, skipping registration.");
            return;
        }
        if (executorAndCompleter instanceof org.bukkit.command.CommandExecutor executor) {
            command.setExecutor(executor);
        }
        if (executorAndCompleter instanceof org.bukkit.command.TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public WorldRestrictionManager getWorldRestrictionManager() {
        return worldRestrictionManager;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public BackpackManager getBackpackManager() {
        return backpackManager;
    }

    public BackupManager getBackupManager() {
        return backupManager;
    }

    public BackupMenu getBackupMenu() {
        return backupMenu;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }
}
