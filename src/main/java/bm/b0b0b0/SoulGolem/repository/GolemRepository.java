package bm.b0b0b0.SoulGolem.repository;

import bm.b0b0b0.SoulGolem.model.SoulGolemData;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface GolemRepository {

    CompletableFuture<Void> save(SoulGolemData data);

    CompletableFuture<Void> delete(UUID id);

    CompletableFuture<Optional<SoulGolemData>> findById(UUID id);

    CompletableFuture<List<SoulGolemData>> findByOwner(UUID ownerUuid);

    CompletableFuture<List<SoulGolemData>> findAll();

    CompletableFuture<Integer> countByOwner(UUID ownerUuid);
}
