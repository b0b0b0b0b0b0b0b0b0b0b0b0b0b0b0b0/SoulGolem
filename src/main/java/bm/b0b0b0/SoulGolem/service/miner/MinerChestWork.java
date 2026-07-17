package bm.b0b0b0.SoulGolem.service.miner;

import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.MinerState;
import bm.b0b0b0.SoulGolem.service.GolemGaze;
import bm.b0b0b0.SoulGolem.service.GolemMovement;
import bm.b0b0b0.SoulGolem.service.GolemTorchWork;
import bm.b0b0b0.SoulGolem.service.SoulChestLid;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class MinerChestWork {

    private final MinerContext ctx;
    private final MinerCarried carried;
    private MinerSupportWork support;

    public MinerChestWork(MinerContext ctx, MinerCarried carried) {
        this.ctx = ctx;
        this.carried = carried;
    }

    public void wire(MinerSupportWork support) {
        this.support = support;
    }

    public void continueDeposit(ActiveGolem golem, CopperGolem copper) {
        SoulChestLid lid = this.ctx.chestService().lid();
        Location chestStand = this.ctx.chestService().chestStandLocation(golem.data());
        if (chestStand == null) {
            lid.closeNow(golem.data());
            golem.state(MinerState.IDLE);
            return;
        }
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), chestStand) > 1.69D) {
            lid.closeNow(golem.data());
            this.ctx.walkTowards(copper, chestStand, golem);
            return;
        }
        this.ctx.movement().stop(copper);
        copper.setVelocity(new Vector(0, 0, 0));
        Location chestLook = new Location(
                copper.getWorld(),
                golem.data().chestX() + 0.5D,
                golem.data().chestY() + 0.5D,
                golem.data().chestZ() + 0.5D
        );
        GolemGaze.face(golem, chestLook);
        lid.open(golem.data());

        if (golem.fetchingTorch()) {
            takeTorchesFromChest(golem);
            lid.closeLater(golem.data());
            return;
        }
        if (golem.fetchingFence() || golem.fetchingGate()) {
            this.support.takeFenceFromChest(golem);
            lid.closeLater(golem.data());
            return;
        }
        if (golem.fetchingSeat()) {
            takeSeatFromChest(golem);
            lid.closeLater(golem.data());
            return;
        }
        if (golem.fetchingFeed()) {
            takeFeedFromChest(golem);
            lid.closeLater(golem.data());
            return;
        }
        if (golem.fetchingPickaxe()) {
            this.ctx.pickaxeWork().swapAtChest(golem);
            lid.closeLater(golem.data());
            return;
        }
        if (golem.fetchingWeapon()) {
            this.ctx.combat().takeWeaponFromChest(golem, copper);
            lid.closeLater(golem.data());
            return;
        }

        if (golem.carried().isEmpty()) {
            lid.closeLater(golem.data());
            afterDeposit(golem);
            return;
        }
        if (!this.ctx.chestService().hasSpace(golem.data())) {
            lid.closeLater(golem.data());
            golem.state(MinerState.WAITING_CHEST);
            this.ctx.notifyChestFull(golem);
            return;
        }

        boolean depositedAll = true;
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack stack : golem.carried()) {
            if (!this.ctx.chestService().deposit(golem.data(), stack.clone())) {
                depositedAll = false;
                leftover.add(stack.clone());
            }
        }
        golem.clearCarried();
        for (ItemStack stack : leftover) {
            golem.carry(stack);
        }

        this.ctx.playDepositFx(chestStand);
        lid.closeLater(golem.data());
        if (!depositedAll) {
            golem.state(MinerState.WAITING_CHEST);
            this.ctx.notifyChestFull(golem);
            return;
        }
        afterDeposit(golem);
    }

    public void afterDeposit(ActiveGolem golem) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        this.ctx.workAreaService().seedOres(golem.data(), radius, this.ctx.oreTable());
        golem.blocksLeftThisTrip(0);
        golem.data().lastActionAt(System.currentTimeMillis());
        golem.markDirty();

        if (this.ctx.pickaxeWork().tryStartUpgrade(golem)) {
            return;
        }

        if (this.ctx.tryStartFeed(golem)) {
            return;
        }

        Settings.Miner miner = this.ctx.settings().miner;
        if (miner.clearArea) {
            List<Block> junk = this.ctx.farmAreaService().minerJunkToClear(golem.data(), radius, this.ctx.oreTable());
            if (!junk.isEmpty()) {
                golem.clearFetchFlags();
                golem.targetCrop(junk.get(0).getLocation());
                golem.state(MinerState.MOVING_TO_CLEAR);
                return;
            }
        }
        Material torch = this.ctx.resolveTorch();
        if (miner.placeTorches
                && this.ctx.chestService().countItem(golem.data(), torch) > 0
                && !this.ctx.farmAreaService().perimeterTorchSpots(golem.data(), radius, miner.maxTorches).isEmpty()) {
            golem.clearFetchFlags();
            golem.fetchingTorch(true);
            golem.state(MinerState.MOVING_TO_CHEST);
            return;
        }
        if (this.support.tryStartFenceJob(golem, radius, miner)) {
            return;
        }
        if (miner.collectGroundLoot && this.ctx.groundLoot().hasLoot(golem.data())) {
            golem.clearFetchFlags();
            golem.state(MinerState.SEEKING);
            return;
        }
        if (this.support.tryGoToSeat(golem, radius, miner)) {
            return;
        }
        startRest(golem);
    }

    public void takeTorchesFromChest(ActiveGolem golem) {
        GolemTorchWork.Phase phase = this.ctx.torchWork().takeFromChest(
                golem,
                this.ctx.resolveTorch(),
                this.ctx.settings().miner.torchesPerTrip,
                this.ctx.settings().miner.maxTorches
        );
        if (phase == GolemTorchWork.Phase.MOVING) {
            golem.state(MinerState.MOVING_TO_TORCH);
        } else {
            golem.state(MinerState.IDLE);
        }
    }

    public void takeSeatFromChest(ActiveGolem golem) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        if (this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            golem.clearFetchFlags();
            golem.state(MinerState.MOVING_TO_SEAT);
            return;
        }
        Material already = MinerCarried.carriedStairs(golem);
        if (already != null) {
            Block spot = this.ctx.farmAreaService().findSeatSpot(golem.data(), radius);
            if (spot == null) {
                this.carried.returnCarriedToChest(golem, already);
                golem.clearFetchFlags();
                golem.state(MinerState.IDLE);
                return;
            }
            golem.clearFetchFlags();
            golem.targetCrop(spot.getLocation());
            golem.state(MinerState.MOVING_TO_SEAT);
            return;
        }
        Material stairs = this.ctx.chestService().findStairsInChest(golem.data());
        Block spot = this.ctx.farmAreaService().findSeatSpot(golem.data(), radius);
        if (stairs == null || spot == null) {
            golem.clearFetchFlags();
            golem.state(MinerState.IDLE);
            return;
        }
        if (!this.ctx.chestService().takeItem(golem.data(), stairs, 1)) {
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

    public void takeFeedFromChest(ActiveGolem golem) {
        this.ctx.eatFeedFromChest(golem);
    }

    public void startRest(ActiveGolem golem) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        if (this.support != null && this.support.tryGoToSeat(golem, radius, this.ctx.settings().miner)) {
            return;
        }
        int mood = this.ctx.moodScore(golem.data());
        double restMult = this.ctx.settings().moodRestMultiplierAt(mood);
        long rest = Math.max(0L, Math.round(this.ctx.settings().miner.standingRestTicks * restMult));
        if (rest <= 0L) {
            golem.state(MinerState.IDLE);
            return;
        }
        golem.clearFetchFlags();
        golem.restTicksLeft(rest);
        golem.data().lastActionAt(System.currentTimeMillis());
        golem.state(MinerState.RESTING);
    }

    public void continueRest(ActiveGolem golem) {
        long now = System.currentTimeMillis();
        long interval = Math.max(500L, this.ctx.settings().miner.standingRestSeatCheckMs);
        if (now - golem.data().lastActionAt() >= interval) {
            golem.data().lastActionAt(now);
            int radius = this.ctx.chestService().effectiveRadius(golem.data());
            if (this.support != null && this.support.tryGoToSeat(golem, radius, this.ctx.settings().miner)) {
                golem.restTicksLeft(0L);
                return;
            }
        }
        long left = golem.restTicksLeft() - Math.max(1L, this.ctx.settings().coordinatorPeriodTicks);
        golem.restTicksLeft(left);
        if (left > 0L) {
            return;
        }
        golem.restTicksLeft(0L);
        golem.data().lastActionAt(System.currentTimeMillis());
        golem.state(MinerState.IDLE);
    }

    public void retryChest(ActiveGolem golem) {
        if (golem.pickaxeSwapBlocked() && this.ctx.chestService().hasSpace(golem.data())) {
            golem.pickaxeSwapBlocked(false);
            golem.chestFullNotified(false);
            if (this.ctx.pickaxeWork().tryStartUpgrade(golem)) {
                return;
            }
        }
        if (!golem.carried().isEmpty()) {
            if (this.ctx.chestService().hasSpace(golem.data())) {
                golem.chestFullNotified(false);
                golem.state(MinerState.MOVING_TO_CHEST);
            } else {
                golem.data().lastActionAt(System.currentTimeMillis());
            }
            return;
        }
        if (this.ctx.chestService().hasSpace(golem.data())) {
            golem.chestFullNotified(false);
            golem.state(MinerState.IDLE);
            return;
        }
        golem.data().lastActionAt(System.currentTimeMillis());
    }
}
