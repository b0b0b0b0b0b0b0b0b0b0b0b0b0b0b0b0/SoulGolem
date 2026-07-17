package bm.b0b0b0.SoulGolem.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class PluginSchedulers {

    private PluginSchedulers() {
    }

    public static void run(Plugin plugin, Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, scheduled -> task.run(), null);
    }

    public static ScheduledTask runLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        return entity.getScheduler().runDelayed(plugin, scheduled -> task.run(), null, delayTicks);
    }

    public static ScheduledTask runTimer(Plugin plugin, Entity entity, Runnable task, long delayTicks, long periodTicks) {
        return entity.getScheduler().runAtFixedRate(plugin, scheduled -> task.run(), null, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
    }

    public static void runAt(Plugin plugin, Location location, Runnable task) {
        Bukkit.getRegionScheduler().run(plugin, location, scheduled -> task.run());
    }

    public static ScheduledTask runAtLater(Plugin plugin, Location location, Runnable task, long delayTicks) {
        return Bukkit.getRegionScheduler().runDelayed(plugin, location, scheduled -> task.run(), delayTicks);
    }

    public static ScheduledTask runAtTimer(Plugin plugin, Location location, Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getRegionScheduler().runAtFixedRate(
                plugin,
                location,
                scheduled -> task.run(),
                Math.max(1L, delayTicks),
                Math.max(1L, periodTicks)
        );
    }

    public static void runGlobal(Plugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, scheduled -> task.run());
    }

    public static ScheduledTask runGlobalLater(Plugin plugin, Runnable task, long delayTicks) {
        return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduled -> task.run(), delayTicks);
    }

    public static ScheduledTask runGlobalTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                scheduled -> task.run(),
                Math.max(1L, delayTicks),
                Math.max(1L, periodTicks)
        );
    }

    public static void runAsync(Plugin plugin, Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    }

    public static ScheduledTask runAsyncLater(Plugin plugin, Runnable task, long delay, TimeUnit unit) {
        return Bukkit.getAsyncScheduler().runDelayed(plugin, scheduled -> task.run(), delay, unit);
    }

    public static ScheduledTask runAsyncTimer(Plugin plugin, Runnable task, long delay, long period, TimeUnit unit) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduled -> task.run(), delay, period, unit);
    }

    public static void runAsync(Plugin plugin, Consumer<ScheduledTask> task) {
        Bukkit.getAsyncScheduler().runNow(plugin, task);
    }
}
