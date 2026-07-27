package ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_rebase;

import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowSources;
import ai.timefold.solver.core.api.domain.variable.ShadowVariable;
import ai.timefold.solver.core.testdomain.TestdataObject;

/**
 * A vehicle whose visits may re-base on the predecessor vehicles' end time mid-chain,
 * so a pre-chain change can affect an element whose predecessors are unchanged.
 */
@PlanningEntity
public class TestdataMultiEntityChainRebaseVehicle extends TestdataObject {

    List<TestdataMultiEntityChainRebaseVehicle> previousVehicles = new ArrayList<>();
    int departureTime;

    @PlanningListVariable(allowsUnassignedValues = true)
    List<TestdataMultiEntityChainRebaseVisit> visits = new ArrayList<>();

    @ShadowVariable(supplierName = "previousEndTimeSupplier")
    Integer previousEndTime;

    @ShadowVariable(supplierName = "endTimeSupplier")
    Integer endTime;

    public TestdataMultiEntityChainRebaseVehicle() {
    }

    public TestdataMultiEntityChainRebaseVehicle(String code, int departureTime) {
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

    public List<TestdataMultiEntityChainRebaseVehicle> getPreviousVehicles() {
        return previousVehicles;
    }

    public void setPreviousVehicles(List<TestdataMultiEntityChainRebaseVehicle> previousVehicles) {
        this.previousVehicles = previousVehicles;
    }

    public int getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(int departureTime) {
        this.departureTime = departureTime;
    }

    public List<TestdataMultiEntityChainRebaseVisit> getVisits() {
        return visits;
    }

    public void setVisits(List<TestdataMultiEntityChainRebaseVisit> visits) {
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
}
