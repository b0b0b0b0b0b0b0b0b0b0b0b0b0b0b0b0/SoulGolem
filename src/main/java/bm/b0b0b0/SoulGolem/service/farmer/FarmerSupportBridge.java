package bm.b0b0b0.SoulGolem.service.farmer;

import bm.b0b0b0.SoulGolem.model.ActiveGolem;
import bm.b0b0b0.SoulGolem.model.FarmerState;
import bm.b0b0b0.SoulGolem.service.GolemAiMode;
import bm.b0b0b0.SoulGolem.service.SupportStateBridge;
import org.bukkit.entity.CopperGolem;

final class FarmerSupportBridge implements SupportStateBridge {

    private final FarmerContext ctx;
    private FarmerCycle cycle;

    FarmerSupportBridge(FarmerContext ctx) {
        this.ctx = ctx;
    }

    void wire(FarmerCycle cycle) {
        this.cycle = cycle;
    }

    @Override
    public void toChest(ActiveGolem golem) {
        golem.farmerState(FarmerState.MOVING_TO_CHEST);
    }

    @Override
    public void toClear(ActiveGolem golem) {
        golem.farmerState(FarmerState.MOVING_TO_CLEAR);
    }

    @Override
    public void clearing(ActiveGolem golem) {
        golem.farmerState(FarmerState.CLEARING);
    }

    @Override
    public void toTorch(ActiveGolem golem) {
        golem.farmerState(FarmerState.MOVING_TO_TORCH);
    }

    @Override
    public void placingTorch(ActiveGolem golem) {
        golem.farmerState(FarmerState.PLACING_TORCH);
    }

    @Override
    public void toFenceClear(ActiveGolem golem) {
        golem.farmerState(FarmerState.MOVING_TO_FENCE_CLEAR);
    }

    @Override
    public void clearingFence(ActiveGolem golem) {
        golem.farmerState(FarmerState.CLEARING_FENCE);
    }

    @Override
    public void toFence(ActiveGolem golem) {
        golem.farmerState(FarmerState.MOVING_TO_FENCE);
    }

    @Override
    public void placingFence(ActiveGolem golem) {
        golem.farmerState(FarmerState.PLACING_FENCE);
    }

    @Override
    public void toGate(ActiveGolem golem) {
        golem.farmerState(FarmerState.MOVING_TO_GATE);
    }

    @Override
    public void placingGate(ActiveGolem golem) {
        golem.farmerState(FarmerState.PLACING_GATE);
    }

    @Override
    public void toCloseGate(ActiveGolem golem) {
        golem.farmerState(FarmerState.MOVING_TO_CLOSE_GATE);
    }

    @Override
    public void toShelter(ActiveGolem golem) {
        golem.farmerState(FarmerState.MOVING_TO_SHELTER);
    }

    @Override
    public void buildingShelter(ActiveGolem golem) {
        golem.farmerState(FarmerState.BUILDING_SHELTER);
    }

    @Override
    public void sheltering(ActiveGolem golem) {
        golem.farmerState(FarmerState.SHELTERING);
    }

    @Override
    public void toSeat(ActiveGolem golem) {
        golem.farmerState(FarmerState.MOVING_TO_SEAT);
    }

    @Override
    public void sitting(ActiveGolem golem) {
        golem.farmerState(FarmerState.SITTING);
    }

    @Override
    public void afterSupportIdle(ActiveGolem golem) {
        golem.wanderTarget(null);
        if (this.cycle != null) {
            this.cycle.resumeAfterClear(golem);
        } else {
            golem.farmerState(FarmerState.WAIT_GROWTH);
        }
    }

    @Override
    public void afterCloseGateIdle(ActiveGolem golem) {
        golem.farmerState(FarmerState.WAITING_SEEDS);
    }

    @Override
    public void afterShelterDone(ActiveGolem golem, CopperGolem copper) {
        golem.wanderTarget(null);
        GolemAiMode.enable(this.ctx.plugin(), copper, this.ctx.registry(), this.ctx.keys());
        afterSupportIdle(golem);
    }

    @Override
    public boolean tryResumePausedSeat(ActiveGolem golem) {
        if (!golem.resumeSeatRest()) {
            return false;
        }
        if (!this.ctx.farmAreaService().hasValidSeat(golem.data())) {
            golem.resumeSeatRest(false);
            return false;
        }
        golem.clearFetchFlags();
        golem.wanderTarget(null);
        golem.targetCrop(null);
        golem.farmerState(FarmerState.MOVING_TO_SEAT);
        return true;
    }
}
