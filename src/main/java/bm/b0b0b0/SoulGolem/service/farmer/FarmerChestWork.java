package bm.b0b0b0.SoulGolem.service.farmer;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.FarmerState;
import bm.b0b0b0.SoulGolem.service.GolemGaze;
import bm.b0b0b0.SoulGolem.service.GolemMovement;
import bm.b0b0b0.SoulGolem.service.SoulChestLid;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.CopperGolem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class FarmerChestWork {

    private final FarmerContext ctx;
    private FarmerCycle cycle;
    private FarmerFieldWork field;
    private FarmerSupportWork support;

    public FarmerChestWork(FarmerContext ctx) {
        this.ctx = ctx;
    }

    public void wire(FarmerCycle cycle, FarmerFieldWork field, FarmerSupportWork support) {
        this.cycle = cycle;
        this.field = field;
        this.support = support;
    }

    private SoulChestLid lid() {
        return this.ctx.chestService().lid();
    }

    public void continueDeposit(ActiveGolem golem, CopperGolem copper) {
        Location chestStand = this.ctx.chestService().chestStandLocation(golem.data());
        if (chestStand == null) {
            lid().closeNow(golem.data());
            golem.farmerState(FarmerState.WAITING_SEEDS);
            return;
        }
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), chestStand) > 1.69D) {
            lid().closeNow(golem.data());
            this.ctx.movement().walkTowards(copper, chestStand, golem);
            return;
        }
        this.ctx.movement().stop(copper);
        copper.setVelocity(new Vector(0, 0, 0));
        Location chestLook = new Location(
                copper.getWorld(),
                golem.data().chestX() + 0.5D,
                golem.data().chestY() + 0.5D,
                golem.data().chestZ() + 0.5D
        );
        GolemGaze.face(golem, chestLook);
        lid().open(golem.data());

        if (golem.fetchingSeed()) {
            this.field.takeSeedFromChest(golem);
            lid().closeLater(golem.data());
            return;
        }
        if (golem.fetchingFeed()) {
            this.ctx.eatFeedFromChest(golem);
            lid().closeLater(golem.data());
            return;
        }
        if (golem.fetchingSeat()) {
            this.support.takeSeatFromChest(golem);
            lid().closeLater(golem.data());
            return;
        }
        if (golem.fetchingTorch()) {
            this.support.takeTorchesFromChest(golem);
            lid().closeLater(golem.data());
            return;
        }
        if (golem.fetchingFence() || golem.fetchingGate()) {
            this.support.takeFenceFromChest(golem);
            lid().closeLater(golem.data());
            return;
        }
        if (golem.fetchingBoneMeal()) {
            this.field.takeBoneMealFromChest(golem);
            lid().closeLater(golem.data());
            return;
        }
        if (golem.fetchingWeapon()) {
            this.ctx.combat().takeWeaponFromChest(golem, copper);
            lid().closeLater(golem.data());
            return;
        }

        if (!this.ctx.hasPlantWork(golem)
                && this.ctx.hasBoneMealWork(golem)
                && FarmerCarried.countCarried(golem, Material.BONE_MEAL) > 0) {
            lid().closeLater(golem.data());
            golem.farmerState(FarmerState.MOVING_TO_BONEMEAL);
            return;
        }

        if (golem.carried().isEmpty()) {
            lid().closeLater(golem.data());
            this.cycle.afterDeposit(golem, copper);
            return;
        }
        if (!this.ctx.chestService().hasSpace(golem.data())) {
            lid().closeLater(golem.data());
            golem.farmerState(FarmerState.WAITING_CHEST);
            this.ctx.notifyChestFull(golem);
            return;
        }
        boolean depositedAll = true;
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack stack : golem.carried()) {
            if (!this.ctx.chestService().deposit(golem.data(), stack.clone())) {
                depositedAll = false;
                leftover.add(stack.clone());
            }
        }
        golem.clearCarried();
        for (ItemStack stack : leftover) {
            golem.carry(stack);
        }
        lid().closeLater(golem.data());
        if (!depositedAll) {
            golem.farmerState(FarmerState.WAITING_CHEST);
            this.ctx.notifyChestFull(golem);
            return;
        }
        this.cycle.afterDeposit(golem, copper);
    }

    public void continueCraft(ActiveGolem golem, CopperGolem copper) {
        Location craftStand = this.ctx.chestService().craftStandLocation(golem.data());
        if (craftStand == null) {
            this.cycle.startRest(golem);
            return;
        }
        Location craftBlock = new Location(
                craftStand.getWorld(),
                Math.floor(golem.data().craftX()) + 0.5D,
                golem.data().craftY(),
                Math.floor(golem.data().craftZ()) + 0.5D
        );
        double toTable = GolemMovement.horizontalDistanceSquared(copper.getLocation(), craftBlock);
        double toStand = GolemMovement.horizontalDistanceSquared(copper.getLocation(), craftStand);
        if (toTable > 2.25D && toStand > 1.0D) {
            golem.farmerState(FarmerState.MOVING_TO_CRAFT);
            this.ctx.movement().walkTowards(copper, craftStand, golem);
            return;
        }
        if (toTable > 2.25D) {
            copper.teleport(craftStand);
        }
        this.ctx.movement().stop(copper);
        copper.setVelocity(new Vector(0, 0, 0));
        golem.farmerState(FarmerState.CRAFTING);
        GolemGaze.face(golem, craftBlock.clone().add(0.0D, 0.5D, 0.0D));
        lid().run(golem.data(), () -> {
            while (this.ctx.settings().farmer.craftBread
                    && this.ctx.chestService().countItem(golem.data(), Material.WHEAT) >= 3
                    && this.ctx.chestService().hasSpace(golem.data())) {
                if (!this.ctx.chestService().craftBread(golem.data())) {
                    break;
                }
            }
        });
        golem.markDirty();
        this.cycle.afterDeposit(golem, copper);
    }
}
