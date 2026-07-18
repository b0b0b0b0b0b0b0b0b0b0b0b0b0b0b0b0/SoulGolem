package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.DiggerState;
import bm.b0b0b0.SoulGolem.model.GolemType;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.repository.GolemRepository;
import bm.b0b0b0.SoulGolem.scheduler.PluginSchedulers;
import bm.b0b0b0.SoulGolem.service.digger.DiggerChestWork;
import bm.b0b0b0.SoulGolem.service.digger.DiggerContext;
import bm.b0b0b0.SoulGolem.service.digger.DiggerCrewWork;
import bm.b0b0b0.SoulGolem.service.digger.DiggerDigWork;
import bm.b0b0b0.SoulGolem.service.digger.DiggerEscapeWork;
import bm.b0b0b0.SoulGolem.service.digger.DiggerPit;
import bm.b0b0b0.SoulGolem.service.digger.DiggerSafety;
import bm.b0b0b0.SoulGolem.service.digger.DiggerSupportWork;
import bm.b0b0b0.SoulGolem.service.setup.GolemSetupWork;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class DiggerTickService {

    private final DiggerContext context;
    private final DiggerDigWork dig;
    private final DiggerSupportWork support;
    private final DiggerChestWork chest;
    private final DiggerEscapeWork escape;
    private final DiggerCrewWork crew;
    private final GolemSetupWork setup;
    private final AtomicInteger cursor = new AtomicInteger();
    private final AtomicInteger fluidSealTick = new AtomicInteger();
    private ScheduledTask coordinatorTask;
    private ScheduledTask saveTask;

    public DiggerTickService(
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
        this.context = new DiggerContext(
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
        this.dig = new DiggerDigWork(this.context);
        this.support = new DiggerSupportWork(this.context);
        this.chest = new DiggerChestWork(this.context);
        this.escape = new DiggerEscapeWork(this.context);
        this.crew = new DiggerCrewWork(this.context);
        this.setup = new GolemSetupWork(
                chestService,
                workAreaService,
                farmAreaService,
                null,
                this.context.movement()
        );
        this.dig.wire(this.support, this.crew);
        this.chest.wire(this.support, this.crew);
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
            if (golem.data().type() == GolemType.DIGGER) {
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
        List<ActiveGolem> diggers = new ArrayList<>();
        for (ActiveGolem golem : all) {
            if (golem.data().type() == GolemType.DIGGER) {
                diggers.add(golem);
            }
        }
        if (diggers.isEmpty()) {
            return;
        }
        int configured = Math.max(1, this.context.settings().golemsPerCoordinatorTick);
        int batch = Math.min(diggers.size(), Math.max(configured, diggers.size()));
        int size = diggers.size();
        int start = Math.floorMod(this.cursor.getAndAdd(batch), size);
        for (int i = 0; i < batch; i++) {
            ActiveGolem golem = diggers.get((start + i) % size);
            SoulGolemData data = golem.data();
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

        this.context.spawnService().refreshSoulEntity(copper, golem.data().type());

        DiggerState state = golem.diggerState();
        GolemAiMode.sync(
                this.context.plugin(),
                copper,
                golem,
                this.context.registry(),
                this.context.keys(),
                this.context.movement()
        );
        GolemGaze.dropPlayerGazeUnlessSitting(golem, state == DiggerState.SITTING);
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

        if (!golem.data().isCrewHelper()) {
            this.crew.tryHireFromChest(golem, copper);
        }

        SoulGolemData pit = this.context.pitData(golem);
        Material seal = DiggerPit.hazardSealMaterial(this.context.digger());
        Location feet = copper.getLocation();
        boolean fluidNearby = DiggerPit.hasFluidNear(feet, 3)
                || DiggerPit.isFluidHazard(feet.getBlock())
                || DiggerPit.isFluidHazard(feet.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN));
        if (fluidNearby) {
            DiggerPit.sealAround(feet, 3, 2, Math.max(2, this.context.digger().caveSafeDepth), seal);
            DiggerPit.sealPitFluids(pit, this.context.digger(), this.context.farmAreaService());
        } else if (!golem.data().isCrewHelper()
                && pit.hasDigProgress()
                && this.fluidSealTick.incrementAndGet() % 6 == 0) {
            DiggerPit.sealPitFluids(pit, this.context.digger(), this.context.farmAreaService());
        }

        boolean setupBusy = GolemSetupWork.isSetupState(golem);
        boolean fenceDuty = state == DiggerState.MOVING_TO_FENCE_CLEAR
                || state == DiggerState.CLEARING_FENCE
                || state == DiggerState.MOVING_TO_FENCE
                || state == DiggerState.PLACING_FENCE
                || state == DiggerState.MOVING_TO_GATE
                || state == DiggerState.PLACING_GATE
                || state == DiggerState.MOVING_TO_CLOSE_GATE
                || state == DiggerState.CLOSING_GATE;
        SoulGolemData pitData = this.context.pitData(golem);
        boolean linkedChest = this.context.chestLink().isLinked(pitData);
        boolean crewReturn = DiggerDigWork.isCrewReturning(golem, pitData, this.context.digger().maxDepth);
        if (linkedChest && !crewReturn && (state == DiggerState.ESCAPING || state == DiggerState.MOVING_TO_CHEST)) {
            golem.clearFetchFlags();
            golem.diggerState(DiggerState.IDLE);
            state = DiggerState.IDLE;
        }
        if (!setupBusy && !fenceDuty && state != DiggerState.ESCAPING) {
            if ((!linkedChest || crewReturn) && this.escape.needsEscape(golem, copper)) {
                this.escape.startEscape(golem);
                state = DiggerState.ESCAPING;
            } else if (DiggerSafety.needsRescue(
                    copper.getLocation(),
                    data,
                    this.context.farmAreaService(),
                    this.context.digger()
            )) {
                Location here = copper.getLocation();
                boolean inFluid = here.getBlock().isLiquid()
                        || here.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN).isLiquid()
                        || DiggerPit.isFluidHazard(here.getBlock())
                        || DiggerPit.isFluidHazard(here.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN));
                Location safe = DiggerSafety.rescueStand(
                        data,
                        this.context.farmAreaService(),
                        this.context.chestService(),
                        this.context.digger()
                );
                if (inFluid && safe != null) {
                    golem.wanderTarget(null);
                    golem.clearPathWaypoint();
                    golem.targetOre(null);
                    copper.teleport(safe);
                    DiggerPit.sealHazardsNear(
                            safe.getBlock(),
                            this.context.digger().caveSafeDepth,
                            DiggerPit.hazardSealMaterial(this.context.digger())
                    );
                    golem.diggerState(DiggerState.IDLE);
                    state = DiggerState.IDLE;
                } else if ((!linkedChest || crewReturn)
                        && this.escape.wantsSurface(golem)
                        && here.getY() < data.homeY() - 1.4D) {
                    this.escape.startEscape(golem);
                    state = DiggerState.ESCAPING;
                } else if (safe != null) {
                    golem.wanderTarget(null);
                    golem.clearPathWaypoint();
                    this.context.walkTowards(copper, safe, golem);
                }
            }
        }

        long now = System.currentTimeMillis();
        long diggerInterval = Math.max(1L, this.context.digger().workIntervalTicks) * 50L;
        long effectiveInterval = Math.max(50L, (long) (diggerInterval * this.context.stickBoostFactor(golem)));
        if (!setupBusy
                && state != DiggerState.DIGGING
                && state != DiggerState.MOVING_TO_DIG
                && state != DiggerState.MOVING_TO_CHEST
                && state != DiggerState.RESTING
                && state != DiggerState.PLACING_STAIR
                && state != DiggerState.ESCAPING
                && state != DiggerState.MOVING_TO_CLEAR
                && state != DiggerState.CLEARING
                && state != DiggerState.MOVING_TO_TORCH
                && state != DiggerState.PLACING_TORCH
                && state != DiggerState.MOVING_TO_SEAT
                && state != DiggerState.PLACING_SEAT
                && state != DiggerState.SITTING
                && state != DiggerState.MOVING_TO_SHELTER
                && state != DiggerState.BUILDING_SHELTER
                && state != DiggerState.SHELTERING
                && state != DiggerState.MOVING_TO_COMBAT
                && state != DiggerState.COMBATING
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
                || state == DiggerState.MOVING_TO_CLEAR
                || state == DiggerState.CLEARING
                || state == DiggerState.MOVING_TO_FENCE_CLEAR
                || state == DiggerState.CLEARING_FENCE) {
            this.context.equipShovel(copper);
        } else if (this.context.combat().isCombatState(golem)) {
            this.context.combat().equipIfArmed(golem, copper);
        } else if (golem.targetOre() != null) {
            this.context.equipForBlock(copper, golem.targetOre().getBlock().getType());
        } else {
            GolemSpawnService.equipTool(copper, data.type(), this.context.settings());
        }

        data.position(copper.getLocation().getX(), copper.getLocation().getY(), copper.getLocation().getZ());
        data.rotation(copper.getLocation().getYaw(), copper.getLocation().getPitch());

        if (setupBusy) {
            this.setup.tick(golem, copper);
        } else {
            boolean closingGate = state == DiggerState.MOVING_TO_CLOSE_GATE
                    || state == DiggerState.CLOSING_GATE;
            GolemSettings.Yard yard = this.context.settings().yard;
            boolean inShelter = state == DiggerState.MOVING_TO_SHELTER
                    || state == DiggerState.BUILDING_SHELTER
                    || state == DiggerState.SHELTERING;
            if (inShelter) {
                if (!yard.rainShelter
                        || !this.context.rainShelter().shouldContinueShelter(golem, copper)) {
                    this.support.continueShelter(golem, copper);
                    refresh(golem, copper);
                    return;
                }
            }
            if (yard.placeFence && yard.gateAutoClose) {
                if (closingGate) {
                    this.support.continueCloseGate(golem, copper);
                    refresh(golem, copper);
                    return;
                }
                if (this.context.gateWatch().shouldStartClose(golem, true, yard.gateCloseDelayMs)) {
                    if (state == DiggerState.SITTING
                            || state == DiggerState.MOVING_TO_SEAT
                            || state == DiggerState.PLACING_SEAT) {
                        if (golem.restTicksLeft() > 0L) {
                            golem.resumeSeatRest(true);
                        }
                        this.context.seatWork().leaveBench(
                                golem,
                                copper,
                                this.context.plugin(),
                                this.context.registry(),
                                this.context.keys()
                        );
                    } else if (state == DiggerState.SHELTERING || state == DiggerState.RESTING) {
                        GolemAiMode.enable(
                                this.context.plugin(),
                                copper,
                                this.context.registry(),
                                this.context.keys()
                        );
                    }
                    golem.clearFetchFlags();
                    golem.diggerState(DiggerState.MOVING_TO_CLOSE_GATE);
                    this.support.continueCloseGate(golem, copper);
                    refresh(golem, copper);
                    return;
                }
            }
            if (yard.rainShelter && this.context.rainShelter().shouldSeekShelter(golem, copper, true)) {
                if (state != DiggerState.MOVING_TO_SHELTER
                        && state != DiggerState.BUILDING_SHELTER
                        && state != DiggerState.SHELTERING) {
                    golem.clearFetchFlags();
                    golem.diggerState(DiggerState.MOVING_TO_SHELTER);
                }
                this.support.continueShelter(golem, copper);
                refresh(golem, copper);
                return;
            }
            if (this.context.combat().isCombatState(golem)) {
                this.context.combat().continueCombat(golem, copper);
                refresh(golem, copper);
                return;
            }
            if (this.context.combat().tryStart(golem, copper)) {
                if (this.context.combat().isCombatState(golem)) {
                    this.context.combat().continueCombat(golem, copper);
                } else if (golem.fetchingWeapon()) {
                    this.chest.continueDeposit(golem, copper);
                }
                refresh(golem, copper);
                return;
            }
            switch (state) {
                case IDLE -> this.dig.beginSeek(golem, copper);
                case DONE -> this.dig.continueDone(golem, copper);
                case MOVING_TO_DIG -> this.dig.continueMoveToDig(golem, copper);
                case DIGGING -> this.dig.continueDig(golem, copper);
                case PLACING_STAIR -> this.dig.placeStairAndDescend(golem, copper);
                case ESCAPING -> this.escape.continueEscape(golem, copper);
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
                default -> golem.diggerState(DiggerState.IDLE);
            }
        }
        GolemGazeService.forceLook(copper, golem);
        refresh(golem, copper);
    }

    private void refresh(ActiveGolem golem, CopperGolem copper) {
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
            if (golem.data().type() != GolemType.DIGGER || !golem.dirty()) {
                continue;
            }
            SoulGolemData data = golem.data();
            golem.clearDirty();
            this.context.repository().save(data);
        }
    }

    private static boolean isStandingWork(DiggerState state) {
        return switch (state) {
            case IDLE, DONE, WAITING_CHEST, RESTING, SITTING, SHELTERING, DIGGING, PLACING_STAIR,
                 PLACING_TORCH, PLACING_SEAT, PLACING_FENCE, PLACING_GATE,
                 CLEARING, CLEARING_FENCE, CLOSING_GATE, BUILDING_SHELTER -> true;
            default -> false;
        };
    }

    private void flushDirtySync() {
        for (ActiveGolem golem : this.context.registry().all()) {
            if (golem.data().type() != GolemType.DIGGER || !golem.dirty()) {
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
