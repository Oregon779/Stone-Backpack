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

        List<ItemStack[]> pages = BackpackLayout.arrange(List.<ItemStack[]>of(stored), 3, 1);

        assertEquals(1, pages.size());
        for (int slot = 0; slot < 27; slot++) {
            assertSame(stored[slot], pages.get(0)[slot]);
        }
    }

    @Test
    void itemUnderTheNextButtonMovesToThePageAfter() {
        ItemStack[] stored = fullPage(27);

        List<ItemStack[]> pages = BackpackLayout.arrange(List.<ItemStack[]>of(stored), 3, 2);

        assertEquals(2, pages.size());
        for (int slot = 0; slot < 26; slot++) {
            assertSame(stored[slot], pages.get(0)[slot]);
        }
        assertNull(pages.get(0)[BackpackLayout.nextSlot(3)]);
        assertSame(stored[26], pages.get(1)[0]);
    }

    @Test
    void pagesBeyondTheMinimumStayOnlyWhileTheyHoldItems() {
        ItemStack first = new ItemStack(Material.DIAMOND);
        ItemStack last = new ItemStack(Material.EMERALD);

        assertEquals(1, BackpackLayout.arrange(List.of(pageWith(27, 0, first), new ItemStack[27], new ItemStack[27]), 3, 1).size());

        List<ItemStack[]> kept = BackpackLayout.arrange(List.of(pageWith(27, 0, first), new ItemStack[27], pageWith(27, 4, last)), 3, 1);
        assertEquals(3, kept.size());
        assertSame(last, kept.get(2)[4]);
    }

    @Test
    void moreRowsKeepEveryItemInItsSlotAndMoveTheButtonsDown() {
        ItemStack onFirst = new ItemStack(Material.DIAMOND);
        ItemStack onSecond = new ItemStack(Material.EMERALD);

        List<ItemStack[]> pages = BackpackLayout.arrange(
                List.of(pageWith(27, 18, onFirst), pageWith(27, 26, onSecond)), 4, 2);

        assertEquals(2, pages.size());
        assertSame(onFirst, pages.get(0)[18]);
        assertSame(onSecond, pages.get(1)[26]);
        assertEquals(36, pages.get(1).length);
        assertNull(pages.get(0)[BackpackLayout.nextSlot(4)]);
        assertNull(pages.get(1)[BackpackLayout.previousSlot(4)]);
    }

    @Test
    void fullBackpackGetsAnotherPageInsteadOfLosingItems() {
        ItemStack[] first = fullPage(9);
        ItemStack[] second = fullPage(9);

        List<ItemStack[]> pages = BackpackLayout.arrange(List.of(first, second), 1, 2);

        assertEquals(3, pages.size());
        assertEverythingKeptAndButtonSlotsFree(List.of(first, second), pages, 1);
    }

    @Test
    void randomLayoutsKeepEveryItemAndLeaveButtonSlotsFree() {
        Random random = new Random(42);
        for (int run = 0; run < 500; run++) {
            int storedRows = 1 + random.nextInt(6);
            int rows = storedRows + random.nextInt(7 - storedRows);
            int minPages = 1 + random.nextInt(BackpackLayout.MAX_PAGES);
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

            List<ItemStack[]> pages = BackpackLayout.arrange(stored, rows, minPages);

            assertTrue(pages.size() >= minPages);
            pages.forEach(page -> assertEquals(rows * 9, page.length));
            assertEverythingKeptAndButtonSlotsFree(stored, pages, rows);
        }
    }

    private static void assertEverythingKeptAndButtonSlotsFree(List<ItemStack[]> stored, List<ItemStack[]> pages, int rows) {
        Set<ItemStack> before = Collections.newSetFromMap(new IdentityHashMap<>());
        stored.forEach(page -> {
            for (ItemStack item : page) {
                if (item != null) {
                    before.add(item);
                }
            }
        });
        Set<ItemStack> after = Collections.newSetFromMap(new IdentityHashMap<>());
        int placed = 0;
        for (int page = 0; page < pages.size(); page++) {
            for (int slot = 0; slot < pages.get(page).length; slot++) {
                ItemStack item = pages.get(page)[slot];
                if (item == null) {
                    continue;
                }
                assertTrue(!BackpackLayout.isButtonSlot(slot, page, pages.size(), rows),
                        "item on a button slot: page " + page + ", slot " + slot);
                after.add(item);
                placed++;
            }
        }
        assertEquals(before.size(), placed);
        assertEquals(before, after);
    }
}
