package dev.stonebackpack.plugin.manager;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CooldownManager {
    private final ConcurrentHashMap<UUID, Long> lastUse = new ConcurrentHashMap<>();

    public long getRemainingSeconds(UUID playerId, int cooldownSeconds) {
        Long last = lastUse.get(playerId);
        if (last == null) {
            return 0;
        }
        long elapsedMillis = System.currentTimeMillis() - last;
        long cooldownMillis = TimeUnit.SECONDS.toMillis(cooldownSeconds);
        if (elapsedMillis >= cooldownMillis) {
            return 0;
        }
        return TimeUnit.MILLISECONDS.toSeconds(cooldownMillis - elapsedMillis) + 1;
    }

    public void trigger(UUID playerId) {
        lastUse.put(playerId, System.currentTimeMillis());
    }

    public void clear(UUID playerId) {
        lastUse.remove(playerId);
    }
}

