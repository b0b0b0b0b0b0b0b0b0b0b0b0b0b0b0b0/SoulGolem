package bm.b0b0b0.SoulGolem.service.farmer;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.FarmerState;
import bm.b0b0b0.SoulGolem.service.GolemGaze;
import bm.b0b0b0.SoulGolem.service.GolemMovement;
import bm.b0b0b0.SoulGolem.service.SoulChestLid;
import bm.b0b0b0.SoulGolem.service.SoulChestLink;
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
    private FarmerCompostWork compost;
    private FarmerCarried carried;

    public FarmerChestWork(FarmerContext ctx) {
        this.ctx = ctx;
    }

    public void wire(
            FarmerCycle cycle,
            FarmerFieldWork field,
            FarmerSupportWork support,
            FarmerCompostWork compost,
            FarmerCarried carried
    ) {
        this.cycle = cycle;
        this.field = field;
        this.support = support;
        this.compost = compost;
        this.carried = carried;
    }

    private SoulChestLid lid() {
        return this.ctx.chestService().lid();
    }

    public void continueDeposit(ActiveGolem golem, CopperGolem copper) {
        SoulChestLink link = this.ctx.chestLink();
        Location chestStand = this.ctx.chestService().chestStandLocation(golem.data());
        if (chestStand == null) {
            lid().closeNow(golem.data());
            golem.farmerState(FarmerState.WAITING_SEEDS);
            return;
        }
        boolean linked = link.isLinked(golem.data());
        if (!link.canAccess(copper, golem.data())) {
            lid().closeNow(golem.data());
            this.ctx.movement().walkTowards(copper, chestStand, golem);
            return;
        }
        this.ctx.movement().stop(copper);
        copper.setVelocity(new Vector(0, 0, 0));
        if (!linked) {
            Location chestLook = new Location(
                    copper.getWorld(),
                    golem.data().chestX() + 0.5D,
                    golem.data().chestY() + 0.5D,
                    golem.data().chestZ() + 0.5D
            );
            GolemGaze.face(golem, chestLook);
            lid().open(golem.data());
        }

        if (golem.fetchingSeed()) {
            this.field.takeSeedFromChest(golem);
            finishChestAccess(golem, copper, linked, SoulChestLink.Kind.WITHDRAW);
            return;
        }
        if (golem.fetchingFeed()) {
            this.ctx.eatFeedFromChest(golem);
            finishChestAccess(golem, copper, linked, SoulChestLink.Kind.WITHDRAW);
            return;
        }
        if (golem.fetchingSeat()) {
            this.support.takeSeatFromChest(golem);
            finishChestAccess(golem, copper, linked, SoulChestLink.Kind.WITHDRAW);
            return;
        }
        if (golem.fetchingTorch()) {
            this.support.takeTorchesFromChest(golem);
            finishChestAccess(golem, copper, linked, SoulChestLink.Kind.WITHDRAW);
            return;
        }
        if (golem.fetchingFence() || golem.fetchingGate()) {
            this.support.takeFenceFromChest(golem);
            finishChestAccess(golem, copper, linked, SoulChestLink.Kind.WITHDRAW);
            return;
        }
        if (golem.fetchingBoneMeal()) {
            this.field.takeBoneMealFromChest(golem);
            finishChestAccess(golem, copper, linked, SoulChestLink.Kind.WITHDRAW);
            return;
        }
        if (golem.fetchingCraft()) {
            takeCraftFromChest(golem);
            finishChestAccess(golem, copper, linked, SoulChestLink.Kind.WITHDRAW);
            return;
        }
        if (golem.fetchingComposter()) {
            this.compost.takeComposterFromChest(golem);
            finishChestAccess(golem, copper, linked, SoulChestLink.Kind.WITHDRAW);
            return;
        }
        if (golem.fetchingCompost()) {
            this.compost.takeCompostFillFromChest(golem);
            finishChestAccess(golem, copper, linked, SoulChestLink.Kind.WITHDRAW);
            return;
        }
        if (golem.fetchingWeapon()) {
            this.ctx.combat().takeWeaponFromChest(golem, copper);
            finishChestAccess(golem, copper, linked, SoulChestLink.Kind.WITHDRAW);
            return;
        }

        if (!this.ctx.hasPlantWork(golem)
                && this.ctx.hasBoneMealWork(golem)
                && FarmerCarried.countCarried(golem, Material.BONE_MEAL) > 0) {
            if (!linked) {
                lid().closeLater(golem.data());
            }
            golem.farmerState(FarmerState.MOVING_TO_BONEMEAL);
            return;
        }

        if (golem.carried().isEmpty()) {
            if (!linked) {
                lid().closeLater(golem.data());
            }
            this.cycle.afterDeposit(golem, copper);
            return;
        }
        if (!this.ctx.chestService().hasSpace(golem.data())) {
            if (!linked) {
                lid().closeLater(golem.data());
            }
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
        finishChestAccess(golem, copper, linked, SoulChestLink.Kind.DEPOSIT);
        if (!depositedAll) {
            golem.farmerState(FarmerState.WAITING_CHEST);
            this.ctx.notifyChestFull(golem);
            return;
        }
        this.cycle.afterDeposit(golem, copper);
    }

    private void finishChestAccess(
            ActiveGolem golem,
            CopperGolem copper,
            boolean linked,
            SoulChestLink.Kind kind
    ) {
        if (linked) {
            this.ctx.chestLink().play(copper, golem.data(), kind);
            return;
        }
        lid().closeLater(golem.data());
    }

    public boolean needsPlaceCraft(ActiveGolem golem) {
        if (!this.ctx.settings().farmer.craftBread
                || this.ctx.chestService().isCraftPresent(golem.data())) {
            return false;
        }
        return this.ctx.chestService().countItem(golem.data(), Material.CRAFTING_TABLE) > 0
                || FarmerCarried.countCarried(golem, Material.CRAFTING_TABLE) > 0;
    }

    public boolean tryStartPlaceCraft(ActiveGolem golem) {
        if (!needsPlaceCraft(golem)) {
            return false;
        }
        Location spot = this.ctx.chestService().findCraftingTableLocation(golem.data());
        if (spot == null) {
            return false;
        }
        if (FarmerCarried.countCarried(golem, Material.CRAFTING_TABLE) > 0) {
            golem.clearFetchFlags();
            golem.wanderTarget(null);
            golem.targetCrop(spot);
            golem.farmerState(FarmerState.MOVING_TO_PLACE_CRAFT);
            return true;
        }
        golem.clearFetchFlags();
        golem.wanderTarget(null);
        golem.fetchingCraft(true);
        golem.targetCrop(spot);
        golem.farmerState(FarmerState.MOVING_TO_CHEST);
        return true;
    }

    public void takeCraftFromChest(ActiveGolem golem) {
        golem.clearFetchFlags();
        Location spot = this.ctx.chestService().findCraftingTableLocation(golem.data());
        if (spot == null || this.ctx.chestService().countItem(golem.data(), Material.CRAFTING_TABLE) <= 0) {
            if (this.cycle != null) {
                this.cycle.assignNextJob(golem);
            }
            return;
        }
        if (!this.ctx.chestService().takeItem(golem.data(), Material.CRAFTING_TABLE, 1)) {
            if (this.cycle != null) {
                this.cycle.assignNextJob(golem);
            }
            return;
        }
        golem.carry(new ItemStack(Material.CRAFTING_TABLE, 1));
        golem.targetCrop(spot);
        golem.farmerState(FarmerState.MOVING_TO_PLACE_CRAFT);
        golem.markDirty();
    }

    public void continuePlaceCraft(ActiveGolem golem, CopperGolem copper) {
        if (!this.ctx.stationsOk(golem)) {
            return;
        }
        Location spot = golem.targetCrop();
        if (spot == null) {
            spot = this.ctx.chestService().findCraftingTableLocation(golem.data());
            golem.targetCrop(spot);
        }
        if (spot == null || FarmerCarried.countCarried(golem, Material.CRAFTING_TABLE) <= 0) {
            if (this.carried != null) {
                this.carried.returnAllCarriedToChest(golem);
            }
            if (this.cycle != null) {
                this.cycle.assignNextJob(golem);
            }
            return;
        }
        Location stand = this.ctx.farmAreaService().standBesideInside(golem.data(), spot.getBlock());
        if (stand == null) {
            stand = spot.clone().add(0.5D, 0.0D, 0.5D);
        }
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 2.25D) {
            golem.farmerState(FarmerState.MOVING_TO_PLACE_CRAFT);
            this.ctx.movement().walkTowards(copper, stand, golem);
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        golem.farmerState(FarmerState.PLACING_CRAFT);
        GolemGaze.faceBlock(golem, spot.getBlock());
        this.ctx.chestService().clearStationColumn(spot);
        this.ctx.chestService().placeCraftingTable(spot, golem.data().id(), golem.data().ownerUuid());
        this.ctx.workAreaService().protect(spot.getBlock(), golem.data().id());
        golem.data().craftPosition(spot.getBlockX(), spot.getBlockY(), spot.getBlockZ());
        FarmerCarried.consumeCarried(golem, Material.CRAFTING_TABLE, 1);
        golem.targetCrop(null);
        golem.markDirty();
        if (this.cycle != null && this.cycle.assignNextJob(golem)) {
            return;
        }
        golem.farmerState(FarmerState.WAIT_GROWTH);
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
