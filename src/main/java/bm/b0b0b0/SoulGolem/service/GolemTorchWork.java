package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class GolemTorchWork {

    public enum Phase {
        DONE,
        MOVING,
        PLACING,
        FETCH
    }

    private final FarmAreaService farmAreaService;
    private final SoulChestService chestService;
    private final GolemMovement movement;

    public GolemTorchWork(
            FarmAreaService farmAreaService,
            SoulChestService chestService,
            GolemMovement movement
    ) {
        this.farmAreaService = farmAreaService;
        this.chestService = chestService;
        this.movement = movement;
    }

    public Phase tick(ActiveGolem golem, CopperGolem copper, Material torch, int maxTorches) {
        if (countCarried(golem, torch) <= 0) {
            golem.targetCrop(null);
            return Phase.DONE;
        }
        int radius = this.chestService.effectiveRadius(golem.data());
        Location target = golem.targetCrop();
        if (target == null) {
            List<Block> spots = this.farmAreaService.perimeterTorchSpots(golem.data(), radius, maxTorches);
            if (spots.isEmpty()) {
                returnMaterial(golem, torch);
                return Phase.DONE;
            }
            target = spots.get(0).getLocation();
            golem.targetCrop(target);
        }
        Location stand = target.clone().add(0.5D, 0.0D, 0.5D);
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 1.5D) {
            this.movement.walkTowards(copper, stand, golem);
            return Phase.MOVING;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        Block spot = target.getBlock();
        GolemGaze.faceBlock(golem, spot);
        if (spot.getType().isAir()) {
            this.farmAreaService.placeTorch(spot, torch, golem.data().id());
            consumeCarried(golem, torch, 1);
        }
        golem.targetCrop(null);
        golem.markDirty();
        if (countCarried(golem, torch) > 0) {
            List<Block> spots = this.farmAreaService.perimeterTorchSpots(golem.data(), radius, maxTorches);
            if (!spots.isEmpty()) {
                golem.targetCrop(spots.get(0).getLocation());
                return Phase.MOVING;
            }
            returnMaterial(golem, torch);
        }
        golem.data().lastActionAt(System.currentTimeMillis());
        return Phase.DONE;
    }

    public Phase takeFromChest(ActiveGolem golem, Material torch, int torchesPerTrip, int maxTorches) {
        int radius = this.chestService.effectiveRadius(golem.data());
        List<Block> spots = this.farmAreaService.perimeterTorchSpots(golem.data(), radius, maxTorches);
        if (spots.isEmpty() || this.chestService.countItem(golem.data(), torch) <= 0) {
            golem.clearFetchFlags();
            return Phase.DONE;
        }
        int want = Math.min(Math.max(1, torchesPerTrip), spots.size());
        want = Math.min(want, this.chestService.countItem(golem.data(), torch));
        if (want <= 0 || !this.chestService.takeItem(golem.data(), torch, want)) {
            golem.clearFetchFlags();
            return Phase.DONE;
        }
        golem.clearCarried();
        golem.carry(new ItemStack(torch, want));
        golem.clearFetchFlags();
        golem.targetCrop(spots.get(0).getLocation());
        golem.markDirty();
        return Phase.MOVING;
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

    private static int countCarried(ActiveGolem golem, Material material) {
        int total = 0;
        for (ItemStack stack : golem.carried()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
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
