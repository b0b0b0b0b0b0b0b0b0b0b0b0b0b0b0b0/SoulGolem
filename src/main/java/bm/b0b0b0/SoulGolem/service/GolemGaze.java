package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import java.util.Collection;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public final class GolemGaze {

    private static final double SIT_LOOK_RANGE = 12.0D;

    private GolemGaze() {
    }

    public static void faceEntity(ActiveGolem golem, Entity entity) {
        if (golem == null || entity == null || !entity.isValid()) {
            return;
        }
        golem.gazePoint(null);
        golem.gazeEntityId(entity.getUniqueId());
    }

    public static void face(ActiveGolem golem, Location point) {
        if (golem == null || point == null || point.getWorld() == null) {
            return;
        }
        golem.gazeEntityId(null);
        golem.gazePoint(point.clone());
    }

    public static void faceBlock(ActiveGolem golem, Block block) {
        if (golem == null || block == null) {
            return;
        }
        face(golem, block.getLocation().add(0.5D, 0.55D, 0.5D));
    }

    public static void faceBlockTop(ActiveGolem golem, Block block) {
        if (golem == null || block == null) {
            return;
        }
        face(golem, block.getLocation().add(0.5D, 1.0D, 0.5D));
    }

    public static void faceSitAudience(ActiveGolem golem, LivingEntity copper) {
        if (golem == null || copper == null || !copper.isValid()) {
            return;
        }
        Player owner = Bukkit.getPlayer(golem.data().ownerUuid());
        if (owner != null
                && owner.isValid()
                && !owner.isDead()
                && owner.getWorld().equals(copper.getWorld())
                && owner.getLocation().distanceSquared(copper.getLocation()) <= SIT_LOOK_RANGE * SIT_LOOK_RANGE) {
            faceEntity(golem, owner);
            return;
        }
        Collection<Entity> nearby = copper.getNearbyEntities(SIT_LOOK_RANGE, SIT_LOOK_RANGE, SIT_LOOK_RANGE);
        Player closest = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : nearby) {
            if (!(entity instanceof Player player) || !player.isValid() || player.isDead()) {
                continue;
            }
            double dist = player.getLocation().distanceSquared(copper.getLocation());
            if (dist < best) {
                best = dist;
                closest = player;
            }
        }
        if (closest != null) {
            faceEntity(golem, closest);
            return;
        }
        clear(golem);
    }

    public static void clear(ActiveGolem golem) {
        if (golem == null) {
            return;
        }
        golem.gazeEntityId(null);
        golem.gazePoint(null);
    }

    public static void dropPlayerGazeUnlessSitting(ActiveGolem golem, boolean sitting) {
        if (golem == null || sitting) {
            return;
        }
        UUID id = golem.gazeEntityId();
        if (id == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(id);
        if (entity instanceof Player) {
            golem.gazeEntityId(null);
        }
    }
}
