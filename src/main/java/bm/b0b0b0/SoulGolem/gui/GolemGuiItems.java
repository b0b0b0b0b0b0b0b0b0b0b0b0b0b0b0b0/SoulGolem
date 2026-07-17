package bm.b0b0b0.SoulGolem.gui;

import bm.b0b0b0.SoulGolem.message.MessageService;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class GolemGuiItems {

    private GolemGuiItems() {
    }

    public static ItemStack filler(String materialName) {
        ItemStack item = new ItemStack(parseMaterial(materialName, Material.GRAY_STAINED_GLASS_PANE));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack button(String materialName, Material fallback, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(parseMaterial(materialName, fallback));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack button(String materialName, Material fallback, Component name) {
        return button(materialName, fallback, name, List.of());
    }

    public static List<Component> loreLines(MessageService messages, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        return messages.componentLines(key, resolvers);
    }

    public static Material parseMaterial(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw.trim());
        return material == null ? fallback : material;
    }
}
