package bm.b0b0b0.SoulGolem.service.farmer;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.CropType;
import bm.b0b0b0.SoulGolem.model.FarmerState;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.repository.GolemRepository;
import bm.b0b0b0.SoulGolem.service.FarmAreaService;
import bm.b0b0b0.SoulGolem.service.GolemCarried;
import bm.b0b0b0.SoulGolem.service.GolemEnergy;
import bm.b0b0b0.SoulGolem.service.GolemRegistry;
import bm.b0b0b0.SoulGolem.service.GolemSpawnService;
import bm.b0b0b0.SoulGolem.service.GolemWorkContext;
import bm.b0b0b0.SoulGolem.service.SoulChestService;
import bm.b0b0b0.SoulGolem.service.WorkAreaService;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class FarmerContext extends GolemWorkContext {

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
    }

    public List<CropType> enabledCrops() {
        return settings().enabledCrops();
    }

    public boolean hasAnySeedInChest(SoulGolemData data) {
        return findSeedInChest(data) != null;
    }

    public Material findSeedInChest(SoulGolemData data) {
        int radius = chestService().effectiveRadius(data);
        for (CropType type : enabledCrops()) {
            if (chestService().countItem(data, type.seed()) <= 0) {
                continue;
            }
            if (!farmAreaService().plantSpots(data, radius, type).isEmpty()) {
                return type.seed();
            }
        }
        for (CropType type : enabledCrops()) {
            if (chestService().countItem(data, type.seed()) > 0) {
                return type.seed();
            }
        }
        return null;
    }

    public boolean hasOpenPlantSpots(SoulGolemData data) {
        int radius = chestService().effectiveRadius(data);
        for (CropType type : enabledCrops()) {
            if (!farmAreaService().plantSpots(data, radius, type).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPlantWork(ActiveGolem golem) {
        int radius = chestService().effectiveRadius(golem.data());
        for (ItemStack stack : golem.carried()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            CropType type = CropType.bySeed(stack.getType());
            if (type != null
                    && enabledCrops().contains(type)
                    && !farmAreaService().plantSpots(golem.data(), radius, type).isEmpty()) {
                return true;
            }
        }
        for (CropType type : enabledCrops()) {
            if (chestService().countItem(golem.data(), type.seed()) <= 0) {
                continue;
            }
            if (!farmAreaService().plantSpots(golem.data(), radius, type).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public void drainFarmEnergy(ActiveGolem golem) {
        GolemEnergy.drain(golem, settings().energyPerFarmAction);
    }

    public boolean tryStartFeed(ActiveGolem golem) {
        return GolemEnergy.tryStartFeed(
                chestService(),
                settings(),
                golem,
                g -> g.farmerState(FarmerState.MOVING_TO_CHEST)
        );
    }

    public void eatFeedFromChest(ActiveGolem golem) {
        GolemEnergy.eatFeed(
                chestService(),
                settings(),
                golem,
                g -> g.farmerState(FarmerState.WAITING_SEEDS)
        );
    }

    public void equipBoneMeal(CopperGolem copper) {
        EntityEquipment equipment = copper.getEquipment();
        if (equipment == null) {
            return;
        }
        ItemStack current = equipment.getItemInMainHand();
        if (current != null && !current.isEmpty() && current.getType() == Material.BONE_MEAL) {
            equipment.setItemInMainHandDropChance(0.0F);
            return;
        }
        equipment.setItemInMainHand(new ItemStack(Material.BONE_MEAL), true);
        equipment.setItemInMainHandDropChance(0.0F);
    }

    public Location farthestPlot(SoulGolemData data, List<Block> blocks) {
        Block block = farmAreaService().pickFarthestFromChest(blocks, data);
        return block == null ? null : block.getLocation();
    }

    public Location boneMealPlot(SoulGolemData data, List<Block> immature) {
        Block block = farmAreaService().pickImmatureForBoneMeal(immature, data);
        return block == null ? null : block.getLocation();
    }

    public List<Block> immatureBoneMealCrops(SoulGolemData data) {
        int radius = chestService().effectiveRadius(data);
        return farmAreaService().boneMealCrops(data, radius, enabledCrops());
    }

    public boolean hasBoneMealWork(ActiveGolem golem) {
        if (!settings().farmer.useBoneMeal) {
            return false;
        }
        if (immatureBoneMealCrops(golem.data()).isEmpty()) {
            return false;
        }
        if (GolemCarried.count(golem, Material.BONE_MEAL) > 0) {
            return true;
        }
        return chestService().countItem(golem.data(), Material.BONE_MEAL) > 0;
    }

    public boolean resumeBoneMealCarried(ActiveGolem golem) {
        if (hasPlantWork(golem)) {
            return false;
        }
        if (!hasBoneMealWork(golem) || GolemCarried.count(golem, Material.BONE_MEAL) <= 0) {
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
}
