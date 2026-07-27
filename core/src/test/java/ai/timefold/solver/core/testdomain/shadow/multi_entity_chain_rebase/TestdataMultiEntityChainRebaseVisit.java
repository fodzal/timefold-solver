package ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_rebase;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.timefold.solver.core.api.domain.variable.PreviousElementShadowVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowSources;
import ai.timefold.solver.core.api.domain.variable.ShadowVariable;
import ai.timefold.solver.core.testdomain.TestdataObject;

@PlanningEntity
public class TestdataMultiEntityChainRebaseVisit extends TestdataObject {

    @InverseRelationShadowVariable(sourceVariableName = "visits")
    TestdataMultiEntityChainRebaseVehicle vehicle;

    @PreviousElementShadowVariable(sourceVariableName = "visits")
    TestdataMultiEntityChainRebaseVisit previousVisit;

    int duration = 1;
    boolean rebase = false;

    @ShadowVariable(supplierName = "endServiceTimeSupplier")
    Integer endServiceTime;

    public TestdataMultiEntityChainRebaseVisit() {
    }

    public TestdataMultiEntityChainRebaseVisit(String code, int duration, boolean rebase) {
        super(code);
        this.duration = duration;
        this.rebase = rebase;
    }

    @ShadowSources({ "vehicle", "vehicle.previousEndTime", "previousVisit", "previousVisit.endServiceTime" })
    public Integer endServiceTimeSupplier() {
        if (vehicle == null) {
            return null;
        }
        Integer base;
        if (previousVisit == null) {
            // The head element does not read previousEndTime.
            base = vehicle.getDepartureTime();
        } else if (previousVisit.getEndServiceTime() == null || vehicle.getPreviousEndTime() == null) {
            return null;
        } else if (rebase) {
            base = Math.max(previousVisit.getEndServiceTime(), vehicle.getPreviousEndTime());
        } else {
            base = previousVisit.getEndServiceTime();
        }
        return base + duration;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public boolean isRebase() {
        return rebase;
    }

    public void setRebase(boolean rebase) {
        this.rebase = rebase;
    }

    public TestdataMultiEntityChainRebaseVehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(TestdataMultiEntityChainRebaseVehicle vehicle) {
        this.vehicle = vehicle;
    }

    public TestdataMultiEntityChainRebaseVisit getPreviousVisit() {
        return previousVisit;
    }

    public void setPreviousVisit(TestdataMultiEntityChainRebaseVisit previousVisit) {
        this.previousVisit = previousVisit;
    }

    public Integer getEndServiceTime() {
        return endServiceTime;
    }

    public void setEndServiceTime(Integer endServiceTime) {
        this.endServiceTime = endServiceTime;
    }
}
