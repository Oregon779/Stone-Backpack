package dev.stonebackpack.plugin;

import dev.stonebackpack.plugin.model.BackpackHolder;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLoadTest extends PluginTestBase {
    @Test
    void enablesAndOpensOwnBackpack() {
        PlayerMock player = server.addPlayer();
        assertTrue(plugin.isEnabled());

        player.performCommand("backpack");
        settle();

        assertInstanceOf(BackpackHolder.class, player.getOpenInventory().getTopInventory().getHolder(false));
    }
}
