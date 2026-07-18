package bm.b0b0b0.SoulGolem.service.digger;

import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.DiggerState;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.service.GolemCarried;
import bm.b0b0b0.SoulGolem.service.GolemGaze;
import bm.b0b0b0.SoulGolem.service.GolemTorchWork;
import bm.b0b0b0.SoulGolem.service.SoulChestLid;
import bm.b0b0b0.SoulGolem.service.SoulChestLink;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class DiggerChestWork {

    private final DiggerContext ctx;
    private DiggerSupportWork support;
    private DiggerCrewWork crew;

    public DiggerChestWork(DiggerContext ctx) {
        this.ctx = ctx;
    }

    public void wire(DiggerSupportWork support, DiggerCrewWork crew) {
        this.support = support;
        this.crew = crew;
    }

    public void continueDeposit(ActiveGolem golem, CopperGolem copper) {
        SoulChestLid lid = this.ctx.chestService().lid();
        SoulChestLink link = this.ctx.chestLink();
        SoulGolemData pit = this.ctx.pitData(golem);
        boolean crewReturn = DiggerDigWork.isCrewReturning(golem, pit, this.ctx.digger());
        Location chestStand = this.ctx.chestService().chestStandLocation(golem.data());
        if (chestStand == null) {
            lid.closeNow(golem.data());
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        boolean linked = link.isLinked(pit) && !crewReturn;
        if (!linked && !DiggerSafety.hasSolidSupport(chestStand)) {
            Location safe = DiggerSafety.rescueStand(
                    golem.data(),
                    this.ctx.farmAreaService(),
                    this.ctx.chestService(),
                    this.ctx.digger()
            );
            if (safe != null) {
                this.ctx.walkTowards(copper, safe, golem);
            }
        }
        if (!link.canAccess(copper, golem.data()) || (crewReturn && !nearChest(copper, chestStand, pit))) {
            lid.closeNow(golem.data());
            if (copper.getLocation().getY() < pit.homeY() - 1.8D) {
                this.ctx.movement().stop(copper);
                golem.diggerState(DiggerState.ESCAPING);
                return;
            }
            this.ctx.walkTowards(copper, chestStand, golem);
            return;
        }
        this.ctx.movement().stop(copper);
        copper.setVelocity(new Vector(0, 0, 0));
        if (!linked) {
            Location chestLook = new Location(
                    copper.getWorld(),
                    golem.data().chestX() + 0.5D,
                    golem.data().chestY() + 0.5D,
                    golem.data().chestZ() + 0.5D
            );
            GolemGaze.face(golem, chestLook);
            lid.open(golem.data());
        }

        if (crewReturn) {
            if (!golem.carried().isEmpty()) {
                List<ItemStack> leftover = new ArrayList<>();
                for (ItemStack stack : golem.carried()) {
                    if (stack == null || stack.getType().isAir()) {
                        continue;
                    }
                    if (!this.ctx.chestService().deposit(pit, stack.clone())) {
                        leftover.add(stack.clone());
                    }
                }
                golem.clearCarried();
                for (ItemStack stack : leftover) {
                    golem.carry(stack);
                }
                if (!leftover.isEmpty()) {
                    golem.diggerState(DiggerState.WAITING_CHEST);
                    this.ctx.notifyChestFull(golem);
                    return;
                }
                finishChestAccess(golem, copper, false, SoulChestLink.Kind.DEPOSIT, lid, chestStand, true);
            }
            if (this.crew != null) {
                this.crew.dismissHelper(golem, copper);
            }
            return;
        }

        if (golem.fetchingTorch()) {
            takeTorchesFromChest(golem);
            finishChestAccess(golem, copper, linked, SoulChestLink.Kind.WITHDRAW, lid, chestStand, false);
            return;
        }
        if (golem.fetchingFence() || golem.fetchingGate()) {
            this.support.takeFenceFromChest(golem);
            finishChestAccess(golem, copper, linked, SoulChestLink.Kind.WITHDRAW, lid, chestStand, false);
            return;
        }
        if (golem.fetchingSeat()) {
            takeSeatFromChest(golem);
            finishChestAccess(golem, copper, linked, SoulChestLink.Kind.WITHDRAW, lid, chestStand, false);
            return;
        }
        if (golem.fetchingFeed()) {
            this.ctx.eatFeedFromChest(golem);
            finishChestAccess(golem, copper, linked, SoulChestLink.Kind.WITHDRAW, lid, chestStand, false);
            return;
        }
        if (golem.fetchingWeapon()) {
            this.ctx.combat().takeWeaponFromChest(golem, copper);
            finishChestAccess(golem, copper, linked, SoulChestLink.Kind.WITHDRAW, lid, chestStand, false);
            return;
        }

        if (golem.carried().isEmpty()) {
            if (!linked) {
                lid.closeLater(golem.data());
            }
            afterDeposit(golem);
            return;
        }
        if (!this.ctx.chestService().hasSpace(golem.data())) {
            if (!linked) {
                lid.closeLater(golem.data());
            }
            golem.diggerState(DiggerState.WAITING_CHEST);
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

        finishChestAccess(golem, copper, linked, SoulChestLink.Kind.DEPOSIT, lid, chestStand, true);
        if (!depositedAll) {
            golem.diggerState(DiggerState.WAITING_CHEST);
            this.ctx.notifyChestFull(golem);
            return;
        }
        afterDeposit(golem);
    }

    private void finishChestAccess(
            ActiveGolem golem,
            CopperGolem copper,
            boolean linked,
            SoulChestLink.Kind kind,
            SoulChestLid lid,
            Location chestStand,
            boolean localDepositFx
    ) {
        if (linked) {
            this.ctx.chestLink().play(copper, golem.data(), kind);
            return;
        }
        if (localDepositFx) {
            this.ctx.playDepositFx(chestStand);
        }
        lid.closeLater(golem.data());
    }

    public void afterDeposit(ActiveGolem golem) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        golem.data().lastActionAt(System.currentTimeMillis());
        golem.markDirty();

        SoulGolemData pit = this.ctx.pitData(golem);
        if (DiggerDigWork.isCrewReturning(golem, pit, this.ctx.digger())) {
            return;
        }
        if (isDepthDone(pit) && !golem.data().isCrewHelper()) {
            golem.diggerState(DiggerState.DONE);
            return;
        }

        if (this.ctx.tryStartFeed(golem)) {
            return;
        }

        boolean digging = pit.hasDigProgress() && !isDepthDone(pit);
        if (digging) {
            golem.diggerState(DiggerState.IDLE);
            return;
        }

        GolemSettings.Yard yard = this.ctx.settings().yard;
        if (yard.clearBorder) {
            List<Block> junk = this.ctx.farmAreaService().diggerYardWeeds(golem.data(), radius);
            if (!junk.isEmpty()) {
                golem.clearFetchFlags();
                golem.targetCrop(junk.get(0).getLocation());
                golem.diggerState(DiggerState.MOVING_TO_CLEAR);
                return;
            }
        }
        Material torch = this.ctx.resolveTorch();
        if (yard.placeTorches
                && this.ctx.chestService().countItem(golem.data(), torch) > 0
                && !this.ctx.farmAreaService().perimeterTorchSpots(golem.data(), radius, yard.maxTorches).isEmpty()) {
            golem.clearFetchFlags();
            golem.fetchingTorch(true);
            golem.diggerState(DiggerState.MOVING_TO_CHEST);
            return;
        }
        if (this.support.tryStartFenceJob(golem)) {
            return;
        }
        if (yard.collectGroundLoot && this.ctx.groundLoot().hasLoot(golem.data())) {
            golem.clearFetchFlags();
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        if (golem.diggerState() != DiggerState.DONE && this.support.tryGoToSeat(golem, radius)) {
            return;
        }
        golem.diggerState(DiggerState.IDLE);
    }

    private static boolean nearChest(CopperGolem copper, Location chestStand, SoulGolemData pit) {
        if (copper == null || chestStand == null) {
            return false;
        }
        double dx = copper.getLocation().getX() - chestStand.getX();
        double dz = copper.getLocation().getZ() - chestStand.getZ();
        return dx * dx + dz * dz <= 2.25D
                && copper.getLocation().getY() >= pit.homeY() - 1.4D;
    }

    public void takeTorchesFromChest(ActiveGolem golem) {
        GolemTorchWork.Phase phase = this.ctx.torchWork().takeFromChest(
                golem,
                this.ctx.resolveTorch(),
                this.ctx.settings().yard.torchesPerTrip,
                this.ctx.settings().yard.maxTorches
        );
        if (phase == GolemTorchWork.Phase.MOVING) {
            golem.diggerState(DiggerState.MOVING_TO_TORCH);
        } else {
            golem.diggerState(DiggerState.IDLE);
        }
    }

    public void takeSeatFromChest(ActiveGolem golem) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        if (this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            golem.clearFetchFlags();
            golem.diggerState(DiggerState.MOVING_TO_SEAT);
            return;
        }
        Material already = GolemCarried.carriedStairs(golem);
        if (already != null) {
            Block spot = this.ctx.farmAreaService().findSeatSpot(golem.data(), radius);
            if (spot == null) {
                GolemCarried.returnToChest(this.ctx.chestService(), golem, already);
                golem.clearFetchFlags();
                golem.diggerState(DiggerState.IDLE);
                return;
            }
            golem.clearFetchFlags();
            golem.targetCrop(spot.getLocation());
            golem.diggerState(DiggerState.MOVING_TO_SEAT);
            return;
        }
        Material stairs = this.ctx.chestService().findStairsInChest(golem.data());
        Block spot = this.ctx.farmAreaService().findSeatSpot(golem.data(), radius);
        if (stairs == null || spot == null) {
            golem.clearFetchFlags();
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        if (!this.ctx.chestService().takeItem(golem.data(), stairs, 1)) {
            golem.clearFetchFlags();
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        golem.clearCarried();
        golem.carry(new ItemStack(stairs, 1));
        golem.clearFetchFlags();
        golem.targetCrop(spot.getLocation());
        golem.diggerState(DiggerState.MOVING_TO_SEAT);
        golem.markDirty();
    }

    public void continueRest(ActiveGolem golem) {
        long left = golem.restTicksLeft() - Math.max(1L, this.ctx.settings().coordinatorPeriodTicks);
        golem.restTicksLeft(left);
        if (left > 0L) {
            return;
        }
        golem.restTicksLeft(0L);
        golem.data().lastActionAt(System.currentTimeMillis());
        golem.diggerState(DiggerState.IDLE);
    }

    public void retryChest(ActiveGolem golem) {
        if (!golem.carried().isEmpty()) {
            if (this.ctx.chestService().hasSpace(golem.data())) {
                golem.chestFullNotified(false);
                golem.diggerState(DiggerState.MOVING_TO_CHEST);
            } else {
                golem.data().lastActionAt(System.currentTimeMillis());
            }
            return;
        }
        if (this.ctx.chestService().hasSpace(golem.data())) {
            golem.chestFullNotified(false);
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        golem.data().lastActionAt(System.currentTimeMillis());
    }

    private boolean isDepthDone(SoulGolemData data) {
        return DiggerDigWork.isPitComplete(data, this.ctx.digger());
    }
}
