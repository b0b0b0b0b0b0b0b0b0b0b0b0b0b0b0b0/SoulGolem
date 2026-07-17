package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.message.MessageService;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.FarmerState;
import bm.b0b0b0.SoulGolem.model.GolemType;
import bm.b0b0b0.SoulGolem.model.MinerState;
import bm.b0b0b0.SoulGolem.repository.GolemRepository;
import bm.b0b0b0.SoulGolem.scheduler.PluginSchedulers;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class GolemControlService {

    private final Plugin plugin;
    private final ConfigurationLoader configurationLoader;
    private final FarmAreaService farmAreaService;
    private final SoulChestService chestService;
    private final GolemRepository repository;
    private final GolemSpawnService spawnService;
    private final PluginKeys keys;

    public GolemControlService(
            Plugin plugin,
            ConfigurationLoader configurationLoader,
            FarmAreaService farmAreaService,
            SoulChestService chestService,
            GolemRepository repository,
            GolemSpawnService spawnService,
            PluginKeys keys
    ) {
        this.plugin = plugin;
        this.configurationLoader = configurationLoader;
        this.farmAreaService = farmAreaService;
        this.chestService = chestService;
        this.repository = repository;
        this.spawnService = spawnService;
        this.keys = keys;
    }

    public boolean requestStop(ActiveGolem golem, Player notifier) {
        if (golem.data().paused()) {
            return false;
        }
        golem.clearFetchFlags();
        golem.wanderTarget(null);
        golem.clearPathWaypoint();
        golem.targetOre(null);
        golem.targetCrop(null);
        golem.blocksLeftThisTrip(0);
        golem.upgradePickaxe(null);
        golem.pickaxeSwapBlocked(false);

        Settings settings = this.configurationLoader.config().settings();
        if (this.farmAreaService.hasValidSeat(golem.data())) {
            long restTicks = golem.data().type() == GolemType.MINER
                    ? Math.max(1L, settings.miner.seatRestTicks)
                    : 1L;
            golem.restTicksLeft(restTicks);
            golem.pauseAfterRest(true);
            golem.data().paused(false);
            if (golem.data().type() == GolemType.MINER) {
                golem.state(MinerState.MOVING_TO_SEAT);
            } else {
                golem.farmerState(FarmerState.MOVING_TO_SEAT);
            }
        } else {
            golem.pauseAfterRest(false);
            golem.data().paused(true);
            if (golem.data().type() == GolemType.MINER) {
                golem.state(MinerState.RESTING);
                golem.restTicksLeft(0L);
            } else {
                golem.farmerState(FarmerState.WAIT_GROWTH);
                golem.restTicksLeft(0L);
            }
        }
        golem.data().lastActionAt(System.currentTimeMillis());
        golem.markDirty();
        this.repository.save(golem.data());
        refreshDisplay(golem);
        if (notifier != null) {
            messages().send(notifier, "golem-paused");
        }
        return true;
    }

    public boolean resume(ActiveGolem golem, Player notifier) {
        if (!golem.data().paused()) {
            return false;
        }
        golem.data().paused(false);
        golem.pauseAfterRest(false);
        golem.data().lastActionAt(System.currentTimeMillis());
        golem.markDirty();
        this.repository.save(golem.data());
        refreshDisplay(golem);
        if (notifier != null) {
            messages().send(notifier, "golem-resumed");
        }
        return true;
    }

    public void remove(UUID golemId, Player notifier) {
        this.spawnService.removeGolem(golemId, notifier);
    }

    public void teleportToGolem(Player player, ActiveGolem golem) {
        Location target = this.farmAreaService.safeStandNearHome(golem.data());
        if (target == null) {
            target = this.chestService.chestStandLocation(golem.data());
        }
        if (target == null) {
            World world = Bukkit.getWorld(golem.data().worldName());
            if (world == null) {
                messages().send(player, "gui-teleport-fail");
                return;
            }
            target = new Location(
                    world,
                    golem.data().homeX() + 0.5D,
                    golem.data().homeY() + 1.0D,
                    golem.data().homeZ() + 0.5D
            );
        }
        player.closeInventory();
        Location destination = target.clone();
        PluginSchedulers.runAt(this.plugin, destination, () -> {
            player.teleport(destination);
            messages().send(player, "gui-teleport-done");
        });
    }

    public static void finishPauseAfterRest(ActiveGolem golem, GolemRepository repository) {
        if (!golem.pauseAfterRest()) {
            return;
        }
        golem.data().paused(true);
        golem.pauseAfterRest(false);
        golem.markDirty();
        repository.save(golem.data());
    }

    private void refreshDisplay(ActiveGolem golem) {
        UUID entityUuid = golem.data().entityUuid();
        if (entityUuid == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(entityUuid);
        if (!(entity instanceof CopperGolem copper)) {
            return;
        }
        Settings.TextDisplays style = this.configurationLoader.config().settings().visuals.textDisplays;
        GolemDisplay.refreshForce(golem, copper, messages(), this.keys, style);
    }

    private MessageService messages() {
        return this.configurationLoader.messages();
    }
}
