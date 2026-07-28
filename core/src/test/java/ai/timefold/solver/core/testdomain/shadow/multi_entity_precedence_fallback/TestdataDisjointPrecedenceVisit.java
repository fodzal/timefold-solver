package ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence_fallback;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.timefold.solver.core.api.domain.variable.PreviousElementShadowVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowSources;
import ai.timefold.solver.core.api.domain.variable.ShadowVariable;
import ai.timefold.solver.core.testdomain.TestdataObject;

/**
 * A visit whose arrivalTime depends on other visits (chain and precedence)
 * but whose departureTime, the variable those visits read, does not depend on its arrivalTime.
 * A cycle entering at arrivalTime can thus not leave through departureTime,
 * so entity-level cycle detection would be inexact
 * and the model must fall back to the arbitrary graph.
 */
@PlanningEntity
public class TestdataDisjointPrecedenceVisit extends TestdataObject {

    @InverseRelationShadowVariable(sourceVariableName = "visits")
    TestdataDisjointPrecedenceVehicle vehicle;

    @PreviousElementShadowVariable(sourceVariableName = "visits")
    TestdataDisjointPrecedenceVisit previousVisit;

    TestdataDisjointPrecedenceVisit requiredPredecessor;
    int duration = 1;

    @ShadowVariable(supplierName = "arrivalTimeSupplier")
    Integer arrivalTime;

    @ShadowVariable(supplierName = "departureTimeSupplier")
    Integer departureTime;

    public TestdataDisjointPrecedenceVisit() {
    }

    public TestdataDisjointPrecedenceVisit(String code, int duration) {
        super(code);
        this.duration = duration;
    }

    @ShadowSources({ "vehicle", "previousVisit", "previousVisit.departureTime", "requiredPredecessor.departureTime" })
    public Integer arrivalTimeSupplier() {
        if (vehicle == null) {
            return null;
        }
        var base = previousVisit != null ? previousVisit.getDepartureTime() : (Integer) vehicle.getDepartureTime();
        if (base == null) {
            return null;
        }
        if (requiredPredecessor != null) {
            var predecessorDepartureTime = requiredPredecessor.getDepartureTime();
            if (predecessorDepartureTime == null) {
                return null;
            }
            base = Math.max(base, predecessorDepartureTime);
        }
        return base;
    }

    @ShadowSources("vehicle")
    public Integer departureTimeSupplier() {
        if (vehicle == null) {
            return null;
        }
        return vehicle.getDepartureTime() + duration;
    }

    public TestdataDisjointPrecedenceVisit getRequiredPredecessor() {
        return requiredPredecessor;
    }

    public void setRequiredPredecessor(TestdataDisjointPrecedenceVisit requiredPredecessor) {
        this.requiredPredecessor = requiredPredecessor;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public TestdataDisjointPrecedenceVehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(TestdataDisjointPrecedenceVehicle vehicle) {
        this.vehicle = vehicle;
    }

    public TestdataDisjointPrecedenceVisit getPreviousVisit() {
        return previousVisit;
    }

    public void setPreviousVisit(TestdataDisjointPrecedenceVisit previousVisit) {
        this.previousVisit = previousVisit;
    }

    public Integer getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(Integer arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public Integer getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(Integer departureTime) {
        this.departureTime = departureTime;
    }
}
