package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.scheduler.PluginSchedulers;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.block.Chest;
import org.bukkit.block.Lidded;
import org.bukkit.plugin.Plugin;

public final class SoulChestLid {

    private static final long CLOSE_DELAY_TICKS = 15L;

    private final Plugin plugin;
    private final Function<SoulGolemData, Chest> chestOf;
    private final Map<UUID, Integer> closeEpoch = new ConcurrentHashMap<>();

    public SoulChestLid(Plugin plugin, Function<SoulGolemData, Chest> chestOf) {
        this.plugin = plugin;
        this.chestOf = chestOf;
    }

    public void open(SoulGolemData data) {
        Chest chest = this.chestOf.apply(data);
        if (!(chest instanceof Lidded lidded)) {
            return;
        }
        if (!lidded.isOpen()) {
            lidded.open();
        }
    }

    public void closeNow(SoulGolemData data) {
        this.closeEpoch.merge(data.id(), 1, Integer::sum);
        closeLid(data);
    }

    public void closeLater(SoulGolemData data) {
        Chest chest = this.chestOf.apply(data);
        if (chest == null) {
            return;
        }
        UUID golemId = data.id();
        int epoch = this.closeEpoch.getOrDefault(golemId, 0);
        Location loc = chest.getLocation();
        PluginSchedulers.runAtLater(this.plugin, loc, () -> {
            if (this.closeEpoch.getOrDefault(golemId, 0) != epoch) {
                return;
            }
            closeLid(data);
        }, CLOSE_DELAY_TICKS);
    }

    public void run(SoulGolemData data, Runnable work) {
        open(data);
        try {
            work.run();
        } finally {
            closeLater(data);
        }
    }

    public <T> T run(SoulGolemData data, Supplier<T> work) {
        open(data);
        try {
            return work.get();
        } finally {
            closeLater(data);
        }
    }

    private void closeLid(SoulGolemData data) {
        Chest chest = this.chestOf.apply(data);
        if (!(chest instanceof Lidded lidded)) {
            return;
        }
        if (lidded.isOpen()) {
            lidded.close();
        }
    }
}
