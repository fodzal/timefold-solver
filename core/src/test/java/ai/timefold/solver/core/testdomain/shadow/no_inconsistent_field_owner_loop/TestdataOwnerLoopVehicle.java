package ai.timefold.solver.core.testdomain.shadow.no_inconsistent_field_owner_loop;

import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowSources;
import ai.timefold.solver.core.api.domain.variable.ShadowVariable;
import ai.timefold.solver.core.testdomain.TestdataObject;

/**
 * A vehicle that starts where its predecessor ends, where the predecessor is a planning variable.
 * Two vehicles chained to each other form a dependency loop
 * that runs through the declarative shadow variables of both vehicles and of their visits.
 */
@PlanningEntity
public class TestdataOwnerLoopVehicle extends TestdataObject {

    @PlanningVariable(allowsUnassigned = true)
    TestdataOwnerLoopVehicle previousVehicle;

    int departureTime;

    @PlanningListVariable(allowsUnassignedValues = true)
    List<TestdataOwnerLoopVisit> visits = new ArrayList<>();

    @ShadowVariable(supplierName = "startTimeSupplier")
    Integer startTime;

    @ShadowVariable(supplierName = "endTimeSupplier")
    Integer endTime;

    public TestdataOwnerLoopVehicle() {
    }

    public TestdataOwnerLoopVehicle(String code, int departureTime) {
        super(code);
        this.departureTime = departureTime;
    }

    @ShadowSources({ "previousVehicle", "previousVehicle.endTime" })
    public Integer startTimeSupplier() {
        if (previousVehicle == null) {
            return departureTime;
        }
        return previousVehicle.getEndTime();
    }

    @ShadowSources({ "visits[].endServiceTime", "startTime" })
    public Integer endTimeSupplier() {
        if (visits.isEmpty()) {
            return startTime;
        }
        return visits.getLast().getEndServiceTime();
    }

    public TestdataOwnerLoopVehicle getPreviousVehicle() {
        return previousVehicle;
    }

    public void setPreviousVehicle(TestdataOwnerLoopVehicle previousVehicle) {
        this.previousVehicle = previousVehicle;
    }

    public int getDepartureTime() {
        return departureTime;
    }

    public List<TestdataOwnerLoopVisit> getVisits() {
        return visits;
    }

    public void setVisits(List<TestdataOwnerLoopVisit> visits) {
        this.visits = visits;
    }

    public Integer getStartTime() {
        return startTime;
    }

    public void setStartTime(Integer startTime) {
        this.startTime = startTime;
    }

    public Integer getEndTime() {
        return endTime;
    }

    public void setEndTime(Integer endTime) {
        this.endTime = endTime;
    }
}
