package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import bm.b0b0b0.SoulGolem.message.MessageService;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.repository.GolemRepository;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public abstract class GolemWorkContext {

    private final Plugin plugin;
    private final ConfigurationLoader configurationLoader;
    private final PluginKeys keys;
    private final GolemRegistry registry;
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
    private final GolemCombatWork combat;
    private final SoulChestLink chestLink;

    protected GolemWorkContext(
            Plugin plugin,
            ConfigurationLoader configurationLoader,
            PluginKeys keys,
            GolemRegistry registry,
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
        this.combat = new GolemCombatWork(chestService, workAreaService, this.movement, this::settings);
        this.chestLink = new SoulChestLink(chestService, this::settings);
    }

    public Plugin plugin() {
        return this.plugin;
    }

    public ConfigurationLoader configurationLoader() {
        return this.configurationLoader;
    }

    public PluginKeys keys() {
        return this.keys;
    }

    public GolemRegistry registry() {
        return this.registry;
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

    public GolemSettings settings() {
        return this.configurationLoader.config().golems();
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

    public GolemCombatWork combat() {
        return this.combat;
    }

    public SoulChestLink chestLink() {
        return this.chestLink;
    }

    public int moodScore(SoulGolemData data) {
        return this.farmAreaService.moodScore(data, this.chestService.effectiveRadius(data));
    }

    public long effectiveWorkIntervalMs(SoulGolemData data) {
        return effectiveWorkIntervalMs(data, 1.0D);
    }

    public long effectiveWorkIntervalMs(ActiveGolem golem) {
        return effectiveWorkIntervalMs(golem.data(), stickBoostFactor(golem));
    }

    public long effectiveWorkIntervalMs(SoulGolemData data, double stickFactor) {
        long intervalMs = Math.max(1L, settings().workIntervalTicks) * 50L;
        double speedMultiplier = levelSpeedMultiplier(data);
        double moodSpeed = settings().moodWorkSpeedAt(moodScore(data));
        return (long) (intervalMs / Math.max(0.1D, speedMultiplier * moodSpeed * Math.max(1.0D, stickFactor)));
    }

    public double stickBoostFactor(ActiveGolem golem) {
        if (golem == null || !settings().stickBoostEnabled || !golem.workBoostActive()) {
            return 1.0D;
        }
        return Math.max(1.0D, settings().stickBoostMultiplier);
    }

    public void applyStickBoost(ActiveGolem golem) {
        if (golem == null || !settings().stickBoostEnabled) {
            return;
        }
        long duration = Math.max(1000L, settings().stickBoostDurationMs);
        golem.workBoostUntilMs(System.currentTimeMillis() + duration);
        golem.data().lastActionAt(0L);
    }

    public double levelSpeedMultiplier(SoulGolemData data) {
        for (GolemSettings.LevelStats level : settings().levels) {
            if (level.level == data.level()) {
                return level.speedMultiplier;
            }
        }
        return 1.0D;
    }

    public Material resolveTorch() {
        Material torch = Material.matchMaterial(settings().yard.torchMaterial);
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
        EntityEquipment equipment = copper.getEquipment();
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

    public boolean stationsOk(ActiveGolem golem) {
        SoulGolemData data = golem.data();
        if (this.chestService.isChestPresent(data)) {
            return true;
        }
        this.spawnService.removeGolem(data.id(), null);
        return false;
    }
}
