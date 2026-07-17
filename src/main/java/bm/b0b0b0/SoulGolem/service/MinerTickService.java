package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.message.MessageService;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.MinerState;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.repository.GolemRepository;
import bm.b0b0b0.SoulGolem.scheduler.PluginSchedulers;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

public final class MinerTickService {

    private final Plugin plugin;
    private final ConfigurationLoader configurationLoader;
    private final PluginKeys keys;
    private final GolemRegistry registry;
    private final OreTableService oreTable;
    private final SoulChestService chestService;
    private final WorkAreaService workAreaService;
    private final FarmAreaService farmAreaService;
    private final GolemRepository repository;
    private final GolemSpawnService spawnService;
    private final AtomicInteger cursor = new AtomicInteger();
    private ScheduledTask coordinatorTask;
    private ScheduledTask saveTask;

    public MinerTickService(
            Plugin plugin,
            ConfigurationLoader configurationLoader,
            PluginKeys keys,
            GolemRegistry registry,
            OreTableService oreTable,
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
        this.oreTable = oreTable;
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

    public void start() {
        stop();
        long period = Math.max(1L, settings().coordinatorPeriodTicks);
        this.coordinatorTask = PluginSchedulers.runGlobalTimer(this.plugin, this::coordinatorTick, period, period);
        this.saveTask = PluginSchedulers.runAsyncTimer(this.plugin, this::flushDirty, 15L, 15L, java.util.concurrent.TimeUnit.SECONDS);
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
        Collection<ActiveGolem> all = this.registry.all();
        if (all.isEmpty()) {
            return;
        }
        List<ActiveGolem> snapshot = new ArrayList<>(all);
        int batch = Math.max(1, Math.min(settings().golemsPerCoordinatorTick, snapshot.size()));
        int size = snapshot.size();
        int start = Math.floorMod(this.cursor.getAndAdd(batch), size);
        for (int i = 0; i < batch; i++) {
            ActiveGolem golem = snapshot.get((start + i) % size);
            SoulGolemData data = golem.data();
            if (data.type() != bm.b0b0b0.SoulGolem.model.GolemType.MINER) {
                continue;
            }
            if (data.paused()) {
                Location home = this.workAreaService.homeLocation(data);
                if (home != null) {
                    PluginSchedulers.runAt(this.plugin, home, () -> {
                        Entity entity = resolveEntity(data);
                        if (entity instanceof CopperGolem copper) {
                            GolemDisplay.refresh(golem, copper, messages(), this.keys, settings().visuals.textDisplays);
                        }
                    });
                }
                continue;
            }
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

        GolemSpawnService.applySoulEntityFlags(copper);

        if (golem.data().paused()) {
            GolemDisplay.refresh(golem, copper, messages(), this.keys, settings().visuals.textDisplays);
            return;
        }

        long now = System.currentTimeMillis();
        long intervalMs = Math.max(1L, settings().workIntervalTicks) * 50L;
        double speedMultiplier = levelSpeedMultiplier(data);
        long effectiveInterval = (long) (intervalMs / Math.max(0.1D, speedMultiplier));
        MinerState state = golem.state();
        if (state != MinerState.MINING
                && state != MinerState.MOVING_TO_ORE
                && state != MinerState.MOVING_TO_CHEST
                && state != MinerState.RESTING
                && state != MinerState.MOVING_TO_CLEAR
                && state != MinerState.CLEARING
                && state != MinerState.MOVING_TO_TORCH
                && state != MinerState.PLACING_TORCH
                && state != MinerState.MOVING_TO_SEAT
                && state != MinerState.PLACING_SEAT
                && state != MinerState.SITTING
                && now - data.lastActionAt() < effectiveInterval) {
            GolemDisplay.refresh(golem, copper, messages(), this.keys, settings().visuals.textDisplays);
            return;
        }

        if (state == MinerState.MOVING_TO_CLEAR || state == MinerState.CLEARING) {
            equipShovel(copper);
        } else {
            GolemSpawnService.equipTool(copper, data.type(), settings());
        }

        data.position(copper.getLocation().getX(), copper.getLocation().getY(), copper.getLocation().getZ());
        data.rotation(copper.getLocation().getYaw(), copper.getLocation().getPitch());

        switch (state) {
            case IDLE, SEEKING -> beginSeek(golem, copper);
            case MOVING_TO_ORE -> continueMoveToOre(golem, copper);
            case MINING -> continueMine(golem, copper);
            case MOVING_TO_CHEST -> continueMoveToChest(golem, copper);
            case WAITING_CHEST -> retryChest(golem);
            case RESTING -> continueRest(golem);
            case MOVING_TO_CLEAR, CLEARING -> continueClear(golem, copper);
            case MOVING_TO_TORCH, PLACING_TORCH -> continueTorch(golem, copper);
            case MOVING_TO_SEAT, PLACING_SEAT, SITTING -> continueSeat(golem, copper);
            default -> golem.state(MinerState.IDLE);
        }
        GolemDisplay.refresh(golem, copper, messages(), this.keys, settings().visuals.textDisplays);
    }

    private void beginSeek(ActiveGolem golem, CopperGolem copper) {
        if (!golem.carried().isEmpty()) {
            golem.state(MinerState.MOVING_TO_CHEST);
            return;
        }
        if (!this.chestService.hasSpace(golem.data())) {
            golem.state(MinerState.WAITING_CHEST);
            notifyChestFull(golem);
            return;
        }
        golem.chestFullNotified(false);

        int radius = this.chestService.effectiveRadius(golem.data());
        Settings.Miner miner = settings().miner;
        if (miner.clearArea) {
            List<Block> junk = this.farmAreaService.minerJunkToClear(golem.data(), radius, this.oreTable);
            if (!junk.isEmpty()) {
                golem.clearFetchFlags();
                golem.targetCrop(junk.get(0).getLocation());
                golem.state(MinerState.MOVING_TO_CLEAR);
                return;
            }
        }
        Material torch = resolveTorch();
        if (miner.placeTorches
                && this.chestService.countItem(golem.data(), torch) > 0
                && !this.farmAreaService.perimeterTorchSpots(golem.data(), radius, miner.maxTorches).isEmpty()) {
            golem.clearFetchFlags();
            golem.fetchingTorch(true);
            golem.state(MinerState.MOVING_TO_CHEST);
            return;
        }
        if (tryStartSeatJob(golem, radius, miner)) {
            return;
        }

        Location home = this.workAreaService.homeLocation(golem.data());
        if (home == null) {
            return;
        }

        Location ore = findExistingOre(golem, home);
        if (ore == null) {
            int max = Math.max(1, settings().maxActiveOres);
            if (this.workAreaService.countOres(golem.data(), radius, this.oreTable) < max) {
                ore = transformNearest(golem, home);
            }
        }
        if (ore == null) {
            golem.state(MinerState.IDLE);
            golem.data().lastActionAt(System.currentTimeMillis());
            return;
        }
        golem.targetOre(ore);
        golem.oreMaterial(ore.getBlock().getType());
        golem.state(MinerState.MOVING_TO_ORE);
        walkTowards(copper, ore.clone().add(0.5D, 1.0D, 0.5D), golem.data());
    }

    private void continueMoveToOre(ActiveGolem golem, CopperGolem copper) {
        Location target = golem.targetOre();
        if (target == null) {
            golem.state(MinerState.IDLE);
            return;
        }
        Block block = target.getBlock();
        if (!this.oreTable.isOre(block.getType())) {
            golem.targetOre(null);
            golem.state(MinerState.IDLE);
            return;
        }
        Location stand = target.clone().add(0.5D, 1.0D, 0.5D);
        if (horizontalDistanceSquared(copper.getLocation(), stand) > 1.0D) {
            walkTowards(copper, stand, golem.data());
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.state(MinerState.MINING);
        golem.mineTicksLeft(Math.max(1L, settings().mineDurationTicks));
        playMineFx(block);
    }

    private void continueMine(ActiveGolem golem, CopperGolem copper) {
        Location target = golem.targetOre();
        if (target == null) {
            golem.state(MinerState.IDLE);
            return;
        }
        Block block = target.getBlock();
        if (!this.oreTable.isOre(block.getType())) {
            golem.targetOre(null);
            golem.state(MinerState.IDLE);
            return;
        }
        long left = golem.mineTicksLeft() - settings().coordinatorPeriodTicks;
        golem.mineTicksLeft(left);
        if (left > 0L) {
            copper.lookAt(block.getLocation().add(0.5D, 0.5D, 0.5D));
            playMineFx(block);
            return;
        }

        Material pickaxe = Material.matchMaterial(settings().pickaxeMaterial);
        if (pickaxe == null) {
            pickaxe = Material.IRON_PICKAXE;
        }
        Collection<ItemStack> drops = block.getDrops(new ItemStack(pickaxe));
        playMineBurst(block);
        this.workAreaService.restoreBlock(block);

        golem.clearCarried();
        for (ItemStack drop : drops) {
            golem.carry(drop);
        }

        golem.data().incrementBlocksMined();
        int drain = settings().energyPerMine;
        if (drain > 0) {
            golem.data().energy(Math.max(0, golem.data().energy() - drain));
        }
        golem.markDirty();
        golem.targetOre(null);
        golem.oreMaterial(null);
        golem.state(MinerState.MOVING_TO_CHEST);

        Location chestStand = this.chestService.chestStandLocation(golem.data());
        if (chestStand != null) {
            walkTowards(copper, chestStand, golem.data());
        }
    }

    private void continueMoveToChest(ActiveGolem golem, CopperGolem copper) {
        Location chestStand = this.chestService.chestStandLocation(golem.data());
        if (chestStand == null) {
            golem.state(MinerState.IDLE);
            return;
        }
        if (horizontalDistanceSquared(copper.getLocation(), chestStand) > 1.69D) {
            walkTowards(copper, chestStand, golem.data());
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        copper.lookAt(chestStand.clone().add(0.0D, 0.5D, 0.0D));

        if (golem.fetchingTorch()) {
            takeTorchesFromChest(golem);
            return;
        }
        if (golem.fetchingSeat()) {
            takeSeatFromChest(golem);
            return;
        }

        if (golem.carried().isEmpty()) {
            afterDeposit(golem);
            return;
        }
        if (!this.chestService.hasSpace(golem.data())) {
            golem.state(MinerState.WAITING_CHEST);
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

        playDepositFx(chestStand);
        if (!depositedAll) {
            golem.state(MinerState.WAITING_CHEST);
            notifyChestFull(golem);
            return;
        }
        afterDeposit(golem);
    }

    private void afterDeposit(ActiveGolem golem) {
        int radius = this.chestService.effectiveRadius(golem.data());
        this.workAreaService.seedOres(golem.data(), radius, this.oreTable);
        golem.data().lastActionAt(System.currentTimeMillis());
        golem.markDirty();

        Settings.Miner miner = settings().miner;
        if (miner.clearArea) {
            List<Block> junk = this.farmAreaService.minerJunkToClear(golem.data(), radius, this.oreTable);
            if (!junk.isEmpty()) {
                golem.clearFetchFlags();
                golem.targetCrop(junk.get(0).getLocation());
                golem.state(MinerState.MOVING_TO_CLEAR);
                return;
            }
        }
        Material torch = resolveTorch();
        if (miner.placeTorches
                && this.chestService.countItem(golem.data(), torch) > 0
                && !this.farmAreaService.perimeterTorchSpots(golem.data(), radius, miner.maxTorches).isEmpty()) {
            golem.clearFetchFlags();
            golem.fetchingTorch(true);
            golem.state(MinerState.MOVING_TO_CHEST);
            return;
        }
        if (tryStartSeatJob(golem, radius, miner)) {
            return;
        }
        startRest(golem);
    }

    private boolean tryStartSeatJob(ActiveGolem golem, int radius, Settings.Miner miner) {
        if (!miner.placeSeat || this.farmAreaService.hasValidSeat(golem.data())) {
            return false;
        }
        if (this.farmAreaService.findSeatSpot(golem.data(), radius) == null) {
            return false;
        }
        Material carried = carriedStairs(golem);
        if (carried != null) {
            golem.clearFetchFlags();
            Block spot = this.farmAreaService.findSeatSpot(golem.data(), radius);
            golem.targetCrop(spot.getLocation());
            golem.state(MinerState.MOVING_TO_SEAT);
            return true;
        }
        if (this.chestService.findStairsInChest(golem.data()) == null) {
            return false;
        }
        golem.clearFetchFlags();
        golem.fetchingSeat(true);
        golem.state(MinerState.MOVING_TO_CHEST);
        return true;
    }

    private void startRest(ActiveGolem golem) {
        if (this.farmAreaService.hasValidSeat(golem.data())) {
            long rest = Math.max(1L, settings().miner.seatRestTicks);
            golem.clearFetchFlags();
            golem.restTicksLeft(rest);
            golem.state(MinerState.MOVING_TO_SEAT);
            return;
        }
        long rest = Math.max(0L, settings().restDurationTicks);
        if (rest <= 0L) {
            golem.state(MinerState.IDLE);
            return;
        }
        golem.restTicksLeft(rest);
        golem.state(MinerState.RESTING);
    }

    private void continueClear(ActiveGolem golem, CopperGolem copper) {
        int radius = this.chestService.effectiveRadius(golem.data());
        if (!golem.carried().isEmpty() && !this.chestService.hasSpace(golem.data())) {
            golem.state(MinerState.MOVING_TO_CHEST);
            return;
        }
        Location target = golem.targetCrop();
        Block junk = target == null ? null : target.getBlock();
        if (junk == null || junk.getType().isAir() || !isClearableJunk(junk)) {
            List<Block> list = this.farmAreaService.minerJunkToClear(golem.data(), radius, this.oreTable);
            if (list.isEmpty()) {
                golem.targetCrop(null);
                if (!golem.carried().isEmpty()) {
                    golem.state(MinerState.MOVING_TO_CHEST);
                    return;
                }
                golem.state(MinerState.IDLE);
                return;
            }
            junk = pickNearest(copper.getLocation(), list);
            golem.targetCrop(junk.getLocation());
        }
        Location stand = this.farmAreaService.standForClear(junk);
        if (stand == null) {
            golem.targetCrop(null);
            return;
        }
        if (horizontalDistanceSquared(copper.getLocation(), stand) > 2.25D) {
            golem.state(MinerState.MOVING_TO_CLEAR);
            walkTowards(copper, stand, golem.data());
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.state(MinerState.CLEARING);
        if (FarmAreaService.isVegetation(junk.getType()) || junk.getType() == Material.SNOW) {
            FarmAreaService.clearVegetation(junk);
            if (junk.getType() == Material.SNOW) {
                junk.setType(Material.AIR, false);
            }
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
        List<Block> left = this.farmAreaService.minerJunkToClear(golem.data(), radius, this.oreTable);
        if (!left.isEmpty() && (golem.carried().isEmpty() || this.chestService.hasSpace(golem.data()))) {
            golem.targetCrop(pickNearest(copper.getLocation(), left).getLocation());
            golem.state(MinerState.MOVING_TO_CLEAR);
            return;
        }
        if (!golem.carried().isEmpty()) {
            golem.state(MinerState.MOVING_TO_CHEST);
            return;
        }
        golem.state(MinerState.IDLE);
    }

    private void takeTorchesFromChest(ActiveGolem golem) {
        Material torch = resolveTorch();
        int radius = this.chestService.effectiveRadius(golem.data());
        Settings.Miner miner = settings().miner;
        List<Block> spots = this.farmAreaService.perimeterTorchSpots(golem.data(), radius, miner.maxTorches);
        if (spots.isEmpty() || this.chestService.countItem(golem.data(), torch) <= 0) {
            golem.clearFetchFlags();
            golem.state(MinerState.IDLE);
            return;
        }
        int want = Math.min(Math.max(1, miner.torchesPerTrip), spots.size());
        want = Math.min(want, this.chestService.countItem(golem.data(), torch));
        if (want <= 0 || !this.chestService.takeItem(golem.data(), torch, want)) {
            golem.clearFetchFlags();
            golem.state(MinerState.IDLE);
            return;
        }
        golem.clearCarried();
        golem.carry(new ItemStack(torch, want));
        golem.clearFetchFlags();
        golem.targetCrop(spots.get(0).getLocation());
        golem.state(MinerState.MOVING_TO_TORCH);
        golem.markDirty();
    }

    private void continueTorch(ActiveGolem golem, CopperGolem copper) {
        Material torch = resolveTorch();
        if (countCarried(golem, torch) <= 0) {
            golem.targetCrop(null);
            golem.state(MinerState.IDLE);
            return;
        }
        Location target = golem.targetCrop();
        if (target == null) {
            int radius = this.chestService.effectiveRadius(golem.data());
            List<Block> spots = this.farmAreaService.perimeterTorchSpots(
                    golem.data(), radius, settings().miner.maxTorches
            );
            if (spots.isEmpty()) {
                returnCarriedToChest(golem, torch);
                golem.state(MinerState.IDLE);
                return;
            }
            target = spots.get(0).getLocation();
            golem.targetCrop(target);
        }
        Location stand = target.clone().add(0.5D, 0.0D, 0.5D);
        if (horizontalDistanceSquared(copper.getLocation(), stand) > 1.5D) {
            golem.state(MinerState.MOVING_TO_TORCH);
            walkTowards(copper, stand, golem.data());
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.state(MinerState.PLACING_TORCH);
        Block spot = target.getBlock();
        if (spot.getType().isAir()) {
            this.farmAreaService.placeTorch(spot, torch, golem.data().id());
            consumeCarried(golem, torch, 1);
        }
        golem.targetCrop(null);
        golem.markDirty();
        if (countCarried(golem, torch) > 0) {
            int radius = this.chestService.effectiveRadius(golem.data());
            List<Block> spots = this.farmAreaService.perimeterTorchSpots(
                    golem.data(), radius, settings().miner.maxTorches
            );
            if (!spots.isEmpty()) {
                golem.targetCrop(spots.get(0).getLocation());
                golem.state(MinerState.MOVING_TO_TORCH);
                return;
            }
            returnCarriedToChest(golem, torch);
        }
        golem.state(MinerState.IDLE);
        golem.data().lastActionAt(System.currentTimeMillis());
    }

    private Material resolveTorch() {
        Material torch = Material.matchMaterial(settings().miner.torchMaterial);
        return torch != null ? torch : Material.TORCH;
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

    private static boolean isClearableJunk(Block block) {
        Material type = block.getType();
        if (type.isAir() || SoulChestService.isChestLike(type) || type == Material.CRAFTING_TABLE) {
            return false;
        }
        if (org.bukkit.Tag.STAIRS.isTagged(type)) {
            return false;
        }
        if (type == Material.TORCH || type == Material.WALL_TORCH
                || type == Material.SOUL_TORCH || type == Material.SOUL_WALL_TORCH) {
            return false;
        }
        return type.isSolid() || type == Material.SNOW || FarmAreaService.isVegetation(type);
    }

    private static Block pickNearest(Location from, List<Block> list) {
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
        List<ItemStack> leftover = new ArrayList<>();
        int left = amount;
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

    private void takeSeatFromChest(ActiveGolem golem) {
        int radius = this.chestService.effectiveRadius(golem.data());
        if (this.farmAreaService.hasValidSeat(golem.data())) {
            golem.clearFetchFlags();
            golem.state(MinerState.MOVING_TO_SEAT);
            return;
        }
        Material already = carriedStairs(golem);
        if (already != null) {
            Block spot = this.farmAreaService.findSeatSpot(golem.data(), radius);
            if (spot == null) {
                returnCarriedToChest(golem, already);
                golem.clearFetchFlags();
                golem.state(MinerState.IDLE);
                return;
            }
            golem.clearFetchFlags();
            golem.targetCrop(spot.getLocation());
            golem.state(MinerState.MOVING_TO_SEAT);
            return;
        }
        Material stairs = this.chestService.findStairsInChest(golem.data());
        Block spot = this.farmAreaService.findSeatSpot(golem.data(), radius);
        if (stairs == null || spot == null) {
            golem.clearFetchFlags();
            golem.state(MinerState.IDLE);
            return;
        }
        if (!this.chestService.takeItem(golem.data(), stairs, 1)) {
            golem.clearFetchFlags();
            golem.state(MinerState.IDLE);
            return;
        }
        golem.clearCarried();
        golem.carry(new ItemStack(stairs, 1));
        golem.clearFetchFlags();
        golem.targetCrop(spot.getLocation());
        golem.state(MinerState.MOVING_TO_SEAT);
        golem.markDirty();
    }

    private void continueSeat(ActiveGolem golem, CopperGolem copper) {
        int radius = this.chestService.effectiveRadius(golem.data());
        Material carried = carriedStairs(golem);
        if (carried != null && !this.farmAreaService.hasValidSeat(golem.data())) {
            placeCarriedSeat(golem, copper, carried, radius);
            return;
        }

        if (!this.farmAreaService.hasValidSeat(golem.data())) {
            if (tryStartSeatJob(golem, radius, settings().miner)) {
                return;
            }
            golem.state(MinerState.IDLE);
            return;
        }

        Location seatStand = this.farmAreaService.seatStandLocation(golem.data());
        if (seatStand == null) {
            golem.state(MinerState.IDLE);
            return;
        }
        if (!this.farmAreaService.isSeatedOnOwnBench(copper.getLocation(), golem.data())
                || horizontalDistanceSquared(copper.getLocation(), seatStand) > 0.35D) {
            if (horizontalDistanceSquared(copper.getLocation(), seatStand) > 1.2D) {
                golem.state(MinerState.MOVING_TO_SEAT);
                walkTowards(copper, seatStand, golem.data());
                return;
            }
            copper.setVelocity(new Vector(0, 0, 0));
            Location sit = seatStand.clone();
            Location look = this.workAreaService.homeLocation(golem.data());
            if (look != null) {
                sit.setYaw(yawTo(sit, look));
            }
            sit.setPitch(0.0F);
            copper.teleport(sit);
            if (look != null) {
                copper.lookAt(look.clone().add(0.0D, 1.0D, 0.0D));
            }
        }

        if (golem.restTicksLeft() <= 0L) {
            golem.restTicksLeft(Math.max(1L, settings().miner.seatRestTicks));
        }
        golem.state(MinerState.SITTING);
        long left = golem.restTicksLeft() - Math.max(1L, settings().coordinatorPeriodTicks);
        golem.restTicksLeft(left);
        if (left > 0L) {
            return;
        }
        golem.restTicksLeft(0L);
        golem.data().lastActionAt(System.currentTimeMillis());
        golem.state(MinerState.IDLE);
    }

    private void placeCarriedSeat(ActiveGolem golem, CopperGolem copper, Material carriedStairs, int radius) {
        Location target = golem.targetCrop();
        if (target == null || !canPlaceSeatAt(target.getBlock())) {
            Block spot = this.farmAreaService.findSeatSpot(golem.data(), radius);
            if (spot == null) {
                returnCarriedToChest(golem, carriedStairs);
                golem.targetCrop(null);
                golem.state(MinerState.IDLE);
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
        if (horizontalDistanceSquared(copper.getLocation(), stand) > 2.25D) {
            golem.state(MinerState.MOVING_TO_SEAT);
            walkTowards(copper, stand, golem.data());
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.state(MinerState.PLACING_SEAT);
        this.farmAreaService.relocateTorchForSeat(golem.data(), seatSpot, radius, golem.data().id());
        if (!this.farmAreaService.placeSeat(golem.data(), seatSpot, carriedStairs, golem.data().id())) {
            returnCarriedToChest(golem, carriedStairs);
            golem.targetCrop(null);
            golem.state(MinerState.IDLE);
            return;
        }
        consumeCarried(golem, carriedStairs, 1);
        golem.targetCrop(null);
        golem.markDirty();
        golem.restTicksLeft(Math.max(1L, settings().miner.seatRestTicks));
        golem.state(MinerState.MOVING_TO_SEAT);
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

    private void continueRest(ActiveGolem golem) {
        long left = golem.restTicksLeft() - Math.max(1L, settings().coordinatorPeriodTicks);
        golem.restTicksLeft(left);
        if (left > 0L) {
            return;
        }
        golem.restTicksLeft(0L);
        golem.data().lastActionAt(System.currentTimeMillis());
        golem.state(MinerState.IDLE);
    }

    private void walkTowards(CopperGolem copper, Location target, SoulGolemData data) {
        Location from = copper.getLocation();
        if (from.getWorld() == null || target.getWorld() == null || !from.getWorld().equals(target.getWorld())) {
            return;
        }
        Location steerTarget = avoidChestWaypoint(from, target, data);
        Vector delta = steerTarget.toVector().subtract(from.toVector());
        delta.setY(0.0D);
        double length = delta.length();
        if (length < 0.05D) {
            return;
        }
        double step = Math.min(Math.max(0.55D, settings().walkSpeed * 0.65D), length);
        Location next = from.clone().add(delta.normalize().multiply(step));
        next.setY(steerTarget.getY());
        if (this.chestService.collidesWithChest(data, next)) {
            next = sidestepAroundChest(from, target, data);
            if (next == null) {
                return;
            }
        }
        next.setYaw(yawTo(from, target));
        next.setPitch(0.0F);
        Block ground = next.getBlock().getRelative(0, -1, 0);
        if (!ground.getType().isSolid()) {
            Block alt = next.getBlock();
            if (alt.getType().isSolid() && !SoulChestService.isChestLike(alt.getType())) {
                next.setY(alt.getY() + 1.0D);
            }
        }
        if (this.chestService.collidesWithChest(data, next)) {
            return;
        }
        copper.teleport(next);
        copper.lookAt(target);
    }

    private Location avoidChestWaypoint(Location from, Location target, SoulGolemData data) {
        if (!pathCrossesChest(from, target, data)) {
            return target;
        }
        Location detour = sidestepAroundChest(from, target, data);
        return detour != null ? detour : target;
    }

    private boolean pathCrossesChest(Location from, Location target, SoulGolemData data) {
        double cx = data.chestX() + 0.5D;
        double cz = data.chestZ() + 0.5D;
        double ax = from.getX();
        double az = from.getZ();
        double bx = target.getX();
        double bz = target.getZ();
        double abx = bx - ax;
        double abz = bz - az;
        double lengthSq = abx * abx + abz * abz;
        if (lengthSq < 0.0001D) {
            return this.chestService.collidesWithChest(data, from);
        }
        double t = ((cx - ax) * abx + (cz - az) * abz) / lengthSq;
        if (t < 0.0D || t > 1.0D) {
            return false;
        }
        double px = ax + t * abx;
        double pz = az + t * abz;
        double dx = px - cx;
        double dz = pz - cz;
        return dx * dx + dz * dz < 0.85D;
    }

    private Location sidestepAroundChest(Location from, Location target, SoulGolemData data) {
        double cx = data.chestX() + 0.5D;
        double cz = data.chestZ() + 0.5D;
        Vector fromChest = new Vector(from.getX() - cx, 0.0D, from.getZ() - cz);
        if (fromChest.lengthSquared() < 0.0001D) {
            fromChest = new Vector(1.0D, 0.0D, 0.0D);
        }
        fromChest.normalize();
        Vector left = new Vector(-fromChest.getZ(), 0.0D, fromChest.getX()).multiply(1.15D);
        Vector right = left.clone().multiply(-1.0D);

        Location optionLeft = from.clone().add(left);
        Location optionRight = from.clone().add(right);
        optionLeft.setY(target.getY());
        optionRight.setY(target.getY());

        boolean leftOk = !this.chestService.collidesWithChest(data, optionLeft);
        boolean rightOk = !this.chestService.collidesWithChest(data, optionRight);
        if (leftOk && rightOk) {
            return horizontalDistanceSquared(optionLeft, target) <= horizontalDistanceSquared(optionRight, target)
                    ? optionLeft : optionRight;
        }
        if (leftOk) {
            return optionLeft;
        }
        if (rightOk) {
            return optionRight;
        }
        Location around = from.clone().add(fromChest.clone().multiply(1.2D));
        around.setY(target.getY());
        return this.chestService.collidesWithChest(data, around) ? null : around;
    }

    private static float yawTo(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private static double horizontalDistanceSquared(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private void retryChest(ActiveGolem golem) {
        if (!golem.carried().isEmpty()) {
            if (this.chestService.hasSpace(golem.data())) {
                golem.chestFullNotified(false);
                golem.state(MinerState.MOVING_TO_CHEST);
            } else {
                golem.data().lastActionAt(System.currentTimeMillis());
            }
            return;
        }
        if (this.chestService.hasSpace(golem.data())) {
            golem.chestFullNotified(false);
            golem.state(MinerState.IDLE);
            return;
        }
        golem.data().lastActionAt(System.currentTimeMillis());
    }

    private void playMineFx(Block block) {
        Settings.Visuals visuals = settings().visuals;
        if (!visuals.particles) {
            if (visuals.sounds) {
                playMineSound(block);
            }
            return;
        }
        Location at = block.getLocation().add(0.5D, 0.55D, 0.5D);
        World world = block.getWorld();
        world.spawnParticle(Particle.BLOCK, at, 18, 0.28D, 0.28D, 0.28D, 0.02D, block.getBlockData());
        world.spawnParticle(Particle.CRIT, at, 8, 0.2D, 0.2D, 0.2D, 0.05D);
        world.spawnParticle(Particle.DUST, at, 6, 0.2D, 0.15D, 0.2D, new Particle.DustOptions(org.bukkit.Color.fromRGB(192, 132, 252), 1.1F));
        if (visuals.sounds) {
            playMineSound(block);
        }
    }

    private void playMineBurst(Block block) {
        Location at = block.getLocation().add(0.5D, 0.5D, 0.5D);
        World world = block.getWorld();
        world.spawnParticle(Particle.BLOCK, at, 40, 0.35D, 0.35D, 0.35D, 0.08D, block.getBlockData());
        world.spawnParticle(Particle.CLOUD, at, 10, 0.2D, 0.2D, 0.2D, 0.02D);
        world.spawnParticle(Particle.DUST, at, 14, 0.3D, 0.25D, 0.3D, new Particle.DustOptions(org.bukkit.Color.fromRGB(168, 85, 247), 1.3F));
        playMineSound(block);
    }

    private void playDepositFx(Location chestStand) {
        Location at = chestStand.clone().add(0.0D, 0.8D, 0.0D);
        World world = chestStand.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.HAPPY_VILLAGER, at, 10, 0.35D, 0.25D, 0.35D, 0.0D);
        world.spawnParticle(Particle.DUST, at, 12, 0.3D, 0.2D, 0.3D, new Particle.DustOptions(org.bukkit.Color.fromRGB(192, 132, 252), 1.2F));
        Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft("block.chest.close"));
        if (sound != null) {
            world.playSound(at, sound, 0.5F, 1.2F);
        }
    }

    private void playMineSound(Block block) {
        Settings.Visuals visuals = settings().visuals;
        Sound sound = resolveSound(visuals.mineSound);
        if (sound != null) {
            block.getWorld().playSound(block.getLocation(), sound, visuals.soundVolume, visuals.soundPitch);
        }
    }

    private static Sound resolveSound(String name) {
        NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase(java.util.Locale.ROOT).replace('_', '.'));
        Sound sound = Registry.SOUNDS.get(key);
        if (sound != null) {
            return sound;
        }
        return Registry.SOUNDS.get(NamespacedKey.minecraft("block.stone.break"));
    }

    private Location findExistingOre(ActiveGolem golem, Location home) {
        int radius = this.chestService.effectiveRadius(golem.data());
        World world = home.getWorld();
        int baseX = (int) Math.floor(golem.data().homeX());
        int baseY = (int) Math.floor(golem.data().homeY());
        int baseZ = (int) Math.floor(golem.data().homeZ());
        Location golemLoc = resolveEntity(golem.data()) instanceof CopperGolem c ? c.getLocation() : home;

        Location bestFar = null;
        double bestFarDist = Double.MAX_VALUE;
        Location bestAny = null;
        double bestAnyDist = Double.MAX_VALUE;

        for (int x = -radius + 1; x <= radius - 1; x++) {
            for (int z = -radius + 1; z <= radius - 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                Block block = world.getBlockAt(baseX + x, baseY, baseZ + z);
                if (this.chestService.isChestColumn(golem.data(), block)) {
                    continue;
                }
                if (!this.oreTable.isOre(block.getType())) {
                    continue;
                }
                Location stand = block.getLocation().add(0.5D, 1.0D, 0.5D);
                double dist = horizontalDistanceSquared(golemLoc, stand);
                if (dist < bestAnyDist) {
                    bestAnyDist = dist;
                    bestAny = block.getLocation();
                }
                if (dist >= 2.25D && dist < bestFarDist) {
                    bestFarDist = dist;
                    bestFar = block.getLocation();
                }
            }
        }
        return bestFar != null ? bestFar : bestAny;
    }

    private Location transformNearest(ActiveGolem golem, Location home) {
        int radius = this.chestService.effectiveRadius(golem.data());
        World world = home.getWorld();
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        int baseX = (int) Math.floor(golem.data().homeX());
        int baseY = (int) Math.floor(golem.data().homeY());
        int baseZ = (int) Math.floor(golem.data().homeZ());
        Location golemLoc = resolveEntity(golem.data()) instanceof CopperGolem c ? c.getLocation() : home;
        for (int x = -radius + 1; x <= radius - 1; x++) {
            for (int z = -radius + 1; z <= radius - 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                Block block = world.getBlockAt(baseX + x, baseY, baseZ + z);
                if (SoulChestService.isChestLike(block.getType()) || this.chestService.isChestColumn(golem.data(), block)) {
                    continue;
                }
                if (!this.oreTable.isTransformable(block.getType())) {
                    continue;
                }
                Location stand = block.getLocation().add(0.5D, 1.0D, 0.5D);
                double dist = horizontalDistanceSquared(golemLoc, stand);
                if (dist < 1.0D) {
                    continue;
                }
                if (dist < bestDist) {
                    bestDist = dist;
                    best = block.getLocation();
                }
            }
        }
        if (best == null) {
            return null;
        }
        Material ore = this.oreTable.rollOre();
        this.workAreaService.placeOre(best.getBlock(), golem.data().id(), ore);
        return best;
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

    private double levelSpeedMultiplier(SoulGolemData data) {
        for (Settings.LevelStats level : settings().levels) {
            if (level.level == data.level()) {
                return level.speedMultiplier;
            }
        }
        return 1.0D;
    }

    private void flushDirty() {
        for (ActiveGolem golem : this.registry.all()) {
            if (golem.data().type() != bm.b0b0b0.SoulGolem.model.GolemType.MINER || !golem.dirty()) {
                continue;
            }
            SoulGolemData data = golem.data();
            golem.clearDirty();
            this.repository.save(data);
        }
    }

    private void flushDirtySync() {
        for (ActiveGolem golem : this.registry.all()) {
            if (golem.data().type() != bm.b0b0b0.SoulGolem.model.GolemType.MINER || !golem.dirty()) {
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
            if (golem.data().type() == bm.b0b0b0.SoulGolem.model.GolemType.MINER) {
                golem.markDirty();
            }
        }
        flushDirtySync();
    }
}
