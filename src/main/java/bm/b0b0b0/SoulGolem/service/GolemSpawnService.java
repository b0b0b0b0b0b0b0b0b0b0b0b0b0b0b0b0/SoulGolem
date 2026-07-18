package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.config.PluginConfig;
import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.item.StatueItemFactory;
import bm.b0b0b0.SoulGolem.message.MessageService;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.DiggerState;
import bm.b0b0b0.SoulGolem.model.FarmerState;
import bm.b0b0b0.SoulGolem.model.GolemType;
import bm.b0b0b0.SoulGolem.model.MinerState;
import bm.b0b0b0.SoulGolem.model.SetupPhase;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.repository.GolemRepository;
import bm.b0b0b0.SoulGolem.scheduler.PluginSchedulers;
import bm.b0b0b0.SoulGolem.service.digger.DiggerDigWork;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import io.papermc.paper.world.WeatheringCopperState;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

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
    private final GolemGazeService gazeService;
    private final Set<UUID> respawning = ConcurrentHashMap.newKeySet();
    private volatile long bootQuietUntilMs;

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
        this.gazeService = new GolemGazeService(plugin, registry);
    }

    public GolemGazeService gazeService() {
        return this.gazeService;
    }

    public void beginBootQuiet(long durationMs) {
        this.bootQuietUntilMs = System.currentTimeMillis() + Math.max(1000L, durationMs);
    }

    public boolean isBootQuiet() {
        return System.currentTimeMillis() < this.bootQuietUntilMs;
    }

    public void persistLivePositions() {
        for (ActiveGolem golem : this.registry.all()) {
            SoulGolemData data = golem.data();
            CopperGolem copper = resolveLiveEntity(data, null);
            if (copper == null || !copper.isValid()) {
                continue;
            }
            Location at = copper.getLocation();
            data.position(at.getX(), at.getY(), at.getZ());
            data.rotation(at.getYaw(), at.getPitch());
            data.entityUuid(copper.getUniqueId());
            golem.markDirty();
        }
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
        GolemSettings golems = config().golems();
        Location homeCenter = clicked.getLocation().add(0.5D, 0.0D, 0.5D);
        int radius = type == GolemType.DIGGER
                ? Math.max(2, golems.digger.pitSize / 2)
                : Math.max(1, golems.workRadius);
        if (overlapsExistingArea(homeCenter, radius, null)) {
            messages.send(player, "spawn-area-overlap");
            return true;
        }
        Location chestLoc = this.chestService.findChestLocation(homeCenter, radius);
        if (chestLoc == null) {
            messages.send(player, "spawn-no-chest-space");
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
        data.energy(golems.energyCapacity);
        data.paused(false);

        CopperGolem entity = spawnEntity(data);
        data.entityUuid(entity.getUniqueId());

        ActiveGolem active = this.registry.wrap(data);
        active.setupComplete(false);
        if (type == GolemType.FARMER) {
            active.farmerState(FarmerState.MOVING_TO_SETUP_CLEAR);
            active.fieldReady(false);
        } else if (type == GolemType.DIGGER) {
            active.diggerState(DiggerState.MOVING_TO_SETUP_CLEAR);
        } else {
            active.state(MinerState.MOVING_TO_SETUP_CLEAR);
        }
        active.setupPhase(SetupPhase.CLEAR);
        this.registry.register(active);
        this.registry.bindEntity(data.id(), entity.getUniqueId());
        refreshSoulEntity(entity, type);
        GolemDisplay.refreshForce(active, entity, messages(), this.keys, config().golems().visuals.textDisplays);
        active.markDirty();
        this.repository.save(data);

        consumeOne(hand, player);
        String spawnKey = switch (type) {
            case FARMER -> "spawn-success-farmer";
            case DIGGER -> "spawn-success-digger";
            default -> "spawn-success-miner";
        };
        messages.send(player, spawnKey);
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
            applyBaseFlags(golem, data.type());
            applyDiggerLeaderGlow(golem, data);
            golem.setAware(false);
            Bukkit.getMobGoals().removeAllGoals(golem);
            clearHand(golem);
            equipTool(golem, data.type(), config().golems());
            golem.getPersistentDataContainer().set(this.keys.golemId(), PersistentDataType.STRING, data.id().toString());
            golem.getPersistentDataContainer().set(this.keys.owner(), PersistentDataType.STRING, data.ownerUuid().toString());
            this.gazeService.ensure(golem);
        });
    }

    public static void applySoulEntityFlags(CopperGolem golem) {
        applyBaseFlags(golem, GolemType.MINER);
    }

    public static void applySoulEntityFlags(CopperGolem golem, GolemType type) {
        applyBaseFlags(golem, type);
    }

    public void refreshSoulEntity(CopperGolem golem, GolemType type) {
        applyBaseFlags(golem, type);
        this.gazeService.ensure(golem);
    }

    private static void applyBaseFlags(CopperGolem golem, GolemType type) {
        golem.setCustomNameVisible(false);
        golem.customName(null);
        golem.setRemoveWhenFarAway(false);
        golem.setPersistent(true);
        golem.setAI(true);
        golem.setCanPickupItems(false);
        golem.setSilent(false);
        golem.setInvulnerable(false);
        golem.setCollidable(true);
        golem.setFireTicks(0);
        golem.setRemainingAir(golem.getMaximumAir());
        golem.setTarget(null);
        GolemType resolved = type == null ? GolemType.MINER : type;
        if (resolved == GolemType.FARMER) {
            golem.setWeatheringState(WeatheringCopperState.OXIDIZED);
        } else if (resolved == GolemType.DIGGER) {
            golem.setWeatheringState(WeatheringCopperState.EXPOSED);
        } else {
            golem.setWeatheringState(WeatheringCopperState.UNAFFECTED);
        }
        golem.setOxidizing(CopperGolem.Oxidizing.waxed());
    }

    private boolean overlapsExistingArea(Location homeCenter, int radius, UUID excludeId) {
        if (homeCenter.getWorld() == null) {
            return false;
        }
        String worldName = homeCenter.getWorld().getName();
        int homeX = homeCenter.getBlockX();
        int homeZ = homeCenter.getBlockZ();
        int r = Math.max(1, radius);
        int padding = Math.max(0, config().settings().areaOverlapPadding);
        for (ActiveGolem other : this.registry.all()) {
            SoulGolemData data = other.data();
            if (excludeId != null && data.id().equals(excludeId)) {
                continue;
            }
            if (!worldName.equals(data.worldName())) {
                continue;
            }
            int otherRadius = Math.max(1, this.chestService.effectiveRadius(data));
            int otherX = (int) Math.floor(data.homeX());
            int otherZ = (int) Math.floor(data.homeZ());
            if (Math.abs(homeX - otherX) <= r + otherRadius + padding
                    && Math.abs(homeZ - otherZ) <= r + otherRadius + padding) {
                return true;
            }
        }
        return false;
    }

    public void ensureAlive(SoulGolemData data) {
        CopperGolem live = resolveLiveEntity(data, null);
        if (live != null) {
            refreshSoulEntity(live, data.type());
            return;
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
                CopperGolem existing = resolveLiveEntity(data, golemLoc);
                if (existing != null) {
                    configureExisting(existing, data);
                    this.registry.bindEntity(data.id(), existing.getUniqueId());
                    this.repository.save(data);
                    return;
                }
                removeTaggedDuplicates(data.id(), null);
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
                    GolemDisplay.refreshForce(active.get(), entity, messages(), this.keys, config().golems().visuals.textDisplays);
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

    public static void equipTool(CopperGolem golem, GolemType type, GolemSettings settings) {
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

    public static Material resolveTool(GolemType type, GolemSettings settings) {
        if (type == GolemType.FARMER) {
            Material hoe = Material.matchMaterial(settings.hoeMaterial);
            if (hoe != null) {
                return hoe;
            }
            Material copperHoe = Material.matchMaterial("COPPER_HOE");
            return copperHoe != null ? copperHoe : Material.IRON_HOE;
        }
        if (type == GolemType.DIGGER) {
            Material shovel = Material.matchMaterial(settings.digger.shovelMaterial);
            if (shovel != null) {
                return shovel;
            }
            Material copperShovel = Material.matchMaterial("COPPER_SHOVEL");
            return copperShovel != null ? copperShovel : Material.IRON_SHOVEL;
        }
        Material pickaxe = Material.matchMaterial(settings.pickaxeMaterial);
        if (pickaxe != null) {
            return pickaxe;
        }
        Material copperPickaxe = Material.matchMaterial("COPPER_PICKAXE");
        return copperPickaxe != null ? copperPickaxe : Material.IRON_PICKAXE;
    }

    public void respawnInWorld(SoulGolemData data) {
        respawnInWorld(data, null);
    }

    public void respawnInWorld(SoulGolemData data, Runnable onDone) {
        Location golemLoc = new Location(
                org.bukkit.Bukkit.getWorld(data.worldName()),
                data.x(),
                data.y(),
                data.z()
        );
        if (golemLoc.getWorld() == null) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        PluginSchedulers.runAt(this.plugin, golemLoc, () -> {
            try {
            Location chest = new Location(
                    golemLoc.getWorld(),
                    data.chestX(),
                    data.chestY(),
                    data.chestZ()
            );
            boolean chestPresent = this.chestService.isSoulChest(chest.getBlock())
                    || SoulChestService.isChestLike(chest.getBlock().getType());

            CopperGolem entity = resolveLiveEntity(data, golemLoc);
            if (entity == null) {
                removeTaggedDuplicates(data.id(), null);
                entity = spawnEntity(data);
            } else {
                configureExisting(entity, data);
            }

            ActiveGolem active = this.registry.wrap(data);
            int radius = this.chestService.effectiveRadius(data);
            boolean helper = data.isCrewHelper();

            if (helper) {
                active.setupComplete(true);
                active.setupPhase(SetupPhase.DONE);
                if (data.type() == GolemType.DIGGER) {
                    DiggerDigWork.ensureDigProgress(data, config().golems().digger, this.farmAreaService);
                    bm.b0b0b0.SoulGolem.service.digger.DiggerPit.clearStairStructureAboveGround(data, config().golems().digger);
                    active.diggerState(DiggerState.IDLE);
                }
            } else if (chestPresent) {
                this.workAreaService.setupArea(data, radius);
                this.workAreaService.reclaimTerritory(data, radius);
                this.farmAreaService.reprotectSeat(data);
                this.chestService.clearStationColumn(chest);
                this.chestService.tagExistingChest(
                        chest,
                        data.id(),
                        data.ownerUuid(),
                        new Location(golemLoc.getWorld(), data.homeX(), data.homeY(), data.homeZ())
                );
                this.workAreaService.protect(chest.getBlock(), data.id());
                if (data.hasCraftStation()) {
                    Location craft = new Location(
                            golemLoc.getWorld(),
                            data.craftX(),
                            data.craftY(),
                            data.craftZ()
                    );
                    this.chestService.clearStationColumn(craft);
                    this.chestService.tagExistingCraftingTable(craft, data.id(), data.ownerUuid());
                    this.workAreaService.protect(craft.getBlock(), data.id());
                }
                if (data.hasCompostStation()) {
                    Location compost = new Location(
                            golemLoc.getWorld(),
                            data.compostX(),
                            data.compostY(),
                            data.compostZ()
                    );
                    this.chestService.clearStationColumn(compost);
                    this.chestService.tagExistingComposter(compost, data.id());
                    this.workAreaService.protect(compost.getBlock(), data.id());
                }
                if (data.type() == GolemType.MINER) {
                    this.workAreaService.seedOres(data, radius, this.oreTableService);
                    active.state(MinerState.IDLE);
                }
                if (data.type() == GolemType.FARMER) {
                    this.farmAreaService.ensureWater(data);
                    active.farmerState(FarmerState.WAITING_SEEDS);
                    active.fieldReady(false);
                }
                if (data.type() == GolemType.DIGGER) {
                    DiggerDigWork.ensureDigProgress(data, config().golems().digger, this.farmAreaService);
                    bm.b0b0b0.SoulGolem.service.digger.DiggerPit.clearStairStructureAboveGround(data, config().golems().digger);
                    active.diggerState(DiggerState.IDLE);
                }
                active.setupComplete(true);
                active.setupPhase(SetupPhase.DONE);
            } else {
                active.setupComplete(false);
                active.setupPhase(SetupPhase.CLEAR);
                if (data.type() == GolemType.FARMER) {
                    active.farmerState(FarmerState.MOVING_TO_SETUP_CLEAR);
                    active.fieldReady(false);
                } else if (data.type() == GolemType.DIGGER) {
                    active.diggerState(DiggerState.MOVING_TO_SETUP_CLEAR);
                } else {
                    active.state(MinerState.MOVING_TO_SETUP_CLEAR);
                }
            }

            this.registry.register(active);
            this.registry.bindEntity(data.id(), entity.getUniqueId());
            data.entityUuid(entity.getUniqueId());
            removeTaggedDuplicates(data.id(), entity.getUniqueId());
            GolemDisplay.refreshForce(active, entity, messages(), this.keys, config().golems().visuals.textDisplays);
            this.repository.save(data);
            } finally {
                if (onDone != null) {
                    onDone.run();
                }
            }
        });
    }

    private void configureExisting(CopperGolem entity, SoulGolemData data) {
        refreshSoulEntity(entity, data.type());
        applyDiggerLeaderGlow(entity, data);
        clearHand(entity);
        equipTool(entity, data.type(), config().golems());
        entity.getPersistentDataContainer().set(this.keys.golemId(), PersistentDataType.STRING, data.id().toString());
        entity.getPersistentDataContainer().set(this.keys.owner(), PersistentDataType.STRING, data.ownerUuid().toString());
    }

    public static void applyDiggerLeaderGlow(CopperGolem golem, SoulGolemData data) {
        if (golem == null || data == null) {
            return;
        }
        boolean leader = data.type() == GolemType.DIGGER && !data.isCrewHelper();
        golem.setGlowing(leader);
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam("sg_digger_lead");
        if (leader) {
            if (team == null) {
                team = board.registerNewTeam("sg_digger_lead");
                team.color(NamedTextColor.LIGHT_PURPLE);
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
                team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            }
            if (!team.hasEntity(golem)) {
                team.addEntity(golem);
            }
            return;
        }
        if (team != null && team.hasEntity(golem)) {
            team.removeEntity(golem);
        }
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
        if (optional.isPresent()) {
            tearDownGolem(optional.get().data(), optional.get());
        } else {
            java.util.Optional<SoulGolemData> stored = this.repository.findById(golemId).join();
            if (stored.isPresent()) {
                tearDownGolem(stored.get(), null);
            } else {
                this.workAreaService.clear(golemId);
                this.farmAreaService.clear(golemId);
                this.repository.delete(golemId);
            }
        }
        if (notifier != null) {
            messages().send(notifier, "golem-removed");
        }
    }

    private void tearDownGolem(SoulGolemData data, ActiveGolem golem) {
        if (data.type() == GolemType.DIGGER && !data.isCrewHelper()) {
            List<UUID> helpers = new ArrayList<>();
            for (ActiveGolem other : this.registry.all()) {
                if (data.id().equals(other.data().crewLeaderId())) {
                    helpers.add(other.data().id());
                }
            }
            for (UUID helperId : helpers) {
                Optional<ActiveGolem> helper = this.registry.byId(helperId);
                if (helper.isPresent()) {
                    tearDownHelperOnly(helper.get().data(), helper.get());
                }
            }
        }

        if (data.isCrewHelper()) {
            tearDownHelperOnly(data, golem);
            return;
        }

        int radius = this.chestService.effectiveRadius(data);
        this.chestService.removeHologram(data);
        this.chestService.removeCraftingTable(data);
        this.chestService.removeComposter(data);
        this.chestService.removeChestBlock(data);
        this.farmAreaService.removeGolemArea(data, radius, this.oreTableService);
        this.workAreaService.removeGolemArea(data, radius, this.oreTableService);
        Location sweep = new Location(
                org.bukkit.Bukkit.getWorld(data.worldName()),
                data.homeX(),
                data.homeY(),
                data.homeZ()
        );
        if (sweep.getWorld() != null) {
            GolemDisplay.removeAllNear(sweep.getWorld(), sweep, radius + 16.0D, data.id().toString(), this.keys);
        }
        removeEntityForData(data);
        data.clearCraftPosition();
        if (golem != null) {
            this.registry.unregister(data.id());
        }
        this.repository.delete(data.id());
    }

    public ItemStack createStatue(GolemType type) {
        return this.statueFactory.create(1, type);
    }

    public void removeCrewHelper(ActiveGolem golem) {
        if (golem == null) {
            return;
        }
        tearDownHelperOnly(golem.data(), golem);
    }

    private void tearDownHelperOnly(SoulGolemData data, ActiveGolem golem) {
        removeEntityForData(data);
        if (golem != null) {
            this.registry.unregister(data.id());
        }
        this.repository.delete(data.id());
    }

    private void removeEntityForData(SoulGolemData data) {
        if (data.entityUuid() == null) {
            return;
        }
        Entity entity = org.bukkit.Bukkit.getEntity(data.entityUuid());
        if (entity == null || !entity.isValid()) {
            return;
        }
        if (entity instanceof CopperGolem copper) {
            GolemDisplay.remove(copper, data.id().toString(), this.keys);
        }
        for (Entity passenger : List.copyOf(entity.getPassengers())) {
            passenger.remove();
        }
        entity.remove();
    }

    public void cleanupOrphan(UUID golemId, Location near, Player notifier) {
        if (this.registry.byId(golemId).isPresent()) {
            removeGolem(golemId, notifier);
            return;
        }
        java.util.Optional<SoulGolemData> stored = this.repository.findById(golemId).join();
        if (stored.isPresent()) {
            if (near != null && near.getWorld() != null) {
                for (Entity entity : near.getWorld().getNearbyEntities(near, 12.0D, 12.0D, 12.0D)) {
                    if (!(entity instanceof CopperGolem copper)) {
                        continue;
                    }
                    String raw = entity.getPersistentDataContainer().get(this.keys.golemId(), PersistentDataType.STRING);
                    if (golemId.toString().equals(raw)) {
                        removeEntity(copper, golemId);
                    }
                }
            }
            tearDownGolem(stored.get(), null);
            if (notifier != null) {
                messages().send(notifier, "golem-removed");
            }
            return;
        }
        int radius = Math.max(3, config().golems().workRadius + 2);
        if (near != null && near.getWorld() != null) {
            this.chestService.removeStationsNear(near, golemId, radius);
            for (Entity entity : near.getWorld().getNearbyEntities(near, radius + 8.0D, radius + 8.0D, radius + 8.0D)) {
                if (!(entity instanceof CopperGolem copper)) {
                    continue;
                }
                String raw = entity.getPersistentDataContainer().get(this.keys.golemId(), PersistentDataType.STRING);
                if (golemId.toString().equals(raw)) {
                    removeEntity(copper, golemId);
                }
            }
            GolemDisplay.removeAllNear(near.getWorld(), near, radius + 16.0D, golemId.toString(), this.keys);
        }
        this.workAreaService.clear(golemId);
        this.farmAreaService.clear(golemId);
        this.repository.delete(golemId);
        if (notifier != null) {
            messages().send(notifier, "golem-removed");
        }
    }

    public void purgeOrphanEntities() {
        boolean quiet = isBootQuiet();
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (CopperGolem copper : List.copyOf(world.getEntitiesByClass(CopperGolem.class))) {
                if (!copper.isValid() || copper.isDead()) {
                    continue;
                }
                String raw = copper.getPersistentDataContainer().get(this.keys.golemId(), PersistentDataType.STRING);
                if (raw == null || raw.isEmpty()) {
                    continue;
                }
                UUID golemId;
                try {
                    golemId = UUID.fromString(raw);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                Optional<ActiveGolem> active = this.registry.byId(golemId);
                if (active.isPresent()) {
                    UUID bound = active.get().data().entityUuid();
                    if (bound != null && !bound.equals(copper.getUniqueId())) {
                        removeEntity(copper, golemId);
                    }
                    continue;
                }
                if (quiet) {
                    continue;
                }
                Optional<SoulGolemData> stored;
                try {
                    stored = this.repository.findById(golemId).join();
                } catch (Exception ignored) {
                    stored = Optional.empty();
                }
                if (stored.isPresent()) {
                    respawnInWorld(stored.get());
                    continue;
                }
                removeEntity(copper, golemId);
            }
        }
    }

    public void handleChunkSoulEntity(CopperGolem copper) {
        if (copper == null || !copper.isValid() || copper.isDead()) {
            return;
        }
        String raw = copper.getPersistentDataContainer().get(this.keys.golemId(), PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return;
        }
        UUID golemId;
        try {
            golemId = UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        Optional<ActiveGolem> active = this.registry.byId(golemId);
        if (active.isPresent()) {
            UUID bound = active.get().data().entityUuid();
            if (bound == null || bound.equals(copper.getUniqueId())) {
                configureExisting(copper, active.get().data());
                active.get().data().entityUuid(copper.getUniqueId());
                this.registry.bindEntity(golemId, copper.getUniqueId());
            } else {
                removeEntity(copper, golemId);
            }
            return;
        }
        if (isBootQuiet()) {
            return;
        }
        removeEntity(copper, golemId);
    }

    public void removeAllSoulEntities() {
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (CopperGolem copper : List.copyOf(world.getEntitiesByClass(CopperGolem.class))) {
                String raw = copper.getPersistentDataContainer().get(this.keys.golemId(), PersistentDataType.STRING);
                if (raw == null || raw.isEmpty()) {
                    continue;
                }
                UUID golemId;
                try {
                    golemId = UUID.fromString(raw);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                removeEntity(copper, golemId);
            }
        }
    }

    private CopperGolem resolveLiveEntity(SoulGolemData data, Location near) {
        if (data.entityUuid() != null) {
            Entity existing = org.bukkit.Bukkit.getEntity(data.entityUuid());
            if (existing instanceof CopperGolem copper && copper.isValid() && !copper.isDead()) {
                return copper;
            }
        }
        org.bukkit.World world = near != null
                ? near.getWorld()
                : org.bukkit.Bukkit.getWorld(data.worldName());
        if (world == null) {
            return null;
        }
        String want = data.id().toString();
        CopperGolem found = null;
        for (CopperGolem copper : world.getEntitiesByClass(CopperGolem.class)) {
            if (!copper.isValid() || copper.isDead()) {
                continue;
            }
            String raw = copper.getPersistentDataContainer().get(this.keys.golemId(), PersistentDataType.STRING);
            if (!want.equals(raw)) {
                continue;
            }
            if (found == null) {
                found = copper;
            } else {
                removeEntity(copper, data.id());
            }
        }
        if (found != null) {
            data.entityUuid(found.getUniqueId());
        }
        return found;
    }

    private void removeTaggedDuplicates(UUID golemId, UUID keepEntityUuid) {
        String want = golemId.toString();
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (CopperGolem copper : world.getEntitiesByClass(CopperGolem.class)) {
                if (!copper.isValid()) {
                    continue;
                }
                String raw = copper.getPersistentDataContainer().get(this.keys.golemId(), PersistentDataType.STRING);
                if (!want.equals(raw)) {
                    continue;
                }
                if (keepEntityUuid != null && keepEntityUuid.equals(copper.getUniqueId())) {
                    continue;
                }
                removeEntity(copper, golemId);
            }
        }
    }

    private void removeEntity(CopperGolem copper, UUID golemId) {
        this.gazeService.stop(copper.getUniqueId());
        GolemDisplay.remove(copper, golemId.toString(), this.keys);
        for (Entity passenger : List.copyOf(copper.getPassengers())) {
            passenger.remove();
        }
        copper.remove();
    }
}
