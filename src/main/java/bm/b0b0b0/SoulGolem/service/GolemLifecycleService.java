package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.repository.GolemRepository;
import bm.b0b0b0.SoulGolem.scheduler.PluginSchedulers;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.bukkit.plugin.Plugin;

public final class GolemLifecycleService {

    private final Plugin plugin;
    private final GolemRepository repository;
    private final GolemSpawnService spawnService;
    private final GolemRegistry registry;
    private final MinerTickService minerTickService;
    private final FarmerTickService farmerTickService;
    private final DiggerTickService diggerTickService;

    public GolemLifecycleService(
            Plugin plugin,
            GolemRepository repository,
            GolemSpawnService spawnService,
            GolemRegistry registry,
            MinerTickService minerTickService,
            FarmerTickService farmerTickService,
            DiggerTickService diggerTickService
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.spawnService = spawnService;
        this.registry = registry;
        this.minerTickService = minerTickService;
        this.farmerTickService = farmerTickService;
        this.diggerTickService = diggerTickService;
    }

    public void loadAll() {
        this.repository.findAll().whenComplete((list, error) -> {
            if (error != null) {
                this.plugin.getLogger().log(Level.SEVERE, "Failed to load Soul Golems", error);
                return;
            }
            PluginSchedulers.runGlobal(this.plugin, () -> spawnLoaded(list));
        });
    }

    private void spawnLoaded(List<SoulGolemData> list) {
        this.registry.clear();
        this.spawnService.beginBootQuiet(20_000L);
        this.spawnService.removeAllSoulEntities();

        if (list == null || list.isEmpty()) {
            finishBoot(0);
            return;
        }

        AtomicInteger pending = new AtomicInteger(list.size());
        for (SoulGolemData data : list) {
            try {
                this.spawnService.respawnInWorld(data, () -> {
                    if (pending.decrementAndGet() <= 0) {
                        PluginSchedulers.runGlobal(this.plugin, () -> finishBoot(list.size()));
                    }
                });
            } catch (Exception exception) {
                this.plugin.getLogger().log(Level.WARNING, "Failed to respawn golem " + data.id(), exception);
                if (pending.decrementAndGet() <= 0) {
                    PluginSchedulers.runGlobal(this.plugin, () -> finishBoot(list.size()));
                }
            }
        }
    }

    private void finishBoot(int loaded) {
        this.spawnService.purgeOrphanEntities();
        this.plugin.getLogger().info("Loaded " + loaded + " Soul Golems");
        this.minerTickService.start();
        this.farmerTickService.start();
        this.diggerTickService.start();
        PluginSchedulers.runGlobalLater(this.plugin, this.spawnService::purgeOrphanEntities, 60L);
        PluginSchedulers.runGlobalLater(this.plugin, this.spawnService::purgeOrphanEntities, 200L);
    }

    public void shutdown() {
        this.minerTickService.stop();
        this.farmerTickService.stop();
        this.diggerTickService.stop();
        this.spawnService.persistLivePositions();
        this.minerTickService.flushAll();
        this.farmerTickService.flushAll();
        this.diggerTickService.flushAll();
        this.spawnService.gazeService().shutdown();
        this.spawnService.removeAllSoulEntities();
        this.registry.clear();
    }
}
