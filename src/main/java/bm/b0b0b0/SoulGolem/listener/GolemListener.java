package bm.b0b0b0.SoulGolem.listener;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.gui.GolemGuiService;
import bm.b0b0b0.SoulGolem.item.StatueItemFactory;
import bm.b0b0b0.SoulGolem.message.MessageService;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.DiggerState;
import bm.b0b0b0.SoulGolem.model.GolemType;
import bm.b0b0b0.SoulGolem.repository.GolemRepository;
import bm.b0b0b0.SoulGolem.service.FarmAreaService;
import bm.b0b0b0.SoulGolem.service.GolemCombatWork;
import bm.b0b0b0.SoulGolem.service.GolemDisplay;
import bm.b0b0b0.SoulGolem.service.GolemMovement;
import bm.b0b0b0.SoulGolem.service.GolemRegistry;
import bm.b0b0b0.SoulGolem.service.GolemSpawnService;
import bm.b0b0b0.SoulGolem.service.SoulChestService;
import bm.b0b0b0.SoulGolem.service.WorkAreaService;
import bm.b0b0b0.SoulGolem.service.digger.DiggerPit;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
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
    private final GolemGuiService guiService;
    private final GolemCombatWork combat;

    public GolemListener(
            ConfigurationLoader configurationLoader,
            PluginKeys keys,
            StatueItemFactory statueFactory,
            GolemSpawnService spawnService,
            SoulChestService chestService,
            WorkAreaService workAreaService,
            FarmAreaService farmAreaService,
            GolemRegistry registry,
            GolemRepository repository,
            GolemGuiService guiService
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
        this.guiService = guiService;
        this.combat = new GolemCombatWork(
                chestService,
                workAreaService,
                new GolemMovement(
                        configurationLoader.config().golems(),
                        chestService,
                        farmAreaService
                ),
                () -> configurationLoader.config().golems()
        );
    }

    private MessageService messages() {
        return this.configurationLoader.messages();
    }

    private boolean isAdmin(Player player) {
        return player.isOp()
                || player.hasPermission(this.configurationLoader.config().settings().permissions.admin);
    }

    private boolean isTerritory(Block block) {
        if (block == null) {
            return false;
        }
        if (this.farmAreaService.isFarmProtected(block)) {
            return true;
        }
        if (this.workAreaService.isTerritoryBlock(
                block,
                this.registry.all(),
                this.chestService::effectiveRadius
        )) {
            return true;
        }
        for (ActiveGolem golem : this.registry.all()) {
            if (this.farmAreaService.isOwnSeatBlock(block, golem.data())) {
                return true;
            }
        }
        return false;
    }

    private boolean canBypassTerritory(Player player) {
        return isAdmin(player) && player.getGameMode() == GameMode.CREATIVE && player.isSneaking();
    }

    private Optional<ActiveGolem> findTerritoryGolem(Block block) {
        if (block == null) {
            return Optional.empty();
        }
        Optional<UUID> mapped = this.workAreaService.findTerritoryGolemId(
                block,
                this.registry.all(),
                this.chestService::effectiveRadius
        );
        if (mapped.isPresent()) {
            return this.registry.byId(mapped.get());
        }
        for (ActiveGolem golem : this.registry.all()) {
            if (this.farmAreaService.isOwnSeatBlock(block, golem.data())) {
                return Optional.of(golem);
            }
            if (this.workAreaService.containsBlock(
                    golem.data(),
                    this.chestService.effectiveRadius(golem.data()),
                    block
            )) {
                return Optional.of(golem);
            }
        }
        return Optional.empty();
    }

    private boolean isTerritoryOwner(Player player, Block block) {
        if (player == null || block == null) {
            return false;
        }
        Optional<ActiveGolem> golem = findTerritoryGolem(block);
        return golem.isPresent() && player.getUniqueId().equals(golem.get().data().ownerUuid());
    }

    private boolean isOwnerCropBreak(Player player, Block block) {
        return isTerritoryOwner(player, block) && FarmAreaService.isAnyCrop(block.getType());
    }

    private boolean isOwnerDiggerAssistBreak(Player player, Block block) {
        if (player == null || block == null) {
            return false;
        }
        Optional<ActiveGolem> golem = findTerritoryGolem(block);
        if (golem.isEmpty() || !player.getUniqueId().equals(golem.get().data().ownerUuid())) {
            return false;
        }
        if (golem.get().data().type() != GolemType.DIGGER) {
            return false;
        }
        var digger = this.configurationLoader.config().golems().digger;
        return DiggerPit.isDiggable(
                block,
                golem.get().data(),
                this.chestService,
                this.farmAreaService,
                digger
        );
    }

    private boolean isOwnerChestUnderBuild(Player player, Block block) {
        if (player == null || block == null) {
            return false;
        }
        if (this.chestService.isSoulChest(block) || stationGolemId(block) != null) {
            return false;
        }
        int depth = Math.max(0, this.configurationLoader.config().golems().ownerChestUnderDepth);
        if (depth <= 0) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        for (ActiveGolem golem : this.registry.all()) {
            if (!playerId.equals(golem.data().ownerUuid())) {
                continue;
            }
            if (this.chestService.isUnderSoulChestColumn(golem.data(), block, depth)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOwnerGateBreak(Player player, Block block) {
        return isTerritoryOwner(player, block)
                && block != null
                && Tag.FENCE_GATES.isTagged(block.getType());
    }

    private boolean isOwnerGateUse(Player player, Block block) {
        return isTerritoryOwner(player, block)
                && block != null
                && Tag.FENCE_GATES.isTagged(block.getType());
    }

    private void handleOwnerGateBreak(Player player, Block block) {
        this.farmAreaService.unprotectBlock(block);
    }

    private void handleOwnerGatePlace(Player player, Block block) {
        if (block == null || !Tag.FENCE_GATES.isTagged(block.getType())) {
            return;
        }
        for (ActiveGolem active : this.registry.all()) {
            if (!player.getUniqueId().equals(active.data().ownerUuid())) {
                continue;
            }
            int radius = this.chestService.effectiveRadius(active.data());
            Block gateSpot = this.farmAreaService.outerFenceGateSpot(active.data(), radius);
            if (gateSpot == null
                    || gateSpot.getX() != block.getX()
                    || gateSpot.getY() != block.getY()
                    || gateSpot.getZ() != block.getZ()
                    || gateSpot.getWorld() == null
                    || !gateSpot.getWorld().equals(block.getWorld())) {
                continue;
            }
            this.farmAreaService.protectOwnedBlock(block, active.data().id());
            return;
        }
    }

    private boolean isAllowedStationUse(Player player, Block block) {
        if (!this.chestService.isSoulChest(block) && !isRegisteredCraftTable(block)) {
            return false;
        }
        if (isAdmin(player)) {
            return true;
        }
        UUID owner = this.chestService.ownerFromChest(block);
        if (owner == null) {
            UUID golemId = stationGolemId(block);
            if (golemId != null) {
                Optional<ActiveGolem> active = this.registry.byId(golemId);
                if (active.isPresent()) {
                    owner = active.get().data().ownerUuid();
                }
            }
        }
        return owner != null && owner.equals(player.getUniqueId());
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

        UUID stationGolemId = stationGolemId(block);
        if (stationGolemId != null) {
            event.setCancelled(true);
            if (isAdmin(player) && (player.getGameMode() == GameMode.CREATIVE || player.isSneaking())) {
                Optional<ActiveGolem> active = this.registry.byId(stationGolemId);
                if (active.isPresent()) {
                    this.spawnService.removeGolem(stationGolemId, player);
                } else {
                    this.spawnService.cleanupOrphan(stationGolemId, block.getLocation(), player);
                }
                return;
            }
            messages().send(player, "protect-station");
            return;
        }

        if (!isTerritory(block)) {
            return;
        }
        if (canBypassTerritory(player) || isOwnerCropBreak(player, block) || isOwnerDiggerAssistBreak(player, block)
                || isOwnerChestUnderBuild(player, block)) {
            return;
        }
        if (isOwnerGateBreak(player, block)) {
            handleOwnerGateBreak(player, block);
            return;
        }
        event.setCancelled(true);
        messages().send(player, "protect-work-block");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProtectedPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Player player = event.getPlayer();
        handleOwnerGatePlace(player, block);
        if (!isTerritory(block)) {
            return;
        }
        if (canBypassTerritory(player)) {
            return;
        }
        if (Tag.FENCE_GATES.isTagged(block.getType()) && isTerritoryOwner(player, block)) {
            return;
        }
        if (isOwnerChestUnderBuild(player, block)) {
            return;
        }
        event.setCancelled(true);
        messages().send(player, "protect-work-block");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSoulCraftOpen(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CRAFTING_TABLE) {
            return;
        }
        if (!this.configurationLoader.config().golems().farmer.denyCraftOpen) {
            return;
        }
        if (!this.chestService.isSoulCraftingTable(block) && !isRegisteredCraftTable(block)) {
            return;
        }
        Player player = event.getPlayer();
        if (canBypassTerritory(player)) {
            return;
        }
        event.setCancelled(true);
        messages().send(player, "protect-craft-denied");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProtectedInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !isTerritory(block)) {
            return;
        }
        if (this.statueFactory.isStatue(event.getItem())) {
            return;
        }
        Player player = event.getPlayer();
        if (canBypassTerritory(player)) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && isAllowedStationUse(player, block)) {
            if (this.chestService.isSoulChest(block)) {
                this.chestService.ensureChestWaxed(block);
            }
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK
                && (isOwnerCropBreak(player, block) || isOwnerDiggerAssistBreak(player, block)
                || isOwnerChestUnderBuild(player, block))) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && isOwnerChestUnderBuild(player, block)) {
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && isOwnerGateBreak(player, block)) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && isOwnerGateUse(player, block)) {
            return;
        }
        event.setCancelled(true);
        messages().send(player, "protect-work-block");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block block = event.getBlock();
        if (!isTerritory(block) && !isTerritory(block.getRelative(event.getBlockFace()))) {
            return;
        }
        if (canBypassTerritory(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        messages().send(event.getPlayer(), "protect-work-block");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block block = event.getBlock();
        if (!isTerritory(block)) {
            return;
        }
        if (canBypassTerritory(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        messages().send(event.getPlayer(), "protect-work-block");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isTerritory);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isTerritory);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSoulChestFade(org.bukkit.event.block.BlockFadeEvent event) {
        Block block = event.getBlock();
        if (!SoulChestService.isChestLike(block.getType())) {
            return;
        }
        if (this.chestService.isSoulChest(block)) {
            event.setCancelled(true);
            this.chestService.ensureChestWaxed(block);
        }
    }

    private UUID stationGolemId(Block block) {
        UUID fromChest = this.chestService.golemIdFromChest(block);
        if (fromChest != null) {
            return fromChest;
        }
        if (this.chestService.isSoulCraftingTable(block) || isRegisteredCraftTable(block)) {
            UUID fromCraft = this.chestService.golemIdFromCraft(block);
            if (fromCraft != null) {
                return fromCraft;
            }
            for (ActiveGolem golem : this.registry.all()) {
                if (this.chestService.isSoulCraftingTable(block, golem.data())) {
                    return golem.data().id();
                }
            }
        }
        if (this.chestService.isSoulComposter(block) || isRegisteredComposter(block)) {
            UUID fromCompost = this.chestService.golemIdFromCompost(block);
            if (fromCompost != null) {
                return fromCompost;
            }
            for (ActiveGolem golem : this.registry.all()) {
                if (this.chestService.isSoulComposter(block, golem.data())) {
                    return golem.data().id();
                }
            }
        }
        return null;
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

    private boolean isRegisteredComposter(Block block) {
        if (block.getType() != Material.COMPOSTER) {
            return false;
        }
        for (ActiveGolem golem : this.registry.all()) {
            if (this.chestService.isSoulComposter(block, golem.data())) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (isSoulInventory(event.getDestination().getLocation())) {
            event.setCancelled(true);
        }
    }

    private boolean isSoulInventory(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        Block block = location.getBlock();
        return this.chestService.isSoulChest(block)
                || isRegisteredCraftTable(block)
                || isRegisteredComposter(block);
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
        if (!isAdmin(player) && !golem.data().ownerUuid().equals(player.getUniqueId())) {
            messages().send(player, "protect-not-owner");
            return;
        }
        if (player.isSneaking()) {
            boolean paused = !golem.data().paused();
            golem.data().paused(paused);
            golem.markDirty();
            this.repository.save(golem.data());
            if (event.getRightClicked() instanceof CopperGolem copper) {
                GolemDisplay.refreshForce(golem, copper, messages(), this.keys, this.configurationLoader.config().golems().visuals.textDisplays);
            }
            messages().send(player, paused ? "golem-paused" : "golem-resumed");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (event.getRightClicked() instanceof CopperGolem copper
                && hand != null
                && GolemCombatWork.isWeapon(hand.getType())
                && this.combat.acceptWeapon(golem, copper, player, hand)) {
            this.repository.save(golem.data());
            GolemDisplay.refreshForce(
                    golem,
                    copper,
                    messages(),
                    this.keys,
                    this.configurationLoader.config().golems().visuals.textDisplays
            );
            messages().send(player, "golem-armed");
            return;
        }
        this.guiService.openManage(player, golem.data().id(), 0);
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
                if (isAdmin(player) || player.getGameMode() == GameMode.CREATIVE || player.isSneaking()) {
                    this.spawnService.cleanupOrphan(UUID.fromString(raw), entity.getLocation(), player);
                    if (entity.isValid()) {
                        entity.remove();
                    }
                }
                return;
            }
            ActiveGolem golem = optional.get();
            if (!isAdmin(player) && !golem.data().ownerUuid().equals(player.getUniqueId())) {
                event.setCancelled(true);
                messages().send(player, "protect-not-owner");
                return;
            }
            if (holdingStick(player) && this.configurationLoader.config().golems().stickBoostEnabled) {
                applyStickBoost(golem, entity, player);
                event.setCancelled(true);
                messages().send(player, "golem-stick-boost");
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

    private static boolean holdingStick(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && main.getType() == Material.STICK) {
            return true;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        return off != null && off.getType() == Material.STICK;
    }

    private void applyStickBoost(ActiveGolem golem, Entity entity, Player player) {
        long duration = Math.max(1000L, this.configurationLoader.config().golems().stickBoostDurationMs);
        golem.workBoostUntilMs(System.currentTimeMillis() + duration);
        golem.data().lastActionAt(0L);
        if (golem.diggerState() == DiggerState.DIGGING) {
            golem.mineTicksLeft(1L);
        }
        if (entity instanceof CopperGolem copper) {
            nudgeFromSlap(copper, player);
        }
        playStickBoostFx(entity.getLocation(), player);
    }

    private static void nudgeFromSlap(CopperGolem copper, Player player) {
        if (copper == null || player == null) {
            return;
        }
        Vector away = copper.getLocation().toVector().subtract(player.getLocation().toVector());
        away.setY(0.04D);
        if (away.lengthSquared() < 0.01D) {
            away = player.getLocation().getDirection().multiply(-0.05D);
            away.setY(0.04D);
        } else {
            away.normalize().multiply(0.05D);
        }
        copper.setVelocity(away);
    }

    private void playStickBoostFx(Location at, Player player) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        World world = at.getWorld();
        Location fx = at.clone().add(0.0D, 0.95D, 0.0D);

        world.spawnParticle(Particle.CRIT, fx, 10, 0.22D, 0.28D, 0.22D, 0.08D);
        world.spawnParticle(Particle.CLOUD, fx, 4, 0.15D, 0.12D, 0.15D, 0.02D);
        world.spawnParticle(
                Particle.DUST,
                fx,
                8,
                0.22D,
                0.25D,
                0.22D,
                0.0D,
                new Particle.DustOptions(Color.fromRGB(253, 224, 71), 1.1F)
        );

        playSound(world, fx, "entity.player.attack.weak", 0.4F, 1.45F);
        playSound(world, fx, "block.note_block.pling", 0.55F, 1.85F);
        playSound(world, fx, "entity.copper_golem.hurt", 0.25F, 1.75F);
        if (player != null) {
            player.playSound(fx, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.25F, 1.7F);
        }
    }

    private static void playSound(World world, Location at, String key, float volume, float pitch) {
        Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(key));
        if (sound != null) {
            world.playSound(at, sound, volume, pitch);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof CopperGolem copper) {
                this.spawnService.handleChunkSoulEntity(copper);
            }
        }
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTrample(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getBlock().getType() != Material.FARMLAND) {
            return;
        }
        if (!isTerritory(event.getBlock())) {
            return;
        }
        if (canBypassTerritory(player) || isTerritoryOwner(player, event.getBlock())) {
            return;
        }
        event.setCancelled(true);
        messages().send(player, "protect-work-block");
    }
}
