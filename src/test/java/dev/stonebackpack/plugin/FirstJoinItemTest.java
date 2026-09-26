package dev.stonebackpack.plugin;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FirstJoinItemTest extends PluginTestBase {

    private int backpackItems(PlayerMock player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (plugin.getItemManager().isBackpackItem(item)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    @Test
    void itemIsGivenOnceEvenIfBackpackIsNeverOpened() {
        editConfig(yaml -> {
            yaml.set("backpack.item.enabled", true);
            yaml.set("backpack.item.give-on-first-join", true);
        });
        PlayerMock player = server.addPlayer();
        server.getScheduler().performTicks(25);
        assertEquals(1, backpackItems(player));

        player.disconnect();
        player.reconnect();
        server.getScheduler().performTicks(25);

        assertEquals(1, backpackItems(player));
    }
}
