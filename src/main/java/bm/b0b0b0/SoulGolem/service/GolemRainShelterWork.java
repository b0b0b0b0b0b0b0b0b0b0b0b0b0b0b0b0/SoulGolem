package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.util.Vector;

public final class GolemRainShelterWork {

    public enum Phase {
        DISABLED,
        UNAVAILABLE,
        MOVING,
        BUILDING,
        SHELTERING,
        DONE
    }

    private final FarmAreaService farmAreaService;
    private final WorkAreaService workAreaService;
    private final GolemMovement movement;

    public GolemRainShelterWork(
            FarmAreaService farmAreaService,
            WorkAreaService workAreaService,
            GolemMovement movement
    ) {
        this.farmAreaService = farmAreaService;
        this.workAreaService = workAreaService;
        this.movement = movement;
    }

    public boolean isStorming(SoulGolemData data) {
        if (data == null) {
            return false;
        }
        return this.farmAreaService.isWorldStorming(Bukkit.getWorld(data.worldName()));
    }

    public boolean shouldSeekShelter(CopperGolem copper, boolean enabled) {
        return enabled && copper != null && copper.isValid() && copper.isInRain();
    }

    public boolean shouldSeekShelter(ActiveGolem golem, CopperGolem copper, boolean enabled) {
        return enabled && golem != null && isStorming(golem.data());
    }

    public Phase tick(ActiveGolem golem, CopperGolem copper, boolean enabled) {
        if (!enabled) {
            return Phase.DISABLED;
        }
        Location stand = this.farmAreaService.rainShelterStand(golem.data());
        if (stand == null || stand.getWorld() == null) {
            return Phase.UNAVAILABLE;
        }

        if (!this.farmAreaService.isWorldStorming(stand.getWorld())) {
            golem.data().lastActionAt(System.currentTimeMillis());
            return Phase.DONE;
        }

        List<Block> missing = this.farmAreaService.missingRainShelterBlocks(golem.data());
        if (!missing.isEmpty()) {
            if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 2.25D) {
                this.movement.walkTowards(copper, stand, golem);
                return Phase.MOVING;
            }
            copper.setVelocity(new Vector(0, 0, 0));
            for (Block block : missing) {
                this.farmAreaService.placeRainShelterBlock(block, golem.data().id());
            }
            golem.markDirty();
            return Phase.BUILDING;
        }

        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 0.85D) {
            this.movement.walkTowards(copper, stand, golem);
            return Phase.MOVING;
        }

        copper.setVelocity(new Vector(0, 0, 0));
        Location look = this.workAreaService.homeLocation(golem.data());
        Location sit = stand.clone();
        if (look != null) {
            sit.setYaw(GolemMovement.yawTo(sit, look));
        }
        sit.setPitch(0.0F);
        copper.teleport(sit);
        golem.data().lastActionAt(System.currentTimeMillis());
        return Phase.SHELTERING;
    }
}
