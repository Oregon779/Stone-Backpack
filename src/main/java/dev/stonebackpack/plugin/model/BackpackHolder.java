package dev.stonebackpack.plugin.model;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * One page of a backpack. The page layout is fixed for the lifetime of the
 * inventory: when rows or pages change, all pages are rebuilt.
 */
public class BackpackHolder implements InventoryHolder {
    private final UUID ownerId;
    private final int page;
    private final int pageCount;
    private Inventory inventory;

    public BackpackHolder(UUID ownerId, int page, int pageCount) {
        this.ownerId = ownerId;
        this.page = page;
        this.pageCount = pageCount;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public int getPage() {
        return page;
    }

    public int getPageCount() {
        return pageCount;
    }

    public boolean isButtonSlot(int slot) {
        return BackpackLayout.isButtonSlot(slot, page, pageCount, inventory.getSize() / 9);
    }

    public boolean isNextButton(int slot) {
        return BackpackLayout.hasNext(page, pageCount) && slot == BackpackLayout.nextSlot(inventory.getSize() / 9);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    @NotNull
    public Inventory getInventory() {
        return inventory;
    }
}
