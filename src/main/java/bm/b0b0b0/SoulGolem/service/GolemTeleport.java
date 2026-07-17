package bm.b0b0b0.SoulGolem.service;

import io.papermc.paper.entity.TeleportFlag;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

public final class GolemTeleport {

    private GolemTeleport() {
    }

    public static boolean park(CopperGolem copper, Location to) {
        if (copper == null || !copper.isValid() || to == null || to.getWorld() == null) {
            return false;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        copper.setNoPhysics(true);
        copper.setGravity(false);
        copper.setCollidable(false);

        Location dest = to.clone();
        if (copper.teleport(dest, TeleportFlag.EntityState.RETAIN_PASSENGERS)) {
            return near(copper, dest);
        }

        List<Entity> passengers = new ArrayList<>(copper.getPassengers());
        for (Entity passenger : passengers) {
            copper.removePassenger(passenger);
        }
        boolean ok = copper.teleport(dest);
        for (Entity passenger : passengers) {
            if (passenger.isValid() && !passenger.isDead()) {
                copper.addPassenger(passenger);
            }
        }
        if (ok || near(copper, dest)) {
            return true;
        }

        return false;
    }

    private static boolean near(CopperGolem copper, Location dest) {
        Location at = copper.getLocation();
        if (at.getWorld() == null || dest.getWorld() == null || !at.getWorld().equals(dest.getWorld())) {
            return false;
        }
        return GolemMovement.horizontalDistanceSquared(at, dest) <= 0.09D
                && Math.abs(at.getY() - dest.getY()) <= 0.75D;
    }
}
