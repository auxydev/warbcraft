package dev.auxy.warbcraft.plugin.command;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Envanterde ticaret malı sayma/alma/verme. Yalnizca ozel verisi olmayan sade esyalar sayilir. */
public final class Items {

    private Items() {
    }

    public static int count(Player player, Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (isPlain(item, material)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /** @return alinan miktar */
    public static int take(Player player, Material material, int amount) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack item = contents[i];
            if (!isPlain(item, material)) {
                continue;
            }
            int taken = Math.min(left, item.getAmount());
            item.setAmount(item.getAmount() - taken);
            contents[i] = item.getAmount() == 0 ? null : item;
            left -= taken;
        }
        inventory.setStorageContents(contents);
        return amount - left;
    }

    /** Envantere sigmayanlar oyuncunun ayagina birakilir. */
    public static void give(Player player, Material material, int amount) {
        int left = amount;
        while (left > 0) {
            int stack = Math.min(left, material.getMaxStackSize());
            left -= stack;
            for (ItemStack overflow : player.getInventory().addItem(new ItemStack(material, stack)).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
        }
    }

    private static boolean isPlain(ItemStack item, Material material) {
        return item != null && item.getType() == material && !item.hasItemMeta();
    }
}
