package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.message.MessageService;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.FarmerState;
import bm.b0b0b0.SoulGolem.model.GolemType;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.repository.GolemRepository;
import bm.b0b0b0.SoulGolem.scheduler.PluginSchedulers;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

public final class FarmerTickService {

    private final Plugin plugin;
    private final ConfigurationLoader configurationLoader;
    private final PluginKeys keys;
    private final GolemRegistry registry;
    private final SoulChestService chestService;
    private final WorkAreaService workAreaService;
    private final FarmAreaService farmAreaService;
    private final GolemRepository repository;
    private final GolemSpawnService spawnService;
    private final AtomicInteger cursor = new AtomicInteger();
    private ScheduledTask coordinatorTask;
    private ScheduledTask saveTask;

    public FarmerTickService(
            Plugin plugin,
            ConfigurationLoader configurationLoader,
            PluginKeys keys,
            GolemRegistry registry,
            SoulChestService chestService,
            WorkAreaService workAreaService,
            FarmAreaService farmAreaService,
            GolemRepository repository,
            GolemSpawnService spawnService
    ) {
        this.plugin = plugin;
        this.configurationLoader = configurationLoader;
        this.keys = keys;
        this.registry = registry;
        this.chestService = chestService;
        this.workAreaService = workAreaService;
        this.farmAreaService = farmAreaService;
        this.repository = repository;
        this.spawnService = spawnService;
    }

    private Settings settings() {
        return this.configurationLoader.config().settings();
    }

    private MessageService messages() {
        return this.configurationLoader.messages();
    }

    private GolemMovement movement() {
        return new GolemMovement(settings(), this.chestService, this.farmAreaService);
    }

    public void start() {
        stop();
        long period = Math.max(1L, settings().coordinatorPeriodTicks);
        this.coordinatorTask = PluginSchedulers.runGlobalTimer(this.plugin, this::coordinatorTick, period, period);
        this.saveTask = PluginSchedulers.runAsyncTimer(this.plugin, this::flushDirty, 15L, 15L, TimeUnit.SECONDS);
    }

    public void stop() {
        if (this.coordinatorTask != null) {
            this.coordinatorTask.cancel();
            this.coordinatorTask = null;
        }
        if (this.saveTask != null) {
            this.saveTask.cancel();
            this.saveTask = null;
        }
        flushDirtySync();
    }

    private void coordinatorTick() {
        List<ActiveGolem> farmers = new ArrayList<>();
        for (ActiveGolem golem : this.registry.all()) {
            if (golem.data().type() == GolemType.FARMER) {
                farmers.add(golem);
            }
        }
        if (farmers.isEmpty()) {
            return;
        }
        int batch = Math.max(1, Math.min(settings().golemsPerCoordinatorTick, farmers.size()));
        int size = farmers.size();
        int start = Math.floorMod(this.cursor.getAndAdd(batch), size);
        for (int i = 0; i < batch; i++) {
            ActiveGolem golem = farmers.get((start + i) % size);
            SoulGolemData data = golem.data();
            Location home = this.workAreaService.homeLocation(data);
            if (home == null) {
                continue;
            }
            PluginSchedulers.runAt(this.plugin, home, () -> tickGolem(golem));
        }
    }

    private void tickGolem(ActiveGolem golem) {
        SoulGolemData data = golem.data();
        Entity entity = resolveEntity(data);
        if (!(entity instanceof CopperGolem copper)) {
            this.spawnService.ensureAlive(data);
            return;
        }

        long now = System.currentTimeMillis();
        long intervalMs = Math.max(1L, settings().workIntervalTicks) * 50L;
        FarmerState state = golem.farmerState();
        boolean busyMove = state == FarmerState.MOVING_TO_PLANT
                || state == FarmerState.MOVING_TO_HARVEST
                || state == FarmerState.MOVING_TO_CHEST
                || state == FarmerState.MOVING_TO_CRAFT
                || state == FarmerState.MOVING_TO_TORCH
                || state == FarmerState.MOVING_TO_SEAT
                || state == FarmerState.MOVING_TO_CLEAR
                || state == FarmerState.MOVING_TO_BONEMEAL
                || state == FarmerState.PLANTING
                || state == FarmerState.HARVESTING
                || state == FarmerState.CRAFTING
                || state == FarmerState.PLACING_TORCH
                || state == FarmerState.PLACING_SEAT
                || state == FarmerState.CLEARING
                || state == FarmerState.APPLYING_BONEMEAL
                || state == FarmerState.PREPARE_FIELD
                || state == FarmerState.MOVING_TO_TILL
                || state == FarmerState.TILLING
                || state == FarmerState.WANDERING
                || state == FarmerState.SITTING
                || state == FarmerState.RESTING
                || golem.fetchingSeed()
                || golem.fetchingTorch()
                || golem.fetchingBoneMeal()
                || golem.fetchingSeat();
        if (!busyMove && now - data.lastActionAt() < intervalMs) {
            if (state != FarmerState.WAITING_SEEDS || golem.fieldReady()) {
                return;
            }
        }

        GolemSpawnService.applySoulEntityFlags(copper);
        boolean workingMove = state == FarmerState.MOVING_TO_CLEAR
                || state == FarmerState.CLEARING
                || state == FarmerState.PREPARE_FIELD
                || state == FarmerState.MOVING_TO_TILL
                || state == FarmerState.TILLING
                || state == FarmerState.MOVING_TO_PLANT
                || state == FarmerState.PLANTING
                || state == FarmerState.MOVING_TO_HARVEST
                || state == FarmerState.HARVESTING
                || state == FarmerState.MOVING_TO_BONEMEAL
                || state == FarmerState.APPLYING_BONEMEAL
                || state == FarmerState.MOVING_TO_TORCH
                || state == FarmerState.PLACING_TORCH
                || state == FarmerState.MOVING_TO_CHEST
                || state == FarmerState.MOVING_TO_CRAFT;
        if (!workingMove
                && state != FarmerState.WAITING_SEEDS
                && this.farmAreaService.needsRescue(copper.getLocation(), data)) {
            Location safe = this.farmAreaService.safeStandNearHome(data);
            if (safe != null) {
                copper.teleport(safe);
            }
        }
        if (state == FarmerState.MOVING_TO_CLEAR || state == FarmerState.CLEARING) {
            equipShovel(copper);
        } else {
            GolemSpawnService.equipTool(copper, data.type(), settings());
        }

        Settings.TextDisplays style = settings().visuals.textDisplays;
        if (data.paused()) {
            GolemDisplay.refresh(golem, copper, messages(), this.keys, style);
            return;
        }

        data.position(copper.getLocation().getX(), copper.getLocation().getY(), copper.getLocation().getZ());
        data.rotation(copper.getLocation().getYaw(), copper.getLocation().getPitch());

        switch (state) {
            case WAITING_SEEDS -> beginCycle(golem, copper);
            case PREPARE_FIELD, MOVING_TO_TILL, TILLING -> continueTill(golem, copper);
            case MOVING_TO_PLANT, PLANTING -> continuePlant(golem, copper);
            case WAIT_GROWTH, WANDERING -> waitGrowth(golem, copper);
            case MOVING_TO_HARVEST, HARVESTING -> continueHarvest(golem, copper);
            case MOVING_TO_CHEST -> continueDeposit(golem, copper);
            case WAITING_CHEST -> retryChest(golem);
            case MOVING_TO_CRAFT, CRAFTING -> continueCraft(golem, copper);
            case MOVING_TO_TORCH, PLACING_TORCH -> continueTorch(golem, copper);
            case MOVING_TO_SEAT, PLACING_SEAT, SITTING -> continueSeat(golem, copper);
            case MOVING_TO_CLEAR, CLEARING -> continueClear(golem, copper);
            case MOVING_TO_BONEMEAL, APPLYING_BONEMEAL -> continueBoneMeal(golem, copper);
            case RESTING -> continueRest(golem);
            default -> golem.farmerState(FarmerState.WAITING_SEEDS);
        }
        GolemDisplay.refresh(golem, copper, messages(), this.keys, style);
    }

