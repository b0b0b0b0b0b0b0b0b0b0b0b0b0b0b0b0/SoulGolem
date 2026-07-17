package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.FarmerState;
import bm.b0b0b0.SoulGolem.model.GolemType;
import bm.b0b0b0.SoulGolem.model.MinerState;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class GolemCombatWork {

    private static final double REACH_SQ = 6.25D;

    private final SoulChestService chestService;
    private final WorkAreaService workAreaService;
    private final GolemMovement movement;
    private final Supplier<Settings> settings;

    public GolemCombatWork(
            SoulChestService chestService,
            WorkAreaService workAreaService,
            GolemMovement movement,
            Supplier<Settings> settings
    ) {
        this.chestService = chestService;
        this.workAreaService = workAreaService;
        this.movement = movement;
        this.settings = settings;
    }

    public static boolean isWeapon(Material material) {
        if (material == null || material.isAir()) {
            return false;
        }
        return Tag.ITEMS_SWORDS.isTagged(material) || Tag.ITEMS_AXES.isTagged(material);
    }

    public boolean enabled() {
        return this.settings.get().combat.enabled;
    }

    public boolean isCombatState(ActiveGolem golem) {
        if (golem.data().type() == GolemType.FARMER) {
            FarmerState state = golem.farmerState();
            return state == FarmerState.MOVING_TO_COMBAT || state == FarmerState.COMBATING;
        }
        MinerState state = golem.state();
        return state == MinerState.MOVING_TO_COMBAT || state == MinerState.COMBATING;
    }

    public boolean tryStart(ActiveGolem golem, CopperGolem copper) {
        if (!enabled() || golem.data().paused() || golem.data().energy() <= 0) {
            return false;
        }
        if (isCombatState(golem) || golem.fetchingWeapon()) {
            return false;
        }
        if (busyOtherFetch(golem)) {
            return false;
        }
        LivingEntity target = findTarget(golem, copper);
        if (target == null) {
            return false;
        }
        Material weapon = resolveWeapon(golem, copper);
        if (weapon == null) {
            Material inChest = findWeaponInChest(golem.data());
            if (inChest == null) {
                return false;
            }
            golem.clearFetchFlags();
            golem.fetchingWeapon(true);
            golem.combatTarget(target.getUniqueId());
            golem.wanderTarget(null);
            setMovingToChest(golem);
            return true;
        }
        armAndFight(golem, copper, weapon, target);
        return true;
    }

    public void continueCombat(ActiveGolem golem, CopperGolem copper) {
        if (!enabled()) {
            finishCombat(golem, copper);
            return;
        }
        Material weapon = golem.combatWeapon();
        if (weapon == null || !isWeapon(weapon)) {
            weapon = resolveWeapon(golem, copper);
            if (weapon == null) {
                finishCombat(golem, copper);
                return;
            }
            golem.combatWeapon(weapon);
        }
        equipWeapon(copper, weapon);

        LivingEntity target = resolveTarget(golem, copper);
        if (target == null) {
            finishCombat(golem, copper);
            return;
        }
        golem.combatTarget(target.getUniqueId());
        setCombatState(golem, copper, target);

        Location goal = clampToTerritory(golem.data(), target.getLocation());
        if (goal == null) {
            finishCombat(golem, copper);
            return;
        }

        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), target.getLocation()) <= REACH_SQ
                && inTerritory(golem.data(), copper.getLocation().getBlock())) {
            copper.setVelocity(new Vector(0, copper.getVelocity().getY(), 0));
            GolemGaze.faceEntity(golem, target);
            tryAttack(golem, copper, target, weapon);
            return;
        }

        setMovingCombat(golem);
        this.movement.walkTowards(copper, goal, golem);
        keepInside(golem, copper);
    }

    public void takeWeaponFromChest(ActiveGolem golem, CopperGolem copper) {
        Material weapon = findWeaponInChest(golem.data());
        if (weapon == null || !this.chestService.takeItem(golem.data(), weapon, 1)) {
            golem.fetchingWeapon(false);
            golem.combatTarget(null);
            resumeWork(golem);
            return;
        }
        golem.fetchingWeapon(false);
        LivingEntity target = resolveTarget(golem, copper);
        if (target == null) {
            target = findTarget(golem, copper);
        }
        if (target == null) {
            this.chestService.deposit(golem.data(), new ItemStack(weapon, 1));
            golem.combatWeapon(null);
            resumeWork(golem);
            return;
        }
        armAndFight(golem, copper, weapon, target);
    }

    public boolean acceptWeapon(ActiveGolem golem, CopperGolem copper, Player player, ItemStack hand) {
        if (!enabled() || hand == null || hand.getType().isAir() || !isWeapon(hand.getType())) {
            return false;
        }
        Material weapon = hand.getType();
        hand.setAmount(hand.getAmount() - 1);
        if (golem.combatWeapon() != null && isWeapon(golem.combatWeapon())) {
            this.chestService.deposit(golem.data(), new ItemStack(golem.combatWeapon(), 1));
            golem.combatWeapon(null);
        }
        golem.clearFetchFlags();
        LivingEntity target = findTarget(golem, copper);
        if (target != null) {
            armAndFight(golem, copper, weapon, target);
        } else {
            golem.carry(new ItemStack(weapon, 1));
        }
        golem.markDirty();
        return true;
    }

    public void equipIfArmed(ActiveGolem golem, CopperGolem copper) {
        if (!isCombatState(golem)) {
            return;
        }
        Material weapon = golem.combatWeapon();
        if (weapon != null && isWeapon(weapon)) {
            equipWeapon(copper, weapon);
        }
    }

    private void armAndFight(ActiveGolem golem, CopperGolem copper, Material weapon, LivingEntity target) {
        consumeWeaponFromCarried(golem, weapon);
        golem.fetchingWeapon(false);
        golem.combatWeapon(weapon);
        golem.combatTarget(target.getUniqueId());
        golem.wanderTarget(null);
        equipWeapon(copper, weapon);
        setCombatState(golem, copper, target);
        golem.markDirty();
    }

    private void finishCombat(ActiveGolem golem, CopperGolem copper) {
        Material weapon = golem.combatWeapon();
        golem.combatTarget(null);
        golem.combatWeapon(null);
        golem.fetchingWeapon(false);
        golem.lastCombatAttackAt(0L);
        if (weapon != null && isWeapon(weapon)) {
            if (!this.chestService.deposit(golem.data(), new ItemStack(weapon, 1))) {
                golem.carry(new ItemStack(weapon, 1));
            }
        }
        resumeWork(golem);
        golem.markDirty();
    }

    private void resumeWork(ActiveGolem golem) {
        if (golem.data().type() == GolemType.FARMER) {
            golem.farmerState(FarmerState.WAITING_SEEDS);
        } else {
            golem.state(MinerState.IDLE);
        }
        golem.data().lastActionAt(System.currentTimeMillis());
    }

    private void setMovingToChest(ActiveGolem golem) {
        if (golem.data().type() == GolemType.FARMER) {
            golem.farmerState(FarmerState.MOVING_TO_CHEST);
        } else {
            golem.state(MinerState.MOVING_TO_CHEST);
        }
    }

    private void setMovingCombat(ActiveGolem golem) {
        if (golem.data().type() == GolemType.FARMER) {
            golem.farmerState(FarmerState.MOVING_TO_COMBAT);
        } else {
            golem.state(MinerState.MOVING_TO_COMBAT);
        }
    }

    private void setCombatState(ActiveGolem golem, CopperGolem copper, LivingEntity target) {
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), target.getLocation()) <= REACH_SQ) {
            if (golem.data().type() == GolemType.FARMER) {
                golem.farmerState(FarmerState.COMBATING);
            } else {
                golem.state(MinerState.COMBATING);
            }
        } else {
            setMovingCombat(golem);
        }
    }

    private void tryAttack(ActiveGolem golem, CopperGolem copper, LivingEntity target, Material weapon) {
        Settings.Combat combat = this.settings.get().combat;
        long now = System.currentTimeMillis();
        if (now - golem.lastCombatAttackAt() < Math.max(200L, combat.attackCooldownMs)) {
            return;
        }
        golem.lastCombatAttackAt(now);
        copper.swingMainHand();
        target.damage(damageFor(weapon, combat), copper);
    }

    private static double damageFor(Material weapon, Settings.Combat combat) {
        String name = weapon.name();
        double base = combat.damageWood;
        if (name.startsWith("NETHERITE_")) {
            base = combat.damageNetherite;
        } else if (name.startsWith("DIAMOND_")) {
            base = combat.damageDiamond;
        } else if (name.startsWith("IRON_")) {
            base = combat.damageIron;
        } else if (name.startsWith("STONE_") || name.startsWith("COPPER_")) {
            base = combat.damageStone;
        } else if (name.startsWith("GOLDEN_")) {
            base = combat.damageGold;
        }
        if (Tag.ITEMS_AXES.isTagged(weapon)) {
            base += combat.axeBonus;
        }
        return Math.max(1.0D, base);
    }

    private LivingEntity resolveTarget(ActiveGolem golem, CopperGolem copper) {
        UUID id = golem.combatTarget();
        if (id != null) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof LivingEntity living
                    && living.isValid()
                    && !living.isDead()
                    && isCombatTarget(living)
                    && inTerritory(golem.data(), living.getLocation().getBlock())) {
                return living;
            }
        }
        return findTarget(golem, copper);
    }

    private LivingEntity findTarget(ActiveGolem golem, CopperGolem copper) {
        SoulGolemData data = golem.data();
        int radius = this.chestService.effectiveRadius(data);
        Location home = this.workAreaService.homeLocation(data);
        if (home == null || home.getWorld() == null) {
            return null;
        }
        World world = home.getWorld();
        double box = radius + 1.5D;
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        Location from = copper.getLocation();
        for (Entity entity : world.getNearbyEntities(home, box, 6.0D, box)) {
            if (!(entity instanceof LivingEntity living) || !isCombatTarget(living)) {
                continue;
            }
            if (!inTerritory(data, living.getLocation().getBlock())) {
                continue;
            }
            double dist = GolemMovement.horizontalDistanceSquared(from, living.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                best = living;
            }
        }
        return best;
    }

    private boolean inTerritory(SoulGolemData data, Block block) {
        return this.workAreaService.containsBlock(data, this.chestService.effectiveRadius(data), block);
    }

    private Location clampToTerritory(SoulGolemData data, Location target) {
        if (target == null || target.getWorld() == null) {
            return null;
        }
        int radius = this.chestService.effectiveRadius(data);
        int homeX = (int) Math.floor(data.homeX());
        int homeZ = (int) Math.floor(data.homeZ());
        int x = target.getBlockX();
        int z = target.getBlockZ();
        x = Math.max(homeX - radius, Math.min(homeX + radius, x));
        z = Math.max(homeZ - radius, Math.min(homeZ + radius, z));
        Block ground = target.getWorld().getBlockAt(x, (int) Math.floor(data.homeY()), z);
        if (!inTerritory(data, ground)) {
            return null;
        }
        return new Location(target.getWorld(), x + 0.5D, target.getY(), z + 0.5D);
    }

    private void keepInside(ActiveGolem golem, CopperGolem copper) {
        Block feet = copper.getLocation().getBlock();
        if (inTerritory(golem.data(), feet)) {
            return;
        }
        Location safe = this.workAreaService.homeLocation(golem.data());
        if (safe == null) {
            return;
        }
        Location stand = safe.clone().add(0.0D, 1.0D, 0.0D);
        copper.teleport(stand);
    }

    private Material resolveWeapon(ActiveGolem golem, CopperGolem copper) {
        if (golem.combatWeapon() != null && isWeapon(golem.combatWeapon())) {
            return golem.combatWeapon();
        }
        Material carried = findWeaponInCarried(golem);
        if (carried != null) {
            return carried;
        }
        EntityEquipment equipment = copper.getEquipment();
        if (equipment != null) {
            ItemStack hand = equipment.getItemInMainHand();
            if (hand != null && isWeapon(hand.getType())) {
                return hand.getType();
            }
        }
        return null;
    }

    public Material findWeaponInCarried(ActiveGolem golem) {
        for (ItemStack stack : golem.carried()) {
            if (stack != null && isWeapon(stack.getType())) {
                return stack.getType();
            }
        }
        return null;
    }

    public Material findWeaponInChest(SoulGolemData data) {
        return this.chestService.findBestCombatWeapon(data);
    }

    public static int weaponScore(Material material) {
        String name = material.name();
        int tier = 1;
        if (name.startsWith("NETHERITE_")) {
            tier = 6;
        } else if (name.startsWith("DIAMOND_")) {
            tier = 5;
        } else if (name.startsWith("IRON_")) {
            tier = 4;
        } else if (name.startsWith("STONE_") || name.startsWith("COPPER_")) {
            tier = 3;
        } else if (name.startsWith("GOLDEN_")) {
            tier = 2;
        }
        if (Tag.ITEMS_SWORDS.isTagged(material)) {
            tier += 1;
        }
        return tier;
    }

    private void consumeWeaponFromCarried(ActiveGolem golem, Material weapon) {
        List<ItemStack> leftover = new ArrayList<>();
        boolean removed = false;
        for (ItemStack stack : golem.carried()) {
            if (!removed && stack != null && stack.getType() == weapon) {
                ItemStack copy = stack.clone();
                copy.setAmount(copy.getAmount() - 1);
                if (copy.getAmount() > 0) {
                    leftover.add(copy);
                }
                removed = true;
            } else if (stack != null && !stack.getType().isAir()) {
                leftover.add(stack.clone());
            }
        }
        if (!removed) {
            return;
        }
        golem.clearCarried();
        for (ItemStack stack : leftover) {
            golem.carry(stack);
        }
    }

    private static void equipWeapon(CopperGolem copper, Material weapon) {
        EntityEquipment equipment = copper.getEquipment();
        if (equipment == null) {
            return;
        }
        ItemStack current = equipment.getItemInMainHand();
        if (current != null && current.getType() == weapon) {
            equipment.setItemInMainHandDropChance(0.0F);
            return;
        }
        equipment.setItemInMainHand(new ItemStack(weapon), true);
        equipment.setItemInMainHandDropChance(0.0F);
    }

    private static boolean isCombatTarget(LivingEntity entity) {
        if (entity instanceof Player
                || entity instanceof ArmorStand
                || entity instanceof CopperGolem
                || entity instanceof IronGolem
                || entity instanceof AbstractVillager) {
            return false;
        }
        return entity.isValid() && !entity.isDead();
    }

    private static boolean busyOtherFetch(ActiveGolem golem) {
        return golem.fetchingSeed()
                || golem.fetchingTorch()
                || golem.fetchingSeat()
                || golem.fetchingFence()
                || golem.fetchingGate()
                || golem.fetchingBoneMeal()
                || golem.fetchingComposter()
                || golem.fetchingCompost()
                || golem.fetchingFeed()
                || golem.fetchingPickaxe();
    }
}
