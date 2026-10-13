package ai.timefold.solver.core.testdomain.shadow.no_inconsistent_field_owner_loop;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.timefold.solver.core.api.domain.variable.PreviousElementShadowVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowSources;
import ai.timefold.solver.core.api.domain.variable.ShadowVariable;
import ai.timefold.solver.core.testdomain.TestdataObject;

@PlanningEntity
public class TestdataOwnerLoopVisit extends TestdataObject {

    @InverseRelationShadowVariable(sourceVariableName = "visits")
    TestdataOwnerLoopVehicle vehicle;

    @PreviousElementShadowVariable(sourceVariableName = "visits")
    TestdataOwnerLoopVisit previousVisit;

    int duration = 1;

    @ShadowVariable(supplierName = "endServiceTimeSupplier")
    Integer endServiceTime;

    public TestdataOwnerLoopVisit() {
    }

    public TestdataOwnerLoopVisit(String code) {
        super(code);
    }

    @ShadowSources({ "vehicle", "vehicle.startTime", "previousVisit", "previousVisit.endServiceTime" })
    public Integer endServiceTimeSupplier() {
        if (vehicle == null) {
            return null;
        }
        var base = previousVisit == null ? vehicle.getStartTime() : previousVisit.getEndServiceTime();
        if (base == null) {
            return null;
        }
        return base + duration;
    }

    public TestdataOwnerLoopVehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(TestdataOwnerLoopVehicle vehicle) {
        this.vehicle = vehicle;
    }

    public TestdataOwnerLoopVisit getPreviousVisit() {
        return previousVisit;
    }

    public void setPreviousVisit(TestdataOwnerLoopVisit previousVisit) {
        this.previousVisit = previousVisit;
    }

    public int getDuration() {
        return duration;
    }

    public Integer getEndServiceTime() {
        return endServiceTime;
    }

    public void setEndServiceTime(Integer endServiceTime) {
        this.endServiceTime = endServiceTime;
    }
}
