package ai.timefold.solver.core.testdomain.shadow.aligned_multi_entity;

import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowSources;
import ai.timefold.solver.core.api.domain.variable.ShadowVariable;
import ai.timefold.solver.core.testdomain.TestdataObject;

/**
 * A vehicle with a declarative shadow variable of its own,
 * so that the declarative shadow variables span more than one entity class.
 */
@PlanningEntity
public class TestdataAlignedMultiEntityVehicle extends TestdataObject {

    int departureTime;

    @PlanningListVariable(allowsUnassignedValues = true)
    List<TestdataAlignedMultiEntityVisit> visits = new ArrayList<>();

    @ShadowVariable(supplierName = "lastEndTimeSupplier")
    Integer lastEndTime;

    public TestdataAlignedMultiEntityVehicle() {
    }

    public TestdataAlignedMultiEntityVehicle(String code, int departureTime) {
        super(code);
        this.departureTime = departureTime;
    }

    @ShadowSources("visits[].endTime")
    public Integer lastEndTimeSupplier() {
        if (visits.isEmpty()) {
            return departureTime;
        }
        return visits.getLast().getEndTime();
    }

    public int getDepartureTime() {
        return departureTime;
    }

    public List<TestdataAlignedMultiEntityVisit> getVisits() {
        return visits;
    }

    public void setVisits(List<TestdataAlignedMultiEntityVisit> visits) {
        this.visits = visits;
    }

    public Integer getLastEndTime() {
        return lastEndTime;
    }

    public void setLastEndTime(Integer lastEndTime) {
        this.lastEndTime = lastEndTime;
    }
}
