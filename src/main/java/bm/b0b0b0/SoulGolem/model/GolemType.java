package bm.b0b0b0.SoulGolem.model;

public enum GolemType {
    MINER,
    FARMER,
    DIGGER;

    public static GolemType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return MINER;
        }
        try {
            return GolemType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return MINER;
        }
    }
}
