package bm.b0b0b0.SoulGolem.gui;

import bm.b0b0b0.SoulGolem.config.settings.GuiConfirmSettings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class GolemDeleteConfirmMenu implements InventoryHolder {

    private final GolemGuiService service;
    private final Player player;
    private final UUID golemId;
    private final int listPage;
    private final Inventory inventory;

    public GolemDeleteConfirmMenu(GolemGuiService service, Player player, ActiveGolem golem, int listPage) {
        this.service = service;
        this.player = player;
        this.golemId = golem.data().id();
        this.listPage = listPage;
        GuiConfirmSettings settings = service.confirmSettings();
        this.inventory = Bukkit.createInventory(
                this,
                settings.size,
                service.messages().component("gui-confirm-title", service.golemResolvers(golem))
        );
        populate(settings);
    }

    private void populate(GuiConfirmSettings settings) {
        ItemStack filler = GolemGuiItems.filler(settings.fillerMaterial);
        for (int slot : settings.fillerSlots) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, filler);
            }
        }

        inventory.setItem(settings.warningSlot, GolemGuiItems.button(
                settings.warningMaterial,
                Material.BARRIER,
                service.messages().component("gui-confirm-warning-name"),
                GolemGuiItems.loreLines(service.messages(), "gui-confirm-warning-lore")
        ));
        inventory.setItem(settings.yesSlot, GolemGuiItems.button(
                settings.yesMaterial,
                Material.LIME_WOOL,
                service.messages().component("gui-confirm-yes-name"),
                GolemGuiItems.loreLines(service.messages(), "gui-confirm-yes-lore")
        ));
        inventory.setItem(settings.noSlot, GolemGuiItems.button(
                settings.noMaterial,
                Material.RED_WOOL,
                service.messages().component("gui-confirm-no-name"),
                GolemGuiItems.loreLines(service.messages(), "gui-confirm-no-lore")
        ));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void handleClick(int slot) {
        GuiConfirmSettings settings = service.confirmSettings();
        if (slot == settings.noSlot) {
            service.openManage(player, golemId, listPage);
            return;
        }
        if (slot == settings.yesSlot) {
            service.findGolem(golemId).ifPresentOrElse(golem -> {
                if (!service.canAccess(player, golem)) {
                    service.messages().send(player, "gui-not-owner");
                    return;
                }
                service.controlService().remove(golemId, player);
                service.openList(player, listPage);
            }, () -> service.openList(player, listPage));
        }
    }
}
