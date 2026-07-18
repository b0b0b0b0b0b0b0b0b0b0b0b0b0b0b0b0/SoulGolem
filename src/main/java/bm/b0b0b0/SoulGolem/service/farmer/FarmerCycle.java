package bm.b0b0b0.SoulGolem.service.farmer;

import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.CropType;
import bm.b0b0b0.SoulGolem.model.FarmerState;
import bm.b0b0b0.SoulGolem.service.GolemFenceWork;
import bm.b0b0b0.SoulGolem.service.GolemGaze;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.inventory.ItemStack;

public final class FarmerCycle {

    private final FarmerContext ctx;
    private FarmerCompostWork compost;
    private FarmerChestWork chest;

    public FarmerCycle(FarmerContext ctx) {
        this.ctx = ctx;
    }

    public void wire(FarmerCompostWork compost, FarmerChestWork chest) {
        this.compost = compost;
        this.chest = chest;
    }

    public void beginCycle(ActiveGolem golem, CopperGolem copper) {
        if (!this.ctx.stationsOk(golem)) {
            return;
        }
        if (!this.ctx.farmAreaService().ensureInsideBorder(golem, copper, this.ctx.movement())) {
            return;
        }
        if (!golem.carried().isEmpty()) {
            if (this.ctx.resumeBoneMealCarried(golem)) {
                return;
            }
            if (this.compost != null && this.compost.carryingCompostables(golem)
                    && this.compost.hasValidComposter(golem.data())
                    && !this.compost.isCompostReady(golem.data())) {
                golem.clearFetchFlags();
                golem.farmerState(FarmerState.MOVING_TO_COMPOST);
                return;
            }
            if (FarmerCarried.countCarried(golem, Material.COMPOSTER) > 0 && this.compost != null
                    && this.compost.tryStartPlace(golem)) {
                return;
            }
            if (FarmerCarried.countCarried(golem, Material.CRAFTING_TABLE) > 0 && this.chest != null
                    && this.chest.tryStartPlaceCraft(golem)) {
                return;
            }
            if (tryStartPlant(golem)) {
                return;
            }
            golem.fetchingSeed(false);
            golem.farmerState(FarmerState.MOVING_TO_CHEST);
            return;
        }
        if (this.ctx.tryStartFeed(golem)) {
            return;
        }
        if (golem.data().energy() <= 0) {
            golem.data().lastActionAt(System.currentTimeMillis());
            return;
        }
        if (tryStartPlant(golem)) {
            return;
        }
        if (tryPrioritizeFence(golem)) {
            return;
        }
        if (assignNextJob(golem)) {
            return;
        }
        enterIdle(golem);
        if (golem.farmerState() == FarmerState.MOVING_TO_SEAT) {
            switch (this.ctx.seatWork().sitOnBench(golem, copper)) {
                case MOVING, PLACING -> golem.farmerState(FarmerState.MOVING_TO_SEAT);
                case SITTING -> golem.farmerState(FarmerState.SITTING);
                case UNAVAILABLE, DONE -> golem.farmerState(FarmerState.WAIT_GROWTH);
            }
        }
    }

    public void enterIdle(ActiveGolem golem) {
        golem.clearFetchFlags();
        golem.fetchingSeed(false);
        golem.wanderTarget(null);
        golem.targetCrop(null);
        GolemGaze.clear(golem);
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        if (this.ctx.settings().yard.collectGroundLoot && this.ctx.groundLoot().hasLoot(golem.data())) {
            golem.farmerState(FarmerState.WAIT_GROWTH);
            golem.data().lastActionAt(System.currentTimeMillis());
            return;
        }
        if (this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            golem.farmerState(FarmerState.MOVING_TO_SEAT);
            golem.data().lastActionAt(System.currentTimeMillis());
            return;
        }
        if (!this.ctx.farmAreaService().emptyFarmland(golem.data(), radius).isEmpty()) {
            if (this.ctx.farmAreaService().hasFieldCrops(golem.data(), radius, this.ctx.enabledCrops())) {
                golem.farmerState(FarmerState.WAIT_GROWTH);
            } else if (this.ctx.hasOpenPlantSpots(golem.data())) {
                golem.farmerState(FarmerState.WAITING_SEEDS);
            } else {
                golem.farmerState(FarmerState.WAIT_GROWTH);
            }
            golem.data().lastActionAt(System.currentTimeMillis());
            return;
        }
        if (!golem.carried().isEmpty() && this.ctx.chestService().hasSpace(golem.data())) {
            golem.farmerState(FarmerState.MOVING_TO_CHEST);
            golem.data().lastActionAt(System.currentTimeMillis());
            return;
        }
        golem.farmerState(FarmerState.WAIT_GROWTH);
        golem.data().lastActionAt(System.currentTimeMillis());
    }

