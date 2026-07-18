package bm.b0b0b0.SoulGolem.service.digger;

import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.DiggerState;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.service.FarmAreaService;
import bm.b0b0b0.SoulGolem.service.GolemCarried;
import bm.b0b0b0.SoulGolem.service.GolemControlService;
import bm.b0b0b0.SoulGolem.service.GolemMovement;
import bm.b0b0b0.SoulGolem.service.GolemSeatWork;
import bm.b0b0b0.SoulGolem.service.GolemSupportWork;
import java.util.Collection;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class DiggerSupportWork {

    private final DiggerContext ctx;
    private final GolemSupportWork shared;

    public DiggerSupportWork(DiggerContext ctx) {
        this.ctx = ctx;
        this.shared = new GolemSupportWork(ctx, new DiggerSupportBridge(ctx));
    }

    public void continueShelter(ActiveGolem golem, CopperGolem copper) {
        this.shared.continueShelter(golem, copper);
    }

    public void continueCloseGate(ActiveGolem golem, CopperGolem copper) {
        this.shared.continueCloseGate(golem, copper);
    }

    public boolean tryGoToSeat(ActiveGolem golem, int radius) {
        return this.shared.tryGoToSeat(golem, radius);
    }

    public boolean tryStartSeatJob(ActiveGolem golem, int radius) {
        return this.shared.tryStartSeatJob(golem, radius);
    }

    public boolean tryStartFenceJob(ActiveGolem golem) {
        return this.shared.tryStartFenceJob(golem);
    }

    public void continueClear(ActiveGolem golem, CopperGolem copper) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        var pit = this.ctx.pitData(golem);
        if (pit.hasDigProgress()) {
            int dug = pit.digStartY() - pit.digLayerY();
            if (dug < Math.max(1, this.ctx.digger().maxDepth)) {
                golem.targetCrop(null);
                golem.diggerState(DiggerState.IDLE);
                return;
            }
        }
        if (!golem.carried().isEmpty() && !this.ctx.chestService().hasSpace(golem.data())) {
            golem.diggerState(DiggerState.MOVING_TO_CHEST);
            return;
        }
        Location target = golem.targetCrop();
        Block junk = target == null ? null : target.getBlock();
        if (junk == null || junk.getType().isAir() || !isDiggerClearableWeed(junk, pit)) {
            List<Block> list = this.ctx.farmAreaService().diggerYardWeeds(golem.data(), radius);
            if (list.isEmpty()) {
                golem.targetCrop(null);
                if (!golem.carried().isEmpty()) {
                    golem.diggerState(DiggerState.MOVING_TO_CHEST);
                    return;
                }
                golem.diggerState(DiggerState.IDLE);
                return;
            }
            junk = GolemSupportWork.pickNearest(copper.getLocation(), list);
            golem.targetCrop(junk.getLocation());
            golem.data().lastActionAt(System.currentTimeMillis());
        }
        Location stand = this.ctx.farmAreaService().standForClear(junk);
        boolean unreachable = stand == null
                || !DiggerSafety.hasSolidSupport(stand)
                || !this.ctx.farmAreaService().insideWoolBorder(golem.data(), stand);
        boolean timedOut = System.currentTimeMillis() - golem.data().lastActionAt() > 4_000L;
        if (unreachable || timedOut) {
            if (junk != null && isDiggerClearableWeed(junk, pit)
                    && GolemMovement.horizontalDistanceSquared(copper.getLocation(), junk.getLocation()) <= 16.0D) {
                junk.setType(Material.AIR, false);
            }
            golem.targetCrop(null);
            golem.data().lastActionAt(System.currentTimeMillis());
            if (this.ctx.farmAreaService().diggerYardWeeds(golem.data(), radius).isEmpty()) {
                golem.diggerState(DiggerState.IDLE);
            }
            return;
        }
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 2.25D) {
            golem.diggerState(DiggerState.MOVING_TO_CLEAR);
            this.ctx.walkTowards(copper, stand, golem.data());
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.diggerState(DiggerState.CLEARING);
        Collection<ItemStack> drops = junk.getDrops(new ItemStack(this.ctx.resolveShovel()));
        Block above = junk.getRelative(org.bukkit.block.BlockFace.UP);
        Block below = junk.getRelative(org.bukkit.block.BlockFace.DOWN);
        junk.setType(Material.AIR, false);
        if (FarmAreaService.isYardJunk(above.getType()) || above.getType() == Material.SNOW) {
            above.setType(Material.AIR, false);
        }
        if (FarmAreaService.isYardJunk(below.getType()) || below.getType() == Material.SNOW) {
            below.setType(Material.AIR, false);
        }
        for (ItemStack drop : drops) {
            golem.carry(drop);
        }
        golem.targetCrop(null);
        golem.data().lastActionAt(System.currentTimeMillis());
        golem.markDirty();
        List<Block> left = this.ctx.farmAreaService().diggerYardWeeds(golem.data(), radius);
        if (!left.isEmpty() && (golem.carried().isEmpty() || this.ctx.chestService().hasSpace(golem.data()))) {
            golem.targetCrop(GolemSupportWork.pickNearest(copper.getLocation(), left).getLocation());
            golem.diggerState(DiggerState.MOVING_TO_CLEAR);
            return;
        }
        if (!golem.carried().isEmpty()) {
            golem.diggerState(DiggerState.MOVING_TO_CHEST);
            return;
        }
        golem.diggerState(DiggerState.IDLE);
    }

    private static boolean isDiggerClearableWeed(Block junk, SoulGolemData pit) {
        if (junk == null || pit == null) {
            return false;
        }
        int homeY = (int) Math.floor(pit.homeY());
        if (junk.getY() < homeY + 1 || junk.getY() > homeY + 2) {
            return false;
        }
        Material type = junk.getType();
        return FarmAreaService.isYardJunk(type) || type == Material.SNOW;
    }

    public void continueTorch(ActiveGolem golem, CopperGolem copper) {
        this.shared.continueTorch(golem, copper);
    }

    public void continueSeat(ActiveGolem golem, CopperGolem copper) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());

        if (golem.diggerState() == DiggerState.SITTING
                && this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            golem.resumeSeatRest(false);
            GolemSettings.Yard yard = this.ctx.settings().yard;
            if (yard.collectGroundLoot && this.ctx.groundLoot().hasLoot(golem.data())) {
                golem.restTicksLeft(0L);
                this.ctx.seatWork().leaveBench(
                        golem,
                        copper,
                        this.ctx.plugin(),
                        this.ctx.registry(),
                        this.ctx.keys()
                );
                golem.diggerState(DiggerState.IDLE);
                return;
            }
            this.ctx.seatWork().holdOnBench(golem, copper);
            if (golem.pauseAfterRest()) {
                GolemControlService.finishPauseAfterRest(golem, this.ctx.repository());
                return;
            }
            long left = golem.restTicksLeft() - Math.max(1L, this.ctx.settings().coordinatorPeriodTicks);
            golem.restTicksLeft(left);
            if (left > 0L) {
                return;
            }
            golem.restTicksLeft(0L);
            golem.data().lastActionAt(System.currentTimeMillis());
            this.ctx.seatWork().leaveBench(
                    golem,
                    copper,
                    this.ctx.plugin(),
                    this.ctx.registry(),
                    this.ctx.keys()
            );
            golem.diggerState(DiggerState.IDLE);
            return;
        }

        Material stairs = GolemCarried.carriedStairs(golem);
        if (stairs != null && !this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            GolemSeatWork.Phase phase = this.ctx.seatWork().placeCarriedSeat(golem, copper, stairs, radius);
            this.shared.applySeatPhase(golem, phase);
            if (phase == GolemSeatWork.Phase.PLACING) {
                golem.restTicksLeft(Math.max(1L, Math.round(this.ctx.settings().miner.seatRestTicks
                        * this.ctx.settings().moodRestMultiplierAt(this.ctx.moodScore(golem.data())))));
            }
            return;
        }

        if (!this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            if (tryStartSeatJob(golem, radius)) {
                return;
            }
            golem.diggerState(DiggerState.IDLE);
            return;
        }

        GolemSeatWork.Phase phase = this.ctx.seatWork().sitOnBench(golem, copper);
        if (phase == GolemSeatWork.Phase.UNAVAILABLE) {
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        if (phase == GolemSeatWork.Phase.MOVING) {
            golem.diggerState(DiggerState.MOVING_TO_SEAT);
            return;
        }

        if (golem.restTicksLeft() <= 0L) {
            int mood = this.ctx.moodScore(golem.data());
            golem.restTicksLeft(Math.max(1L, Math.round(this.ctx.settings().miner.seatRestTicks
                    * this.ctx.settings().moodRestMultiplierAt(mood))));
        }
        golem.resumeSeatRest(false);
        golem.diggerState(DiggerState.SITTING);
        if (golem.pauseAfterRest()) {
            GolemControlService.finishPauseAfterRest(golem, this.ctx.repository());
            return;
        }
        long left = golem.restTicksLeft() - Math.max(1L, this.ctx.settings().coordinatorPeriodTicks);
        golem.restTicksLeft(left);
        if (left > 0L) {
            return;
        }
        golem.restTicksLeft(0L);
        golem.data().lastActionAt(System.currentTimeMillis());
        this.ctx.seatWork().leaveBench(
                golem,
                copper,
                this.ctx.plugin(),
                this.ctx.registry(),
                this.ctx.keys()
        );
        golem.diggerState(DiggerState.IDLE);
    }

    public void continueFence(ActiveGolem golem, CopperGolem copper) {
        this.shared.continueFence(golem, copper, null);
    }

    public void takeFenceFromChest(ActiveGolem golem) {
        this.shared.takeFenceFromChest(golem);
    }
}
