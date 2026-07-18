package bm.b0b0b0.SoulGolem.service.miner;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.MinerState;
import bm.b0b0b0.SoulGolem.service.GolemAiMode;
import bm.b0b0b0.SoulGolem.service.SupportStateBridge;
import org.bukkit.entity.CopperGolem;

final class MinerSupportBridge implements SupportStateBridge {

    private final MinerContext ctx;

    MinerSupportBridge(MinerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void toChest(ActiveGolem golem) {
        golem.state(MinerState.MOVING_TO_CHEST);
    }

    @Override
    public void toClear(ActiveGolem golem) {
        golem.state(MinerState.MOVING_TO_CLEAR);
    }

    @Override
    public void clearing(ActiveGolem golem) {
        golem.state(MinerState.CLEARING);
    }

    @Override
    public void toTorch(ActiveGolem golem) {
        golem.state(MinerState.MOVING_TO_TORCH);
    }

    @Override
    public void placingTorch(ActiveGolem golem) {
        golem.state(MinerState.PLACING_TORCH);
    }

    @Override
    public void toFenceClear(ActiveGolem golem) {
        golem.state(MinerState.MOVING_TO_FENCE_CLEAR);
    }

    @Override
    public void clearingFence(ActiveGolem golem) {
        golem.state(MinerState.CLEARING_FENCE);
    }

    @Override
    public void toFence(ActiveGolem golem) {
        golem.state(MinerState.MOVING_TO_FENCE);
    }

    @Override
    public void placingFence(ActiveGolem golem) {
        golem.state(MinerState.PLACING_FENCE);
    }

    @Override
    public void toGate(ActiveGolem golem) {
        golem.state(MinerState.MOVING_TO_GATE);
    }

    @Override
    public void placingGate(ActiveGolem golem) {
        golem.state(MinerState.PLACING_GATE);
    }

    @Override
    public void toCloseGate(ActiveGolem golem) {
        golem.state(MinerState.MOVING_TO_CLOSE_GATE);
    }

    @Override
    public void toShelter(ActiveGolem golem) {
        golem.state(MinerState.MOVING_TO_SHELTER);
    }

    @Override
    public void buildingShelter(ActiveGolem golem) {
        golem.state(MinerState.BUILDING_SHELTER);
    }

    @Override
    public void sheltering(ActiveGolem golem) {
        golem.state(MinerState.SHELTERING);
    }

    @Override
    public void toSeat(ActiveGolem golem) {
        golem.state(MinerState.MOVING_TO_SEAT);
    }

    @Override
    public void sitting(ActiveGolem golem) {
        golem.state(MinerState.SITTING);
    }

    @Override
    public void afterSupportIdle(ActiveGolem golem) {
        golem.state(MinerState.IDLE);
    }

    @Override
    public void afterCloseGateIdle(ActiveGolem golem) {
        golem.state(MinerState.IDLE);
    }

    @Override
    public void afterShelterDone(ActiveGolem golem, CopperGolem copper) {
        GolemAiMode.enable(this.ctx.plugin(), copper, this.ctx.registry(), this.ctx.keys());
        golem.state(MinerState.IDLE);
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
        golem.state(MinerState.MOVING_TO_SEAT);
        return true;
    }
}
