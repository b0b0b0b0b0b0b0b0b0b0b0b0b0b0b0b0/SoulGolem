package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GolemRegistry {

    private final Map<UUID, ActiveGolem> byId = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> entityToGolem = new ConcurrentHashMap<>();

    public void register(ActiveGolem golem) {
        this.byId.put(golem.data().id(), golem);
        UUID entityUuid = golem.data().entityUuid();
        if (entityUuid != null) {
            this.entityToGolem.put(entityUuid, golem.data().id());
        }
    }

    public void unregister(UUID golemId) {
        ActiveGolem removed = this.byId.remove(golemId);
        if (removed != null && removed.data().entityUuid() != null) {
            this.entityToGolem.remove(removed.data().entityUuid());
        }
    }

    public void bindEntity(UUID golemId, UUID entityUuid) {
        ActiveGolem golem = this.byId.get(golemId);
        if (golem == null) {
            return;
        }
        UUID old = golem.data().entityUuid();
        if (old != null) {
            this.entityToGolem.remove(old);
        }
        golem.data().entityUuid(entityUuid);
        this.entityToGolem.put(entityUuid, golemId);
        golem.markDirty();
    }

    public Optional<ActiveGolem> byId(UUID id) {
        return Optional.ofNullable(this.byId.get(id));
    }

    public Optional<ActiveGolem> byEntity(UUID entityUuid) {
        UUID golemId = this.entityToGolem.get(entityUuid);
        if (golemId == null) {
            return Optional.empty();
        }
        return byId(golemId);
    }

    public Collection<ActiveGolem> all() {
        return this.byId.values();
    }

    public int countByOwner(UUID ownerUuid) {
        int count = 0;
        for (ActiveGolem golem : this.byId.values()) {
            if (ownerUuid.equals(golem.data().ownerUuid())) {
                count++;
            }
        }
        return count;
    }

    public void clear() {
        this.byId.clear();
        this.entityToGolem.clear();
    }

    public ActiveGolem wrap(SoulGolemData data) {
        return new ActiveGolem(data);
    }
}
