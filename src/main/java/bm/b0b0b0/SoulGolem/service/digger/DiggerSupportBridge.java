package bm.b0b0b0.SoulGolem.service.digger;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.DiggerState;
import bm.b0b0b0.SoulGolem.service.GolemAiMode;
import bm.b0b0b0.SoulGolem.service.SupportStateBridge;
import org.bukkit.entity.CopperGolem;

final class DiggerSupportBridge implements SupportStateBridge {

    private final DiggerContext ctx;

    DiggerSupportBridge(DiggerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void toChest(ActiveGolem golem) {
        golem.diggerState(DiggerState.MOVING_TO_CHEST);
    }

    @Override
    public void toClear(ActiveGolem golem) {
        golem.diggerState(DiggerState.MOVING_TO_CLEAR);
    }

    @Override
    public void clearing(ActiveGolem golem) {
        golem.diggerState(DiggerState.CLEARING);
    }

    @Override
    public void toTorch(ActiveGolem golem) {
        golem.diggerState(DiggerState.MOVING_TO_TORCH);
    }

    @Override
    public void placingTorch(ActiveGolem golem) {
        golem.diggerState(DiggerState.PLACING_TORCH);
    }

    @Override
    public void toFenceClear(ActiveGolem golem) {
        golem.diggerState(DiggerState.MOVING_TO_FENCE_CLEAR);
    }

    @Override
    public void clearingFence(ActiveGolem golem) {
        golem.diggerState(DiggerState.CLEARING_FENCE);
    }

    @Override
    public void toFence(ActiveGolem golem) {
        golem.diggerState(DiggerState.MOVING_TO_FENCE);
    }

    @Override
    public void placingFence(ActiveGolem golem) {
        golem.diggerState(DiggerState.PLACING_FENCE);
    }

    @Override
    public void toGate(ActiveGolem golem) {
        golem.diggerState(DiggerState.MOVING_TO_GATE);
    }

    @Override
    public void placingGate(ActiveGolem golem) {
        golem.diggerState(DiggerState.PLACING_GATE);
    }

    @Override
    public void toCloseGate(ActiveGolem golem) {
        golem.diggerState(DiggerState.MOVING_TO_CLOSE_GATE);
    }

    @Override
    public void toShelter(ActiveGolem golem) {
        golem.diggerState(DiggerState.MOVING_TO_SHELTER);
    }

    @Override
    public void buildingShelter(ActiveGolem golem) {
        golem.diggerState(DiggerState.BUILDING_SHELTER);
    }

    @Override
    public void sheltering(ActiveGolem golem) {
        golem.diggerState(DiggerState.SHELTERING);
    }

    @Override
    public void toSeat(ActiveGolem golem) {
        golem.diggerState(DiggerState.MOVING_TO_SEAT);
    }

    @Override
    public void sitting(ActiveGolem golem) {
        golem.diggerState(DiggerState.SITTING);
    }

    @Override
    public void afterSupportIdle(ActiveGolem golem) {
        golem.diggerState(DiggerState.IDLE);
    }

    @Override
    public void afterCloseGateIdle(ActiveGolem golem) {
        golem.diggerState(DiggerState.IDLE);
    }

    @Override
    public void afterShelterDone(ActiveGolem golem, CopperGolem copper) {
        GolemAiMode.enable(this.ctx.plugin(), copper, this.ctx.registry(), this.ctx.keys());
        golem.diggerState(DiggerState.IDLE);
    }

    @Override
    public boolean tryResumePausedSeat(ActiveGolem golem) {
        if (!golem.resumeSeatRest()) {
            return false;
        }
        if (golem.restTicksLeft() <= 0L || !this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            golem.resumeSeatRest(false);
            return false;
        }
        golem.clearFetchFlags();
        golem.targetCrop(null);
        golem.diggerState(DiggerState.MOVING_TO_SEAT);
        return true;
    }
}