    public boolean tryStartPlant(ActiveGolem golem) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        CropType plantType = carriedCropType(golem);
        if (plantType == null) {
            Material seed = this.ctx.findSeedInChest(golem.data());
            plantType = seed == null ? null : CropType.bySeed(seed);
        }
        if (plantType == null || !this.ctx.enabledCrops().contains(plantType)) {
            return false;
        }
        List<Block> empty = this.ctx.farmAreaService().plantSpots(golem.data(), radius, plantType);
        if (empty.isEmpty()) {
            return false;
        }
        Location plot = this.ctx.farthestPlot(golem.data(), empty);
        if (carriedHasSeed(golem)) {
            golem.clearFetchFlags();
            golem.wanderTarget(null);
            golem.targetCrop(plot);
            golem.farmerState(FarmerState.MOVING_TO_PLANT);
            return true;
        }
        if (!this.ctx.hasAnySeedInChest(golem.data())) {
            return false;
        }
        golem.clearFetchFlags();
        golem.wanderTarget(null);
        golem.fetchingSeed(true);
        golem.targetCrop(plot);
        golem.farmerState(FarmerState.MOVING_TO_CHEST);
        return true;
    }

    private CropType carriedCropType(ActiveGolem golem) {
        for (ItemStack stack : golem.carried()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            CropType type = CropType.bySeed(stack.getType());
            if (type != null && this.ctx.enabledCrops().contains(type)) {
                return type;
            }
        }
        return null;
    }

    private boolean carriedHasSeed(ActiveGolem golem) {
        for (ItemStack stack : golem.carried()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            CropType type = CropType.bySeed(stack.getType());
            if (type != null && this.ctx.enabledCrops().contains(type)) {
                return true;
            }
        }
        return false;
    }

    public boolean tryStartBread(ActiveGolem golem) {
        if (!this.ctx.settings().farmer.craftBread
                || !this.ctx.chestService().isCraftPresent(golem.data())
                || this.ctx.chestService().countItem(golem.data(), Material.WHEAT) < 3
                || !this.ctx.chestService().hasSpace(golem.data())) {
            return false;
        }
        golem.clearFetchFlags();
        golem.wanderTarget(null);
        golem.farmerState(FarmerState.MOVING_TO_CRAFT);
        return true;
    }

    public boolean tryPrioritizeFence(ActiveGolem golem) {
        GolemFenceWork.Phase phase = this.ctx.fenceWork().tryStart(
                golem,
                this.ctx.settings().yard.placeFence
        );
        return switch (phase) {
            case MOVING_FENCE, MOVING_CLEAR, CLEARING, PLACING_FENCE, MOVING_GATE, PLACING_GATE -> {
                golem.wanderTarget(null);
                golem.farmerState(switch (phase) {
                    case MOVING_CLEAR, CLEARING -> FarmerState.MOVING_TO_FENCE_CLEAR;
                    case MOVING_GATE, PLACING_GATE -> FarmerState.MOVING_TO_GATE;
                    default -> FarmerState.MOVING_TO_FENCE;
                });
                yield true;
            }
            case FETCH_FENCE, FETCH_GATE -> {
                golem.wanderTarget(null);
                golem.farmerState(FarmerState.MOVING_TO_CHEST);
                yield true;
            }
            default -> false;
        };
    }

    public boolean assignNextJob(ActiveGolem golem) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        GolemSettings.Farmer farmer = this.ctx.settings().farmer;
        GolemSettings.Yard yard = this.ctx.settings().yard;

        List<Block> mature = this.ctx.farmAreaService().matureCrops(golem.data(), radius, this.ctx.enabledCrops());
        if (!mature.isEmpty()) {
            long noticeTicks = Math.max(0L, farmer.harvestNoticeTicks);
            if (noticeTicks > 0L && golem.harvestNoticeUntil() <= 0L) {
                golem.armHarvestNotice(noticeTicks);
            }
            if (!golem.harvestNoticeReady()) {
                return false;
            }
            golem.clearFetchFlags();
            golem.wanderTarget(null);
            golem.targetCrop(this.ctx.farthestPlot(golem.data(), mature));
            golem.farmerState(FarmerState.MOVING_TO_HARVEST);
            return true;
        }
        golem.clearHarvestNotice();

        List<Block> weeds = this.ctx.farmAreaService().fieldWeeds(golem.data(), radius);
        if (!weeds.isEmpty()) {
            golem.clearFetchFlags();
            golem.wanderTarget(null);
            golem.targetCrop(weeds.get(0).getLocation());
            golem.farmerState(FarmerState.MOVING_TO_CLEAR);
            return true;
        }

        if (!golem.fieldReady() || !this.ctx.farmAreaService().tillableSoil(golem.data(), radius).isEmpty()) {
            golem.clearFetchFlags();
            golem.wanderTarget(null);
            golem.fieldReady(false);
            golem.farmerState(FarmerState.PREPARE_FIELD);
            return true;
        }

        if (tryStartPlant(golem)) {
            return true;
        }

        if (tryStartTorchJob(golem, radius)) {
            return true;
        }

        if (this.chest != null && this.chest.tryStartPlaceCraft(golem)) {
            return true;
        }

        if (tryStartBread(golem)) {
            return true;
        }

        if (this.compost != null) {
            if (this.compost.tryStartCollect(golem)) {
                return true;
            }
            if (this.compost.tryStartPlace(golem)) {
                return true;
            }
        }

        if (farmer.useBoneMeal && this.ctx.hasBoneMealWork(golem)) {
            int meal = this.compost == null ? this.ctx.chestService().countItem(golem.data(), Material.BONE_MEAL)
                    : this.compost.boneMealAvailable(golem);
            if (meal > 0) {
                this.ctx.requestBoneMealFromChest(golem);
                return true;
            }
            if (this.compost != null && this.compost.tryStartFill(golem)) {
                return true;
            }
            this.ctx.requestBoneMealFromChest(golem);
            return true;
        }

        if (this.compost != null && this.compost.tryStartFill(golem)) {
            return true;
        }

        if (yard.clearBorder && !this.ctx.farmAreaService().weedsToClear(golem.data(), radius).isEmpty()) {
            golem.clearFetchFlags();
            golem.wanderTarget(null);
            List<Block> junk = this.ctx.farmAreaService().weedsToClear(golem.data(), radius);
            golem.targetCrop(junk.get(0).getLocation());
            golem.farmerState(FarmerState.MOVING_TO_CLEAR);
            return true;
        }

        if (yard.placeSeat
                && !this.ctx.farmAreaService().hasValidSeat(golem.data())
                && this.ctx.farmAreaService().findSeatSpot(golem.data(), radius) != null) {
            Material stairs = FarmerCarried.carriedStairs(golem);
            if (stairs != null) {
                golem.clearFetchFlags();
                golem.wanderTarget(null);
                Block spot = this.ctx.farmAreaService().findSeatSpot(golem.data(), radius);
                golem.targetCrop(spot.getLocation());
                golem.farmerState(FarmerState.MOVING_TO_SEAT);
                return true;
            }
            if (this.ctx.chestService().findStairsInChest(golem.data()) != null) {
                golem.clearFetchFlags();
                golem.wanderTarget(null);
                golem.fetchingSeat(true);
                golem.farmerState(FarmerState.MOVING_TO_CHEST);
                return true;
            }
        }

        if (this.ctx.hasOpenPlantSpots(golem.data()) && !this.ctx.hasAnySeedInChest(golem.data())) {
            if (this.ctx.farmAreaService().hasFieldCrops(golem.data(), radius, this.ctx.enabledCrops())) {
                return false;
            }
            golem.clearFetchFlags();
            golem.wanderTarget(null);
            golem.targetCrop(null);
            golem.farmerState(FarmerState.WAITING_SEEDS);
            golem.data().lastActionAt(System.currentTimeMillis());
            return true;
        }

        return false;
    }

    public void maybeStartSupportJobs(ActiveGolem golem) {
        if (tryPrioritizeFence(golem)) {
            return;
        }
        assignNextJob(golem);
    }

    public boolean tryStartTorchJob(ActiveGolem golem) {
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        return tryStartTorchJob(golem, radius);
    }

    private boolean tryStartTorchJob(ActiveGolem golem, int radius) {
        Material torch = this.ctx.resolveTorch();
        if (!this.ctx.settings().yard.placeTorches
                || this.ctx.chestService().countItem(golem.data(), torch) <= 0
                || this.ctx.farmAreaService().perimeterTorchSpots(golem.data(), radius).isEmpty()) {
            return false;
        }
        golem.clearFetchFlags();
        golem.fetchingTorch(true);
        golem.targetCrop(null);
        golem.farmerState(FarmerState.MOVING_TO_CHEST);
        return true;
    }

    public void afterDeposit(ActiveGolem golem, CopperGolem copper) {
        golem.chestFullNotified(false);
        if (this.ctx.tryStartFeed(golem)) {
            return;
        }
        int radius = this.ctx.chestService().effectiveRadius(golem.data());
        if (!this.ctx.farmAreaService().matureCrops(golem.data(), radius, this.ctx.enabledCrops()).isEmpty()
                || !this.ctx.farmAreaService().fieldWeeds(golem.data(), radius).isEmpty()) {
            if (assignNextJob(golem)) {
                return;
            }
        }
        if (this.ctx.settings().yard.collectGroundLoot && this.ctx.groundLoot().hasLoot(golem.data())) {
            golem.clearFetchFlags();
            golem.fetchingSeed(false);
            golem.wanderTarget(null);
            golem.targetCrop(null);
            golem.farmerState(FarmerState.WAIT_GROWTH);
            golem.data().lastActionAt(System.currentTimeMillis());
            return;
        }
        if (this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            golem.clearFetchFlags();
            golem.fetchingSeed(false);
            golem.wanderTarget(null);
            golem.targetCrop(null);
            golem.farmerState(FarmerState.MOVING_TO_SEAT);
            golem.data().lastActionAt(System.currentTimeMillis());
            startSeatNow(golem, copper);
            return;
        }
        if (tryStartPlant(golem)) {
            return;
        }
        if (tryPrioritizeFence(golem)) {
            return;
        }
        if (assignNextJob(golem)) {
            return;
        }
        enterIdle(golem);
        startSeatNow(golem, copper);
    }

    private void startSeatNow(ActiveGolem golem, CopperGolem copper) {
        if (copper == null || !copper.isValid() || golem.farmerState() != FarmerState.MOVING_TO_SEAT) {
            return;
        }
        switch (this.ctx.seatWork().sitOnBench(golem, copper)) {
            case MOVING, PLACING -> golem.farmerState(FarmerState.MOVING_TO_SEAT);
            case SITTING -> golem.farmerState(FarmerState.SITTING);
            case UNAVAILABLE, DONE -> golem.farmerState(FarmerState.WAIT_GROWTH);
        }
    }

    public void startRest(ActiveGolem golem) {
        golem.restTicksLeft(0L);
        enterIdle(golem);
    }

    public void continueRest(ActiveGolem golem) {
        golem.restTicksLeft(0L);
        enterIdle(golem);
    }

    public void retryChest(ActiveGolem golem) {
        if (!golem.carried().isEmpty()) {
            if (this.ctx.chestService().hasSpace(golem.data())) {
                golem.chestFullNotified(false);
                golem.farmerState(FarmerState.MOVING_TO_CHEST);
            } else {
                golem.data().lastActionAt(System.currentTimeMillis());
            }
            return;
        }
        if (this.ctx.chestService().hasSpace(golem.data())) {
            golem.chestFullNotified(false);
            enterIdle(golem);
            return;
        }
        golem.data().lastActionAt(System.currentTimeMillis());
    }

    public void resumeAfterClear(ActiveGolem golem) {
        golem.targetCrop(null);
        if (!golem.carried().isEmpty()) {
            golem.farmerState(FarmerState.MOVING_TO_CHEST);
            return;
        }
        if (tryPrioritizeFence(golem)) {
            return;
        }
        if (assignNextJob(golem)) {
            return;
        }
        enterIdle(golem);
    }

    public void afterPlant(ActiveGolem golem) {
        if (assignNextJob(golem)) {
            return;
        }
        enterIdle(golem);
    }
}
