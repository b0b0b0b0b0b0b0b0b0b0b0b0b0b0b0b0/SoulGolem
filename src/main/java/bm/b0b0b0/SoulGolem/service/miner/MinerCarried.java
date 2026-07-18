package bm.b0b0b0.SoulGolem.service.miner;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.service.GolemCarried;
import org.bukkit.Material;

public final class MinerCarried {

    private final MinerContext ctx;

    public MinerCarried(MinerContext ctx) {
        this.ctx = ctx;
    }

    public static int countCarried(ActiveGolem golem, Material material) {
        return GolemCarried.count(golem, material);
    }

    public static void consumeCarried(ActiveGolem golem, Material material, int amount) {
        GolemCarried.consume(golem, material, amount);
    }

    public void returnCarriedToChest(ActiveGolem golem, Material material) {
        GolemCarried.returnToChest(this.ctx.chestService(), golem, material);
    }

    public static Material carriedStairs(ActiveGolem golem) {
        return GolemCarried.carriedStairs(golem);
    }
}
