package ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_fallback;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.timefold.solver.core.api.domain.variable.PreviousElementShadowVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowSources;
import ai.timefold.solver.core.api.domain.variable.ShadowVariable;
import ai.timefold.solver.core.testdomain.TestdataObject;

@PlanningEntity
public class TestdataNonDeclarativeListEntityVisit extends TestdataObject {

    @InverseRelationShadowVariable(sourceVariableName = "visits")
    TestdataNonDeclarativeListEntityVehicle vehicle;

    @PreviousElementShadowVariable(sourceVariableName = "visits")
    TestdataNonDeclarativeListEntityVisit previousVisit;

    int duration = 1;

    @ShadowVariable(supplierName = "endServiceTimeSupplier")
    Integer endServiceTime;

    public TestdataNonDeclarativeListEntityVisit() {
    }

    public TestdataNonDeclarativeListEntityVisit(String code, int duration) {
        super(code);
        this.duration = duration;
    }

    @ShadowSources({ "vehicle", "previousVisit", "previousVisit.endServiceTime" })
    public Integer endServiceTimeSupplier() {
        if (vehicle == null) {
            return null;
        }
        var base = previousVisit != null ? previousVisit.getEndServiceTime() : (Integer) vehicle.getDepartureTime();
        if (base == null) {
            return null;
        }
        return base + duration;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public TestdataNonDeclarativeListEntityVehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(TestdataNonDeclarativeListEntityVehicle vehicle) {
        this.vehicle = vehicle;
    }

    public TestdataNonDeclarativeListEntityVisit getPreviousVisit() {
        return previousVisit;
    }

    public void setPreviousVisit(TestdataNonDeclarativeListEntityVisit previousVisit) {
        this.previousVisit = previousVisit;
    }

    public Integer getEndServiceTime() {
        return endServiceTime;
    }

    public void setEndServiceTime(Integer endServiceTime) {
        this.endServiceTime = endServiceTime;
    }
}