    private void beginCycle(ActiveGolem golem, CopperGolem copper) {
        if (!stationsOk(golem)) {
            return;
        }
        if (!golem.carried().isEmpty()) {
            golem.fetchingSeed(false);
            golem.farmerState(FarmerState.MOVING_TO_CHEST);
            return;
        }
        int radius = this.chestService.effectiveRadius(golem.data());
        List<Block> mature = this.farmAreaService.matureWheat(golem.data(), radius);
        if (!mature.isEmpty()) {
            golem.fetchingSeed(false);
            golem.targetCrop(farthestPlot(golem.data(), mature));
            golem.farmerState(FarmerState.MOVING_TO_HARVEST);
            return;
        }
        if (!golem.fieldReady() || !this.farmAreaService.tillableSoil(golem.data(), radius).isEmpty()) {
            List<Block> weeds = this.farmAreaService.fieldWeeds(golem.data(), radius);
            if (!weeds.isEmpty()) {
                golem.clearFetchFlags();
                golem.targetCrop(weeds.get(0).getLocation());
                golem.farmerState(FarmerState.MOVING_TO_CLEAR);
                return;
            }
            golem.fieldReady(false);
            golem.farmerState(FarmerState.PREPARE_FIELD);
            return;
        }
        List<Block> empty = this.farmAreaService.emptyFarmland(golem.data(), radius);
        if (!empty.isEmpty() && this.chestService.countItem(golem.data(), Material.WHEAT_SEEDS) > 0) {
            golem.fetchingSeed(true);
            golem.targetCrop(farthestPlot(golem.data(), empty));
            golem.farmerState(FarmerState.MOVING_TO_CHEST);
            return;
        }
        if (tryStartBread(golem)) {
            return;
        }
        if (this.farmAreaService.hasAnyWheat(golem.data(), radius)) {
            maybeStartSupportJobs(golem);
            if (golem.farmerState() == FarmerState.WAITING_SEEDS
                    || golem.farmerState() == FarmerState.WAIT_GROWTH) {
                golem.farmerState(FarmerState.WAIT_GROWTH);
                golem.data().lastActionAt(System.currentTimeMillis());
            }
            return;
        }
        maybeStartSupportJobs(golem);
        if (golem.farmerState() != FarmerState.MOVING_TO_CHEST
                && golem.farmerState() != FarmerState.MOVING_TO_TORCH
                && golem.farmerState() != FarmerState.MOVING_TO_SEAT
                && golem.farmerState() != FarmerState.MOVING_TO_CRAFT
                && golem.farmerState() != FarmerState.MOVING_TO_BONEMEAL
                && golem.farmerState() != FarmerState.MOVING_TO_CLEAR) {
            golem.farmerState(FarmerState.WAITING_SEEDS);
            golem.data().lastActionAt(System.currentTimeMillis());
            Location stand = this.chestService.chestStandLocation(golem.data());
            if (stand != null) {
                movement().walkTowards(copper, stand, golem);
            }
        }
    }

    private boolean tryStartBread(ActiveGolem golem) {
        if (!settings().farmer.craftBread
                || !golem.data().hasCraftStation()
                || this.chestService.countItem(golem.data(), Material.WHEAT) < 3
                || !this.chestService.hasSpace(golem.data())) {
            return false;
        }
        golem.clearFetchFlags();
        golem.wanderTarget(null);
        golem.farmerState(FarmerState.MOVING_TO_CRAFT);
        return true;
    }

    private Location farthestPlot(SoulGolemData data, List<Block> blocks) {
        Block block = this.farmAreaService.pickFarthestFromChest(blocks, data);
        return block == null ? null : block.getLocation();
    }

    private Location boneMealPlot(SoulGolemData data, List<Block> immature) {
        Block block = this.farmAreaService.pickImmatureForBoneMeal(immature, data);
        return block == null ? null : block.getLocation();
    }

    private void maybeStartSupportJobs(ActiveGolem golem) {
        int radius = this.chestService.effectiveRadius(golem.data());
        Settings.Farmer farmer = settings().farmer;
        if (farmer.clearBorder && !this.farmAreaService.weedsToClear(golem.data(), radius).isEmpty()) {
            golem.clearFetchFlags();
            List<Block> junk = this.farmAreaService.weedsToClear(golem.data(), radius);
            golem.targetCrop(junk.get(0).getLocation());
            golem.farmerState(FarmerState.MOVING_TO_CLEAR);
            return;
        }
        if (farmer.placeSeat
                && !this.farmAreaService.hasValidSeat(golem.data())
                && this.farmAreaService.findSeatSpot(golem.data(), radius) != null) {
            Material carried = carriedStairs(golem);
            if (carried != null) {
                golem.clearFetchFlags();
                Block spot = this.farmAreaService.findSeatSpot(golem.data(), radius);
                golem.targetCrop(spot.getLocation());
                golem.farmerState(FarmerState.MOVING_TO_SEAT);
                return;
            }
            if (this.chestService.findStairsInChest(golem.data()) != null) {
                golem.clearFetchFlags();
                golem.fetchingSeat(true);
                golem.farmerState(FarmerState.MOVING_TO_CHEST);
                return;
            }
        }
        if (farmer.useBoneMeal
                && this.chestService.countItem(golem.data(), Material.BONE_MEAL) > 0
                && !this.farmAreaService.immatureWheat(golem.data(), radius).isEmpty()) {
            golem.clearFetchFlags();
            golem.fetchingBoneMeal(true);
            List<Block> immature = this.farmAreaService.immatureWheat(golem.data(), radius);
            golem.targetCrop(boneMealPlot(golem.data(), immature));
            golem.farmerState(FarmerState.MOVING_TO_CHEST);
            return;
        }
        Material torch = resolveTorch();
        if (farmer.placeTorches
                && this.chestService.countItem(golem.data(), torch) > 0
                && !this.farmAreaService.perimeterTorchSpots(golem.data(), radius).isEmpty()) {
            golem.clearFetchFlags();
            golem.fetchingTorch(true);
            golem.farmerState(FarmerState.MOVING_TO_CHEST);
        }
    }

