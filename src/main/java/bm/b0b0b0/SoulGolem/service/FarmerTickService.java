package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.FarmerState;
import bm.b0b0b0.SoulGolem.model.GolemType;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.repository.GolemRepository;
import bm.b0b0b0.SoulGolem.scheduler.PluginSchedulers;
import bm.b0b0b0.SoulGolem.service.farmer.FarmerCarried;
import bm.b0b0b0.SoulGolem.service.farmer.FarmerChestWork;
import bm.b0b0b0.SoulGolem.service.farmer.FarmerContext;
import bm.b0b0b0.SoulGolem.service.farmer.FarmerCycle;
import bm.b0b0b0.SoulGolem.service.farmer.FarmerFieldWork;
import bm.b0b0b0.SoulGolem.service.farmer.FarmerSupportWork;
import bm.b0b0b0.SoulGolem.service.setup.GolemSetupWork;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class FarmerTickService {

    private final FarmerContext context;
    private final FarmerCycle cycle;
    private final FarmerFieldWork field;
    private final FarmerSupportWork support;
    private final FarmerChestWork chest;
    private final GolemSetupWork setup;
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
        this.context = new FarmerContext(
                plugin,
                configurationLoader,
                keys,
                registry,
                chestService,
                workAreaService,
                farmAreaService,
                repository,
                spawnService
        );
        FarmerCarried carried = new FarmerCarried(this.context);
        this.cycle = new FarmerCycle(this.context);
        this.field = new FarmerFieldWork(this.context, carried);
        this.support = new FarmerSupportWork(this.context, carried);
        this.chest = new FarmerChestWork(this.context);
        this.setup = new GolemSetupWork(
                chestService,
                workAreaService,
                farmAreaService,
                null,
                this.context.movement()
        );
        this.field.wire(this.cycle, this.support);
        this.support.wire(this.cycle);
        this.chest.wire(this.cycle, this.field, this.support);
    }

    public void start() {
        stop();
        long period = Math.max(1L, this.context.settings().coordinatorPeriodTicks);
        this.coordinatorTask = PluginSchedulers.runGlobalTimer(
                this.context.plugin(), this::coordinatorTick, period, period);
        this.saveTask = PluginSchedulers.runAsyncTimer(
                this.context.plugin(), this::flushDirty, 15L, 15L, TimeUnit.SECONDS);
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

    public void flushAll() {
        for (ActiveGolem golem : this.context.registry().all()) {
            if (golem.data().type() == GolemType.FARMER) {
                golem.markDirty();
            }
        }
        flushDirtySync();
    }

    private void coordinatorTick() {
        List<ActiveGolem> farmers = new ArrayList<>();
        for (ActiveGolem golem : this.context.registry().all()) {
            if (golem.data().type() == GolemType.FARMER) {
                farmers.add(golem);
            }
        }
        if (farmers.isEmpty()) {
            return;
        }
        int batch = Math.max(1, Math.min(this.context.settings().golemsPerCoordinatorTick, farmers.size()));
        int size = farmers.size();
        int start = Math.floorMod(this.cursor.getAndAdd(batch), size);
        for (int i = 0; i < batch; i++) {
            ActiveGolem golem = farmers.get((start + i) % size);
            SoulGolemData data = golem.data();
            Location home = this.context.workAreaService().homeLocation(data);
            if (home == null) {
                continue;
            }
            PluginSchedulers.runAt(this.context.plugin(), home, () -> tickGolem(golem));
        }
    }

    private void tickGolem(ActiveGolem golem) {
        SoulGolemData data = golem.data();
        Entity entity = resolveEntity(data);
        if (!(entity instanceof CopperGolem copper)) {
            this.context.spawnService().ensureAlive(data);
            return;
        }

        GolemSpawnService.applySoulEntityFlags(copper, data.type());
        FarmerState state = golem.farmerState();
        if (data.paused() || isStandingWork(state)) {
            this.context.movement().stop(copper);
        }
        boolean setupBusy = GolemSetupWork.isSetupState(golem);
        boolean workingMove = setupBusy
                || state == FarmerState.MOVING_TO_CLEAR
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
                || state == FarmerState.MOVING_TO_FENCE_CLEAR
                || state == FarmerState.CLEARING_FENCE
                || state == FarmerState.MOVING_TO_FENCE
                || state == FarmerState.PLACING_FENCE
                || state == FarmerState.MOVING_TO_GATE
                || state == FarmerState.PLACING_GATE
                || state == FarmerState.MOVING_TO_CLOSE_GATE
                || state == FarmerState.CLOSING_GATE
                || state == FarmerState.MOVING_TO_SHELTER
                || state == FarmerState.BUILDING_SHELTER
                || state == FarmerState.SHELTERING
                || state == FarmerState.MOVING_TO_COMBAT
                || state == FarmerState.COMBATING
                || state == FarmerState.MOVING_TO_CHEST
                || state == FarmerState.MOVING_TO_CRAFT;
        if (!workingMove
                && state != FarmerState.WAITING_SEEDS
                && this.context.farmAreaService().needsRescue(copper.getLocation(), data)) {
            Location safe = this.context.farmAreaService().safeStandNearHome(data);
            if (safe != null) {
                copper.teleport(safe);
            }
        }
        if (GolemSetupWork.isClearingSetup(golem)
                || state == FarmerState.MOVING_TO_CLEAR
                || state == FarmerState.CLEARING
                || state == FarmerState.MOVING_TO_FENCE_CLEAR
                || state == FarmerState.CLEARING_FENCE) {
            this.context.equipShovel(copper);
        } else if (this.context.combat().isCombatState(golem)) {
            this.context.combat().equipIfArmed(golem, copper);
        } else {
            GolemSpawnService.equipTool(copper, data.type(), this.context.settings());
        }

        Settings.TextDisplays style = this.context.settings().visuals.textDisplays;
        if (data.paused()) {
            GolemDisplay.refresh(golem, copper, this.context.messages(), this.context.keys(), style);
            return;
        }

        data.position(copper.getLocation().getX(), copper.getLocation().getY(), copper.getLocation().getZ());
        data.rotation(copper.getLocation().getYaw(), copper.getLocation().getPitch());

        if (setupBusy) {
            this.setup.tick(golem, copper);
            GolemDisplay.refresh(golem, copper, this.context.messages(), this.context.keys(), style);
            return;
        }

        boolean closingGate = state == FarmerState.MOVING_TO_CLOSE_GATE
                || state == FarmerState.CLOSING_GATE;
        Settings.Farmer farmer = this.context.settings().farmer;
        if (farmer.placeFence && farmer.gateAutoClose) {
            if (closingGate) {
                this.support.continueCloseGate(golem, copper);
                GolemDisplay.refresh(golem, copper, this.context.messages(), this.context.keys(), style);
                return;
            }
            if (this.context.gateWatch().shouldStartClose(golem, true, farmer.gateCloseDelayMs)) {
                golem.clearFetchFlags();
                golem.wanderTarget(null);
                golem.farmerState(FarmerState.MOVING_TO_CLOSE_GATE);
                this.support.continueCloseGate(golem, copper);
                GolemDisplay.refresh(golem, copper, this.context.messages(), this.context.keys(), style);
                return;
            }
        }

        if (farmer.rainShelter && this.context.rainShelter().shouldSeekShelter(golem, copper, true)) {
            if (state != FarmerState.MOVING_TO_SHELTER
                    && state != FarmerState.BUILDING_SHELTER
                    && state != FarmerState.SHELTERING) {
                golem.clearFetchFlags();
                golem.wanderTarget(null);
                golem.farmerState(FarmerState.MOVING_TO_SHELTER);
            }
            this.support.continueShelter(golem, copper);
            GolemDisplay.refresh(golem, copper, this.context.messages(), this.context.keys(), style);
            return;
        }

        if (this.context.combat().isCombatState(golem)) {
            this.context.combat().continueCombat(golem, copper);
            GolemDisplay.refresh(golem, copper, this.context.messages(), this.context.keys(), style);
            return;
        }
        if (this.context.combat().tryStart(golem, copper)) {
            if (this.context.combat().isCombatState(golem)) {
                this.context.combat().continueCombat(golem, copper);
            } else if (golem.fetchingWeapon()) {
                this.chest.continueDeposit(golem, copper);
            }
            GolemDisplay.refresh(golem, copper, this.context.messages(), this.context.keys(), style);
            return;
        }

        switch (state) {
            case WAITING_SEEDS -> this.cycle.beginCycle(golem, copper);
            case PREPARE_FIELD, MOVING_TO_TILL, TILLING -> this.field.continueTill(golem, copper);
            case MOVING_TO_PLANT, PLANTING -> this.field.continuePlant(golem, copper);
            case WAIT_GROWTH, WANDERING -> this.field.waitGrowth(golem, copper);
            case MOVING_TO_HARVEST, HARVESTING -> this.field.continueHarvest(golem, copper);
            case MOVING_TO_CHEST -> this.chest.continueDeposit(golem, copper);
            case WAITING_CHEST -> this.cycle.retryChest(golem);
            case MOVING_TO_CRAFT, CRAFTING -> this.chest.continueCraft(golem, copper);
            case MOVING_TO_TORCH, PLACING_TORCH -> this.support.continueTorch(golem, copper);
            case MOVING_TO_SEAT, PLACING_SEAT, SITTING -> this.support.continueSeat(golem, copper);
            case MOVING_TO_CLEAR, CLEARING -> this.support.continueClear(golem, copper);
            case MOVING_TO_FENCE_CLEAR, CLEARING_FENCE,
                 MOVING_TO_FENCE, PLACING_FENCE,
                 MOVING_TO_GATE, PLACING_GATE -> this.support.continueFence(golem, copper);
            case MOVING_TO_CLOSE_GATE, CLOSING_GATE -> this.support.continueCloseGate(golem, copper);
            case MOVING_TO_BONEMEAL, APPLYING_BONEMEAL -> this.field.continueBoneMeal(golem, copper);
            case RESTING -> this.cycle.continueRest(golem);
            case MOVING_TO_SHELTER, BUILDING_SHELTER, SHELTERING -> this.support.continueShelter(golem, copper);
            case MOVING_TO_COMBAT, COMBATING -> this.context.combat().continueCombat(golem, copper);
            default -> golem.farmerState(FarmerState.WAITING_SEEDS);
        }
        GolemDisplay.refresh(golem, copper, this.context.messages(), this.context.keys(), style);
    }

    private Entity resolveEntity(SoulGolemData data) {
        if (data.entityUuid() == null) {
            return null;
        }
        return Bukkit.getEntity(data.entityUuid());
    }

    private static boolean isStandingWork(FarmerState state) {
        return switch (state) {
            case WAITING_SEEDS, WAIT_GROWTH, WAITING_CHEST, RESTING, SITTING, SHELTERING,
                 TILLING, PLANTING, HARVESTING, APPLYING_BONEMEAL, CRAFTING,
                 PLACING_TORCH, PLACING_SEAT, PLACING_FENCE, PLACING_GATE,
                 CLEARING, CLEARING_FENCE, CLOSING_GATE, BUILDING_SHELTER -> true;
            default -> false;
        };
    }

    private void flushDirty() {
        for (ActiveGolem golem : this.context.registry().all()) {
            if (golem.data().type() != GolemType.FARMER || !golem.dirty()) {
                continue;
            }
            golem.clearDirty();
            this.context.repository().save(golem.data());
        }
    }

    private void flushDirtySync() {
        for (ActiveGolem golem : this.context.registry().all()) {
            if (golem.data().type() != GolemType.FARMER || !golem.dirty()) {
                continue;
            }
            golem.clearDirty();
            try {
                this.context.repository().save(golem.data()).join();
            } catch (Exception ignored) {
            }
        }
    }
}
