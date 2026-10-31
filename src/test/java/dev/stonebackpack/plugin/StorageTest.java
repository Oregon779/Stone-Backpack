package dev.stonebackpack.plugin;

import dev.stonebackpack.plugin.model.StorageMenuHolder;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageTest extends PluginTestBase {
    private static final int GIVE = 52;
    private static final int TAKE_ALL = 53;

    private PlayerMock admin;

    @BeforeEach
    void addAdmin() {
        admin = server.addPlayer("Admin");
        admin.setOp(true);
    }

    private void store(UUID playerId, String name, ItemStack... items) {
        plugin.getDataManager().addStoredAsync(playerId, name, List.of(items));
        settle();
    }

    private List<ItemStack> stored(UUID playerId) {
        File file = new File(plugin.getDataFolder(), "storage/" + playerId + ".yml");
        if (!file.exists()) {
            return List.of();
        }
        List<ItemStack> items = new ArrayList<>();
        for (Object element : YamlConfiguration.loadConfiguration(file).getList("items")) {
            items.add((ItemStack) element);
        }
        return items;
    }

    private InventoryClickEvent click(PlayerMock player, int slot) {
        InventoryClickEvent event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private Inventory clickAndSettle(PlayerMock player, int slot) {
        assertTrue(click(player, slot).isCancelled());
        settle();
        return player.getOpenInventory().getTopInventory();
    }

    private Inventory openStorage() {
        admin.performCommand("stonebackpack storage");
        settle();
        return admin.getOpenInventory().getTopInventory();
    }

    private static boolean storageMenuOpen(PlayerMock player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        return top != null && top.getHolder(false) instanceof StorageMenuHolder;
    }

    private static StorageMenuHolder holder(Inventory inventory) {
        return assertInstanceOf(StorageMenuHolder.class, inventory.getHolder(false));
    }

    private static int amountOf(PlayerMock player, Material material) {
        int amount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                amount += item.getAmount();
            }
        }
        return amount;
    }

    @Test
    void withoutStoredItemsTheMenuSaysSo() {
        openStorage();

        assertFalse(storageMenuOpen(admin));
        assertTrue(drainMessages(admin).stream().anyMatch(message -> message.contains("No items are stored")));
    }

    @Test
    void playersWithoutAdminCannotOpenIt() {
        PlayerMock player = server.addPlayer("Player");
        store(player.getUniqueId(), "Player", new ItemStack(Material.DIAMOND));

        player.performCommand("stonebackpack storage");
        settle();

        assertFalse(storageMenuOpen(player));
    }

    @Test
    void listShowsEveryPlayerWithStoredItemsAlsoOfflineOnes() {
        PlayerMock online = server.addPlayer("Online");
        UUID offline = UUID.randomUUID();
        store(online.getUniqueId(), "Online", new ItemStack(Material.DIAMOND));
        store(offline, "Gone", new ItemStack(Material.EMERALD), new ItemStack(Material.GOLD_INGOT));

        Inventory list = openStorage();

        StorageMenuHolder holder = holder(list);
        assertEquals(StorageMenuHolder.View.PLAYERS, holder.getView());
        assertEquals(Material.PLAYER_HEAD, list.getItem(0).getType());
        assertEquals(Material.PLAYER_HEAD, list.getItem(1).getType());
        int gone = holder.entryAt(0).playerId().equals(offline) ? 0 : 1;
        assertEquals("Gone", holder.entryAt(gone).name());
        assertEquals(2, holder.entryAt(gone).stacks());
    }

    @Test
    void takingAnItemGivesItToTheAdminExactlyOnceEvenOnADoubleClick() {
        PlayerMock owner = server.addPlayer("Owner");
        store(owner.getUniqueId(), "Owner", new ItemStack(Material.DIAMOND, 5), new ItemStack(Material.EMERALD, 2));
        openStorage();
        Inventory items = clickAndSettle(admin, 0);
        assertEquals(StorageMenuHolder.View.ITEMS, holder(items).getView());
        assertEquals(Material.DIAMOND, items.getItem(0).getType());

        click(admin, 0);
        click(admin, 0);
        settle();

        assertEquals(5, amountOf(admin, Material.DIAMOND));
        assertEquals(0, amountOf(admin, Material.EMERALD));
        assertEquals(List.of(new ItemStack(Material.EMERALD, 2)), stored(owner.getUniqueId()));
        Inventory after = admin.getOpenInventory().getTopInventory();
        assertEquals(Material.EMERALD, after.getItem(0).getType());
        assertNull(after.getItem(1));
    }

    @Test
    void takeAllGivesShulkerBoxesAndEmptiesTheStorage() {
        PlayerMock owner = server.addPlayer("Owner");
        ItemStack[] items = new ItemStack[30];
        for (int i = 0; i < items.length; i++) {
            items[i] = new ItemStack(Material.DIRT, i + 1);
        }
        store(owner.getUniqueId(), "Owner", items);
        openStorage();
        clickAndSettle(admin, 0);

        clickAndSettle(admin, TAKE_ALL);

        assertEquals(2, amountOf(admin, Material.SHULKER_BOX));
        assertTrue(stored(owner.getUniqueId()).isEmpty());
        assertFalse(storageMenuOpen(admin));
    }

    @Test
    void givingBackFillsTheOwnersInventoryAndKeepsWhatDoesNotFit() {
        PlayerMock owner = server.addPlayer("Owner");
        // Everything full but one slot, so the diamonds fit and the emeralds don't.
        for (int slot = 0; slot < owner.getInventory().getSize(); slot++) {
            if (slot != 8) {
                owner.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
            }
        }
        store(owner.getUniqueId(), "Owner", new ItemStack(Material.DIAMOND, 5), new ItemStack(Material.EMERALD, 2));
        openStorage();
        clickAndSettle(admin, 0);
        drainMessages(owner);

        clickAndSettle(admin, GIVE);

        assertEquals(5, amountOf(owner, Material.DIAMOND));
        assertEquals(0, amountOf(owner, Material.EMERALD));
        assertEquals(List.of(new ItemStack(Material.EMERALD, 2)), stored(owner.getUniqueId()));
        assertTrue(drainMessages(owner).stream().anyMatch(message -> message.contains("gave you back 1")));
        assertEquals(Material.EMERALD, admin.getOpenInventory().getTopInventory().getItem(0).getType());
    }

    @Test
    void givingBackToAnOfflinePlayerKeepsEverythingStored() {
        UUID gone = UUID.randomUUID();
        store(gone, "Gone", new ItemStack(Material.DIAMOND, 5));
        openStorage();
        clickAndSettle(admin, 0);
        drainMessages(admin);

        clickAndSettle(admin, GIVE);

        assertEquals(List.of(new ItemStack(Material.DIAMOND, 5)), stored(gone));
        assertTrue(drainMessages(admin).stream().anyMatch(message -> message.contains("offline")));
    }

    @Test
    void itemGoesBackIntoTheStorageWhenTheAdminLeavesDuringATake() {
        PlayerMock owner = server.addPlayer("Owner");
        store(owner.getUniqueId(), "Owner", new ItemStack(Material.DIAMOND, 5));
        openStorage();
        clickAndSettle(admin, 0);

        click(admin, 0);
        server.getScheduler().performTicks(1);
        admin.disconnect();
        settle();

        assertEquals(List.of(new ItemStack(Material.DIAMOND, 5)), stored(owner.getUniqueId()));
    }

    @Test
    void aTakeForAnItemThatIsNoLongerThereHandsOutNothing() {
        UUID owner = UUID.randomUUID();
        ItemStack diamonds = new ItemStack(Material.DIAMOND, 5);
        store(owner, "Owner", diamonds, new ItemStack(Material.EMERALD));
        AtomicReference<ItemStack> first = new AtomicReference<>();
        AtomicReference<ItemStack> second = new AtomicReference<>();

        plugin.getDataManager().takeStoredAsync(owner, 0, diamonds, (taken, remaining) -> first.set(taken));
        plugin.getDataManager().takeStoredAsync(owner, 0, diamonds, (taken, remaining) -> second.set(taken));
        settle();

        assertEquals(diamonds, first.get());
        assertNull(second.get());
        assertEquals(List.of(new ItemStack(Material.EMERALD)), stored(owner));
    }
}
