package ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_fallback;

import java.util.List;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.SimpleScore;
import ai.timefold.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.timefold.solver.core.preview.api.domain.metamodel.PlanningSolutionMetaModel;

@PlanningSolution
public class TestdataNonDeclarativeListEntitySolution {

    public static SolutionDescriptor<TestdataNonDeclarativeListEntitySolution> buildSolutionDescriptor() {
        return SolutionDescriptor.buildSolutionDescriptor(TestdataNonDeclarativeListEntitySolution.class,
                TestdataNonDeclarativeListEntityVehicle.class, TestdataNonDeclarativeListEntityVisit.class,
                TestdataNonDeclarativeListEntityDepot.class);
    }

    public static PlanningSolutionMetaModel<TestdataNonDeclarativeListEntitySolution> buildMetaModel() {
        return buildSolutionDescriptor().getMetaModel();
    }

    @PlanningEntityCollectionProperty
    List<TestdataNonDeclarativeListEntityVehicle> vehicles;

    @PlanningEntityCollectionProperty
    @ValueRangeProvider
    List<TestdataNonDeclarativeListEntityVisit> visits;

    @PlanningEntityCollectionProperty
    List<TestdataNonDeclarativeListEntityDepot> depots;

    @PlanningScore
    SimpleScore score;

    public List<TestdataNonDeclarativeListEntityVehicle> getVehicles() {
        return vehicles;
    }

    public void setVehicles(List<TestdataNonDeclarativeListEntityVehicle> vehicles) {
        this.vehicles = vehicles;
    }

    public List<TestdataNonDeclarativeListEntityVisit> getVisits() {
        return visits;
    }

    public void setVisits(List<TestdataNonDeclarativeListEntityVisit> visits) {
        this.visits = visits;
    }

    public List<TestdataNonDeclarativeListEntityDepot> getDepots() {
        return depots;
    }

    public void setDepots(List<TestdataNonDeclarativeListEntityDepot> depots) {
        this.depots = depots;
    }

    public SimpleScore getScore() {
        return score;
    }

    public void setScore(SimpleScore score) {
        this.score = score;
    }
}
