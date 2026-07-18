package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.DiggerState;
import bm.b0b0b0.SoulGolem.model.FarmerState;
import bm.b0b0b0.SoulGolem.model.GolemType;
import bm.b0b0b0.SoulGolem.model.MinerState;
import bm.b0b0b0.SoulGolem.scheduler.PluginSchedulers;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

public final class GolemGazeService {

    private final Plugin plugin;
    private final GolemRegistry registry;
    private final Map<UUID, ScheduledTask> tasks = new ConcurrentHashMap<>();

    public GolemGazeService(Plugin plugin, GolemRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void ensure(CopperGolem copper) {
        if (copper == null || !copper.isValid()) {
            return;
        }
        UUID entityId = copper.getUniqueId();
        if (this.tasks.containsKey(entityId)) {
            return;
        }
        ScheduledTask task = PluginSchedulers.runTimer(this.plugin, copper, () -> tick(copper), 1L, 1L);
        this.tasks.put(entityId, task);
    }

    public void stop(UUID entityUuid) {
        if (entityUuid == null) {
            return;
        }
        ScheduledTask task = this.tasks.remove(entityUuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void shutdown() {
        for (ScheduledTask task : this.tasks.values()) {
            task.cancel();
        }
        this.tasks.clear();
    }

    private void tick(CopperGolem copper) {
        if (copper == null || !copper.isValid() || copper.isDead()) {
            if (copper != null) {
                stop(copper.getUniqueId());
            }
            return;
        }
        this.registry.byEntity(copper.getUniqueId()).ifPresent(golem -> forceLook(copper, golem));
    }

    public static void forceLook(CopperGolem copper, ActiveGolem golem) {
        if (copper == null || !copper.isValid() || golem == null) {
            return;
        }
        Location target = resolveTarget(copper, golem);
        if (target == null || target.getWorld() == null) {
            return;
        }
        if (copper.getWorld() == null || !copper.getWorld().equals(target.getWorld())) {
            return;
        }

        boolean sitting = isSitting(golem);
        if (sitting || GolemAiMode.isParked(golem)) {
            if (copper.getPathfinder().hasPath()) {
                copper.getPathfinder().stopPathfinding();
            }
            copper.setVelocity(new Vector(0, 0, 0));
            Location eyes = copper.getEyeLocation();
            double dx = target.getX() - eyes.getX();
            double dy = target.getY() - eyes.getY();
            double dz = target.getZ() - eyes.getZ();
            double horiz = Math.sqrt(dx * dx + dz * dz);
            if (horiz * horiz + dy * dy < 1.0E-6D) {
                return;
            }
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.max(1.0E-3D, horiz)));
            if (pitch < -90.0F) {
                pitch = -90.0F;
            } else if (pitch > 90.0F) {
                pitch = 90.0F;
            }
            copper.setRotation(yaw, pitch);
            return;
        }

        if (copper.getPathfinder().hasPath()) {
            if (copper.getTicksLived() % 4 == 0) {
                copper.lookAt(target, 25.0F, 40.0F);
            }
            return;
        }
        copper.lookAt(target, 40.0F, 60.0F);
    }

    private static Location resolveTarget(CopperGolem copper, ActiveGolem golem) {
        boolean sitting = isSitting(golem);
        UUID entityId = golem.gazeEntityId();
        if (entityId != null) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null && entity.isValid() && !entity.isDead()) {
                if (entity instanceof Player && !sitting) {
                    golem.gazeEntityId(null);
                } else {
                    return entity instanceof org.bukkit.entity.LivingEntity living
                            ? living.getEyeLocation()
                            : entity.getLocation().add(0.0D, 0.9D, 0.0D);
                }
            } else {
                golem.gazeEntityId(null);
            }
        }
        Location point = golem.gazePoint();
        if (point != null && point.getWorld() != null) {
            return point;
        }
        return null;
    }

    private static boolean isSitting(ActiveGolem golem) {
        if (golem.data().type() == GolemType.FARMER) {
            return golem.farmerState() == FarmerState.SITTING;
        }
        if (golem.data().type() == GolemType.DIGGER) {
            return golem.diggerState() == DiggerState.SITTING;
        }
        return golem.state() == MinerState.SITTING;
    }
}
