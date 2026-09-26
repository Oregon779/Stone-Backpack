package dev.stonebackpack.plugin.model;

import dev.stonebackpack.plugin.manager.DataManager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public class BackupMenuHolder implements InventoryHolder {
    public enum View { PLAYERS, BACKUP }

    public record Target(UUID id, String name) {
    }

    private final View view;
    private final int page;
    private final int pageCount;
    private final List<Target> pageTargets;
    private final Target target;
    private final DataManager.Backup backup;
    private final int returnPage;
    private Inventory inventory;

    private BackupMenuHolder(View view, int page, int pageCount, List<Target> pageTargets,
                             Target target, DataManager.Backup backup, int returnPage) {
        this.view = view;
        this.page = page;
        this.pageCount = pageCount;
        this.pageTargets = pageTargets;
        this.target = target;
        this.backup = backup;
        this.returnPage = returnPage;
    }

    public static BackupMenuHolder playerList(int page, int pageCount, List<Target> pageTargets) {
        return new BackupMenuHolder(View.PLAYERS, page, pageCount, pageTargets, null, null, 0);
    }

    public static BackupMenuHolder backup(Target target, DataManager.Backup backup, int page, int pageCount, int returnPage) {
        return new BackupMenuHolder(View.BACKUP, page, pageCount, List.of(), target, backup, returnPage);
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

    public Target targetAt(int slot) {
        return slot >= 0 && slot < pageTargets.size() ? pageTargets.get(slot) : null;
    }

    public Target getTarget() {
        return target;
    }

    public DataManager.Backup getBackup() {
        return backup;
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
