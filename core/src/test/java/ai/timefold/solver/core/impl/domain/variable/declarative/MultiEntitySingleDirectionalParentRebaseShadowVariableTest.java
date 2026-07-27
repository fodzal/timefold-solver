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
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_rebase.TestdataMultiEntityChainRebaseConstraintProvider;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_rebase.TestdataMultiEntityChainRebaseSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_rebase.TestdataMultiEntityChainRebaseVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_rebase.TestdataMultiEntityChainRebaseVisit;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link MultiEntitySingleDirectionalParentVariableReferenceGraph} on a model where
 * an element in the middle of the chain reads a pre-chain variable directly,
 * so a pre-chain change must reach it even when its predecessors are unchanged.
 */
class MultiEntitySingleDirectionalParentRebaseShadowVariableTest {

    @Test
    void preChainChangeReachesRebasingElement() {
        var w = new TestdataMultiEntityChainRebaseVisit("w", 5, false); // Initially unassigned.
        var v1 = new TestdataMultiEntityChainRebaseVisit("v1", 1, false);
        var v2 = new TestdataMultiEntityChainRebaseVisit("v2", 1, true);
        var v3 = new TestdataMultiEntityChainRebaseVisit("v3", 1, false);

        var vehicleA = new TestdataMultiEntityChainRebaseVehicle("A", 0);
        var vehicleB = new TestdataMultiEntityChainRebaseVehicle("B", 0);
        vehicleB.getPreviousVehicles().add(vehicleA);
        vehicleB.setVisits(new ArrayList<>(List.of(v1, v2, v3)));

        var solution = new TestdataMultiEntityChainRebaseSolution();
        solution.setVehicles(List.of(vehicleA, vehicleB));
        solution.setVisits(List.of(w, v1, v2, v3));

        var solutionMetaModel = TestdataMultiEntityChainRebaseSolution.buildMetaModel();
        var listVariableMetaModel = solutionMetaModel.genuineEntity(TestdataMultiEntityChainRebaseVehicle.class)
                .listVariable("visits", TestdataMultiEntityChainRebaseVisit.class);

        var context = MoveTester.build(solutionMetaModel).using(solution);
        assertThat(vehicleB.getPreviousEndTime()).isEqualTo(0);
        assertThat(v1.getEndServiceTime()).isEqualTo(1);
        assertThat(v2.getEndServiceTime()).isEqualTo(2);
        assertThat(v3.getEndServiceTime()).isEqualTo(3);
        assertThat(vehicleB.getEndTime()).isEqualTo(3);

        // Assigning w to A changes B's previousEndTime;
        // v1 does not read it and stays unchanged, but v2 re-bases on it.
        context.execute(Moves.assign(listVariableMetaModel, w, vehicleA, 0));
        assertThat(vehicleA.getEndTime()).isEqualTo(5);
        assertThat(vehicleB.getPreviousEndTime()).isEqualTo(5);
        assertThat(v1.getEndServiceTime()).isEqualTo(1);
        assertThat(v2.getEndServiceTime()).isEqualTo(6);
        assertThat(v3.getEndServiceTime()).isEqualTo(7);
        assertThat(vehicleB.getEndTime()).isEqualTo(7);
        assertShadowsAreAtFixedPoint(solution);
    }

    @Test
    void solveWithFullAssert() {
        var vehicles = new ArrayList<TestdataMultiEntityChainRebaseVehicle>();
        for (var i = 0; i < 3; i++) {
            vehicles.add(new TestdataMultiEntityChainRebaseVehicle("vehicle" + i, i));
        }
        // vehicle0 -> vehicle1 -> vehicle2 chain.
        vehicles.get(1).getPreviousVehicles().add(vehicles.get(0));
        vehicles.get(2).getPreviousVehicles().add(vehicles.get(1));
        var visits = new ArrayList<TestdataMultiEntityChainRebaseVisit>();
        for (var i = 0; i < 6; i++) {
            visits.add(new TestdataMultiEntityChainRebaseVisit("visit" + i, 1 + (i % 3), i % 2 == 0));
        }
        var problem = new TestdataMultiEntityChainRebaseSolution();
        problem.setVehicles(vehicles);
        problem.setVisits(visits);

        var solverConfig = new SolverConfig()
                .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
                .withSolutionClass(TestdataMultiEntityChainRebaseSolution.class)
                .withEntityClasses(TestdataMultiEntityChainRebaseVehicle.class, TestdataMultiEntityChainRebaseVisit.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(TestdataMultiEntityChainRebaseConstraintProvider.class))
                .withTerminationConfig(new TerminationConfig().withMoveCountLimit(1000L));

        var solution = SolverFactory.<TestdataMultiEntityChainRebaseSolution> create(solverConfig).buildSolver()
                .solve(problem);

        assertThat(solution).isNotNull();
        assertShadowsAreAtFixedPoint(solution);
    }

    /**
     * The incrementally maintained shadow variables must equal a from-scratch recomputation,
     * which uses the arbitrary graph.
     */
    private static void assertShadowsAreAtFixedPoint(TestdataMultiEntityChainRebaseSolution solution) {
        var vehicleToEndTime = solution.getVehicles().stream()
                .map(TestdataMultiEntityChainRebaseVehicle::getEndTime)
                .toList();
        var visitToEndServiceTime = solution.getVisits().stream()
                .map(TestdataMultiEntityChainRebaseVisit::getEndServiceTime)
                .toList();

        SolutionManager.updateShadowVariables(solution);

        assertThat(solution.getVehicles().stream().map(TestdataMultiEntityChainRebaseVehicle::getEndTime))
                .containsExactlyElementsOf(vehicleToEndTime);
        assertThat(solution.getVisits().stream().map(TestdataMultiEntityChainRebaseVisit::getEndServiceTime))
                .containsExactlyElementsOf(visitToEndServiceTime);
    }
}
