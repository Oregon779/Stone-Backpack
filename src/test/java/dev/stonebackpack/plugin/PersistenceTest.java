package dev.stonebackpack.plugin;

import dev.stonebackpack.plugin.model.BackpackHolder;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceTest extends PluginTestBase {

    @Test
    void closingSavesContentsWithoutLeavingTempFiles() {
        PlayerMock player = server.addPlayer();
        Inventory backpack = openOwnBackpack(player);
        backpack.setItem(0, new ItemStack(Material.DIAMOND, 5));
        player.closeInventory();
        settle();

        File file = playerDataFile(player.getUniqueId());
        ItemStack saved = savedItem(file, 0);
        assertNotNull(saved);
        assertEquals(Material.DIAMOND, saved.getType());
        assertEquals(5, saved.getAmount());
        assertFalse(new File(file.getPath() + ".tmp").exists());
    }

    @Test
    void rejoiningRightAfterQuitSeesLatestContents() {
        PlayerMock player = server.addPlayer();
        Inventory backpack = openOwnBackpack(player);
        backpack.setItem(3, new ItemStack(Material.EMERALD, 2));
        player.closeInventory();
        player.disconnect();
        player.reconnect();

        Inventory reopened = openOwnBackpack(player);

        assertNotNull(reopened.getItem(3));
        assertEquals(Material.EMERALD, reopened.getItem(3).getType());
        assertEquals(2, reopened.getItem(3).getAmount());
    }

    @Test
    void corruptFileIsMovedAsideInsteadOfOverwritten() throws Exception {
        PlayerMock player = server.addPlayer();
        File file = playerDataFile(player.getUniqueId());
        String corrupt = "rows: [unclosed\nitems: {{{\n";
        Files.writeString(file.toPath(), corrupt);

        Inventory backpack = openOwnBackpack(player);

        assertInstanceOf(BackpackHolder.class, backpack.getHolder(false));
        assertTrue(backpack.isEmpty());
        File[] aside = file.getParentFile().listFiles((dir, name) -> name.startsWith(file.getName() + ".corrupt-"));
        assertNotNull(aside);
        assertEquals(1, aside.length);
        assertEquals(corrupt, Files.readString(aside[0].toPath()));
    }

    @Test
    void offlineBackpackIsReloadedFromDiskAfterAdminClosesIt() throws Exception {
        PlayerMock owner = server.addPlayer("Owner");
        Inventory own = openOwnBackpack(owner);
        own.setItem(0, new ItemStack(Material.DIAMOND));
        owner.closeInventory();
        owner.disconnect();
        settle();

        PlayerMock admin = server.addPlayer("Admin");
        admin.setOp(true);
        admin.performCommand("backpack Owner");
        settle();
        assertEquals(Material.DIAMOND, admin.getOpenInventory().getTopInventory().getItem(0).getType());
        admin.closeInventory();
        settle();

        // If the closed backpack were still cached, reopening would ignore this edit.
        File file = playerDataFile(owner.getUniqueId());
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<Object> items = new ArrayList<>(yaml.getList("items"));
        items.set(0, new ItemStack(Material.GOLD_INGOT));
        yaml.set("items", items);
        yaml.save(file);

        admin.performCommand("backpack Owner");
        settle();
        assertEquals(Material.GOLD_INGOT, admin.getOpenInventory().getTopInventory().getItem(0).getType());
    }

    @Test
    void disablingClosesOpenBackpacksAndSavesThem() {
        PlayerMock player = server.addPlayer();
        Inventory backpack = openOwnBackpack(player);
        backpack.setItem(0, new ItemStack(Material.NETHERITE_INGOT));

        server.getPluginManager().disablePlugin(plugin);

        assertNotEquals(backpack, player.getOpenInventory().getTopInventory());
        ItemStack saved = savedItem(playerDataFile(player.getUniqueId()), 0);
        assertNotNull(saved);
        assertEquals(Material.NETHERITE_INGOT, saved.getType());
    }

    @Test
    void reloadCommandDoesNotRegisterListenersTwice() {
        int before = org.bukkit.event.HandlerList.getRegisteredListeners(plugin).size();
        PlayerMock admin = server.addPlayer();
        admin.setOp(true);

        admin.performCommand("stonebackpack reload");
        admin.performCommand("stonebackpack reload");

        assertEquals(before, org.bukkit.event.HandlerList.getRegisteredListeners(plugin).size());
    }
}
