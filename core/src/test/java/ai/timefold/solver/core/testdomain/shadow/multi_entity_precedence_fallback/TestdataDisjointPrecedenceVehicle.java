package ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence_fallback;

import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowSources;
import ai.timefold.solver.core.api.domain.variable.ShadowVariable;
import ai.timefold.solver.core.testdomain.TestdataObject;

@PlanningEntity
public class TestdataDisjointPrecedenceVehicle extends TestdataObject {

    int departureTime;

    @PlanningListVariable(allowsUnassignedValues = true)
    List<TestdataDisjointPrecedenceVisit> visits = new ArrayList<>();

    @ShadowVariable(supplierName = "endTimeSupplier")
    Integer endTime;

    public TestdataDisjointPrecedenceVehicle() {
    }

    public TestdataDisjointPrecedenceVehicle(String code, int departureTime) {
        super(code);
        this.departureTime = departureTime;
    }

    @ShadowSources("visits[].departureTime")
    public Integer endTimeSupplier() {
        var max = departureTime;
        for (var visit : visits) {
            if (visit.getDepartureTime() == null) {
                return null;
            }
            max = Math.max(max, visit.getDepartureTime());
        }
        return max;
    }

    public int getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(int departureTime) {
        this.departureTime = departureTime;
    }

    public List<TestdataDisjointPrecedenceVisit> getVisits() {
        return visits;
    }

    public void setVisits(List<TestdataDisjointPrecedenceVisit> visits) {
        this.visits = visits;
    }

    public Integer getEndTime() {
        return endTime;
    }

    public void setEndTime(Integer endTime) {
        this.endTime = endTime;
    }
}
