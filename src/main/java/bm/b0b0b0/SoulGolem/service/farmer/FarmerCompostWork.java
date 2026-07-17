package bm.b0b0b0.SoulGolem.service.farmer;

import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.CropType;
import bm.b0b0b0.SoulGolem.model.FarmerState;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.service.GolemGaze;
import bm.b0b0b0.SoulGolem.service.GolemMovement;
import bm.b0b0b0.SoulGolem.service.WorkAreaService;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.CopperGolem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class FarmerCompostWork {

    private final FarmerContext ctx;
    private final FarmerCarried carried;
    private final WorkAreaService workAreaService;
    private FarmerCycle cycle;

    public FarmerCompostWork(FarmerContext ctx, FarmerCarried carried, WorkAreaService workAreaService) {
        this.ctx = ctx;
        this.carried = carried;
        this.workAreaService = workAreaService;
    }

    public void wire(FarmerCycle cycle) {
        this.cycle = cycle;
    }

    public boolean hasValidComposter(SoulGolemData data) {
        return this.ctx.chestService().isCompostPresent(data);
    }

    public boolean isCompostReady(SoulGolemData data) {
        Block block = compostBlock(data);
        if (block == null || !(block.getBlockData() instanceof Levelled levelled)) {
            return false;
        }
        return levelled.getLevel() >= levelled.getMaximumLevel();
    }

    public boolean needsPlaceComposter(ActiveGolem golem) {
        Settings.Farmer farmer = this.ctx.settings().farmer;
        if (!farmer.useComposter || hasValidComposter(golem.data())) {
            return false;
        }
        return this.ctx.chestService().countItem(golem.data(), Material.COMPOSTER) > 0
                || FarmerCarried.countCarried(golem, Material.COMPOSTER) > 0;
    }

    public boolean hasFillWork(ActiveGolem golem) {
        Settings.Farmer farmer = this.ctx.settings().farmer;
        if (!farmer.useComposter || !hasValidComposter(golem.data()) || isCompostReady(golem.data())) {
            return false;
        }
        return excessSeedCount(golem) > 0 || excessCropCount(golem) > 0;
    }

    public boolean tryStartCollect(ActiveGolem golem) {
        Settings.Farmer farmer = this.ctx.settings().farmer;
        if (!farmer.useComposter || !hasValidComposter(golem.data()) || !isCompostReady(golem.data())) {
            return false;
        }
        golem.clearFetchFlags();
        golem.wanderTarget(null);
        Block block = compostBlock(golem.data());
        golem.targetCrop(block == null ? null : block.getLocation());
        golem.farmerState(FarmerState.MOVING_TO_COLLECT_COMPOST);
        return true;
    }

    public boolean tryStartPlace(ActiveGolem golem) {
        if (!needsPlaceComposter(golem)) {
            return false;
        }
        Location spot = this.ctx.chestService().findComposterLocation(golem.data());
        if (spot == null) {
            return false;
        }
        if (FarmerCarried.countCarried(golem, Material.COMPOSTER) > 0) {
            golem.clearFetchFlags();
            golem.wanderTarget(null);
            golem.targetCrop(spot);
            golem.farmerState(FarmerState.MOVING_TO_PLACE_COMPOSTER);
            return true;
        }
        golem.clearFetchFlags();
        golem.wanderTarget(null);
        golem.fetchingComposter(true);
        golem.targetCrop(spot);
        golem.farmerState(FarmerState.MOVING_TO_CHEST);
        return true;
    }

    public boolean tryStartFill(ActiveGolem golem) {
        if (!hasFillWork(golem)) {
            return false;
        }
        if (hasCompostableCarried(golem)) {
            golem.clearFetchFlags();
            golem.wanderTarget(null);
            Block block = compostBlock(golem.data());
            golem.targetCrop(block == null ? null : block.getLocation());
            golem.farmerState(FarmerState.MOVING_TO_COMPOST);
            return true;
        }
        golem.clearFetchFlags();
        golem.wanderTarget(null);
        golem.fetchingCompost(true);
        Block block = compostBlock(golem.data());
        golem.targetCrop(block == null ? null : block.getLocation());
        golem.farmerState(FarmerState.MOVING_TO_CHEST);
        return true;
    }

    public void takeComposterFromChest(ActiveGolem golem) {
        golem.clearFetchFlags();
        Location spot = this.ctx.chestService().findComposterLocation(golem.data());
        if (spot == null || this.ctx.chestService().countItem(golem.data(), Material.COMPOSTER) <= 0) {
            if (this.cycle != null) {
                this.cycle.assignNextJob(golem);
            }
            return;
        }
        if (!this.ctx.chestService().takeItem(golem.data(), Material.COMPOSTER, 1)) {
            if (this.cycle != null) {
                this.cycle.assignNextJob(golem);
            }
            return;
        }
        golem.carry(new ItemStack(Material.COMPOSTER, 1));
        golem.targetCrop(spot);
        golem.farmerState(FarmerState.MOVING_TO_PLACE_COMPOSTER);
        golem.markDirty();
    }

    public void takeCompostFillFromChest(ActiveGolem golem) {
        golem.clearFetchFlags();
        Settings.Farmer farmer = this.ctx.settings().farmer;
        int want = Math.max(1, farmer.compostItemsPerTrip);
        int taken = 0;
        taken += takeExcessSeeds(golem, want - taken);
        if (taken < want && shouldCompostCrops(golem)) {
            taken += takeExcessCrops(golem, want - taken);
        }
        if (taken <= 0 || !hasValidComposter(golem.data()) || isCompostReady(golem.data())) {
            this.carried.returnAllCarriedToChest(golem);
            if (this.cycle != null) {
                this.cycle.assignNextJob(golem);
            }
            return;
        }
        Block block = compostBlock(golem.data());
        golem.targetCrop(block == null ? null : block.getLocation());
        golem.farmerState(FarmerState.MOVING_TO_COMPOST);
        golem.markDirty();
    }

    public void continuePlace(ActiveGolem golem, CopperGolem copper) {
        if (!this.ctx.stationsOk(golem)) {
            return;
        }
        Location spot = golem.targetCrop();
        if (spot == null) {
            spot = this.ctx.chestService().findComposterLocation(golem.data());
            golem.targetCrop(spot);
        }
        if (spot == null || FarmerCarried.countCarried(golem, Material.COMPOSTER) <= 0) {
            this.carried.returnAllCarriedToChest(golem);
            if (this.cycle != null) {
                this.cycle.assignNextJob(golem);
            }
            return;
        }
        Location stand = this.ctx.farmAreaService().standBesideInside(golem.data(), spot.getBlock());
        if (stand == null) {
            stand = spot.clone().add(0.5D, 0.0D, 0.5D);
        }
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 2.25D) {
            golem.farmerState(FarmerState.MOVING_TO_PLACE_COMPOSTER);
            this.ctx.movement().walkTowards(copper, stand, golem);
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.farmerState(FarmerState.PLACING_COMPOSTER);
        GolemGaze.faceBlock(golem, spot.getBlock());
        this.ctx.chestService().clearStationColumn(spot);
        this.ctx.chestService().placeComposter(spot, golem.data().id());
        this.workAreaService.protect(spot.getBlock(), golem.data().id());
        golem.data().compostPosition(spot.getBlockX(), spot.getBlockY(), spot.getBlockZ());
        FarmerCarried.consumeCarried(golem, Material.COMPOSTER, 1);
        golem.targetCrop(null);
        golem.markDirty();
        if (this.cycle != null && this.cycle.assignNextJob(golem)) {
            return;
        }
        golem.farmerState(FarmerState.WAIT_GROWTH);
    }

    public void continueFill(ActiveGolem golem, CopperGolem copper) {
        if (!this.ctx.stationsOk(golem)) {
            return;
        }
        if (!hasValidComposter(golem.data()) || isCompostReady(golem.data()) || !hasCompostableCarried(golem)) {
            this.carried.returnAllCarriedToChest(golem);
            if (this.cycle != null) {
                this.cycle.assignNextJob(golem);
            }
            return;
        }
        Block block = compostBlock(golem.data());
        Location stand = this.ctx.chestService().compostStandLocation(golem.data());
        if (stand == null) {
            stand = block.getLocation().add(0.5D, 0.0D, 0.5D);
        }
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 2.25D) {
            golem.farmerState(FarmerState.MOVING_TO_COMPOST);
            this.ctx.movement().walkTowards(copper, stand, golem);
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.farmerState(FarmerState.COMPOSTING);
        GolemGaze.faceBlock(golem, block);
        Material material = firstCompostableCarried(golem);
        if (material == null) {
            this.carried.returnAllCarriedToChest(golem);
            if (this.cycle != null) {
                this.cycle.assignNextJob(golem);
            }
            return;
        }
        FarmerCarried.consumeCarried(golem, material, 1);
        tryCompost(block, material);
        golem.markDirty();
        if (isCompostReady(golem.data())) {
            this.carried.returnAllCarriedToChest(golem);
            golem.farmerState(FarmerState.MOVING_TO_COLLECT_COMPOST);
            golem.targetCrop(block.getLocation());
            return;
        }
        if (hasCompostableCarried(golem)) {
            return;
        }
        if (this.cycle != null && this.cycle.assignNextJob(golem)) {
            return;
        }
        golem.farmerState(FarmerState.WAIT_GROWTH);
    }

    public void continueCollect(ActiveGolem golem, CopperGolem copper) {
        if (!this.ctx.stationsOk(golem)) {
            return;
        }
        if (!hasValidComposter(golem.data()) || !isCompostReady(golem.data())) {
            if (this.cycle != null) {
                this.cycle.assignNextJob(golem);
            }
            return;
        }
        Block block = compostBlock(golem.data());
        Location stand = this.ctx.chestService().compostStandLocation(golem.data());
        if (stand == null) {
            stand = block.getLocation().add(0.5D, 0.0D, 0.5D);
        }
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 2.25D) {
            golem.farmerState(FarmerState.MOVING_TO_COLLECT_COMPOST);
            this.ctx.movement().walkTowards(copper, stand, golem);
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.farmerState(FarmerState.COLLECTING_COMPOST);
        GolemGaze.faceBlock(golem, block);
        if (!(block.getBlockData() instanceof Levelled levelled) || levelled.getLevel() < levelled.getMaximumLevel()) {
            if (this.cycle != null) {
                this.cycle.assignNextJob(golem);
            }
            return;
        }
        levelled.setLevel(0);
        block.setBlockData(levelled, false);
        ItemStack meal = new ItemStack(Material.BONE_MEAL, 1);
        if (!this.ctx.chestService().deposit(golem.data(), meal)) {
            golem.carry(meal);
            golem.farmerState(FarmerState.MOVING_TO_CHEST);
            golem.markDirty();
            return;
        }
        golem.targetCrop(null);
        golem.markDirty();
        if (this.cycle != null && this.cycle.assignNextJob(golem)) {
            return;
        }
        golem.farmerState(FarmerState.WAIT_GROWTH);
    }

    public int boneMealAvailable(ActiveGolem golem) {
        return this.ctx.chestService().countItem(golem.data(), Material.BONE_MEAL)
                + FarmerCarried.countCarried(golem, Material.BONE_MEAL);
    }

    public boolean shouldCompostCrops(ActiveGolem golem) {
        Settings.Farmer farmer = this.ctx.settings().farmer;
        if (!farmer.useBoneMeal || !this.ctx.hasBoneMealWork(golem)) {
            return false;
        }
        return boneMealAvailable(golem) < Math.max(0, farmer.compostWhenBoneMealBelow);
    }

    private int excessSeedCount(ActiveGolem golem) {
        Settings.Farmer farmer = this.ctx.settings().farmer;
        int reserve = Math.max(0, farmer.compostSeedReserve);
        if (this.ctx.hasOpenPlantSpots(golem.data())) {
            reserve = Math.max(reserve, Math.max(1, farmer.seedsPerTrip));
        }
        int total = 0;
        for (CropType type : this.ctx.enabledCrops()) {
            int count = this.ctx.chestService().countItem(golem.data(), type.seed());
            total += Math.max(0, count - reserve);
        }
        return total;
    }

    private int excessCropCount(ActiveGolem golem) {
        if (!shouldCompostCrops(golem)) {
            return 0;
        }
        Settings.Farmer farmer = this.ctx.settings().farmer;
        int keep = Math.max(0, farmer.compostCropKeep);
        int total = 0;
        for (CropType type : this.ctx.enabledCrops()) {
            Material product = harvestMaterial(type);
            if (product == null) {
                continue;
            }
            int count = this.ctx.chestService().countItem(golem.data(), product);
            int keepForType = keep;
            if (product == Material.WHEAT && farmer.craftBread
                    && this.ctx.chestService().isCraftPresent(golem.data())) {
                keepForType = Math.max(keepForType, 3);
            }
            total += Math.max(0, count - keepForType);
        }
        return total;
    }

    private int takeExcessSeeds(ActiveGolem golem, int max) {
        if (max <= 0) {
            return 0;
        }
        Settings.Farmer farmer = this.ctx.settings().farmer;
        int reserve = Math.max(0, farmer.compostSeedReserve);
        if (this.ctx.hasOpenPlantSpots(golem.data())) {
            reserve = Math.max(reserve, Math.max(1, farmer.seedsPerTrip));
        }
        int taken = 0;
        for (CropType type : this.ctx.enabledCrops()) {
            if (taken >= max) {
                break;
            }
            int count = this.ctx.chestService().countItem(golem.data(), type.seed());
            int excess = Math.max(0, count - reserve);
            int want = Math.min(excess, max - taken);
            if (want <= 0) {
                continue;
            }
            if (this.ctx.chestService().takeItem(golem.data(), type.seed(), want)) {
                golem.carry(new ItemStack(type.seed(), want));
                taken += want;
            }
        }
        return taken;
    }

    private int takeExcessCrops(ActiveGolem golem, int max) {
        if (max <= 0 || !shouldCompostCrops(golem)) {
            return 0;
        }
        Settings.Farmer farmer = this.ctx.settings().farmer;
        int keep = Math.max(0, farmer.compostCropKeep);
        int taken = 0;
        for (CropType type : this.ctx.enabledCrops()) {
            if (taken >= max) {
                break;
            }
            Material product = harvestMaterial(type);
            if (product == null) {
                continue;
            }
            int keepForType = keep;
            if (product == Material.WHEAT && farmer.craftBread
                    && this.ctx.chestService().isCraftPresent(golem.data())) {
                keepForType = Math.max(keepForType, 3);
            }
            int count = this.ctx.chestService().countItem(golem.data(), product);
            int excess = Math.max(0, count - keepForType);
            int want = Math.min(excess, max - taken);
            if (want <= 0) {
                continue;
            }
            if (this.ctx.chestService().takeItem(golem.data(), product, want)) {
                golem.carry(new ItemStack(product, want));
                taken += want;
            }
        }
        return taken;
    }

    private static Material harvestMaterial(CropType type) {
        return switch (type) {
            case WHEAT -> Material.WHEAT;
            case CARROT -> Material.CARROT;
            case POTATO -> Material.POTATO;
            case BEETROOT -> Material.BEETROOT;
            case PUMPKIN -> Material.PUMPKIN;
            case MELON -> Material.MELON_SLICE;
        };
    }

    private boolean hasCompostableCarried(ActiveGolem golem) {
        return firstCompostableCarried(golem) != null;
    }

    public boolean carryingCompostables(ActiveGolem golem) {
        return hasCompostableCarried(golem);
    }

    private Material firstCompostableCarried(ActiveGolem golem) {
        for (ItemStack stack : golem.carried()) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            if (compostChance(stack.getType()) > 0.0D) {
                return stack.getType();
            }
        }
        return null;
    }

    private static void tryCompost(Block block, Material material) {
        if (!(block.getBlockData() instanceof Levelled levelled)) {
            return;
        }
        if (levelled.getLevel() >= levelled.getMaximumLevel()) {
            return;
        }
        double chance = compostChance(material);
        if (chance <= 0.0D) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            levelled.setLevel(Math.min(levelled.getMaximumLevel(), levelled.getLevel() + 1));
            block.setBlockData(levelled, false);
        }
    }

    private static double compostChance(Material material) {
        if (material == null) {
            return 0.0D;
        }
        return switch (material) {
            case WHEAT_SEEDS, BEETROOT_SEEDS, PUMPKIN_SEEDS, MELON_SEEDS -> 0.3D;
            case MELON_SLICE -> 0.5D;
            case WHEAT, CARROT, POTATO, BEETROOT, PUMPKIN, MELON, POISONOUS_POTATO -> 0.65D;
            default -> 0.0D;
        };
    }

    private Block compostBlock(SoulGolemData data) {
        if (!data.hasCompostStation()) {
            return null;
        }
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return null;
        }
        return world.getBlockAt(
                (int) Math.floor(data.compostX()),
                (int) Math.floor(data.compostY()),
                (int) Math.floor(data.compostZ())
        );
    }
}
