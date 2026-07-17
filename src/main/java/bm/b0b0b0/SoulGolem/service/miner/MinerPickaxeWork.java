package bm.b0b0b0.SoulGolem.service.miner;

import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.model.MinerState;
import bm.b0b0b0.SoulGolem.service.SoulChestService;
import bm.b0b0b0.SoulGolem.util.GolemUpgrades;
import org.bukkit.Material;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

public final class MinerPickaxeWork {

    private final SoulChestService chestService;
    private final MinerContext ctx;

    public MinerPickaxeWork(MinerContext ctx, SoulChestService chestService) {
        this.ctx = ctx;
        this.chestService = chestService;
    }

    public void loadFromData(ActiveGolem golem) {
        Material stored = GolemUpgrades.readPickaxe(golem.data().upgradesJson());
        if (isUpgradePickaxe(stored)) {
            golem.upgradePickaxe(stored);
        }
    }

    public void persist(ActiveGolem golem) {
        golem.data().upgradesJson(GolemUpgrades.writePickaxe(golem.data().upgradesJson(), golem.upgradePickaxe()));
        golem.markDirty();
    }

    public static boolean isUpgradePickaxe(Material material) {
        return material == Material.IRON_PICKAXE
                || material == Material.DIAMOND_PICKAXE
                || material == Material.NETHERITE_PICKAXE;
    }

    public static int tier(Material material) {
        if (material == Material.NETHERITE_PICKAXE) {
            return 4;
        }
        if (material == Material.DIAMOND_PICKAXE) {
            return 3;
        }
        if (material == Material.IRON_PICKAXE) {
            return 2;
        }
        return 1;
    }

    public int blocksPerTrip(ActiveGolem golem) {
        return tier(effectivePickaxe(golem));
    }

    public Material effectivePickaxe(ActiveGolem golem) {
        Material upgrade = golem.upgradePickaxe();
        if (isUpgradePickaxe(upgrade)) {
            return upgrade;
        }
        Settings settings = this.ctx.settings();
        Material copper = Material.matchMaterial(settings.pickaxeMaterial);
        if (copper != null) {
            return copper;
        }
        Material fallback = Material.matchMaterial("COPPER_PICKAXE");
        return fallback != null ? fallback : Material.IRON_PICKAXE;
    }

    public Material bestInChest(SoulGolemData data) {
        Material best = null;
        int bestTier = 0;
        for (Material candidate : new Material[]{
                Material.IRON_PICKAXE,
                Material.DIAMOND_PICKAXE,
                Material.NETHERITE_PICKAXE
        }) {
            if (this.chestService.countItem(data, candidate) <= 0) {
                continue;
            }
            int t = tier(candidate);
            if (t > bestTier) {
                bestTier = t;
                best = candidate;
            }
        }
        return best;
    }

    public boolean tryStartUpgrade(ActiveGolem golem) {
        if (!this.ctx.settings().miner.pickaxeUpgrades) {
            return false;
        }
        Material chestBest = bestInChest(golem.data());
        if (chestBest == null) {
            return false;
        }
        Material held = golem.upgradePickaxe();
        if (tier(chestBest) <= tier(held)) {
            return false;
        }
        golem.fetchingPickaxe(true);
        golem.state(MinerState.MOVING_TO_CHEST);
        return true;
    }

    public void swapAtChest(ActiveGolem golem) {
        Material chestBest = bestInChest(golem.data());
        Material held = golem.upgradePickaxe();
        if (chestBest == null || tier(chestBest) <= tier(held)) {
            golem.fetchingPickaxe(false);
            golem.state(MinerState.IDLE);
            return;
        }
        if (held != null) {
            if (!this.chestService.hasSpace(golem.data())) {
                notifyChestFullForPickaxe(golem);
                golem.fetchingPickaxe(false);
                golem.state(MinerState.WAITING_CHEST);
                return;
            }
            if (!this.chestService.deposit(golem.data(), new ItemStack(held, 1))) {
                notifyChestFullForPickaxe(golem);
                golem.fetchingPickaxe(false);
                golem.state(MinerState.WAITING_CHEST);
                return;
            }
            golem.upgradePickaxe(null);
        }
        if (!this.chestService.takeItem(golem.data(), chestBest, 1)) {
            golem.fetchingPickaxe(false);
            golem.state(MinerState.IDLE);
            return;
        }
        golem.upgradePickaxe(chestBest);
        golem.pickaxeSwapBlocked(false);
        golem.chestFullNotified(false);
        persist(golem);
        golem.fetchingPickaxe(false);
        golem.blocksLeftThisTrip(0);
        golem.state(MinerState.IDLE);
    }

    public void equip(CopperGolem copper, ActiveGolem golem) {
        Material tool = effectivePickaxe(golem);
        var equipment = copper.getEquipment();
        if (equipment == null) {
            return;
        }
        ItemStack current = equipment.getItemInMainHand();
        if (current != null && !current.isEmpty() && current.getType() == tool) {
            equipment.setItemInMainHandDropChance(0.0F);
            equipment.setItemInOffHandDropChance(0.0F);
            return;
        }
        equipment.setItemInMainHand(new ItemStack(tool), true);
        equipment.setItemInOffHand(ItemStack.empty(), true);
        equipment.setItemInMainHandDropChance(0.0F);
        equipment.setItemInOffHandDropChance(0.0F);
    }

    public ItemStack miningTool(ActiveGolem golem) {
        ItemStack stack = new ItemStack(effectivePickaxe(golem));
        if (stack.getItemMeta() instanceof Damageable damageable) {
            damageable.setDamage(0);
            stack.setItemMeta(damageable);
        }
        return stack;
    }

    private void notifyChestFullForPickaxe(ActiveGolem golem) {
        golem.pickaxeSwapBlocked(true);
        if (golem.chestFullNotified()) {
            return;
        }
        golem.chestFullNotified(true);
        Player owner = org.bukkit.Bukkit.getPlayer(golem.data().ownerUuid());
        if (owner != null) {
            this.ctx.messages().send(owner, "chest-full-pickaxe-swap");
        }
    }
}
