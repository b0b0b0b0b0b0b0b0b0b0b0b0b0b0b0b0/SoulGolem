package bm.b0b0b0.SoulGolem.service.digger;

import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.DiggerState;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.service.GolemMovement;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;

public final class DiggerEscapeWork {

    private static final double ON_STEP_H_SQ = 1.21D;
    private static final double ON_STEP_Y = 0.55D;
    private static final long CLIMB_HOP_MS = 2_000L;

    private final DiggerContext ctx;

    public DiggerEscapeWork(DiggerContext ctx) {
        this.ctx = ctx;
    }

    public boolean wantsSurface(ActiveGolem golem) {
        DiggerState state = golem.diggerState();
        SoulGolemData pit = this.ctx.pitData(golem);
        boolean crewReturn = DiggerDigWork.isCrewReturning(golem, pit, this.ctx.digger());
        return switch (state) {
            case MOVING_TO_CHEST, WAITING_CHEST, ESCAPING, DONE, RESTING -> true;
            case IDLE -> crewReturn
                    || golem.fetchingFeed() || golem.fetchingTorch()
                    || golem.fetchingFence() || golem.fetchingGate() || golem.fetchingSeat()
                    || golem.fetchingWeapon()
                    || DiggerDigWork.carriedCount(golem) >= this.ctx.digger().blocksPerTrip
                    || (!golem.carried().isEmpty() && !this.ctx.chestService().hasSpace(golem.data()));
            default -> false;
        };
    }

    public boolean needsEscape(ActiveGolem golem, CopperGolem copper) {
        if (golem.diggerState() == DiggerState.ESCAPING || !wantsSurface(golem)) {
            return false;
        }
        SoulGolemData pit = this.ctx.pitData(golem);
        boolean crewReturn = DiggerDigWork.isCrewReturning(golem, pit, this.ctx.digger());
        boolean leaderAscent = DiggerDigWork.needsLeaderAscent(
                golem,
                pit,
                this.ctx.digger(),
                copper.getLocation()
        );
        if (this.ctx.chestLink().isLinked(pit) && !crewReturn && !leaderAscent) {
            return false;
        }
        if (copper.getLocation().getY() >= pit.homeY() - 1.4D) {
            return false;
        }
        DiggerState state = golem.diggerState();
        if (!crewReturn
                && (state == DiggerState.DIGGING || state == DiggerState.MOVING_TO_DIG || state == DiggerState.PLACING_STAIR)) {
            return false;
        }
        return true;
    }

    public void startEscape(ActiveGolem golem) {
        golem.clearPathWaypoint();
        golem.wanderTarget(null);
        golem.targetOre(null);
        golem.fenceStuckTicks(0L);
        golem.diggerState(DiggerState.ESCAPING);
        golem.data().lastActionAt(System.currentTimeMillis());
    }

