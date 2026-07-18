package bm.b0b0b0.SoulGolem.service.miner;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.MinerState;
import bm.b0b0b0.SoulGolem.repository.GolemRepository;
import bm.b0b0b0.SoulGolem.service.FarmAreaService;
import bm.b0b0b0.SoulGolem.service.GolemEnergy;
import bm.b0b0b0.SoulGolem.service.GolemRegistry;
import bm.b0b0b0.SoulGolem.service.GolemSpawnService;
import bm.b0b0b0.SoulGolem.service.GolemWorkContext;
import bm.b0b0b0.SoulGolem.service.OreTableService;
import bm.b0b0b0.SoulGolem.service.SoulChestService;
import bm.b0b0b0.SoulGolem.service.WorkAreaService;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

public final class MinerContext extends GolemWorkContext {

    private final OreTableService oreTable;
    private final MinerPickaxeWork pickaxeWork;

    public MinerContext(
            Plugin plugin,
            ConfigurationLoader configurationLoader,
            PluginKeys keys,
            GolemRegistry registry,
            OreTableService oreTable,
            SoulChestService chestService,
            WorkAreaService workAreaService,
            FarmAreaService farmAreaService,
            GolemRepository repository,
            GolemSpawnService spawnService
    ) {
        super(
                plugin,
                configurationLoader,
                keys,
                registry,
                chestService,
                workAreaService,
                farmAreaService,
                repository,
                spawnService
        );
        this.oreTable = oreTable;
        this.pickaxeWork = new MinerPickaxeWork(this, chestService);
    }

    public OreTableService oreTable() {
        return this.oreTable;
    }

    public MinerPickaxeWork pickaxeWork() {
        return this.pickaxeWork;
    }

    public boolean tryStartFeed(ActiveGolem golem) {
        return GolemEnergy.tryStartFeed(
                chestService(),
                settings(),
                golem,
                g -> g.state(MinerState.MOVING_TO_CHEST)
        );
    }

    public void eatFeedFromChest(ActiveGolem golem) {
        GolemEnergy.eatFeed(
                chestService(),
                settings(),
                golem,
                g -> g.state(MinerState.IDLE)
        );
    }

    public void playMineFx(Block block) {
        GolemSettings.Visuals visuals = settings().visuals;
        if (!visuals.particles) {
            if (visuals.sounds) {
                playMineSound(block);
            }
            return;
        }
        Location at = block.getLocation().add(0.5D, 0.55D, 0.5D);
        World world = block.getWorld();
        world.spawnParticle(Particle.BLOCK, at, 18, 0.28D, 0.28D, 0.28D, 0.02D, block.getBlockData());
        world.spawnParticle(Particle.CRIT, at, 8, 0.2D, 0.2D, 0.2D, 0.05D);
        world.spawnParticle(Particle.DUST, at, 6, 0.2D, 0.15D, 0.2D, new Particle.DustOptions(org.bukkit.Color.fromRGB(192, 132, 252), 1.1F));
        if (visuals.sounds) {
            playMineSound(block);
        }
    }

    public void playMineBurst(Block block) {
        Location at = block.getLocation().add(0.5D, 0.5D, 0.5D);
        World world = block.getWorld();
        world.spawnParticle(Particle.BLOCK, at, 40, 0.35D, 0.35D, 0.35D, 0.08D, block.getBlockData());
        world.spawnParticle(Particle.CLOUD, at, 10, 0.2D, 0.2D, 0.2D, 0.02D);
        world.spawnParticle(Particle.DUST, at, 14, 0.3D, 0.25D, 0.3D, new Particle.DustOptions(org.bukkit.Color.fromRGB(168, 85, 247), 1.3F));
        playMineSound(block);
    }

    public void playDepositFx(Location chestStand) {
        Location at = chestStand.clone().add(0.0D, 0.8D, 0.0D);
        World world = chestStand.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.HAPPY_VILLAGER, at, 10, 0.35D, 0.25D, 0.35D, 0.0D);
        world.spawnParticle(Particle.DUST, at, 12, 0.3D, 0.2D, 0.3D, new Particle.DustOptions(org.bukkit.Color.fromRGB(192, 132, 252), 1.2F));
        Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft("block.chest.close"));
        if (sound != null) {
            world.playSound(at, sound, 0.5F, 1.2F);
        }
    }

    private void playMineSound(Block block) {
        GolemSettings.Visuals visuals = settings().visuals;
        Sound sound = resolveSound(visuals.mineSound);
        if (sound != null) {
            block.getWorld().playSound(block.getLocation(), sound, visuals.soundVolume, visuals.soundPitch);
        }
    }

    private static Sound resolveSound(String name) {
        NamespacedKey key = NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT).replace('_', '.'));
        Sound sound = Registry.SOUNDS.get(key);
        if (sound != null) {
            return sound;
        }
        return Registry.SOUNDS.get(NamespacedKey.minecraft("block.stone.break"));
    }
}
