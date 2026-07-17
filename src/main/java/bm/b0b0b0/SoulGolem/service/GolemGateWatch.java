package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.util.Vector;

/**
 * Ленивое закрытие калитки: увидел открытую → ждёт delay → идёт закрывает.
 */
public final class GolemGateWatch {

    public enum Phase {
        IDLE,
        MOVING,
        CLOSING,
        DONE
    }

    private final FarmAreaService farmAreaService;
    private final SoulChestService chestService;
    private final GolemMovement movement;

    public GolemGateWatch(
            FarmAreaService farmAreaService,
            SoulChestService chestService,
            GolemMovement movement
    ) {
        this.farmAreaService = farmAreaService;
        this.chestService = chestService;
        this.movement = movement;
    }

    /** Дешёвый peek: открыта дольше delayMs? */
    public boolean shouldStartClose(ActiveGolem golem, boolean enabled, long delayMs) {
        if (!enabled) {
            golem.gateOpenSeenAt(0L);
            return false;
        }
        if (!this.farmAreaService.isOuterGateOpen(golem.data())) {
            golem.gateOpenSeenAt(0L);
            return false;
        }
        long now = System.currentTimeMillis();
        long seen = golem.gateOpenSeenAt();
        if (seen <= 0L) {
            golem.gateOpenSeenAt(now);
            return false;
        }
        return now - seen >= Math.max(0L, delayMs);
    }

    public Phase tickClose(ActiveGolem golem, CopperGolem copper) {
        Block gate = this.farmAreaService.outerFenceGateSpot(
                golem.data(),
                this.chestService.effectiveRadius(golem.data())
        );
        if (gate == null || !this.farmAreaService.isOuterGateOpen(golem.data())) {
            golem.gateOpenSeenAt(0L);
            return Phase.DONE;
        }

        Location stand = this.farmAreaService.standOnBorderForFence(golem.data(), gate);
        if (stand == null) {
            stand = this.farmAreaService.standBeside(gate);
        }
        if (stand == null) {
            stand = gate.getLocation().add(0.5D, 0.0D, 0.5D);
        }

        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 2.25D) {
            this.movement.walkTowards(copper, stand, golem);
            return Phase.MOVING;
        }

        copper.setVelocity(new Vector(0, 0, 0));
        this.farmAreaService.closeOuterGate(gate);
        golem.gateOpenSeenAt(0L);
        golem.data().lastActionAt(System.currentTimeMillis());
        return Phase.CLOSING;
    }
}
