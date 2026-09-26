package dev.stonebackpack.plugin;

import dev.stonebackpack.plugin.model.BackupMenuHolder;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupTest extends PluginTestBase {
    private static final int INTERVAL_TICKS = 10 * 20;

    @BeforeEach
    void shortInterval() {
        setConfig("backups.interval-seconds", 10);
    }

    private void runBackup() {
        server.getScheduler().performTicks(INTERVAL_TICKS);
        settle();
    }

    @Test
    void backsUpOnlinePlayersWhoHaveNotOpenedTheirBackpack() throws Exception {
        PlayerMock player = server.addPlayer();
        // Saved in an earlier server session; not opened since joining.
        YamlConfiguration stored = new YamlConfiguration();
        stored.set("rows", 3);
        ItemStack[] items = new ItemStack[27];
        items[0] = new ItemStack(Material.DIAMOND, 3);
        stored.set("items", java.util.Arrays.asList(items));
        stored.save(playerDataFile(player.getUniqueId()));

        runBackup();

        YamlConfiguration backup = YamlConfiguration.loadConfiguration(backupFile(player.getUniqueId()));
        assertTrue(backup.getLong("timestamp") > 0);
        ItemStack saved = savedItem(backupFile(player.getUniqueId()), 0);
        assertNotNull(saved);
        assertEquals(Material.DIAMOND, saved.getType());
        assertEquals(3, saved.getAmount());
    }

    @Test
    void backsUpLiveContentsOfAnOpenBackpack() {
        PlayerMock player = server.addPlayer();
        Inventory backpack = openOwnBackpack(player);
        backpack.setItem(5, new ItemStack(Material.EMERALD));

        runBackup();

        ItemStack saved = savedItem(backupFile(player.getUniqueId()), 5);
        assertNotNull(saved);
        assertEquals(Material.EMERALD, saved.getType());
    }

    @Test
    void restoreItemsArePackedIntoShulkerBoxesOf27() {
        ItemStack[] contents = new ItemStack[54];
        for (int i = 0; i < 30; i++) {
            contents[i] = new ItemStack(Material.DIRT, i + 1);
        }
        contents[40] = new ItemStack(Material.SHULKER_BOX);

        List<ItemStack> items = plugin.getBackupManager().createRestoreItems(contents, "Tester");

        assertEquals(3, items.size());
        assertEquals(27, items.get(0).getData(DataComponentTypes.CONTAINER).contents().size());
        ItemContainerContents second = items.get(1).getData(DataComponentTypes.CONTAINER);
        assertEquals(3, second.contents().size());
        assertEquals(30, second.contents().get(2).getAmount());
        assertEquals(Material.SHULKER_BOX, items.get(2).getType());
        assertTrue(items.get(2).getData(DataComponentTypes.CONTAINER) == null);
    }

    @Test
    void backupMenuIsReadOnlyAndCopyGivesShulkerBox() {
        PlayerMock owner = server.addPlayer("Owner");
        Inventory backpack = openOwnBackpack(owner);
        backpack.setItem(0, new ItemStack(Material.GOLD_INGOT, 7));
        runBackup();

        PlayerMock admin = server.addPlayer("Admin");
        admin.setOp(true);
        admin.performCommand("stonebackpack backups");
        settle();
        Inventory list = admin.getOpenInventory().getTopInventory();
        assertInstanceOf(BackupMenuHolder.class, list.getHolder(false));
        int ownerSlot = list.getItem(0).getType() == Material.PLAYER_HEAD
                && ((BackupMenuHolder) list.getHolder(false)).targetAt(0).id().equals(owner.getUniqueId()) ? 0 : 1;

        assertTrue(click(admin, ownerSlot).isCancelled());
        settle();
        Inventory view = admin.getOpenInventory().getTopInventory();
        BackupMenuHolder holder = (BackupMenuHolder) view.getHolder(false);
        assertEquals(BackupMenuHolder.View.BACKUP, holder.getView());
        assertEquals(Material.GOLD_INGOT, view.getItem(0).getType());
        assertTrue(click(admin, 0).isCancelled());

        click(admin, 53);
        settle();

        // Box contents are covered by restoreItemsArePackedIntoShulkerBoxesOf27:
        // MockBukkit drops data components when addItem copies the stack.
        ItemStack box = admin.getInventory().getItem(0);
        assertNotNull(box);
        assertEquals(Material.SHULKER_BOX, box.getType());
        String name = PlainTextComponentSerializer.plainText().serialize(box.getItemMeta().displayName());
        assertTrue(name.contains("Owner"), name);
    }

    private InventoryClickEvent click(PlayerMock player, int rawSlot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, rawSlot,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
        return event;
    }
}
