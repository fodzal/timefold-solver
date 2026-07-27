package ai.timefold.solver.core.impl.domain.variable.declarative;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import ai.timefold.solver.core.preview.api.move.builtin.Moves;
import ai.timefold.solver.core.preview.api.move.test.MoveTester;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_next.TestdataMultiEntityChainNextConstraintProvider;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_next.TestdataMultiEntityChainNextSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_next.TestdataMultiEntityChainNextVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_next.TestdataMultiEntityChainNextVisit;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link MultiEntitySingleDirectionalParentVariableReferenceGraph} on a next-directional model,
 * where a vehicle's latest start time is bound by its successor vehicles
 * and propagation walks each route backwards.
 */
class MultiEntitySingleDirectionalParentNextShadowVariableTest {

    @Test
    void changeOnSuccessorVehiclePropagatesBackwards() {
        var x1 = new TestdataMultiEntityChainNextVisit("x1");
        var x2 = new TestdataMultiEntityChainNextVisit("x2");
        var x3 = new TestdataMultiEntityChainNextVisit("x3");
        var y1 = new TestdataMultiEntityChainNextVisit("y1");
        var y2 = new TestdataMultiEntityChainNextVisit("y2");
        var y3 = new TestdataMultiEntityChainNextVisit("y3"); // Initially unassigned.

        var vehicleA = new TestdataMultiEntityChainNextVehicle("A", 100);
        var vehicleB = new TestdataMultiEntityChainNextVehicle("B", 100);
        vehicleA.getNextVehicles().add(vehicleB);
        vehicleA.setVisits(new ArrayList<>(List.of(x1, x2, x3)));
        vehicleB.setVisits(new ArrayList<>(List.of(y1, y2)));

        var solution = new TestdataMultiEntityChainNextSolution();
        solution.setVehicles(List.of(vehicleA, vehicleB));
        solution.setVisits(List.of(x1, x2, x3, y1, y2, y3));

        var solutionMetaModel = TestdataMultiEntityChainNextSolution.buildMetaModel();
        var listVariableMetaModel = solutionMetaModel.genuineEntity(TestdataMultiEntityChainNextVehicle.class)
                .listVariable("visits", TestdataMultiEntityChainNextVisit.class);

        var context = MoveTester.build(solutionMetaModel).using(solution);
        // B = [y1, y2] starts at 98; A is bound by B -> [x1, x2, x3] starts at 95.
        assertThat(vehicleB.getStartTime()).isEqualTo(98);
        assertThat(vehicleA.getNextStartTime()).isEqualTo(98);
        assertThat(x3.getLatestStartTime()).isEqualTo(97);
        assertThat(x2.getLatestStartTime()).isEqualTo(96);
        assertThat(x1.getLatestStartTime()).isEqualTo(95);
        assertThat(vehicleA.getStartTime()).isEqualTo(95);

        // Prepending y3 to B shifts A's whole route backwards.
        context.execute(Moves.assign(listVariableMetaModel, y3, vehicleB, 0));
        assertThat(vehicleB.getStartTime()).isEqualTo(97);
        assertThat(vehicleA.getNextStartTime()).isEqualTo(97);
        assertThat(x3.getLatestStartTime()).isEqualTo(96);
        assertThat(x2.getLatestStartTime()).isEqualTo(95);
        assertThat(x1.getLatestStartTime()).isEqualTo(94);
        assertThat(vehicleA.getStartTime()).isEqualTo(94);
        assertShadowsAreAtFixedPoint(solution);

        // Removing y3 restores the original times.
        context.execute(Moves.unassign(listVariableMetaModel, vehicleB, 0));
        assertThat(y3.getLatestStartTime()).isNull();
        assertThat(vehicleB.getStartTime()).isEqualTo(98);
        assertThat(vehicleA.getNextStartTime()).isEqualTo(98);
        assertThat(x1.getLatestStartTime()).isEqualTo(95);
        assertThat(vehicleA.getStartTime()).isEqualTo(95);
        assertShadowsAreAtFixedPoint(solution);
    }

    @Test
    void solveWithFullAssert() {
        solveWithFullAssert(generateSolution(false));
    }

    @Test
    void solveWithFullAssertFromInitializedSolution() {
        solveWithFullAssert(generateSolution(true));
    }

    private static TestdataMultiEntityChainNextSolution generateSolution(boolean initialized) {
        var vehicles = new ArrayList<TestdataMultiEntityChainNextVehicle>();
        for (var i = 0; i < 3; i++) {
            vehicles.add(new TestdataMultiEntityChainNextVehicle("vehicle" + i, 100));
        }
        // vehicle0 -> vehicle1 -> vehicle2 chain.
        vehicles.get(0).getNextVehicles().add(vehicles.get(1));
        vehicles.get(1).getNextVehicles().add(vehicles.get(2));
        var visits = new ArrayList<TestdataMultiEntityChainNextVisit>();
        for (var i = 0; i < 6; i++) {
            visits.add(new TestdataMultiEntityChainNextVisit("visit" + i, 1 + (i % 3)));
        }
        if (initialized) {
            vehicles.get(0).setVisits(new ArrayList<>(visits.subList(0, 4)));
            vehicles.get(1).setVisits(new ArrayList<>(visits.subList(4, 6)));
        }
        var solution = new TestdataMultiEntityChainNextSolution();
        solution.setVehicles(vehicles);
        solution.setVisits(visits);
        return solution;
    }

    private static void solveWithFullAssert(TestdataMultiEntityChainNextSolution problem) {
        var solverConfig = new SolverConfig()
                .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
                .withSolutionClass(TestdataMultiEntityChainNextSolution.class)
                .withEntityClasses(TestdataMultiEntityChainNextVehicle.class, TestdataMultiEntityChainNextVisit.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(TestdataMultiEntityChainNextConstraintProvider.class))
                .withTerminationConfig(new TerminationConfig().withMoveCountLimit(1000L));

        var solution = SolverFactory.<TestdataMultiEntityChainNextSolution> create(solverConfig).buildSolver()
                .solve(problem);

        assertThat(solution).isNotNull();
        assertShadowsAreAtFixedPoint(solution);
    }

    /**
     * The incrementally maintained shadow variables must equal a from-scratch recomputation,
     * which uses the arbitrary graph.
     */
    private static void assertShadowsAreAtFixedPoint(TestdataMultiEntityChainNextSolution solution) {
        var vehicleToStartTime = solution.getVehicles().stream()
                .map(TestdataMultiEntityChainNextVehicle::getStartTime)
                .toList();
        var visitToLatestStartTime = solution.getVisits().stream()
                .map(TestdataMultiEntityChainNextVisit::getLatestStartTime)
                .toList();

        SolutionManager.updateShadowVariables(solution);

        assertThat(solution.getVehicles().stream().map(TestdataMultiEntityChainNextVehicle::getStartTime))
                .containsExactlyElementsOf(vehicleToStartTime);
        assertThat(solution.getVisits().stream().map(TestdataMultiEntityChainNextVisit::getLatestStartTime))
                .containsExactlyElementsOf(visitToLatestStartTime);
    }
}
