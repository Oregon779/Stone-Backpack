package dev.stonebackpack.plugin.manager;

import dev.stonebackpack.plugin.config.ConfigMigrations;
import dev.stonebackpack.plugin.config.ConfigUpdater;
import dev.stonebackpack.plugin.model.BackpackLayout;
import dev.stonebackpack.plugin.model.WorldMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;

public class ConfigManager {
    // Server owners pick their own row/page -> permission entries there; ones
    // they removed must not come back on the next update.
    private static final ConfigUpdater.Options UPDATE_OPTIONS = new ConfigUpdater.Options(
            "config-version", ConfigMigrations.CONFIG, Set.of("backpack.size-permissions", "backpack.page-permissions"));

    private final Plugin plugin;
    private File configFile;
    private YamlConfiguration config;

    private Map<String, Integer> sizePermissionsCache = Collections.emptyMap();
    private Map<String, Integer> pagePermissionsCache = Collections.emptyMap();
    private Set<Material> blockedItemsCache = EnumSet.noneOf(Material.class);
    private Set<String> worldListCache = Collections.emptySet();
    private Material itemMaterialCache = Material.BUNDLE;
    private Material pageButtonMaterialCache = Material.ARROW;
    private Sound openSoundCache = Sound.BLOCK_ENDER_CHEST_OPEN;
    private Sound closeSoundCache = Sound.BLOCK_ENDER_CHEST_CLOSE;
    private Sound pageSoundCache = Sound.ITEM_BOOK_PAGE_TURN;
    private WorldMode worldModeCache = WorldMode.NONE;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
            plugin.getLogger().info("Created default config.yml.");
        } else {
            ConfigUpdater.update(plugin, "config.yml", configFile, UPDATE_OPTIONS);
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        recomputeCaches();
    }

    public void reload() {
        load();
    }

    public YamlConfiguration raw() {
        return config;
    }

    private void recomputeCaches() {
        sizePermissionsCache = computeTierPermissions("backpack.size-permissions", "row count", this::clampRows);
        pagePermissionsCache = computeTierPermissions("backpack.page-permissions", "page count", this::clampPages);
        blockedItemsCache = computeBlockedItems();
        worldListCache = computeWorldList();
        itemMaterialCache = computeItemMaterial();
        pageButtonMaterialCache = computePageButtonMaterial();
        openSoundCache = computeSound("backpack.sounds.open", Sound.BLOCK_ENDER_CHEST_OPEN);
        closeSoundCache = computeSound("backpack.sounds.close", Sound.BLOCK_ENDER_CHEST_CLOSE);
        pageSoundCache = computeSound("backpack.sounds.page", Sound.ITEM_BOOK_PAGE_TURN);
        worldModeCache = WorldMode.fromConfig(config.getString("worlds.mode"), plugin.getLogger());
    }

    public String getLanguage() {
        return config.getString("language", "en");
    }

    public String getBackpackTitleTemplate() {
        return config.getString("backpack.title", "&8{player}'s Backpack");
    }

    public int getDefaultRows() {
        return clampRows(config.getInt("backpack.default-rows", 3));
    }

    public Map<String, Integer> getSizePermissions() {
        return sizePermissionsCache;
    }

    public int getDefaultPages() {
        return clampPages(config.getInt("backpack.default-pages", 1));
    }

    public Map<String, Integer> getPagePermissions() {
        return pagePermissionsCache;
    }

    // Permission -> amount, biggest amount first.
    private Map<String, Integer> computeTierPermissions(String path, String label, IntUnaryOperator clamp) {
        Map<String, Integer> result = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section != null) {
            for (String amountKey : section.getKeys(false)) {
                String permission = section.getString(amountKey);
                if (permission == null || permission.isBlank()) {
                    continue;
                }
                try {
                    result.put(permission.trim(), clamp.applyAsInt(Integer.parseInt(amountKey.trim())));
                } catch (NumberFormatException exception) {
                    plugin.getLogger().warning("Invalid " + label + " '" + amountKey + "' in " + path + ", ignoring.");
                }
            }
        }
        Map<String, Integer> sorted = new LinkedHashMap<>();
        result.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(sorted);
    }

    private int clampRows(int rows) {
        return Math.max(1, Math.min(6, rows));
    }

    private int clampPages(int pages) {
        return Math.max(1, Math.min(BackpackLayout.MAX_PAGES, pages));
    }

    public String getPageTitleTemplate() {
        return config.getString("backpack.page-title", "");
    }

    public Material getPageButtonMaterial() {
        return pageButtonMaterialCache;
    }

    private Material computePageButtonMaterial() {
        String name = config.getString("backpack.page-button-material", "ARROW");
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isItem() || material.isAir()) {
            plugin.getLogger().warning("Unknown item '" + name + "' in backpack.page-button-material, falling back to ARROW.");
            return Material.ARROW;
        }
        return material;
    }

    public Set<Material> getBlockedItems() {
        return blockedItemsCache;
    }

    private Set<Material> computeBlockedItems() {
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (String name : config.getStringList("backpack.blocked-items")) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                materials.add(material);
            } else {
                plugin.getLogger().warning("Unknown material '" + name + "' in backpack.blocked-items, ignoring.");
            }
        }
        return materials;
    }

    public boolean isItemEnabled() {
        return config.getBoolean("backpack.item.enabled", false);
    }

    public Material getItemMaterial() {
        return itemMaterialCache;
    }

    private Material computeItemMaterial() {
        String name = config.getString("backpack.item.material", "BUNDLE");
        Material material = Material.matchMaterial(name);
        if (material == null) {
            plugin.getLogger().warning("Unknown material '" + name + "' in backpack.item.material, falling back to BUNDLE.");
            return Material.BUNDLE;
        }
        return material;
    }

    public String getItemName() {
        return config.getString("backpack.item.name", "&6Backpack");
    }

    public List<String> getItemLore() {
        return config.getStringList("backpack.item.lore");
    }

    public int getItemCustomModelData() {
        return config.getInt("backpack.item.custom-model-data", 0);
    }

    public boolean isGiveOnFirstJoin() {
        return config.getBoolean("backpack.item.give-on-first-join", false);
    }

    private Sound computeSound(String path, Sound fallback) {
        String name = config.getString(path);
        if (name == null) {
            return fallback;
        }
        try {
            return Sound.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Unknown sound '" + name + "' at " + path + ", ignoring.");
            return fallback;
        }
    }

    public Sound getOpenSound() {
        return openSoundCache;
    }

    public Sound getCloseSound() {
        return closeSoundCache;
    }

    public Sound getPageSound() {
        return pageSoundCache;
    }

    public float getSoundVolume() {
        return (float) config.getDouble("backpack.sounds.volume", 1.0);
    }

    public float getSoundPitch() {
        return (float) config.getDouble("backpack.sounds.pitch", 1.0);
    }

    public int getCommandCooldownSeconds() {
        return config.getInt("command.cooldown-seconds", 2);
    }

    public WorldMode getWorldMode() {
        return worldModeCache;
    }

    public Set<String> getWorldList() {
        return worldListCache;
    }

    private Set<String> computeWorldList() {
        Set<String> names = new LinkedHashSet<>();
        for (String name : config.getStringList("worlds.list")) {
            names.add(name.toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableSet(names);
    }

    public int getAutosaveIntervalMinutes() {
        return Math.max(1, config.getInt("data.autosave-interval-minutes", 5));
    }

    public boolean isSaveOnClose() {
        return config.getBoolean("data.save-on-close", true);
    }

    public boolean isSaveOnQuit() {
        return config.getBoolean("data.save-on-quit", true);
    }

    public boolean isBackupsEnabled() {
        return config.getBoolean("backups.enabled", true);
    }

    public int getBackupIntervalSeconds() {
        return Math.max(10, config.getInt("backups.interval-seconds", 60));
    }

    public boolean isUpdateCheckerEnabled() {
        return config.getBoolean("update-checker.enabled", true);
    }

    public int getUpdateCheckIntervalMinutes() {
        return Math.max(5, config.getInt("update-checker.check-interval-minutes", 60));
    }
}

