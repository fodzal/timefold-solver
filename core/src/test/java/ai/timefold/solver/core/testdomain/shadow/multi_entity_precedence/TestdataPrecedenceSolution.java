package ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence;

import java.util.List;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.SimpleScore;
import ai.timefold.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.timefold.solver.core.preview.api.domain.metamodel.PlanningSolutionMetaModel;

@PlanningSolution
public class TestdataPrecedenceSolution {

    public static SolutionDescriptor<TestdataPrecedenceSolution> buildSolutionDescriptor() {
        return SolutionDescriptor.buildSolutionDescriptor(TestdataPrecedenceSolution.class,
                TestdataPrecedenceVehicle.class, TestdataPrecedenceVisit.class);
    }

    public static PlanningSolutionMetaModel<TestdataPrecedenceSolution> buildMetaModel() {
        return buildSolutionDescriptor().getMetaModel();
    }

    @PlanningEntityCollectionProperty
    List<TestdataPrecedenceVehicle> vehicles;

    @PlanningEntityCollectionProperty
    @ValueRangeProvider
    List<TestdataPrecedenceVisit> visits;

    @PlanningScore
    SimpleScore score;

    public List<TestdataPrecedenceVehicle> getVehicles() {
        return vehicles;
    }

    public void setVehicles(List<TestdataPrecedenceVehicle> vehicles) {
        this.vehicles = vehicles;
    }

    public List<TestdataPrecedenceVisit> getVisits() {
        return visits;
    }

    public void setVisits(List<TestdataPrecedenceVisit> visits) {
        this.visits = visits;
    }

    public SimpleScore getScore() {
        return score;
    }

    public void setScore(SimpleScore score) {
        this.score = score;
    }
}
