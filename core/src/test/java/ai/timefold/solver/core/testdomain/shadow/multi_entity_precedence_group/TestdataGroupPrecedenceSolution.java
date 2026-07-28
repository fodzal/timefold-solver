package ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence_group;

import java.util.List;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.SimpleScore;
import ai.timefold.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.timefold.solver.core.preview.api.domain.metamodel.PlanningSolutionMetaModel;

@PlanningSolution
public class TestdataGroupPrecedenceSolution {

    public static SolutionDescriptor<TestdataGroupPrecedenceSolution> buildSolutionDescriptor() {
        return SolutionDescriptor.buildSolutionDescriptor(TestdataGroupPrecedenceSolution.class,
                TestdataGroupPrecedenceVehicle.class, TestdataGroupPrecedenceVisit.class);
    }

    public static PlanningSolutionMetaModel<TestdataGroupPrecedenceSolution> buildMetaModel() {
        return buildSolutionDescriptor().getMetaModel();
    }

    @PlanningEntityCollectionProperty
    List<TestdataGroupPrecedenceVehicle> vehicles;

    @PlanningEntityCollectionProperty
    @ValueRangeProvider
    List<TestdataGroupPrecedenceVisit> visits;

    @PlanningScore
    SimpleScore score;

    public List<TestdataGroupPrecedenceVehicle> getVehicles() {
        return vehicles;
    }

    public void setVehicles(List<TestdataGroupPrecedenceVehicle> vehicles) {
        this.vehicles = vehicles;
    }

    public List<TestdataGroupPrecedenceVisit> getVisits() {
        return visits;
    }

    public void setVisits(List<TestdataGroupPrecedenceVisit> visits) {
        this.visits = visits;
    }

    public SimpleScore getScore() {
        return score;
    }

    public void setScore(SimpleScore score) {
        this.score = score;
    }
}
