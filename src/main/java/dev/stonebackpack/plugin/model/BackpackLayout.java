package dev.stonebackpack.plugin.model;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
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

    /** The laid-out pages, and the items that have no place on them. */
    public record Layout(List<ItemStack[]> pages, List<ItemStack> removed) {
    }

    /**
     * Lays the stored pages out as {@code pageCount} pages of {@code rows}
     * rows. An item where a page button goes, or in a row that no longer
     * exists, moves to the first free slot from the next page on. Items of
     * pages beyond {@code pageCount}, and any item that finds no free slot,
     * are returned as removed instead of being dropped. Items that have a
     * place keep it, so laying out the result again changes nothing.
     */
    public static Layout arrange(List<ItemStack[]> stored, int rows, int pageCount) {
        int size = rows * 9;
        int count = Math.max(1, pageCount);
        List<ItemStack[]> pages = new ArrayList<>(count);
        for (int page = 0; page < count; page++) {
            pages.add(new ItemStack[size]);
        }
        List<Displaced> displaced = new ArrayList<>();
        List<ItemStack> removed = new ArrayList<>();
        for (int page = 0; page < stored.size(); page++) {
            ItemStack[] old = stored.get(page);
            for (int slot = 0; slot < old.length; slot++) {
                ItemStack item = old[slot];
                if (isEmpty(item)) {
                    continue;
                }
                if (page >= count) {
                    removed.add(item);
                } else if (slot < size && !isButtonSlot(slot, page, count, rows)) {
                    pages.get(page)[slot] = item;
                } else {
                    displaced.add(new Displaced(item, page));
                }
            }
        }
        for (Displaced item : displaced) {
            if (!placeFrom(pages, item.item(), item.fromPage() + 1, rows)) {
                removed.add(item.item());
            }
        }
        return new Layout(pages, removed);
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

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.isEmpty();
    }
}
