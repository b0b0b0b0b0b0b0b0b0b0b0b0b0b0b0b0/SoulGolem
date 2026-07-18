package bm.b0b0b0.SoulGolem.model;

import java.util.UUID;

public final class SoulGolemData {

    private final UUID id;
    private UUID ownerUuid;
    private GolemType type;
    private String worldName;
    private double x;
    private double y;
    private double z;
    private double homeX;
    private double homeY;
    private double homeZ;
    private double yaw;
    private double pitch;
    private double chestX;
    private double chestY;
    private double chestZ;
    private double craftX = -1.0D;
    private double craftY = -1.0D;
    private double craftZ = -1.0D;
    private double seatX = -1.0D;
    private double seatY = -1.0D;
    private double seatZ = -1.0D;
    private double compostX = -1.0D;
    private double compostY = -1.0D;
    private double compostZ = -1.0D;
    private UUID entityUuid;
    private int level;
    private int energy;
    private boolean paused;
    private long blocksMined;
    private String upgradesJson;
    private long lastActionAt;
    private int digStartY = Integer.MIN_VALUE;
    private int digLayerY = Integer.MIN_VALUE;
    private int digStairIndex;
    private UUID crewLeaderId;

    public SoulGolemData(UUID id) {
        this.id = id;
        this.type = GolemType.MINER;
        this.level = 1;
        this.energy = 1000;
        this.paused = false;
        this.blocksMined = 0L;
        this.upgradesJson = "{}";
        this.lastActionAt = 0L;
        this.digStartY = Integer.MIN_VALUE;
        this.digLayerY = Integer.MIN_VALUE;
        this.digStairIndex = 0;
        this.crewLeaderId = null;
    }

    public UUID id() {
        return this.id;
    }

    public UUID ownerUuid() {
        return this.ownerUuid;
    }

    public void ownerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public GolemType type() {
        return this.type;
    }

    public void type(GolemType type) {
        this.type = type;
    }

    public String worldName() {
        return this.worldName;
    }

    public void worldName(String worldName) {
        this.worldName = worldName;
    }

    public double x() {
        return this.x;
    }

    public double y() {
        return this.y;
    }

    public double z() {
        return this.z;
    }

    public void position(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double homeX() {
        return this.homeX;
    }

    public double homeY() {
        return this.homeY;
    }

    public double homeZ() {
        return this.homeZ;
    }

    public void home(double x, double y, double z) {
        this.homeX = x;
        this.homeY = y;
        this.homeZ = z;
    }

    public double yaw() {
        return this.yaw;
    }

    public double pitch() {
        return this.pitch;
    }

    public void rotation(double yaw, double pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public double chestX() {
        return this.chestX;
    }

    public double chestY() {
        return this.chestY;
    }

    public double chestZ() {
        return this.chestZ;
    }

    public void chestPosition(double x, double y, double z) {
        this.chestX = x;
        this.chestY = y;
        this.chestZ = z;
    }

    public boolean hasCraftStation() {
        return this.craftX != -1.0D || this.craftY != -1.0D || this.craftZ != -1.0D;
    }

    public double craftX() {
        return this.craftX;
    }

    public double craftY() {
        return this.craftY;
    }

    public double craftZ() {
        return this.craftZ;
    }

    public void craftPosition(double x, double y, double z) {
        this.craftX = x;
        this.craftY = y;
        this.craftZ = z;
    }

    public void clearCraftPosition() {
        this.craftX = -1.0D;
        this.craftY = -1.0D;
        this.craftZ = -1.0D;
    }

    public boolean hasSeat() {
        return this.seatX != -1.0D || this.seatY != -1.0D || this.seatZ != -1.0D;
    }

    public double seatX() {
        return this.seatX;
    }

    public double seatY() {
        return this.seatY;
    }

    public double seatZ() {
        return this.seatZ;
    }

    public void seatPosition(double x, double y, double z) {
        this.seatX = x;
        this.seatY = y;
        this.seatZ = z;
    }

    public void clearSeatPosition() {
        this.seatX = -1.0D;
        this.seatY = -1.0D;
        this.seatZ = -1.0D;
    }

    public boolean hasCompostStation() {
        return this.compostX != -1.0D || this.compostY != -1.0D || this.compostZ != -1.0D;
    }

    public double compostX() {
        return this.compostX;
    }

    public double compostY() {
        return this.compostY;
    }

    public double compostZ() {
        return this.compostZ;
    }

    public void compostPosition(double x, double y, double z) {
        this.compostX = x;
        this.compostY = y;
        this.compostZ = z;
    }

    public void clearCompostPosition() {
        this.compostX = -1.0D;
        this.compostY = -1.0D;
        this.compostZ = -1.0D;
    }

    public UUID entityUuid() {
        return this.entityUuid;
    }

    public void entityUuid(UUID entityUuid) {
        this.entityUuid = entityUuid;
    }

    public int level() {
        return this.level;
    }

    public void level(int level) {
        this.level = level;
    }

    public int energy() {
        return this.energy;
    }

    public void energy(int energy) {
        this.energy = energy;
    }

    public boolean paused() {
        return this.paused;
    }

    public void paused(boolean paused) {
        this.paused = paused;
    }

    public long blocksMined() {
        return this.blocksMined;
    }

    public void blocksMined(long blocksMined) {
        this.blocksMined = blocksMined;
    }

    public void incrementBlocksMined() {
        this.blocksMined++;
    }

    public String upgradesJson() {
        return this.upgradesJson;
    }

    public void upgradesJson(String upgradesJson) {
        this.upgradesJson = upgradesJson;
    }

    public long lastActionAt() {
        return this.lastActionAt;
    }

    public void lastActionAt(long lastActionAt) {
        this.lastActionAt = lastActionAt;
    }

    public int digStartY() {
        return this.digStartY;
    }

    public void digStartY(int digStartY) {
        this.digStartY = digStartY;
    }

    public int digLayerY() {
        return this.digLayerY;
    }

    public void digLayerY(int digLayerY) {
        this.digLayerY = digLayerY;
    }

    public int digStairIndex() {
        return this.digStairIndex;
    }

    public void digStairIndex(int digStairIndex) {
        this.digStairIndex = digStairIndex;
    }

    public boolean hasDigProgress() {
        return this.digStartY != Integer.MIN_VALUE;
    }

    public UUID crewLeaderId() {
        return this.crewLeaderId;
    }

    public void crewLeaderId(UUID crewLeaderId) {
        this.crewLeaderId = crewLeaderId;
    }

    public boolean isCrewHelper() {
        return this.crewLeaderId != null;
    }
}
