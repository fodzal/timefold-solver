package ai.timefold.solver.core.impl.domain.variable.declarative;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence.TestdataPrecedenceConstraintProvider;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence.TestdataPrecedenceSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence.TestdataPrecedenceVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence.TestdataPrecedenceVisit;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence_group.TestdataGroupPrecedenceSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence_group.TestdataGroupPrecedenceVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence_group.TestdataGroupPrecedenceVisit;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link MultiEntityPrecedenceVariableReferenceGraph} on models where
 * a visit may only start after another visit ends, wherever that visit is assigned.
 */
class MultiEntityPrecedenceShadowVariableTest {

    @Test
    void precedencePropagatesAcrossVehicles() {
        var a0 = new TestdataPrecedenceVisit("a0", 5); // Initially unassigned.
        var a1 = new TestdataPrecedenceVisit("a1", 1);
        var a2 = new TestdataPrecedenceVisit("a2", 1);
        var b1 = new TestdataPrecedenceVisit("b1", 1);
        var b2 = new TestdataPrecedenceVisit("b2", 1);
        b2.setRequiredPredecessor(a2);

        var vehicleA = new TestdataPrecedenceVehicle("A", 0);
        var vehicleB = new TestdataPrecedenceVehicle("B", 0);
        vehicleA.setVisits(new ArrayList<>(List.of(a1, a2)));
        vehicleB.setVisits(new ArrayList<>(List.of(b1, b2)));

        var solution = new TestdataPrecedenceSolution();
        solution.setVehicles(List.of(vehicleA, vehicleB));
        solution.setVisits(List.of(a0, a1, a2, b1, b2));

        var solutionMetaModel = TestdataPrecedenceSolution.buildMetaModel();
        var listVariableMetaModel = solutionMetaModel.genuineEntity(TestdataPrecedenceVehicle.class)
                .listVariable("visits", TestdataPrecedenceVisit.class);

        var context = MoveTester.build(solutionMetaModel).using(solution);
        assertThat(a2.getEndServiceTime()).isEqualTo(2);
        assertThat(b1.getEndServiceTime()).isEqualTo(1);
        assertThat(b2.getEndServiceTime()).isEqualTo(3); // max(1, 2) + 1
        assertThat(vehicleB.getEndTime()).isEqualTo(3);
        assertThat(b2.isInconsistent()).isFalse();

        // Prepending a0 to A shifts a2, whose precedence shifts b2 on the other vehicle.
        context.execute(Moves.assign(listVariableMetaModel, a0, vehicleA, 0));
        assertThat(a2.getEndServiceTime()).isEqualTo(7);
        assertThat(b1.getEndServiceTime()).isEqualTo(1);
        assertThat(b2.getEndServiceTime()).isEqualTo(8); // max(1, 7) + 1
        assertThat(vehicleB.getEndTime()).isEqualTo(8);
        assertShadowsAreAtFixedPoint(solution);
    }

    @Test
    void sameVehicleWrongOrderBecomesInconsistent() {
        var a = new TestdataPrecedenceVisit("a", 1);
        var b = new TestdataPrecedenceVisit("b", 1);
        b.setRequiredPredecessor(a);

        var vehicle = new TestdataPrecedenceVehicle("A", 0);
        // b is scheduled before its required predecessor a: a dependency cycle.
        vehicle.setVisits(new ArrayList<>(List.of(b, a)));

        var solution = new TestdataPrecedenceSolution();
        solution.setVehicles(List.of(vehicle));
        solution.setVisits(List.of(a, b));

        var solutionMetaModel = TestdataPrecedenceSolution.buildMetaModel();
        var listVariableMetaModel = solutionMetaModel.genuineEntity(TestdataPrecedenceVehicle.class)
                .listVariable("visits", TestdataPrecedenceVisit.class);

        var context = MoveTester.build(solutionMetaModel).using(solution);
        assertThat(a.isInconsistent()).isTrue();
        assertThat(b.isInconsistent()).isTrue();
        assertThat(a.getEndServiceTime()).isNull();
        assertThat(b.getEndServiceTime()).isNull();
        assertThat(vehicle.isInconsistent()).isTrue();
        assertThat(vehicle.getEndTime()).isNull();
        assertThat(vehicle.getPreviousEndTime()).isEqualTo(0);
        assertShadowsAreAtFixedPoint(solution);

        // Swapping restores the precedence order and breaks the cycle.
        context.execute(Moves.swap(listVariableMetaModel, vehicle, 0, vehicle, 1));
        assertThat(a.isInconsistent()).isFalse();
        assertThat(b.isInconsistent()).isFalse();
        assertThat(a.getEndServiceTime()).isEqualTo(1);
        assertThat(b.getEndServiceTime()).isEqualTo(2);
        assertThat(vehicle.isInconsistent()).isFalse();
        assertThat(vehicle.getEndTime()).isEqualTo(2);
        assertShadowsAreAtFixedPoint(solution);
    }

