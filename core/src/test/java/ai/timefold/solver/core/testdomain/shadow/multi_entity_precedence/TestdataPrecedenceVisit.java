package ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.timefold.solver.core.api.domain.variable.PreviousElementShadowVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowSources;
import ai.timefold.solver.core.api.domain.variable.ShadowVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowVariablesInconsistent;
import ai.timefold.solver.core.testdomain.TestdataObject;

/**
 * A visit that may only start after another visit (its required predecessor) ends,
 * wherever that predecessor is assigned.
 */
@PlanningEntity
public class TestdataPrecedenceVisit extends TestdataObject {

    @InverseRelationShadowVariable(sourceVariableName = "visits")
    TestdataPrecedenceVehicle vehicle;

    @PreviousElementShadowVariable(sourceVariableName = "visits")
    TestdataPrecedenceVisit previousVisit;

    TestdataPrecedenceVisit requiredPredecessor;
    int duration = 1;

    @ShadowVariable(supplierName = "endServiceTimeSupplier")
    Integer endServiceTime;

    @ShadowVariablesInconsistent
    boolean inconsistent;

    public TestdataPrecedenceVisit() {
    }

    public TestdataPrecedenceVisit(String code, int duration) {
        super(code);
        this.duration = duration;
    }

    @ShadowSources({ "vehicle", "vehicle.previousEndTime", "previousVisit", "previousVisit.endServiceTime",
            "requiredPredecessor.endServiceTime" })
    public Integer endServiceTimeSupplier() {
        if (vehicle == null) {
            return null;
        }
        var base = previousVisit != null ? previousVisit.getEndServiceTime() : vehicle.getPreviousEndTime();
        if (base == null) {
            return null;
        }
        if (requiredPredecessor != null) {
            var predecessorEndServiceTime = requiredPredecessor.getEndServiceTime();
            if (predecessorEndServiceTime == null) {
                return null;
            }
            base = Math.max(base, predecessorEndServiceTime);
        }
        return base + duration;
    }

    public TestdataPrecedenceVisit getRequiredPredecessor() {
        return requiredPredecessor;
    }

    public void setRequiredPredecessor(TestdataPrecedenceVisit requiredPredecessor) {
        this.requiredPredecessor = requiredPredecessor;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public TestdataPrecedenceVehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(TestdataPrecedenceVehicle vehicle) {
        this.vehicle = vehicle;
    }

    public TestdataPrecedenceVisit getPreviousVisit() {
        return previousVisit;
    }

    public void setPreviousVisit(TestdataPrecedenceVisit previousVisit) {
        this.previousVisit = previousVisit;
    }

    public Integer getEndServiceTime() {
        return endServiceTime;
    }

    public void setEndServiceTime(Integer endServiceTime) {
        this.endServiceTime = endServiceTime;
    }

    public boolean isInconsistent() {
        return inconsistent;
    }

    public void setInconsistent(boolean inconsistent) {
        this.inconsistent = inconsistent;
    }
}
