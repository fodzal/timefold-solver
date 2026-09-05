package ai.timefold.solver.core.impl.domain.variable.declarative;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.preview.api.move.builtin.Moves;
import ai.timefold.solver.core.preview.api.move.test.MoveTester;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_element_sourced.TestdataElementSourcedSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_element_sourced.TestdataElementSourcedVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_element_sourced.TestdataElementSourcedVisit;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link ListElementCascadeVariableReferenceGraph} on a model whose post-chain variable is
 * sourced from the route alone, so nothing declares its dependency on the pre-chain variable the
 * route reads. The cascade adds that edge itself; without it the post-chain variable is computed
 * before the route has been walked, and a dependency loop running through the route is invisible
 * to the graph.
 */
class ListElementCascadeElementSourcedShadowVariableTest {

    @Test
    void changeOnPredecessorVehiclePropagatesThroughTheRoute() {
        var a1 = new TestdataElementSourcedVisit("a1", 2);
        var a2 = new TestdataElementSourcedVisit("a2", 3);
        var b1 = new TestdataElementSourcedVisit("b1", 4);
        var vehicleA = new TestdataElementSourcedVehicle("A", 0);
        var vehicleB = new TestdataElementSourcedVehicle("B", 10);
        vehicleA.setVisits(new ArrayList<>(List.of(a1, a2)));
        vehicleB.setVisits(new ArrayList<>(List.of(b1)));

        var solution = new TestdataElementSourcedSolution();
        solution.setVehicles(List.of(vehicleA, vehicleB));
        solution.setVisits(List.of(a1, a2, b1));

        var solutionMetaModel = TestdataElementSourcedSolution.buildMetaModel();
        var previousVehicleMetaModel = solutionMetaModel.genuineEntity(TestdataElementSourcedVehicle.class)
                .basicVariable("previousVehicle", TestdataElementSourcedVehicle.class);
        var context = MoveTester.build(solutionMetaModel).using(solution);

        // Unchained: A starts at 0 -> [2, 5]; B starts at 10 -> [14].
        assertThat(a1.getEndServiceTime()).isEqualTo(2);
        assertThat(a2.getEndServiceTime()).isEqualTo(5);
        assertThat(vehicleA.getEndTime()).isEqualTo(5);
        assertThat(b1.getEndServiceTime()).isEqualTo(14);

        // Chaining A after B shifts A's whole route, which its endTime must reflect.
        context.execute(Moves.change(previousVehicleMetaModel, vehicleA, vehicleB));
        assertThat(vehicleA.getStartTime()).isEqualTo(14);
        assertThat(a1.getEndServiceTime()).isEqualTo(16);
        assertThat(a2.getEndServiceTime()).isEqualTo(19);
        assertThat(vehicleA.getEndTime()).isEqualTo(19);
    }

    @Test
    void vehicleLoopThroughTheRouteMarksBothRoutesInconsistent() {
        var a1 = new TestdataElementSourcedVisit("a1", 2);
        var a2 = new TestdataElementSourcedVisit("a2", 3);
        var b1 = new TestdataElementSourcedVisit("b1", 4);
        var vehicleA = new TestdataElementSourcedVehicle("A", 0);
        var vehicleB = new TestdataElementSourcedVehicle("B", 10);
        vehicleA.setVisits(new ArrayList<>(List.of(a1, a2)));
        vehicleB.setVisits(new ArrayList<>(List.of(b1)));

        var solution = new TestdataElementSourcedSolution();
        solution.setVehicles(List.of(vehicleA, vehicleB));
        solution.setVisits(List.of(a1, a2, b1));

        var solutionMetaModel = TestdataElementSourcedSolution.buildMetaModel();
        var previousVehicleMetaModel = solutionMetaModel.genuineEntity(TestdataElementSourcedVehicle.class)
                .basicVariable("previousVehicle", TestdataElementSourcedVehicle.class);
        var context = MoveTester.build(solutionMetaModel).using(solution);

        context.execute(Moves.change(previousVehicleMetaModel, vehicleA, vehicleB));
        // Chaining B after A closes a loop that exists only through the two routes: B's startTime
        // feeds A's visits, which feed A's endTime, which feeds B's startTime.
        context.execute(Moves.change(previousVehicleMetaModel, vehicleB, vehicleA));
        assertThat(vehicleA.getInconsistent()).isTrue();
        assertThat(vehicleB.getInconsistent()).isTrue();
        assertThat(a1.getInconsistent()).isTrue();
        assertThat(a2.getInconsistent()).isTrue();
        assertThat(b1.getInconsistent()).isTrue();
        assertThat(a1.getEndServiceTime()).isNull();
        assertThat(b1.getEndServiceTime()).isNull();

        // Breaking the loop brings both routes back.
        context.execute(Moves.change(previousVehicleMetaModel, vehicleB, null));
        assertThat(vehicleA.getInconsistent()).isFalse();
        assertThat(vehicleB.getInconsistent()).isFalse();
        assertThat(b1.getEndServiceTime()).isEqualTo(14);
        assertThat(a1.getEndServiceTime()).isEqualTo(16);
        assertThat(a2.getEndServiceTime()).isEqualTo(19);
        assertThat(vehicleA.getEndTime()).isEqualTo(19);
    }
}
