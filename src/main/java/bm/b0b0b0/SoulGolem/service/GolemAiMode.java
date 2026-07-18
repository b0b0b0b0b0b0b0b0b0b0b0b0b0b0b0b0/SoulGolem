package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import org.bukkit.Bukkit;
import org.bukkit.entity.CopperGolem;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

public final class GolemAiMode {

    private GolemAiMode() {
    }

    public static boolean isParked(ActiveGolem golem) {
        if (golem == null || golem.data().paused()) {
            return true;
        }
        if (golem.data().type() == bm.b0b0b0.SoulGolem.model.GolemType.FARMER) {
            return switch (golem.farmerState()) {
                case WAITING_SEEDS, WAITING_CHEST, RESTING, SITTING, SHELTERING -> true;
                default -> false;
            };
        }
        if (golem.data().type() == bm.b0b0b0.SoulGolem.model.GolemType.DIGGER) {
            return switch (golem.diggerState()) {
                case WAITING_CHEST, RESTING, SITTING, SHELTERING, DONE -> true;
                default -> false;
            };
        }
        return switch (golem.state()) {
            case WAITING_CHEST, RESTING, SITTING, SHELTERING -> true;
            default -> false;
        };
    }

    public static void disable(CopperGolem copper, GolemMovement movement) {
        if (copper == null || !copper.isValid()) {
            return;
        }
        copper.setAI(false);
        copper.setAware(true);
        copper.setGravity(false);
        copper.setCollidable(true);
        copper.setNoPhysics(false);
        Bukkit.getMobGoals().removeAllGoals(copper);
        if (movement != null) {
            movement.stop(copper);
        } else {
            copper.getPathfinder().stopPathfinding();
        }
        copper.setVelocity(new Vector(0, 0, 0));
    }

    public static void enable(
            Plugin plugin,
            CopperGolem copper,
            GolemRegistry registry,
            PluginKeys keys
    ) {
        if (copper == null || !copper.isValid()) {
            return;
        }
        copper.setNoPhysics(false);
        copper.setGravity(true);
        copper.setCollidable(true);
        copper.setAI(true);
        copper.setAware(true);
    }

    public static void sync(
            Plugin plugin,
            CopperGolem copper,
            ActiveGolem golem,
            GolemRegistry registry,
            PluginKeys keys,
            GolemMovement movement
    ) {
        if (copper == null || !copper.isValid() || golem == null) {
            return;
        }
        if (isParked(golem)) {
            disable(copper, movement);
        } else {
            enable(plugin, copper, registry, keys);
        }
        GolemGazeService.forceLook(copper, golem);
    }
}
