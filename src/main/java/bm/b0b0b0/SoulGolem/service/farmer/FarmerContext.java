package bm.b0b0b0.SoulGolem.service.farmer;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.message.MessageService;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.CropType;
import bm.b0b0b0.SoulGolem.model.FarmerState;
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
import bm.b0b0b0.SoulGolem.service.SoulChestService;
import bm.b0b0b0.SoulGolem.service.WorkAreaService;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class FarmerContext {

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

    public FarmerContext(
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

    public GolemCombatWork combat() {
        return this.combat;
    }

    public List<CropType> enabledCrops() {
        return settings().enabledCrops();
    }

    public boolean hasAnySeedInChest(SoulGolemData data) {
        return findSeedInChest(data) != null;
    }

    public Material findSeedInChest(SoulGolemData data) {
        int radius = this.chestService.effectiveRadius(data);
        for (CropType type : enabledCrops()) {
            if (this.chestService.countItem(data, type.seed()) <= 0) {
                continue;
            }
            if (!this.farmAreaService.plantSpots(data, radius, type).isEmpty()) {
                return type.seed();
            }
        }
        for (CropType type : enabledCrops()) {
            if (this.chestService.countItem(data, type.seed()) > 0) {
                return type.seed();
            }
        }
        return null;
    }

    public boolean hasOpenPlantSpots(SoulGolemData data) {
        int radius = this.chestService.effectiveRadius(data);
        for (CropType type : enabledCrops()) {
            if (!this.farmAreaService.plantSpots(data, radius, type).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPlantWork(ActiveGolem golem) {
        int radius = this.chestService.effectiveRadius(golem.data());
        for (ItemStack stack : golem.carried()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            CropType type = CropType.bySeed(stack.getType());
            if (type != null
                    && enabledCrops().contains(type)
                    && !this.farmAreaService.plantSpots(golem.data(), radius, type).isEmpty()) {
                return true;
            }
        }
        for (CropType type : enabledCrops()) {
            if (this.chestService.countItem(golem.data(), type.seed()) <= 0) {
                continue;
            }
            if (!this.farmAreaService.plantSpots(golem.data(), radius, type).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public void drainFarmEnergy(ActiveGolem golem) {
        int drain = settings().energyPerFarmAction;
        if (drain <= 0) {
            return;
        }
        golem.data().energy(Math.max(0, golem.data().energy() - drain));
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
        golem.farmerState(FarmerState.MOVING_TO_CHEST);
        return true;
    }

    public void eatFeedFromChest(ActiveGolem golem) {
        Material feed = settings().energyFeedMaterial();
        if (!this.chestService.takeItem(golem.data(), feed, 1)) {
            golem.fetchingFeed(false);
            golem.farmerState(FarmerState.WAITING_SEEDS);
            return;
        }
        int restored = Math.max(1, settings().energyPerIngot);
        int capacity = Math.max(1, settings().energyCapacity);
        golem.data().energy(Math.min(capacity, golem.data().energy() + restored));
        golem.fetchingFeed(false);
        golem.markDirty();
        golem.farmerState(FarmerState.WAITING_SEEDS);
        golem.data().lastActionAt(System.currentTimeMillis());
    }

    public boolean stationsOk(ActiveGolem golem) {
        SoulGolemData data = golem.data();
        if (this.chestService.isChestPresent(data) && this.chestService.isCraftPresent(data)) {
            return true;
        }
        this.spawnService.removeGolem(data.id(), null);
        return false;
    }

    public Material resolveShovel() {
        Material shovel = Material.matchMaterial(settings().shovelMaterial);
        if (shovel != null) {
            return shovel;
        }
        Material copper = Material.matchMaterial("COPPER_SHOVEL");
        return copper != null ? copper : Material.IRON_SHOVEL;
    }

    public Material resolveTorch() {
        Material torch = Material.matchMaterial(settings().farmer.torchMaterial);
        return torch != null ? torch : Material.TORCH;
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

    public Location farthestPlot(SoulGolemData data, List<Block> blocks) {
        Block block = this.farmAreaService.pickFarthestFromChest(blocks, data);
        return block == null ? null : block.getLocation();
    }

    public Location boneMealPlot(SoulGolemData data, List<Block> immature) {
        Block block = this.farmAreaService.pickImmatureForBoneMeal(immature, data);
        return block == null ? null : block.getLocation();
    }

    public List<Block> immatureBoneMealCrops(SoulGolemData data) {
        int radius = this.chestService.effectiveRadius(data);
        return this.farmAreaService.immatureCrops(data, radius, enabledCrops());
    }

    public boolean hasBoneMealWork(ActiveGolem golem) {
        if (!settings().farmer.useBoneMeal) {
            return false;
        }
        if (immatureBoneMealCrops(golem.data()).isEmpty()) {
            return false;
        }
        if (FarmerCarried.countCarried(golem, Material.BONE_MEAL) > 0) {
            return true;
        }
        return this.chestService.countItem(golem.data(), Material.BONE_MEAL) > 0;
    }

    public boolean resumeBoneMealCarried(ActiveGolem golem) {
        if (hasPlantWork(golem)) {
            return false;
        }
        if (!hasBoneMealWork(golem) || FarmerCarried.countCarried(golem, Material.BONE_MEAL) <= 0) {
            return false;
        }
        if (golem.targetCrop() == null) {
            golem.targetCrop(boneMealPlot(golem.data(), immatureBoneMealCrops(golem.data())));
        }
        golem.clearFetchFlags();
        golem.farmerState(FarmerState.MOVING_TO_BONEMEAL);
        return true;
    }

    public void requestBoneMealFromChest(ActiveGolem golem) {
        golem.clearFetchFlags();
        golem.fetchingBoneMeal(true);
        List<Block> immature = immatureBoneMealCrops(golem.data());
        golem.targetCrop(boneMealPlot(golem.data(), immature));
        golem.farmerState(FarmerState.MOVING_TO_CHEST);
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
}
