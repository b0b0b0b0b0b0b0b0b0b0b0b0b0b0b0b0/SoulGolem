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
import bm.b0b0b0.SoulGolem.service.SoulChestService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
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
    private final Map<String, Long> logThrottle = new ConcurrentHashMap<>();

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
        Location feet = copper.getLocation();
        digLog(golem, "seek-start state=" + golem.diggerState()
                + " feet=" + locPos(feet)
                + " energy=" + golem.data().energy()
                + " carried=" + carriedCount(golem)
                + " linked=" + linked
                + " homeY=" + (int) Math.floor(pit.homeY())
                + " digStartY=" + pit.digStartY()
                + " digLayerY=" + pit.digLayerY()
                + " stair=" + pit.digStairIndex()
                + " progress=" + pit.hasDigProgress()
                + " depthDone=" + isDepthDone(pit));

        int layerBefore = pit.hasDigProgress() ? pit.digLayerY() : Integer.MIN_VALUE;
        ensureDigProgress(pit, this.ctx.digger(), this.ctx.farmAreaService());
        if (pit.hasDigProgress() && layerBefore != Integer.MIN_VALUE && pit.digLayerY() > layerBefore) {
            digLog(golem, "surface-raise " + layerBefore + "->" + pit.digLayerY()
                    + " digStartY=" + pit.digStartY());
        }
        if (isDepthDone(pit)) {
            digLog(golem, "seek-stop pit-complete digStartY=" + pit.digStartY()
                    + " digLayerY=" + pit.digLayerY());
            handlePitComplete(golem, copper, linked);
            return;
        }

        if (!golem.data().isCrewHelper() && this.crew != null && this.crew.tryHireFromChest(golem, copper)) {
            digLog(golem, "seek-stop hired-helper");
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
                digLog(golem, "seek-stop no-energy linked");
                abandonDigTarget(golem);
                golem.diggerState(DiggerState.IDLE);
                golem.data().lastActionAt(System.currentTimeMillis());
                return;
            }
            if (!this.ctx.chestService().hasSpace(golem.data())) {
                linkDump(golem, copper, true);
                if (!this.ctx.chestService().hasSpace(golem.data())) {
                    digLog(golem, "seek-stop chest-full linked");
                    golem.diggerState(DiggerState.WAITING_CHEST);
                    this.ctx.notifyChestFull(golem);
                    return;
                }
            }
            golem.chestFullNotified(false);
            if (resumeDigTarget(golem, copper, pit)) {
                digLog(golem, "seek-resume existing-target");
                return;
            }
            releaseAbandonedClaims(this.ctx.pitId(golem), pit);
            assignDigTarget(golem, copper, pit);
            return;
        }

        if (layerHasDiggable(pit)) {
            releaseAbandonedClaims(this.ctx.pitId(golem), pit);
        }
        if (carriedCount(golem) >= this.ctx.digger().blocksPerTrip) {
            digLog(golem, "seek-stop trip-full carried=" + carriedCount(golem)
                    + " limit=" + this.ctx.digger().blocksPerTrip);
            golem.diggerState(DiggerState.MOVING_TO_CHEST);
            return;
        }
        if (!golem.carried().isEmpty() && !this.ctx.chestService().hasSpace(golem.data())) {
            digLog(golem, "seek-stop chest-full carried=" + carriedCount(golem));
            golem.diggerState(DiggerState.WAITING_CHEST);
            this.ctx.notifyChestFull(golem);
            return;
        }
        if (golem.data().energy() <= this.ctx.settings().energyHungryThreshold) {
            abandonDigTarget(golem);
        }
        if (this.ctx.tryStartFeed(golem)) {
            digLog(golem, "seek-stop feeding energy=" + golem.data().energy());
            return;
        }
        if (golem.data().energy() <= 0) {
            digLog(golem, "seek-stop no-energy");
            abandonDigTarget(golem);
            golem.diggerState(DiggerState.IDLE);
            golem.data().lastActionAt(System.currentTimeMillis());
            return;
        }
        if (!this.ctx.chestService().hasSpace(golem.data())) {
            digLog(golem, "seek-stop chest-full empty-hands");
            golem.diggerState(DiggerState.WAITING_CHEST);
            this.ctx.notifyChestFull(golem);
            return;
        }
        golem.chestFullNotified(false);

        GolemSettings.Yard yard = this.ctx.settings().yard;
        int radius = this.ctx.chestService().effectiveRadius(pit);

        boolean digPriority = pit.hasDigProgress() && !isDepthDone(pit);
        if (!digPriority) {
            digLog(golem, "seek-yard digPriority=false hasProgress=" + pit.hasDigProgress());
            if (yard.collectGroundLoot) {
                GolemGroundLootWork.Phase loot = this.ctx.groundLoot().tick(golem, copper, true);
                if (loot == GolemGroundLootWork.Phase.MOVING || loot == GolemGroundLootWork.Phase.PICKED) {
                    digLog(golem, "seek-stop ground-loot phase=" + loot);
                    golem.diggerState(DiggerState.IDLE);
                    return;
                }
            }
            if (!golem.data().isCrewHelper() && yard.clearBorder) {
                var junk = this.ctx.farmAreaService().diggerYardWeeds(pit, radius);
                if (!junk.isEmpty()) {
                    digLog(golem, "seek-stop yard-clear junk=" + junk.size());
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
                digLog(golem, "seek-stop torch-job");
                golem.clearFetchFlags();
                golem.fetchingTorch(true);
                golem.diggerState(DiggerState.MOVING_TO_CHEST);
                return;
            }
            if (!golem.data().isCrewHelper() && this.support.tryStartFenceJob(golem)) {
                digLog(golem, "seek-stop fence-job");
                return;
            }
            if (!golem.data().isCrewHelper() && this.support.tryStartSeatJob(golem, radius)) {
                digLog(golem, "seek-stop seat-job");
                return;
            }
        }

        if (resumeDigTarget(golem, copper, pit)) {
            digLog(golem, "seek-resume existing-target");
            return;
        }
        releaseAbandonedClaims(this.ctx.pitId(golem), pit);
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
            digLog(golem, "resume-drop not-diggable " + blockPos(block)
                    + " type=" + block.getType()
                    + " why=" + whyNotDiggable(block, pit));
            clearDigTarget(golem, block);
            return false;
        }
        if (!canTakeClaim(pitId, block, diggerId)) {
            digLog(golem, "resume-drop claim-busy " + blockPos(block)
                    + " owner=" + shortId(DiggerClaims.owner(pitId, block)));
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
        UUID pitId = this.ctx.pitId(golem);
        LayerScan scan = scanLayer(pit, pitId, golem.data().id());
        digLog(golem, "assign-scan " + scan.summary()
                + " digStartY=" + pit.digStartY()
                + " digLayerY=" + pit.digLayerY()
                + " stair=" + pit.digStairIndex()
                + " surfaceFill=" + DiggerPit.findHighestPitFillY(pit, this.ctx.digger(), this.ctx.farmAreaService()));
        releaseAbandonedClaims(pitId, pit);
        Block next = findNextDigBlock(golem, copper, pit);
        if (next == null) {
            digLog(golem, "assign-miss pass=1 " + scan.summary());
            releaseAbandonedClaims(pitId, pit);
            next = findNextDigBlock(golem, copper, pit);
        }
        if (next == null) {
            golem.wanderTarget(null);
            if (isDepthDone(pit)) {
                digLog(golem, "assign-stop pit-complete");
                handlePitComplete(golem, copper, this.ctx.chestLink().isLinked(pit));
                return;
            }
            if (layerHasDiggable(pit)) {
                digLog(golem, "assign-force-claims diggable=" + scan.diggable + " claimedBusy=" + scan.claimedBusy);
                forceReleaseLayerClaims(pitId, pit);
                next = findNextDigBlock(golem, copper, pit);
            }
        }
        if (next == null) {
            LayerScan after = scanLayer(pit, pitId, golem.data().id());
            if (layerHasDiggable(pit)) {
                digLogThrottled(golem, "stuck-noclaim", 2000L,
                        "stuck diggable-but-no-claim " + after.summary()
                                + " sample=" + after.sampleWhy);
                golem.diggerState(DiggerState.IDLE);
                golem.data().lastActionAt(System.currentTimeMillis());
                return;
            }
            digLogThrottled(golem, "layer-empty", 2000L,
                    "layer-empty -> stair/descend " + after.summary()
                            + " sample=" + after.sampleWhy);
            if (!golem.data().isCrewHelper()) {
                golem.diggerState(DiggerState.PLACING_STAIR);
                placeStairAndDescend(golem, copper);
            } else {
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
            digLog(golem, "assign " + blockPos(next) + " type=" + next.getType()
                    + " stand=FALLBACK " + locPos(stand));
        } else {
            digLog(golem, "assign " + blockPos(next) + " type=" + next.getType()
                    + " stand=" + locPos(stand));
        }
        golem.wanderTarget(stand.clone());
        if (canReachDigStand(copper, stand)) {
            digLog(golem, "assign-reach start-dig now");
            startDiggingBlock(golem, copper, next);
            return;
        }
        digLog(golem, "assign-walk to=" + locPos(stand)
                + " from=" + locPos(copper.getLocation())
                + " dist2=" + GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand));
        this.ctx.walkTowards(copper, stand, golem);
    }

    public void continueMoveToDig(ActiveGolem golem, CopperGolem copper) {
        Location target = golem.targetOre();
        if (target == null) {
            SoulGolemData pit = this.ctx.pitData(golem);
            digLog(golem, "move no-target diggable=" + layerHasDiggable(pit));
            if (layerHasDiggable(pit)) {
                assignDigTarget(golem, copper, pit);
                return;
            }
            golem.wanderTarget(null);
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        SoulGolemData pit = this.ctx.pitData(golem);
        Block block = target.getBlock();
        if (golem.data().energy() <= 0) {
            digLog(golem, "move-abort no-energy " + blockPos(block));
            clearDigTarget(golem, block);
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        if (!this.ctx.chestLink().isLinked(pit)
                && golem.data().energy() <= this.ctx.settings().energyHungryThreshold
                && this.ctx.chestService().countItem(pit, this.ctx.settings().energyFeedMaterial()) > 0) {
            digLog(golem, "move-abort feed " + blockPos(block));
            clearDigTarget(golem, block);
            this.ctx.tryStartFeed(golem);
            return;
        }
        if (!DiggerPit.isDiggable(block, pit, this.ctx.chestService(), this.ctx.farmAreaService(), this.ctx.digger())) {
            digLog(golem, "move-abort not-diggable " + blockPos(block)
                    + " type=" + block.getType()
                    + " why=" + whyNotDiggable(block, pit));
            clearDigTarget(golem, block);
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        DiggerPit.prepareDigFloor(block, this.ctx.digger());
        Location stand = stableStandForDig(golem, copper, block, pit);
        if (stand == null || !DiggerSafety.hasSolidSupport(stand)) {
            if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), block.getLocation().add(0.5D, 1.0D, 0.5D)) <= 2.25D) {
                stand = copper.getLocation();
                digLogThrottled(golem, "stand-self", 1500L, "move-stand use-self near " + blockPos(block));
            } else {
                digLog(golem, "move-abort no-stand " + blockPos(block)
                        + " type=" + block.getType()
                        + " supportFail standNull=" + (stand == null));
                clearDigTarget(golem, block);
                assignDigTarget(golem, copper, pit);
                return;
            }
        }
        golem.wanderTarget(stand.clone());
        double standDist2 = GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand);
        double dy = Math.abs(copper.getLocation().getY() - stand.getY());
        Location blockCenter = block.getLocation().add(0.5D, 1.0D, 0.5D);
        double blockDist2 = GolemMovement.horizontalDistanceSquared(copper.getLocation(), blockCenter);
        double blockDy = Math.abs(copper.getLocation().getY() - blockCenter.getY());
        if (canReachDigStand(copper, stand) || (blockDist2 <= 6.25D && blockDy <= 2.0D)) {
            DiggerClaims.renew(this.ctx.pitId(golem), block, golem.data().id());
            digLog(golem, "move-reached start-dig " + blockPos(block)
                    + " blockDist2=" + String.format("%.2f", blockDist2));
            startDiggingBlock(golem, copper, block);
            return;
        }
        UUID pitId = this.ctx.pitId(golem);
        Long claimAge = DiggerClaims.claimAgeMs(pitId, block);
        if (claimAge == null) {
            takeClaim(pitId, block, golem.data().id());
            claimAge = 0L;
        }
        if (claimAge >= 3_500L) {
            if (DiggerSafety.hasSolidSupport(stand)) {
                digLog(golem, "move-stuck teleport stand=" + locPos(stand)
                        + " from=" + locPos(copper.getLocation())
                        + " target=" + blockPos(block)
                        + " claimAge=" + claimAge);
                copper.setVelocity(new Vector(0, 0, 0));
                copper.teleport(stand);
                DiggerClaims.renew(pitId, block, golem.data().id());
                startDiggingBlock(golem, copper, block);
                return;
            }
            digLog(golem, "move-stuck give-up target=" + blockPos(block)
                    + " claimAge=" + claimAge
                    + " feet=" + locPos(copper.getLocation())
                    + " stand=" + locPos(stand));
            clearDigTarget(golem, block);
            golem.diggerState(DiggerState.IDLE);
            golem.data().lastActionAt(System.currentTimeMillis());
            return;
        }
        if (copper.getLocation().getY() < stand.getY() - 1.4D) {
            digLogThrottled(golem, "climb-" + blockPos(block), 1500L,
                    "move-climb-up target=" + blockPos(block)
                            + " feet=" + locPos(copper.getLocation())
                            + " stand=" + locPos(stand)
                            + " claimAge=" + claimAge);
            this.ctx.movement().walkClimbStair(copper, stand, golem);
            return;
        }
        digLogThrottled(golem, "walk-" + blockPos(block), 1500L,
                "move-walk target=" + blockPos(block)
                        + " type=" + block.getType()
                        + " stand=" + locPos(stand)
                        + " feet=" + locPos(copper.getLocation())
                        + " dist2=" + String.format("%.2f", standDist2)
                        + " blockDist2=" + String.format("%.2f", blockDist2)
                        + " dy=" + String.format("%.2f", dy)
                        + " claimAge=" + claimAge);
        this.ctx.walkTowards(copper, stand, golem);
    }

    public void continueDig(ActiveGolem golem, CopperGolem copper) {
        Location target = golem.targetOre();
        if (target == null) {
            digLog(golem, "dig-abort no target");
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        SoulGolemData pit = this.ctx.pitData(golem);
        Block block = target.getBlock();
        if (golem.data().energy() <= 0) {
            digLog(golem, "dig-abort no energy " + blockPos(block));
            clearDigTarget(golem, block);
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        if (!DiggerPit.isDiggable(block, pit, this.ctx.chestService(), this.ctx.farmAreaService(), this.ctx.digger())) {
            digLog(golem, "dig-abort not-diggable " + blockPos(block)
                    + " type=" + block.getType()
                    + " why=" + whyNotDiggable(block, pit));
            clearDigTarget(golem, block);
            golem.diggerState(DiggerState.IDLE);
            return;
        }
        DiggerPit.prepareDigFloor(block, this.ctx.digger());
        DiggerClaims.renew(this.ctx.pitId(golem), block, golem.data().id());
        this.ctx.equipForBlock(copper, block.getType());
        long step = digStepTicks(golem);
        long before = golem.mineTicksLeft();
        long left = before - step;
        golem.mineTicksLeft(left);
        if (left > 0L) {
            digLogThrottled(golem, "hit-" + blockPos(block), 1000L,
                    "dig-hit " + blockPos(block) + " type=" + block.getType()
                            + " left=" + before + "->" + left + " step=" + step
                            + " boost=" + this.ctx.stickBoostFactor(golem));
            GolemGaze.faceBlock(golem, block);
            this.ctx.playDigFx(block);
            return;
        }

        int dugX = block.getX();
        int dugY = block.getY();
        int dugZ = block.getZ();
        Collection<ItemStack> drops = block.getDrops(this.ctx.digTool(block.getType()));
        this.ctx.playDigBurst(block);
        digLog(golem, "dig-break " + blockPos(block) + " type=" + block.getType());
        block.setType(Material.AIR, false);
        DiggerPit.prepareDigFloor(block, this.ctx.digger());
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
        ensureDigProgress(pit, this.ctx.digger(), this.ctx.farmAreaService());
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
            if (isDepthDone(pit)) {
                handlePitComplete(golem, copper, this.ctx.chestLink().isLinked(pit));
                return;
            }
            int y = pit.digLayerY();
            if (y - 1 < world.getMinHeight()) {
                digLog(golem, "stair-stop bedrock-min layerY=" + y);
                handlePitComplete(golem, copper, this.ctx.chestLink().isLinked(pit));
                return;
            }
            int radiusForIndex = DiggerPit.radius(this.ctx.digger());
            int stairIndex = DiggerPit.stairIndexForY(pit, radiusForIndex, y);
            int[] cell = DiggerPit.stairCell(pit, radiusForIndex, stairIndex);
            boolean corner = DiggerPit.isCornerStairIndex(pit, radiusForIndex, stairIndex);
            int[] next = corner ? DiggerPit.stairCell(pit, radiusForIndex, stairIndex + 1) : cell;
            digLog(golem, "stair-plan layerY=" + y
                    + " index=" + stairIndex
                    + " corner=" + corner
                    + " support=" + cell[0] + "," + (y - 1) + "," + cell[1]
                    + (corner
                    ? " landing=" + cell[0] + "," + y + "," + cell[1]
                    + " stair=" + next[0] + "," + y + "," + next[1]
                    + " support2=" + next[0] + "," + (y - 1) + "," + next[1]
                    : " stair=" + cell[0] + "," + y + "," + cell[1]));
            int used = DiggerPit.placeStair(world, pit, this.ctx.digger(), y, stairIndex);
            Block supportBlock = world.getBlockAt(cell[0], y - 1, cell[1]);
            Block mainBlock = world.getBlockAt(cell[0], y, cell[1]);
            Block nextBlock = world.getBlockAt(next[0], y, next[1]);
            Block nextSupport = world.getBlockAt(next[0], y - 1, next[1]);
            if (used <= 0) {
                int before = pit.digLayerY();
                ensureDigProgress(pit, this.ctx.digger(), this.ctx.farmAreaService());
                if (pit.digLayerY() > before || layerHasDiggable(pit)) {
                    digLog(golem, "stair-abort surface layerY=" + pit.digLayerY());
                    golem.diggerState(DiggerState.IDLE);
                    golem.data().lastActionAt(0L);
                    return;
                }
                digLog(golem, "stair-fail layerY=" + y + " stair=" + stairIndex
                        + " cell=" + cell[0] + "," + y + "," + cell[1]
                        + " got=" + mainBlock.getType()
                        + " descend anyway");
            }
            digLog(golem, "stair-placed used=" + used
                    + " support@" + cell[0] + "," + (y - 1) + "," + cell[1] + "=" + supportBlock.getType()
                    + (corner
                    ? " landing@" + cell[0] + "," + y + "," + cell[1] + "=" + mainBlock.getType()
                    + " stair@" + next[0] + "," + y + "," + next[1] + "=" + nextBlock.getType()
                    + " support2@" + next[0] + "," + (y - 1) + "," + next[1] + "=" + nextSupport.getType()
                    : " stair@" + cell[0] + "," + y + "," + cell[1] + "=" + mainBlock.getType())
                    + " -> digLayerY " + y + "->" + (y - 1));
            pit.digLayerY(y - 1);
            pit.digStairIndex(DiggerPit.stairIndexForY(pit, radiusForIndex, pit.digLayerY()));
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
        if (needsLeaderAscent(golem, pit, this.ctx.digger(), copper.getLocation())) {
            golem.diggerState(DiggerState.ESCAPING);
            return;
        }
        golem.diggerState(DiggerState.DONE);
        if (GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand) > 2.25D
                || Math.abs(copper.getLocation().getY() - stand.getY()) > 1.8D) {
            if (copper.getLocation().getY() < stand.getY() - 1.0D) {
                this.ctx.movement().walkClimbStair(copper, stand, golem);
            } else {
                this.ctx.walkTowards(copper, stand, golem);
            }
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
        if (needsLeaderAscent(golem, pit, this.ctx.digger(), copper.getLocation())) {
            golem.diggerState(DiggerState.ESCAPING);
            return;
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

    public static boolean layerHasDiggable(
            SoulGolemData pit,
            SoulChestService chestService,
            FarmAreaService farmArea,
            GolemSettings.Digger digger
    ) {
        World world = Bukkit.getWorld(pit.worldName());
        if (world == null) {
            return false;
        }
        int radius = DiggerPit.radius(digger);
        int y = pit.digLayerY();
        for (int z = DiggerPit.digMinZ(pit, radius); z <= DiggerPit.digMaxZ(pit, radius); z++) {
            for (int x = DiggerPit.digMinX(pit, radius); x <= DiggerPit.digMaxX(pit, radius); x++) {
                Block block = world.getBlockAt(x, y, z);
                if (DiggerPit.isDiggable(block, pit, chestService, farmArea, digger)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean layerHasDiggable(SoulGolemData pit) {
        return layerHasDiggable(pit, this.ctx.chestService(), this.ctx.farmAreaService(), this.ctx.digger());
    }

    private void placeCurrentLayerStair(ActiveGolem golem, SoulGolemData pit) {
        World world = Bukkit.getWorld(pit.worldName());
        if (world == null || !pit.hasDigProgress()) {
            return;
        }
        synchronized (("digger-stair-" + pit.id()).intern()) {
            int y = pit.digLayerY();
            int stairIndex = DiggerPit.stairIndexForY(pit, DiggerPit.radius(this.ctx.digger()), y);
            int[] cell = DiggerPit.stairCell(pit, DiggerPit.radius(this.ctx.digger()), stairIndex);
            int used = DiggerPit.placeStair(world, pit, this.ctx.digger(), y, stairIndex);
            if (used > 0) {
                digLog(golem, "stair-slot layerY=" + y
                        + " index=" + stairIndex
                        + " cell=" + cell[0] + "," + y + "," + cell[1]
                        + " used=" + used);
                this.ctx.markPitDirty(golem);
            }
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
        int stairIndex = DiggerPit.stairIndexForY(pit, radius, y);
        int[] stairCell = DiggerPit.stairCell(pit, radius, stairIndex);
        int[] stairWalk = DiggerPit.stairWalkCell(pit, radius, stairIndex);
        double ox = copper.getLocation().getX();
        double oz = copper.getLocation().getZ();
        int width = maxX - minX + 1;
        Location lastTarget = golem.targetOre();
        int lastX = lastTarget != null ? lastTarget.getBlockX() : Integer.MIN_VALUE;
        int lastZ = lastTarget != null ? lastTarget.getBlockZ() : Integer.MIN_VALUE;

        List<ScoredBlock> candidates = new ArrayList<>();
        int skippedClaim = 0;
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                Block block = world.getBlockAt(x, y, z);
                if (!DiggerPit.isDiggable(block, pit, this.ctx.chestService(), this.ctx.farmAreaService(), this.ctx.digger())) {
                    continue;
                }
                double dx = (x + 0.5D) - ox;
                double dz = (z + 0.5D) - oz;
                double myDist2 = dx * dx + dz * dz;
                if (!canTakeClaim(pitId, block, diggerId)) {
                    skippedClaim++;
                    continue;
                }
                int cellIndex = (z - minZ) * width + (x - minX);
                boolean stairSlot = (x == stairCell[0] && z == stairCell[1])
                        || (x == stairWalk[0] && z == stairWalk[1]);
                long score = (long) (myDist2 * 100.0D);
                if (stairSlot) {
                    score -= 50_000L;
                }
                if (Math.floorMod(cellIndex, crewSize) == myIndex) {
                    score -= 500L;
                }
                if (lastX != Integer.MIN_VALUE && Math.abs(x - lastX) + Math.abs(z - lastZ) <= 1) {
                    score -= 800L;
                }
                candidates.add(new ScoredBlock(block, score, myDist2));
            }
        }
        candidates.sort(Comparator.comparingLong(ScoredBlock::score));
        digLogThrottled(golem, "find-" + y, 2000L,
                "find-layer y=" + y
                        + " candidates=" + candidates.size()
                        + " claimBusy=" + skippedClaim
                        + " crew=" + myIndex + "/" + crewSize
                        + " stairCell=" + stairCell[0] + "," + stairCell[1]
                        + " topCandidate=" + (candidates.isEmpty()
                        ? "none"
                        : blockPos(candidates.get(0).block()) + ":" + candidates.get(0).block().getType()));
        return claimFirst(candidates, pitId, diggerId);
    }

    private boolean canTakeClaim(UUID pitId, Block block, UUID diggerId) {
        UUID ownerId = DiggerClaims.owner(pitId, block);
        if (ownerId == null || ownerId.equals(diggerId)) {
            return true;
        }
        ActiveGolem owner = this.ctx.registry().byId(ownerId).orElse(null);
        if (owner == null) {
            DiggerClaims.forceRelease(pitId, block);
            return true;
        }
        if (isWorkingBlock(owner, block)) {
            return false;
        }
        digLog(owner, "claim-steal by=" + shortId(diggerId) + " " + blockPos(block)
                + " ownerState=" + owner.diggerState());
        transferBlockTask(pitId, owner, block);
        return true;
    }

    private boolean isWorkingBlock(ActiveGolem owner, Block block) {
        if (owner.data().energy() <= 0 || owner.fetchingFeed()) {
            return false;
        }
        DiggerState state = owner.diggerState();
        if (state != DiggerState.DIGGING && state != DiggerState.MOVING_TO_DIG) {
            return false;
        }
        Location target = owner.targetOre();
        if (target == null
                || target.getBlockX() != block.getX()
                || target.getBlockY() != block.getY()
                || target.getBlockZ() != block.getZ()) {
            return false;
        }
        if (state == DiggerState.MOVING_TO_DIG) {
            Long age = DiggerClaims.claimAgeMs(this.ctx.pitId(owner), block);
            return age != null && age < 4_000L;
        }
        return isBreakingBlock(owner, block);
    }

    private boolean isBreakingBlock(ActiveGolem owner, Block block) {
        if (owner.data().energy() <= 0 || owner.fetchingFeed()) {
            return false;
        }
        if (owner.diggerState() != DiggerState.DIGGING) {
            return false;
        }
        Location target = owner.targetOre();
        if (target == null
                || target.getBlockX() != block.getX()
                || target.getBlockY() != block.getY()
                || target.getBlockZ() != block.getZ()) {
            return false;
        }
        double ox = owner.data().x() - (block.getX() + 0.5D);
        double oz = owner.data().z() - (block.getZ() + 0.5D);
        return ox * ox + oz * oz <= 9.0D;
    }

    private void transferBlockTask(UUID pitId, ActiveGolem owner, Block block) {
        DiggerClaims.forceRelease(pitId, block);
        Location target = owner.targetOre();
        if (target == null
                || target.getBlockX() != block.getX()
                || target.getBlockY() != block.getY()
                || target.getBlockZ() != block.getZ()) {
            return;
        }
        digLog(owner, "claim-lost " + blockPos(block) + " state=" + owner.diggerState());
        owner.targetOre(null);
        owner.oreMaterial(null);
        owner.wanderTarget(null);
        if (owner.diggerState() == DiggerState.MOVING_TO_DIG || owner.diggerState() == DiggerState.DIGGING) {
            owner.diggerState(DiggerState.IDLE);
        }
        owner.data().lastActionAt(0L);
    }

    private void releaseAbandonedClaims(UUID pitId, SoulGolemData pit) {
        World world = Bukkit.getWorld(pit.worldName());
        if (world == null) {
            return;
        }
        int radius = DiggerPit.radius(this.ctx.digger());
        int y = pit.digLayerY();
        for (int z = DiggerPit.digMinZ(pit, radius); z <= DiggerPit.digMaxZ(pit, radius); z++) {
            for (int x = DiggerPit.digMinX(pit, radius); x <= DiggerPit.digMaxX(pit, radius); x++) {
                Block block = world.getBlockAt(x, y, z);
                if (!DiggerPit.isDiggable(block, pit, this.ctx.chestService(), this.ctx.farmAreaService(), this.ctx.digger())) {
                    continue;
                }
                UUID ownerId = DiggerClaims.owner(pitId, block);
                if (ownerId == null) {
                    continue;
                }
                ActiveGolem owner = this.ctx.registry().byId(ownerId).orElse(null);
                if (owner == null) {
                    DiggerClaims.forceRelease(pitId, block);
                    continue;
                }
                if (isWorkingBlock(owner, block)) {
                    continue;
                }
                Long age = DiggerClaims.claimAgeMs(pitId, block);
                if (age != null && age < 8_000L) {
                    continue;
                }
                digLog(owner, "claim-abandon " + blockPos(block) + " age=" + age + " state=" + owner.diggerState());
                transferBlockTask(pitId, owner, block);
            }
        }
    }

    private Block claimFirst(List<ScoredBlock> candidates, UUID pitId, UUID diggerId) {
        for (ScoredBlock scored : candidates) {
            DiggerPit.prepareDigFloor(scored.block(), this.ctx.digger());
            if (!canTakeClaim(pitId, scored.block(), diggerId)) {
                continue;
            }
            takeClaim(pitId, scored.block(), diggerId);
            return scored.block();
        }
        return null;
    }

    private void forceReleaseLayerClaims(UUID pitId, SoulGolemData pit) {
        World world = Bukkit.getWorld(pit.worldName());
        if (world == null) {
            return;
        }
        int radius = DiggerPit.radius(this.ctx.digger());
        int y = pit.digLayerY();
        for (int z = DiggerPit.digMinZ(pit, radius); z <= DiggerPit.digMaxZ(pit, radius); z++) {
            for (int x = DiggerPit.digMinX(pit, radius); x <= DiggerPit.digMaxX(pit, radius); x++) {
                Block block = world.getBlockAt(x, y, z);
                UUID ownerId = DiggerClaims.owner(pitId, block);
                if (ownerId == null) {
                    continue;
                }
                ActiveGolem owner = this.ctx.registry().byId(ownerId).orElse(null);
                if (owner == null) {
                    DiggerClaims.forceRelease(pitId, block);
                    continue;
                }
                Long age = DiggerClaims.claimAgeMs(pitId, block);
                if (isWorkingBlock(owner, block) && (age == null || age < 4_000L)) {
                    continue;
                }
                digLog(owner, "claim-force " + blockPos(block) + " age=" + age
                        + " ownerState=" + owner.diggerState());
                transferBlockTask(pitId, owner, block);
            }
        }
    }

    private void takeClaim(UUID pitId, Block block, UUID diggerId) {
        UUID previous = DiggerClaims.claim(pitId, block, diggerId);
        if (previous == null || previous.equals(diggerId)) {
            return;
        }
        this.ctx.registry().byId(previous).ifPresent(other -> transferBlockTask(pitId, other, block));
    }

    private record ScoredBlock(Block block, long score, double dist2) {
    }

    private long digStepTicks(ActiveGolem golem) {
        return Math.max(1L, this.ctx.settings().coordinatorPeriodTicks);
    }

    private static boolean canReachDigStand(CopperGolem copper, Location stand) {
        if (stand == null) {
            return false;
        }
        double standDist2 = GolemMovement.horizontalDistanceSquared(copper.getLocation(), stand);
        return standDist2 <= 1.69D && Math.abs(copper.getLocation().getY() - stand.getY()) <= 1.6D;
    }

    private void startDiggingBlock(ActiveGolem golem, CopperGolem copper, Block block) {
        copper.setVelocity(new Vector(0, 0, 0));
        this.ctx.equipForBlock(copper, block.getType());
        golem.diggerState(DiggerState.DIGGING);
        GolemGaze.faceBlock(golem, block);
        long duration = this.ctx.digDurationTicks(block.getType(), golem);
        golem.mineTicksLeft(duration);
        digLog(golem, "dig-start " + blockPos(block) + " type=" + block.getType()
                + " duration=" + duration + " boost=" + this.ctx.stickBoostFactor(golem));
        this.ctx.playDigFx(block);
    }

    public void logTick(ActiveGolem golem, String message) {
        digLogThrottled(golem, "tick-" + message, 2000L, message);
    }

    private void digLog(ActiveGolem golem, String message) {
        if (!this.ctx.digger().digDebugLogs) {
            return;
        }
        Logger log = this.ctx.plugin().getLogger();
        if (golem == null) {
            log.info("[dig] ? " + message);
            return;
        }
        String name = golem.data().isCrewHelper() ? "helper" : "leader";
        log.info("[dig] " + name + "/" + shortId(golem.data().id()) + " " + message);
    }

    private void digLogThrottled(ActiveGolem golem, String key, long intervalMs, String message) {
        if (!this.ctx.digger().digDebugLogs) {
            return;
        }
        String fullKey = (golem == null ? "?" : golem.data().id()) + ":" + key;
        long now = System.currentTimeMillis();
        Long prev = this.logThrottle.get(fullKey);
        if (prev != null && now - prev < intervalMs) {
            return;
        }
        this.logThrottle.put(fullKey, now);
        digLog(golem, message);
    }

    private static String shortId(UUID id) {
        if (id == null) {
            return "?";
        }
        return id.toString().substring(0, 8);
    }

    private static String blockPos(Block block) {
        return block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private static String locPos(Location loc) {
        if (loc == null) {
            return "null";
        }
        return String.format("%.1f,%.1f,%.1f", loc.getX(), loc.getY(), loc.getZ());
    }

    private String whyNotDiggable(Block block, SoulGolemData pit) {
        if (block == null) {
            return "null-block";
        }
        if (DiggerPit.isProtected(block, pit, this.ctx.chestService())) {
            return "protected:" + block.getType();
        }
        if (DiggerPit.isStairStructureBlock(block, pit, this.ctx.digger())) {
            return "stair-structure:" + block.getType();
        }
        int radius = DiggerPit.radius(this.ctx.digger());
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        if (!DiggerPit.isInsidePit(pit, x, z, radius)) {
            return "outside-pit";
        }
        if (pit.hasDigProgress()) {
            if (y != pit.digLayerY()) {
                return "wrong-layer y=" + y + " need=" + pit.digLayerY();
            }
            if (y > pit.digStartY()) {
                return "above-start y=" + y + " start=" + pit.digStartY();
            }
        }
        if (DiggerPit.isBorderColumn(pit, x, z, radius)) {
            return "border-column";
        }
        if (DiggerPit.isStationColumn(pit, x, z)) {
            return "station-column";
        }
        Material type = block.getType();
        if (this.ctx.farmAreaService().isBorderMaterial(type)) {
            return "border-material:" + type;
        }
        if (FarmAreaService.isFoliage(type) || FarmAreaService.isVegetation(type) || type == Material.SNOW) {
            return "ok-foliage";
        }
        if (!type.isSolid()) {
            return "not-solid:" + type;
        }
        return "unknown:" + type;
    }

    private LayerScan scanLayer(SoulGolemData pit, UUID pitId, UUID diggerId) {
        LayerScan scan = new LayerScan();
        World world = Bukkit.getWorld(pit.worldName());
        if (world == null || !pit.hasDigProgress()) {
            scan.sampleWhy = "no-world-or-progress";
            return scan;
        }
        int radius = DiggerPit.radius(this.ctx.digger());
        int y = pit.digLayerY();
        StringBuilder samples = new StringBuilder();
        int samplesLeft = 4;
        for (int z = DiggerPit.digMinZ(pit, radius); z <= DiggerPit.digMaxZ(pit, radius); z++) {
            for (int x = DiggerPit.digMinX(pit, radius); x <= DiggerPit.digMaxX(pit, radius); x++) {
                scan.cells++;
                Block block = world.getBlockAt(x, y, z);
                Material type = block.getType();
                if (type.isAir()) {
                    scan.air++;
                    continue;
                }
                if (DiggerPit.isDiggable(block, pit, this.ctx.chestService(), this.ctx.farmAreaService(), this.ctx.digger())) {
                    scan.diggable++;
                    UUID owner = DiggerClaims.owner(pitId, block);
                    if (owner != null && !owner.equals(diggerId)) {
                        ActiveGolem other = this.ctx.registry().byId(owner).orElse(null);
                        if (other != null && isWorkingBlock(other, block)) {
                            scan.claimedBusy++;
                        } else {
                            scan.claimedStale++;
                        }
                    } else {
                        scan.free++;
                    }
                    continue;
                }
                scan.blocked++;
                if (samplesLeft > 0) {
                    if (!samples.isEmpty()) {
                        samples.append(" | ");
                    }
                    samples.append(blockPos(block)).append('=').append(type)
                            .append('(').append(whyNotDiggable(block, pit)).append(')');
                    samplesLeft--;
                }
            }
        }
        scan.sampleWhy = samples.isEmpty() ? "-" : samples.toString();
        return scan;
    }

    private static final class LayerScan {
        int cells;
        int air;
        int diggable;
        int free;
        int claimedBusy;
        int claimedStale;
        int blocked;
        String sampleWhy = "-";

        String summary() {
            return "cells=" + this.cells
                    + " air=" + this.air
                    + " diggable=" + this.diggable
                    + " free=" + this.free
                    + " claimBusy=" + this.claimedBusy
                    + " claimStale=" + this.claimedStale
                    + " blocked=" + this.blocked;
        }
    }

    private Location stableStandForDig(ActiveGolem golem, CopperGolem copper, Block block, SoulGolemData pit) {
        Location cached = golem.wanderTarget();
        if (cached != null
                && cached.getWorld() != null
                && cached.getWorld().equals(block.getWorld())
                && !isStandOnStair(cached)
                && DiggerSafety.hasSolidSupport(cached)
                && GolemMovement.horizontalDistanceSquared(cached, block.getLocation().add(0.5D, 1.0D, 0.5D)) <= 6.25D) {
            return cached;
        }
        return standForDig(block, pit, copper.getLocation());
    }

    private static boolean isStandOnStair(Location stand) {
        if (stand == null || stand.getWorld() == null) {
            return false;
        }
        Block below = stand.getBlock().getRelative(BlockFace.DOWN);
        return org.bukkit.Tag.STAIRS.isTagged(below.getType());
    }

    private Location standForDig(Block block, SoulGolemData pit, Location from) {
        World world = block.getWorld();
        double hx = block.getX() + 0.5D;
        double hz = block.getZ() + 0.5D;
        double fx = from != null ? from.getX() : hx;
        double fz = from != null ? from.getZ() : hz;
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
                continue;
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
        ensureDigProgress(data, new GolemSettings.Digger(), null);
    }

    public static void ensureDigProgress(
            SoulGolemData data,
            GolemSettings.Digger digger,
            FarmAreaService farmArea
    ) {
        if (data == null || digger == null) {
            return;
        }
        int surface = DiggerPit.findHighestPitFillY(data, digger, farmArea);
        if (!data.hasDigProgress()) {
            data.digStartY(surface);
            data.digLayerY(surface);
            data.digStairIndex(0);
            return;
        }
        int above = DiggerPit.findHighestPitFillAbove(data, digger, farmArea, data.digLayerY());
        if (above > data.digLayerY()) {
            data.digStartY(Math.max(data.digStartY(), above));
            data.digLayerY(above);
        }
        int canonical = DiggerPit.stairIndexForY(data, DiggerPit.radius(digger), data.digLayerY());
        if (data.digStairIndex() != canonical) {
            data.digStairIndex(canonical);
        }
    }

    private boolean isDepthDone(SoulGolemData data) {
        return isPitComplete(data, this.ctx.digger());
    }

    public static boolean isPitComplete(SoulGolemData data, GolemSettings.Digger digger) {
        if (data == null || !data.hasDigProgress() || digger == null) {
            return false;
        }
        World world = Bukkit.getWorld(data.worldName());
        if (world == null) {
            return false;
        }
        if (data.digLayerY() <= world.getMinHeight()) {
            return true;
        }
        if (DiggerPit.isBedrockFloorLayer(data, world, DiggerPit.radius(digger))) {
            return true;
        }
        if (digger.maxDepth > 0 && data.digStartY() - data.digLayerY() >= digger.maxDepth) {
            return true;
        }
        return false;
    }

    public static boolean isPitComplete(SoulGolemData data, int maxDepth) {
        GolemSettings.Digger digger = new GolemSettings.Digger();
        digger.maxDepth = maxDepth;
        return isPitComplete(data, digger);
    }

    public static boolean isCrewReturning(ActiveGolem golem, SoulGolemData pit, GolemSettings.Digger digger) {
        return golem != null
                && golem.data().isCrewHelper()
                && (golem.crewReturning() || isPitComplete(pit, digger));
    }

    public static boolean needsLeaderAscent(ActiveGolem golem, SoulGolemData pit, GolemSettings.Digger digger, Location feet) {
        return golem != null
                && feet != null
                && !golem.data().isCrewHelper()
                && isPitComplete(pit, digger)
                && feet.getY() < pit.homeY() - 1.4D;
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
