package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import java.util.function.Supplier;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.CopperGolem;
import org.bukkit.util.Vector;

public final class SoulChestLink {

    public enum Kind {
        DEPOSIT,
        WITHDRAW
    }

    private final SoulChestService chests;
    private final Supplier<GolemSettings> settings;

    public SoulChestLink(SoulChestService chests, Supplier<GolemSettings> settings) {
        this.chests = chests;
        this.settings = settings;
    }

    public boolean isLinked(SoulGolemData data) {
        GolemSettings golems = this.settings.get();
        if (golems == null || !golems.chestLinkEnabled || data == null) {
            return false;
        }
        Material link = Material.matchMaterial(golems.chestLinkItem);
        if (link == null) {
            link = Material.HOPPER;
        }
        return this.chests.countItem(data, link) > 0;
    }

    public boolean canAccess(CopperGolem copper, SoulGolemData data) {
        if (isLinked(data)) {
            return true;
        }
        return isNearChest(copper, data);
    }

    public boolean isNearChest(CopperGolem copper, SoulGolemData data) {
        if (copper == null || data == null) {
            return false;
        }
        Location stand = this.chests.chestStandLocation(data);
        if (stand == null) {
            return false;
        }
        return GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) <= 1.69D
                && Math.abs(copper.getLocation().getY() - stand.getY()) <= 2.5D;
    }

    public void play(CopperGolem copper, SoulGolemData data, Kind kind) {
        if (copper == null || !copper.isValid() || data == null) {
            return;
        }
        Location golemAt = copper.getLocation().clone().add(0.0D, 0.85D, 0.0D);
        Location chestAt = new Location(
                copper.getWorld(),
                data.chestX() + 0.5D,
                data.chestY() + 0.65D,
                data.chestZ() + 0.5D
        );
        if (golemAt.getWorld() == null || chestAt.getWorld() == null) {
            return;
        }
        if (kind == Kind.DEPOSIT) {
            beam(golemAt, chestAt, true);
            burst(golemAt, false);
            burst(chestAt, true);
            sound(golemAt, true);
        } else {
            beam(chestAt, golemAt, false);
            burst(chestAt, false);
            burst(golemAt, true);
            sound(golemAt, false);
        }
        this.chests.lid().open(data);
        this.chests.lid().closeLater(data);
    }

    private void beam(Location from, Location to, boolean deposit) {
        World world = from.getWorld();
        if (world == null || to.getWorld() == null || !world.equals(to.getWorld())) {
            return;
        }
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length < 0.15D) {
            return;
        }
        Vector step = delta.normalize().multiply(0.28D);
        int steps = Math.max(1, (int) Math.ceil(length / 0.28D));
        Particle.DustOptions dust = deposit
                ? new Particle.DustOptions(Color.fromRGB(251, 191, 36), 1.15F)
                : new Particle.DustOptions(Color.fromRGB(192, 132, 252), 1.15F);
        Location cursor = from.clone();
        for (int i = 0; i <= steps; i++) {
            world.spawnParticle(Particle.DUST, cursor, 2, 0.02D, 0.02D, 0.02D, 0.0D, dust);
            world.spawnParticle(Particle.END_ROD, cursor, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            if (deposit) {
                world.spawnParticle(Particle.CRIT, cursor, 1, 0.04D, 0.04D, 0.04D, 0.0D);
            } else {
                world.spawnParticle(Particle.ENCHANT, cursor, 2, 0.05D, 0.05D, 0.05D, 0.0D);
            }
            cursor.add(step);
        }
    }

    private void burst(Location at, boolean receive) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        if (receive) {
            world.spawnParticle(Particle.HAPPY_VILLAGER, at, 14, 0.35D, 0.4D, 0.35D, 0.0D);
            world.spawnParticle(Particle.FIREWORK, at, 10, 0.2D, 0.3D, 0.2D, 0.01D);
            world.spawnParticle(
                    Particle.DUST,
                    at,
                    18,
                    0.3D,
                    0.35D,
                    0.3D,
                    new Particle.DustOptions(Color.fromRGB(253, 224, 71), 1.4F)
            );
        } else {
            world.spawnParticle(Particle.CLOUD, at, 8, 0.2D, 0.25D, 0.2D, 0.01D);
            world.spawnParticle(Particle.WITCH, at, 10, 0.25D, 0.3D, 0.25D, 0.0D);
            world.spawnParticle(
                    Particle.DUST,
                    at,
                    14,
                    0.25D,
                    0.3D,
                    0.25D,
                    new Particle.DustOptions(Color.fromRGB(167, 139, 250), 1.25F)
            );
        }
    }

    private void sound(Location at, boolean deposit) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        Sound primary = Registry.SOUNDS.get(NamespacedKey.minecraft(
                deposit ? "block.copper_chest.close" : "block.copper_chest.open"
        ));
        if (primary == null) {
            primary = Registry.SOUNDS.get(NamespacedKey.minecraft(
                    deposit ? "block.chest.close" : "block.chest.open"
            ));
        }
        if (primary != null) {
            world.playSound(at, primary, 0.55F, deposit ? 1.35F : 1.15F);
        }
        Sound chime = Registry.SOUNDS.get(NamespacedKey.minecraft(
                deposit ? "block.amethyst_block.resonate" : "block.amethyst_block.chime"
        ));
        if (chime == null) {
            chime = Registry.SOUNDS.get(NamespacedKey.minecraft("entity.experience_orb.pickup"));
        }
        if (chime != null) {
            world.playSound(at, chime, 0.45F, deposit ? 1.55F : 1.75F);
        }
        Sound whoosh = Registry.SOUNDS.get(NamespacedKey.minecraft("entity.breeze.wind_burst"));
        if (whoosh == null) {
            whoosh = Registry.SOUNDS.get(NamespacedKey.minecraft("item.trident.riptide_1"));
        }
        if (whoosh != null) {
            world.playSound(at, whoosh, 0.25F, deposit ? 1.4F : 1.6F);
        }
    }
}
