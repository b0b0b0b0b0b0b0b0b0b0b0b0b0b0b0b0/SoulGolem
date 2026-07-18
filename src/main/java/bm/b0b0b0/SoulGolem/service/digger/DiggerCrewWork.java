package bm.b0b0b0.SoulGolem.service.digger;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.DiggerState;
import bm.b0b0b0.SoulGolem.model.GolemType;
import bm.b0b0b0.SoulGolem.model.SetupPhase;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.scheduler.PluginSchedulers;
import bm.b0b0b0.SoulGolem.service.GolemDisplay;
import bm.b0b0b0.SoulGolem.service.GolemSpawnService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.CopperGolem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class DiggerCrewWork {

    private static final String[] STATUE_MATERIALS = {
            "COPPER_GOLEM_STATUE",
            "EXPOSED_COPPER_GOLEM_STATUE",
            "WEATHERED_COPPER_GOLEM_STATUE",
            "OXIDIZED_COPPER_GOLEM_STATUE",
            "WAXED_COPPER_GOLEM_STATUE",
            "WAXED_EXPOSED_COPPER_GOLEM_STATUE",
            "WAXED_WEATHERED_COPPER_GOLEM_STATUE",
            "WAXED_OXIDIZED_COPPER_GOLEM_STATUE"
    };

    private final DiggerContext ctx;

    public DiggerCrewWork(DiggerContext ctx) {
        this.ctx = ctx;
    }

    public boolean tryHireFromChest(ActiveGolem leader, CopperGolem copper) {
        if (leader.data().type() != GolemType.DIGGER || leader.data().isCrewHelper()) {
            return false;
        }
        if (!leader.setupComplete()) {
            return false;
        }
        SoulGolemData pit = this.ctx.pitData(leader);
        if (DiggerDigWork.isPitComplete(pit, this.ctx.digger())) {
            return false;
        }
        int max = Math.max(1, this.ctx.digger().maxCrew);
        if (crewSize(leader.data().id()) >= max) {
            return false;
        }
        Material statue = findStatueInChest(leader.data());
        if (statue == null) {
            return false;
        }
        if (!this.ctx.chestService().takeItem(leader.data(), statue, 1)) {
            return false;
        }
        spawnHelper(leader, copper, statue);
        return true;
    }

    public int crewSize(UUID leaderId) {
        return listCrew(leaderId).size();
    }

    public List<ActiveGolem> listCrew(UUID leaderId) {
        List<ActiveGolem> helpers = new ArrayList<>();
        ActiveGolem leader = null;
        for (ActiveGolem other : this.ctx.registry().all()) {
            if (other.data().type() != GolemType.DIGGER) {
                continue;
            }
            if (other.data().id().equals(leaderId) && !other.data().isCrewHelper()) {
                leader = other;
            } else if (leaderId.equals(other.data().crewLeaderId())) {
                helpers.add(other);
            }
        }
        helpers.sort(Comparator.comparing(g -> g.data().id()));
        List<ActiveGolem> crew = new ArrayList<>(1 + helpers.size());
        if (leader != null) {
            crew.add(leader);
        }
        crew.addAll(helpers);
        if (crew.isEmpty()) {
            this.ctx.registry().byId(leaderId).ifPresent(crew::add);
        }
        return crew;
    }

    public int crewIndex(ActiveGolem golem) {
        UUID leaderId = golem.data().isCrewHelper() ? golem.data().crewLeaderId() : golem.data().id();
        if (leaderId == null) {
            return 0;
        }
        List<ActiveGolem> crew = listCrew(leaderId);
        for (int i = 0; i < crew.size(); i++) {
            if (crew.get(i).data().id().equals(golem.data().id())) {
                return i;
            }
        }
        return Math.max(0, crew.size() - 1);
    }

    private Material findStatueInChest(SoulGolemData data) {
        for (String name : STATUE_MATERIALS) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                continue;
            }
            if (this.ctx.chestService().countItem(data, material) > 0) {
                return material;
            }
        }
        return this.ctx.chestService().findCopperGolemStatue(data);
    }

    private void spawnHelper(ActiveGolem leader, CopperGolem leaderEntity, Material statueMat) {
        SoulGolemData leaderData = leader.data();
        Location stand = this.ctx.chestService().chestStandLocation(leaderData);
        if (stand == null || stand.getWorld() == null) {
            stand = this.ctx.workAreaService().homeLocation(leaderData);
        }
        if (stand == null || stand.getWorld() == null) {
            this.ctx.chestService().deposit(leaderData, new ItemStack(statueMat, 1));
            return;
        }

        UUID helperId = UUID.randomUUID();
        SoulGolemData data = new SoulGolemData(helperId);
        data.ownerUuid(leaderData.ownerUuid());
        data.type(GolemType.DIGGER);
        data.worldName(leaderData.worldName());
        data.home(leaderData.homeX(), leaderData.homeY(), leaderData.homeZ());
        data.chestPosition(leaderData.chestX(), leaderData.chestY(), leaderData.chestZ());
        if (leaderData.hasCraftStation()) {
            data.craftPosition(leaderData.craftX(), leaderData.craftY(), leaderData.craftZ());
        }
        data.position(stand.getX(), stand.getY() + 0.2D, stand.getZ());
        data.rotation(leaderEntity.getLocation().getYaw(), 0.0D);
        data.level(leaderData.level());
        data.energy(this.ctx.settings().energyCapacity);
        data.paused(false);
        data.crewLeaderId(leaderData.id());
        DiggerDigWork.ensureDigProgress(leaderData, this.ctx.digger(), this.ctx.farmAreaService());

        Location spawnAt = stand.clone().add(0.0D, 0.15D, 0.0D);
        playHireFx(spawnAt);

        CopperGolem entity = spawnAt.getWorld().spawn(spawnAt, CopperGolem.class, golem -> {
            GolemSpawnService.applySoulEntityFlags(golem, GolemType.DIGGER);
            GolemSpawnService.applyDiggerLeaderGlow(golem, data);
            golem.setAware(false);
            org.bukkit.Bukkit.getMobGoals().removeAllGoals(golem);
            GolemSpawnService.clearHand(golem);
            GolemSpawnService.equipTool(golem, GolemType.DIGGER, this.ctx.settings());
            golem.getPersistentDataContainer().set(this.ctx.keys().golemId(), PersistentDataType.STRING, helperId.toString());
            golem.getPersistentDataContainer().set(this.ctx.keys().owner(), PersistentDataType.STRING, data.ownerUuid().toString());
            golem.setGravity(false);
            golem.setVelocity(new org.bukkit.util.Vector(0, 0.08D, 0));
        });
        data.entityUuid(entity.getUniqueId());

        ActiveGolem active = this.ctx.registry().wrap(data);
        active.setupComplete(true);
        active.setupPhase(SetupPhase.DONE);
        active.diggerState(DiggerState.IDLE);
        this.ctx.registry().register(active);
        this.ctx.registry().bindEntity(data.id(), entity.getUniqueId());
        this.ctx.spawnService().gazeService().ensure(entity);
        GolemDisplay.refreshForce(
                active,
                entity,
                this.ctx.messages(),
                this.ctx.keys(),
                this.ctx.settings().visuals.textDisplays
        );
        active.markDirty();
        this.ctx.repository().save(data);

        PluginSchedulers.runAtLater(this.ctx.plugin(), spawnAt, () -> {
            if (entity.isValid()) {
                entity.setGravity(true);
                entity.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
                playHireLandFx(entity.getLocation());
            }
        }, 18L);

        org.bukkit.entity.Player owner = org.bukkit.Bukkit.getPlayer(leaderData.ownerUuid());
        if (owner != null) {
            this.ctx.messages().send(owner, "digger-crew-hired");
        }
    }

    private void playHireFx(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.END_ROD, at.clone().add(0, 0.6D, 0), 28, 0.35D, 0.55D, 0.35D, 0.02D);
        world.spawnParticle(Particle.HAPPY_VILLAGER, at, 16, 0.4D, 0.4D, 0.4D, 0.0D);
        world.spawnParticle(
                Particle.DUST,
                at.clone().add(0, 0.4D, 0),
                24,
                0.35D,
                0.45D,
                0.35D,
                new Particle.DustOptions(Color.fromRGB(192, 132, 252), 1.35F)
        );
        Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft("block.respawn_anchor.charge"));
        if (sound == null) {
            sound = Registry.SOUNDS.get(NamespacedKey.minecraft("entity.evoker.cast_spell"));
        }
        if (sound != null) {
            world.playSound(at, sound, 0.7F, 1.35F);
        }
    }

    private void playHireLandFx(Location at) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.CLOUD, at, 10, 0.25D, 0.1D, 0.25D, 0.02D);
        Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft("entity.copper_golem.spawn"));
        if (sound == null) {
            sound = Registry.SOUNDS.get(NamespacedKey.minecraft("block.copper.place"));
        }
        if (sound != null) {
            world.playSound(at, sound, 0.8F, 1.1F);
        }
    }

    public void dismissHelper(ActiveGolem helper, CopperGolem copper) {
        if (helper == null || !helper.data().isCrewHelper()) {
            return;
        }
        SoulGolemData pit = this.ctx.pitData(helper);
        Location at = copper != null && copper.isValid()
                ? copper.getLocation()
                : this.ctx.chestService().chestStandLocation(pit);
        for (ItemStack stack : new ArrayList<>(helper.carried())) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (!this.ctx.chestService().deposit(pit, stack.clone())) {
                if (at != null && at.getWorld() != null) {
                    at.getWorld().dropItemNaturally(at, stack.clone());
                }
            }
        }
        helper.clearCarried();
        ItemStack statue = this.ctx.spawnService().createStatue(GolemType.DIGGER);
        if (!this.ctx.chestService().deposit(pit, statue)) {
            if (at != null && at.getWorld() != null) {
                at.getWorld().dropItemNaturally(at, statue);
            }
        }
        playDismissFx(at);
        this.ctx.spawnService().removeCrewHelper(helper);
        org.bukkit.entity.Player owner = org.bukkit.Bukkit.getPlayer(pit.ownerUuid());
        if (owner != null) {
            this.ctx.messages().send(owner, "digger-crew-returned");
        }
    }

    private void playDismissFx(Location at) {
        if (at == null || at.getWorld() == null) {
            return;
        }
        World world = at.getWorld();
        world.spawnParticle(Particle.POOF, at.clone().add(0, 0.5D, 0), 18, 0.3D, 0.4D, 0.3D, 0.02D);
        world.spawnParticle(Particle.END_ROD, at.clone().add(0, 0.4D, 0), 12, 0.2D, 0.35D, 0.2D, 0.01D);
        Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft("entity.item.pickup"));
        if (sound == null) {
            sound = Registry.SOUNDS.get(NamespacedKey.minecraft("block.chest.close"));
        }
        if (sound != null) {
            world.playSound(at, sound, 0.8F, 1.2F);
        }
    }
}
