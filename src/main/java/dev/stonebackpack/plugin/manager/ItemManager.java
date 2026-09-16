package dev.stonebackpack.plugin.manager;

import dev.stonebackpack.plugin.StoneBackpack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ItemManager {
    private final StoneBackpack plugin;
    private final NamespacedKey backpackItemKey;

    public ItemManager(StoneBackpack plugin) {
        this.plugin = plugin;
        this.backpackItemKey = new NamespacedKey(plugin, "backpack_item");
    }

    public NamespacedKey getBackpackItemKey() {
        return backpackItemKey;
    }

    public ItemStack createBackpackItem() {
        ConfigManager config = plugin.getConfigManager();
        MessageManager messages = plugin.getMessageManager();

        ItemStack item = new ItemStack(config.getItemMaterial());
        ItemMeta meta = item.getItemMeta();

        Component name = messages.parse(config.getItemName()).decoration(TextDecoration.ITALIC, false);
        meta.displayName(name);

        List<Component> lore = new ArrayList<>();
        for (String line : config.getItemLore()) {
            lore.add(messages.parse(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);

        if (config.getItemCustomModelData() > 0) {
            meta.setCustomModelData(config.getItemCustomModelData());
        }

        meta.getPersistentDataContainer().set(backpackItemKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isBackpackItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Boolean tag = item.getItemMeta().getPersistentDataContainer().get(backpackItemKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(tag);
    }

    public void give(org.bukkit.entity.Player player, ItemStack item) {
        var leftover = player.getInventory().addItem(item);
        for (ItemStack overflow : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
    }
}

