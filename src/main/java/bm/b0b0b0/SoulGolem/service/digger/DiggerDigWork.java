package bm.b0b0b0.SoulGolem.service.digger;

import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.DiggerState;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import bm.b0b0b0.SoulGolem.service.FarmAreaService;
import bm.b0b0b0.SoulGolem.service.GolemEnergy;
import bm.b0b0b0.SoulGolem.service.GolemGaze;
import bm.b0b0b0.SoulGolem.service.GolemGroundLootWork;
import bm.b0b0b0.SoulGolem.service.GolemMovement;
import bm.b0b0b0.SoulGolem.service.SoulChestLink;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.CopperGolem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class DiggerDigWork {

    private final DiggerContext ctx;
    private DiggerSupportWork support;
    private DiggerCrewWork crew;

    public DiggerDigWork(DiggerContext ctx) {
        this.ctx = ctx;
    }

    public void wire(DiggerSupportWork support, DiggerCrewWork crew) {
        this.support = support;
        this.crew = crew;
    }

    public void beginSeek(ActiveGolem golem, CopperGolem copper) {
        SoulGolemData pit = this.ctx.pitData(golem);
        boolean linked = this.ctx.chestLink().isLinked(pit);

        ensureDigProgress(pit);
        if (isDepthDone(pit)) {
            handlePitComplete(golem, copper, linked);
            return;
        }

        if (!golem.data().isCrewHelper() && this.crew != null && this.crew.tryHireFromChest(golem, copper)) {
            golem.data().lastActionAt(System.currentTimeMillis());
            return;
        }

        if (linked) {
            golem.clearFetchFlags();
            if (golem.diggerState() == DiggerState.ESCAPING || golem.diggerState() == DiggerState.MOVING_TO_CHEST) {
                golem.diggerState(DiggerState.IDLE);
            }
            linkDump(golem, copper, false);
            linkFeed(golem, copper);
            if (golem.data().energy() <= 0) {
                abandonDigTarget(golem);
                golem.diggerState(DiggerState.IDLE);
                golem.data().lastActionAt(System.currentTimeMillis());
                return;
            }
            if (!this.ctx.chestService().hasSpace(golem.data())) {
                linkDump(golem, copper, true);
                if (!this.ctx.chestService().hasSpace(golem.data())) {
                    golem.diggerState(DiggerState.WAITING_CHEST);
                    this.ctx.notifyChestFull(golem);
                    return;
                }
            }
            golem.chestFullNotified(false);
            if (resumeDigTarget(golem, copper, pit)) {
                return;
            }
            assignDigTarget(golem, copper, pit);
            return;
        }

        if (carriedCount(golem) >= this.ctx.digger().blocksPerTrip) {
            golem.diggerState(DiggerState.MOVING_TO_CHEST);
            return;
        }
        if (!golem.carried().isEmpty() && !this.ctx.chestService().hasSpace(golem.data())) {
            golem.diggerState(DiggerState.WAITING_CHEST);
            this.ctx.notifyChestFull(golem);
            return;
        }
        if (golem.data().energy() <= this.ctx.settings().energyHungryThreshold) {
            abandonDigTarget(golem);
        }
        if (this.ctx.tryStartFeed(golem)) {
            return;
        }
        if (golem.data().energy() <= 0) {
            abandonDigTarget(golem);
            golem.diggerState(DiggerState.IDLE);
            golem.data().lastActionAt(System.currentTimeMillis());
            return;
        }
        if (!this.ctx.chestService().hasSpace(golem.data())) {
            golem.diggerState(DiggerState.WAITING_CHEST);
            this.ctx.notifyChestFull(golem);
            return;
        }
        golem.chestFullNotified(false);

        GolemSettings.Yard yard = this.ctx.settings().yard;
        int radius = this.ctx.chestService().effectiveRadius(pit);

        boolean digPriority = pit.hasDigProgress() && !isDepthDone(pit);
        if (!digPriority) {
            if (yard.collectGroundLoot) {
                GolemGroundLootWork.Phase loot = this.ctx.groundLoot().tick(golem, copper, true);
                if (loot == GolemGroundLootWork.Phase.MOVING || loot == GolemGroundLootWork.Phase.PICKED) {
                    golem.diggerState(DiggerState.IDLE);
                    return;
                }
            }
            if (!golem.data().isCrewHelper() && yard.clearBorder) {
                var junk = this.ctx.farmAreaService().diggerYardWeeds(pit, radius);
                if (!junk.isEmpty()) {
                    golem.clearFetchFlags();
                    golem.targetCrop(junk.get(0).getLocation());
                    golem.diggerState(DiggerState.MOVING_TO_CLEAR);
                    return;
                }
            }
            Material torch = this.ctx.resolveTorch();
            if (!golem.data().isCrewHelper()
                    && yard.placeTorches
                    && this.ctx.chestService().countItem(pit, torch) > 0
                    && !this.ctx.farmAreaService().perimeterTorchSpots(pit, radius, yard.maxTorches).isEmpty()) {
                golem.clearFetchFlags();
                golem.fetchingTorch(true);
                golem.diggerState(DiggerState.MOVING_TO_CHEST);
                return;
            }
            if (!golem.data().isCrewHelper() && this.support.tryStartFenceJob(golem)) {
                return;
            }
            if (!golem.data().isCrewHelper() && this.support.tryStartSeatJob(golem, radius)) {
                return;
            }
        }

        if (resumeDigTarget(golem, copper, pit)) {
            return;
        }
        assignDigTarget(golem, copper, pit);
    }

    private boolean resumeDigTarget(ActiveGolem golem, CopperGolem copper, SoulGolemData pit) {
        Location target = golem.targetOre();
        if (target == null) {
            return false;
        }
        Block block = target.getBlock();
        UUID pitId = this.ctx.pitId(golem);
        UUID diggerId = golem.data().id();
        if (!DiggerPit.isDiggable(block, pit, this.ctx.chestService(), this.ctx.farmAreaService(), this.ctx.digger())) {
            clearDigTarget(golem, block);
            return false;
        }
        double dx = copper.getLocation().getX() - (block.getX() + 0.5D);
        double dz = copper.getLocation().getZ() - (block.getZ() + 0.5D);
        if (!canTakeClaim(pitId, block, diggerId, dx * dx + dz * dz)) {
            clearDigTarget(golem, block);
            return false;
        }
        takeClaim(pitId, block, diggerId);
        golem.diggerState(DiggerState.MOVING_TO_DIG);
        continueMoveToDig(golem, copper);
        return true;
    }

    private void clearDigTarget(ActiveGolem golem, Block block) {
        if (block != null) {
            DiggerClaims.release(this.ctx.pitId(golem), block, golem.data().id());
        }
        golem.targetOre(null);
        golem.oreMaterial(null);
        golem.wanderTarget(null);
    }

    private void abandonDigTarget(ActiveGolem golem) {
        Location target = golem.targetOre();
        clearDigTarget(golem, target != null ? target.getBlock() : null);
    }

    private void assignDigTarget(ActiveGolem golem, CopperGolem copper, SoulGolemData pit) {
        Block next = findNextDigBlock(golem, copper, pit);
        if (next == null) {
            golem.wanderTarget(null);
            if (isDepthDone(pit)) {
                handlePitComplete(golem, copper, this.ctx.chestLink().isLinked(pit));
                return;
            }
            if (!golem.data().isCrewHelper()) {
                golem.diggerState(DiggerState.PLACING_STAIR);
                placeStairAndDescend(golem, copper);
            } else {
                ensureCurrentStairPlaced(golem, pit);
                golem.diggerState(DiggerState.IDLE);
                golem.data().lastActionAt(System.currentTimeMillis());
            }
            return;
        }
        golem.targetOre(next.getLocation());
        golem.oreMaterial(next.getType());
        golem.diggerState(DiggerState.MOVING_TO_DIG);
        golem.data().lastActionAt(System.currentTimeMillis());
        Location stand = stableStandForDig(golem, copper, next, pit);
        if (stand == null) {
            stand = next.getLocation().add(0.5D, 1.0D, 0.5D);
        }
        golem.wanderTarget(stand.clone());
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) <= 1.69D
                && Math.abs(copper.getLocation().getY() - stand.getY()) <= 1.6D) {
            copper.setVelocity(new Vector(0, 0, 0));
            this.ctx.equipForBlock(copper, next.getType());
            golem.diggerState(DiggerState.DIGGING);
            GolemGaze.faceBlock(golem, next);
            golem.mineTicksLeft(this.ctx.digDurationTicks(next.getType(), golem));
            this.ctx.playDigFx(next);
            return;
        }
        this.ctx.walkTowards(copper, stand, golem);
    }

    public void continueMoveToDig(ActiveGolem golem, CopperGolem copper) {
        Location target = golem.targetOre();
        if (target == null) {
            golem.wanderTarget(null);
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        SoulGolemData pit = this.ctx.pitData(golem);
        Block block = target.getBlock();
        if (golem.data().energy() <= 0) {
            clearDigTarget(golem, block);
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        if (!this.ctx.chestLink().isLinked(pit)
                && golem.data().energy() <= this.ctx.settings().energyHungryThreshold
                && this.ctx.chestService().countItem(pit, this.ctx.settings().energyFeedMaterial()) > 0) {
            clearDigTarget(golem, block);
            this.ctx.tryStartFeed(golem);
            return;
        }
        if (!DiggerPit.isDiggable(block, pit, this.ctx.chestService(), this.ctx.farmAreaService(), this.ctx.digger())) {
            clearDigTarget(golem, block);
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        if (!DiggerPit.prepareDigFloor(block, this.ctx.digger())) {
            clearDigTarget(golem, block);
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        Location stand = stableStandForDig(golem, copper, block, pit);
        if (stand == null || !DiggerSafety.hasSolidSupport(stand)) {
            if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), block.getLocation().add(0.5D, 1.0D, 0.5D)) <= 2.25D) {
                stand = copper.getLocation();
            } else {
                clearDigTarget(golem, block);
                golem.diggerState(DiggerState.IDLE);
                return;
            }
        }
        golem.wanderTarget(stand.clone());
        double standDist2 = GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand);
        if (standDist2 <= 2.25D) {
            DiggerClaims.renew(this.ctx.pitId(golem), block, golem.data().id());
        }
        if (standDist2 > 1.69D) {
            if (System.currentTimeMillis() - golem.data().lastActionAt() > 3000L) {
                clearDigTarget(golem, block);
                assignDigTarget(golem, copper, pit);
                return;
            }
            this.ctx.walkTowards(copper, stand, golem);
            return;
        }
        copper.setVelocity(new Vector(0, 0, 0));
        this.ctx.equipForBlock(copper, block.getType());
        golem.diggerState(DiggerState.DIGGING);
        GolemGaze.faceBlock(golem, block);
        golem.mineTicksLeft(this.ctx.digDurationTicks(block.getType(), golem));
        this.ctx.playDigFx(block);
    }

    public void continueDig(ActiveGolem golem, CopperGolem copper) {
        Location target = golem.targetOre();
        if (target == null) {
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        SoulGolemData pit = this.ctx.pitData(golem);
        Block block = target.getBlock();
        if (golem.data().energy() <= 0) {
            clearDigTarget(golem, block);
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        if (!DiggerPit.isDiggable(block, pit, this.ctx.chestService(), this.ctx.farmAreaService(), this.ctx.digger())
                || !DiggerPit.prepareDigFloor(block, this.ctx.digger())) {
            clearDigTarget(golem, block);
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        DiggerClaims.renew(this.ctx.pitId(golem), block, golem.data().id());
        this.ctx.equipForBlock(copper, block.getType());
        long step = Math.max(1L, (long) (this.ctx.settings().coordinatorPeriodTicks * this.ctx.stickBoostFactor(golem)));
        long left = golem.mineTicksLeft() - step;
        golem.mineTicksLeft(left);
        if (left > 0L) {
            GolemGaze.faceBlock(golem, block);
            this.ctx.playDigFx(block);
            return;
        }

        int dugX = block.getX();
        int dugY = block.getY();
        int dugZ = block.getZ();
        Collection<ItemStack> drops = block.getDrops(this.ctx.digTool(block.getType()));
        this.ctx.playDigBurst(block);
        DiggerPit.prepareDigFloor(block, this.ctx.digger());
        block.setType(Material.AIR, false);
        DiggerPit.sealHazardsNear(block, this.ctx.digger().caveSafeDepth, DiggerPit.hazardSealMaterial(this.ctx.digger()));
        DiggerClaims.release(this.ctx.pitId(golem), block, golem.data().id());
        for (ItemStack drop : drops) {
            golem.carry(drop);
        }
        golem.data().incrementBlocksMined();
        int drain = this.ctx.settings().energyPerMine;
        if (drain > 0) {
            golem.data().energy(Math.max(0, golem.data().energy() - drain));
        }
        golem.markDirty();
        golem.targetOre(null);
        golem.oreMaterial(null);
        golem.wanderTarget(null);
        golem.data().lastActionAt(System.currentTimeMillis());

        if (DiggerPit.isCurrentStairSlot(pit, this.ctx.digger(), dugX, dugY, dugZ)) {
            placeCurrentLayerStair(golem, pit);
        }

        boolean linked = this.ctx.chestLink().isLinked(pit);
        if (linked) {
            boolean tripFull = carriedCount(golem) >= this.ctx.digger().blocksPerTrip;
            boolean noSpace = !this.ctx.chestService().hasSpace(golem.data());
            if (tripFull || noSpace || golem.data().energy() <= 0) {
                linkDump(golem, copper, true);
            }
            linkFeed(golem, copper);
            if (!this.ctx.chestService().hasSpace(golem.data()) && !golem.carried().isEmpty()) {
                golem.diggerState(DiggerState.WAITING_CHEST);
                this.ctx.notifyChestFull(golem);
                return;
            }
            if (golem.data().energy() <= 0) {
                golem.diggerState(DiggerState.IDLE);
                return;
            }
            if (!golem.data().isCrewHelper() && !layerHasDiggable(pit)) {
                linkDump(golem, copper, true);
                golem.diggerState(DiggerState.PLACING_STAIR);
                placeStairAndDescend(golem, copper);
                if (golem.diggerState() == DiggerState.DONE) {
                    return;
                }
            }
            assignDigTarget(golem, copper, pit);
            return;
        }

        if (carriedCount(golem) >= this.ctx.digger().blocksPerTrip
                || golem.data().energy() <= 0
                || !this.ctx.chestService().hasSpace(golem.data())) {
            if (!golem.data().isCrewHelper() && !layerHasDiggable(pit)) {
                golem.diggerState(DiggerState.PLACING_STAIR);
                placeStairAndDescend(golem, copper);
                if (golem.diggerState() == DiggerState.DONE) {
                    return;
                }
            }
            golem.diggerState(DiggerState.MOVING_TO_CHEST);
            return;
        }
        assignDigTarget(golem, copper, pit);
    }

    public void placeStairAndDescend(ActiveGolem golem, CopperGolem copper) {
        if (golem.data().isCrewHelper()) {
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        SoulGolemData pit = this.ctx.pitData(golem);
        ensureDigProgress(pit);
        World world = Bukkit.getWorld(pit.worldName());
        if (world == null) {
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        synchronized (("digger-stair-" + pit.id()).intern()) {
            if (layerHasDiggable(pit)) {
                golem.diggerState(DiggerState.IDLE);
                return;
            }
            int y = pit.digLayerY();
            int stairIndex = pit.digStairIndex();
            int used = DiggerPit.placeStair(world, pit, this.ctx.digger(), y, stairIndex);
            if (used <= 0) {
                golem.diggerState(DiggerState.IDLE);
                golem.data().lastActionAt(System.currentTimeMillis());
                return;
            }
            pit.digStairIndex(stairIndex + used);
            pit.digLayerY(y - 1);
            this.ctx.markPitDirty(golem);

            if (isDepthDone(pit)) {
                handlePitComplete(golem, copper, this.ctx.chestLink().isLinked(pit));
                return;
            }

            int radius = DiggerPit.radius(this.ctx.digger());
            int[] walk = DiggerPit.stairWalkCell(pit, radius, stairIndex);
            Location stand = DiggerPit.stairStandInward(world, pit, walk, y);
            if (DiggerSafety.hasSolidSupport(stand) || DiggerPit.isStairBlock(world.getBlockAt(walk[0], y, walk[1]))) {
                this.ctx.walkTowards(copper, stand, golem);
            }
        }
        golem.diggerState(DiggerState.IDLE);
        golem.data().lastActionAt(System.currentTimeMillis());
    }

    public void continueDone(ActiveGolem golem, CopperGolem copper) {
        SoulGolemData pit = this.ctx.pitData(golem);
        if (golem.data().isCrewHelper()) {
            handlePitComplete(golem, copper, this.ctx.chestLink().isLinked(pit));
            return;
        }
        if (!golem.carried().isEmpty()) {
            if (this.ctx.chestLink().isLinked(pit)) {
                linkDump(golem, copper, true);
            } else {
                golem.diggerState(DiggerState.MOVING_TO_CHEST);
                return;
            }
        }
        Location stand = this.ctx.chestService().chestStandLocation(pit);
        if (stand == null) {
            stand = this.ctx.workAreaService().homeLocation(pit);
        }
        if (stand == null) {
            return;
        }
        golem.diggerState(DiggerState.DONE);
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 2.25D
                || Math.abs(copper.getLocation().getY() - stand.getY()) > 1.8D) {
            this.ctx.walkTowards(copper, stand, golem);
            return;
        }
        this.ctx.movement().stop(copper);
        copper.setVelocity(new Vector(0, 0, 0));
        Location look = new Location(
                copper.getWorld(),
                pit.chestX() + 0.5D,
                pit.chestY() + 0.5D,
                pit.chestZ() + 0.5D
        );
        GolemGaze.face(golem, look);
    }

    private void handlePitComplete(ActiveGolem golem, CopperGolem copper, boolean linked) {
        abandonDigTarget(golem);
        golem.clearFetchFlags();
        SoulGolemData pit = this.ctx.pitData(golem);
        golem.data().lastActionAt(System.currentTimeMillis());

        if (golem.data().isCrewHelper()) {
            golem.crewReturning(true);
            if (copper.getLocation().getY() < pit.homeY() - 1.4D) {
                golem.diggerState(DiggerState.ESCAPING);
                return;
            }
            golem.diggerState(DiggerState.MOVING_TO_CHEST);
            return;
        }

        if (!golem.carried().isEmpty()) {
            if (linked) {
                linkDump(golem, copper, true);
            } else {
                golem.diggerState(DiggerState.MOVING_TO_CHEST);
                return;
            }
        }
        golem.diggerState(DiggerState.DONE);
        continueDone(golem, copper);
    }

    private void linkDump(ActiveGolem golem, CopperGolem copper, boolean force) {
        if (golem.carried().isEmpty()) {
            return;
        }
        if (!force && carriedCount(golem) < this.ctx.digger().blocksPerTrip) {
            return;
        }
        if (!this.ctx.chestService().hasSpace(golem.data())) {
            return;
        }
        boolean any = false;
        List<ItemStack> leftover = new ArrayList<>();
        for (ItemStack stack : golem.carried()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if (this.ctx.chestService().deposit(golem.data(), stack.clone())) {
                any = true;
            } else {
                leftover.add(stack.clone());
            }
        }
        golem.clearCarried();
        for (ItemStack stack : leftover) {
            golem.carry(stack);
        }
        if (any) {
            this.ctx.chestLink().play(copper, golem.data(), SoulChestLink.Kind.DEPOSIT);
            golem.markDirty();
        }
    }

    private void linkFeed(ActiveGolem golem, CopperGolem copper) {
        if (golem.data().energy() > this.ctx.settings().energyHungryThreshold) {
            return;
        }
        Material feed = this.ctx.settings().energyFeedMaterial();
        if (this.ctx.chestService().countItem(golem.data(), feed) <= 0) {
            return;
        }
        int before = golem.data().energy();
        GolemEnergy.eatUntilFull(this.ctx.chestService(), this.ctx.settings(), golem);
        if (golem.data().energy() > before) {
            this.ctx.chestLink().play(copper, golem.data(), SoulChestLink.Kind.WITHDRAW);
        }
    }

    private boolean layerHasDiggable(SoulGolemData pit) {
        World world = Bukkit.getWorld(pit.worldName());
        if (world == null) {
            return false;
        }
        int radius = DiggerPit.radius(this.ctx.digger());
        int y = pit.digLayerY();
        for (int z = DiggerPit.digMinZ(pit, radius); z <= DiggerPit.digMaxZ(pit, radius); z++) {
            for (int x = DiggerPit.digMinX(pit, radius); x <= DiggerPit.digMaxX(pit, radius); x++) {
                Block block = world.getBlockAt(x, y, z);
                if (DiggerPit.isDiggable(block, pit, this.ctx.chestService(), this.ctx.farmAreaService(), this.ctx.digger())
                        && DiggerPit.prepareDigFloor(block, this.ctx.digger())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void placeCurrentLayerStair(ActiveGolem golem, SoulGolemData pit) {
        World world = Bukkit.getWorld(pit.worldName());
        if (world == null || !pit.hasDigProgress()) {
            return;
        }
        synchronized (("digger-stair-" + pit.id()).intern()) {
            int y = pit.digLayerY();
            int stairIndex = pit.digStairIndex();
            if (DiggerPit.placeStair(world, pit, this.ctx.digger(), y, stairIndex) > 0) {
                this.ctx.markPitDirty(golem);
            }
        }
    }

    private void ensureCurrentStairPlaced(ActiveGolem golem, SoulGolemData pit) {
        World world = Bukkit.getWorld(pit.worldName());
        if (world == null || !pit.hasDigProgress()) {
            return;
        }
        int radius = DiggerPit.radius(this.ctx.digger());
        int stairIndex = pit.digStairIndex();
        int[] cell = DiggerPit.stairCell(pit, radius, stairIndex);
        int y = pit.digLayerY();
        Block at = world.getBlockAt(cell[0], y, cell[1]);
        if (DiggerPit.isStairBlock(at)) {
            return;
        }
        if (DiggerPit.isCornerStairIndex(pit, radius, stairIndex)) {
            Material support = DiggerPit.stairSupportMaterial(this.ctx.digger());
            int[] next = DiggerPit.stairCell(pit, radius, stairIndex + 1);
            Block nextBlock = world.getBlockAt(next[0], y, next[1]);
            if (at.getType() == support && DiggerPit.isStairBlock(nextBlock)) {
                return;
            }
        }
        if (at.getType().isAir()
                || DiggerPit.isDiggable(at, pit, this.ctx.chestService(), this.ctx.farmAreaService(), this.ctx.digger())) {
            placeCurrentLayerStair(golem, pit);
        }
    }

    private Block findNextDigBlock(ActiveGolem golem, CopperGolem copper, SoulGolemData pit) {
        World world = Bukkit.getWorld(pit.worldName());
        if (world == null) {
            return null;
        }
        int radius = DiggerPit.radius(this.ctx.digger());
        int y = pit.digLayerY();
        int minX = DiggerPit.digMinX(pit, radius);
        int maxX = DiggerPit.digMaxX(pit, radius);
        int minZ = DiggerPit.digMinZ(pit, radius);
        int maxZ = DiggerPit.digMaxZ(pit, radius);
        UUID pitId = this.ctx.pitId(golem);
        UUID diggerId = golem.data().id();
        UUID leaderId = golem.data().isCrewHelper() ? golem.data().crewLeaderId() : golem.data().id();
        int crewSize = 1;
        int myIndex = 0;
        if (this.crew != null && leaderId != null) {
            crewSize = Math.max(1, this.crew.crewSize(leaderId));
            myIndex = this.crew.crewIndex(golem);
        }
        int stairIndex = pit.digStairIndex();
        int[] stairCell = DiggerPit.stairCell(pit, radius, stairIndex);
        int[] stairWalk = DiggerPit.stairWalkCell(pit, radius, stairIndex);
        double ox = copper.getLocation().getX();
        double oz = copper.getLocation().getZ();
        int width = maxX - minX + 1;

        List<ScoredBlock> own = new ArrayList<>();
        List<ScoredBlock> shared = new ArrayList<>();
        int caveSafeDepth = Math.max(1, this.ctx.digger().caveSafeDepth);
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                Block block = world.getBlockAt(x, y, z);
                if (!DiggerPit.isDiggable(block, pit, this.ctx.chestService(), this.ctx.farmAreaService(), this.ctx.digger())) {
                    continue;
                }
                if (DiggerPit.hasCaveBelow(block, caveSafeDepth)) {
                    continue;
                }
                double dx = (x + 0.5D) - ox;
                double dz = (z + 0.5D) - oz;
                double myDist2 = dx * dx + dz * dz;
                if (!canTakeClaim(pitId, block, diggerId, myDist2)) {
                    continue;
                }
                int cellIndex = (z - minZ) * width + (x - minX);
                boolean mine = Math.floorMod(cellIndex, crewSize) == myIndex;
                boolean stairSlot = (x == stairCell[0] && z == stairCell[1])
                        || (x == stairWalk[0] && z == stairWalk[1]);
                long score = (long) myDist2;
                if (stairSlot) {
                    score -= 1_000_000L;
                }
                (mine ? own : shared).add(new ScoredBlock(block, score, myDist2));
            }
        }
        own.sort(Comparator.comparingLong(ScoredBlock::score));
        shared.sort(Comparator.comparingLong(ScoredBlock::score));
        Block claimed = claimFirst(own, pitId, diggerId);
        if (claimed != null) {
            return claimed;
        }
        return claimFirst(shared, pitId, diggerId);
    }

    private boolean canTakeClaim(UUID pitId, Block block, UUID diggerId, double myDist2) {
        UUID ownerId = DiggerClaims.owner(pitId, block);
        if (ownerId == null || ownerId.equals(diggerId)) {
            return true;
        }
        ActiveGolem owner = this.ctx.registry().byId(ownerId).orElse(null);
        if (owner == null || !isActivelyWorkingBlock(owner, block)) {
            return true;
        }
        if (owner.diggerState() == DiggerState.DIGGING) {
            return false;
        }
        double ox = owner.data().x() - (block.getX() + 0.5D);
        double oz = owner.data().z() - (block.getZ() + 0.5D);
        double theirDist2 = ox * ox + oz * oz;
        return myDist2 + 0.75D < theirDist2;
    }

    private static boolean isActivelyWorkingBlock(ActiveGolem owner, Block block) {
        if (owner.data().energy() <= 0 || owner.fetchingFeed()) {
            return false;
        }
        DiggerState state = owner.diggerState();
        if (state != DiggerState.DIGGING && state != DiggerState.MOVING_TO_DIG) {
            return false;
        }
        Location target = owner.targetOre();
        return target != null
                && target.getBlockX() == block.getX()
                && target.getBlockY() == block.getY()
                && target.getBlockZ() == block.getZ();
    }

    private Block claimFirst(List<ScoredBlock> candidates, UUID pitId, UUID diggerId) {
        for (ScoredBlock scored : candidates) {
            if (!DiggerPit.prepareDigFloor(scored.block(), this.ctx.digger())) {
                continue;
            }
            if (!canTakeClaim(pitId, scored.block(), diggerId, scored.dist2())) {
                continue;
            }
            takeClaim(pitId, scored.block(), diggerId);
            return scored.block();
        }
        return null;
    }

    private void takeClaim(UUID pitId, Block block, UUID diggerId) {
        UUID previous = DiggerClaims.claim(pitId, block, diggerId);
        if (previous == null || previous.equals(diggerId)) {
            return;
        }
        this.ctx.registry().byId(previous).ifPresent(other -> {
            Location target = other.targetOre();
            if (target == null
                    || target.getBlockX() != block.getX()
                    || target.getBlockY() != block.getY()
                    || target.getBlockZ() != block.getZ()) {
                return;
            }
            other.targetOre(null);
            other.oreMaterial(null);
            other.wanderTarget(null);
            if (other.diggerState() == DiggerState.MOVING_TO_DIG || other.diggerState() == DiggerState.DIGGING) {
                other.diggerState(DiggerState.IDLE);
            }
        });
    }

    private record ScoredBlock(Block block, long score, double dist2) {
    }

    private Location stableStandForDig(ActiveGolem golem, CopperGolem copper, Block block, SoulGolemData pit) {
        Location cached = golem.wanderTarget();
        if (cached != null
                && cached.getWorld() != null
                && cached.getWorld().equals(block.getWorld())
                && DiggerSafety.hasSolidSupport(cached)
                && GolemMovement.horizontalDistanceSquared(cached, block.getLocation().add(0.5D, 1.0D, 0.5D)) <= 6.25D) {
            return cached;
        }
        return standForDig(block, pit, copper.getLocation());
    }

    private Location standForDig(Block block, SoulGolemData pit, Location from) {
        World world = block.getWorld();
        double hx = block.getX() + 0.5D;
        double hz = block.getZ() + 0.5D;
        double fx = from != null ? from.getX() : hx;
        double fz = from != null ? from.getZ() : hz;
        if (DiggerPit.isCurrentStairSlot(pit, this.ctx.digger(), block.getX(), block.getY(), block.getZ())) {
            Location inward = DiggerPit.stairStandInward(world, pit, new int[]{block.getX(), block.getZ()}, block.getY());
            if (DiggerSafety.hasSolidSupport(inward) || DiggerPit.isStairBlock(block)) {
                return inward;
            }
        }
        int[][] offsets = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        Location best = null;
        double bestScore = Double.MAX_VALUE;
        for (int[] o : offsets) {
            Block standBlock = world.getBlockAt(block.getX() + o[0], block.getY() + 1, block.getZ() + o[1]);
            Location stand = standBlock.getLocation().add(0.5D, 0.0D, 0.5D);
            Block below = standBlock.getRelative(BlockFace.DOWN);
            if (below.equals(block)) {
                continue;
            }
            if (FarmAreaService.isFoliage(standBlock.getType()) || FarmAreaService.isVegetation(standBlock.getType())) {
                standBlock.setType(Material.AIR, false);
            }
            if (!DiggerSafety.hasSolidSupport(stand)) {
                continue;
            }
            if (standBlock.getType().isSolid()
                    && !org.bukkit.Tag.STAIRS.isTagged(standBlock.getType())
                    && !FarmAreaService.isFoliage(standBlock.getType())) {
                continue;
            }
            double toBlock = Math.abs(stand.getX() - hx) + Math.abs(stand.getZ() - hz);
            double toGolem = Math.abs(stand.getX() - fx) + Math.abs(stand.getZ() - fz);
            double score = toBlock + toGolem * 0.35D;
            if (org.bukkit.Tag.STAIRS.isTagged(below.getType())) {
                stand = DiggerPit.stairStandInward(
                        world,
                        pit,
                        new int[]{below.getX(), below.getZ()},
                        below.getY()
                );
                score -= 0.4D;
            }
            if (score < bestScore) {
                bestScore = score;
                best = stand;
            }
        }
        if (best != null) {
            return best;
        }
        Location above = block.getLocation().add(0.5D, 1.0D, 0.5D);
        if (DiggerSafety.hasSolidSupport(above)) {
            return above;
        }
        return null;
    }

    public static void ensureDigProgress(SoulGolemData data) {
        if (data.hasDigProgress()) {
            return;
        }
        int floorY = (int) Math.floor(data.homeY());
        data.digStartY(floorY);
        data.digLayerY(floorY);
        data.digStairIndex(0);
    }

    private boolean isDepthDone(SoulGolemData data) {
        return isPitComplete(data, this.ctx.digger().maxDepth);
    }

    public static boolean isPitComplete(SoulGolemData data, int maxDepth) {
        if (data == null || !data.hasDigProgress()) {
            return false;
        }
        return data.digStartY() - data.digLayerY() >= Math.max(1, maxDepth);
    }

    public static boolean isCrewReturning(ActiveGolem golem, SoulGolemData pit, int maxDepth) {
        return golem != null
                && golem.data().isCrewHelper()
                && (golem.crewReturning() || isPitComplete(pit, maxDepth));
    }

    static int carriedCount(ActiveGolem golem) {
        int total = 0;
        for (ItemStack stack : golem.carried()) {
            if (stack != null) {
                total += stack.getAmount();
            }
        }
        return total;
    }
}
