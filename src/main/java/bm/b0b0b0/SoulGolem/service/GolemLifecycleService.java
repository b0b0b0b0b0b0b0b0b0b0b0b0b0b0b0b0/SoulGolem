package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.repository.GolemRepository;
import bm.b0b0b0.SoulGolem.scheduler.PluginSchedulers;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.plugin.Plugin;

public final class GolemLifecycleService {

    private final Plugin plugin;
    private final GolemRepository repository;
    private final GolemSpawnService spawnService;
    private final GolemRegistry registry;
    private final MinerTickService minerTickService;
    private final FarmerTickService farmerTickService;

    public GolemLifecycleService(
            Plugin plugin,
            GolemRepository repository,
            GolemSpawnService spawnService,
            GolemRegistry registry,
            MinerTickService minerTickService,
            FarmerTickService farmerTickService
    ) {
        this.plugin = plugin;
        this.repository = repository;
        this.spawnService = spawnService;
        this.registry = registry;
        this.minerTickService = minerTickService;
        this.farmerTickService = farmerTickService;
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
        for (SoulGolemData data : list) {
            try {
                this.spawnService.respawnInWorld(data);
            } catch (Exception exception) {
                this.plugin.getLogger().log(Level.WARNING, "Failed to respawn golem " + data.id(), exception);
            }
        }
        this.plugin.getLogger().info("Loaded " + list.size() + " Soul Golems");
        this.spawnService.purgeOrphanEntities();
        PluginSchedulers.runGlobalLater(this.plugin, this.spawnService::purgeOrphanEntities, 40L);
        PluginSchedulers.runGlobalLater(this.plugin, this.spawnService::purgeOrphanEntities, 100L);
        this.minerTickService.start();
        this.farmerTickService.start();
    }

    public void shutdown() {
        this.minerTickService.stop();
        this.farmerTickService.stop();
        this.minerTickService.flushAll();
        this.farmerTickService.flushAll();
        this.spawnService.gazeService().shutdown();
        this.spawnService.removeAllSoulEntities();
        this.registry.clear();
    }
}