    private Material resolveShovel() {
        Material shovel = Material.matchMaterial(settings().shovelMaterial);
        return shovel != null ? shovel : Material.IRON_SHOVEL;
    }

    private void equipShovel(CopperGolem copper) {
        org.bukkit.inventory.EntityEquipment equipment = copper.getEquipment();
        if (equipment == null) {
            return;
        }
        Material shovel = resolveShovel();
        ItemStack current = equipment.getItemInMainHand();
        if (current != null && !current.isEmpty() && current.getType() == shovel) {
            equipment.setItemInMainHandDropChance(0.0F);
            return;
        }
        equipment.setItemInMainHand(new ItemStack(shovel), true);
        equipment.setItemInMainHandDropChance(0.0F);
    }

    private Material resolveTorch() {
        Material torch = Material.matchMaterial(settings().farmer.torchMaterial);
        return torch != null ? torch : Material.TORCH;
    }

    private void continueTill(ActiveGolem golem, CopperGolem copper) {
        if (!stationsOk(golem)) {
            return;
        }
        this.farmAreaService.ensureWater(golem.data());
        int radius = this.chestService.effectiveRadius(golem.data());
        Location target = golem.targetCrop();
        Block soil = target == null ? null : target.getBlock();
        if (soil == null || !canTillNow(soil)) {
            List<Block> tillable = this.farmAreaService.tillableSoil(golem.data(), radius);
            if (tillable.isEmpty()) {
                golem.targetCrop(null);
                golem.fieldReady(true);
                golem.farmerState(FarmerState.WAITING_SEEDS);
                golem.markDirty();
                return;
            }
            soil = this.farmAreaService.pickFarthestFromChest(tillable, golem.data());
            if (soil == null) {
                soil = tillable.get(0);
            }
            golem.targetCrop(soil.getLocation());
        }
        Location stand = this.farmAreaService.standOn(soil);
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 1.2D
                || Math.abs(copper.getLocation().getY() - stand.getY()) > 1.0D) {
            golem.farmerState(FarmerState.MOVING_TO_TILL);
            movement().walkTowards(copper, stand, golem);
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.farmerState(FarmerState.TILLING);
        this.farmAreaService.tillSoil(soil, golem.data().id());
        golem.targetCrop(null);
        golem.markDirty();
        List<Block> left = this.farmAreaService.tillableSoil(golem.data(), radius);
        if (left.isEmpty()) {
            golem.fieldReady(true);
            golem.farmerState(FarmerState.WAITING_SEEDS);
            return;
        }
        Block next = this.farmAreaService.pickFarthestFromChest(left, golem.data());
        golem.targetCrop(next != null ? next.getLocation() : left.get(0).getLocation());
        golem.farmerState(FarmerState.MOVING_TO_TILL);
    }

    private static boolean canTillNow(Block soil) {
        Material type = soil.getType();
        return type == Material.GRASS_BLOCK
                || type == Material.DIRT
                || type == Material.COARSE_DIRT
                || type == Material.ROOTED_DIRT
                || type == Material.DIRT_PATH;
    }

    private boolean stationsOk(ActiveGolem golem) {
        SoulGolemData data = golem.data();
        if (this.chestService.isChestPresent(data) && this.chestService.isCraftPresent(data)) {
            return true;
        }
        this.spawnService.removeGolem(data.id(), null);
        return false;
    }

    private void continuePlant(ActiveGolem golem, CopperGolem copper) {
        if (!hasCarriedSeed(golem)) {
            golem.fetchingSeed(true);
            golem.farmerState(FarmerState.MOVING_TO_CHEST);
            return;
        }
        Location target = golem.targetCrop();
        if (target == null) {
            int radius = this.chestService.effectiveRadius(golem.data());
            List<Block> empty = this.farmAreaService.emptyFarmland(golem.data(), radius);
            if (empty.isEmpty()) {
                returnSeedToChest(golem);
                golem.farmerState(FarmerState.WAIT_GROWTH);
                return;
            }
            target = farthestPlot(golem.data(), empty);
            golem.targetCrop(target);
        }
        if (target == null) {
            returnSeedToChest(golem);
            golem.farmerState(FarmerState.WAITING_SEEDS);
            return;
        }
        Block soil = target.getBlock();
        if (soil.getType() != Material.FARMLAND) {
            golem.targetCrop(null);
            returnSeedToChest(golem);
            golem.farmerState(FarmerState.WAITING_SEEDS);
            return;
        }
        Location stand = this.farmAreaService.standOn(soil);
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 1.2D) {
            golem.farmerState(FarmerState.MOVING_TO_PLANT);
            movement().walkTowards(copper, stand, golem);
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.farmerState(FarmerState.PLANTING);
        consumeCarriedSeed(golem);
        this.farmAreaService.plantWheat(soil, golem.data().id());
        golem.targetCrop(null);
        golem.fetchingSeed(false);
        golem.data().lastActionAt(System.currentTimeMillis());
        golem.markDirty();
        golem.farmerState(FarmerState.WAITING_SEEDS);
    }

    private void waitGrowth(ActiveGolem golem, CopperGolem copper) {
        long now = System.currentTimeMillis();
        long intervalMs = Math.max(1L, settings().workIntervalTicks) * 50L;
        boolean decide = golem.farmerState() != FarmerState.WANDERING
                || now - golem.data().lastActionAt() >= intervalMs;
        if (decide) {
            if (!stationsOk(golem)) {
                return;
            }
            int radius = this.chestService.effectiveRadius(golem.data());
            List<Block> mature = this.farmAreaService.matureWheat(golem.data(), radius);
            if (!mature.isEmpty()) {
                golem.wanderTarget(null);
                golem.clearFetchFlags();
                golem.targetCrop(farthestPlot(golem.data(), mature));
                golem.farmerState(FarmerState.MOVING_TO_HARVEST);
                return;
            }
            List<Block> empty = this.farmAreaService.emptyFarmland(golem.data(), radius);
            if (!empty.isEmpty() && this.chestService.countItem(golem.data(), Material.WHEAT_SEEDS) > 0) {
                golem.wanderTarget(null);
                golem.clearFetchFlags();
                golem.fetchingSeed(true);
                golem.targetCrop(farthestPlot(golem.data(), empty));
                golem.farmerState(FarmerState.MOVING_TO_CHEST);
                return;
            }
            if (tryStartBread(golem)) {
                return;
            }
            if (carriedStairs(golem) != null && !this.farmAreaService.hasValidSeat(golem.data())) {
                golem.wanderTarget(null);
                golem.clearFetchFlags();
                golem.farmerState(FarmerState.MOVING_TO_SEAT);
                return;
            }
            maybeStartSupportJobs(golem);
            if (golem.fetchingBoneMeal() || golem.fetchingTorch() || golem.fetchingSeat()
                    || golem.farmerState() == FarmerState.MOVING_TO_SEAT) {
                golem.wanderTarget(null);
                return;
            }
            golem.data().lastActionAt(now);
        }
        if (golem.fetchingBoneMeal() || golem.fetchingTorch() || golem.fetchingSeat()) {
            return;
        }
        if (golem.farmerState() == FarmerState.MOVING_TO_CRAFT) {
            return;
        }
        if (this.farmAreaService.hasValidSeat(golem.data())) {
            golem.wanderTarget(null);
            golem.farmerState(FarmerState.MOVING_TO_SEAT);
            continueSeat(golem, copper);
            return;
        }
        if (settings().farmer.wanderWhileWaiting) {
            wander(golem, copper, this.chestService.effectiveRadius(golem.data()));
            return;
        }
        golem.farmerState(FarmerState.WAIT_GROWTH);
        golem.data().lastActionAt(now);
    }