    public void continueEscape(ActiveGolem golem, CopperGolem copper) {
        SoulGolemData pit = this.ctx.pitData(golem);
        World world = Bukkit.getWorld(pit.worldName());
        if (world == null) {
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        GolemSettings.Digger digger = this.ctx.digger();
        int topY = (int) Math.floor(pit.homeY());
        Location from = copper.getLocation();
        double feetY = from.getY();

        synchronized (("digger-stair-" + pit.id()).intern()) {
            DiggerDigWork.ensureDigProgress(pit, this.ctx.digger(), this.ctx.farmAreaService());
            DiggerPit.ensureSpiralStairs(
                    world,
                    pit,
                    digger,
                    Math.min(from.getBlockY(), pit.hasDigProgress() ? pit.digLayerY() : topY)
            );
            this.ctx.markPitDirty(golem);
        }

        if (feetY >= topY - 0.2D) {
            finishEscape(golem);
            return;
        }

        Location step = nextClimbStep(world, pit, digger, from, topY);
        if (step == null) {
            Location chest = this.ctx.chestService().chestStandLocation(pit);
            if (chest != null) {
                this.ctx.movement().walkClimbStair(copper, chest, golem);
            }
            if (System.currentTimeMillis() - golem.data().lastActionAt() > 5_000L) {
                finishEscape(golem);
            }
            return;
        }

        double h2 = GolemMovement.horizontalDistanceSquared(from, step);
        double yDiff = Math.abs(feetY - step.getY());
        if (h2 <= ON_STEP_H_SQ && yDiff <= ON_STEP_Y) {
            golem.fenceStuckTicks(0L);
            Location above = nextClimbStep(world, pit, digger, step.clone().add(0.0D, 0.15D, 0.0D), topY);
            if (above != null && above.getY() > step.getY() + 0.08D) {
                this.ctx.movement().walkClimbStair(copper, above, golem);
            } else if (feetY >= topY - 0.2D) {
                finishEscape(golem);
            }
            golem.data().lastActionAt(System.currentTimeMillis());
            return;
        }

        long tickMs = Math.max(50L, this.ctx.settings().coordinatorPeriodTicks * 50L);
        long stuck = golem.fenceStuckTicks() + tickMs;
        golem.fenceStuckTicks(stuck);
        if (stuck >= CLIMB_HOP_MS && h2 <= 6.25D) {
            copper.teleport(step);
            golem.fenceStuckTicks(0L);
            golem.data().lastActionAt(System.currentTimeMillis());
            return;
        }

        this.ctx.movement().walkClimbStair(copper, step, golem);
        golem.data().lastActionAt(System.currentTimeMillis());
    }

    private void finishEscape(ActiveGolem golem) {
        golem.fenceStuckTicks(0L);
        SoulGolemData pit = this.ctx.pitData(golem);
        if (DiggerDigWork.isCrewReturning(golem, pit, this.ctx.digger())) {
            golem.diggerState(DiggerState.MOVING_TO_CHEST);
            golem.data().lastActionAt(System.currentTimeMillis());
            return;
        }
        if (!golem.data().isCrewHelper() && DiggerDigWork.isPitComplete(pit, this.ctx.digger())) {
            golem.diggerState(DiggerState.DONE);
            golem.data().lastActionAt(System.currentTimeMillis());
            return;
        }
        golem.diggerState(needsChestAfterEscape(golem) ? DiggerState.MOVING_TO_CHEST : DiggerState.IDLE);
        golem.data().lastActionAt(System.currentTimeMillis());
    }

    private static boolean needsChestAfterEscape(ActiveGolem golem) {
        return !golem.carried().isEmpty()
                || golem.fetchingFeed()
                || golem.fetchingTorch()
                || golem.fetchingFence()
                || golem.fetchingGate()
                || golem.fetchingSeat()
                || golem.fetchingWeapon();
    }

    private Location nextClimbStep(
            World world,
            SoulGolemData pit,
            GolemSettings.Digger digger,
            Location from,
            int topY
    ) {
        if (!pit.hasDigProgress()) {
            return this.ctx.chestService().chestStandLocation(pit);
        }
        int radius = DiggerPit.radius(digger);
        double feetY = from.getY();
        int minFloor = Math.max(pit.digLayerY(), (int) Math.floor(feetY) - 2);
        Location walkTarget = null;
        for (int floor = minFloor; floor <= topY; floor++) {
            int stairIndex = DiggerPit.stairIndexForY(pit, radius, floor);
            Location stand = stairStandAt(world, pit, digger, floor, stairIndex);
            if (stand == null || stand.getY() < feetY - 0.75D) {
                continue;
            }
            double h2 = GolemMovement.horizontalDistanceSquared(from, stand);
            double yDiff = Math.abs(feetY - stand.getY());
            boolean onStep = h2 <= ON_STEP_H_SQ && yDiff <= ON_STEP_Y;
            if (onStep && floor < topY) {
                int nextFloor = floor + 1;
                int nextIndex = DiggerPit.stairIndexForY(pit, radius, nextFloor);
                Location above = stairStandAt(world, pit, digger, nextFloor, nextIndex);
                if (above != null && above.getY() > stand.getY() + 0.05D) {
                    return above;
                }
            }
            if (!onStep) {
                walkTarget = stand;
                break;
            }
            if (floor == topY) {
                return stand;
            }
        }
        if (walkTarget != null) {
            return walkTarget;
        }
        return this.ctx.chestService().chestStandLocation(pit);
    }

    private Location stairStandAt(
            World world,
            SoulGolemData pit,
            GolemSettings.Digger digger,
            int floorY,
            int stairIndex
    ) {
        ensureStairAt(world, pit, digger, floorY, stairIndex);
        int radius = DiggerPit.radius(digger);
        int[] walk = DiggerPit.stairWalkCell(pit, radius, stairIndex);
        Block stair = world.getBlockAt(walk[0], floorY, walk[1]);
        int[] landingCell = DiggerPit.stairCell(pit, radius, stairIndex);
        Block landing = world.getBlockAt(landingCell[0], floorY, landingCell[1]);
        if (!DiggerPit.isStairBlock(stair)
                && !landing.getType().isSolid()
                && !DiggerSafety.hasSolidSupport(
                new Location(world, walk[0] + 0.5D, floorY + 1.0D, walk[1] + 0.5D))) {
            return null;
        }
        return DiggerPit.stairStandInward(world, pit, walk, floorY);
    }

    private static void ensureStairAt(
            World world,
            SoulGolemData pit,
            GolemSettings.Digger digger,
            int floorY,
            int stairIndex
    ) {
        int radius = DiggerPit.radius(digger);
        int[] walk = DiggerPit.stairWalkCell(pit, radius, stairIndex);
        Block stair = world.getBlockAt(walk[0], floorY, walk[1]);
        if (!DiggerPit.isStairBlock(stair)) {
            DiggerPit.placeStair(world, pit, digger, floorY, stairIndex);
        }
    }

    static boolean hasUsableStairNearby(CopperGolem copper, SoulGolemData pit) {
        World world = copper.getWorld();
        int x = copper.getLocation().getBlockX();
        int y = copper.getLocation().getBlockY();
        int z = copper.getLocation().getBlockZ();
        int homeY = (int) Math.floor(pit.homeY());
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= Math.min(4, homeY - y + 1); dy++) {
                    Block block = world.getBlockAt(x + dx, y + dy, z + dz);
                    if (Tag.STAIRS.isTagged(block.getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
