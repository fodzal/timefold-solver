package ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_rebase;

import java.util.List;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.SimpleScore;
import ai.timefold.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.timefold.solver.core.preview.api.domain.metamodel.PlanningSolutionMetaModel;

@PlanningSolution
public class TestdataMultiEntityChainRebaseSolution {

    public static SolutionDescriptor<TestdataMultiEntityChainRebaseSolution> buildSolutionDescriptor() {
        return SolutionDescriptor.buildSolutionDescriptor(TestdataMultiEntityChainRebaseSolution.class,
                TestdataMultiEntityChainRebaseVehicle.class, TestdataMultiEntityChainRebaseVisit.class);
    }

    public static PlanningSolutionMetaModel<TestdataMultiEntityChainRebaseSolution> buildMetaModel() {
        return buildSolutionDescriptor().getMetaModel();
    }

    @PlanningEntityCollectionProperty
    List<TestdataMultiEntityChainRebaseVehicle> vehicles;

    @PlanningEntityCollectionProperty
    @ValueRangeProvider
    List<TestdataMultiEntityChainRebaseVisit> visits;

    @PlanningScore
    SimpleScore score;

    public List<TestdataMultiEntityChainRebaseVehicle> getVehicles() {
        return vehicles;
    }

    public void setVehicles(List<TestdataMultiEntityChainRebaseVehicle> vehicles) {
        this.vehicles = vehicles;
    }

    public List<TestdataMultiEntityChainRebaseVisit> getVisits() {
        return visits;
    }

    public void setVisits(List<TestdataMultiEntityChainRebaseVisit> visits) {
        this.visits = visits;
    }

    public SimpleScore getScore() {
        return score;
    }

    public void setScore(SimpleScore score) {
        this.score = score;
    }
}
