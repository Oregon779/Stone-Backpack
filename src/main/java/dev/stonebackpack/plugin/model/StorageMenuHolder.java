package dev.stonebackpack.plugin.model;

import dev.stonebackpack.plugin.manager.DataManager.StoredSummary;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public class StorageMenuHolder implements InventoryHolder {
    public enum View { PLAYERS, ITEMS }

    private final View view;
    private final int page;
    private final int pageCount;
    private final List<StoredSummary> pageEntries;
    private final UUID targetId;
    private final String targetName;
    private final List<ItemStack> items;
    private final int returnPage;
    private Inventory inventory;

    private StorageMenuHolder(View view, int page, int pageCount, List<StoredSummary> pageEntries,
                              UUID targetId, String targetName, List<ItemStack> items, int returnPage) {
        this.view = view;
        this.page = page;
        this.pageCount = pageCount;
        this.pageEntries = pageEntries;
        this.targetId = targetId;
        this.targetName = targetName;
        this.items = items;
        this.returnPage = returnPage;
    }

    public static StorageMenuHolder playerList(int page, int pageCount, List<StoredSummary> pageEntries) {
        return new StorageMenuHolder(View.PLAYERS, page, pageCount, pageEntries, null, null, List.of(), 0);
    }

    /** {@code items} is the whole storage of the player as it was loaded, not just this page. */
    public static StorageMenuHolder items(UUID targetId, String targetName, List<ItemStack> items,
                                          int page, int pageCount, int returnPage) {
        return new StorageMenuHolder(View.ITEMS, page, pageCount, List.of(), targetId, targetName, items, returnPage);
    }

    public View getView() {
        return view;
    }

    public int getPage() {
        return page;
    }

    public int getPageCount() {
        return pageCount;
    }

    public StoredSummary entryAt(int slot) {
        return slot >= 0 && slot < pageEntries.size() ? pageEntries.get(slot) : null;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public List<ItemStack> getItems() {
        return items;
    }

    public int getReturnPage() {
        return returnPage;
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
