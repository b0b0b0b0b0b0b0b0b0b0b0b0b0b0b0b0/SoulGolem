package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import java.util.List;
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
        return this.farmAreaService.isWorldStorming(
                org.bukkit.Bukkit.getWorld(data.worldName())
        );
    }

    /** Only when the golem is actually getting wet and already has a bench. */
    public boolean shouldSeekShelter(CopperGolem copper, boolean enabled) {
        return enabled && this.farmAreaService.isRainedOn(copper);
    }

    public boolean shouldSeekShelter(ActiveGolem golem, CopperGolem copper, boolean enabled) {
        return enabled
                && golem != null
                && this.farmAreaService.hasValidSeat(golem.data())
                && this.farmAreaService.isRainedOn(copper);
    }

    /** Stay under roof while storm still wets open sky at home (golem itself is dry under canopy). */
    public boolean shouldStaySheltered(ActiveGolem golem, CopperGolem copper) {
        if (golem == null || copper == null || !copper.isValid()) {
            return false;
        }
        if (!this.farmAreaService.hasValidSeat(golem.data())) {
            return false;
        }
        World world = copper.getWorld();
        if (!this.farmAreaService.isWorldStorming(world)) {
            return false;
        }
        Location probe = this.farmAreaService.rainOpenSkyProbe(golem.data(), world);
        return probe != null && this.farmAreaService.isRainedOn(probe);
    }

    public boolean shouldContinueShelter(ActiveGolem golem, CopperGolem copper) {
        return this.farmAreaService.isRainedOn(copper) || shouldStaySheltered(golem, copper);
    }

    public Phase tick(ActiveGolem golem, CopperGolem copper, boolean enabled) {
        if (!enabled) {
            return Phase.DISABLED;
        }
        if (!this.farmAreaService.hasValidSeat(golem.data())) {
            return Phase.UNAVAILABLE;
        }
        Location stand = this.farmAreaService.rainShelterStand(golem.data());
        if (stand == null || stand.getWorld() == null) {
            return Phase.UNAVAILABLE;
        }

        if (!shouldContinueShelter(golem, copper)) {
            golem.data().lastActionAt(System.currentTimeMillis());
            return Phase.DONE;
        }

        List<Block> missing = this.farmAreaService.missingRainShelterBlocks(golem.data());
        if (!missing.isEmpty()) {
            if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 2.25D) {
                this.movement.walkTowards(copper, stand, golem);
                return Phase.MOVING;
            }
            this.movement.stop(copper);
            copper.setVelocity(new Vector(0, 0, 0));
            Block first = missing.get(0);
            GolemGaze.faceBlock(golem, first);
            for (Block block : missing) {
                this.farmAreaService.placeRainShelterBlock(block, golem.data().id());
            }
            golem.markDirty();
            return Phase.BUILDING;
        }

        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 2.25D) {
            this.movement.walkTowards(copper, stand, golem);
            return Phase.MOVING;
        }

        parkUnderShelter(golem, copper, stand);
        return Phase.SHELTERING;
    }

    private void parkUnderShelter(ActiveGolem golem, CopperGolem copper, Location stand) {
        GolemAiMode.disable(copper, this.movement);
        Location sit = stand.clone();
        Location look = this.workAreaService.homeLocation(golem.data());
        if (look != null) {
            sit.setYaw(GolemMovement.yawTo(sit, look));
            GolemGaze.face(golem, look.clone().add(0.0D, 1.0D, 0.0D));
        }
        sit.setPitch(0.0F);
        GolemTeleport.park(copper, sit);
        GolemGazeService.forceLook(copper, golem);
        golem.data().lastActionAt(System.currentTimeMillis());
    }
}
