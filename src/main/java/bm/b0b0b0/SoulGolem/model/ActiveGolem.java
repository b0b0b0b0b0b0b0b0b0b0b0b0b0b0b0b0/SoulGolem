package bm.b0b0b0.SoulGolem.model;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class ActiveGolem {

    private final SoulGolemData data;
    private MinerState state = MinerState.IDLE;
    private FarmerState farmerState = FarmerState.WAITING_SEEDS;
    private Location targetOre;
    private Location targetCrop;
    private Material oreMaterial;
    private long mineTicksLeft;
    private long restTicksLeft;
    private boolean fieldReady;
    private boolean fetchingSeed;
    private boolean fetchingTorch;
    private boolean fetchingBoneMeal;
    private boolean fetchingSeat;
    private Location wanderTarget;
    private Location pathWaypoint;
    private double pathGoalX = Double.NaN;
    private double pathGoalZ = Double.NaN;
    private boolean dirty;
    private boolean chestFullNotified;
    private String lastStatusKey = "";
    private final List<ItemStack> carried = new ArrayList<>();

    public ActiveGolem(SoulGolemData data) {
        this.data = data;
    }

    public SoulGolemData data() {
        return this.data;
    }

    public MinerState state() {
        return this.state;
    }

    public void state(MinerState state) {
        this.state = state;
    }

    public FarmerState farmerState() {
        return this.farmerState;
    }

    public void farmerState(FarmerState farmerState) {
        this.farmerState = farmerState;
    }

    public Location targetOre() {
        return this.targetOre;
    }

    public void targetOre(Location targetOre) {
        this.targetOre = targetOre;
    }

    public Location targetCrop() {
        return this.targetCrop;
    }

    public void targetCrop(Location targetCrop) {
        this.targetCrop = targetCrop;
    }

    public Material oreMaterial() {
        return this.oreMaterial;
    }

    public void oreMaterial(Material oreMaterial) {
        this.oreMaterial = oreMaterial;
    }

    public long mineTicksLeft() {
        return this.mineTicksLeft;
    }

    public void mineTicksLeft(long mineTicksLeft) {
        this.mineTicksLeft = mineTicksLeft;
    }

    public long restTicksLeft() {
        return this.restTicksLeft;
    }

    public void restTicksLeft(long restTicksLeft) {
        this.restTicksLeft = restTicksLeft;
    }

    public boolean fieldReady() {
        return this.fieldReady;
    }

    public void fieldReady(boolean fieldReady) {
        this.fieldReady = fieldReady;
    }

    public boolean fetchingSeed() {
        return this.fetchingSeed;
    }

    public void fetchingSeed(boolean fetchingSeed) {
        this.fetchingSeed = fetchingSeed;
    }

    public boolean fetchingTorch() {
        return this.fetchingTorch;
    }

    public void fetchingTorch(boolean fetchingTorch) {
        this.fetchingTorch = fetchingTorch;
    }

    public boolean fetchingBoneMeal() {
        return this.fetchingBoneMeal;
    }

    public void fetchingBoneMeal(boolean fetchingBoneMeal) {
        this.fetchingBoneMeal = fetchingBoneMeal;
    }

    public boolean fetchingSeat() {
        return this.fetchingSeat;
    }

    public void fetchingSeat(boolean fetchingSeat) {
        this.fetchingSeat = fetchingSeat;
    }

    public Location wanderTarget() {
        return this.wanderTarget;
    }

    public void wanderTarget(Location wanderTarget) {
        this.wanderTarget = wanderTarget;
    }

    public Location pathWaypoint() {
        return this.pathWaypoint;
    }

    public void pathWaypoint(Location pathWaypoint) {
        this.pathWaypoint = pathWaypoint;
    }

    public void pathGoal(Location goal) {
        if (goal == null) {
            this.pathGoalX = Double.NaN;
            this.pathGoalZ = Double.NaN;
            return;
        }
        this.pathGoalX = goal.getX();
        this.pathGoalZ = goal.getZ();
    }

    public boolean pathGoalMatches(Location goal) {
        if (goal == null || Double.isNaN(this.pathGoalX) || Double.isNaN(this.pathGoalZ)) {
            return false;
        }
        double dx = goal.getX() - this.pathGoalX;
        double dz = goal.getZ() - this.pathGoalZ;
        return dx * dx + dz * dz < 1.0D;
    }

    public void clearPathWaypoint() {
        this.pathWaypoint = null;
        this.pathGoalX = Double.NaN;
        this.pathGoalZ = Double.NaN;
    }

    public void clearFetchFlags() {
        this.fetchingSeed = false;
        this.fetchingTorch = false;
        this.fetchingBoneMeal = false;
        this.fetchingSeat = false;
    }

    public String lastStatusKey() {
        return this.lastStatusKey;
    }

    public void lastStatusKey(String lastStatusKey) {
        this.lastStatusKey = lastStatusKey == null ? "" : lastStatusKey;
    }

    public boolean dirty() {
        return this.dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public boolean chestFullNotified() {
        return this.chestFullNotified;
    }

    public void chestFullNotified(boolean chestFullNotified) {
        this.chestFullNotified = chestFullNotified;
    }

    public List<ItemStack> carried() {
        return this.carried;
    }

    public void clearCarried() {
        this.carried.clear();
    }

    public void carry(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        this.carried.add(stack.clone());
    }
}
