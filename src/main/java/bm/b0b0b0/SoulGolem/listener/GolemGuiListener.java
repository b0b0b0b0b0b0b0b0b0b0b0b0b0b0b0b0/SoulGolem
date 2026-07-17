package bm.b0b0b0.SoulGolem.listener;

import bm.b0b0b0.SoulGolem.gui.GolemDeleteConfirmMenu;
import bm.b0b0b0.SoulGolem.gui.GolemListMenu;
import bm.b0b0b0.SoulGolem.gui.GolemManageMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class GolemGuiListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder(false) instanceof GolemListMenu menu) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (event.getClickedInventory() == null || event.getClickedInventory() != top) {
                return;
            }
            menu.handleClick(event.getRawSlot());
            return;
        }
        if (top.getHolder(false) instanceof GolemManageMenu menu) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (event.getClickedInventory() == null || event.getClickedInventory() != top) {
                return;
            }
            menu.handleClick(event.getRawSlot());
            return;
        }
        if (top.getHolder(false) instanceof GolemDeleteConfirmMenu menu) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            if (event.getClickedInventory() == null || event.getClickedInventory() != top) {
                return;
            }
            menu.handleClick(event.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder(false) instanceof GolemListMenu
                || top.getHolder(false) instanceof GolemManageMenu
                || top.getHolder(false) instanceof GolemDeleteConfirmMenu) {
            event.setCancelled(true);
        }
    }
}
