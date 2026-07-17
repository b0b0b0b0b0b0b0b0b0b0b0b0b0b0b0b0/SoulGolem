package bm.b0b0b0.SoulGolem.item;

import bm.b0b0b0.SoulGolem.message.MessageService;
import bm.b0b0b0.SoulGolem.model.GolemType;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class StatueItemFactory {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final PluginKeys keys;
    private final Supplier<MessageService> messages;

    public StatueItemFactory(PluginKeys keys, Supplier<MessageService> messages) {
        this.keys = keys;
        this.messages = messages;
    }

    public ItemStack create(int amount) {
        return create(amount, GolemType.MINER);
    }

    public ItemStack create(int amount, GolemType type) {
        GolemType resolved = type == null ? GolemType.MINER : type;
        MessageService messageService = this.messages.get();
        ItemStack stack = new ItemStack(Material.COPPER_BLOCK, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        String nameKey = resolved == GolemType.FARMER ? "statue-name-farmer" : "statue-name-miner";
        String loreKey = resolved == GolemType.FARMER ? "statue-lore-farmer" : "statue-lore-miner";
        meta.displayName(MINI.deserialize(messageService.raw(nameKey)));
        List<Component> lore = new ArrayList<>();
        for (String line : messageService.rawListLines(loreKey)) {
            lore.add(MINI.deserialize(line));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(this.keys.statue(), PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(this.keys.statueType(), PersistentDataType.STRING, resolved.name());
        stack.setItemMeta(meta);
        return stack;
    }

    public GolemType typeOf(ItemStack stack) {
        if (!isStatue(stack)) {
            return GolemType.MINER;
        }
        ItemMeta meta = stack.getItemMeta();
        String raw = meta.getPersistentDataContainer().get(this.keys.statueType(), PersistentDataType.STRING);
        return GolemType.fromString(raw);
    }

    public boolean isStatue(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(this.keys.statue(), PersistentDataType.BYTE);
    }
}
