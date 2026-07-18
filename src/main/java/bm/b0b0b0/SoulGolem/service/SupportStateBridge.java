package bm.b0b0b0.SoulGolem.service;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import org.bukkit.entity.CopperGolem;

public interface SupportStateBridge {

    void toChest(ActiveGolem golem);

    void toClear(ActiveGolem golem);

    void clearing(ActiveGolem golem);

    void toTorch(ActiveGolem golem);

    void placingTorch(ActiveGolem golem);

    void toFenceClear(ActiveGolem golem);

    void clearingFence(ActiveGolem golem);

    void toFence(ActiveGolem golem);

    void placingFence(ActiveGolem golem);

    void toGate(ActiveGolem golem);

    void placingGate(ActiveGolem golem);

    void toCloseGate(ActiveGolem golem);

    void toShelter(ActiveGolem golem);

    void buildingShelter(ActiveGolem golem);

    void sheltering(ActiveGolem golem);

    void toSeat(ActiveGolem golem);

    void sitting(ActiveGolem golem);

    void afterSupportIdle(ActiveGolem golem);

    void afterCloseGateIdle(ActiveGolem golem);

    void afterShelterDone(ActiveGolem golem, CopperGolem copper);

    boolean tryResumePausedSeat(ActiveGolem golem);
}
