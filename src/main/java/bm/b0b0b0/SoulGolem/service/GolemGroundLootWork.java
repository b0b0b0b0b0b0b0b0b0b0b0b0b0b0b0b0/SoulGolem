package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class GolemGroundLootWork {

    public enum Phase {
        NONE,
        MOVING,
        PICKED
    }

    private static final double REACH_SQ = 2.25D;

    private final SoulChestService chestService;
    private final GolemMovement movement;

    public GolemGroundLootWork(SoulChestService chestService, GolemMovement movement) {
        this.chestService = chestService;
        this.movement = movement;
    }

    public Phase tick(ActiveGolem golem, CopperGolem copper, boolean enabled) {
        if (!enabled || busyFetching(golem)) {
            return Phase.NONE;
        }
        int radius = this.chestService.effectiveRadius(golem.data());
        Item loot = findNearest(golem.data(), radius, copper.getLocation());
        if (loot == null) {
            return Phase.NONE;
        }
        Location at = loot.getLocation();
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), at) > REACH_SQ) {
            golem.wanderTarget(null);
            this.movement.walkTowards(copper, at, golem);
            copper.setVelocity(new Vector(0, copper.getVelocity().getY(), 0));
            return Phase.MOVING;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        ItemStack stack = loot.getItemStack();
        if (stack != null && !stack.isEmpty()) {
            golem.carry(stack);
            golem.markDirty();
        }
        loot.remove();
        return Phase.PICKED;
    }

    public boolean hasLoot(SoulGolemData data) {
        int radius = this.chestService.effectiveRadius(data);
        Location home = homeCenter(data);
        if (home == null) {
            return false;
        }
        return findNearest(data, radius, home) != null;
    }

    public boolean inTerritory(SoulGolemData data, Location location, int radius) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().getName().equals(data.worldName())) {
            return false;
        }
        int homeX = (int) Math.floor(data.homeX());
        int homeY = (int) Math.floor(data.homeY());
        int homeZ = (int) Math.floor(data.homeZ());
        int maxR = Math.max(1, radius) + 1;
        int dx = Math.abs(location.getBlockX() - homeX);
        int dz = Math.abs(location.getBlockZ() - homeZ);
        if (dx > maxR || dz > maxR) {
            return false;
        }
        return Math.abs(location.getY() - (homeY + 1.0D)) <= 3.5D;
    }

    private Item findNearest(SoulGolemData data, int radius, Location from) {
        Location home = homeCenter(data);
        if (home == null || from == null || from.getWorld() == null) {
            return null;
        }
        World world = home.getWorld();
        if (world == null) {
            return null;
        }
        int maxR = Math.max(1, radius) + 1;
        double box = maxR + 1.5D;
        Item best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : world.getNearbyEntities(home, box, 4.0D, box)) {
            if (!(entity instanceof Item item)) {
                continue;
            }
            ItemStack stack = item.getItemStack();
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (item.getPickupDelay() > 40) {
                continue;
            }
            Location at = item.getLocation();
            if (!inTerritory(data, at, radius)) {
                continue;
            }
            double dist = GolemMovement.horizontalDistanceSquared(from, at);
            if (dist < bestDist) {
                bestDist = dist;
                best = item;
            }
        }
        return best;
    }

    private static Location homeCenter(SoulGolemData data) {
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                Math.floor(data.homeX()) + 0.5D,
                Math.floor(data.homeY()) + 1.0D,
                Math.floor(data.homeZ()) + 0.5D
        );
    }

    private static boolean busyFetching(ActiveGolem golem) {
        return golem.fetchingSeed()
                || golem.fetchingTorch()
                || golem.fetchingSeat()
                || golem.fetchingFence()
                || golem.fetchingGate()
                || golem.fetchingBoneMeal()
                || golem.fetchingFeed()
                || golem.fetchingWeapon();
    }
}
