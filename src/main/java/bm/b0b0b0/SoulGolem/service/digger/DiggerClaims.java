package bm.b0b0b0.SoulGolem.service.digger;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.block.Block;

final class DiggerClaims {

    private static final long TTL_MS = 2_500L;
    private static final Map<Long, Claim> CLAIMS = new ConcurrentHashMap<>();

    private DiggerClaims() {
    }

    static UUID owner(UUID pitId, Block block) {
        Claim existing = getFresh(pitId, block);
        return existing == null ? null : existing.diggerId;
    }

    static boolean isClaimedByOther(UUID pitId, Block block, UUID diggerId) {
        Claim existing = getFresh(pitId, block);
        return existing != null && !existing.diggerId.equals(diggerId);
    }

    static UUID claim(UUID pitId, Block block, UUID diggerId) {
        if (pitId == null || block == null || diggerId == null) {
            return null;
        }
        long key = pack(pitId, block.getX(), block.getY(), block.getZ());
        long now = System.currentTimeMillis();
        Claim previous = CLAIMS.put(key, new Claim(diggerId, now));
        purge(now);
        if (previous == null || previous.diggerId.equals(diggerId) || now - previous.at >= TTL_MS) {
            return null;
        }
        return previous.diggerId;
    }

    static void renew(UUID pitId, Block block, UUID diggerId) {
        if (pitId == null || block == null || diggerId == null) {
            return;
        }
        long key = pack(pitId, block.getX(), block.getY(), block.getZ());
        Claim existing = CLAIMS.get(key);
        if (existing != null && existing.diggerId.equals(diggerId)) {
            CLAIMS.put(key, new Claim(diggerId, System.currentTimeMillis()));
        }
    }

    static void release(UUID pitId, Block block, UUID diggerId) {
        if (pitId == null || block == null || diggerId == null) {
            return;
        }
        long key = pack(pitId, block.getX(), block.getY(), block.getZ());
        Claim existing = CLAIMS.get(key);
        if (existing != null && existing.diggerId.equals(diggerId)) {
            CLAIMS.remove(key);
        }
    }

    private static Claim getFresh(UUID pitId, Block block) {
        if (pitId == null || block == null) {
            return null;
        }
        long key = pack(pitId, block.getX(), block.getY(), block.getZ());
        Claim existing = CLAIMS.get(key);
        if (existing == null) {
            return null;
        }
        if (System.currentTimeMillis() - existing.at >= TTL_MS) {
            CLAIMS.remove(key, existing);
            return null;
        }
        return existing;
    }

    private static void purge(long now) {
        if (CLAIMS.size() < 64) {
            return;
        }
        Iterator<Map.Entry<Long, Claim>> it = CLAIMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Claim> e = it.next();
            if (now - e.getValue().at >= TTL_MS) {
                it.remove();
            }
        }
    }

    private static long pack(UUID pitId, int x, int y, int z) {
        long h = pitId.getMostSignificantBits() ^ pitId.getLeastSignificantBits();
        return h * 31L
                + (((long) x & 0x3FFFFFFL) << 36)
                + (((long) (y + 512) & 0x3FFL) << 26)
                + ((long) z & 0x3FFFFFFL);
    }

    private record Claim(UUID diggerId, long at) {
    }
}
