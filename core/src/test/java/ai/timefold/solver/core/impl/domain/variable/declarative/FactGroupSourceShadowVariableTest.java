package ai.timefold.solver.core.impl.domain.variable.declarative;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.timefold.solver.core.testdomain.shadow.fact_group_source.TestdataFactGroupSourceSolution;
import ai.timefold.solver.core.testdomain.shadow.fact_group_source.TestdataFactGroupSourceVehicle;
import ai.timefold.solver.core.testdomain.shadow.fact_group_source.TestdataFactGroupSourceVisit;
import ai.timefold.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;

/**
 * Tests that the arbitrary graph computes every declarative shadow variable once,
 * including those without any source that could ever trigger them.
 */
class FactGroupSourceShadowVariableTest {

    @Test
    void graphStructureIsArbitrary() {
        var vehicle = new TestdataFactGroupSourceVehicle("A", 5);
        var visit = new TestdataFactGroupSourceVisit("a");
        var solutionDescriptor = SolutionDescriptor.buildSolutionDescriptor(TestdataFactGroupSourceSolution.class,
                TestdataFactGroupSourceVehicle.class, TestdataFactGroupSourceVisit.class);

        assertThat(GraphStructure.determineGraphStructure(solutionDescriptor, vehicle, visit).structure())
                .isEqualTo(GraphStructure.ARBITRARY);
    }

    @Test
    void computeVariableWithoutPredecessors() {
        var vehicleA = new TestdataFactGroupSourceVehicle("A", 5);
        var vehicleB = new TestdataFactGroupSourceVehicle("B", 0);
        var visitA = new TestdataFactGroupSourceVisit("a");
        var visitB = new TestdataFactGroupSourceVisit("b");
        vehicleB.setPreviousVehicles(List.of(vehicleA));
        vehicleA.setVisits(new ArrayList<>(List.of(visitA)));
        vehicleB.setVisits(new ArrayList<>(List.of(visitB)));

        var solution = new TestdataFactGroupSourceSolution();
        solution.setVehicles(List.of(vehicleA, vehicleB));
        solution.setVisits(List.of(visitA, visitB));

        SolutionManager.updateShadowVariables(solution);

        assertThat(vehicleA.getStartTime()).isEqualTo(5);
        assertThat(visitA.getEndServiceTime()).isEqualTo(6);
        assertThat(vehicleA.getEndTime()).isEqualTo(6);
        assertThat(vehicleB.getStartTime()).isEqualTo(6);
        assertThat(visitB.getEndServiceTime()).isEqualTo(7);
        assertThat(vehicleB.getEndTime()).isEqualTo(7);
    }

    @Test
    void computeUnassignedVisitWithoutVehicles() {
        var visit = new TestdataFactGroupSourceVisit("a");
        visit.setEndServiceTime(42);

        var solution = new TestdataFactGroupSourceSolution();
        solution.setVehicles(List.of());
        solution.setVisits(List.of(visit));

        // Without any vehicle instance, the entity classes must come from the solver configuration.
        var solverFactory = PlannerTestUtils.buildSolverFactory(TestdataFactGroupSourceSolution.class,
                TestdataFactGroupSourceVehicle.class, TestdataFactGroupSourceVisit.class);
        SolutionManager.create(solverFactory).update(solution);

        assertThat(visit.getEndServiceTime()).isNull();
    }
}
