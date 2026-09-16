package dev.stonebackpack.plugin.model;

public enum WorldMode {
    NONE,

    WHITELIST,

    BLACKLIST;

    public static WorldMode fromConfig(String raw, java.util.logging.Logger logger) {
        if (raw == null) {
            return NONE;
        }
        try {
            return WorldMode.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            if (logger != null) {
                logger.warning("Unknown worlds.mode '" + raw + "' in config.yml, falling back to NONE.");
            }
            return NONE;
        }
    }
}

