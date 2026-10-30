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

    private record Displaced(ItemStack item, int fromPage) {
    }

    /**
     * Lays the stored pages out as pages of {@code rows} rows: at least
     * {@code minPages}, and as many as needed to keep every stored item. An
     * item lying where a page button goes (or past the end of a page) moves to
     * the first free slot from the next page on, so nothing is ever dropped.
     */
    public static List<ItemStack[]> arrange(List<ItemStack[]> stored, int rows, int minPages) {
        int size = rows * 9;
        int pageCount = Math.max(1, Math.max(minPages, lastUsedPage(stored) + 1));
        List<ItemStack[]> pages = new ArrayList<>(pageCount);
        Deque<Displaced> displaced = new ArrayDeque<>();

        for (int page = 0; page < pageCount; page++) {
            ItemStack[] laidOut = new ItemStack[size];
            ItemStack[] old = page < stored.size() ? stored.get(page) : new ItemStack[0];
            for (int slot = 0; slot < old.length; slot++) {
                if (isEmpty(old[slot])) {
                    continue;
                }
                if (slot < size && !isButtonSlot(slot, page, pageCount, rows)) {
                    laidOut[slot] = old[slot];
                } else {
                    displaced.add(new Displaced(old[slot], page));
                }
            }
            pages.add(laidOut);
        }

        while (!displaced.isEmpty()) {
            Displaced next = displaced.poll();
            if (!placeFrom(pages, next.item(), next.fromPage() + 1, rows)) {
                // Every slot is taken: open one more page. The former last page
                // gets a next button, so whatever lies there moves on as well.
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

    private static int lastUsedPage(List<ItemStack[]> pages) {
        for (int page = pages.size() - 1; page >= 0; page--) {
            for (ItemStack item : pages.get(page)) {
                if (!isEmpty(item)) {
                    return page;
                }
            }
        }
        return -1;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.isEmpty();
    }
}