    @Test
    void crossVehicleCycleBecomesInconsistent() {
        var a = new TestdataPrecedenceVisit("a", 1);
        var b = new TestdataPrecedenceVisit("b", 1);
        var c = new TestdataPrecedenceVisit("c", 1);
        var d = new TestdataPrecedenceVisit("d", 1);
        b.setRequiredPredecessor(a);
        c.setRequiredPredecessor(d);

        var vehicleA = new TestdataPrecedenceVehicle("A", 0);
        var vehicleB = new TestdataPrecedenceVehicle("B", 0);
        // Cycle: a -> b (precedence), b -> d (chain), d -> c (precedence), c -> a (chain).
        vehicleA.setVisits(new ArrayList<>(List.of(c, a)));
        vehicleB.setVisits(new ArrayList<>(List.of(b, d)));

        var solution = new TestdataPrecedenceSolution();
        solution.setVehicles(List.of(vehicleA, vehicleB));
        solution.setVisits(List.of(a, b, c, d));

        var solutionMetaModel = TestdataPrecedenceSolution.buildMetaModel();
        var listVariableMetaModel = solutionMetaModel.genuineEntity(TestdataPrecedenceVehicle.class)
                .listVariable("visits", TestdataPrecedenceVisit.class);

        var context = MoveTester.build(solutionMetaModel).using(solution);
        for (var visit : List.of(a, b, c, d)) {
            assertThat(visit.isInconsistent()).as("%s should be inconsistent", visit).isTrue();
            assertThat(visit.getEndServiceTime()).isNull();
        }
        assertThat(vehicleA.isInconsistent()).isTrue();
        assertThat(vehicleB.isInconsistent()).isTrue();
        assertShadowsAreAtFixedPoint(solution);

        // Unassigning d breaks the cycle: c depends on an unassigned visit,
        // so its value is null, but nothing is inconsistent anymore.
        context.execute(Moves.unassign(listVariableMetaModel, vehicleB, 1));
        for (var visit : List.of(a, b, c, d)) {
            assertThat(visit.isInconsistent()).as("%s should be consistent", visit).isFalse();
        }
        assertThat(d.getEndServiceTime()).isNull();
        assertThat(c.getEndServiceTime()).isNull();
        assertThat(a.getEndServiceTime()).isNull(); // Depends on c through the chain.
        assertThat(b.getEndServiceTime()).isNull(); // Depends on a through the precedence.
        assertThat(vehicleA.isInconsistent()).isFalse();
        assertThat(vehicleB.isInconsistent()).isFalse();
        assertShadowsAreAtFixedPoint(solution);
    }

    @Test
    void vehicleDagMediatedCycleBecomesInconsistent() {
        var a = new TestdataPrecedenceVisit("a", 1);
        var b = new TestdataPrecedenceVisit("b", 1);
        // Cycle: b -> a (precedence), a -> A.endTime (elements),
        // A.endTime -> B.previousEndTime (vehicle DAG), B.previousEndTime -> b (inverse).
        a.setRequiredPredecessor(b);

        var vehicleA = new TestdataPrecedenceVehicle("A", 0);
        var vehicleB = new TestdataPrecedenceVehicle("B", 0);
        vehicleB.getPreviousVehicles().add(vehicleA);
        vehicleA.setVisits(new ArrayList<>(List.of(a)));
        vehicleB.setVisits(new ArrayList<>(List.of(b)));

        var solution = new TestdataPrecedenceSolution();
        solution.setVehicles(List.of(vehicleA, vehicleB));
        solution.setVisits(List.of(a, b));

        var solutionMetaModel = TestdataPrecedenceSolution.buildMetaModel();
        var listVariableMetaModel = solutionMetaModel.genuineEntity(TestdataPrecedenceVehicle.class)
                .listVariable("visits", TestdataPrecedenceVisit.class);

        var context = MoveTester.build(solutionMetaModel).using(solution);
        assertThat(a.isInconsistent()).isTrue();
        assertThat(b.isInconsistent()).isTrue();
        assertThat(a.getEndServiceTime()).isNull();
        assertThat(b.getEndServiceTime()).isNull();
        assertThat(vehicleA.isInconsistent()).isTrue();
        assertThat(vehicleA.getEndTime()).isNull();
        assertThat(vehicleA.getPreviousEndTime()).isEqualTo(0); // Not downstream of the cycle.
        assertThat(vehicleB.isInconsistent()).isTrue();
        assertThat(vehicleB.getPreviousEndTime()).isNull();
        assertThat(vehicleB.getEndTime()).isNull();
        assertShadowsAreAtFixedPoint(solution);

        // Unassigning a breaks the cycle.
        context.execute(Moves.unassign(listVariableMetaModel, vehicleA, 0));
        assertThat(a.isInconsistent()).isFalse();
        assertThat(b.isInconsistent()).isFalse();
        assertThat(a.getEndServiceTime()).isNull(); // Unassigned.
        assertThat(vehicleA.isInconsistent()).isFalse();
        assertThat(vehicleA.getEndTime()).isEqualTo(0);
        assertThat(vehicleB.getPreviousEndTime()).isEqualTo(0);
        assertThat(b.getEndServiceTime()).isEqualTo(1);
        assertThat(vehicleB.isInconsistent()).isFalse();
        assertThat(vehicleB.getEndTime()).isEqualTo(1);
        assertShadowsAreAtFixedPoint(solution);
    }

