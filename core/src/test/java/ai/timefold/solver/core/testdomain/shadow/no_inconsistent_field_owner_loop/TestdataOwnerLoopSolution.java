package ai.timefold.solver.core.testdomain.shadow.no_inconsistent_field_owner_loop;

import java.util.List;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.SimpleScore;

@PlanningSolution
public class TestdataOwnerLoopSolution {

    // The vehicles are their own value range, so previousVehicle can chain any two of them.
    @PlanningEntityCollectionProperty
    @ValueRangeProvider
    List<TestdataOwnerLoopVehicle> vehicles;

    @PlanningEntityCollectionProperty
    @ValueRangeProvider
    List<TestdataOwnerLoopVisit> visits;

    @PlanningScore
    SimpleScore score;

    public List<TestdataOwnerLoopVehicle> getVehicles() {
        return vehicles;
    }

    public void setVehicles(List<TestdataOwnerLoopVehicle> vehicles) {
        this.vehicles = vehicles;
    }

    public List<TestdataOwnerLoopVisit> getVisits() {
        return visits;
    }

    public void setVisits(List<TestdataOwnerLoopVisit> visits) {
        this.visits = visits;
    }

    public SimpleScore getScore() {
        return score;
    }

    public void setScore(SimpleScore score) {
        this.score = score;
    }
}
