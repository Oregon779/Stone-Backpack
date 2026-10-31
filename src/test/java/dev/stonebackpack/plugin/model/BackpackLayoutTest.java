package dev.stonebackpack.plugin.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackpackLayoutTest {

    @BeforeEach
    void mock() {
        MockBukkit.mock();
    }

    @AfterEach
    void unmock() {
        MockBukkit.unmock();
    }

    private static ItemStack[] fullPage(int slots) {
        ItemStack[] page = new ItemStack[slots];
        for (int i = 0; i < slots; i++) {
            page[i] = new ItemStack(Material.DIRT, i + 1);
        }
        return page;
    }

    private static ItemStack[] pageWith(int slots, int slot, ItemStack item) {
        ItemStack[] page = new ItemStack[slots];
        page[slot] = item;
        return page;
    }

    @Test
    void onePageKeepsEverySlotAndHasNoButtons() {
        ItemStack[] stored = fullPage(27);

        BackpackLayout.Layout layout = BackpackLayout.arrange(List.<ItemStack[]>of(stored), 3, 1);

        assertEquals(1, layout.pages().size());
        assertTrue(layout.removed().isEmpty());
        for (int slot = 0; slot < 27; slot++) {
            assertSame(stored[slot], layout.pages().get(0)[slot]);
        }
    }

    @Test
    void itemUnderTheNextButtonMovesToThePageAfter() {
        ItemStack[] stored = fullPage(27);

        BackpackLayout.Layout layout = BackpackLayout.arrange(List.<ItemStack[]>of(stored), 3, 2);

        List<ItemStack[]> pages = layout.pages();
        assertEquals(2, pages.size());
        for (int slot = 0; slot < 26; slot++) {
            assertSame(stored[slot], pages.get(0)[slot]);
        }
        assertNull(pages.get(0)[BackpackLayout.nextSlot(3)]);
        assertSame(stored[26], pages.get(1)[0]);
        assertTrue(layout.removed().isEmpty());
    }

    @Test
    void itemsOfRemovedPagesAreHandedBackInsteadOfMoved() {
        ItemStack first = new ItemStack(Material.DIAMOND);
        ItemStack last = new ItemStack(Material.EMERALD);

        BackpackLayout.Layout layout = BackpackLayout.arrange(
                List.of(pageWith(27, 0, first), new ItemStack[27], pageWith(27, 4, last)), 3, 1);

        assertEquals(1, layout.pages().size());
        assertSame(first, layout.pages().get(0)[0]);
        assertNull(layout.pages().get(0)[1]);
        assertEquals(1, layout.removed().size());
        assertSame(last, layout.removed().getFirst());
    }

    @Test
    void moreRowsKeepEveryItemInItsSlotAndMoveTheButtonsDown() {
        ItemStack onFirst = new ItemStack(Material.DIAMOND);
        ItemStack onSecond = new ItemStack(Material.EMERALD);

        List<ItemStack[]> pages = BackpackLayout.arrange(
                List.of(pageWith(27, 18, onFirst), pageWith(27, 26, onSecond)), 4, 2).pages();

        assertEquals(2, pages.size());
        assertSame(onFirst, pages.get(0)[18]);
        assertSame(onSecond, pages.get(1)[26]);
        assertEquals(36, pages.get(1).length);
        assertNull(pages.get(0)[BackpackLayout.nextSlot(4)]);
        assertNull(pages.get(1)[BackpackLayout.previousSlot(4)]);
    }

    @Test
    void itemsThatFindNoFreeSlotAreHandedBack() {
        ItemStack[] first = fullPage(9);
        ItemStack[] second = fullPage(9);

        BackpackLayout.Layout layout = BackpackLayout.arrange(List.of(first, second), 1, 2);

        assertEquals(2, layout.pages().size());
        assertEquals(List.of(first[8], second[0]), layout.removed());
        assertEachItemOnceAndButtonSlotsFree(List.of(first, second), layout, 1);
    }

    @Test
    void randomLayoutsKeepOrHandBackEveryItemAndLeaveButtonSlotsFree() {
        Random random = new Random(42);
        for (int run = 0; run < 500; run++) {
            int storedRows = 1 + random.nextInt(6);
            int rows = 1 + random.nextInt(6);
            int pageCount = 1 + random.nextInt(BackpackLayout.MAX_PAGES);
            double fill = random.nextDouble();
            List<ItemStack[]> stored = new ArrayList<>();
            for (int page = random.nextInt(5); page > 0; page--) {
                ItemStack[] contents = new ItemStack[storedRows * 9];
                for (int slot = 0; slot < contents.length; slot++) {
                    if (random.nextDouble() < fill) {
                        contents[slot] = new ItemStack(Material.STONE);
                    }
                }
                stored.add(contents);
            }

            BackpackLayout.Layout layout = BackpackLayout.arrange(stored, rows, pageCount);

            assertEquals(pageCount, layout.pages().size());
            layout.pages().forEach(page -> assertEquals(rows * 9, page.length));
            assertEachItemOnceAndButtonSlotsFree(stored, layout, rows);
            for (int page = pageCount; page < stored.size(); page++) {
                for (ItemStack item : stored.get(page)) {
                    assertTrue(item == null || layout.removed().contains(item), "item of a removed page stayed");
                }
            }
            // Laying out the result again must not move or remove anything.
            BackpackLayout.Layout again = BackpackLayout.arrange(layout.pages(), rows, pageCount);
            assertTrue(again.removed().isEmpty());
            for (int page = 0; page < pageCount; page++) {
                for (int slot = 0; slot < rows * 9; slot++) {
                    assertSame(layout.pages().get(page)[slot], again.pages().get(page)[slot]);
                }
            }
        }
    }

    private static void assertEachItemOnceAndButtonSlotsFree(List<ItemStack[]> stored, BackpackLayout.Layout layout, int rows) {
        Set<ItemStack> before = Collections.newSetFromMap(new IdentityHashMap<>());
        stored.forEach(page -> {
            for (ItemStack item : page) {
                if (item != null) {
                    before.add(item);
                }
            }
        });
        Set<ItemStack> after = Collections.newSetFromMap(new IdentityHashMap<>());
        int seen = 0;
        List<ItemStack[]> pages = layout.pages();
        for (int page = 0; page < pages.size(); page++) {
            for (int slot = 0; slot < pages.get(page).length; slot++) {
                ItemStack item = pages.get(page)[slot];
                if (item == null) {
                    continue;
                }
                assertTrue(!BackpackLayout.isButtonSlot(slot, page, pages.size(), rows),
                        "item on a button slot: page " + page + ", slot " + slot);
                after.add(item);
                seen++;
            }
        }
        after.addAll(layout.removed());
        seen += layout.removed().size();
        assertEquals(before.size(), seen, "an item was duplicated or dropped");
        assertEquals(before, after);
    }
}