    @Test
    void unassignedPredecessorKeepsConsistency() {
        var a = new TestdataPrecedenceVisit("a", 1); // Unassigned.
        var b = new TestdataPrecedenceVisit("b", 1);
        b.setRequiredPredecessor(a);

        var vehicle = new TestdataPrecedenceVehicle("A", 0);
        vehicle.setVisits(new ArrayList<>(List.of(b)));

        var solution = new TestdataPrecedenceSolution();
        solution.setVehicles(List.of(vehicle));
        solution.setVisits(List.of(a, b));

        MoveTester.build(TestdataPrecedenceSolution.buildMetaModel()).using(solution);
        assertThat(a.isInconsistent()).isFalse();
        assertThat(b.isInconsistent()).isFalse();
        assertThat(b.getEndServiceTime()).isNull(); // The predecessor is unassigned.
        assertThat(vehicle.isInconsistent()).isFalse();
        assertShadowsAreAtFixedPoint(solution);
    }

    @Test
    void groupPrecedenceWithMultiplePredecessors() {
        var a = new TestdataGroupPrecedenceVisit("a", 1);
        var b = new TestdataGroupPrecedenceVisit("b", 2);
        var c = new TestdataGroupPrecedenceVisit("c", 1);
        c.getRequiredPredecessorList().add(a);
        c.getRequiredPredecessorList().add(b);

        var vehicleA = new TestdataGroupPrecedenceVehicle("A", 0);
        var vehicleB = new TestdataGroupPrecedenceVehicle("B", 0);
        vehicleA.setVisits(new ArrayList<>(List.of(a, c)));
        vehicleB.setVisits(new ArrayList<>(List.of(b)));

        var solution = new TestdataGroupPrecedenceSolution();
        solution.setVehicles(List.of(vehicleA, vehicleB));
        solution.setVisits(List.of(a, b, c));

        var solutionMetaModel = TestdataGroupPrecedenceSolution.buildMetaModel();
        var listVariableMetaModel = solutionMetaModel.genuineEntity(TestdataGroupPrecedenceVehicle.class)
                .listVariable("visits", TestdataGroupPrecedenceVisit.class);

        var context = MoveTester.build(solutionMetaModel).using(solution);
        assertThat(a.getEndServiceTime()).isEqualTo(1);
        assertThat(b.getEndServiceTime()).isEqualTo(2);
        assertThat(c.getEndServiceTime()).isEqualTo(3); // max(1, 1, 2) + 1
        assertThat(c.isInconsistent()).isFalse();

        // Moving c before a creates a cycle with one of its two predecessors.
        context.execute(Moves.change(listVariableMetaModel, vehicleA, 1, vehicleA, 0));
        assertThat(a.isInconsistent()).isTrue();
        assertThat(c.isInconsistent()).isTrue();
        assertThat(b.isInconsistent()).isFalse();
        assertThat(b.getEndServiceTime()).isEqualTo(2);
        assertThat(vehicleA.isInconsistent()).isTrue();
        assertThat(vehicleB.isInconsistent()).isFalse();

        // Moving it back restores consistency.
        context.execute(Moves.change(listVariableMetaModel, vehicleA, 0, vehicleA, 1));
        assertThat(a.isInconsistent()).isFalse();
        assertThat(c.isInconsistent()).isFalse();
        assertThat(c.getEndServiceTime()).isEqualTo(3);
        assertThat(vehicleA.isInconsistent()).isFalse();
    }

