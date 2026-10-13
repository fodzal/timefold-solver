package ai.timefold.solver.core.testdomain.shadow.fact_group_source;

import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowSources;
import ai.timefold.solver.core.api.domain.variable.ShadowVariable;
import ai.timefold.solver.core.testdomain.TestdataObject;

/**
 * A vehicle that starts once all of its predecessors, a problem fact, have ended.
 * The start time of a vehicle without predecessors has no source that can ever change.
 */
@PlanningEntity
public class TestdataFactGroupSourceVehicle extends TestdataObject {

    List<TestdataFactGroupSourceVehicle> previousVehicles = new ArrayList<>();

    int departureTime;

    @PlanningListVariable(allowsUnassignedValues = true)
    List<TestdataFactGroupSourceVisit> visits = new ArrayList<>();

    @ShadowVariable(supplierName = "startTimeSupplier")
    Integer startTime;

    @ShadowVariable(supplierName = "endTimeSupplier")
    Integer endTime;

    public TestdataFactGroupSourceVehicle() {
    }

    public TestdataFactGroupSourceVehicle(String code, int departureTime) {
        super(code);
        this.departureTime = departureTime;
    }

    @ShadowSources("previousVehicles[].endTime")
    public Integer startTimeSupplier() {
        var start = departureTime;
        for (var previousVehicle : previousVehicles) {
            if (previousVehicle.getEndTime() == null) {
                return null;
            }
            start = Math.max(start, previousVehicle.getEndTime());
        }
        return start;
    }

    @ShadowSources({ "visits[].endServiceTime", "startTime" })
    public Integer endTimeSupplier() {
        if (visits.isEmpty()) {
            return startTime;
        }
        return visits.getLast().getEndServiceTime();
    }

    public List<TestdataFactGroupSourceVehicle> getPreviousVehicles() {
        return previousVehicles;
    }

    public void setPreviousVehicles(List<TestdataFactGroupSourceVehicle> previousVehicles) {
        this.previousVehicles = previousVehicles;
    }

    public int getDepartureTime() {
        return departureTime;
    }

    public List<TestdataFactGroupSourceVisit> getVisits() {
        return visits;
    }

    public void setVisits(List<TestdataFactGroupSourceVisit> visits) {
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
