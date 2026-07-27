package ai.timefold.solver.core.impl.domain.variable.declarative;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import ai.timefold.solver.core.testdomain.shadow.list_element.TestdataMixedListElementEntity;
import ai.timefold.solver.core.testdomain.shadow.list_element.TestdataMixedListElementSolution;
import ai.timefold.solver.core.testdomain.shadow.list_element.TestdataMixedListElementValue;

import org.junit.jupiter.api.Test;

class DefaultVariableReferenceGraphTest {

    /**
     * Counts how often the topological order was recomputed.
     */
    private static final class CountingTopologicalOrderGraph extends DefaultTopologicalOrderGraph {

        private int commitCount = 0;

        private CountingTopologicalOrderGraph(int size) {
            super(size);
        }

        @Override
        public void commitChanges(BitSet changed) {
            commitCount++;
            super.commitChanges(changed);
        }
    }

    @Test
    void recomputesTopologicalOrderOnlyWhenAnEdgeChanged() {
        var solutionDescriptor = TestdataMixedListElementSolution.buildSolutionDescriptor();
        var value1 = new TestdataMixedListElementValue("v1");
        var value2 = new TestdataMixedListElementValue("v2");
        value1.setDuration(2);
        value2.setDuration(3);
        var entity = new TestdataMixedListElementEntity("A");
        entity.setValues(new ArrayList<>(List.of(value1, value2)));

        var solutionMetaModel = solutionDescriptor.getMetaModel();
        var durationMetaModel = solutionMetaModel.genuineEntity(TestdataMixedListElementValue.class)
                .basicVariable("duration", Integer.class);
        var valuesMetaModel = solutionMetaModel.genuineEntity(TestdataMixedListElementEntity.class)
                .listVariable("values", TestdataMixedListElementValue.class);

        // The graph creates exactly one topological order graph, which we need a handle on.
        var topologicalOrderGraphList = new ArrayList<CountingTopologicalOrderGraph>();
        var graph = DefaultShadowVariableSessionFactory.buildGraphForStructureAndDirection(
                new GraphStructure.GraphStructureAndDirection(GraphStructure.ARBITRARY, null, null),
                new DefaultShadowVariableSessionFactory.GraphDescriptor<>(solutionDescriptor,
                        ChangedVariableNotifier.empty(), entity, value1, value2)
                        .withGraphCreator(size -> {
                            var topologicalOrderGraph = new CountingTopologicalOrderGraph(size);
                            topologicalOrderGraphList.add(topologicalOrderGraph);
                            return topologicalOrderGraph;
                        }));
        assertThat(topologicalOrderGraphList).hasSize(1);
        var topologicalOrderGraph = topologicalOrderGraphList.get(0);

        // The initial update needs the topological order, which is only known after the first recomputation.
        graph.updateChanged();
        assertThat(topologicalOrderGraph.commitCount).isOne();
        assertThat(entity.getTotalDuration()).isEqualTo(3 + 4);

        // Changing a genuine basic variable adds and removes no edge, ...
        graph.beforeVariableChanged(durationMetaModel, value1);
        value1.setDuration(5);
        graph.afterVariableChanged(durationMetaModel, value1);
        graph.updateChanged();
        // ... so the topological order is not recomputed, ...
        assertThat(topologicalOrderGraph.commitCount).isOne();
        // ... but the change still propagates to the entity that aggregates its elements.
        assertThat(value1.getPaddedDuration()).isEqualTo(6);
        assertThat(entity.getTotalDuration()).isEqualTo(6 + 4);

        // Unassigning an element removes the edge from that element to the entity, ...
        graph.beforeListVariableChanged(valuesMetaModel, entity, new ArrayList<>(entity.getValues()), 1, 2);
        entity.getValues().remove(1);
        graph.afterListVariableChanged(valuesMetaModel, entity, new ArrayList<>(entity.getValues()), 1, 1);
        graph.updateChanged();
        // ... so the topological order is recomputed.
        assertThat(topologicalOrderGraph.commitCount).isEqualTo(2);
        assertThat(entity.getTotalDuration()).isEqualTo(6);
    }

}
