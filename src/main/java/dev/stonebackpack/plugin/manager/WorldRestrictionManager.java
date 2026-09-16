package dev.stonebackpack.plugin.manager;

import dev.stonebackpack.plugin.model.WorldMode;
import org.bukkit.World;

import java.util.Locale;

public class WorldRestrictionManager {
    private final ConfigManager configManager;

    public WorldRestrictionManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public boolean isAllowed(World world) {
        WorldMode mode = configManager.getWorldMode();
        if (mode == WorldMode.NONE) {
            return true;
        }
        boolean listed = configManager.getWorldList().contains(world.getName().toLowerCase(Locale.ROOT));
        return mode == WorldMode.WHITELIST ? listed : !listed;
    }
}

