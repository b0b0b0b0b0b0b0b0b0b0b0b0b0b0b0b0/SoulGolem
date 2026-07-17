package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class GolemFenceWork {

    public enum Phase {
        DISABLED,
        DONE,
        PAUSE,
        FETCH_FENCE,
        FETCH_GATE,
        MOVING_CLEAR,
        CLEARING,
        MOVING_FENCE,
        PLACING_FENCE,
        MOVING_GATE,
        PLACING_GATE
    }

    private final FarmAreaService farmAreaService;
    private final SoulChestService chestService;
    private final GolemMovement movement;

    public GolemFenceWork(
            FarmAreaService farmAreaService,
            SoulChestService chestService,
            GolemMovement movement
    ) {
        this.farmAreaService = farmAreaService;
        this.chestService = chestService;
        this.movement = movement;
    }

    public Phase tryStart(ActiveGolem golem, boolean enabled) {
        if (!enabled) {
            return Phase.DISABLED;
        }
        int radius = this.chestService.effectiveRadius(golem.data());
        Material fence = this.farmAreaService.resolveFenceMaterial(golem.data());
        Material gate = this.farmAreaService.resolveGateMaterial(golem.data());
        if (!this.farmAreaService.canProgressOuterFence(
                golem.data(), radius, countCarried(golem, fence), countCarried(golem, gate))) {
            return Phase.PAUSE;
        }
        golem.clearFetchFlags();
        if (countCarried(golem, fence) > 0 || countCarried(golem, gate) > 0) {
            return Phase.MOVING_FENCE;
        }
        if (this.farmAreaService.outerFenceSlots(golem.data(), radius).isEmpty()
                && this.farmAreaService.needsOuterFenceGate(golem.data(), radius)) {
            golem.fetchingGate(true);
            return Phase.FETCH_GATE;
        }
        golem.fetchingFence(true);
        return Phase.FETCH_FENCE;
    }

    public Phase tick(
            ActiveGolem golem,
            CopperGolem copper,
            boolean enabled,
            int fencesPerTrip,
            Consumer<ActiveGolem> onEnergy
    ) {
        if (!enabled) {
            returnUnused(golem);
            return Phase.DISABLED;
        }
        int radius = this.chestService.effectiveRadius(golem.data());
        Material fence = this.farmAreaService.resolveFenceMaterial(golem.data());
        Material gate = this.farmAreaService.resolveGateMaterial(golem.data());

        List<Block> junk = this.farmAreaService.outerFenceObstructions(golem.data(), radius);
        if (!junk.isEmpty()) {
            return clearJunk(golem, copper, junk, onEnergy);
        }

        List<Block> slots = this.farmAreaService.outerFenceSlots(golem.data(), radius);
        if (!slots.isEmpty()) {
            if (countCarried(golem, fence) <= 0) {
                if (this.chestService.countItem(golem.data(), fence) > 0) {
                    returnMaterial(golem, gate);
                    golem.clearFetchFlags();
                    golem.fetchingFence(true);
                    return Phase.FETCH_FENCE;
                }
                returnUnused(golem);
                golem.wanderTarget(null);
                return Phase.PAUSE;
            }
            return placePost(golem, copper, slots, fence, onEnergy);
        }

        if (this.farmAreaService.needsOuterFenceGate(golem.data(), radius)) {
            if (countCarried(golem, gate) <= 0) {
                if (this.chestService.countItem(golem.data(), gate) > 0) {
                    returnMaterial(golem, fence);
                    golem.clearFetchFlags();
                    golem.fetchingGate(true);
                    return Phase.FETCH_GATE;
                }
                returnUnused(golem);
                golem.wanderTarget(null);
                return Phase.PAUSE;
            }
            return placeGate(golem, copper, gate, onEnergy);
        }

        returnUnused(golem);
        golem.data().lastActionAt(System.currentTimeMillis());
        return Phase.DONE;
    }

    public Phase takeFromChest(ActiveGolem golem, int fencesPerTrip) {
        int radius = this.chestService.effectiveRadius(golem.data());
        Material fence = this.farmAreaService.resolveFenceMaterial(golem.data());
        Material gate = this.farmAreaService.resolveGateMaterial(golem.data());
        returnMaterial(golem, fence);
        returnMaterial(golem, gate);
        if (!this.farmAreaService.needsOuterFenceWork(golem.data(), radius)) {
            golem.clearFetchFlags();
            return Phase.DONE;
        }
        List<Block> slots = this.farmAreaService.outerFenceSlots(golem.data(), radius);
        boolean needGate = this.farmAreaService.needsOuterFenceGate(golem.data(), radius);
        int fenceWant = 0;
        if (!slots.isEmpty()) {
            fenceWant = Math.min(Math.max(1, fencesPerTrip), slots.size());
            fenceWant = Math.min(fenceWant, this.chestService.countItem(golem.data(), fence));
        }
        boolean takeGate = slots.isEmpty()
                && needGate
                && this.chestService.countItem(golem.data(), gate) > 0;
        if (fenceWant <= 0 && !takeGate) {
            golem.clearFetchFlags();
            return Phase.PAUSE;
        }
        if (fenceWant > 0 && this.chestService.takeItem(golem.data(), fence, fenceWant)) {
            golem.carry(new ItemStack(fence, fenceWant));
        }
        if (takeGate && this.chestService.takeItem(golem.data(), gate, 1)) {
            golem.carry(new ItemStack(gate, 1));
        }
        golem.clearFetchFlags();
        golem.markDirty();
        if (!this.farmAreaService.outerFenceObstructions(golem.data(), radius).isEmpty()) {
            return Phase.MOVING_CLEAR;
        }
        if (!slots.isEmpty() && countCarried(golem, fence) > 0) {
            golem.targetCrop(slots.get(0).getLocation());
            return Phase.MOVING_FENCE;
        }
        if (takeGate && countCarried(golem, gate) > 0) {
            return Phase.MOVING_GATE;
        }
        returnUnused(golem);
        return Phase.PAUSE;
    }

    public void returnUnused(ActiveGolem golem) {
        Material fence = this.farmAreaService.resolveFenceMaterial(golem.data());
        Material gate = this.farmAreaService.resolveGateMaterial(golem.data());
        returnMaterial(golem, fence);
        returnMaterial(golem, gate);
        golem.clearFetchFlags();
    }

    private Phase clearJunk(
            ActiveGolem golem,
            CopperGolem copper,
            List<Block> junk,
            Consumer<ActiveGolem> onEnergy
    ) {
        Location target = golem.targetCrop();
        Block block = target == null ? null : target.getBlock();
        if (block == null || block.getType().isAir() || !containsSameBlock(junk, block)) {
            block = pickNearest(copper.getLocation(), junk);
            golem.targetCrop(block.getLocation());
        }
        Block standAnchor = block.getY() > (int) Math.floor(golem.data().homeY())
                ? block.getRelative(0, -1, 0)
                : block;
        Location stand = this.farmAreaService.standOnBorderForFence(golem.data(), standAnchor);
        if (stand == null) {
            stand = this.farmAreaService.standForClear(block);
        }
        if (stand == null) {
            return Phase.MOVING_CLEAR;
        }
        if (!approachStand(golem, copper, stand)) {
            return Phase.MOVING_CLEAR;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        this.farmAreaService.clearOuterFenceObstruction(block, golem.data());
        if (onEnergy != null) {
            onEnergy.accept(golem);
        }
        golem.targetCrop(null);
        golem.markDirty();
        int radius = this.chestService.effectiveRadius(golem.data());
        List<Block> left = this.farmAreaService.outerFenceObstructions(golem.data(), radius);
        if (!left.isEmpty()) {
            golem.targetCrop(pickNearest(copper.getLocation(), left).getLocation());
            return Phase.MOVING_CLEAR;
        }
        return Phase.MOVING_FENCE;
    }

    private Phase placePost(
            ActiveGolem golem,
            CopperGolem copper,
            List<Block> slots,
            Material fence,
            Consumer<ActiveGolem> onEnergy
    ) {
        Location target = golem.targetCrop();
        Block spot = target == null ? null : target.getBlock();
        if (spot == null || !containsSameBlock(slots, spot)) {
            spot = slots.get(0);
            golem.targetCrop(spot.getLocation());
        }
        Location stand = this.farmAreaService.standOnBorderForFence(golem.data(), spot);
        if (stand == null) {
            return Phase.MOVING_FENCE;
        }
        if (!approachStand(golem, copper, stand)) {
            return Phase.MOVING_FENCE;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        this.farmAreaService.placeOuterFence(spot, fence, golem.data().id());
        consumeCarried(golem, fence, 1);
        if (onEnergy != null) {
            onEnergy.accept(golem);
        }
        golem.targetCrop(null);
        golem.markDirty();
        int radius = this.chestService.effectiveRadius(golem.data());
        List<Block> left = this.farmAreaService.outerFenceSlots(golem.data(), radius);
        if (!left.isEmpty() && countCarried(golem, fence) > 0) {
            golem.targetCrop(left.get(0).getLocation());
            Location nextStand = this.farmAreaService.standOnBorderForFence(golem.data(), left.get(0));
            if (nextStand != null) {
                approachStand(golem, copper, nextStand);
            }
            return Phase.MOVING_FENCE;
        }
        if (!left.isEmpty()) {
            golem.clearFetchFlags();
            golem.fetchingFence(true);
            return Phase.FETCH_FENCE;
        }
        if (this.farmAreaService.needsOuterFenceGate(golem.data(), radius)) {
            return Phase.MOVING_GATE;
        }
        returnUnused(golem);
        return Phase.DONE;
    }

    private Phase placeGate(
            ActiveGolem golem,
            CopperGolem copper,
            Material gate,
            Consumer<ActiveGolem> onEnergy
    ) {
        int radius = this.chestService.effectiveRadius(golem.data());
        Block spot = this.farmAreaService.outerFenceGateSpot(golem.data(), radius);
        if (spot == null) {
            returnUnused(golem);
            return Phase.DONE;
        }
        golem.targetCrop(spot.getLocation());
        Location stand = this.farmAreaService.standOnBorderForFence(golem.data(), spot);
        if (stand == null) {
            return Phase.MOVING_GATE;
        }
        if (!approachStand(golem, copper, stand)) {
            return Phase.MOVING_GATE;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        this.farmAreaService.placeOuterGate(golem.data(), spot, gate, golem.data().id());
        consumeCarried(golem, gate, 1);
        if (onEnergy != null) {
            onEnergy.accept(golem);
        }
        golem.targetCrop(null);
        golem.markDirty();
        returnUnused(golem);
        golem.data().lastActionAt(System.currentTimeMillis());
        return Phase.DONE;
    }

    private boolean approachStand(ActiveGolem golem, CopperGolem copper, Location stand) {
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) <= 2.25D) {
            return true;
        }
        Location before = copper.getLocation().clone();
        this.movement.walkTowards(copper, stand, golem);
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) <= 2.25D) {
            return true;
        }
        if (GolemMovement.horizontalDistanceSquared(before, copper.getLocation()) < 0.04D) {
            copper.teleport(stand);
            copper.setVelocity(new Vector(0, 0, 0));
            return GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) <= 2.25D;
        }
        return false;
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

    private static boolean containsSameBlock(List<Block> list, Block block) {
        for (Block entry : list) {
            if (entry.getWorld().equals(block.getWorld())
                    && entry.getX() == block.getX()
                    && entry.getY() == block.getY()
                    && entry.getZ() == block.getZ()) {
                return true;
            }
        }
        return false;
    }

    private static Block pickNearest(Location from, List<Block> list) {
        Block best = list.get(0);
        double bestDist = Double.MAX_VALUE;
        for (Block block : list) {
            double dx = block.getX() + 0.5D - from.getX();
            double dz = block.getZ() + 0.5D - from.getZ();
            double dist = dx * dx + dz * dz;
            if (dist < bestDist) {
                bestDist = dist;
                best = block;
            }
        }
        return best;
    }
}