    @Test
    void staticPrecedenceCycleFailsFast() {
        var a = new TestdataPrecedenceVisit("a", 1);
        var b = new TestdataPrecedenceVisit("b", 1);
        a.setRequiredPredecessor(b);
        b.setRequiredPredecessor(a);

        var vehicle = new TestdataPrecedenceVehicle("A", 0);
        vehicle.setVisits(new ArrayList<>(List.of(a, b)));

        var solution = new TestdataPrecedenceSolution();
        solution.setVehicles(List.of(vehicle));
        solution.setVisits(List.of(a, b));

        assertThatCode(() -> MoveTester.build(TestdataPrecedenceSolution.buildMetaModel()).using(solution))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixed dependency loops");
    }

    @Test
    void solveWithFullAssert() {
        var vehicles = new ArrayList<TestdataPrecedenceVehicle>();
        for (var i = 0; i < 2; i++) {
            vehicles.add(new TestdataPrecedenceVehicle("vehicle" + i, i));
        }
        vehicles.get(1).getPreviousVehicles().add(vehicles.get(0));
        var visits = new ArrayList<TestdataPrecedenceVisit>();
        for (var i = 0; i < 6; i++) {
            visits.add(new TestdataPrecedenceVisit("visit" + i, 1 + (i % 3)));
        }
        visits.get(3).setRequiredPredecessor(visits.get(0));
        visits.get(5).setRequiredPredecessor(visits.get(3));
        visits.get(4).setRequiredPredecessor(visits.get(1));
        var problem = new TestdataPrecedenceSolution();
        problem.setVehicles(vehicles);
        problem.setVisits(visits);

        var solverConfig = new SolverConfig()
                .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
                .withSolutionClass(TestdataPrecedenceSolution.class)
                .withEntityClasses(TestdataPrecedenceVehicle.class, TestdataPrecedenceVisit.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(TestdataPrecedenceConstraintProvider.class))
                .withTerminationConfig(new TerminationConfig().withMoveCountLimit(1000L));

        var solution = SolverFactory.<TestdataPrecedenceSolution> create(solverConfig).buildSolver()
                .solve(problem);

        assertThat(solution).isNotNull();
        assertShadowsAreAtFixedPoint(solution);
    }

    /**
     * The incrementally maintained shadow variables and consistency flags
     * must equal a from-scratch recomputation, which uses the arbitrary graph.
     */
    private static void assertShadowsAreAtFixedPoint(TestdataPrecedenceSolution solution) {
        var vehicleToPreviousEndTime = solution.getVehicles().stream()
                .map(TestdataPrecedenceVehicle::getPreviousEndTime)
                .toList();
        var vehicleToEndTime = solution.getVehicles().stream()
                .map(TestdataPrecedenceVehicle::getEndTime)
                .toList();
        var vehicleToInconsistent = solution.getVehicles().stream()
                .map(TestdataPrecedenceVehicle::isInconsistent)
                .toList();
        var visitToEndServiceTime = solution.getVisits().stream()
                .map(TestdataPrecedenceVisit::getEndServiceTime)
                .toList();
        var visitToInconsistent = solution.getVisits().stream()
                .map(TestdataPrecedenceVisit::isInconsistent)
                .toList();

        SolutionManager.updateShadowVariables(solution);

        assertThat(solution.getVehicles().stream().map(TestdataPrecedenceVehicle::getPreviousEndTime))
                .containsExactlyElementsOf(vehicleToPreviousEndTime);
        assertThat(solution.getVehicles().stream().map(TestdataPrecedenceVehicle::getEndTime))
                .containsExactlyElementsOf(vehicleToEndTime);
        assertThat(solution.getVehicles().stream().map(TestdataPrecedenceVehicle::isInconsistent))
                .containsExactlyElementsOf(vehicleToInconsistent);
        assertThat(solution.getVisits().stream().map(TestdataPrecedenceVisit::getEndServiceTime))
                .containsExactlyElementsOf(visitToEndServiceTime);
        assertThat(solution.getVisits().stream().map(TestdataPrecedenceVisit::isInconsistent))
                .containsExactlyElementsOf(visitToInconsistent);
    }
}
