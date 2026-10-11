package dev.auxy.warbcraft.plugin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** Menu simgeleri. Metinler MiniMessage'dir; oyuncu girdisi cagiran tarafta kacirilmis olmalidir. */
public final class Icons {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Icons() {
    }

    public static Component text(String mini) {
        return MM.deserialize(mini).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(text(name));
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(text("<gray>" + line));
            }
            meta.lore(lines);
            meta.addItemFlags(ItemFlag.values());
        });
        return stack;
    }

    public static ItemStack icon(Material material, String name, String... lore) {
        return icon(material, name, List.of(lore));
    }

    public static ItemStack glow(ItemStack stack, boolean glow) {
        stack.editMeta(meta -> meta.setEnchantmentGlintOverride(glow ? Boolean.TRUE : null));
        return stack;
    }

    public static ItemStack amount(ItemStack stack, int amount) {
        stack.setAmount(Math.clamp(amount, 1, stack.getMaxStackSize()));
        return stack;
    }

    public static ItemStack head(OfflinePlayer owner, String name, List<String> lore) {
        ItemStack stack = icon(Material.PLAYER_HEAD, name, lore);
        stack.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(owner));
        return stack;
    }

    public static ItemStack filler() {
        return icon(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    public static ItemStack back() {
        return icon(Material.ARROW, "<yellow>Geri");
    }
}
