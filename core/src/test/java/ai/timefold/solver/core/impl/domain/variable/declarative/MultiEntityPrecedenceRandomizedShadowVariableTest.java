package ai.timefold.solver.core.impl.domain.variable.declarative;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.timefold.solver.core.preview.api.domain.metamodel.PlanningListVariableMetaModel;
import ai.timefold.solver.core.preview.api.move.Move;
import ai.timefold.solver.core.preview.api.move.builtin.Moves;
import ai.timefold.solver.core.preview.api.move.test.MoveTestContext;
import ai.timefold.solver.core.preview.api.move.test.MoveTester;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence.TestdataPrecedenceSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence.TestdataPrecedenceVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence.TestdataPrecedenceVisit;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence_group.TestdataGroupPrecedenceSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence_group.TestdataGroupPrecedenceVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence_group.TestdataGroupPrecedenceVisit;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Applies random move sequences on {@link MultiEntityPrecedenceVariableReferenceGraph}
 * and asserts after every move that all shadow variables and consistency flags
 * equal a from-scratch recomputation, which uses the arbitrary graph.
 * The moves routinely create and break dependency cycles.
 */
class MultiEntityPrecedenceRandomizedShadowVariableTest {

    private static final int STEP_COUNT = 100;

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, 2, 3, 4 })
    void singlePredecessorModel(long seed) {
        var vehicles = new ArrayList<TestdataPrecedenceVehicle>();
        for (var i = 0; i < 4; i++) {
            vehicles.add(new TestdataPrecedenceVehicle("vehicle" + i, i));
        }
        // Diamond: vehicle0 -> vehicle1 -> vehicle2 <- vehicle3.
        vehicles.get(1).getPreviousVehicles().add(vehicles.get(0));
        vehicles.get(2).getPreviousVehicles().add(vehicles.get(1));
        vehicles.get(2).getPreviousVehicles().add(vehicles.get(3));
        var visits = new ArrayList<TestdataPrecedenceVisit>();
        for (var i = 0; i < 10; i++) {
            visits.add(new TestdataPrecedenceVisit("visit" + i, 1 + (i % 3)));
        }
        // Precedence chains: 0 -> 3 -> 5 -> 9 and 2 -> 7; visit8 also requires visit3.
        visits.get(3).setRequiredPredecessor(visits.get(0));
        visits.get(5).setRequiredPredecessor(visits.get(3));
        visits.get(9).setRequiredPredecessor(visits.get(5));
        visits.get(7).setRequiredPredecessor(visits.get(2));
        visits.get(8).setRequiredPredecessor(visits.get(3));
        var solution = new TestdataPrecedenceSolution();
        solution.setVehicles(vehicles);
        solution.setVisits(visits);
        assertPrecedenceStructure(TestdataPrecedenceSolution.buildSolutionDescriptor(), vehicles, visits);

        var solutionMetaModel = TestdataPrecedenceSolution.buildMetaModel();
        var listVariableMetaModel = solutionMetaModel.genuineEntity(TestdataPrecedenceVehicle.class)
                .listVariable("visits", TestdataPrecedenceVisit.class);
        var context = MoveTester.build(solutionMetaModel).using(solution);

        runRandomizedMoves(new Random(seed), context, listVariableMetaModel, vehicles, visits,
                TestdataPrecedenceVisit::getVehicle, TestdataPrecedenceVehicle::getVisits,
                () -> {
                    var vehicleToPreviousEndTime =
                            vehicles.stream().map(TestdataPrecedenceVehicle::getPreviousEndTime).toList();
                    var vehicleToEndTime = vehicles.stream().map(TestdataPrecedenceVehicle::getEndTime).toList();
                    var vehicleToInconsistent =
                            vehicles.stream().map(TestdataPrecedenceVehicle::isInconsistent).toList();
                    var visitToEndServiceTime =
                            visits.stream().map(TestdataPrecedenceVisit::getEndServiceTime).toList();
                    var visitToInconsistent = visits.stream().map(TestdataPrecedenceVisit::isInconsistent).toList();
                    SolutionManager.updateShadowVariables(solution);
                    assertThat(vehicles.stream().map(TestdataPrecedenceVehicle::getPreviousEndTime))
                            .containsExactlyElementsOf(vehicleToPreviousEndTime);
                    assertThat(vehicles.stream().map(TestdataPrecedenceVehicle::getEndTime))
                            .containsExactlyElementsOf(vehicleToEndTime);
                    assertThat(vehicles.stream().map(TestdataPrecedenceVehicle::isInconsistent))
                            .containsExactlyElementsOf(vehicleToInconsistent);
                    assertThat(visits.stream().map(TestdataPrecedenceVisit::getEndServiceTime))
                            .containsExactlyElementsOf(visitToEndServiceTime);
                    assertThat(visits.stream().map(TestdataPrecedenceVisit::isInconsistent))
                            .containsExactlyElementsOf(visitToInconsistent);
                });
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, 2, 3, 4 })
    void multiPredecessorModel(long seed) {
        var vehicles = new ArrayList<TestdataGroupPrecedenceVehicle>();
        for (var i = 0; i < 3; i++) {
            vehicles.add(new TestdataGroupPrecedenceVehicle("vehicle" + i, i));
        }
        var visits = new ArrayList<TestdataGroupPrecedenceVisit>();
        for (var i = 0; i < 10; i++) {
            visits.add(new TestdataGroupPrecedenceVisit("visit" + i, 1 + (i % 3)));
        }
        // A precedence diamond: 6 requires 0 and 3, 9 requires 6 and 2.
        visits.get(6).getRequiredPredecessorList().add(visits.get(0));
        visits.get(6).getRequiredPredecessorList().add(visits.get(3));
        visits.get(9).getRequiredPredecessorList().add(visits.get(6));
        visits.get(9).getRequiredPredecessorList().add(visits.get(2));
        var solution = new TestdataGroupPrecedenceSolution();
        solution.setVehicles(vehicles);
        solution.setVisits(visits);
        assertPrecedenceStructure(TestdataGroupPrecedenceSolution.buildSolutionDescriptor(), vehicles, visits);

        var solutionMetaModel = TestdataGroupPrecedenceSolution.buildMetaModel();
        var listVariableMetaModel = solutionMetaModel.genuineEntity(TestdataGroupPrecedenceVehicle.class)
                .listVariable("visits", TestdataGroupPrecedenceVisit.class);
        var context = MoveTester.build(solutionMetaModel).using(solution);

        runRandomizedMoves(new Random(seed), context, listVariableMetaModel, vehicles, visits,
                TestdataGroupPrecedenceVisit::getVehicle, TestdataGroupPrecedenceVehicle::getVisits,
                () -> {
                    var vehicleToEndTime = vehicles.stream().map(TestdataGroupPrecedenceVehicle::getEndTime).toList();
                    var vehicleToInconsistent =
                            vehicles.stream().map(TestdataGroupPrecedenceVehicle::isInconsistent).toList();
                    var visitToEndServiceTime =
                            visits.stream().map(TestdataGroupPrecedenceVisit::getEndServiceTime).toList();
                    var visitToInconsistent =
                            visits.stream().map(TestdataGroupPrecedenceVisit::isInconsistent).toList();
                    SolutionManager.updateShadowVariables(solution);
                    assertThat(vehicles.stream().map(TestdataGroupPrecedenceVehicle::getEndTime))
                            .containsExactlyElementsOf(vehicleToEndTime);
                    assertThat(vehicles.stream().map(TestdataGroupPrecedenceVehicle::isInconsistent))
                            .containsExactlyElementsOf(vehicleToInconsistent);
                    assertThat(visits.stream().map(TestdataGroupPrecedenceVisit::getEndServiceTime))
                            .containsExactlyElementsOf(visitToEndServiceTime);
                    assertThat(visits.stream().map(TestdataGroupPrecedenceVisit::isInconsistent))
                            .containsExactlyElementsOf(visitToInconsistent);
                });
    }

    private static void assertPrecedenceStructure(SolutionDescriptor<?> solutionDescriptor,
            List<?> vehicles, List<?> visits) {
        var entities = new ArrayList<Object>(vehicles);
        entities.addAll(visits);
        assertThat(GraphStructure.determineGraphStructure(solutionDescriptor, entities.toArray()))
                .hasFieldOrPropertyWithValue("structure",
                        GraphStructure.MULTI_ENTITY_SINGLE_DIRECTIONAL_PARENT_WITH_PRECEDENCE);
    }

    private static <Solution_, Vehicle_, Visit_> void runRandomizedMoves(
            Random random,
            MoveTestContext<Solution_> context,
            PlanningListVariableMetaModel<Solution_, Vehicle_, Visit_> listVariableMetaModel,
            List<Vehicle_> vehicles,
            List<Visit_> visits,
            Function<Visit_, ?> visitToVehicle,
            Function<Vehicle_, List<Visit_>> vehicleToVisits,
            Runnable fixedPointAssertion) {
        for (var step = 0; step < STEP_COUNT; step++) {
            var move = generateMove(random, listVariableMetaModel, vehicles, visits, visitToVehicle, vehicleToVisits);
            if (move == null) {
                continue;
            }
            context.execute(move);
            fixedPointAssertion.run();
        }
    }

    private static <Solution_, Vehicle_, Visit_> Move<Solution_> generateMove(
            Random random,
            PlanningListVariableMetaModel<Solution_, Vehicle_, Visit_> listVariableMetaModel,
            List<Vehicle_> vehicles,
            List<Visit_> visits,
            Function<Visit_, ?> visitToVehicle,
            Function<Vehicle_, List<Visit_>> vehicleToVisits) {
        switch (random.nextInt(4)) {
            case 0 -> { // Assign an unassigned visit.
                var unassignedList = visits.stream().filter(visit -> visitToVehicle.apply(visit) == null).toList();
                if (unassignedList.isEmpty()) {
                    return null;
                }
                var visit = unassignedList.get(random.nextInt(unassignedList.size()));
                var vehicle = vehicles.get(random.nextInt(vehicles.size()));
                var index = random.nextInt(vehicleToVisits.apply(vehicle).size() + 1);
                return Moves.assign(listVariableMetaModel, visit, vehicle, index);
            }
            case 1 -> { // Unassign a visit.
                var vehicle = pickVehicleWithVisits(random, vehicles, vehicleToVisits);
                if (vehicle == null) {
                    return null;
                }
                return Moves.unassign(listVariableMetaModel, vehicle,
                        random.nextInt(vehicleToVisits.apply(vehicle).size()));
            }
            case 2 -> { // Move a visit to another position.
                var sourceVehicle = pickVehicleWithVisits(random, vehicles, vehicleToVisits);
                if (sourceVehicle == null) {
                    return null;
                }
                var sourceIndex = random.nextInt(vehicleToVisits.apply(sourceVehicle).size());
                var destinationVehicle = vehicles.get(random.nextInt(vehicles.size()));
                var destinationSize = vehicleToVisits.apply(destinationVehicle).size()
                        - (destinationVehicle == sourceVehicle ? 1 : 0);
                var destinationIndex = random.nextInt(destinationSize + 1);
                if (destinationVehicle == sourceVehicle && destinationIndex == sourceIndex) {
                    return null;
                }
                return Moves.change(listVariableMetaModel, sourceVehicle, sourceIndex, destinationVehicle,
                        destinationIndex);
            }
            default -> { // Swap two visits.
                var leftVehicle = pickVehicleWithVisits(random, vehicles, vehicleToVisits);
                var rightVehicle = pickVehicleWithVisits(random, vehicles, vehicleToVisits);
                if (leftVehicle == null || rightVehicle == null) {
                    return null;
                }
                var leftIndex = random.nextInt(vehicleToVisits.apply(leftVehicle).size());
                var rightIndex = random.nextInt(vehicleToVisits.apply(rightVehicle).size());
                if (leftVehicle == rightVehicle && leftIndex == rightIndex) {
                    return null;
                }
                return Moves.swap(listVariableMetaModel, leftVehicle, leftIndex, rightVehicle, rightIndex);
            }
        }
    }

    private static <Vehicle_, Visit_> Vehicle_ pickVehicleWithVisits(Random random, List<Vehicle_> vehicles,
            Function<Vehicle_, List<Visit_>> vehicleToVisits) {
        var nonEmptyVehicles = vehicles.stream()
                .filter(vehicle -> !vehicleToVisits.apply(vehicle).isEmpty())
                .toList();
        if (nonEmptyVehicles.isEmpty()) {
            return null;
        }
        return nonEmptyVehicles.get(random.nextInt(nonEmptyVehicles.size()));
    }
}
