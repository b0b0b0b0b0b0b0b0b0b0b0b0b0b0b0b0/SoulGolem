package bm.b0b0b0.SoulGolem.listener;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.item.StatueItemFactory;
import bm.b0b0b0.SoulGolem.message.MessageService;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.repository.GolemRepository;
import bm.b0b0b0.SoulGolem.service.FarmAreaService;
import bm.b0b0b0.SoulGolem.service.GolemDisplay;
import bm.b0b0b0.SoulGolem.service.GolemRegistry;
import bm.b0b0b0.SoulGolem.service.GolemSpawnService;
import bm.b0b0b0.SoulGolem.service.SoulChestService;
import bm.b0b0b0.SoulGolem.service.WorkAreaService;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class GolemListener implements Listener {

    private final ConfigurationLoader configurationLoader;
    private final PluginKeys keys;
    private final StatueItemFactory statueFactory;
    private final GolemSpawnService spawnService;
    private final SoulChestService chestService;
    private final WorkAreaService workAreaService;
    private final FarmAreaService farmAreaService;
    private final GolemRegistry registry;
    private final GolemRepository repository;

    public GolemListener(
            ConfigurationLoader configurationLoader,
            PluginKeys keys,
            StatueItemFactory statueFactory,
            GolemSpawnService spawnService,
            SoulChestService chestService,
            WorkAreaService workAreaService,
            FarmAreaService farmAreaService,
            GolemRegistry registry,
            GolemRepository repository
    ) {
        this.configurationLoader = configurationLoader;
        this.keys = keys;
        this.statueFactory = statueFactory;
        this.spawnService = spawnService;
        this.chestService = chestService;
        this.workAreaService = workAreaService;
        this.farmAreaService = farmAreaService;
        this.registry = registry;
        this.repository = repository;
    }

    private MessageService messages() {
        return this.configurationLoader.messages();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStatueUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        ItemStack item = event.getItem();
        if (!this.statueFactory.isStatue(item)) {
            return;
        }
        event.setCancelled(true);
        this.spawnService.trySpawnFromStatue(event.getPlayer(), block, item);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProtectedBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        boolean admin = player.hasPermission(this.configurationLoader.config().settings().permissions.admin);

        if (this.chestService.isSoulChest(block) || isRegisteredCraftTable(block) || this.chestService.isSoulCraftingTable(block)) {
            event.setCancelled(true);
            messages().send(player, "protect-station");
            return;
        }

        if (!this.workAreaService.isProtected(block) && !this.farmAreaService.isFarmProtected(block)) {
            return;
        }
        if (admin) {
            return;
        }
        event.setCancelled(true);
        messages().send(player, "protect-work-block");
    }

    private boolean isRegisteredCraftTable(Block block) {
        if (block.getType() != Material.CRAFTING_TABLE) {
            return false;
        }
        for (ActiveGolem golem : this.registry.all()) {
            if (this.chestService.isSoulCraftingTable(block, golem.data())) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (isSoulInventory(event.getSource().getLocation()) || isSoulInventory(event.getDestination().getLocation())) {
            event.setCancelled(true);
        }
    }

    private boolean isSoulInventory(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        Block block = location.getBlock();
        return this.chestService.isSoulChest(block) || isRegisteredCraftTable(block);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGolemInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity entity = event.getRightClicked();
        String raw = entity.getPersistentDataContainer().get(this.keys.golemId(), PersistentDataType.STRING);
        if (raw == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Optional<ActiveGolem> optional = this.registry.byId(UUID.fromString(raw));
        if (optional.isEmpty()) {
            return;
        }
        ActiveGolem golem = optional.get();
        boolean admin = player.hasPermission(this.configurationLoader.config().settings().permissions.admin);
        if (!admin && !golem.data().ownerUuid().equals(player.getUniqueId())) {
            messages().send(player, "protect-not-owner");
            return;
        }
        if (player.isSneaking()) {
            boolean paused = !golem.data().paused();
            golem.data().paused(paused);
            golem.markDirty();
            this.repository.save(golem.data());
            if (event.getRightClicked() instanceof org.bukkit.entity.CopperGolem copper) {
                GolemDisplay.refreshForce(golem, copper, messages(), this.keys, this.configurationLoader.config().settings().visuals.textDisplays);
            }
            messages().send(player, paused ? "golem-paused" : "golem-resumed");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGolemDrop(EntityDropItemEvent event) {
        String raw = event.getEntity().getPersistentDataContainer().get(this.keys.golemId(), PersistentDataType.STRING);
        if (raw != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGolemTarget(EntityTargetEvent event) {
        String raw = event.getEntity().getPersistentDataContainer().get(this.keys.golemId(), PersistentDataType.STRING);
        if (raw != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGolemDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        String raw = entity.getPersistentDataContainer().get(this.keys.golemId(), PersistentDataType.STRING);
        if (raw == null) {
            return;
        }
        event.setCancelled(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        if (entity instanceof LivingEntity living && living.isValid()) {
            var maxHealth = living.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                living.setHealth(maxHealth.getValue());
            }
            living.setFireTicks(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGolemDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        String raw = entity.getPersistentDataContainer().get(this.keys.golemId(), PersistentDataType.STRING);
        if (raw == null) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player player) {
            Optional<ActiveGolem> optional = this.registry.byId(UUID.fromString(raw));
            if (optional.isEmpty()) {
                event.setCancelled(true);
                return;
            }
            ActiveGolem golem = optional.get();
            boolean admin = player.hasPermission(this.configurationLoader.config().settings().permissions.admin);
            if (!admin && !golem.data().ownerUuid().equals(player.getUniqueId())) {
                event.setCancelled(true);
                messages().send(player, "protect-not-owner");
                return;
            }
            if (player.getGameMode() == GameMode.CREATIVE || player.isSneaking()) {
                event.setCancelled(true);
                this.spawnService.removeGolem(golem.data().id(), player);
                if (entity.isValid()) {
                    entity.remove();
                }
                return;
            }
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSoulGolemTrample(EntityChangeBlockEvent event) {
        if (event.getBlock().getType() != Material.FARMLAND) {
            return;
        }
        String raw = event.getEntity().getPersistentDataContainer().get(this.keys.golemId(), PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return;
        }
        event.setCancelled(true);
    }
}
