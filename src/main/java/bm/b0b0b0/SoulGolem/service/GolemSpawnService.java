package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.config.PluginConfig;
import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.item.StatueItemFactory;
import bm.b0b0b0.SoulGolem.message.MessageService;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.FarmerState;
import bm.b0b0b0.SoulGolem.model.GolemType;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.repository.GolemRepository;
import bm.b0b0b0.SoulGolem.scheduler.PluginSchedulers;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class GolemSpawnService {

    private final Plugin plugin;
    private final ConfigurationLoader configurationLoader;
    private final PluginKeys keys;
    private final StatueItemFactory statueFactory;
    private final SoulChestService chestService;
    private final WorkAreaService workAreaService;
    private final FarmAreaService farmAreaService;
    private final OreTableService oreTableService;
    private final GolemRegistry registry;
    private final GolemRepository repository;
    private final Set<UUID> respawning = ConcurrentHashMap.newKeySet();

    public GolemSpawnService(
            Plugin plugin,
            ConfigurationLoader configurationLoader,
            PluginKeys keys,
            StatueItemFactory statueFactory,
            SoulChestService chestService,
            WorkAreaService workAreaService,
            FarmAreaService farmAreaService,
            OreTableService oreTableService,
            GolemRegistry registry,
            GolemRepository repository
    ) {
        this.plugin = plugin;
        this.configurationLoader = configurationLoader;
        this.keys = keys;
        this.statueFactory = statueFactory;
        this.chestService = chestService;
        this.workAreaService = workAreaService;
        this.farmAreaService = farmAreaService;
        this.oreTableService = oreTableService;
        this.registry = registry;
        this.repository = repository;
    }

    private PluginConfig config() {
        return this.configurationLoader.config();
    }

    private MessageService messages() {
        return this.configurationLoader.messages();
    }

    public boolean trySpawnFromStatue(Player player, Block clicked, ItemStack hand) {
        Settings settings = config().settings();
        MessageService messages = messages();
        if (!player.hasPermission(settings.permissions.use) && !player.hasPermission(settings.permissions.admin)) {
            messages.send(player, "spawn-no-permission");
            return true;
        }
        if (!this.statueFactory.isStatue(hand)) {
            return false;
        }
        if (!isPlacementBlock(clicked.getType(), settings)) {
            messages.send(player, "spawn-not-allowed-block");
            return true;
        }
        if (!player.hasPermission(settings.permissions.bypassLimit) && !player.hasPermission(settings.permissions.admin)) {
            int count = this.registry.countByOwner(player.getUniqueId());
            if (count >= settings.maxGolemsPerPlayer) {
                messages.send(player, "spawn-limit", MessageService.stub("max", String.valueOf(settings.maxGolemsPerPlayer)));
                return true;
            }
        }
        if (settings.activationXpLevels > 0 && player.getLevel() < settings.activationXpLevels
                && !player.hasPermission(settings.permissions.admin)) {
            messages.send(player, "spawn-need-xp", MessageService.stub("levels", String.valueOf(settings.activationXpLevels)));
            return true;
        }
        Map<Material, Integer> costs = parseItemCosts(settings.activationItems);
        if (!costs.isEmpty() && !player.hasPermission(settings.permissions.admin) && !hasItems(player, costs)) {
            messages.send(player, "spawn-need-items");
            return true;
        }

        GolemType type = this.statueFactory.typeOf(hand);
        Location homeCenter = clicked.getLocation().add(0.5D, 0.0D, 0.5D);
        int radius = Math.max(1, settings.workRadius);
        Location chestLoc = this.chestService.findChestLocation(homeCenter, radius);
        if (chestLoc == null) {
            messages.send(player, "spawn-no-chest-space");
            return true;
        }
        if (type == GolemType.FARMER
                && this.chestService.findCraftingTableLocation(chestLoc, homeCenter, radius) == null) {
            messages.send(player, "spawn-no-craft-space");
            return true;
        }

        if (settings.activationXpLevels > 0 && !player.hasPermission(settings.permissions.admin)) {
            player.setLevel(player.getLevel() - settings.activationXpLevels);
        }
        if (!costs.isEmpty() && !player.hasPermission(settings.permissions.admin)) {
            takeItems(player, costs);
        }

        UUID golemId = UUID.randomUUID();
        SoulGolemData data = new SoulGolemData(golemId);
        data.ownerUuid(player.getUniqueId());
        data.type(type);
        data.worldName(homeCenter.getWorld().getName());
        Location spawnLoc = clicked.getLocation().add(0.5D, 1.0D, 0.5D);
        if (type == GolemType.FARMER) {
            spawnLoc = this.farmAreaService.farmerSpawnStand(clicked);
        }
        data.position(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ());
        data.home(clicked.getX() + 0.5D, clicked.getY(), clicked.getZ() + 0.5D);
        data.rotation(player.getLocation().getYaw(), 0.0D);
        data.chestPosition(chestLoc.getBlockX(), chestLoc.getBlockY(), chestLoc.getBlockZ());
        data.level(1);
        data.energy(settings.energyCapacity);
        data.paused(false);

        this.chestService.placeChest(chestLoc, golemId, player.getUniqueId());

        CopperGolem entity = spawnEntity(data);
        data.entityUuid(entity.getUniqueId());

        radius = this.chestService.effectiveRadius(data);
        this.workAreaService.setupArea(data, radius);
        this.workAreaService.protect(chestLoc.getBlock(), golemId);

        if (type == GolemType.FARMER) {
            Location craftLoc = this.chestService.findCraftingTableLocation(chestLoc, homeCenter, radius);
            if (craftLoc == null) {
                this.chestService.removeHologram(data);
                this.chestService.removeChestBlock(data);
                this.workAreaService.removeGolemArea(data, radius);
                entity.remove();
                messages.send(player, "spawn-no-craft-space");
                return true;
            }
            data.craftPosition(craftLoc.getBlockX(), craftLoc.getBlockY(), craftLoc.getBlockZ());
            this.chestService.placeCraftingTable(craftLoc, golemId, player.getUniqueId());
            this.workAreaService.protect(craftLoc.getBlock(), golemId);
            this.farmAreaService.ensureWater(data);
        }

        if (type == GolemType.MINER) {
            this.workAreaService.seedOres(data, radius, this.oreTableService);
        }

        ActiveGolem active = this.registry.wrap(data);
        if (type == GolemType.FARMER) {
            active.farmerState(FarmerState.WAITING_SEEDS);
            active.fieldReady(false);
        }
        this.registry.register(active);
        this.registry.bindEntity(data.id(), entity.getUniqueId());
        GolemDisplay.refreshForce(active, entity, messages(), this.keys, config().settings().visuals.textDisplays);
        active.markDirty();
        this.repository.save(data);

        consumeOne(hand, player);
        messages.send(player, type == GolemType.FARMER ? "spawn-success-farmer" : "spawn-success-miner");
        return true;
    }

    private boolean isPlacementBlock(Material type, Settings settings) {
        for (String name : settings.placementBlocks) {
            Material material = Material.matchMaterial(name);
            if (material == type) {
                return true;
            }
        }
        return false;
    }

    public CopperGolem spawnEntity(SoulGolemData data) {
        Location location = new Location(
                org.bukkit.Bukkit.getWorld(data.worldName()),
                data.x(),
                data.y(),
                data.z(),
                (float) data.yaw(),
                (float) data.pitch()
        );
        if (location.getWorld() == null) {
            throw new IllegalStateException("World missing for golem " + data.id());
        }
        return location.getWorld().spawn(location, CopperGolem.class, golem -> {
            applySoulEntityFlags(golem);
            clearHand(golem);
            equipTool(golem, data.type(), config().settings());
            golem.getPersistentDataContainer().set(this.keys.golemId(), PersistentDataType.STRING, data.id().toString());
            golem.getPersistentDataContainer().set(this.keys.owner(), PersistentDataType.STRING, data.ownerUuid().toString());
        });
    }

    public static void applySoulEntityFlags(CopperGolem golem) {
        golem.setCustomNameVisible(false);
        golem.customName(null);
        golem.setRemoveWhenFarAway(false);
        golem.setPersistent(true);
        golem.setAI(false);
        golem.setAware(false);
        golem.setCanPickupItems(false);
        golem.setSilent(false);
        golem.setInvulnerable(true);
        golem.setGravity(false);
        golem.setCollidable(true);
        golem.setFireTicks(0);
        golem.setRemainingAir(golem.getMaximumAir());
    }

    public void ensureAlive(SoulGolemData data) {
        if (data.entityUuid() != null) {
            Entity existing = org.bukkit.Bukkit.getEntity(data.entityUuid());
            if (existing instanceof CopperGolem copper && copper.isValid() && !copper.isDead()) {
                applySoulEntityFlags(copper);
                return;
            }
        }
        if (!this.respawning.add(data.id())) {
            return;
        }
        Location golemLoc = new Location(
                org.bukkit.Bukkit.getWorld(data.worldName()),
                data.x(),
                data.y(),
                data.z()
        );
        if (golemLoc.getWorld() == null) {
            this.respawning.remove(data.id());
            return;
        }
        PluginSchedulers.runAt(this.plugin, golemLoc, () -> {
            try {
                if (data.entityUuid() != null) {
                    Entity existing = org.bukkit.Bukkit.getEntity(data.entityUuid());
                    if (existing instanceof CopperGolem copper && copper.isValid() && !copper.isDead()) {
                        applySoulEntityFlags(copper);
                        configureExisting(copper, data);
                        return;
                    }
                }
                CopperGolem entity = spawnEntity(data);
                data.entityUuid(entity.getUniqueId());
                if (data.type() == GolemType.FARMER) {
                    Location safe = this.farmAreaService.safeStandNearHome(data);
                    if (safe != null) {
                        entity.teleport(safe);
                        data.position(safe.getX(), safe.getY(), safe.getZ());
                    }
                }
                Optional<ActiveGolem> active = this.registry.byId(data.id());
                if (active.isPresent()) {
                    this.registry.bindEntity(data.id(), entity.getUniqueId());
                    GolemDisplay.refreshForce(active.get(), entity, messages(), this.keys, config().settings().visuals.textDisplays);
                }
                this.repository.save(data);
            } finally {
                this.respawning.remove(data.id());
            }
        });
    }

    public static void clearHand(CopperGolem golem) {
        EntityEquipment equipment = golem.getEquipment();
        if (equipment == null) {
            return;
        }
        equipment.setItemInMainHand(ItemStack.empty());
        equipment.setItemInOffHand(ItemStack.empty());
        equipment.setItemInMainHandDropChance(0.0F);
        equipment.setItemInOffHandDropChance(0.0F);
    }

    public static void equipTool(CopperGolem golem, GolemType type, Settings settings) {
        EntityEquipment equipment = golem.getEquipment();
        if (equipment == null) {
            return;
        }
        Material tool = resolveTool(type, settings);
        ItemStack current = equipment.getItemInMainHand();
        if (current != null && !current.isEmpty() && current.getType() == tool) {
            equipment.setItemInMainHandDropChance(0.0F);
            equipment.setItemInOffHandDropChance(0.0F);
            return;
        }
        equipment.setItemInMainHand(new ItemStack(tool), true);
        equipment.setItemInOffHand(ItemStack.empty(), true);
        equipment.setItemInMainHandDropChance(0.0F);
        equipment.setItemInOffHandDropChance(0.0F);
    }

    public static Material resolveTool(GolemType type, Settings settings) {
        if (type == GolemType.FARMER) {
            Material hoe = Material.matchMaterial(settings.hoeMaterial);
            return hoe != null ? hoe : Material.IRON_HOE;
        }
        Material pickaxe = Material.matchMaterial(settings.pickaxeMaterial);
        return pickaxe != null ? pickaxe : Material.IRON_PICKAXE;
    }

    public void respawnInWorld(SoulGolemData data) {
        Location golemLoc = new Location(
                org.bukkit.Bukkit.getWorld(data.worldName()),
                data.x(),
                data.y(),
                data.z()
        );
        if (golemLoc.getWorld() == null) {
            return;
        }
        PluginSchedulers.runAt(this.plugin, golemLoc, () -> {
            Location chest = new Location(
                    golemLoc.getWorld(),
                    data.chestX(),
                    data.chestY(),
                    data.chestZ()
            );
            this.chestService.tagExistingChest(chest, data.id(), data.ownerUuid());
            if (data.hasCraftStation()) {
                Location craft = new Location(
                        golemLoc.getWorld(),
                        data.craftX(),
                        data.craftY(),
                        data.craftZ()
                );
                this.chestService.tagExistingCraftingTable(craft, data.id(), data.ownerUuid());
            }

            CopperGolem entity = null;
            if (data.entityUuid() != null) {
                Entity existing = org.bukkit.Bukkit.getEntity(data.entityUuid());
                if (existing instanceof CopperGolem copper && copper.isValid()) {
                    entity = copper;
                    configureExisting(entity, data);
                }
            }
            if (entity == null) {
                entity = spawnEntity(data);
            }

            int radius = this.chestService.effectiveRadius(data);
            this.workAreaService.setupArea(data, radius);
            this.workAreaService.protect(chest.getBlock(), data.id());
            if (data.hasCraftStation()) {
                this.workAreaService.protect(
                        golemLoc.getWorld().getBlockAt(
                                (int) Math.floor(data.craftX()),
                                (int) Math.floor(data.craftY()),
                                (int) Math.floor(data.craftZ())
                        ),
                        data.id()
                );
            }

            if (data.type() == GolemType.MINER) {
                this.workAreaService.seedOres(data, radius, this.oreTableService);
            }
            if (data.type() == GolemType.FARMER) {
                this.farmAreaService.ensureWater(data);
            }

            ActiveGolem active = this.registry.wrap(data);
            if (data.type() == GolemType.FARMER) {
                active.farmerState(FarmerState.WAITING_SEEDS);
                active.fieldReady(false);
            }
            this.registry.register(active);
            this.registry.bindEntity(data.id(), entity.getUniqueId());
            GolemDisplay.refreshForce(active, entity, messages(), this.keys, config().settings().visuals.textDisplays);
            this.repository.save(data);
        });
    }

    private void configureExisting(CopperGolem entity, SoulGolemData data) {
        applySoulEntityFlags(entity);
        clearHand(entity);
        equipTool(entity, data.type(), config().settings());
        entity.getPersistentDataContainer().set(this.keys.golemId(), PersistentDataType.STRING, data.id().toString());
        entity.getPersistentDataContainer().set(this.keys.owner(), PersistentDataType.STRING, data.ownerUuid().toString());
    }

    private void consumeOne(ItemStack hand, Player player) {
        int amount = hand.getAmount();
        if (amount <= 1) {
            player.getInventory().setItemInMainHand(ItemStack.empty());
        } else {
            hand.setAmount(amount - 1);
        }
    }

    private static Map<Material, Integer> parseItemCosts(java.util.List<String> raw) {
        Map<Material, Integer> map = new HashMap<>();
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String[] parts = entry.split(":");
            Material material = Material.matchMaterial(parts[0].trim().toUpperCase(Locale.ROOT));
            if (material == null) {
                continue;
            }
            int amount = 1;
            if (parts.length > 1) {
                try {
                    amount = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException ignored) {
                    amount = 1;
                }
            }
            map.merge(material, Math.max(1, amount), Integer::sum);
        }
        return map;
    }

    private static boolean hasItems(Player player, Map<Material, Integer> costs) {
        for (Map.Entry<Material, Integer> entry : costs.entrySet()) {
            if (!player.getInventory().containsAtLeast(new ItemStack(entry.getKey()), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static void takeItems(Player player, Map<Material, Integer> costs) {
        for (Map.Entry<Material, Integer> entry : costs.entrySet()) {
            player.getInventory().removeItem(new ItemStack(entry.getKey(), entry.getValue()));
        }
    }

    public void removeGolem(UUID golemId, Player notifier) {
        java.util.Optional<ActiveGolem> optional = this.registry.byId(golemId);
        if (optional.isEmpty()) {
            this.workAreaService.clear(golemId);
            this.farmAreaService.clear(golemId);
            this.repository.delete(golemId);
            return;
        }
        ActiveGolem golem = optional.get();
        SoulGolemData data = golem.data();
        int radius = this.chestService.effectiveRadius(data);
        this.chestService.removeHologram(data);
        this.chestService.removeCraftingTable(data);
        this.chestService.removeChestBlock(data);
        this.farmAreaService.removeSeat(data);
        this.farmAreaService.removeBorderTorches(data, radius);
        this.farmAreaService.clear(golemId);
        this.workAreaService.removeGolemArea(data, radius);
        Location sweep = new Location(
                org.bukkit.Bukkit.getWorld(data.worldName()),
                data.homeX(),
                data.homeY(),
                data.homeZ()
        );
        if (sweep.getWorld() != null) {
            GolemDisplay.removeAllNear(sweep.getWorld(), sweep, radius + 16.0D, golemId.toString(), this.keys);
        }
        if (data.entityUuid() != null) {
            Entity entity = org.bukkit.Bukkit.getEntity(data.entityUuid());
            if (entity != null && entity.isValid()) {
                if (entity instanceof CopperGolem copper) {
                    GolemDisplay.remove(copper, golemId.toString(), this.keys);
                }
                for (Entity passenger : List.copyOf(entity.getPassengers())) {
                    passenger.remove();
                }
                entity.remove();
            }
        }
        data.clearCraftPosition();
        this.registry.unregister(golemId);
        this.repository.delete(golemId);
        if (notifier != null) {
            messages().send(notifier, "golem-removed");
        }
    }
}
