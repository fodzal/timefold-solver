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
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain.TestdataMultiEntityChainSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain.TestdataMultiEntityChainVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain.TestdataMultiEntityChainVisit;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_next.TestdataMultiEntityChainNextSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_next.TestdataMultiEntityChainNextVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_next.TestdataMultiEntityChainNextVisit;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_rebase.TestdataMultiEntityChainRebaseSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_rebase.TestdataMultiEntityChainRebaseVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_rebase.TestdataMultiEntityChainRebaseVisit;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Applies random move sequences on {@link MultiEntitySingleDirectionalParentVariableReferenceGraph}
 * and asserts after every move that all shadow variables equal a from-scratch recomputation,
 * which uses the arbitrary graph.
 */
class MultiEntitySingleDirectionalParentRandomizedShadowVariableTest {

    private static final int STEP_COUNT = 100;

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, 2 })
    void previousModel(long seed) {
        var vehicles = new ArrayList<TestdataMultiEntityChainVehicle>();
        for (var i = 0; i < 4; i++) {
            vehicles.add(new TestdataMultiEntityChainVehicle("vehicle" + i, i));
        }
        // Diamond: vehicle0 -> vehicle1 -> vehicle2 <- vehicle3.
        vehicles.get(1).getPreviousVehicles().add(vehicles.get(0));
        vehicles.get(2).getPreviousVehicles().add(vehicles.get(1));
        vehicles.get(2).getPreviousVehicles().add(vehicles.get(3));
        var visits = new ArrayList<TestdataMultiEntityChainVisit>();
        for (var i = 0; i < 10; i++) {
            visits.add(new TestdataMultiEntityChainVisit("visit" + i, 1 + (i % 3)));
        }
        var solution = new TestdataMultiEntityChainSolution();
        solution.setVehicles(vehicles);
        solution.setVisits(visits);
        assertMultiEntityStructure(TestdataMultiEntityChainSolution.buildSolutionDescriptor(), vehicles, visits);

        var solutionMetaModel = TestdataMultiEntityChainSolution.buildMetaModel();
        var listVariableMetaModel = solutionMetaModel.genuineEntity(TestdataMultiEntityChainVehicle.class)
                .listVariable("visits", TestdataMultiEntityChainVisit.class);
        var context = MoveTester.build(solutionMetaModel).using(solution);

        runRandomizedMoves(new Random(seed), context, listVariableMetaModel, vehicles, visits,
                TestdataMultiEntityChainVisit::getVehicle, TestdataMultiEntityChainVehicle::getVisits,
                () -> {
                    var vehicleToEndTime = vehicles.stream().map(TestdataMultiEntityChainVehicle::getEndTime).toList();
                    var visitToEndServiceTime =
                            visits.stream().map(TestdataMultiEntityChainVisit::getEndServiceTime).toList();
                    SolutionManager.updateShadowVariables(solution);
                    assertThat(vehicles.stream().map(TestdataMultiEntityChainVehicle::getEndTime))
                            .containsExactlyElementsOf(vehicleToEndTime);
                    assertThat(visits.stream().map(TestdataMultiEntityChainVisit::getEndServiceTime))
                            .containsExactlyElementsOf(visitToEndServiceTime);
                });
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, 2 })
    void nextModel(long seed) {
        var vehicles = new ArrayList<TestdataMultiEntityChainNextVehicle>();
        for (var i = 0; i < 4; i++) {
            vehicles.add(new TestdataMultiEntityChainNextVehicle("vehicle" + i, 100 + i));
        }
        // Diamond: vehicle0 <- vehicle1 <- vehicle2 -> vehicle3.
        vehicles.get(0).getNextVehicles().add(vehicles.get(1));
        vehicles.get(1).getNextVehicles().add(vehicles.get(2));
        vehicles.get(3).getNextVehicles().add(vehicles.get(2));
        var visits = new ArrayList<TestdataMultiEntityChainNextVisit>();
        for (var i = 0; i < 10; i++) {
            visits.add(new TestdataMultiEntityChainNextVisit("visit" + i, 1 + (i % 3)));
        }
        var solution = new TestdataMultiEntityChainNextSolution();
        solution.setVehicles(vehicles);
        solution.setVisits(visits);
        assertMultiEntityStructure(TestdataMultiEntityChainNextSolution.buildSolutionDescriptor(), vehicles, visits);

        var solutionMetaModel = TestdataMultiEntityChainNextSolution.buildMetaModel();
        var listVariableMetaModel = solutionMetaModel.genuineEntity(TestdataMultiEntityChainNextVehicle.class)
                .listVariable("visits", TestdataMultiEntityChainNextVisit.class);
        var context = MoveTester.build(solutionMetaModel).using(solution);

        runRandomizedMoves(new Random(seed), context, listVariableMetaModel, vehicles, visits,
                TestdataMultiEntityChainNextVisit::getVehicle, TestdataMultiEntityChainNextVehicle::getVisits,
                () -> {
                    var vehicleToStartTime =
                            vehicles.stream().map(TestdataMultiEntityChainNextVehicle::getStartTime).toList();
                    var visitToLatestStartTime =
                            visits.stream().map(TestdataMultiEntityChainNextVisit::getLatestStartTime).toList();
                    SolutionManager.updateShadowVariables(solution);
                    assertThat(vehicles.stream().map(TestdataMultiEntityChainNextVehicle::getStartTime))
                            .containsExactlyElementsOf(vehicleToStartTime);
                    assertThat(visits.stream().map(TestdataMultiEntityChainNextVisit::getLatestStartTime))
                            .containsExactlyElementsOf(visitToLatestStartTime);
                });
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, 2 })
    void rebaseModel(long seed) {
        var vehicles = new ArrayList<TestdataMultiEntityChainRebaseVehicle>();
        for (var i = 0; i < 3; i++) {
            vehicles.add(new TestdataMultiEntityChainRebaseVehicle("vehicle" + i, i));
        }
        // vehicle0 -> vehicle1 -> vehicle2 chain.
        vehicles.get(1).getPreviousVehicles().add(vehicles.get(0));
        vehicles.get(2).getPreviousVehicles().add(vehicles.get(1));
        var visits = new ArrayList<TestdataMultiEntityChainRebaseVisit>();
        for (var i = 0; i < 10; i++) {
            visits.add(new TestdataMultiEntityChainRebaseVisit("visit" + i, 1 + (i % 3), i % 2 == 0));
        }
        var solution = new TestdataMultiEntityChainRebaseSolution();
        solution.setVehicles(vehicles);
        solution.setVisits(visits);
        assertMultiEntityStructure(TestdataMultiEntityChainRebaseSolution.buildSolutionDescriptor(), vehicles, visits);

        var solutionMetaModel = TestdataMultiEntityChainRebaseSolution.buildMetaModel();
        var listVariableMetaModel = solutionMetaModel.genuineEntity(TestdataMultiEntityChainRebaseVehicle.class)
                .listVariable("visits", TestdataMultiEntityChainRebaseVisit.class);
        var context = MoveTester.build(solutionMetaModel).using(solution);

        runRandomizedMoves(new Random(seed), context, listVariableMetaModel, vehicles, visits,
                TestdataMultiEntityChainRebaseVisit::getVehicle, TestdataMultiEntityChainRebaseVehicle::getVisits,
                () -> {
                    var vehicleToEndTime =
                            vehicles.stream().map(TestdataMultiEntityChainRebaseVehicle::getEndTime).toList();
                    var visitToEndServiceTime =
                            visits.stream().map(TestdataMultiEntityChainRebaseVisit::getEndServiceTime).toList();
                    SolutionManager.updateShadowVariables(solution);
                    assertThat(vehicles.stream().map(TestdataMultiEntityChainRebaseVehicle::getEndTime))
                            .containsExactlyElementsOf(vehicleToEndTime);
                    assertThat(visits.stream().map(TestdataMultiEntityChainRebaseVisit::getEndServiceTime))
                            .containsExactlyElementsOf(visitToEndServiceTime);
                });
    }

    private static void assertMultiEntityStructure(SolutionDescriptor<?> solutionDescriptor,
            List<?> vehicles, List<?> visits) {
        var entities = new ArrayList<Object>(vehicles);
        entities.addAll(visits);
        assertThat(GraphStructure.determineGraphStructure(solutionDescriptor, entities.toArray()))
                .hasFieldOrPropertyWithValue("structure", GraphStructure.MULTI_ENTITY_SINGLE_DIRECTIONAL_PARENT);
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
