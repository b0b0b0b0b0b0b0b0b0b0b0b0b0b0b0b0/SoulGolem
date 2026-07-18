package bm.b0b0b0.SoulGolem.service.digger;

import bm.b0b0b0.SoulGolem.config.ConfigurationLoader;
import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.DiggerState;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.repository.GolemRepository;
import bm.b0b0b0.SoulGolem.service.FarmAreaService;
import bm.b0b0b0.SoulGolem.service.GolemEnergy;
import bm.b0b0b0.SoulGolem.service.GolemRegistry;
import bm.b0b0b0.SoulGolem.service.GolemSpawnService;
import bm.b0b0b0.SoulGolem.service.GolemWorkContext;
import bm.b0b0b0.SoulGolem.service.SoulChestService;
import bm.b0b0b0.SoulGolem.service.WorkAreaService;
import bm.b0b0b0.SoulGolem.util.PluginKeys;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class DiggerContext extends GolemWorkContext {

    public DiggerContext(
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

    public GolemSettings.Digger digger() {
        return settings().digger;
    }

    public SoulGolemData pitData(ActiveGolem golem) {
        UUID leaderId = golem.data().crewLeaderId();
        if (leaderId == null) {
            return golem.data();
        }
        return registry().byId(leaderId).map(ActiveGolem::data).orElse(golem.data());
    }

    public UUID pitId(ActiveGolem golem) {
        UUID leaderId = golem.data().crewLeaderId();
        return leaderId != null ? leaderId : golem.data().id();
    }

    public void markPitDirty(ActiveGolem golem) {
        SoulGolemData pit = pitData(golem);
        if (pit.id().equals(golem.data().id())) {
            golem.markDirty();
            return;
        }
        registry().byId(pit.id()).ifPresent(ActiveGolem::markDirty);
        golem.markDirty();
    }

    public boolean tryStartFeed(ActiveGolem golem) {
        return GolemEnergy.tryStartFeed(
                chestService(),
                settings(),
                golem,
                g -> g.diggerState(DiggerState.MOVING_TO_CHEST)
        );
    }

    public void eatFeedFromChest(ActiveGolem golem) {
        GolemEnergy.eatFeed(
                chestService(),
                settings(),
                golem,
                g -> g.diggerState(DiggerState.IDLE)
        );
    }

    public Material resolveDiggerShovel() {
        Material shovel = Material.matchMaterial(digger().shovelMaterial);
        if (shovel != null) {
            return shovel;
        }
        return resolveShovel();
    }

    public Material resolveDiggerPickaxe() {
        Material pickaxe = Material.matchMaterial(digger().pickaxeMaterial);
        if (pickaxe != null) {
            return pickaxe;
        }
        Material copper = Material.matchMaterial("COPPER_PICKAXE");
        return copper != null ? copper : Material.IRON_PICKAXE;
    }

    public void equipForBlock(CopperGolem copper, Material blockType) {
        EntityEquipment equipment = copper.getEquipment();
        if (equipment == null) {
            return;
        }
        Material tool = DiggerPit.isSoft(blockType) ? resolveDiggerShovel() : resolveDiggerPickaxe();
        ItemStack current = equipment.getItemInMainHand();
        if (current != null && !current.isEmpty() && current.getType() == tool) {
            equipment.setItemInMainHandDropChance(0.0F);
            return;
        }
        equipment.setItemInMainHand(new ItemStack(tool), true);
        equipment.setItemInMainHandDropChance(0.0F);
    }

    public ItemStack digTool(Material blockType) {
        return new ItemStack(DiggerPit.isSoft(blockType) ? resolveDiggerShovel() : resolveDiggerPickaxe());
    }

    public void playDigFx(Block block) {
        GolemSettings.Visuals visuals = settings().visuals;
        if (!visuals.particles) {
            if (visuals.sounds) {
                playDigSound(block);
            }
            return;
        }
        Location at = block.getLocation().add(0.5D, 0.55D, 0.5D);
        World world = block.getWorld();
        world.spawnParticle(Particle.BLOCK, at, 14, 0.28D, 0.28D, 0.28D, 0.02D, block.getBlockData());
        world.spawnParticle(Particle.CRIT, at, 6, 0.2D, 0.2D, 0.2D, 0.05D);
        if (visuals.sounds) {
            playDigSound(block);
        }
    }

    public void playDigBurst(Block block) {
        Location at = block.getLocation().add(0.5D, 0.5D, 0.5D);
        World world = block.getWorld();
        world.spawnParticle(Particle.BLOCK, at, 36, 0.35D, 0.35D, 0.35D, 0.08D, block.getBlockData());
        world.spawnParticle(Particle.CLOUD, at, 8, 0.2D, 0.2D, 0.2D, 0.02D);
        playDigSound(block);
    }

    public long digDurationTicks(Material blockType) {
        return digDurationTicks(blockType, 1.0D);
    }

    public long digDurationTicks(Material blockType, ActiveGolem golem) {
        return digDurationTicks(blockType, stickBoostFactor(golem));
    }

    public long digDurationTicks(Material blockType, double stickFactor) {
        GolemSettings.Digger digger = digger();
        long base = DiggerPit.isSoft(blockType)
                ? Math.max(1L, digger.softDigDurationTicks)
                : Math.max(1L, digger.hardDigDurationTicks);
        return Math.max(1L, (long) (base / Math.max(1.0D, stickFactor)));
    }

    public void playDepositFx(Location chestStand) {
        Location at = chestStand.clone().add(0.0D, 0.8D, 0.0D);
        World world = chestStand.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.HAPPY_VILLAGER, at, 10, 0.35D, 0.25D, 0.35D, 0.0D);
        Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft("block.chest.close"));
        if (sound != null) {
            world.playSound(at, sound, 0.5F, 1.2F);
        }
    }

    private void playDigSound(Block block) {
        GolemSettings.Visuals visuals = settings().visuals;
        if (!visuals.sounds) {
            return;
        }
        Sound sound = block.getBlockSoundGroup().getBreakSound();
        if (sound == null) {
            sound = DiggerPit.isSoft(block.getType())
                    ? Registry.SOUNDS.get(NamespacedKey.minecraft("block.gravel.break"))
                    : Registry.SOUNDS.get(NamespacedKey.minecraft("block.stone.break"));
        }
        if (sound != null) {
            float pitch = DiggerPit.isSoft(block.getType())
                    ? Math.min(2.0F, visuals.soundPitch + 0.15F)
                    : visuals.soundPitch;
            block.getWorld().playSound(block.getLocation(), sound, visuals.soundVolume, pitch);
        }
    }
}
