package bm.b0b0b0.SoulGolem.service.miner;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.message.MessageService;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.MinerState;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.repository.GolemRepository;
import bm.b0b0b0.SoulGolem.service.FarmAreaService;
import bm.b0b0b0.SoulGolem.service.GolemCombatWork;
import bm.b0b0b0.SoulGolem.service.GolemFenceWork;
import bm.b0b0b0.SoulGolem.service.GolemGateWatch;
import bm.b0b0b0.SoulGolem.service.GolemGroundLootWork;
import bm.b0b0b0.SoulGolem.service.GolemMovement;
import bm.b0b0b0.SoulGolem.service.GolemRainShelterWork;
import bm.b0b0b0.SoulGolem.service.GolemSeatWork;
import bm.b0b0b0.SoulGolem.service.GolemTorchWork;
import bm.b0b0b0.SoulGolem.service.GolemRegistry;
import bm.b0b0b0.SoulGolem.service.GolemSpawnService;
import bm.b0b0b0.SoulGolem.service.OreTableService;
import bm.b0b0b0.SoulGolem.service.SoulChestService;
import bm.b0b0b0.SoulGolem.service.WorkAreaService;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class MinerContext {

    private final Plugin plugin;
    private final ConfigurationLoader configurationLoader;
    private final PluginKeys keys;
    private final GolemRegistry registry;
    private final OreTableService oreTable;
    private final SoulChestService chestService;
    private final WorkAreaService workAreaService;
    private final FarmAreaService farmAreaService;
    private final GolemRepository repository;
    private final GolemSpawnService spawnService;
    private final GolemMovement movement;
    private final GolemRainShelterWork rainShelter;
    private final GolemFenceWork fenceWork;
    private final GolemGateWatch gateWatch;
    private final GolemTorchWork torchWork;
    private final GolemSeatWork seatWork;
    private final GolemGroundLootWork groundLoot;
    private final MinerPickaxeWork pickaxeWork;
    private final GolemCombatWork combat;

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
        this.plugin = plugin;
        this.configurationLoader = configurationLoader;
        this.keys = keys;
        this.registry = registry;
        this.oreTable = oreTable;
        this.chestService = chestService;
        this.workAreaService = workAreaService;
        this.farmAreaService = farmAreaService;
        this.repository = repository;
        this.spawnService = spawnService;
        this.movement = new GolemMovement(settings(), chestService, farmAreaService);
        this.rainShelter = new GolemRainShelterWork(farmAreaService, workAreaService, this.movement);
        this.fenceWork = new GolemFenceWork(farmAreaService, chestService, this.movement);
        this.gateWatch = new GolemGateWatch(farmAreaService, chestService, this.movement);
        this.torchWork = new GolemTorchWork(farmAreaService, chestService, this.movement);
        this.seatWork = new GolemSeatWork(farmAreaService, chestService, workAreaService, this.movement);
        this.groundLoot = new GolemGroundLootWork(chestService, this.movement);
        this.pickaxeWork = new MinerPickaxeWork(this, chestService);
        this.combat = new GolemCombatWork(chestService, workAreaService, this.movement, this::settings);
    }

    public Plugin plugin() {
        return this.plugin;
    }

    public PluginKeys keys() {
        return this.keys;
    }

    public GolemRegistry registry() {
        return this.registry;
    }

    public OreTableService oreTable() {
        return this.oreTable;
    }

    public SoulChestService chestService() {
        return this.chestService;
    }

    public WorkAreaService workAreaService() {
        return this.workAreaService;
    }

    public FarmAreaService farmAreaService() {
        return this.farmAreaService;
    }

    public GolemRepository repository() {
        return this.repository;
    }

    public GolemSpawnService spawnService() {
        return this.spawnService;
    }

    public Settings settings() {
        return this.configurationLoader.config().settings();
    }

    public MessageService messages() {
        return this.configurationLoader.messages();
    }

    public GolemMovement movement() {
        return this.movement;
    }

    public GolemRainShelterWork rainShelter() {
        return this.rainShelter;
    }

    public GolemFenceWork fenceWork() {
        return this.fenceWork;
    }

    public GolemGateWatch gateWatch() {
        return this.gateWatch;
    }

    public GolemTorchWork torchWork() {
        return this.torchWork;
    }

    public GolemSeatWork seatWork() {
        return this.seatWork;
    }

    public GolemGroundLootWork groundLoot() {
        return this.groundLoot;
    }

    public MinerPickaxeWork pickaxeWork() {
        return this.pickaxeWork;
    }

    public GolemCombatWork combat() {
        return this.combat;
    }

    public int moodScore(SoulGolemData data) {
        return this.farmAreaService.moodScore(data, this.chestService.effectiveRadius(data));
    }

    public long effectiveWorkIntervalMs(SoulGolemData data) {
        long intervalMs = Math.max(1L, settings().workIntervalTicks) * 50L;
        double speedMultiplier = levelSpeedMultiplier(data);
        double moodSpeed = settings().moodWorkSpeedAt(moodScore(data));
        return (long) (intervalMs / Math.max(0.1D, speedMultiplier * moodSpeed));
    }

    public double levelSpeedMultiplier(SoulGolemData data) {
        for (Settings.LevelStats level : settings().levels) {
            if (level.level == data.level()) {
                return level.speedMultiplier;
            }
        }
        return 1.0D;
    }

    public Material resolveTorch() {
        Material torch = Material.matchMaterial(settings().miner.torchMaterial);
        return torch != null ? torch : Material.TORCH;
    }

    public Material resolveShovel() {
        Material shovel = Material.matchMaterial(settings().shovelMaterial);
        if (shovel != null) {
            return shovel;
        }
        Material copper = Material.matchMaterial("COPPER_SHOVEL");
        return copper != null ? copper : Material.IRON_SHOVEL;
    }

    public void equipShovel(CopperGolem copper) {
        org.bukkit.inventory.EntityEquipment equipment = copper.getEquipment();
        if (equipment == null) {
            return;
        }
        Material shovel = resolveShovel();
        ItemStack current = equipment.getItemInMainHand();
        if (current != null && !current.isEmpty() && current.getType() == shovel) {
            equipment.setItemInMainHandDropChance(0.0F);
            return;
        }
        equipment.setItemInMainHand(new ItemStack(shovel), true);
        equipment.setItemInMainHandDropChance(0.0F);
    }

    public boolean tryStartFeed(ActiveGolem golem) {
        if (golem.data().energy() > settings().energyHungryThreshold) {
            return false;
        }
        Material feed = settings().energyFeedMaterial();
        if (this.chestService.countItem(golem.data(), feed) <= 0) {
            return false;
        }
        golem.clearFetchFlags();
        golem.fetchingFeed(true);
        golem.state(MinerState.MOVING_TO_CHEST);
        return true;
    }

    public void eatFeedFromChest(ActiveGolem golem) {
        Material feed = settings().energyFeedMaterial();
        if (!this.chestService.takeItem(golem.data(), feed, 1)) {
            golem.fetchingFeed(false);
            golem.state(MinerState.IDLE);
            return;
        }
        int restored = Math.max(1, settings().energyPerIngot);
        int capacity = Math.max(1, settings().energyCapacity);
        golem.data().energy(Math.min(capacity, golem.data().energy() + restored));
        golem.fetchingFeed(false);
        golem.markDirty();
        golem.state(MinerState.IDLE);
        golem.data().lastActionAt(System.currentTimeMillis());
    }

    public void notifyChestFull(ActiveGolem golem) {
        if (golem.chestFullNotified()) {
            return;
        }
        golem.chestFullNotified(true);
        Player owner = Bukkit.getPlayer(golem.data().ownerUuid());
        if (owner != null) {
            messages().send(owner, "chest-full");
        }
    }

    public void walkTowards(CopperGolem copper, Location target, SoulGolemData data) {
        this.movement.walkTowards(copper, target, data);
    }

    public void walkTowards(CopperGolem copper, Location target, ActiveGolem golem) {
        this.movement.walkTowards(copper, target, golem);
    }

    public Entity resolveEntity(SoulGolemData data) {
        if (data.entityUuid() == null) {
            return null;
        }
        return Bukkit.getEntity(data.entityUuid());
    }

    public void playMineFx(Block block) {
        Settings.Visuals visuals = settings().visuals;
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
        Settings.Visuals visuals = settings().visuals;
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
