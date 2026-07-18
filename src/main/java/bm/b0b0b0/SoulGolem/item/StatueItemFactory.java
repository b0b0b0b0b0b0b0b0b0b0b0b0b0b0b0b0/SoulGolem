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
        ItemStack stack = new ItemStack(statueMaterial(resolved), Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        String nameKey = switch (resolved) {
            case FARMER -> "statue-name-farmer";
            case DIGGER -> "statue-name-digger";
            default -> "statue-name-miner";
        };
        String loreKey = switch (resolved) {
            case FARMER -> "statue-lore-farmer";
            case DIGGER -> "statue-lore-digger";
            default -> "statue-lore-miner";
        };
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

    private static Material statueMaterial(GolemType type) {
        if (type == GolemType.FARMER) {
            Material oxidized = Material.matchMaterial("OXIDIZED_COPPER_GOLEM_STATUE");
            return oxidized != null ? oxidized : Material.COPPER_BLOCK;
        }
        if (type == GolemType.DIGGER) {
            Material exposed = Material.matchMaterial("EXPOSED_COPPER_GOLEM_STATUE");
            if (exposed != null) {
                return exposed;
            }
        }
        Material statue = Material.matchMaterial("COPPER_GOLEM_STATUE");
        return statue != null ? statue : Material.COPPER_BLOCK;
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
