package ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence_group;

import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.timefold.solver.core.api.domain.variable.PreviousElementShadowVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowSources;
import ai.timefold.solver.core.api.domain.variable.ShadowVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowVariablesInconsistent;
import ai.timefold.solver.core.testdomain.TestdataObject;

/**
 * A visit that may only start after all of its required predecessor visits end.
 */
@PlanningEntity
public class TestdataGroupPrecedenceVisit extends TestdataObject {

    @InverseRelationShadowVariable(sourceVariableName = "visits")
    TestdataGroupPrecedenceVehicle vehicle;

    @PreviousElementShadowVariable(sourceVariableName = "visits")
    TestdataGroupPrecedenceVisit previousVisit;

    List<TestdataGroupPrecedenceVisit> requiredPredecessorList = new ArrayList<>();
    int duration = 1;

    @ShadowVariable(supplierName = "endServiceTimeSupplier")
    Integer endServiceTime;

    @ShadowVariablesInconsistent
    boolean inconsistent;

    public TestdataGroupPrecedenceVisit() {
    }

    public TestdataGroupPrecedenceVisit(String code, int duration) {
        super(code);
        this.duration = duration;
    }

    @ShadowSources({ "vehicle", "previousVisit", "previousVisit.endServiceTime",
            "requiredPredecessorList[].endServiceTime" })
    public Integer endServiceTimeSupplier() {
        if (vehicle == null) {
            return null;
        }
        var base = previousVisit != null ? previousVisit.getEndServiceTime() : (Integer) vehicle.getDepartureTime();
        if (base == null) {
            return null;
        }
        for (var requiredPredecessor : requiredPredecessorList) {
            var predecessorEndServiceTime = requiredPredecessor.getEndServiceTime();
            if (predecessorEndServiceTime == null) {
                return null;
            }
            base = Math.max(base, predecessorEndServiceTime);
        }
        return base + duration;
    }

    public List<TestdataGroupPrecedenceVisit> getRequiredPredecessorList() {
        return requiredPredecessorList;
    }

    public void setRequiredPredecessorList(List<TestdataGroupPrecedenceVisit> requiredPredecessorList) {
        this.requiredPredecessorList = requiredPredecessorList;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public TestdataGroupPrecedenceVehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(TestdataGroupPrecedenceVehicle vehicle) {
        this.vehicle = vehicle;
    }

    public TestdataGroupPrecedenceVisit getPreviousVisit() {
        return previousVisit;
    }

    public void setPreviousVisit(TestdataGroupPrecedenceVisit previousVisit) {
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
