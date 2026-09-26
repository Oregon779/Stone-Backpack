package dev.stonebackpack.plugin;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlocklistTest extends PluginTestBase {
    private PlayerMock player;
    private Inventory backpack;

    @BeforeEach
    void openBackpack() {
        player = server.addPlayer();
        backpack = openOwnBackpack(player);
    }

    private InventoryClickEvent click(int rawSlot, ClickType type, InventoryAction action) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, rawSlot, type, action);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void placingShulkerBoxFromCursorIsBlocked() {
        player.setItemOnCursor(new ItemStack(Material.SHULKER_BOX));
        assertTrue(click(0, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled());
    }

    @Test
    void placingNormalItemIsAllowed() {
        player.setItemOnCursor(new ItemStack(Material.DIAMOND));
        assertFalse(click(0, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled());
    }

    @Test
    void shiftClickingShulkerBoxIntoBackpackIsBlocked() {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, backpack.getSize(),
                ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        // MockBukkit maps raw slots differently for getCurrentItem() and getClickedInventory().
        event.setCurrentItem(new ItemStack(Material.RED_SHULKER_BOX));
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void hotbarSwappingShulkerBoxIntoBackpackIsBlocked() {
        player.getInventory().setItem(2, new ItemStack(Material.SHULKER_BOX));
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.NUMBER_KEY,
                InventoryAction.HOTBAR_SWAP, 2);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void draggingShulkerBoxIntoBackpackIsBlocked() {
        ItemStack shulker = new ItemStack(Material.SHULKER_BOX);
        InventoryDragEvent event = new InventoryDragEvent(player.getOpenInventory(), null, shulker, false,
                Map.of(0, shulker));
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void backpackItemCannotBeSmuggledInInsideABundle() {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        bundle.setData(DataComponentTypes.BUNDLE_CONTENTS,
                BundleContents.bundleContents(List.of(plugin.getItemManager().createBackpackItem())));
        // MockBukkit drops data components when an item is copied onto the
        // cursor, so the bundle is handed to the event through the view.
        InventoryView view = withCursor(player.getOpenInventory(), bundle);

        for (InventoryAction action : List.of(InventoryAction.PLACE_FROM_BUNDLE, InventoryAction.PLACE_ALL)) {
            InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, action);
            server.getPluginManager().callEvent(event);
            assertTrue(event.isCancelled(), action.name());
        }
    }

    private static InventoryView withCursor(InventoryView view, ItemStack cursor) {
        return (InventoryView) Proxy.newProxyInstance(BlocklistTest.class.getClassLoader(), new Class<?>[]{InventoryView.class},
                (proxy, method, args) -> method.getName().equals("getCursor") ? cursor : method.invoke(view, args));
    }

    @Test
    void bypassPermissionAllowsBlockedItems() {
        player.addAttachment(plugin, "stonebackpack.bypass.blocklist", true);
        player.setItemOnCursor(new ItemStack(Material.SHULKER_BOX));
        assertFalse(click(0, ClickType.LEFT, InventoryAction.PLACE_ALL).isCancelled());
    }
}