    private void wander(ActiveGolem golem, CopperGolem copper, int radius) {
        golem.farmerState(FarmerState.WANDERING);
        Location target = golem.wanderTarget();
        if (target == null || GolemMovement.horizontalDistanceSquared(copper.getLocation(), target) < 1.0D) {
            target = this.farmAreaService.randomWanderPoint(golem.data(), radius);
            golem.wanderTarget(target);
            if (target == null) {
                return;
            }
        }
        movement().walkTowards(copper, target, golem);
    }

    private void continueHarvest(ActiveGolem golem, CopperGolem copper) {
        Location target = golem.targetCrop();
        if (target == null) {
            golem.farmerState(FarmerState.WAITING_SEEDS);
            return;
        }
        Block crop = target.getBlock();
        if (crop.getType() != Material.WHEAT) {
            golem.targetCrop(null);
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        Location stand = crop.getLocation().add(0.5D, 0.0D, 0.5D);
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 1.2D) {
            golem.farmerState(FarmerState.MOVING_TO_HARVEST);
            movement().walkTowards(copper, stand, golem);
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.farmerState(FarmerState.HARVESTING);
        Collection<ItemStack> drops = wheatHarvestDrops(crop);
        crop.setType(Material.AIR, false);
        for (ItemStack drop : drops) {
            golem.carry(drop);
        }
        golem.data().incrementBlocksMined();
        golem.targetCrop(null);
        golem.fetchingSeed(false);
        golem.markDirty();
        golem.farmerState(FarmerState.MOVING_TO_CHEST);
    }

    private static Collection<ItemStack> wheatHarvestDrops(Block crop) {
        List<ItemStack> drops = new ArrayList<>(2);
        if (!(crop.getBlockData() instanceof Ageable ageable)
                || ageable.getAge() < ageable.getMaximumAge()) {
            drops.add(new ItemStack(Material.WHEAT_SEEDS, 1));
            return drops;
        }
        drops.add(new ItemStack(Material.WHEAT, 1));
        int seeds = 1;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 3; i++) {
            if (random.nextDouble() < 0.5714286D) {
                seeds++;
            }
        }
        drops.add(new ItemStack(Material.WHEAT_SEEDS, seeds));
        return drops;
    }

