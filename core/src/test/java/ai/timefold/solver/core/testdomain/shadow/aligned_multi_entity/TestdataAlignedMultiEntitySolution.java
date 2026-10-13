package ai.timefold.solver.core.testdomain.shadow.aligned_multi_entity;

import java.util.List;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.SimpleScore;

@PlanningSolution
public class TestdataAlignedMultiEntitySolution {

    @PlanningEntityCollectionProperty
    List<TestdataAlignedMultiEntityVehicle> vehicles;

    @PlanningEntityCollectionProperty
    @ValueRangeProvider
    List<TestdataAlignedMultiEntityVisit> visits;

    @PlanningScore
    SimpleScore score;

    public List<TestdataAlignedMultiEntityVehicle> getVehicles() {
        return vehicles;
    }

    public void setVehicles(List<TestdataAlignedMultiEntityVehicle> vehicles) {
        this.vehicles = vehicles;
    }

    public List<TestdataAlignedMultiEntityVisit> getVisits() {
        return visits;
    }

    public void setVisits(List<TestdataAlignedMultiEntityVisit> visits) {
        this.visits = visits;
    }

    public SimpleScore getScore() {
        return score;
    }

    public void setScore(SimpleScore score) {
        this.score = score;
    }
}
