package ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence_fallback;

import java.util.List;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.SimpleScore;
import ai.timefold.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.timefold.solver.core.preview.api.domain.metamodel.PlanningSolutionMetaModel;

@PlanningSolution
public class TestdataDisjointPrecedenceSolution {

    public static SolutionDescriptor<TestdataDisjointPrecedenceSolution> buildSolutionDescriptor() {
        return SolutionDescriptor.buildSolutionDescriptor(TestdataDisjointPrecedenceSolution.class,
                TestdataDisjointPrecedenceVehicle.class, TestdataDisjointPrecedenceVisit.class);
    }

    public static PlanningSolutionMetaModel<TestdataDisjointPrecedenceSolution> buildMetaModel() {
        return buildSolutionDescriptor().getMetaModel();
    }

    @PlanningEntityCollectionProperty
    List<TestdataDisjointPrecedenceVehicle> vehicles;

    @PlanningEntityCollectionProperty
    @ValueRangeProvider
    List<TestdataDisjointPrecedenceVisit> visits;

    @PlanningScore
    SimpleScore score;

    public List<TestdataDisjointPrecedenceVehicle> getVehicles() {
        return vehicles;
    }

    public void setVehicles(List<TestdataDisjointPrecedenceVehicle> vehicles) {
        this.vehicles = vehicles;
    }

    public List<TestdataDisjointPrecedenceVisit> getVisits() {
        return visits;
    }

    public void setVisits(List<TestdataDisjointPrecedenceVisit> visits) {
        this.visits = visits;
    }

    public SimpleScore getScore() {
        return score;
    }

    public void setScore(SimpleScore score) {
        this.score = score;
    }
}
