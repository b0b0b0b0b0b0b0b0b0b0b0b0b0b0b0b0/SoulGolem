package bm.b0b0b0.SoulGolem.service.farmer;

import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.FarmerState;
import bm.b0b0b0.SoulGolem.service.FarmAreaService;
import bm.b0b0b0.SoulGolem.service.GolemCarried;
import bm.b0b0b0.SoulGolem.service.GolemControlService;
import bm.b0b0b0.SoulGolem.service.GolemGaze;
import bm.b0b0b0.SoulGolem.service.GolemMovement;
import bm.b0b0b0.SoulGolem.service.GolemSeatWork;
import bm.b0b0b0.SoulGolem.service.GolemSupportWork;
import java.util.Collection;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class FarmerSupportWork {

    private final FarmerContext ctx;
    private final FarmerSupportBridge bridge;
    private final GolemSupportWork shared;
    private FarmerCycle cycle;
    private FarmerCompostWork compost;
    private FarmerChestWork chest;

    public FarmerSupportWork(FarmerContext ctx) {
        this.ctx = ctx;
        this.bridge = new FarmerSupportBridge(ctx);
        this.shared = new GolemSupportWork(ctx, this.bridge);
    }

    public void wire(FarmerCycle cycle, FarmerCompostWork compost, FarmerChestWork chest) {
        this.cycle = cycle;
        this.compost = compost;
        this.chest = chest;
        this.bridge.wire(cycle);
    }

    public void continueClear(ActiveGolem golem, CopperGolem copper) {
        if (!this.ctx.stationsOk(golem)) {
            return;
        }
        if (this.cycle.tryPrioritizeFence(golem)) {
            return;
        }
        this.ctx.farmAreaService().ensureWater(golem.data());
        if (!golem.carried().isEmpty() && !this.ctx.chestService().hasSpace(golem.data())) {
            golem.farmerState(FarmerState.MOVING_TO_CHEST);
            return;
        }
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        Location target = golem.targetCrop();
        Block junk = target == null ? null : target.getBlock();
        if (junk == null || junk.getType().isAir() || !GolemSupportWork.isClearableJunk(junk) || isBorderBlock(junk)) {
            List<Block> list = this.ctx.farmAreaService().weedsToClear(golem.data(), radius);
            list.removeIf(this::isBorderBlock);
            if (list.isEmpty()) {
                this.cycle.resumeAfterClear(golem);
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
            golem.farmerState(FarmerState.MOVING_TO_CLEAR);
            this.ctx.movement().walkTowards(copper, stand, golem);
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.farmerState(FarmerState.CLEARING);
        GolemGaze.faceBlock(golem, junk);
        if (FarmAreaService.isVegetation(junk.getType())) {
            FarmAreaService.clearVegetation(junk);
        } else {
            Material shovel = this.ctx.resolveShovel();
            Collection<ItemStack> drops = junk.getDrops(new ItemStack(shovel));
            junk.setType(Material.AIR, false);
            for (ItemStack drop : drops) {
                golem.carry(drop);
            }
        }
        this.ctx.drainFarmEnergy(golem);
        golem.targetCrop(null);
        golem.markDirty();
        List<Block> left = this.ctx.farmAreaService().weedsToClear(golem.data(), radius);
        left.removeIf(this::isBorderBlock);
        if (!left.isEmpty() && (golem.carried().isEmpty() || this.ctx.chestService().hasSpace(golem.data()))) {
            Block next = GolemSupportWork.pickNearest(copper.getLocation(), left);
            golem.targetCrop(next.getLocation());
            golem.farmerState(FarmerState.MOVING_TO_CLEAR);
            return;
        }
        this.cycle.resumeAfterClear(golem);
    }

    public void continueTorch(ActiveGolem golem, CopperGolem copper) {
        this.shared.continueTorch(golem, copper);
    }

    public void takeTorchesFromChest(ActiveGolem golem) {
        this.shared.takeTorchesFromChest(golem);
    }

    public void continueFence(ActiveGolem golem, CopperGolem copper) {
        if (!this.ctx.stationsOk(golem)) {
            return;
        }
        this.shared.continueFence(golem, copper, this.ctx::drainFarmEnergy);
    }

    public void takeFenceFromChest(ActiveGolem golem) {
        this.shared.takeFenceFromChest(golem);
    }

    public void continueShelter(ActiveGolem golem, CopperGolem copper) {
        this.shared.continueShelter(golem, copper);
    }

    public void continueCloseGate(ActiveGolem golem, CopperGolem copper) {
        this.shared.continueCloseGate(golem, copper);
    }

    public void continueSeat(ActiveGolem golem, CopperGolem copper) {
        if (!this.ctx.stationsOk(golem)) {
            return;
        }

        if (golem.farmerState() == FarmerState.SITTING
                && this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            golem.resumeSeatRest(false);
            if (shouldLeaveSeat(golem)) {
                golem.wanderTarget(null);
                this.ctx.seatWork().leaveBench(
                        golem,
                        copper,
                        this.ctx.plugin(),
                        this.ctx.registry(),
                        this.ctx.keys()
                );
                if (this.cycle.tryPrioritizeFence(golem)) {
                    return;
                }
                if (this.cycle.assignNextJob(golem)) {
                    return;
                }
                if (this.ctx.settings().yard.collectGroundLoot
                        && this.ctx.groundLoot().hasLoot(golem.data())) {
                    golem.farmerState(FarmerState.WAIT_GROWTH);
                    return;
                }
                this.shared.applySeatPhase(golem, this.ctx.seatWork().sitOnBench(golem, copper));
                return;
            }
            this.ctx.seatWork().holdOnBench(golem, copper);
            return;
        }

        if (!this.ctx.farmAreaService().ensureInsideBorder(golem, copper, this.ctx.movement())) {
            return;
        }

        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        Material carriedStairs = GolemCarried.carriedStairs(golem);
        if (carriedStairs != null && !this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            this.shared.placeCarriedSeat(golem, copper, carriedStairs, radius);
            return;
        }

        if (!this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            if (this.cycle.assignNextJob(golem)) {
                return;
            }
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }

        GolemSeatWork.Phase phase = this.ctx.seatWork().sitOnBench(golem, copper);
        this.shared.applySeatPhase(golem, phase);
        if (phase == GolemSeatWork.Phase.SITTING && golem.pauseAfterRest()) {
            GolemControlService.finishPauseAfterRest(golem, this.ctx.repository());
        }
    }

    private boolean shouldLeaveSeat(ActiveGolem golem) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        if (!this.ctx.farmAreaService().matureCrops(golem.data(), radius, this.ctx.enabledCrops()).isEmpty()) {
            long noticeTicks = Math.max(0L, this.ctx.settings().farmer.harvestNoticeTicks);
            if (noticeTicks > 0L && golem.harvestNoticeUntil() <= 0L) {
                golem.armHarvestNotice(noticeTicks);
            }
            return golem.harvestNoticeReady();
        }
        if (!this.ctx.farmAreaService().fieldWeeds(golem.data(), radius).isEmpty()) {
            return true;
        }
        if (!golem.fieldReady()
                || !this.ctx.farmAreaService().tillableSoil(golem.data(), radius).isEmpty()) {
            return true;
        }
        if (this.ctx.hasPlantWork(golem)) {
            return true;
        }
        GolemSettings.Farmer farmer = this.ctx.settings().farmer;
        GolemSettings.Yard yard = this.ctx.settings().yard;
        if (farmer.useBoneMeal && this.ctx.hasBoneMealWork(golem)) {
            return true;
        }
        if (this.compost != null && farmer.useComposter) {
            if (this.compost.isCompostReady(golem.data())
                    || this.compost.needsPlaceComposter(golem)
                    || this.compost.hasFillWork(golem)) {
                return true;
            }
        }
        if (farmer.craftBread
                && this.ctx.chestService().isCraftPresent(golem.data())
                && this.ctx.chestService().countItem(golem.data(), Material.WHEAT) >= 3
                && this.ctx.chestService().hasSpace(golem.data())) {
            return true;
        }
        if (this.chest != null && this.chest.needsPlaceCraft(golem)) {
            return true;
        }
        if (canStartTorchJob(golem, radius, yard)) {
            return true;
        }
        if (yard.collectGroundLoot && this.ctx.groundLoot().hasLoot(golem.data())) {
            return true;
        }
        if (!yard.placeFence) {
            return false;
        }
        return this.ctx.farmAreaService().canProgressOuterFence(
                golem.data(),
                radius,
                GolemCarried.countTagged(golem, Tag.FENCES),
                GolemCarried.countTagged(golem, Tag.FENCE_GATES)
        );
    }

    private boolean canStartTorchJob(ActiveGolem golem, int radius, GolemSettings.Yard yard) {
        if (!yard.placeTorches) {
            return false;
        }
        Material torch = this.ctx.resolveTorch();
        return this.ctx.chestService().countItem(golem.data(), torch) > 0
                && !this.ctx.farmAreaService().perimeterTorchSpots(golem.data(), radius).isEmpty();
    }

    public void placeCarriedSeat(ActiveGolem golem, CopperGolem copper, Material carriedStairs, int radius) {
        this.shared.placeCarriedSeat(golem, copper, carriedStairs, radius);
    }

    public void takeSeatFromChest(ActiveGolem golem) {
        this.shared.takeSeatFromChest(golem, () -> golem.farmerState(FarmerState.WAIT_GROWTH));
    }

    public boolean isBorderBlock(Block block) {
        return this.ctx.farmAreaService().isBorderMaterial(block.getType());
    }
}
