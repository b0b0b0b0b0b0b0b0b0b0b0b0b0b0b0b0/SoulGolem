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

    private final DiggerContext ctx;

    public DiggerEscapeWork(DiggerContext ctx) {
        this.ctx = ctx;
    }

    public boolean wantsSurface(ActiveGolem golem) {
        DiggerState state = golem.diggerState();
        SoulGolemData pit = this.ctx.pitData(golem);
        boolean crewReturn = DiggerDigWork.isCrewReturning(golem, pit, this.ctx.digger().maxDepth);
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
        boolean crewReturn = DiggerDigWork.isCrewReturning(golem, pit, this.ctx.digger().maxDepth);
        if (this.ctx.chestLink().isLinked(pit) && !crewReturn) {
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
        double feetY = copper.getLocation().getY();
        int bottomY = Math.min(copper.getLocation().getBlockY(), pit.hasDigProgress() ? pit.digLayerY() : topY);

        synchronized (("digger-stair-" + pit.id()).intern()) {
            DiggerDigWork.ensureDigProgress(pit);
            DiggerPit.ensureSpiralStairs(world, pit, digger, bottomY);
            this.ctx.markPitDirty(golem);
        }

        if (feetY >= topY - 0.2D) {
            finishEscape(golem);
            return;
        }

        Location step = nearestClimbStep(world, pit, digger, copper.getLocation(), topY);
        if (step == null) {
            Location chest = this.ctx.chestService().chestStandLocation(pit);
            if (chest != null) {
                this.ctx.walkTowards(copper, chest, golem);
            }
            if (System.currentTimeMillis() - golem.data().lastActionAt() > 8000L) {
                finishEscape(golem);
            }
            return;
        }

        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), step) <= 1.0D
                && Math.abs(copper.getLocation().getY() - step.getY()) <= 1.25D) {
            golem.data().lastActionAt(System.currentTimeMillis());
            Location next = nearestClimbStep(world, pit, digger, step.clone().add(0, 0.1D, 0), topY);
            if (next != null && next.getY() > copper.getLocation().getY() + 0.2D) {
                this.ctx.walkTowards(copper, next, golem);
            } else if (copper.getLocation().getY() >= topY - 0.2D) {
                finishEscape(golem);
            } else {
                this.ctx.walkTowards(copper, step, golem);
            }
            return;
        }

        this.ctx.walkTowards(copper, step, golem);
        if (System.currentTimeMillis() - golem.data().lastActionAt() > 6000L) {
            golem.data().lastActionAt(System.currentTimeMillis());
            Location alt = nearestClimbStep(world, pit, digger, copper.getLocation().add(0.01D, 0, 0.01D), topY);
            if (alt != null) {
                this.ctx.walkTowards(copper, alt, golem);
            }
        }
    }

    private void finishEscape(ActiveGolem golem) {
        SoulGolemData pit = this.ctx.pitData(golem);
        if (DiggerDigWork.isCrewReturning(golem, pit, this.ctx.digger().maxDepth)) {
            golem.diggerState(DiggerState.MOVING_TO_CHEST);
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

    private Location nearestClimbStep(
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
        int startY = pit.digStartY();
        double fromY = from.getY();
        Location bestNear = null;
        double bestNearDist = Double.MAX_VALUE;
        Location bestUp = null;
        double bestUpDist = Double.MAX_VALUE;

        for (int y = pit.digLayerY(); y <= topY; y++) {
            int stairIndex = DiggerPit.stairIndexForY(pit, radius, y);
            int[] cell = DiggerPit.stairCell(pit, radius, stairIndex);
            int[] walk = DiggerPit.stairWalkCell(pit, radius, stairIndex);
            Block stair = world.getBlockAt(walk[0], y, walk[1]);
            if (!DiggerPit.isStairBlock(stair) && y <= startY) {
                DiggerPit.placeStair(world, pit, digger, y, stairIndex);
                stair = world.getBlockAt(walk[0], y, walk[1]);
            }
            Block landing = world.getBlockAt(cell[0], y, cell[1]);
            if (!DiggerPit.isStairBlock(stair)
                    && !landing.getType().isSolid()
                    && !DiggerSafety.hasSolidSupport(
                    new Location(world, walk[0] + 0.5D, y + 1.0D, walk[1] + 0.5D))) {
                continue;
            }
            Location stand = DiggerPit.stairStandInward(world, pit, walk, y);
            double dy = stand.getY() - fromY;
            double dist2 = GolemMovement.horizontalDistanceSquared(from, stand);
            if (dy < -0.6D) {
                continue;
            }
            if (dy <= 1.35D) {
                if (dist2 < bestNearDist) {
                    bestNearDist = dist2;
                    bestNear = stand;
                }
            } else if (dist2 < bestUpDist) {
                bestUpDist = dist2;
                bestUp = stand;
            }
        }
        if (bestNear != null) {
            return bestNear;
        }
        if (bestUp != null) {
            return bestUp;
        }
        return this.ctx.chestService().chestStandLocation(pit);
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
