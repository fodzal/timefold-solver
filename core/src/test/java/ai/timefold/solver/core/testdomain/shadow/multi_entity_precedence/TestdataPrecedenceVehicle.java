package ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence;

import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowSources;
import ai.timefold.solver.core.api.domain.variable.ShadowVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowVariablesInconsistent;
import ai.timefold.solver.core.testdomain.TestdataObject;

/**
 * A vehicle that starts where its predecessor vehicles end,
 * whose visits may depend on visits of other vehicles through precedences.
 */
@PlanningEntity
public class TestdataPrecedenceVehicle extends TestdataObject {

    List<TestdataPrecedenceVehicle> previousVehicles = new ArrayList<>();
    int departureTime;

    @PlanningListVariable(allowsUnassignedValues = true)
    List<TestdataPrecedenceVisit> visits = new ArrayList<>();

    @ShadowVariable(supplierName = "previousEndTimeSupplier")
    Integer previousEndTime;

    @ShadowVariable(supplierName = "endTimeSupplier")
    Integer endTime;

    @ShadowVariablesInconsistent
    boolean inconsistent;

    public TestdataPrecedenceVehicle() {
    }

    public TestdataPrecedenceVehicle(String code, int departureTime) {
        super(code);
        this.departureTime = departureTime;
        // A head vehicle's only source is an empty fact collection,
        // so its supplier is never triggered; initialize to the value it would compute.
        this.previousEndTime = departureTime;
    }

    @ShadowSources("previousVehicles[].endTime")
    public Integer previousEndTimeSupplier() {
        var max = departureTime;
        for (var previousVehicle : previousVehicles) {
            if (previousVehicle.getEndTime() == null) {
                return null;
            }
            max = Math.max(max, previousVehicle.getEndTime());
        }
        return max;
    }

    @ShadowSources({ "visits[].endServiceTime", "previousEndTime" })
    public Integer endTimeSupplier() {
        if (visits.isEmpty()) {
            return previousEndTime;
        }
        return visits.get(visits.size() - 1).getEndServiceTime();
    }

    public List<TestdataPrecedenceVehicle> getPreviousVehicles() {
        return previousVehicles;
    }

    public void setPreviousVehicles(List<TestdataPrecedenceVehicle> previousVehicles) {
        this.previousVehicles = previousVehicles;
    }

    public int getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(int departureTime) {
        this.departureTime = departureTime;
    }

    public List<TestdataPrecedenceVisit> getVisits() {
        return visits;
    }

    public void setVisits(List<TestdataPrecedenceVisit> visits) {
        this.visits = visits;
    }

    public Integer getPreviousEndTime() {
        return previousEndTime;
    }

    public void setPreviousEndTime(Integer previousEndTime) {
        this.previousEndTime = previousEndTime;
    }

    public Integer getEndTime() {
        return endTime;
    }

    public void setEndTime(Integer endTime) {
        this.endTime = endTime;
    }

    public boolean isInconsistent() {
        return inconsistent;
    }

    public void setInconsistent(boolean inconsistent) {
        this.inconsistent = inconsistent;
    }
}
