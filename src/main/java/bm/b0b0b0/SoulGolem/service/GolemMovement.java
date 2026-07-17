package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.settings.Settings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import com.destroystokyo.paper.entity.Pathfinder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.CopperGolem;
import org.bukkit.util.Vector;

public final class GolemMovement {

    private static final double ARRIVE_SQ = 0.36D;
    private static final double SAME_GOAL_SQ = 1.21D;

    private final Settings settings;
    private final SoulChestService chestService;
    private final FarmAreaService farmAreaService;

    public GolemMovement(Settings settings, SoulChestService chestService) {
        this(settings, chestService, null);
    }

    public GolemMovement(Settings settings, SoulChestService chestService, FarmAreaService farmAreaService) {
        this.settings = settings;
        this.chestService = chestService;
        this.farmAreaService = farmAreaService;
    }

    public void walkTowards(CopperGolem copper, Location target, SoulGolemData data) {
        walkTowards(copper, target, data, null);
    }

    public void walkTowards(CopperGolem copper, Location target, ActiveGolem golem) {
        walkTowards(copper, target, golem.data(), golem);
    }

    public void walkTowards(CopperGolem copper, Location target, SoulGolemData data, ActiveGolem golem) {
        Location from = copper.getLocation();
        if (from.getWorld() == null || target.getWorld() == null || !from.getWorld().equals(target.getWorld())) {
            return;
        }

        Location goal = resolveGoal(from, target, data, golem);
        snapFeet(goal, data);

        if (blocked(data, goal)) {
            Location open = stepAwayFromHazard(from, goal, target, data, 1.0D);
            if (open != null) {
                goal = open;
            }
        }

        if (horizontalDistanceSquared(from, goal) < ARRIVE_SQ
                && Math.abs(from.getY() - goal.getY()) < 1.25D) {
            stop(copper);
            return;
        }

        double speed = Math.max(1.0D, this.settings.walkSpeed);
        if (golem != null) {
            Location look = target.clone();
            look.setY(look.getY() + 0.9D);
            GolemGaze.face(golem, look);
        }
        if (alreadyPathingTo(copper, goal)) {
            return;
        }
        copper.getPathfinder().moveTo(goal, speed);
    }

    public void stop(CopperGolem copper) {
        if (copper == null || !copper.isValid()) {
            return;
        }
        copper.getPathfinder().stopPathfinding();
        Vector velocity = copper.getVelocity();
        copper.setVelocity(new Vector(0.0D, velocity.getY(), 0.0D));
    }

    private static boolean alreadyPathingTo(CopperGolem copper, Location goal) {
        Pathfinder pathfinder = copper.getPathfinder();
        if (!pathfinder.hasPath()) {
            return false;
        }
        Pathfinder.PathResult path = pathfinder.getCurrentPath();
        if (path == null) {
            return false;
        }
        Location finalPoint = path.getFinalPoint();
        return finalPoint != null
                && finalPoint.getWorld() != null
                && finalPoint.getWorld().equals(goal.getWorld())
                && horizontalDistanceSquared(finalPoint, goal) <= SAME_GOAL_SQ;
    }

    private Location resolveGoal(Location from, Location target, SoulGolemData data, ActiveGolem golem) {
        Location goal = target.clone();

        if (this.farmAreaService != null && pathCrossesWater(from, target, data)) {
            if (golem != null
                    && golem.pathWaypoint() != null
                    && golem.pathGoalMatches(target)
                    && !blocked(data, golem.pathWaypoint())
                    && horizontalDistanceSquared(from, golem.pathWaypoint()) > 0.35D) {
                return golem.pathWaypoint().clone();
            }
            Location detour = stableWaterDetour(from, target, data);
            if (detour != null) {
                if (golem != null) {
                    golem.pathWaypoint(detour.clone());
                    golem.pathGoal(target);
                }
                goal = detour;
            } else if (golem != null) {
                golem.clearPathWaypoint();
            }
        } else if (golem != null) {
            golem.clearPathWaypoint();
        }

        if (pathCrossesStation(from, goal, data)) {
            Location beside = sidestepAroundStation(from, goal, data);
            if (beside != null) {
                goal = beside;
            } else {
                Location detour = stableStationDetour(from, target, data);
                if (detour != null) {
                    if (golem != null) {
                        golem.pathWaypoint(detour.clone());
                        golem.pathGoal(target);
                    }
                    goal = detour;
                }
            }
        }
        return goal;
    }

