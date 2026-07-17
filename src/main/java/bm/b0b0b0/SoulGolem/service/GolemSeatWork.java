package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class GolemSeatWork {

    public enum Phase {
        UNAVAILABLE,
        MOVING,
        PLACING,
        SITTING,
        DONE
    }

    private final FarmAreaService farmAreaService;
    private final SoulChestService chestService;
    private final WorkAreaService workAreaService;
    private final GolemMovement movement;

    public GolemSeatWork(
            FarmAreaService farmAreaService,
            SoulChestService chestService,
            WorkAreaService workAreaService,
            GolemMovement movement
    ) {
        this.farmAreaService = farmAreaService;
        this.chestService = chestService;
        this.workAreaService = workAreaService;
        this.movement = movement;
    }

    public Phase placeCarriedSeat(ActiveGolem golem, CopperGolem copper, Material stairs, int radius) {
        Location target = golem.targetCrop();
        if (target == null || !canPlaceSeatAt(target.getBlock())) {
            Block spot = this.farmAreaService.findSeatSpot(golem.data(), radius);
            if (spot == null) {
                returnMaterial(golem, stairs);
                golem.targetCrop(null);
                return Phase.UNAVAILABLE;
            }
            target = spot.getLocation();
            golem.targetCrop(target);
        }
        Block seatSpot = target.getBlock();
        Block blockingTorch = this.farmAreaService.findTorchBlockingSeat(seatSpot);
        Location stand = blockingTorch != null
                ? blockingTorch.getLocation().add(0.5D, 0.0D, 0.5D)
                : this.farmAreaService.standBeside(seatSpot);
        if (stand == null) {
            stand = seatSpot.getLocation().add(0.5D, 0.0D, 0.5D);
        }
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 2.25D) {
            this.movement.walkTowards(copper, stand, golem);
            return Phase.MOVING;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        this.farmAreaService.relocateTorchForSeat(golem.data(), seatSpot, radius, golem.data().id());
        if (!this.farmAreaService.placeSeat(golem.data(), seatSpot, stairs, golem.data().id())) {
            returnMaterial(golem, stairs);
            golem.targetCrop(null);
            return Phase.UNAVAILABLE;
        }
        consumeCarried(golem, stairs, 1);
        golem.targetCrop(null);
        golem.markDirty();
        return Phase.PLACING;
    }

    public Phase sitOnBench(ActiveGolem golem, CopperGolem copper) {
        if (!this.farmAreaService.hasValidSeat(golem.data())) {
            return Phase.UNAVAILABLE;
        }
        Location seatStand = this.farmAreaService.seatStandLocation(golem.data());
        if (seatStand == null) {
            return Phase.UNAVAILABLE;
        }
        Block seatBlock = this.farmAreaService.seatBlock(golem.data());
        Location approach = seatBlock != null
                ? this.farmAreaService.standBeside(seatBlock)
                : null;
        if (approach == null) {
            approach = seatStand.clone();
            approach.setY(Math.floor(golem.data().homeY()) + 1.0D);
        }
        boolean seated = this.farmAreaService.isSeatedOnOwnBench(copper.getLocation(), golem.data())
                && GolemMovement.horizontalDistanceSquared(copper.getLocation(), seatStand) <= 0.35D;
        if (!seated) {
            if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), approach) > 1.2D) {
                this.movement.walkTowards(copper, approach, golem);
                return Phase.MOVING;
            }
            copper.setVelocity(new Vector(0, 0, 0));
            Location sit = seatStand.clone();
            Location look = this.workAreaService.homeLocation(golem.data());
            if (look != null) {
                sit.setYaw(GolemMovement.yawTo(sit, look));
            }
            sit.setPitch(0.0F);
            copper.teleport(sit);
            if (look != null) {
                copper.lookAt(look.clone().add(0.0D, 1.0D, 0.0D));
            }
        }
        golem.data().lastActionAt(System.currentTimeMillis());
        return Phase.SITTING;
    }

    public static Material carriedStairs(ActiveGolem golem) {
        for (ItemStack stack : golem.carried()) {
            if (stack != null && Tag.STAIRS.isTagged(stack.getType())) {
                return stack.getType();
            }
        }
        return null;
    }

    public static boolean canPlaceSeatAt(Block spot) {
        if (spot == null) {
            return false;
        }
        Material type = spot.getType();
        return type.isAir()
                || FarmAreaService.isVegetation(type)
                || Tag.STAIRS.isTagged(type)
                || type == Material.TORCH
                || type == Material.WALL_TORCH
                || type == Material.SOUL_TORCH
                || type == Material.SOUL_WALL_TORCH;
    }

    private void returnMaterial(ActiveGolem golem, Material material) {
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack stack : golem.carried()) {
            if (stack != null && stack.getType() == material) {
                this.chestService.deposit(golem.data(), stack.clone());
            } else if (stack != null) {
                leftover.add(stack.clone());
            }
        }
        golem.clearCarried();
        for (ItemStack stack : leftover) {
            golem.carry(stack);
        }
    }

    private static void consumeCarried(ActiveGolem golem, Material material, int amount) {
        int left = amount;
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack stack : golem.carried()) {
            if (stack == null) {
                continue;
            }
            if (left > 0 && stack.getType() == material) {
                int take = Math.min(left, stack.getAmount());
                left -= take;
                int remain = stack.getAmount() - take;
                if (remain > 0) {
                    ItemStack copy = stack.clone();
                    copy.setAmount(remain);
                    leftover.add(copy);
                }
                continue;
            }
            leftover.add(stack.clone());
        }
        golem.clearCarried();
        for (ItemStack stack : leftover) {
            golem.carry(stack);
        }
    }
}
