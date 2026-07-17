package bm.b0b0b0.SoulGolem.repository;

import bm.b0b0b0.SoulGolem.database.DatabaseManager;
import bm.b0b0b0.SoulGolem.model.GolemType;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SqlGolemRepository implements GolemRepository {

    private static final String UPSERT = """
            INSERT INTO soul_golems (
                id, owner_uuid, type, world, x, y, z, home_x, home_y, home_z, yaw, pitch,
                chest_x, chest_y, chest_z, craft_x, craft_y, craft_z, seat_x, seat_y, seat_z,
                entity_uuid, level, energy, paused, blocks_mined, upgrades_json, last_action_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                owner_uuid = excluded.owner_uuid,
                type = excluded.type,
                world = excluded.world,
                x = excluded.x,
                y = excluded.y,
                z = excluded.z,
                home_x = excluded.home_x,
                home_y = excluded.home_y,
                home_z = excluded.home_z,
                yaw = excluded.yaw,
                pitch = excluded.pitch,
                chest_x = excluded.chest_x,
                chest_y = excluded.chest_y,
                chest_z = excluded.chest_z,
                craft_x = excluded.craft_x,
                craft_y = excluded.craft_y,
                craft_z = excluded.craft_z,
                seat_x = excluded.seat_x,
                seat_y = excluded.seat_y,
                seat_z = excluded.seat_z,
                entity_uuid = excluded.entity_uuid,
                level = excluded.level,
                energy = excluded.energy,
                paused = excluded.paused,
                blocks_mined = excluded.blocks_mined,
                upgrades_json = excluded.upgrades_json,
                last_action_at = excluded.last_action_at
            """;

    private static final String UPSERT_MYSQL = """
            INSERT INTO soul_golems (
                id, owner_uuid, type, world, x, y, z, home_x, home_y, home_z, yaw, pitch,
                chest_x, chest_y, chest_z, craft_x, craft_y, craft_z, seat_x, seat_y, seat_z,
                entity_uuid, level, energy, paused, blocks_mined, upgrades_json, last_action_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                owner_uuid = VALUES(owner_uuid),
                type = VALUES(type),
                world = VALUES(world),
                x = VALUES(x),
                y = VALUES(y),
                z = VALUES(z),
                home_x = VALUES(home_x),
                home_y = VALUES(home_y),
                home_z = VALUES(home_z),
                yaw = VALUES(yaw),
                pitch = VALUES(pitch),
                chest_x = VALUES(chest_x),
                chest_y = VALUES(chest_y),
                chest_z = VALUES(chest_z),
                craft_x = VALUES(craft_x),
                craft_y = VALUES(craft_y),
                craft_z = VALUES(craft_z),
                seat_x = VALUES(seat_x),
                seat_y = VALUES(seat_y),
                seat_z = VALUES(seat_z),
                entity_uuid = VALUES(entity_uuid),
                level = VALUES(level),
                energy = VALUES(energy),
                paused = VALUES(paused),
                blocks_mined = VALUES(blocks_mined),
                upgrades_json = VALUES(upgrades_json),
                last_action_at = VALUES(last_action_at)
            """;

    private final DatabaseManager databaseManager;
    private final boolean mysql;

    public SqlGolemRepository(DatabaseManager databaseManager, boolean mysql) {
        this.databaseManager = databaseManager;
        this.mysql = mysql;
    }

    @Override
    public CompletableFuture<Void> save(SoulGolemData data) {
        return this.databaseManager.runAsync(() -> {
            String sql = this.mysql ? UPSERT_MYSQL : UPSERT;
            try (Connection connection = this.databaseManager.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, data);
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to save golem " + data.id(), exception);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<SoulGolemData>> findById(UUID id) {
        return this.databaseManager.supplyAsync(() -> {
            try (Connection connection = this.databaseManager.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM soul_golems WHERE id = ?")) {
                statement.setString(1, id.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return Optional.of(map(resultSet));
                    }
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to load golem " + id, exception);
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<List<SoulGolemData>> findAll() {
        return this.databaseManager.supplyAsync(() -> {
            List<SoulGolemData> list = new ArrayList<>();
            try (Connection connection = this.databaseManager.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM soul_golems");
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    list.add(map(resultSet));
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to load golems", exception);
            }
            return list;
        });
    }

    @Override
    public CompletableFuture<List<SoulGolemData>> findByOwner(UUID ownerUuid) {
        return this.databaseManager.supplyAsync(() -> {
            List<SoulGolemData> list = new ArrayList<>();
            try (Connection connection = this.databaseManager.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT * FROM soul_golems WHERE owner_uuid = ?")) {
                statement.setString(1, ownerUuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        list.add(map(resultSet));
                    }
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to load golems for owner", exception);
            }
            return list;
        });
    }

    @Override
    public CompletableFuture<Void> delete(UUID id) {
        return this.databaseManager.runAsync(() -> {
            try (Connection connection = this.databaseManager.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM soul_golems WHERE id = ?")) {
                statement.setString(1, id.toString());
                statement.executeUpdate();
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to delete golem " + id, exception);
            }
        });
    }

    @Override
    public CompletableFuture<Integer> countByOwner(UUID ownerUuid) {
        return this.databaseManager.supplyAsync(() -> {
            try (Connection connection = this.databaseManager.dataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT COUNT(*) FROM soul_golems WHERE owner_uuid = ?")) {
                statement.setString(1, ownerUuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getInt(1);
                    }
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("Failed to count golems", exception);
            }
            return 0;
        });
    }

    private static void bind(PreparedStatement statement, SoulGolemData data) throws SQLException {
        statement.setString(1, data.id().toString());
        statement.setString(2, data.ownerUuid().toString());
        statement.setString(3, data.type().name());
        statement.setString(4, data.worldName());
        statement.setDouble(5, data.x());
        statement.setDouble(6, data.y());
        statement.setDouble(7, data.z());
        statement.setDouble(8, data.homeX());
        statement.setDouble(9, data.homeY());
        statement.setDouble(10, data.homeZ());
        statement.setDouble(11, data.yaw());
        statement.setDouble(12, data.pitch());
        statement.setDouble(13, data.chestX());
        statement.setDouble(14, data.chestY());
        statement.setDouble(15, data.chestZ());
        statement.setDouble(16, data.craftX());
        statement.setDouble(17, data.craftY());
        statement.setDouble(18, data.craftZ());
        statement.setDouble(19, data.seatX());
        statement.setDouble(20, data.seatY());
        statement.setDouble(21, data.seatZ());
        statement.setString(22, data.entityUuid() == null ? null : data.entityUuid().toString());
        statement.setInt(23, data.level());
        statement.setInt(24, data.energy());
        statement.setInt(25, data.paused() ? 1 : 0);
        statement.setLong(26, data.blocksMined());
        statement.setString(27, data.upgradesJson());
        statement.setLong(28, data.lastActionAt());
    }

    private static SoulGolemData map(ResultSet resultSet) throws SQLException {
        SoulGolemData data = new SoulGolemData(UUID.fromString(resultSet.getString("id")));
        data.ownerUuid(UUID.fromString(resultSet.getString("owner_uuid")));
        data.type(GolemType.fromString(resultSet.getString("type")));
        data.worldName(resultSet.getString("world"));
        data.position(resultSet.getDouble("x"), resultSet.getDouble("y"), resultSet.getDouble("z"));
        double homeX = resultSet.getDouble("home_x");
        double homeY = resultSet.getDouble("home_y");
        double homeZ = resultSet.getDouble("home_z");
        if (homeX == 0.0D && homeY == 0.0D && homeZ == 0.0D) {
            data.home(data.x(), data.y(), data.z());
        } else {
            data.home(homeX, homeY, homeZ);
        }
        data.rotation(resultSet.getDouble("yaw"), resultSet.getDouble("pitch"));
        data.chestPosition(resultSet.getDouble("chest_x"), resultSet.getDouble("chest_y"), resultSet.getDouble("chest_z"));
        try {
            data.craftPosition(resultSet.getDouble("craft_x"), resultSet.getDouble("craft_y"), resultSet.getDouble("craft_z"));
        } catch (SQLException ignored) {
            data.clearCraftPosition();
        }
        try {
            data.seatPosition(resultSet.getDouble("seat_x"), resultSet.getDouble("seat_y"), resultSet.getDouble("seat_z"));
        } catch (SQLException ignored) {
            data.clearSeatPosition();
        }
        String entity = resultSet.getString("entity_uuid");
        if (entity != null && !entity.isEmpty()) {
            data.entityUuid(UUID.fromString(entity));
        }
        data.level(resultSet.getInt("level"));
        data.energy(resultSet.getInt("energy"));
        data.paused(resultSet.getInt("paused") != 0);
        data.blocksMined(resultSet.getLong("blocks_mined"));
        data.upgradesJson(resultSet.getString("upgrades_json"));
        data.lastActionAt(resultSet.getLong("last_action_at"));
        return data;
    }
}