    private Location stepAwayFromHazard(
            Location from,
            Location goal,
            Location finalTarget,
            SoulGolemData data,
            double step
    ) {
        Vector toGoal = goal.toVector().subtract(from.toVector());
        toGoal.setY(0.0D);
        if (toGoal.lengthSquared() < 1.0E-6D) {
            toGoal = finalTarget.toVector().subtract(from.toVector());
            toGoal.setY(0.0D);
        }
        if (toGoal.lengthSquared() < 1.0E-6D) {
            return null;
        }
        toGoal.normalize();

        int waterSide = waterSideSign(from, finalTarget, data);
        double[] angles = waterSide >= 0
                ? new double[]{-90.0D, -45.0D, -135.0D, 90.0D, 45.0D}
                : new double[]{90.0D, 45.0D, 135.0D, -90.0D, -45.0D};

        Location best = null;
        double bestScore = Double.MAX_VALUE;
        for (double degrees : angles) {
            double rad = Math.toRadians(degrees);
            double cos = Math.cos(rad);
            double sin = Math.sin(rad);
            Vector dir = new Vector(
                    toGoal.getX() * cos - toGoal.getZ() * sin,
                    0.0D,
                    toGoal.getX() * sin + toGoal.getZ() * cos
            ).multiply(step);
            Location candidate = from.clone().add(dir);
            candidate.setY(goal.getY());
            snapFeet(candidate, data);
            if (blocked(data, candidate)) {
                continue;
            }
            double score = horizontalDistanceSquared(candidate, finalTarget);
            if (this.farmAreaService != null && pathCrossesWater(candidate, finalTarget, data)) {
                score += 6.0D;
            }
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private int waterSideSign(Location from, Location target, SoulGolemData data) {
        if (this.farmAreaService == null) {
            return 1;
        }
        double wx = Math.floor(data.homeX()) + 0.5D;
        double wz = Math.floor(data.homeZ()) + 0.5D;
        double tx = target.getX() - from.getX();
        double tz = target.getZ() - from.getZ();
        double cross = tx * (wz - from.getZ()) - tz * (wx - from.getX());
        return cross >= 0.0D ? 1 : -1;
    }

    private boolean blocked(SoulGolemData data, Location location) {
        if (this.chestService.collidesWithStation(data, location)) {
            return true;
        }
        if (this.farmAreaService != null && this.farmAreaService.isWaterBlock(data, location)) {
            return true;
        }
        Material feet = location.getBlock().getType();
        return Tag.FENCES.isTagged(feet) || Tag.FENCE_GATES.isTagged(feet);
    }

    private static boolean isClimbableSolid(Material type) {
        if (!type.isSolid()) {
            return false;
        }
        if (SoulChestService.isChestLike(type) || type == Material.CRAFTING_TABLE || type == Material.COMPOSTER || type == Material.WHEAT) {
            return false;
        }
        if (FarmAreaService.isVegetation(type)) {
            return false;
        }
        return !Tag.FENCES.isTagged(type) && !Tag.FENCE_GATES.isTagged(type);
    }

    private void snapFeet(Location next, SoulGolemData data) {
        int homeY = (int) Math.floor(data.homeY());
        int homeX = (int) Math.floor(data.homeX());
        int homeZ = (int) Math.floor(data.homeZ());
        Block feet = next.getBlock();
        Material feetType = feet.getType();
        if (Tag.FENCES.isTagged(feetType) || Tag.FENCE_GATES.isTagged(feetType)) {
            pushOffFenceColumn(next, homeX, homeY, homeZ);
            return;
        }
        Block below = feet.getRelative(0, -1, 0);
        Material belowType = below.getType();
        if (belowType.isSolid() || belowType == Material.FARMLAND) {
            if (isClimbableSolid(feetType)) {
                next.setY(feet.getY() + 1.0D);
            } else {
                next.setY(below.getY() + 1.0D);
            }
            if (Tag.FENCES.isTagged(next.getBlock().getType()) || Tag.FENCE_GATES.isTagged(next.getBlock().getType())) {
                pushOffFenceColumn(next, homeX, homeY, homeZ);
            }
            return;
        }
        if (feetType.isSolid() || feetType == Material.FARMLAND) {
            if (Tag.FENCES.isTagged(feetType) || Tag.FENCE_GATES.isTagged(feetType)) {
                pushOffFenceColumn(next, homeX, homeY, homeZ);
            } else {
                next.setY(feet.getY() + 1.0D);
            }
            return;
        }
        Block ground = next.getWorld().getBlockAt(next.getBlockX(), homeY, next.getBlockZ());
        if (ground.getType().isSolid() || ground.getType() == Material.FARMLAND) {
            next.setY(homeY + 1.0D);
        }
        if (Tag.FENCES.isTagged(next.getBlock().getType()) || Tag.FENCE_GATES.isTagged(next.getBlock().getType())) {
            pushOffFenceColumn(next, homeX, homeY, homeZ);
        }
    }

    private static void pushOffFenceColumn(Location next, int homeX, int homeY, int homeZ) {
        int bx = next.getBlockX();
        int bz = next.getBlockZ();
        int nx = bx + Integer.compare(homeX, bx);
        int nz = bz + Integer.compare(homeZ, bz);
        if (nx == bx && nz == bz) {
            nx = bx + 1;
        }
        next.setX(nx + 0.5D);
        next.setZ(nz + 0.5D);
        next.setY(homeY + 1.0D);
        if (Tag.FENCES.isTagged(next.getBlock().getType()) || Tag.FENCE_GATES.isTagged(next.getBlock().getType())) {
            nx = bx + Integer.compare(homeX, bx) * 2;
            nz = bz + Integer.compare(homeZ, bz) * 2;
            if (nx == bx && nz == bz) {
                nx = bx + 2;
            }
            next.setX(nx + 0.5D);
            next.setZ(nz + 0.5D);
            next.setY(homeY + 1.0D);
        }
    }

    public boolean insideWorkArea(SoulGolemData data, Location location) {
        int radius = this.chestService.effectiveRadius(data);
        double hx = Math.floor(data.homeX()) + 0.5D;
        double hz = Math.floor(data.homeZ()) + 0.5D;
        double max = radius + 0.75D;
        return Math.abs(location.getX() - hx) <= max && Math.abs(location.getZ() - hz) <= max;
    }

    private boolean pathCrossesWater(Location from, Location target, SoulGolemData data) {
        int hx = (int) Math.floor(data.homeX());
        int hz = (int) Math.floor(data.homeZ());
        return crossesColumn(from, target, hx + 0.5D, hz + 0.5D, 0.72D);
    }

    private Location stableWaterDetour(Location from, Location target, SoulGolemData data) {
        int prefer = waterSideSign(from, target, data);
        double wx = Math.floor(data.homeX()) + 0.5D;
        double wz = Math.floor(data.homeZ()) + 0.5D;
        double tx = target.getX() - from.getX();
        double tz = target.getZ() - from.getZ();
        double hy = target.getY();
        double r = 1.55D;

        double[][] offsets = {
                {r, 0}, {-r, 0}, {0, r}, {0, -r},
                {r, r}, {r, -r}, {-r, r}, {-r, -r}
        };

        Location bestPreferred = null;
        double bestPreferredScore = Double.MAX_VALUE;
        Location bestAny = null;
        double bestAnyScore = Double.MAX_VALUE;

        for (double[] o : offsets) {
            Location waypoint = new Location(from.getWorld(), wx + o[0], hy, wz + o[1]);
            snapFeet(waypoint, data);
            if (blocked(data, waypoint)) {
                continue;
            }
            double fromWp = Math.sqrt(horizontalDistanceSquared(from, waypoint));
            if (fromWp < 0.55D) {
                continue;
            }
            if (pathCrossesWater(from, waypoint, data)) {
                continue;
            }
            double wpTarget = Math.sqrt(horizontalDistanceSquared(waypoint, target));
            double score = fromWp + wpTarget;
            boolean clears = !pathCrossesWater(waypoint, target, data);
            if (clears) {
                score -= 8.0D;
            }

            double wpCross = tx * (waypoint.getZ() - from.getZ()) - tz * (waypoint.getX() - from.getX());
            boolean preferredSide = prefer >= 0 ? wpCross <= 0.0D : wpCross >= 0.0D;
            if (preferredSide) {
                if (score < bestPreferredScore) {
                    bestPreferredScore = score;
                    bestPreferred = waypoint;
                }
            }
            if (score < bestAnyScore) {
                bestAnyScore = score;
                bestAny = waypoint;
            }
        }

        Location chosen = bestPreferred != null ? bestPreferred : bestAny;
        if (chosen == null) {
            return cornerFallback(from, target, data, prefer);
        }
        return chosen;
    }

    private Location cornerFallback(Location from, Location target, SoulGolemData data, int prefer) {
        double wx = Math.floor(data.homeX()) + 0.5D;
        double wz = Math.floor(data.homeZ()) + 0.5D;
        double hy = target.getY();
        double r = 1.55D;
        Location a = new Location(from.getWorld(), wx + r, hy, wz + (prefer >= 0 ? r : -r));
        Location b = new Location(from.getWorld(), wx - r, hy, wz + (prefer >= 0 ? r : -r));
        snapFeet(a, data);
        snapFeet(b, data);
        boolean aOk = !blocked(data, a) && !pathCrossesWater(from, a, data);
        boolean bOk = !blocked(data, b) && !pathCrossesWater(from, b, data);
        if (aOk && bOk) {
            return horizontalDistanceSquared(a, target) <= horizontalDistanceSquared(b, target) ? a : b;
        }
        if (aOk) {
            return a;
        }
        if (bOk) {
            return b;
        }
        return null;
    }

    private boolean pathCrossesStation(Location from, Location target, SoulGolemData data) {
        if (crossesColumn(from, target, Math.floor(data.chestX()) + 0.5D, Math.floor(data.chestZ()) + 0.5D, 0.85D)) {
            return true;
        }
        if (data.hasCraftStation()
                && crossesColumn(from, target, Math.floor(data.craftX()) + 0.5D, Math.floor(data.craftZ()) + 0.5D, 0.85D)) {
            return true;
        }
        return data.hasCompostStation()
                && crossesColumn(
                from,
                target,
                Math.floor(data.compostX()) + 0.5D,
                Math.floor(data.compostZ()) + 0.5D,
                0.85D
        );
    }

    private Location sidestepAroundStation(Location from, Location target, SoulGolemData data) {
        double cx = Math.floor(data.chestX()) + 0.5D;
        double cz = Math.floor(data.chestZ()) + 0.5D;
        double bestDist = horizontalDistanceSquared(from, new Location(from.getWorld(), cx, from.getY(), cz));
        if (data.hasCraftStation()) {
            double fx = Math.floor(data.craftX()) + 0.5D;
            double fz = Math.floor(data.craftZ()) + 0.5D;
            double dist = horizontalDistanceSquared(from, new Location(from.getWorld(), fx, from.getY(), fz));
            if (dist < bestDist) {
                bestDist = dist;
                cx = fx;
                cz = fz;
            }
        }
        if (data.hasCompostStation()) {
            double px = Math.floor(data.compostX()) + 0.5D;
            double pz = Math.floor(data.compostZ()) + 0.5D;
            double dist = horizontalDistanceSquared(from, new Location(from.getWorld(), px, from.getY(), pz));
            if (dist < bestDist) {
                cx = px;
                cz = pz;
            }
        }
        Vector fromHazard = new Vector(from.getX() - cx, 0.0D, from.getZ() - cz);
        if (fromHazard.lengthSquared() < 0.0001D) {
            fromHazard = new Vector(target.getX() - cx, 0.0D, target.getZ() - cz);
        }
        if (fromHazard.lengthSquared() < 0.0001D) {
            fromHazard = new Vector(1.0D, 0.0D, 0.0D);
        }
        fromHazard.normalize();

        Location best = null;
        double bestScore = Double.MAX_VALUE;
        double[] distances = {1.15D, 1.55D, 2.05D};
        double[] angles = {90.0D, -90.0D, 45.0D, -45.0D, 135.0D, -135.0D, 180.0D};
        for (double dist : distances) {
            for (double degrees : angles) {
                double rad = Math.toRadians(degrees);
                double cos = Math.cos(rad);
                double sin = Math.sin(rad);
                Vector dir = new Vector(
                        fromHazard.getX() * cos - fromHazard.getZ() * sin,
                        0.0D,
                        fromHazard.getX() * sin + fromHazard.getZ() * cos
                ).multiply(dist);
                Location option = from.clone().add(dir);
                option.setY(target.getY());
                snapFeet(option, data);
                if (blocked(data, option) || this.chestService.collidesWithStation(data, option)) {
                    continue;
                }
                if (pathCrossesStation(from, option, data)) {
                    continue;
                }
                double score = horizontalDistanceSquared(option, target);
                if (!pathCrossesStation(option, target, data)) {
                    score -= 4.0D;
                }
                if (score < bestScore) {
                    bestScore = score;
                    best = option;
                }
            }
        }
        return best;
    }

    private Location stableStationDetour(Location from, Location target, SoulGolemData data) {
        double cx = Math.floor(data.chestX()) + 0.5D;
        double cz = Math.floor(data.chestZ()) + 0.5D;
        double hy = target.getY();
        double r = 1.75D;
        double[][] offsets = {
                {r, 0}, {-r, 0}, {0, r}, {0, -r},
                {r, r}, {r, -r}, {-r, r}, {-r, -r},
                {r * 1.4D, 0}, {-r * 1.4D, 0}, {0, r * 1.4D}, {0, -r * 1.4D}
        };
        Location best = null;
        double bestScore = Double.MAX_VALUE;
        for (double[] o : offsets) {
            Location waypoint = new Location(from.getWorld(), cx + o[0], hy, cz + o[1]);
            snapFeet(waypoint, data);
            if (blocked(data, waypoint) || this.chestService.collidesWithStation(data, waypoint)) {
                continue;
            }
            if (pathCrossesStation(from, waypoint, data)) {
                continue;
            }
            double score = Math.sqrt(horizontalDistanceSquared(from, waypoint))
                    + Math.sqrt(horizontalDistanceSquared(waypoint, target));
            if (!pathCrossesStation(waypoint, target, data)) {
                score -= 6.0D;
            }
            if (score < bestScore) {
                bestScore = score;
                best = waypoint;
            }
        }
        return best;
    }

    private static boolean crossesColumn(Location from, Location target, double cx, double cz, double radius) {
        double ax = from.getX();
        double az = from.getZ();
        double bx = target.getX();
        double bz = target.getZ();
        double dx = bx - ax;
        double dz = bz - az;
        double len2 = dx * dx + dz * dz;
        if (len2 < 1.0E-6D) {
            return false;
        }
        double t = ((cx - ax) * dx + (cz - az) * dz) / len2;
        if (t < 0.0D || t > 1.0D) {
            return false;
        }
        double px = ax + t * dx;
        double pz = az + t * dz;
        double dist2 = (px - cx) * (px - cx) + (pz - cz) * (pz - cz);
        return dist2 < radius * radius;
    }

    public static float yawTo(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    public static double horizontalDistanceSquared(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
