package bm.b0b0b0.SoulGolem.service.miner;

import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.MinerState;
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

public final class MinerSupportWork {

    private final MinerContext ctx;
    private final GolemSupportWork shared;

    public MinerSupportWork(MinerContext ctx) {
        this.ctx = ctx;
        this.shared = new GolemSupportWork(ctx, new MinerSupportBridge(ctx));
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

    public boolean tryStartFenceJob(ActiveGolem golem, int radius) {
        return this.shared.tryStartFenceJob(golem);
    }

    public void continueClear(ActiveGolem golem, CopperGolem copper) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        if (!golem.carried().isEmpty() && !this.ctx.chestService().hasSpace(golem.data())) {
            golem.state(MinerState.MOVING_TO_CHEST);
            return;
        }
        Location target = golem.targetCrop();
        Block junk = target == null ? null : target.getBlock();
        if (junk == null || junk.getType().isAir() || !GolemSupportWork.isClearableJunk(junk)) {
            List<Block> list = this.ctx.farmAreaService().minerJunkToClear(golem.data(), radius, this.ctx.oreTable());
            if (list.isEmpty()) {
                golem.targetCrop(null);
                if (!golem.carried().isEmpty()) {
                    golem.state(MinerState.MOVING_TO_CHEST);
                    return;
                }
                golem.state(MinerState.IDLE);
                return;
            }
            junk = GolemSupportWork.pickNearest(copper.getLocation(), list);
            golem.targetCrop(junk.getLocation());
        }
        Location stand = this.ctx.farmAreaService().standForClear(junk);
        if (stand == null) {
            golem.targetCrop(null);
            return;
        }
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 2.25D) {
            golem.state(MinerState.MOVING_TO_CLEAR);
            this.ctx.walkTowards(copper, stand, golem);
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
            Material shovel = this.ctx.resolveShovel();
            Collection<ItemStack> drops = junk.getDrops(new ItemStack(shovel));
            junk.setType(Material.AIR, false);
            for (ItemStack drop : drops) {
                golem.carry(drop);
            }
        }
        golem.targetCrop(null);
        golem.markDirty();
        List<Block> left = this.ctx.farmAreaService().minerJunkToClear(golem.data(), radius, this.ctx.oreTable());
        if (!left.isEmpty() && (golem.carried().isEmpty() || this.ctx.chestService().hasSpace(golem.data()))) {
            golem.targetCrop(GolemSupportWork.pickNearest(copper.getLocation(), left).getLocation());
            golem.state(MinerState.MOVING_TO_CLEAR);
            return;
        }
        if (!golem.carried().isEmpty()) {
            golem.state(MinerState.MOVING_TO_CHEST);
            return;
        }
        golem.state(MinerState.IDLE);
    }

    public void continueTorch(ActiveGolem golem, CopperGolem copper) {
        this.shared.continueTorch(golem, copper);
    }

    public void continueSeat(ActiveGolem golem, CopperGolem copper) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());

        if (golem.state() == MinerState.SITTING
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
                golem.state(MinerState.SEEKING);
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
            golem.restTicksLeft(0L);
            golem.state(MinerState.IDLE);
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
            golem.state(MinerState.IDLE);
            return;
        }

        GolemSeatWork.Phase phase = this.ctx.seatWork().sitOnBench(golem, copper);
        if (phase == GolemSeatWork.Phase.UNAVAILABLE) {
            golem.state(MinerState.IDLE);
            return;
        }
        if (phase == GolemSeatWork.Phase.MOVING) {
            golem.state(MinerState.MOVING_TO_SEAT);
            return;
        }

        if (golem.restTicksLeft() <= 0L) {
            int mood = this.ctx.moodScore(golem.data());
            golem.restTicksLeft(Math.max(1L, Math.round(this.ctx.settings().miner.seatRestTicks
                    * this.ctx.settings().moodRestMultiplierAt(mood))));
        }
        golem.resumeSeatRest(false);
        golem.state(MinerState.SITTING);
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
        golem.state(MinerState.IDLE);
    }

    public void placeCarriedSeat(ActiveGolem golem, CopperGolem copper, Material carriedStairs, int radius) {
        GolemSeatWork.Phase phase = this.ctx.seatWork().placeCarriedSeat(golem, copper, carriedStairs, radius);
        this.shared.applySeatPhase(golem, phase);
        if (phase == GolemSeatWork.Phase.PLACING) {
            golem.restTicksLeft(Math.max(1L, Math.round(this.ctx.settings().miner.seatRestTicks
                    * this.ctx.settings().moodRestMultiplierAt(this.ctx.moodScore(golem.data())))));
        }
    }

    public void continueFence(ActiveGolem golem, CopperGolem copper) {
        this.shared.continueFence(golem, copper, null);
    }

    public void takeFenceFromChest(ActiveGolem golem) {
        this.shared.takeFenceFromChest(golem);
    }
}
