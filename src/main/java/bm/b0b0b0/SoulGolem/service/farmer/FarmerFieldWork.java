package bm.b0b0b0.SoulGolem.service.farmer;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.CropType;
import bm.b0b0b0.SoulGolem.model.FarmerState;
import bm.b0b0b0.SoulGolem.service.GolemGroundLootWork;
import bm.b0b0b0.SoulGolem.service.GolemGaze;
import bm.b0b0b0.SoulGolem.service.GolemMovement;
import java.util.Collection;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class FarmerFieldWork {

    private final FarmerContext ctx;
    private final FarmerCarried carried;
    private FarmerCycle cycle;
    private FarmerSupportWork support;

    public FarmerFieldWork(FarmerContext ctx, FarmerCarried carried) {
        this.ctx = ctx;
        this.carried = carried;
    }

    public void wire(FarmerCycle cycle, FarmerSupportWork support) {
        this.cycle = cycle;
        this.support = support;
    }

    private boolean divertToFence(ActiveGolem golem, CopperGolem copper) {
        if (!this.cycle.tryPrioritizeFence(golem)) {
            return false;
        }
        FarmerState state = golem.farmerState();
        if (state == FarmerState.MOVING_TO_FENCE
                || state == FarmerState.MOVING_TO_FENCE_CLEAR
                || state == FarmerState.CLEARING_FENCE
                || state == FarmerState.PLACING_FENCE
                || state == FarmerState.MOVING_TO_GATE
                || state == FarmerState.PLACING_GATE) {
            this.support.continueFence(golem, copper);
        }
        return true;
    }

    public void continueTill(ActiveGolem golem, CopperGolem copper) {
        if (!this.ctx.stationsOk(golem)) {
            return;
        }
        if (divertToFence(golem, copper)) {
            return;
        }
        this.ctx.farmAreaService().ensureWater(golem.data());
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        Location target = golem.targetCrop();
        Block soil = target == null ? null : target.getBlock();
        if (soil == null || !canTillNow(soil)) {
            List<Block> tillable = this.ctx.farmAreaService().tillableSoil(golem.data(), radius);
            if (tillable.isEmpty()) {
                golem.targetCrop(null);
                golem.fieldReady(true);
                if (!this.cycle.tryStartPlant(golem)) {
                    int r = this.ctx.chestService().effectiveRadius(golem.data());
                    if (this.ctx.farmAreaService().hasFieldCrops(golem.data(), r, this.ctx.enabledCrops())) {
                        golem.farmerState(FarmerState.WAIT_GROWTH);
                    } else if (this.ctx.hasOpenPlantSpots(golem.data())) {
                        golem.farmerState(FarmerState.WAITING_SEEDS);
                    } else {
                        golem.farmerState(FarmerState.WAIT_GROWTH);
                    }
                }
                golem.markDirty();
                return;
            }
            soil = this.ctx.farmAreaService().pickFarthestFromChest(tillable, golem.data());
            if (soil == null) {
                soil = tillable.get(0);
            }
            golem.targetCrop(soil.getLocation());
        }
        Location stand = this.ctx.farmAreaService().standOn(soil);
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 1.2D
                || Math.abs(copper.getLocation().getY() - stand.getY()) > 1.0D) {
            golem.farmerState(FarmerState.MOVING_TO_TILL);
            this.ctx.movement().walkTowards(copper, stand, golem);
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.farmerState(FarmerState.TILLING);
        GolemGaze.faceBlock(golem, soil);
        this.ctx.farmAreaService().tillSoil(soil, golem.data().id());
        this.ctx.drainFarmEnergy(golem);
        golem.targetCrop(null);
        golem.markDirty();
        List<Block> left = this.ctx.farmAreaService().tillableSoil(golem.data(), radius);
        if (left.isEmpty()) {
            golem.fieldReady(true);
            if (!this.cycle.tryStartPlant(golem)) {
                int r = this.ctx.chestService().effectiveRadius(golem.data());
                if (this.ctx.farmAreaService().hasFieldCrops(golem.data(), r, this.ctx.enabledCrops())) {
                    golem.farmerState(FarmerState.WAIT_GROWTH);
                } else if (this.ctx.hasOpenPlantSpots(golem.data())) {
                    golem.farmerState(FarmerState.WAITING_SEEDS);
                } else {
                    golem.farmerState(FarmerState.WAIT_GROWTH);
                }
            }
            return;
        }
        Block next = this.ctx.farmAreaService().pickFarthestFromChest(left, golem.data());
        golem.targetCrop(next != null ? next.getLocation() : left.get(0).getLocation());
        golem.farmerState(FarmerState.MOVING_TO_TILL);
    }

    private static boolean canTillNow(Block soil) {
        Material type = soil.getType();
        return type == Material.GRASS_BLOCK
                || type == Material.DIRT
                || type == Material.COARSE_DIRT
                || type == Material.ROOTED_DIRT
                || type == Material.DIRT_PATH;
    }

    public void continuePlant(ActiveGolem golem, CopperGolem copper) {
        if (divertToFence(golem, copper)) {
            return;
        }
        if (!this.carried.hasCarriedSeed(golem)) {
            golem.fetchingSeed(true);
            golem.farmerState(FarmerState.MOVING_TO_CHEST);
            return;
        }
        Location target = golem.targetCrop();
        if (target == null) {
            int radius = this.ctx.chestService().effectiveRadius(golem.data());
            List<Block> empty = this.ctx.farmAreaService().emptyFarmland(golem.data(), radius);
            if (empty.isEmpty()) {
                this.carried.returnSeedToChest(golem);
                golem.farmerState(FarmerState.WAIT_GROWTH);
                return;
            }
            target = this.ctx.farthestPlot(golem.data(), empty);
            golem.targetCrop(target);
        }
        if (target == null) {
            this.carried.returnSeedToChest(golem);
            this.cycle.afterPlant(golem);
            return;
        }
        Block soil = target.getBlock();
        if (soil.getType() != Material.FARMLAND) {
            golem.targetCrop(null);
            this.carried.returnSeedToChest(golem);
            this.cycle.afterPlant(golem);
            return;
        }
        Location stand = this.ctx.farmAreaService().standOn(soil);
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 1.2D) {
            golem.farmerState(FarmerState.MOVING_TO_PLANT);
            this.ctx.movement().walkTowards(copper, stand, golem);
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.farmerState(FarmerState.PLANTING);
        CropType plantType = this.carried.carriedCropType(golem);
        if (plantType == null) {
            plantType = CropType.WHEAT;
        }
        GolemGaze.faceBlockTop(golem, soil);
        this.carried.consumeCarriedSeed(golem);
        this.ctx.farmAreaService().plantCrop(soil, plantType, golem.data().id());
        this.ctx.drainFarmEnergy(golem);
        golem.targetCrop(null);
        golem.fetchingSeed(false);
        golem.data().lastActionAt(System.currentTimeMillis());
        golem.markDirty();
        if (this.carried.hasCarriedSeed(golem)) {
            int radiusLeft = this.ctx.chestService().effectiveRadius(golem.data());
            List<Block> emptyLeft = this.ctx.farmAreaService().plantSpots(golem.data(), radiusLeft, plantType);
            if (!emptyLeft.isEmpty()) {
                golem.targetCrop(this.ctx.farthestPlot(golem.data(), emptyLeft));
                golem.farmerState(FarmerState.MOVING_TO_PLANT);
                return;
            }
            this.carried.returnSeedToChest(golem);
        }
        this.cycle.afterPlant(golem);
    }

    public void waitGrowth(ActiveGolem golem, CopperGolem copper) {
        long now = System.currentTimeMillis();
        if (!this.ctx.stationsOk(golem)) {
            return;
        }
        if (!this.ctx.farmAreaService().ensureInsideBorder(golem, copper, this.ctx.movement())) {
            return;
        }
        if (divertToFence(golem, copper)) {
            return;
        }
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        if (this.cycle.tryStartTorchJob(golem)) {
            golem.wanderTarget(null);
            return;
        }
        if (!this.ctx.farmAreaService().matureCrops(golem.data(), radius, this.ctx.enabledCrops()).isEmpty()
                || !this.ctx.farmAreaService().fieldWeeds(golem.data(), radius).isEmpty()) {
            if (this.cycle.assignNextJob(golem)) {
                golem.wanderTarget(null);
                return;
            }
        }
        if (this.ctx.settings().farmer.collectGroundLoot) {
            GolemGroundLootWork.Phase loot = this.ctx.groundLoot().tick(golem, copper, true);
            if (loot == GolemGroundLootWork.Phase.MOVING) {
                golem.farmerState(FarmerState.WANDERING);
                return;
            }
            if (loot == GolemGroundLootWork.Phase.PICKED) {
                golem.wanderTarget(null);
                if (this.ctx.groundLoot().hasLoot(golem.data())) {
                    golem.farmerState(FarmerState.WANDERING);
                    return;
                }
                if (!golem.carried().isEmpty() && this.ctx.chestService().hasSpace(golem.data())) {
                    if (this.ctx.resumeBoneMealCarried(golem)) {
                        return;
                    }
                    golem.farmerState(FarmerState.MOVING_TO_CHEST);
                    return;
                }
            }
        }
        if (FarmerCarried.carriedStairs(golem) != null && !this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            golem.wanderTarget(null);
            golem.clearFetchFlags();
            golem.farmerState(FarmerState.MOVING_TO_SEAT);
            this.support.continueSeat(golem, copper);
            return;
        }
        if (this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            golem.wanderTarget(null);
            golem.farmerState(FarmerState.MOVING_TO_SEAT);
            this.support.continueSeat(golem, copper);
            return;
        }
        if (this.cycle.assignNextJob(golem)) {
            golem.wanderTarget(null);
            return;
        }
        if (!golem.carried().isEmpty() && this.ctx.chestService().hasSpace(golem.data())) {
            golem.wanderTarget(null);
            if (this.ctx.resumeBoneMealCarried(golem)) {
                return;
            }
            golem.farmerState(FarmerState.MOVING_TO_CHEST);
            return;
        }
        if (this.ctx.settings().farmer.wanderWhileWaiting) {
            wander(golem, copper, this.ctx.chestService().effectiveRadius(golem.data()));
            return;
        }
        golem.farmerState(FarmerState.WAIT_GROWTH);
        golem.data().lastActionAt(now);
    }

    public void wander(ActiveGolem golem, CopperGolem copper, int radius) {
        if (!this.ctx.farmAreaService().ensureInsideBorder(golem, copper, this.ctx.movement())) {
            return;
        }
        golem.farmerState(FarmerState.WANDERING);
        Location target = golem.wanderTarget();
        if (target == null
                || !this.ctx.farmAreaService().insideWoolBorder(golem.data(), target)
                || GolemMovement.horizontalDistanceSquared(copper.getLocation(), target) < 1.0D) {
            target = this.ctx.farmAreaService().randomWanderPoint(golem.data(), radius);
            golem.wanderTarget(target);
            if (target == null) {
                return;
            }
        }
        this.ctx.movement().walkTowards(copper, target, golem);
    }

    public void continueHarvest(ActiveGolem golem, CopperGolem copper) {
        if (divertToFence(golem, copper)) {
            return;
        }
        Location target = golem.targetCrop();
        if (target == null) {
            this.cycle.enterIdle(golem);
            return;
        }
        Block crop = target.getBlock();
        CropType cropType = CropType.byCrop(crop.getType());
        if (cropType == null || !this.ctx.enabledCrops().contains(cropType)) {
            golem.targetCrop(null);
            if (this.cycle.assignNextJob(golem)) {
                return;
            }
            this.cycle.enterIdle(golem);
            return;
        }
        Location stand = crop.getLocation().add(0.5D, 0.0D, 0.5D);
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 1.2D) {
            golem.farmerState(FarmerState.MOVING_TO_HARVEST);
            this.ctx.movement().walkTowards(copper, stand, golem);
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.farmerState(FarmerState.HARVESTING);
        GolemGaze.faceBlock(golem, crop);
        Collection<ItemStack> drops = FarmerCarried.cropHarvestDrops(crop, cropType);
        crop.setType(Material.AIR, false);
        for (ItemStack drop : drops) {
            golem.carry(drop);
        }
        golem.data().incrementBlocksMined();
        this.ctx.drainFarmEnergy(golem);
        golem.targetCrop(null);
        golem.fetchingSeed(false);
        golem.markDirty();
        golem.farmerState(FarmerState.MOVING_TO_CHEST);
    }

    public void continueBoneMeal(ActiveGolem golem, CopperGolem copper) {
        if (divertToFence(golem, copper)) {
            return;
        }
        if (this.ctx.hasPlantWork(golem)) {
            this.carried.returnCarriedToChest(golem, Material.BONE_MEAL);
            golem.targetCrop(null);
            golem.clearFetchFlags();
            if (this.cycle.assignNextJob(golem)) {
                return;
            }
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        if (FarmerCarried.countCarried(golem, Material.BONE_MEAL) <= 0) {
            if (this.cycle.assignNextJob(golem)) {
                return;
            }
            golem.targetCrop(null);
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        Location target = golem.targetCrop();
        if (target == null) {
            List<Block> immature = this.ctx.immatureBoneMealCrops(golem.data());
            if (immature.isEmpty()) {
                this.carried.returnCarriedToChest(golem, Material.BONE_MEAL);
                golem.farmerState(FarmerState.WAIT_GROWTH);
                return;
            }
            target = this.ctx.boneMealPlot(golem.data(), immature);
            golem.targetCrop(target);
        }
        if (target == null) {
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        Block crop = target.getBlock();
        CropType cropType = CropType.byCrop(crop.getType());
        if (cropType == null
                || !this.ctx.enabledCrops().contains(cropType)
                || !(crop.getBlockData() instanceof org.bukkit.block.data.Ageable ageableCrop)) {
            golem.targetCrop(null);
            List<Block> left = this.ctx.immatureBoneMealCrops(golem.data());
            if (!left.isEmpty()) {
                golem.targetCrop(this.ctx.boneMealPlot(golem.data(), left));
                golem.farmerState(FarmerState.MOVING_TO_BONEMEAL);
                return;
            }
            this.carried.returnCarriedToChest(golem, Material.BONE_MEAL);
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        if (ageableCrop.getAge() >= ageableCrop.getMaximumAge()) {
            golem.targetCrop(crop.getLocation());
            golem.farmerState(FarmerState.MOVING_TO_HARVEST);
            return;
        }
        Location stand = crop.getLocation().add(0.5D, 0.0D, 0.5D);
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 1.2D) {
            golem.farmerState(FarmerState.MOVING_TO_BONEMEAL);
            this.ctx.movement().walkTowards(copper, stand, golem);
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.farmerState(FarmerState.APPLYING_BONEMEAL);
        GolemGaze.faceBlock(golem, crop);

        int ageBefore = 0;
        if (crop.getBlockData() instanceof org.bukkit.block.data.Ageable ageable) {
            ageBefore = ageable.getAge();
        }
        crop.applyBoneMeal(org.bukkit.block.BlockFace.UP);
        boolean grew = false;
        if (crop.getBlockData() instanceof org.bukkit.block.data.Ageable after) {
            grew = after.getAge() > ageBefore;
        }
        if (grew) {
            FarmerCarried.consumeCarried(golem, Material.BONE_MEAL, 1);
        } else {
            this.carried.returnCarriedToChest(golem, Material.BONE_MEAL);
            golem.targetCrop(null);
            golem.farmerState(FarmerState.WAIT_GROWTH);
            golem.data().lastActionAt(System.currentTimeMillis());
            return;
        }
        golem.markDirty();

        if (crop.getBlockData() instanceof org.bukkit.block.data.Ageable done && done.getAge() >= done.getMaximumAge()) {
            golem.targetCrop(crop.getLocation());
            golem.farmerState(FarmerState.MOVING_TO_HARVEST);
            return;
        }
        if (FarmerCarried.countCarried(golem, Material.BONE_MEAL) > 0) {
            if (this.ctx.hasPlantWork(golem)) {
                this.carried.returnCarriedToChest(golem, Material.BONE_MEAL);
                golem.targetCrop(null);
                if (this.cycle.assignNextJob(golem)) {
                    return;
                }
            }
            List<Block> stillGrowing = this.ctx.immatureBoneMealCrops(golem.data());
            Location next = this.ctx.boneMealPlot(golem.data(), stillGrowing);
            golem.targetCrop(next != null ? next : crop.getLocation());
            golem.farmerState(FarmerState.MOVING_TO_BONEMEAL);
            return;
        }
        if (this.cycle.assignNextJob(golem)) {
            return;
        }
        golem.targetCrop(null);
        golem.farmerState(FarmerState.WAIT_GROWTH);
        golem.data().lastActionAt(System.currentTimeMillis());
    }

    private void approachChestForBoneMeal(ActiveGolem golem, CopperGolem copper) {
        Location chestStand = this.ctx.chestService().chestStandLocation(golem.data());
        if (chestStand == null) {
            golem.clearFetchFlags();
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        this.ctx.requestBoneMealFromChest(golem);
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), chestStand) <= 1.69D) {
            this.ctx.chestService().lid().run(golem.data(), () -> takeBoneMealFromChest(golem));
            return;
        }
        this.ctx.chestService().lid().closeNow(golem.data());
        this.ctx.movement().walkTowards(copper, chestStand, golem);
    }

    public void takeBoneMealFromChest(ActiveGolem golem) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        List<Block> immature = this.ctx.immatureBoneMealCrops(golem.data());
        int available = this.ctx.chestService().countItem(golem.data(), Material.BONE_MEAL);
        if (immature.isEmpty() || available <= 0) {
            golem.clearFetchFlags();
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        int want = Math.min(Math.max(1, this.ctx.settings().farmer.boneMealPerTrip), available);
        if (!this.ctx.chestService().takeItem(golem.data(), Material.BONE_MEAL, want)) {
            golem.clearFetchFlags();
            golem.farmerState(FarmerState.WAIT_GROWTH);
            return;
        }
        golem.clearCarried();
        golem.carry(new ItemStack(Material.BONE_MEAL, want));
        golem.clearFetchFlags();
        golem.targetCrop(this.ctx.boneMealPlot(golem.data(), immature));
        golem.farmerState(FarmerState.MOVING_TO_BONEMEAL);
        golem.markDirty();
    }

    public void takeSeedFromChest(ActiveGolem golem) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        Material seed = this.ctx.findSeedInChest(golem.data());
        if (seed == null) {
            golem.fetchingSeed(false);
            this.cycle.afterPlant(golem);
            return;
        }
        CropType plantType = CropType.bySeed(seed);
        if (plantType == null) {
            golem.fetchingSeed(false);
            this.cycle.afterPlant(golem);
            return;
        }
        List<Block> spots = this.ctx.farmAreaService().plantSpots(golem.data(), radius, plantType);
        if (spots.isEmpty()) {
            golem.fetchingSeed(false);
            this.cycle.afterPlant(golem);
            return;
        }
        int available = this.ctx.chestService().countItem(golem.data(), seed);
        int want = Math.min(Math.max(1, this.ctx.settings().farmer.seedsPerTrip), spots.size());
        want = Math.min(want, available);
        if (want <= 0 || !this.ctx.chestService().takeItem(golem.data(), seed, want)) {
            golem.fetchingSeed(false);
            this.cycle.afterPlant(golem);
            return;
        }
        golem.clearCarried();
        golem.carry(new ItemStack(seed, want));
        golem.fetchingSeed(false);
        golem.targetCrop(this.ctx.farthestPlot(golem.data(), spots));
        golem.farmerState(FarmerState.MOVING_TO_PLANT);
        golem.markDirty();
    }
}
