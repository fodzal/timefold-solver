package ai.timefold.solver.core.impl.domain.variable.declarative;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import ai.timefold.solver.core.preview.api.move.builtin.Moves;
import ai.timefold.solver.core.preview.api.move.test.MoveTester;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_fallback.TestdataWatchedVisitsSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_fallback.TestdataWatchedVisitsVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_fallback.TestdataWatchedVisitsVisit;

import org.junit.jupiter.api.Test;

/**
 * Verifies that a model rejected by {@link GraphStructure#MULTI_ENTITY_SINGLE_DIRECTIONAL_PARENT}
 * stays fresh on the fallback graph.
 */
class MultiEntitySingleDirectionalParentFallbackShadowVariableTest {

    @Test
    void watchedVisitsStayFreshOnFallbackGraph() {
        var x1 = new TestdataWatchedVisitsVisit("x1", 1);
        var vehicleA = new TestdataWatchedVisitsVehicle("A", 0);
        var watcher = new TestdataWatchedVisitsVehicle("W", 0);
        watcher.getWatchedVisits().add(x1);

        var solution = new TestdataWatchedVisitsSolution();
        solution.setVehicles(List.of(vehicleA, watcher));
        solution.setVisits(List.of(x1));

        var solutionMetaModel = TestdataWatchedVisitsSolution.buildMetaModel();
        var listVariableMetaModel = solutionMetaModel.genuineEntity(TestdataWatchedVisitsVehicle.class)
                .listVariable("visits", TestdataWatchedVisitsVisit.class);

        var context = MoveTester.build(solutionMetaModel).using(solution);
        assertThat(watcher.getWatchedEndTime()).isNull();

        // Assigning x1 to another vehicle must update the watcher.
        context.execute(Moves.assign(listVariableMetaModel, x1, vehicleA, 0));
        assertThat(x1.getEndServiceTime()).isEqualTo(1);
        assertThat(watcher.getWatchedEndTime()).isEqualTo(1);

        context.execute(Moves.unassign(listVariableMetaModel, vehicleA, 0));
        assertThat(x1.getEndServiceTime()).isNull();
        assertThat(watcher.getWatchedEndTime()).isNull();
    }
}
