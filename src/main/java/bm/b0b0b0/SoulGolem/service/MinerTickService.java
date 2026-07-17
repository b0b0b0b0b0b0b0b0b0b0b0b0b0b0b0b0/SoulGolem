package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.GolemType;
import bm.b0b0b0.SoulGolem.model.MinerState;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.repository.GolemRepository;
import bm.b0b0b0.SoulGolem.scheduler.PluginSchedulers;
import bm.b0b0b0.SoulGolem.service.miner.MinerCarried;
import bm.b0b0b0.SoulGolem.service.miner.MinerChestWork;
import bm.b0b0b0.SoulGolem.service.miner.MinerContext;
import bm.b0b0b0.SoulGolem.service.miner.MinerMineWork;
import bm.b0b0b0.SoulGolem.service.miner.MinerSupportWork;
import bm.b0b0b0.SoulGolem.service.setup.GolemSetupWork;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class MinerTickService {

    private final MinerContext context;
    private final MinerMineWork mine;
    private final MinerSupportWork support;
    private final MinerChestWork chest;
    private final GolemSetupWork setup;
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
        this.context = new MinerContext(
                plugin,
                configurationLoader,
                keys,
                registry,
                oreTable,
                chestService,
                workAreaService,
                farmAreaService,
                repository,
                spawnService
        );
        MinerCarried carried = new MinerCarried(this.context);
        this.mine = new MinerMineWork(this.context);
        this.support = new MinerSupportWork(this.context, carried);
        this.chest = new MinerChestWork(this.context, carried);
        this.setup = new GolemSetupWork(
                chestService,
                workAreaService,
                farmAreaService,
                oreTable,
                this.context.movement()
        );
        this.mine.wire(this.support);
        this.chest.wire(this.support);
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
            if (golem.data().type() == GolemType.MINER) {
                golem.markDirty();
            }
        }
        flushDirtySync();
    }

    private void coordinatorTick() {
        Collection<ActiveGolem> all = this.context.registry().all();
        if (all.isEmpty()) {
            return;
        }
        List<ActiveGolem> snapshot = new ArrayList<>(all);
        int batch = Math.max(1, Math.min(this.context.settings().golemsPerCoordinatorTick, snapshot.size()));
        int size = snapshot.size();
        int start = Math.floorMod(this.cursor.getAndAdd(batch), size);
        for (int i = 0; i < batch; i++) {
            ActiveGolem golem = snapshot.get((start + i) % size);
            SoulGolemData data = golem.data();
            if (data.type() != GolemType.MINER) {
                continue;
            }
            if (data.paused()) {
                Location home = this.context.workAreaService().homeLocation(data);
                if (home != null) {
                    PluginSchedulers.runAt(this.context.plugin(), home, () -> {
                        Entity entity = this.context.resolveEntity(data);
                        if (entity instanceof CopperGolem copper) {
                            GolemDisplay.refresh(
                                    golem,
                                    copper,
                                    this.context.messages(),
                                    this.context.keys(),
                                    this.context.settings().visuals.textDisplays
                            );
                        }
                    });
                }
                continue;
            }
            Location home = this.context.workAreaService().homeLocation(data);
            if (home == null) {
                continue;
            }
            PluginSchedulers.runAt(this.context.plugin(), home, () -> tickGolem(golem));
        }
    }

    private void tickGolem(ActiveGolem golem) {
        SoulGolemData data = golem.data();
        Entity entity = this.context.resolveEntity(data);
        if (!(entity instanceof CopperGolem copper)) {
            this.context.spawnService().ensureAlive(data);
            return;
        }

        GolemSpawnService.applySoulEntityFlags(copper, golem.data().type());

        MinerState state = golem.state();
        if (golem.data().paused() || isStandingWork(state)) {
            this.context.movement().stop(copper);
        }

        if (golem.data().paused()) {
            GolemDisplay.refresh(
                    golem,
                    copper,
                    this.context.messages(),
                    this.context.keys(),
                    this.context.settings().visuals.textDisplays
            );
            return;
        }

        long now = System.currentTimeMillis();
        long effectiveInterval = this.context.effectiveWorkIntervalMs(data);
        boolean setupBusy = GolemSetupWork.isSetupState(golem);
        if (!setupBusy
                && state != MinerState.MINING
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
                && state != MinerState.MOVING_TO_SHELTER
                && state != MinerState.BUILDING_SHELTER
                && state != MinerState.SHELTERING
                && state != MinerState.MOVING_TO_COMBAT
                && state != MinerState.COMBATING
                && now - data.lastActionAt() < effectiveInterval) {
            GolemDisplay.refresh(
                    golem,
                    copper,
                    this.context.messages(),
                    this.context.keys(),
                    this.context.settings().visuals.textDisplays
            );
            return;
        }

        if (GolemSetupWork.isClearingSetup(golem)
                || state == MinerState.MOVING_TO_CLEAR
                || state == MinerState.CLEARING
                || state == MinerState.MOVING_TO_FENCE_CLEAR
                || state == MinerState.CLEARING_FENCE) {
            this.context.equipShovel(copper);
        } else if (this.context.combat().isCombatState(golem)) {
            this.context.combat().equipIfArmed(golem, copper);
        } else {
            this.context.pickaxeWork().loadFromData(golem);
            this.context.pickaxeWork().equip(copper, golem);
        }

        data.position(copper.getLocation().getX(), copper.getLocation().getY(), copper.getLocation().getZ());
        data.rotation(copper.getLocation().getYaw(), copper.getLocation().getPitch());

        if (setupBusy) {
            this.setup.tick(golem, copper);
        } else {
            boolean closingGate = state == MinerState.MOVING_TO_CLOSE_GATE
                    || state == MinerState.CLOSING_GATE;
            Settings.Miner miner = this.context.settings().miner;
            if (miner.placeFence && miner.gateAutoClose) {
                if (closingGate) {
                    this.support.continueCloseGate(golem, copper);
                    GolemDisplay.refresh(
                            golem,
                            copper,
                            this.context.messages(),
                            this.context.keys(),
                            this.context.settings().visuals.textDisplays
                    );
                    return;
                }
                if (this.context.gateWatch().shouldStartClose(golem, true, miner.gateCloseDelayMs)) {
                    golem.clearFetchFlags();
                    golem.state(MinerState.MOVING_TO_CLOSE_GATE);
                    this.support.continueCloseGate(golem, copper);
                    GolemDisplay.refresh(
                            golem,
                            copper,
                            this.context.messages(),
                            this.context.keys(),
                            this.context.settings().visuals.textDisplays
                    );
                    return;
                }
            }
            if (miner.rainShelter && this.context.rainShelter().shouldSeekShelter(golem, copper, true)) {
                if (state != MinerState.MOVING_TO_SHELTER
                        && state != MinerState.BUILDING_SHELTER
                        && state != MinerState.SHELTERING) {
                    golem.clearFetchFlags();
                    golem.state(MinerState.MOVING_TO_SHELTER);
                }
                this.support.continueShelter(golem, copper);
                GolemDisplay.refresh(
                        golem,
                        copper,
                        this.context.messages(),
                        this.context.keys(),
                        this.context.settings().visuals.textDisplays
                );
                return;
            }
            if (this.context.combat().isCombatState(golem)) {
                this.context.combat().continueCombat(golem, copper);
                GolemDisplay.refresh(
                        golem,
                        copper,
                        this.context.messages(),
                        this.context.keys(),
                        this.context.settings().visuals.textDisplays
                );
                return;
            }
            if (this.context.combat().tryStart(golem, copper)) {
                if (this.context.combat().isCombatState(golem)) {
                    this.context.combat().continueCombat(golem, copper);
                } else if (golem.fetchingWeapon()) {
                    this.chest.continueDeposit(golem, copper);
                }
                GolemDisplay.refresh(
                        golem,
                        copper,
                        this.context.messages(),
                        this.context.keys(),
                        this.context.settings().visuals.textDisplays
                );
                return;
            }
            switch (state) {
                case IDLE, SEEKING -> this.mine.beginSeek(golem, copper);
                case MOVING_TO_ORE -> this.mine.continueMoveToOre(golem, copper);
                case MINING -> this.mine.continueMine(golem, copper);
                case MOVING_TO_CHEST -> this.chest.continueDeposit(golem, copper);
                case WAITING_CHEST -> this.chest.retryChest(golem);
                case RESTING -> this.chest.continueRest(golem);
                case MOVING_TO_CLEAR, CLEARING -> this.support.continueClear(golem, copper);
                case MOVING_TO_TORCH, PLACING_TORCH -> this.support.continueTorch(golem, copper);
                case MOVING_TO_SEAT, PLACING_SEAT, SITTING -> this.support.continueSeat(golem, copper);
                case MOVING_TO_FENCE_CLEAR, CLEARING_FENCE,
                     MOVING_TO_FENCE, PLACING_FENCE,
                     MOVING_TO_GATE, PLACING_GATE -> this.support.continueFence(golem, copper);
                case MOVING_TO_CLOSE_GATE, CLOSING_GATE -> this.support.continueCloseGate(golem, copper);
                case MOVING_TO_SHELTER, BUILDING_SHELTER, SHELTERING -> this.support.continueShelter(golem, copper);
                case MOVING_TO_COMBAT, COMBATING -> this.context.combat().continueCombat(golem, copper);
                default -> golem.state(MinerState.IDLE);
            }
        }
        GolemDisplay.refresh(
                golem,
                copper,
                this.context.messages(),
                this.context.keys(),
                this.context.settings().visuals.textDisplays
        );
    }

    private void flushDirty() {
        for (ActiveGolem golem : this.context.registry().all()) {
            if (golem.data().type() != GolemType.MINER || !golem.dirty()) {
                continue;
            }
            SoulGolemData data = golem.data();
            golem.clearDirty();
            this.context.repository().save(data);
        }
    }

    private static boolean isStandingWork(MinerState state) {
        return switch (state) {
            case IDLE, SEEKING, WAITING_CHEST, RESTING, SITTING, SHELTERING, MINING,
                 PLACING_TORCH, PLACING_SEAT, PLACING_FENCE, PLACING_GATE,
                 CLEARING, CLEARING_FENCE, CLOSING_GATE, BUILDING_SHELTER -> true;
            default -> false;
        };
    }

    private void flushDirtySync() {
        for (ActiveGolem golem : this.context.registry().all()) {
            if (golem.data().type() != GolemType.MINER || !golem.dirty()) {
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