    private void continueDeposit(ActiveGolem golem, CopperGolem copper) {
        Location chestStand = this.chestService.chestStandLocation(golem.data());
        if (chestStand == null) {
            golem.farmerState(FarmerState.WAITING_SEEDS);
            return;
        }
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), chestStand) > 1.69D) {
            movement().walkTowards(copper, chestStand, golem);
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));

        if (golem.fetchingSeed()) {
            takeSeedFromChest(golem);
            return;
        }
        if (golem.fetchingSeat()) {
            takeSeatFromChest(golem);
            return;
        }
        if (golem.fetchingTorch()) {
            takeTorchesFromChest(golem);
            return;
        }
        if (golem.fetchingBoneMeal()) {
            takeBoneMealFromChest(golem);
            return;
        }

        if (golem.carried().isEmpty()) {
            afterDeposit(golem);
            return;
        }
        if (!this.chestService.hasSpace(golem.data())) {
            golem.farmerState(FarmerState.WAITING_CHEST);
            notifyChestFull(golem);
            return;
        }
        boolean depositedAll = true;
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack stack : golem.carried()) {
            if (!this.chestService.deposit(golem.data(), stack.clone())) {
                depositedAll = false;
                leftover.add(stack.clone());
            }
        }
        golem.clearCarried();
        for (ItemStack stack : leftover) {
            golem.carry(stack);
        }
        if (!depositedAll) {
            golem.farmerState(FarmerState.WAITING_CHEST);
            notifyChestFull(golem);
            return;
        }
        afterDeposit(golem);
    }

    private void takeSeedFromChest(ActiveGolem golem) {
        int radius = this.chestService.effectiveRadius(golem.data());
        List<Block> empty = this.farmAreaService.emptyFarmland(golem.data(), radius);
        if (empty.isEmpty()) {
            golem.fetchingSeed(false);
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        if (this.chestService.countItem(golem.data(), Material.WHEAT_SEEDS) <= 0) {
            golem.fetchingSeed(false);
            golem.farmerState(FarmerState.WAITING_SEEDS);
            return;
        }
        if (!this.chestService.takeItem(golem.data(), Material.WHEAT_SEEDS, 1)) {
            golem.fetchingSeed(false);
            golem.farmerState(FarmerState.WAITING_SEEDS);
            return;
        }
        golem.clearCarried();
        golem.carry(new ItemStack(Material.WHEAT_SEEDS, 1));
        golem.fetchingSeed(false);
        if (golem.targetCrop() == null) {
            golem.targetCrop(farthestPlot(golem.data(), empty));
        }
        golem.farmerState(FarmerState.MOVING_TO_PLANT);
        golem.markDirty();
    }

    private void takeSeatFromChest(ActiveGolem golem) {
        int radius = this.chestService.effectiveRadius(golem.data());
        if (this.farmAreaService.hasValidSeat(golem.data())) {
            golem.clearFetchFlags();
            golem.farmerState(FarmerState.MOVING_TO_SEAT);
            return;
        }
        Material already = carriedStairs(golem);
        if (already != null) {
            Block spot = this.farmAreaService.findSeatSpot(golem.data(), radius);
            if (spot == null) {
                returnCarriedToChest(golem, already);
                golem.clearFetchFlags();
                golem.farmerState(FarmerState.WAIT_GROWTH);
                return;
            }
            golem.clearFetchFlags();
            golem.targetCrop(spot.getLocation());
            golem.farmerState(FarmerState.MOVING_TO_SEAT);
            return;
        }
        Material stairs = this.chestService.findStairsInChest(golem.data());
        Block spot = this.farmAreaService.findSeatSpot(golem.data(), radius);
        if (stairs == null || spot == null) {
            golem.clearFetchFlags();
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        if (!this.chestService.takeItem(golem.data(), stairs, 1)) {
            golem.clearFetchFlags();
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        golem.clearCarried();
        golem.carry(new ItemStack(stairs, 1));
        golem.clearFetchFlags();
        golem.targetCrop(spot.getLocation());
        golem.farmerState(FarmerState.MOVING_TO_SEAT);
        golem.markDirty();
    }

    private void continueSeat(ActiveGolem golem, CopperGolem copper) {
        if (!stationsOk(golem)) {
            return;
        }
        int radius = this.chestService.effectiveRadius(golem.data());

        Material carriedStairs = carriedStairs(golem);
        if (carriedStairs != null && !this.farmAreaService.hasValidSeat(golem.data())) {
            placeCarriedSeat(golem, copper, carriedStairs, radius);
            return;
        }

        List<Block> mature = this.farmAreaService.matureWheat(golem.data(), radius);
        if (!mature.isEmpty()) {
            golem.clearFetchFlags();
            golem.targetCrop(farthestPlot(golem.data(), mature));
            golem.farmerState(FarmerState.MOVING_TO_HARVEST);
            return;
        }
        if (!golem.fieldReady() || !this.farmAreaService.tillableSoil(golem.data(), radius).isEmpty()) {
            List<Block> weeds = this.farmAreaService.fieldWeeds(golem.data(), radius);
            if (!weeds.isEmpty()) {
                golem.clearFetchFlags();
                golem.targetCrop(weeds.get(0).getLocation());
                golem.farmerState(FarmerState.MOVING_TO_CLEAR);
                return;
            }
            golem.fieldReady(false);
            golem.clearFetchFlags();
            golem.farmerState(FarmerState.PREPARE_FIELD);
            return;
        }
        List<Block> empty = this.farmAreaService.emptyFarmland(golem.data(), radius);
        if (!empty.isEmpty() && this.chestService.countItem(golem.data(), Material.WHEAT_SEEDS) > 0) {
            golem.clearFetchFlags();
            golem.fetchingSeed(true);
            golem.targetCrop(farthestPlot(golem.data(), empty));
            golem.farmerState(FarmerState.MOVING_TO_CHEST);
            return;
        }
        if (tryStartBread(golem)) {
            return;
        }

        maybeStartSupportJobs(golem);
        if (golem.fetchingBoneMeal() || golem.fetchingTorch() || golem.fetchingSeat()) {
            return;
        }
        if (golem.farmerState() == FarmerState.MOVING_TO_SEAT && !this.farmAreaService.hasValidSeat(golem.data())) {
            return;
        }

        if (!this.farmAreaService.hasValidSeat(golem.data())) {
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }

        Location seatStand = this.farmAreaService.seatStandLocation(golem.data());
        if (seatStand == null) {
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        if (!this.farmAreaService.isSeatedOnOwnBench(copper.getLocation(), golem.data())
                || GolemMovement.horizontalDistanceSquared(copper.getLocation(), seatStand) > 0.35D) {
            if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), seatStand) > 1.2D) {
                golem.farmerState(FarmerState.MOVING_TO_SEAT);
                movement().walkTowards(copper, seatStand, golem);
                return;
            }
            copper.setVelocity(new Vector(0, 0, 0));
            Location sit = seatStand.clone();
            Location look = this.workAreaService.homeLocation(golem.data());
            if (look != null) {
                sit.setYaw(GolemMovement.yawTo(sit, look));
            }
            sit.setPitch(0.0F);
            copper.teleport(sit);
            if (look != null) {
                copper.lookAt(look.clone().add(0.0D, 1.0D, 0.0D));
            }
        }
        golem.farmerState(FarmerState.SITTING);
        golem.data().lastActionAt(System.currentTimeMillis());
    }

    private void placeCarriedSeat(ActiveGolem golem, CopperGolem copper, Material carriedStairs, int radius) {
        Location target = golem.targetCrop();
        if (target == null || !canPlaceSeatAt(target.getBlock())) {
            Block spot = this.farmAreaService.findSeatSpot(golem.data(), radius);
            if (spot == null) {
                returnCarriedToChest(golem, carriedStairs);
                golem.targetCrop(null);
                golem.farmerState(FarmerState.WAIT_GROWTH);
                return;
            }
            target = spot.getLocation();
            golem.targetCrop(target);
        }
        Block seatSpot = target.getBlock();
        Block blockingTorch = this.farmAreaService.findTorchBlockingSeat(seatSpot);
        Location stand = blockingTorch != null
                ? blockingTorch.getLocation().add(0.5D, 0.0D, 0.5D)
                : this.farmAreaService.standBeside(seatSpot);
        if (stand == null) {
            stand = seatSpot.getLocation().add(0.5D, 0.0D, 0.5D);
        }
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 2.25D) {
            golem.farmerState(FarmerState.MOVING_TO_SEAT);
            movement().walkTowards(copper, stand, golem);
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.farmerState(FarmerState.PLACING_SEAT);
        this.farmAreaService.relocateTorchForSeat(golem.data(), seatSpot, radius, golem.data().id());
        if (!this.farmAreaService.placeSeat(golem.data(), seatSpot, carriedStairs, golem.data().id())) {
            returnCarriedToChest(golem, carriedStairs);
            golem.targetCrop(null);
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        consumeCarried(golem, carriedStairs, 1);
        golem.targetCrop(null);
        golem.markDirty();
        golem.farmerState(FarmerState.MOVING_TO_SEAT);
    }

    private static boolean canPlaceSeatAt(Block spot) {
        if (spot == null) {
            return false;
        }
        Material type = spot.getType();
        return type.isAir()
                || FarmAreaService.isVegetation(type)
                || org.bukkit.Tag.STAIRS.isTagged(type)
                || type == Material.TORCH
                || type == Material.WALL_TORCH
                || type == Material.SOUL_TORCH
                || type == Material.SOUL_WALL_TORCH;
    }

    private static Material carriedStairs(ActiveGolem golem) {
        for (ItemStack stack : golem.carried()) {
            if (stack != null && org.bukkit.Tag.STAIRS.isTagged(stack.getType())) {
                return stack.getType();
            }
        }
        return null;
    }

    private void continueClear(ActiveGolem golem, CopperGolem copper) {
        if (!stationsOk(golem)) {
            return;
        }
        this.farmAreaService.ensureWater(golem.data());
        if (!golem.carried().isEmpty() && !this.chestService.hasSpace(golem.data())) {
            golem.farmerState(FarmerState.MOVING_TO_CHEST);
            return;
        }
        int radius = this.chestService.effectiveRadius(golem.data());
        Location target = golem.targetCrop();
        Block junk = target == null ? null : target.getBlock();
        if (junk == null || junk.getType().isAir() || !isClearableJunk(junk) || isBorderBlock(junk)) {
            List<Block> list = this.farmAreaService.weedsToClear(golem.data(), radius);
            list.removeIf(this::isBorderBlock);
            if (list.isEmpty()) {
                resumeAfterClear(golem);
                return;
            }
            junk = pickNearestJunk(copper.getLocation(), list);
            golem.targetCrop(junk.getLocation());
        }
        Location stand = this.farmAreaService.standForClear(junk);
        if (stand == null) {
            golem.targetCrop(null);
            return;
        }
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 2.25D) {
            golem.farmerState(FarmerState.MOVING_TO_CLEAR);
            movement().walkTowards(copper, stand, golem);
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.farmerState(FarmerState.CLEARING);
        if (FarmAreaService.isVegetation(junk.getType())) {
            FarmAreaService.clearVegetation(junk);
        } else {
            Material shovel = resolveShovel();
            Collection<ItemStack> drops = junk.getDrops(new ItemStack(shovel));
            junk.setType(Material.AIR, false);
            for (ItemStack drop : drops) {
                golem.carry(drop);
            }
        }
        golem.targetCrop(null);
        golem.markDirty();
        List<Block> left = this.farmAreaService.weedsToClear(golem.data(), radius);
        left.removeIf(this::isBorderBlock);
        if (!left.isEmpty() && (golem.carried().isEmpty() || this.chestService.hasSpace(golem.data()))) {
            Block next = pickNearestJunk(copper.getLocation(), left);
            golem.targetCrop(next.getLocation());
            golem.farmerState(FarmerState.MOVING_TO_CLEAR);
            return;
        }
        resumeAfterClear(golem);
    }

    private void resumeAfterClear(ActiveGolem golem) {
        golem.targetCrop(null);
        if (!golem.carried().isEmpty()) {
            golem.farmerState(FarmerState.MOVING_TO_CHEST);
            return;
        }
        int radius = this.chestService.effectiveRadius(golem.data());
        List<Block> weeds = this.farmAreaService.weedsToClear(golem.data(), radius);
        weeds.removeIf(this::isBorderBlock);
        if (!weeds.isEmpty()) {
            golem.targetCrop(weeds.get(0).getLocation());
            golem.farmerState(FarmerState.MOVING_TO_CLEAR);
            return;
        }
        if (!golem.fieldReady() || !this.farmAreaService.tillableSoil(golem.data(), radius).isEmpty()) {
            golem.fieldReady(false);
            golem.farmerState(FarmerState.PREPARE_FIELD);
            return;
        }
        List<Block> empty = this.farmAreaService.emptyFarmland(golem.data(), radius);
        if (!empty.isEmpty() && this.chestService.countItem(golem.data(), Material.WHEAT_SEEDS) > 0) {
            golem.fetchingSeed(true);
            golem.targetCrop(farthestPlot(golem.data(), empty));
            golem.farmerState(FarmerState.MOVING_TO_CHEST);
            return;
        }
        golem.farmerState(FarmerState.WAITING_SEEDS);
    }

    private static Block pickNearestJunk(Location from, List<Block> list) {
        Block best = list.get(0);
        double bestDist = Double.MAX_VALUE;
        for (Block block : list) {
            double dx = block.getX() + 0.5D - from.getX();
            double dz = block.getZ() + 0.5D - from.getZ();
            double dist = dx * dx + dz * dz;
            if (dist < bestDist) {
                bestDist = dist;
                best = block;
            }
        }
        return best;
    }

    private boolean isBorderBlock(Block block) {
        return this.farmAreaService.isBorderMaterial(block.getType());
    }

    private static boolean isClearableJunk(Block block) {
        Material type = block.getType();
        if (type.isAir() || SoulChestService.isChestLike(type) || type == Material.CRAFTING_TABLE) {
            return false;
        }
        if (org.bukkit.Tag.STAIRS.isTagged(type) || type == Material.WHEAT) {
            return false;
        }
        if (type == Material.TORCH || type == Material.WALL_TORCH
                || type == Material.SOUL_TORCH || type == Material.SOUL_WALL_TORCH) {
            return false;
        }
        return type.isSolid() || type == Material.SNOW || FarmAreaService.isVegetation(type);
    }

    private void takeTorchesFromChest(ActiveGolem golem) {
        Material torch = resolveTorch();
        int radius = this.chestService.effectiveRadius(golem.data());
        List<Block> spots = this.farmAreaService.perimeterTorchSpots(golem.data(), radius);
        if (spots.isEmpty() || this.chestService.countItem(golem.data(), torch) <= 0) {
            golem.clearFetchFlags();
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        int want = Math.min(Math.max(1, settings().farmer.torchesPerTrip), spots.size());
        want = Math.min(want, this.chestService.countItem(golem.data(), torch));
        if (want <= 0 || !this.chestService.takeItem(golem.data(), torch, want)) {
            golem.clearFetchFlags();
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        golem.clearCarried();
        golem.carry(new ItemStack(torch, want));
        golem.clearFetchFlags();
        golem.targetCrop(spots.get(0).getLocation());
        golem.farmerState(FarmerState.MOVING_TO_TORCH);
        golem.markDirty();
    }

    private void takeBoneMealFromChest(ActiveGolem golem) {
        int radius = this.chestService.effectiveRadius(golem.data());
        List<Block> immature = this.farmAreaService.immatureWheat(golem.data(), radius);
        int available = this.chestService.countItem(golem.data(), Material.BONE_MEAL);
        if (immature.isEmpty() || available <= 0) {
            golem.clearFetchFlags();
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        int want = Math.min(Math.max(1, settings().farmer.boneMealPerTrip), available);
        if (!this.chestService.takeItem(golem.data(), Material.BONE_MEAL, want)) {
            golem.clearFetchFlags();
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        golem.clearCarried();
        golem.carry(new ItemStack(Material.BONE_MEAL, want));
        golem.clearFetchFlags();
        golem.targetCrop(boneMealPlot(golem.data(), immature));
        golem.farmerState(FarmerState.MOVING_TO_BONEMEAL);
        golem.markDirty();
    }

    private void continueTorch(ActiveGolem golem, CopperGolem copper) {
        Material torch = resolveTorch();
        if (countCarried(golem, torch) <= 0) {
            golem.targetCrop(null);
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        Location target = golem.targetCrop();
        if (target == null) {
            int radius = this.chestService.effectiveRadius(golem.data());
            List<Block> spots = this.farmAreaService.perimeterTorchSpots(golem.data(), radius);
            if (spots.isEmpty()) {
                returnCarriedToChest(golem, torch);
                golem.farmerState(FarmerState.WAIT_GROWTH);
                return;
            }
            target = spots.get(0).getLocation();
            golem.targetCrop(target);
        }
        Location stand = target.clone().add(0.5D, 0.0D, 0.5D);
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 1.5D) {
            golem.farmerState(FarmerState.MOVING_TO_TORCH);
            movement().walkTowards(copper, stand, golem);
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.farmerState(FarmerState.PLACING_TORCH);
        Block spot = target.getBlock();
        if (spot.getType().isAir()) {
            this.farmAreaService.placeTorch(spot, torch, golem.data().id());
            consumeCarried(golem, torch, 1);
        }
        golem.targetCrop(null);
        golem.markDirty();
        if (countCarried(golem, torch) > 0) {
            int radius = this.chestService.effectiveRadius(golem.data());
            List<Block> spots = this.farmAreaService.perimeterTorchSpots(golem.data(), radius);
            if (!spots.isEmpty()) {
                golem.targetCrop(spots.get(0).getLocation());
                golem.farmerState(FarmerState.MOVING_TO_TORCH);
                return;
            }
            returnCarriedToChest(golem, torch);
        }
        golem.farmerState(FarmerState.WAIT_GROWTH);
        golem.data().lastActionAt(System.currentTimeMillis());
    }

    private void continueBoneMeal(ActiveGolem golem, CopperGolem copper) {
        if (countCarried(golem, Material.BONE_MEAL) <= 0) {
            int radius = this.chestService.effectiveRadius(golem.data());
            if (settings().farmer.useBoneMeal
                    && this.chestService.countItem(golem.data(), Material.BONE_MEAL) > 0
                    && !this.farmAreaService.immatureWheat(golem.data(), radius).isEmpty()) {
                golem.fetchingBoneMeal(true);
                golem.farmerState(FarmerState.MOVING_TO_CHEST);
                return;
            }
            golem.targetCrop(null);
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        Location target = golem.targetCrop();
        if (target == null) {
            int radius = this.chestService.effectiveRadius(golem.data());
            List<Block> immature = this.farmAreaService.immatureWheat(golem.data(), radius);
            if (immature.isEmpty()) {
                returnCarriedToChest(golem, Material.BONE_MEAL);
                golem.farmerState(FarmerState.WAIT_GROWTH);
                return;
            }
            target = boneMealPlot(golem.data(), immature);
            golem.targetCrop(target);
        }
        Block crop = target.getBlock();
        if (crop.getType() != Material.WHEAT) {
            golem.targetCrop(null);
            if (countCarried(golem, Material.BONE_MEAL) > 0) {
                golem.farmerState(FarmerState.MOVING_TO_BONEMEAL);
                return;
            }
            returnCarriedToChest(golem, Material.BONE_MEAL);
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        if (crop.getBlockData() instanceof org.bukkit.block.data.Ageable ripe
                && ripe.getAge() >= ripe.getMaximumAge()) {
            golem.targetCrop(crop.getLocation());
            golem.farmerState(FarmerState.MOVING_TO_HARVEST);
            return;
        }
        Location stand = crop.getLocation().add(0.5D, 0.0D, 0.5D);
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 1.2D) {
            golem.farmerState(FarmerState.MOVING_TO_BONEMEAL);
            movement().walkTowards(copper, stand, golem);
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.farmerState(FarmerState.APPLYING_BONEMEAL);

        int ageBefore = 0;
        if (crop.getBlockData() instanceof org.bukkit.block.data.Ageable ageable) {
            ageBefore = ageable.getAge();
        }
        crop.applyBoneMeal(org.bukkit.block.BlockFace.UP);
        boolean grew = false;
        if (crop.getBlockData() instanceof org.bukkit.block.data.Ageable after) {
            grew = after.getAge() > ageBefore;
        }
        if (grew) {
            consumeCarried(golem, Material.BONE_MEAL, 1);
        } else {
            returnCarriedToChest(golem, Material.BONE_MEAL);
            golem.targetCrop(null);
            golem.farmerState(FarmerState.WAIT_GROWTH);
            golem.data().lastActionAt(System.currentTimeMillis());
            return;
        }
        golem.markDirty();

        if (crop.getBlockData() instanceof org.bukkit.block.data.Ageable done && done.getAge() >= done.getMaximumAge()) {
            golem.targetCrop(crop.getLocation());
            golem.farmerState(FarmerState.MOVING_TO_HARVEST);
            return;
        }
        if (countCarried(golem, Material.BONE_MEAL) > 0) {
            int radiusLeft = this.chestService.effectiveRadius(golem.data());
            List<Block> stillGrowing = this.farmAreaService.immatureWheat(golem.data(), radiusLeft);
            Location next = boneMealPlot(golem.data(), stillGrowing);
            golem.targetCrop(next != null ? next : crop.getLocation());
            golem.farmerState(FarmerState.MOVING_TO_BONEMEAL);
            return;
        }
        int radius = this.chestService.effectiveRadius(golem.data());
        if (settings().farmer.useBoneMeal
                && this.chestService.countItem(golem.data(), Material.BONE_MEAL) > 0
                && !this.farmAreaService.immatureWheat(golem.data(), radius).isEmpty()) {
            golem.fetchingBoneMeal(true);
            golem.targetCrop(null);
            golem.farmerState(FarmerState.MOVING_TO_CHEST);
            return;
        }
        golem.targetCrop(null);
        golem.farmerState(FarmerState.WAIT_GROWTH);
        golem.data().lastActionAt(System.currentTimeMillis());
    }

    private static int countCarried(ActiveGolem golem, Material material) {
        int total = 0;
        for (ItemStack stack : golem.carried()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private static void consumeCarried(ActiveGolem golem, Material material, int amount) {
        int left = amount;
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack stack : golem.carried()) {
            if (stack == null) {
                continue;
            }
            if (left > 0 && stack.getType() == material) {
                int take = Math.min(left, stack.getAmount());
                left -= take;
                int remain = stack.getAmount() - take;
                if (remain > 0) {
                    ItemStack copy = stack.clone();
                    copy.setAmount(remain);
                    leftover.add(copy);
                }
                continue;
            }
            leftover.add(stack.clone());
        }
        golem.clearCarried();
        for (ItemStack stack : leftover) {
            golem.carry(stack);
        }
    }

    private void returnCarriedToChest(ActiveGolem golem, Material material) {
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack stack : golem.carried()) {
            if (stack != null && stack.getType() == material) {
                this.chestService.deposit(golem.data(), stack.clone());
            } else if (stack != null) {
                leftover.add(stack.clone());
            }
        }
        golem.clearCarried();
        for (ItemStack stack : leftover) {
            golem.carry(stack);
        }
    }

    private void afterDeposit(ActiveGolem golem) {
        golem.chestFullNotified(false);
        int radius = this.chestService.effectiveRadius(golem.data());
        List<Block> mature = this.farmAreaService.matureWheat(golem.data(), radius);
        if (!mature.isEmpty()) {
            golem.targetCrop(farthestPlot(golem.data(), mature));
            golem.farmerState(FarmerState.MOVING_TO_HARVEST);
            return;
        }
        List<Block> empty = this.farmAreaService.emptyFarmland(golem.data(), radius);
        if (!empty.isEmpty() && this.chestService.countItem(golem.data(), Material.WHEAT_SEEDS) > 0) {
            golem.fetchingSeed(true);
            golem.targetCrop(farthestPlot(golem.data(), empty));
            golem.farmerState(FarmerState.MOVING_TO_CHEST);
            return;
        }
        if (tryStartBread(golem)) {
            return;
        }
        maybeStartSupportJobs(golem);
        if (golem.fetchingBoneMeal() || golem.fetchingTorch() || golem.fetchingSeat()) {
            return;
        }
        if (this.farmAreaService.hasValidSeat(golem.data())) {
            golem.farmerState(FarmerState.MOVING_TO_SEAT);
            return;
        }
        if (this.farmAreaService.hasAnyWheat(golem.data(), radius)) {
            golem.farmerState(FarmerState.WAIT_GROWTH);
            golem.data().lastActionAt(System.currentTimeMillis());
            return;
        }
        startRest(golem);
    }

    private static boolean hasCarriedSeed(ActiveGolem golem) {
        for (ItemStack stack : golem.carried()) {
            if (stack != null && stack.getType() == Material.WHEAT_SEEDS && !stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void consumeCarriedSeed(ActiveGolem golem) {
        List<ItemStack> leftover = new ArrayList<>();
        boolean removed = false;
        for (ItemStack stack : golem.carried()) {
            if (!removed && stack != null && stack.getType() == Material.WHEAT_SEEDS && !stack.isEmpty()) {
                int amount = stack.getAmount() - 1;
                if (amount > 0) {
                    ItemStack copy = stack.clone();
                    copy.setAmount(amount);
                    leftover.add(copy);
                }
                removed = true;
                continue;
            }
            leftover.add(stack.clone());
        }
        golem.clearCarried();
        for (ItemStack stack : leftover) {
            golem.carry(stack);
        }
    }

    private void returnSeedToChest(ActiveGolem golem) {
        if (!hasCarriedSeed(golem)) {
            return;
        }
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack stack : golem.carried()) {
            if (stack != null && stack.getType() == Material.WHEAT_SEEDS) {
                this.chestService.deposit(golem.data(), stack.clone());
            } else if (stack != null) {
                leftover.add(stack.clone());
            }
        }
        golem.clearCarried();
        for (ItemStack stack : leftover) {
            golem.carry(stack);
        }
    }

    private void continueCraft(ActiveGolem golem, CopperGolem copper) {
        Location craftStand = this.chestService.craftStandLocation(golem.data());
        if (craftStand == null) {
            startRest(golem);
            return;
        }
        Location craftBlock = new Location(
                craftStand.getWorld(),
                Math.floor(golem.data().craftX()) + 0.5D,
                golem.data().craftY(),
                Math.floor(golem.data().craftZ()) + 0.5D
        );
        double toTable = GolemMovement.horizontalDistanceSquared(copper.getLocation(), craftBlock);
        double toStand = GolemMovement.horizontalDistanceSquared(copper.getLocation(), craftStand);
        if (toTable > 2.25D && toStand > 1.0D) {
            golem.farmerState(FarmerState.MOVING_TO_CRAFT);
            movement().walkTowards(copper, craftStand, golem);
            return;
        }
        if (toTable > 2.25D) {
            copper.teleport(craftStand);
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.farmerState(FarmerState.CRAFTING);
        while (settings().farmer.craftBread
                && this.chestService.countItem(golem.data(), Material.WHEAT) >= 3
                && this.chestService.hasSpace(golem.data())) {
            if (!this.chestService.craftBread(golem.data())) {
                break;
            }
        }
        golem.markDirty();
        afterDeposit(golem);
    }

    private void startRest(ActiveGolem golem) {
        long rest = Math.max(0L, settings().restDurationTicks);
        if (rest <= 0L) {
            golem.farmerState(FarmerState.WAITING_SEEDS);
            golem.data().lastActionAt(System.currentTimeMillis());
            return;
        }
        golem.restTicksLeft(rest);
        golem.farmerState(FarmerState.RESTING);
        golem.data().lastActionAt(System.currentTimeMillis());
    }

    private void continueRest(ActiveGolem golem) {
        long left = golem.restTicksLeft() - Math.max(1L, settings().coordinatorPeriodTicks);
        golem.restTicksLeft(left);
        if (left > 0L) {
            return;
        }
        golem.restTicksLeft(0L);
        golem.farmerState(FarmerState.WAITING_SEEDS);
        golem.data().lastActionAt(System.currentTimeMillis());
    }

    private void retryChest(ActiveGolem golem) {
        if (!golem.carried().isEmpty()) {
            if (this.chestService.hasSpace(golem.data())) {
                golem.chestFullNotified(false);
                golem.farmerState(FarmerState.MOVING_TO_CHEST);
            } else {
                golem.data().lastActionAt(System.currentTimeMillis());
            }
            return;
        }
        if (this.chestService.hasSpace(golem.data())) {
            golem.chestFullNotified(false);
            golem.farmerState(FarmerState.WAITING_SEEDS);
            return;
        }
        golem.data().lastActionAt(System.currentTimeMillis());
    }

    private void notifyChestFull(ActiveGolem golem) {
        if (golem.chestFullNotified()) {
            return;
        }
        golem.chestFullNotified(true);
        Player owner = Bukkit.getPlayer(golem.data().ownerUuid());
        if (owner != null) {
            messages().send(owner, "chest-full");
        }
    }

    private Entity resolveEntity(SoulGolemData data) {
        if (data.entityUuid() == null) {
            return null;
        }
        return Bukkit.getEntity(data.entityUuid());
    }

    private void flushDirty() {
        for (ActiveGolem golem : this.registry.all()) {
            if (golem.data().type() != GolemType.FARMER || !golem.dirty()) {
                continue;
            }
            golem.clearDirty();
            this.repository.save(golem.data());
        }
    }

    private void flushDirtySync() {
        for (ActiveGolem golem : this.registry.all()) {
            if (golem.data().type() != GolemType.FARMER || !golem.dirty()) {
                continue;
            }
            golem.clearDirty();
            try {
                this.repository.save(golem.data()).join();
            } catch (Exception ignored) {
            }
        }
    }

    public void flushAll() {
        for (ActiveGolem golem : this.registry.all()) {
            if (golem.data().type() == GolemType.FARMER) {
                golem.markDirty();
            }
        }
        flushDirtySync();
    }
}
