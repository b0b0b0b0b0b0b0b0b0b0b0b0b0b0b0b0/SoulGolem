package bm.b0b0b0.SoulGolem.gui;

import bm.b0b0b0.SoulGolem.config.settings.GuiListSettings;
import bm.b0b0b0.SoulGolem.message.MessageService;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class GolemListMenu implements InventoryHolder {

    private final GolemGuiService service;
    private final Player player;
    private final int page;
    private final List<ActiveGolem> allGolems;
    private final Inventory inventory;
    private final List<UUID> pageGolemIds = new ArrayList<>();

    public GolemListMenu(GolemGuiService service, Player player, int page, List<ActiveGolem> allGolems) {
        this.service = service;
        this.player = player;
        this.allGolems = allGolems;
        GuiListSettings settings = service.listSettings();
        int pageSize = Math.max(1, settings.golemSlots.size());
        int totalPages = Math.max(1, (allGolems.size() + pageSize - 1) / pageSize);
        this.page = Math.min(Math.max(0, page), totalPages - 1);
        this.inventory = Bukkit.createInventory(
                this,
                settings.size,
                service.messages().component(
                        "gui-list-title",
                        MessageService.stub("page", String.valueOf(this.page + 1)),
                        MessageService.stub("pages", String.valueOf(totalPages))
                )
        );
        populate(pageSize, totalPages);
    }

    private void populate(int pageSize, int totalPages) {
        GuiListSettings settings = service.listSettings();
        ItemStack filler = GolemGuiItems.filler(settings.fillerMaterial);
        for (int slot : settings.fillerSlots) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, filler);
            }
        }

        int start = page * pageSize;
        int end = Math.min(allGolems.size(), start + pageSize);
        pageGolemIds.clear();
        for (int index = start; index < end; index++) {
            int slotIndex = index - start;
            if (slotIndex >= settings.golemSlots.size()) {
                break;
            }
            int slot = settings.golemSlots.get(slotIndex);
            ActiveGolem golem = allGolems.get(index);
            pageGolemIds.add(golem.data().id());
            inventory.setItem(slot, golemItem(golem, settings));
        }

        if (page > 0) {
            inventory.setItem(settings.prevSlot, GolemGuiItems.button(
                    settings.prevMaterial,
                    Material.ARROW,
                    service.messages().component("gui-list-prev")
            ));
        }
        if (page + 1 < totalPages) {
            inventory.setItem(settings.nextSlot, GolemGuiItems.button(
                    settings.nextMaterial,
                    Material.ARROW,
                    service.messages().component("gui-list-next")
            ));
        }
    }

    private ItemStack golemItem(ActiveGolem golem, GuiListSettings settings) {
        List<Component> lore = new ArrayList<>(GolemGuiItems.loreLines(
                service.messages(),
                "gui-list-golem-lore",
                service.golemResolvers(golem)
        ));
        lore.add(service.statusComponent(golem));
        return GolemGuiItems.button(
                service.golemIconMaterial(golem, settings),
                Material.COPPER_INGOT,
                service.messages().component(service.golemNameKey(golem)),
                lore
        );
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void handleClick(int slot) {
        GuiListSettings settings = service.listSettings();
        if (slot == settings.prevSlot && page > 0) {
            service.openList(player, page - 1);
            return;
        }
        if (slot == settings.nextSlot) {
            int pageSize = Math.max(1, settings.golemSlots.size());
            int totalPages = Math.max(1, (allGolems.size() + pageSize - 1) / pageSize);
            if (page + 1 < totalPages) {
                service.openList(player, page + 1);
            }
            return;
        }
        for (int i = 0; i < settings.golemSlots.size() && i < pageGolemIds.size(); i++) {
            if (settings.golemSlots.get(i) == slot) {
                service.openManage(player, pageGolemIds.get(i), page);
                return;
            }
        }
    }
}
