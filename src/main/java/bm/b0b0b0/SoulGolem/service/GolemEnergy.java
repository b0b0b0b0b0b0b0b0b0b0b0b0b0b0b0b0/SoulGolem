package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.config.settings.GolemSettings;
import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import java.util.function.Consumer;
import org.bukkit.Material;

public final class GolemEnergy {

    private GolemEnergy() {
    }

    public static void drain(ActiveGolem golem, int amount) {
        if (amount <= 0) {
            return;
        }
        golem.data().energy(Math.max(0, golem.data().energy() - amount));
    }

    public static boolean tryStartFeed(
            SoulChestService chest,
            GolemSettings settings,
            ActiveGolem golem,
            Consumer<ActiveGolem> moveToChest
    ) {
        if (golem.data().energy() > settings.energyHungryThreshold) {
            return false;
        }
        Material feed = settings.energyFeedMaterial();
        if (chest.countItem(golem.data(), feed) <= 0) {
            return false;
        }
        golem.clearFetchFlags();
        golem.fetchingFeed(true);
        moveToChest.accept(golem);
        return true;
    }

    public static void eatFeed(
            SoulChestService chest,
            GolemSettings settings,
            ActiveGolem golem,
            Consumer<ActiveGolem> afterEat
    ) {
        eatUntilFull(chest, settings, golem);
        afterEat.accept(golem);
    }

    public static int eatUntilFull(SoulChestService chest, GolemSettings settings, ActiveGolem golem) {
        Material feed = settings.energyFeedMaterial();
        int restoredPer = Math.max(1, settings.energyPerIngot);
        int capacity = Math.max(1, settings.energyCapacity);
        int eaten = 0;
        while (golem.data().energy() < capacity) {
            if (!chest.takeItem(golem.data(), feed, 1)) {
                break;
            }
            golem.data().energy(Math.min(capacity, golem.data().energy() + restoredPer));
            eaten++;
        }
        golem.fetchingFeed(false);
        if (eaten > 0) {
            golem.markDirty();
            golem.data().lastActionAt(System.currentTimeMillis());
        }
        return eaten;
    }
}
