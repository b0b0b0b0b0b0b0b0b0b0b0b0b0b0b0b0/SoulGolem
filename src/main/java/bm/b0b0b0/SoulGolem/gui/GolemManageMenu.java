package bm.b0b0b0.SoulGolem.gui;

import bm.b0b0b0.SoulGolem.config.settings.GuiGeneralSettings;
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

public final class GolemManageMenu implements InventoryHolder {

    private final GolemGuiService service;
    private final Player player;
    private final UUID golemId;
    private final int listPage;
    private final Inventory inventory;

    public GolemManageMenu(GolemGuiService service, Player player, ActiveGolem golem, int listPage) {
        this.service = service;
        this.player = player;
        this.golemId = golem.data().id();
        this.listPage = listPage;
        GuiGeneralSettings settings = service.manageSettings();
        this.inventory = Bukkit.createInventory(
                this,
                settings.size,
                service.messages().component("gui-manage-title", service.golemResolvers(golem))
        );
        populate(golem, settings);
    }

    private void populate(ActiveGolem golem, GuiGeneralSettings settings) {
        ItemStack filler = GolemGuiItems.filler(settings.fillerMaterial);
        for (int slot : settings.fillerSlots) {
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, filler);
            }
        }

        List<Component> infoLore = new ArrayList<>(GolemGuiItems.loreLines(
                service.messages(),
                "gui-manage-info-lore",
                service.golemResolvers(golem)
        ));
        infoLore.add(service.statusComponent(golem));
        inventory.setItem(settings.infoSlot, GolemGuiItems.button(
                settings.infoMaterial,
                Material.COPPER_INGOT,
                service.messages().component(service.golemNameKey(golem)),
                infoLore
        ));

        if (golem.data().paused()) {
            inventory.setItem(settings.pauseSlot, GolemGuiItems.button(
                    settings.resumeMaterial,
                    Material.LIME_DYE,
                    service.messages().component("gui-manage-resume-name"),
                    GolemGuiItems.loreLines(service.messages(), "gui-manage-resume-lore")
            ));
        } else {
            inventory.setItem(settings.pauseSlot, GolemGuiItems.button(
                    settings.pauseMaterial,
                    Material.REDSTONE,
                    service.messages().component("gui-manage-stop-name"),
                    GolemGuiItems.loreLines(service.messages(), "gui-manage-stop-lore")
            ));
        }

        inventory.setItem(settings.deleteSlot, GolemGuiItems.button(
                settings.deleteMaterial,
                Material.TNT,
                service.messages().component("gui-manage-delete-name"),
                GolemGuiItems.loreLines(service.messages(), "gui-manage-delete-lore")
        ));

        inventory.setItem(settings.teleportSlot, GolemGuiItems.button(
                settings.teleportMaterial,
                Material.ENDER_PEARL,
                service.messages().component("gui-manage-teleport-name"),
                GolemGuiItems.loreLines(service.messages(), "gui-manage-teleport-lore")
        ));

        inventory.setItem(settings.backSlot, GolemGuiItems.button(
                settings.backMaterial,
                Material.ARROW,
                service.messages().component("gui-manage-back-name"),
                GolemGuiItems.loreLines(service.messages(), "gui-manage-back-lore")
        ));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void handleClick(int slot) {
        GuiGeneralSettings settings = service.manageSettings();
        if (slot == settings.backSlot) {
            service.openList(player, listPage);
            return;
        }
        if (slot == settings.deleteSlot) {
            service.openDeleteConfirm(player, golemId, listPage);
            return;
        }
        if (slot == settings.teleportSlot) {
            service.findGolem(golemId).ifPresent(golem -> {
                if (!service.canAccess(player, golem)) {
                    service.messages().send(player, "gui-not-owner");
                    return;
                }
                service.controlService().teleportToGolem(player, golem);
            });
            return;
        }
        if (slot == settings.pauseSlot) {
            service.findGolem(golemId).ifPresent(golem -> {
                if (!service.canAccess(player, golem)) {
                    service.messages().send(player, "gui-not-owner");
                    return;
                }
                if (golem.data().paused()) {
                    service.controlService().resume(golem, player);
                } else {
                    service.controlService().requestStop(golem, player);
                }
                service.openManage(player, golemId, listPage);
            });
        }
    }
}
