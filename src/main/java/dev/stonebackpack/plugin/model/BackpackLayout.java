package dev.stonebackpack.plugin.model;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Where the page buttons of a backpack sit, and how stored items are laid out
 * around them. Free of server state, so it can be tested on its own.
 */
public final class BackpackLayout {
    public static final int MAX_PAGES = 10;

    private BackpackLayout() {
    }

    public static int previousSlot(int rows) {
        return (rows - 1) * 9;
    }

    public static int nextSlot(int rows) {
        return rows * 9 - 1;
    }

    public static boolean hasPrevious(int page) {
        return page > 0;
    }

    public static boolean hasNext(int page, int pageCount) {
        return page < pageCount - 1;
    }

    public static boolean isButtonSlot(int slot, int page, int pageCount, int rows) {
        return (hasPrevious(page) && slot == previousSlot(rows))
                || (hasNext(page, pageCount) && slot == nextSlot(rows));
    }

    // Every page but the first has a back button, every page but the last a next button.
    public static int capacity(int rows, int pageCount) {
        return pageCount * rows * 9 - 2 * (pageCount - 1);
    }

    public static int pagesFor(int items, int rows) {
        int pages = 1;
        while (capacity(rows, pages) < items) {
            pages++;
        }
        return pages;
    }

    // fromPage -1: the item's page no longer exists, so it goes to the first free slot.
    private record Displaced(ItemStack item, int fromPage) {
    }

    /**
     * Lays the stored pages out as {@code pageCount} pages of {@code rows}
     * rows, or more if the items don't fit on those. An item without a place
     * there - where a page button goes, or in a row or page that no longer
     * exists - moves to the first free slot from the next page on. Items that
     * have a place keep it, so laying out the result again changes nothing.
     */
    public static List<ItemStack[]> arrange(List<ItemStack[]> stored, int rows, int pageCount) {
        int size = rows * 9;
        int count = Math.max(Math.max(1, pageCount), pagesFor(countItems(stored), rows));
        List<ItemStack[]> pages = new ArrayList<>(count);
        for (int page = 0; page < count; page++) {
            pages.add(new ItemStack[size]);
        }
        Deque<Displaced> displaced = new ArrayDeque<>();
        for (int page = 0; page < stored.size(); page++) {
            ItemStack[] old = stored.get(page);
            for (int slot = 0; slot < old.length; slot++) {
                if (isEmpty(old[slot])) {
                    continue;
                }
                if (page < count && slot < size && !isButtonSlot(slot, page, count, rows)) {
                    pages.get(page)[slot] = old[slot];
                } else {
                    displaced.add(new Displaced(old[slot], page < count ? page : -1));
                }
            }
        }

        while (!displaced.isEmpty()) {
            Displaced next = displaced.poll();
            if (!placeFrom(pages, next.item(), next.fromPage() + 1, rows)) {
                // Not reached while the page count above covers every item; kept
                // so that a miscount opens another page instead of dropping one.
                ItemStack[] last = pages.getLast();
                ItemStack underButton = last[nextSlot(rows)];
                last[nextSlot(rows)] = null;
                pages.add(new ItemStack[size]);
                if (!isEmpty(underButton)) {
                    displaced.addFirst(new Displaced(underButton, pages.size() - 2));
                }
                displaced.addFirst(next);
            }
        }
        return pages;
    }

    // Tries the pages from firstPage on, wrapping around to the first page.
    private static boolean placeFrom(List<ItemStack[]> pages, ItemStack item, int firstPage, int rows) {
        int pageCount = pages.size();
        for (int step = 0; step < pageCount; step++) {
            int page = (firstPage + step) % pageCount;
            ItemStack[] contents = pages.get(page);
            for (int slot = 0; slot < contents.length; slot++) {
                if (contents[slot] == null && !isButtonSlot(slot, page, pageCount, rows)) {
                    contents[slot] = item;
                    return true;
                }
            }
        }
        return false;
    }

    public static int countItems(List<ItemStack[]> pages) {
        int items = 0;
        for (ItemStack[] page : pages) {
            for (ItemStack item : page) {
                if (!isEmpty(item)) {
                    items++;
                }
            }
        }
        return items;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.isEmpty();
    }
}
