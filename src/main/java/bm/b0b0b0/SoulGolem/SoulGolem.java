package bm.b0b0b0.SoulGolem;

import bm.b0b0b0.SoulGolem.command.SoulGolemCommand;
import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.config.PluginConfig;
import bm.b0b0b0.SoulGolem.database.DatabaseManager;
import bm.b0b0b0.SoulGolem.item.StatueItemFactory;
import bm.b0b0b0.SoulGolem.listener.GolemGuiListener;
import bm.b0b0b0.SoulGolem.listener.GolemListener;
import bm.b0b0b0.SoulGolem.repository.GolemRepository;
import bm.b0b0b0.SoulGolem.repository.SqlGolemRepository;
import bm.b0b0b0.SoulGolem.scheduler.PluginSchedulers;
import bm.b0b0b0.SoulGolem.service.FarmAreaService;
import bm.b0b0b0.SoulGolem.service.FarmerTickService;
import bm.b0b0b0.SoulGolem.service.GolemControlService;
import bm.b0b0b0.SoulGolem.service.GolemLifecycleService;
import bm.b0b0b0.SoulGolem.gui.GolemGuiService;
import bm.b0b0b0.SoulGolem.service.GolemRegistry;
import bm.b0b0b0.SoulGolem.service.GolemSpawnService;
import bm.b0b0b0.SoulGolem.service.MinerTickService;
import bm.b0b0b0.SoulGolem.service.OreTableService;
import bm.b0b0b0.SoulGolem.service.SoulChestService;
import bm.b0b0b0.SoulGolem.service.WorkAreaService;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SoulGolem extends JavaPlugin {

    private ConfigurationLoader configurationLoader;
    private DatabaseManager databaseManager;
    private GolemLifecycleService lifecycleService;
    private WorkAreaService workAreaService;

    @Override
    public void onEnable() {
        this.configurationLoader = new ConfigurationLoader(this);
        try {
            this.configurationLoader.load();
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Failed to load configuration", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PluginConfig config = this.configurationLoader.config();
        PluginKeys keys = new PluginKeys(this);
        StatueItemFactory statueFactory = new StatueItemFactory(keys, this.configurationLoader::messages);
        GolemRegistry registry = new GolemRegistry();
        OreTableService oreTable = new OreTableService(config);
        SoulChestService chestService = new SoulChestService(
                this,
                keys,
                config,
                this.configurationLoader.messages().raw("chest-name"),
                this.configurationLoader.messages().raw("craft-table-name")
        );
        this.workAreaService = new WorkAreaService(this.configurationLoader);
        FarmAreaService farmAreaService = new FarmAreaService(this.configurationLoader, this.workAreaService, chestService);

        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.start(config.settings().database, config.dataFolder())
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        getLogger().log(Level.SEVERE, "Failed to start database", error);
                        PluginSchedulers.runGlobal(this, () -> getServer().getPluginManager().disablePlugin(this));
                        return;
                    }
                    PluginSchedulers.runGlobal(this, () -> afterDatabase(keys, statueFactory, registry, oreTable, chestService, farmAreaService));
                });
    }

    private void afterDatabase(
            PluginKeys keys,
            StatueItemFactory statueFactory,
            GolemRegistry registry,
            OreTableService oreTable,
            SoulChestService chestService,
            FarmAreaService farmAreaService
    ) {
        PluginConfig config = this.configurationLoader.config();
        boolean mysql = "mysql".equalsIgnoreCase(config.settings().database.type);
        GolemRepository repository = new SqlGolemRepository(this.databaseManager, mysql);

        GolemSpawnService spawnService = new GolemSpawnService(
                this,
                this.configurationLoader,
                keys,
                statueFactory,
                chestService,
                this.workAreaService,
                farmAreaService,
                oreTable,
                registry,
                repository
        );

        MinerTickService minerTickService = new MinerTickService(
                this,
                this.configurationLoader,
                keys,
                registry,
                oreTable,
                chestService,
                this.workAreaService,
                farmAreaService,
                repository,
                spawnService
        );

        FarmerTickService farmerTickService = new FarmerTickService(
                this,
                this.configurationLoader,
                keys,
                registry,
                chestService,
                this.workAreaService,
                farmAreaService,
                repository,
                spawnService
        );

        this.lifecycleService = new GolemLifecycleService(
                this,
                repository,
                spawnService,
                registry,
                minerTickService,
                farmerTickService
        );

        GolemControlService controlService = new GolemControlService(
                this,
                this.configurationLoader,
                farmAreaService,
                chestService,
                repository,
                spawnService,
                keys
        );
        GolemGuiService guiService = new GolemGuiService(
                this.configurationLoader,
                registry,
                controlService
        );

        getServer().getPluginManager().registerEvents(new GolemListener(
                this.configurationLoader,
                keys,
                statueFactory,
                spawnService,
                chestService,
                this.workAreaService,
                farmAreaService,
                registry,
                repository
        ), this);
        getServer().getPluginManager().registerEvents(new GolemGuiListener(), this);

        SoulGolemCommand command = new SoulGolemCommand(
                this.configurationLoader,
                statueFactory,
                oreTable,
                chestService,
                guiService
        );
        PluginCommand pluginCommand = getCommand("soulgolem");
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        this.lifecycleService.loadAll();
        getLogger().info("SoulGolem enabled");
    }

    @Override
    public void onDisable() {
        if (this.lifecycleService != null) {
            this.lifecycleService.shutdown();
        }
        if (this.databaseManager != null) {
            this.databaseManager.shutdown();
        }
    }
}
