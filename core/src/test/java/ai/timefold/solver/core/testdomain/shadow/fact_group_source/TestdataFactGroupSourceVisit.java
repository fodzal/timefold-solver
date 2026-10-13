package ai.timefold.solver.core.testdomain.shadow.fact_group_source;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.timefold.solver.core.api.domain.variable.PreviousElementShadowVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowSources;
import ai.timefold.solver.core.api.domain.variable.ShadowVariable;
import ai.timefold.solver.core.testdomain.TestdataObject;

@PlanningEntity
public class TestdataFactGroupSourceVisit extends TestdataObject {

    @InverseRelationShadowVariable(sourceVariableName = "visits")
    TestdataFactGroupSourceVehicle vehicle;

    @PreviousElementShadowVariable(sourceVariableName = "visits")
    TestdataFactGroupSourceVisit previousVisit;

    @ShadowVariable(supplierName = "endServiceTimeSupplier")
    Integer endServiceTime;

    public TestdataFactGroupSourceVisit() {
    }

    public TestdataFactGroupSourceVisit(String code) {
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
        return base + 1;
    }

    public TestdataFactGroupSourceVehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(TestdataFactGroupSourceVehicle vehicle) {
        this.vehicle = vehicle;
    }

    public TestdataFactGroupSourceVisit getPreviousVisit() {
        return previousVisit;
    }

    public void setPreviousVisit(TestdataFactGroupSourceVisit previousVisit) {
        this.previousVisit = previousVisit;
    }

    public Integer getEndServiceTime() {
        return endServiceTime;
    }

    public void setEndServiceTime(Integer endServiceTime) {
        this.endServiceTime = endServiceTime;
    }
}
