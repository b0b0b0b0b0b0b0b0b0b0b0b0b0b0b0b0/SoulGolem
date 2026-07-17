package bm.b0b0b0.SoulGolem.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    private boolean fetchingFeed;
    private boolean fetchingFence;
    private boolean fetchingGate;
    private boolean fetchingPickaxe;
    private boolean fetchingWeapon;
    private UUID combatTarget;
    private Material combatWeapon;
    private long lastCombatAttackAt;
    private boolean pickaxeSwapBlocked;
    private Material upgradePickaxe;
    private int blocksLeftThisTrip;
    private Location wanderTarget;
    private Location pathWaypoint;
    private double pathGoalX = Double.NaN;
    private double pathGoalZ = Double.NaN;
    private long gateOpenSeenAt;
    private boolean dirty;
    private boolean pauseAfterRest;
    private boolean chestFullNotified;
    private boolean setupComplete;
    private SetupPhase setupPhase = SetupPhase.CLEAR;
    private final List<Location> setupQueue = new ArrayList<>();
    private int setupQueueIndex;
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

    public boolean fetchingFeed() {
        return this.fetchingFeed;
    }

    public void fetchingFeed(boolean fetchingFeed) {
        this.fetchingFeed = fetchingFeed;
    }

    public boolean fetchingFence() {
        return this.fetchingFence;
    }

    public void fetchingFence(boolean fetchingFence) {
        this.fetchingFence = fetchingFence;
        if (fetchingFence) {
            this.fetchingGate = false;
        }
    }

    public boolean fetchingGate() {
        return this.fetchingGate;
    }

    public void fetchingGate(boolean fetchingGate) {
        this.fetchingGate = fetchingGate;
        if (fetchingGate) {
            this.fetchingFence = false;
        }
    }

    public boolean fetchingPickaxe() {
        return this.fetchingPickaxe;
    }

    public void fetchingPickaxe(boolean fetchingPickaxe) {
        this.fetchingPickaxe = fetchingPickaxe;
    }

    public boolean fetchingWeapon() {
        return this.fetchingWeapon;
    }

    public void fetchingWeapon(boolean fetchingWeapon) {
        this.fetchingWeapon = fetchingWeapon;
    }

    public UUID combatTarget() {
        return this.combatTarget;
    }

    public void combatTarget(UUID combatTarget) {
        this.combatTarget = combatTarget;
    }

    public Material combatWeapon() {
        return this.combatWeapon;
    }

    public void combatWeapon(Material combatWeapon) {
        this.combatWeapon = combatWeapon;
    }

    public long lastCombatAttackAt() {
        return this.lastCombatAttackAt;
    }

    public void lastCombatAttackAt(long lastCombatAttackAt) {
        this.lastCombatAttackAt = lastCombatAttackAt;
    }

    public boolean pickaxeSwapBlocked() {
        return this.pickaxeSwapBlocked;
    }

    public void pickaxeSwapBlocked(boolean pickaxeSwapBlocked) {
        this.pickaxeSwapBlocked = pickaxeSwapBlocked;
    }

    public Material upgradePickaxe() {
        return this.upgradePickaxe;
    }

    public void upgradePickaxe(Material upgradePickaxe) {
        if (upgradePickaxe != null
                && upgradePickaxe != Material.IRON_PICKAXE
                && upgradePickaxe != Material.DIAMOND_PICKAXE
                && upgradePickaxe != Material.NETHERITE_PICKAXE) {
            this.upgradePickaxe = null;
            return;
        }
        this.upgradePickaxe = upgradePickaxe;
    }

    public int blocksLeftThisTrip() {
        return this.blocksLeftThisTrip;
    }

    public void blocksLeftThisTrip(int blocksLeftThisTrip) {
        this.blocksLeftThisTrip = Math.max(0, blocksLeftThisTrip);
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

    public long gateOpenSeenAt() {
        return this.gateOpenSeenAt;
    }

    public void gateOpenSeenAt(long gateOpenSeenAt) {
        this.gateOpenSeenAt = gateOpenSeenAt;
    }

    public void clearFetchFlags() {
        this.fetchingSeed = false;
        this.fetchingTorch = false;
        this.fetchingBoneMeal = false;
        this.fetchingSeat = false;
        this.fetchingFeed = false;
        this.fetchingFence = false;
        this.fetchingGate = false;
        this.fetchingPickaxe = false;
        this.fetchingWeapon = false;
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

    public boolean pauseAfterRest() {
        return this.pauseAfterRest;
    }

    public void pauseAfterRest(boolean pauseAfterRest) {
        this.pauseAfterRest = pauseAfterRest;
    }

    public boolean chestFullNotified() {
        return this.chestFullNotified;
    }

    public void chestFullNotified(boolean chestFullNotified) {
        this.chestFullNotified = chestFullNotified;
    }

    public boolean setupComplete() {
        return this.setupComplete;
    }

    public void setupComplete(boolean setupComplete) {
        this.setupComplete = setupComplete;
    }

    public SetupPhase setupPhase() {
        return this.setupPhase;
    }

    public void setupPhase(SetupPhase setupPhase) {
        this.setupPhase = setupPhase == null ? SetupPhase.CLEAR : setupPhase;
    }

    public List<Location> setupQueue() {
        return this.setupQueue;
    }

    public void clearSetupQueue() {
        this.setupQueue.clear();
        this.setupQueueIndex = 0;
    }

    public void setSetupQueue(List<Location> locations) {
        this.setupQueue.clear();
        if (locations != null) {
            this.setupQueue.addAll(locations);
        }
        this.setupQueueIndex = 0;
    }

    public int setupQueueIndex() {
        return this.setupQueueIndex;
    }

    public void setupQueueIndex(int setupQueueIndex) {
        this.setupQueueIndex = Math.max(0, setupQueueIndex);
    }

    public Location currentSetupTarget() {
        if (this.setupQueueIndex < 0 || this.setupQueueIndex >= this.setupQueue.size()) {
            return null;
        }
        return this.setupQueue.get(this.setupQueueIndex);
    }

    public void advanceSetupTarget() {
        this.setupQueueIndex++;
    }

    public boolean setupQueueDone() {
        return this.setupQueue.isEmpty() || this.setupQueueIndex >= this.setupQueue.size();
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
