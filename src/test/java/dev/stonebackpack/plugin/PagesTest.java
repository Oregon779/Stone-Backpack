package dev.stonebackpack.plugin;

import dev.stonebackpack.plugin.model.BackpackHolder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagesTest extends PluginTestBase {
    private static final int NEXT = 26;
    private static final int PREVIOUS = 18;

    private PlayerMock playerWithPages(String name, int pages) {
        PlayerMock player = server.addPlayer(name);
        player.addAttachment(plugin, "stonebackpack.pages." + pages, true);
        return player;
    }

    private InventoryClickEvent click(PlayerMock player, int slot, ClickType type, InventoryAction action) {
        InventoryClickEvent event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                slot, type, action);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private Inventory turn(PlayerMock player, int slot) {
        assertTrue(click(player, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled());
        server.getScheduler().performTicks(1);
        return player.getOpenInventory().getTopInventory();
    }

    private static BackpackHolder holder(Inventory inventory) {
        return (BackpackHolder) inventory.getHolder(false);
    }

    private boolean isButton(ItemStack item) {
        return plugin.getItemManager().isPageButton(item);
    }

    private void writeBackpack(PlayerMock player, int pages, ItemStack[] items) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("rows", 3);
        if (pages > 0) {
            yaml.set("pages", pages);
        }
        yaml.set("items", Arrays.asList(items));
        yaml.save(playerDataFile(player.getUniqueId()));
    }

    @Test
    void withoutPagePermissionThereIsOnePageAndNoButton() {
        PlayerMock player = server.addPlayer();
        Inventory page = openOwnBackpack(player);

        assertEquals(1, holder(page).getPageCount());
        assertNull(page.getItem(NEXT));
        player.setItemOnCursor(new ItemStack(Material.DIAMOND));
        assertFalse(click(player, NEXT, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled());
    }

    @Test
    void buttonsInTheBottomCornersSwitchBetweenPages() {
        PlayerMock player = playerWithPages("Paged", 3);

        Inventory first = openOwnBackpack(player);
        assertEquals(3, holder(first).getPageCount());
        assertTrue(isButton(first.getItem(NEXT)));
        assertNull(first.getItem(PREVIOUS));

        Inventory second = turn(player, NEXT);
        assertEquals(1, holder(second).getPage());
        assertTrue(isButton(second.getItem(PREVIOUS)));
        assertTrue(isButton(second.getItem(NEXT)));
        String title = PlainTextComponentSerializer.plainText().serialize(server.titleOf(second));
        assertTrue(title.endsWith("(2/3)"), title);
        String singleTitle = PlainTextComponentSerializer.plainText().serialize(server.titleOf(openOwnBackpack(server.addPlayer("Single"))));
        assertFalse(singleTitle.contains("/"), singleTitle);

        Inventory third = turn(player, NEXT);
        assertEquals(2, holder(third).getPage());
        assertNull(third.getItem(NEXT));

        assertEquals(second, turn(player, PREVIOUS));
        assertEquals(first, turn(player, PREVIOUS));
    }

    @Test
    void oldBackpackKeepsEveryItemAndTheOneUnderTheButtonMovesToPageTwo() throws Exception {
        PlayerMock player = playerWithPages("Veteran", 2);
        ItemStack[] items = new ItemStack[27];
        for (int i = 0; i < items.length; i++) {
            items[i] = new ItemStack(Material.DIRT, i + 1);
        }
        writeBackpack(player, 0, items);

        Inventory first = openOwnBackpack(player);
        for (int slot = 0; slot < NEXT; slot++) {
            assertEquals(slot + 1, first.getItem(slot).getAmount());
        }
        assertTrue(isButton(first.getItem(NEXT)));
        Inventory second = turn(player, NEXT);
        assertEquals(Material.DIRT, second.getItem(0).getType());
        assertEquals(27, second.getItem(0).getAmount());

        player.closeInventory();
        settle();
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(playerDataFile(player.getUniqueId()));
        assertEquals(2, saved.getInt("pages"));
        List<?> savedItems = saved.getList("items");
        assertEquals(54, savedItems.size());
        int total = 0;
        for (Object element : savedItems) {
            if (element instanceof ItemStack item) {
                assertEquals(Material.DIRT, item.getType(), "a page button was saved");
                total += item.getAmount();
            }
        }
        assertEquals(27 * 28 / 2, total);
    }

    @Test
    void buttonsCanNeitherBeTakenNorCoveredEvenWithTheBlocklistBypass() {
        PlayerMock player = playerWithPages("Admin", 2);
        player.setOp(true);
        openOwnBackpack(player);

        assertTrue(click(player, NEXT, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled());
        InventoryClickEvent hotbar = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                NEXT, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0);
        server.getPluginManager().callEvent(hotbar);
        assertTrue(hotbar.isCancelled());

        ItemStack diamonds = new ItemStack(Material.DIAMOND, 2);
        InventoryDragEvent drag = new InventoryDragEvent(player.getOpenInventory(), null, diamonds, false,
                Map.of(25, new ItemStack(Material.DIAMOND), NEXT, new ItemStack(Material.DIAMOND)));
        server.getPluginManager().callEvent(drag);
        assertTrue(drag.isCancelled());
    }

    @Test
    void itemOnTheCursorComesAlongToTheNextPage() {
        PlayerMock player = playerWithPages("Mover", 2);
        openOwnBackpack(player);
        // Like Paper: closing a view hands the cursor item back to the inventory.
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onClose(InventoryCloseEvent event) {
                ItemStack cursor = event.getPlayer().getItemOnCursor();
                if (!cursor.isEmpty()) {
                    event.getPlayer().getInventory().addItem(cursor);
                    event.getPlayer().setItemOnCursor(null);
                }
            }
        }, plugin);
        player.setItemOnCursor(new ItemStack(Material.EMERALD, 4));

        Inventory second = turn(player, NEXT);

        assertEquals(1, holder(second).getPage());
        assertEquals(Material.EMERALD, player.getItemOnCursor().getType());
        assertEquals(4, player.getItemOnCursor().getAmount());
        assertFalse(player.getInventory().contains(Material.EMERALD));
    }

    @Test
    void adminTurningPagesOfAnOfflineBackpackKeepsEditsOnEveryPage() {
        PlayerMock owner = playerWithPages("Owner", 2);
        openOwnBackpack(owner).setItem(0, new ItemStack(Material.DIAMOND));
        owner.closeInventory();
        owner.disconnect();
        settle();

        PlayerMock admin = server.addPlayer("Admin");
        admin.setOp(true);
        admin.performCommand("backpack Owner");
        settle();
        Inventory second = turn(admin, NEXT);
        assertEquals(1, holder(second).getPage());
        second.setItem(4, new ItemStack(Material.GOLD_INGOT));
        admin.closeInventory();
        settle();

        File file = playerDataFile(owner.getUniqueId());
        assertEquals(Material.DIAMOND, savedItem(file, 0).getType());
        ItemStack gold = savedItem(file, 27 + 4);
        assertNotNull(gold, "turning the page unloaded the backpack, so the edit on page 2 was lost");
        assertEquals(Material.GOLD_INGOT, gold.getType());
    }

    @Test
    void pagesBeyondThePermissionStayWhileTheyHoldItems() throws Exception {
        PlayerMock keeper = server.addPlayer("Keeper");
        ItemStack[] items = new ItemStack[81];
        items[54 + 3] = new ItemStack(Material.NETHERITE_INGOT);
        writeBackpack(keeper, 3, items);
        assertEquals(3, holder(openOwnBackpack(keeper)).getPageCount());

        PlayerMock emptied = server.addPlayer("Emptied");
        ItemStack[] onlyFirst = new ItemStack[81];
        onlyFirst[0] = new ItemStack(Material.DIAMOND);
        writeBackpack(emptied, 3, onlyFirst);
        Inventory page = openOwnBackpack(emptied);
        assertEquals(1, holder(page).getPageCount());
        assertEquals(Material.DIAMOND, page.getItem(0).getType());
    }

    @Test
    void newPagePermissionMovesTheItemUnderTheButtonToTheNewPage() {
        PlayerMock player = server.addPlayer();
        Inventory first = openOwnBackpack(player);
        first.setItem(NEXT, new ItemStack(Material.EMERALD, 3));
        player.closeInventory();

        player.addAttachment(plugin, "stonebackpack.pages.2", true);
        first = openOwnBackpack(player);
        assertEquals(2, holder(first).getPageCount());
        assertTrue(isButton(first.getItem(NEXT)));
        Inventory second = turn(player, NEXT);
        assertEquals(Material.EMERALD, second.getItem(0).getType());
        assertEquals(3, second.getItem(0).getAmount());
    }

    @Test
    void backupsContainEveryPageButNoButtons() {
        setConfig("backups.interval-seconds", 10);
        PlayerMock player = playerWithPages("Saver", 2);
        openOwnBackpack(player);
        turn(player, NEXT).setItem(2, new ItemStack(Material.EMERALD));

        server.getScheduler().performTicks(10 * 20);
        settle();

        File backup = backupFile(player.getUniqueId());
        assertEquals(Material.EMERALD, savedItem(backup, 27 + 2).getType());
        assertNull(savedItem(backup, NEXT));
        assertNull(savedItem(backup, 27 + PREVIOUS));
    }
}
