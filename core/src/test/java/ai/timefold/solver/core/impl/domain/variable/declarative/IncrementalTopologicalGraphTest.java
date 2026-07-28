package ai.timefold.solver.core.impl.domain.variable.declarative;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class IncrementalTopologicalGraphTest extends AbstractTopologicalGraphTest<IncrementalTopologicalOrderGraph> {

    @Override
    protected IncrementalTopologicalOrderGraph createTopologicalGraph(int graphSize) {
        return new IncrementalTopologicalOrderGraph(graphSize);
    }

    @Override
    protected void verifyConsistent(IncrementalTopologicalOrderGraph graph) {
        var size = graph.getSize();

        // The topological order must be a permutation of [0, size).
        var seenOrders = new BitSet(size);
        for (var node = 0; node < size; node++) {
            var order = graph.getTopologicalOrder(node);
            assertThat(order)
                    .withFailMessage("Node %d has out-of-range order %d in graph %s".formatted(node, order, graph))
                    .isBetween(0, size - 1);
            assertThat(seenOrders.get(order))
                    .withFailMessage("Node %d has duplicate order %d in graph %s".formatted(node, order, graph))
                    .isFalse();
            seenOrders.set(order);
        }

        // Every edge must respect the order, unless it stays within a component.
        // Component correctness itself is asserted by the inherited tests
        // through getComponentMembers() and by randomEdgeChangesMatchTheDefaultGraph().
        graph.forEachEdge((from, to) -> {
            if (!graph.getComponent(from).equals(graph.getComponent(to))) {
                assertThat(graph.getTopologicalOrder(from))
                        .withFailMessage("Edge (%d, %d) violates the topological order in graph %s"
                                .formatted(from, to, graph))
                        .isLessThan(graph.getTopologicalOrder(to));
            }
        });
    }

    @Override
    protected List<Integer> getComponentMembers(IncrementalTopologicalOrderGraph graph, int graphSize, int node) {
        return graph.getComponent(node);
    }

    @Test
    void acyclicEdgeChangesSkipTheFullRecomputation() {
        var graph = new IncrementalTopologicalOrderGraph(6);
        graph.addEdge(0, 1);
        graph.addEdge(1, 2);
        graph.addEdge(3, 4);
        var changed = new BitSet();
        graph.commitChanges(changed);
        assertThat(graph.requiresFullRecomputation()).isFalse();
        assertThat(changed.cardinality()).isZero();

        // A violating edge is repaired in place, without a full recomputation.
        graph.addEdge(4, 0);
        assertThat(graph.requiresFullRecomputation()).isFalse();
        assertThat(graph.getTopologicalOrder(4)).isLessThan(graph.getTopologicalOrder(0));
        assertThat(graph.getTopologicalOrder(3)).isLessThan(graph.getTopologicalOrder(4));
        assertThat(graph.getTopologicalOrder(0)).isLessThan(graph.getTopologicalOrder(1));
        assertThat(graph.getTopologicalOrder(1)).isLessThan(graph.getTopologicalOrder(2));

        // A removal never requires anything.
        graph.removeEdge(4, 0);
        assertThat(graph.requiresFullRecomputation()).isFalse();

        graph.commitChanges(changed);
        assertThat(changed.cardinality()).isZero();
    }

    @Test
    void cycleCreationFallsBackToTheFullRecomputation() {
        var graph = new IncrementalTopologicalOrderGraph(4);
        graph.addEdge(0, 1);
        graph.addEdge(1, 2);
        graph.addEdge(2, 3);
        var changed = new BitSet();
        graph.commitChanges(changed);
        assertThat(changed.cardinality()).isZero();

        graph.addEdge(2, 1);
        assertThat(graph.requiresFullRecomputation()).isTrue();
        graph.commitChanges(changed);
        assertThat(changed.cardinality()).isEqualTo(2);
        assertThat(changed.get(1)).isTrue();
        assertThat(changed.get(2)).isTrue();
        assertThat(graph.getComponent(1)).containsExactlyInAnyOrder(1, 2);

        // Breaking the loop falls back again and un-loops the members.
        changed.clear();
        graph.removeEdge(2, 1);
        assertThat(graph.requiresFullRecomputation()).isTrue();
        graph.commitChanges(changed);
        assertThat(changed.cardinality()).isEqualTo(2);
        assertThat(graph.getComponent(1)).containsExactlyInAnyOrder(1);
        assertThat(graph.requiresFullRecomputation()).isFalse();
    }

    @Test
    void removalOutsideLoopedComponentsSkipsTheFullRecomputation() {
        var graph = new IncrementalTopologicalOrderGraph(5);
        graph.addEdge(0, 1);
        graph.addEdge(1, 0);
        graph.addEdge(1, 2);
        graph.addEdge(3, 4);
        var changed = new BitSet();
        graph.commitChanges(changed);
        assertThat(changed.cardinality()).isEqualTo(2);

        // The removed edge is not inside the looped component {0, 1}.
        graph.removeEdge(3, 4);
        assertThat(graph.requiresFullRecomputation()).isFalse();

        // Any addition while a looped component exists falls back.
        graph.addEdge(3, 4);
        assertThat(graph.requiresFullRecomputation()).isTrue();
    }

    @Test
    void randomEdgeChangesMatchTheDefaultGraph() {
        for (var seed = 0; seed < 30; seed++) {
            var random = new Random(seed);
            var size = 5 + random.nextInt(20);
            var incremental = new IncrementalTopologicalOrderGraph(size);
            var oracle = new DefaultTopologicalOrderGraph(size);

            // Even seeds stay acyclic (edges follow a hidden random priority),
            // exercising the Pearce-Kelly repairs;
            // odd seeds allow cycles, exercising the fallback.
            var acyclicOnly = seed % 2 == 0;
            var nodePriorities = new ArrayList<Integer>(size);
            for (var node = 0; node < size; node++) {
                nodePriorities.add(node);
            }
            Collections.shuffle(nodePriorities, random);

            var edgeList = new ArrayList<int[]>();
            var edgeSet = new HashSet<Long>();
            for (var round = 0; round < 100; round++) {
                var operationCount = 1 + random.nextInt(4);
                for (var operation = 0; operation < operationCount; operation++) {
                    if (edgeList.isEmpty() || random.nextInt(10) < 6) {
                        var from = random.nextInt(size);
                        var to = random.nextInt(size);
                        if (from == to) {
                            continue;
                        }
                        if (acyclicOnly && nodePriorities.get(from) > nodePriorities.get(to)) {
                            var swap = from;
                            from = to;
                            to = swap;
                        }
                        if (edgeSet.add(((long) from << 32) | to)) {
                            edgeList.add(new int[] { from, to });
                            incremental.addEdge(from, to);
                            oracle.addEdge(from, to);
                        }
                    } else {
                        var edgeIndex = random.nextInt(edgeList.size());
                        var edge = edgeList.remove(edgeIndex);
                        edgeSet.remove(((long) edge[0] << 32) | edge[1]);
                        incremental.removeEdge(edge[0], edge[1]);
                        oracle.removeEdge(edge[0], edge[1]);
                    }
                }

                var incrementalChanged = new BitSet();
                var oracleChanged = new BitSet();
                incremental.commitChanges(incrementalChanged);
                oracle.commitChanges(oracleChanged);
                assertThat(incrementalChanged)
                        .withFailMessage("Changed bits differ on seed %d round %d:%nincremental %s%noracle %s"
                                .formatted(seed, round, incrementalChanged, oracleChanged))
                        .isEqualTo(oracleChanged);

                var incrementalTracker = new LoopedTracker(size, new int[0][]);
                var oracleTracker = new LoopedTracker(size, new int[0][]);
                for (var node = 0; node < size; node++) {
                    assertThat(incremental.isLooped(incrementalTracker, node))
                            .withFailMessage("Looped status differs for node %d on seed %d round %d"
                                    .formatted(node, seed, round))
                            .isEqualTo(oracle.isLooped(oracleTracker, node));
                }

                for (var edge : edgeList) {
                    if (!incremental.isLooped(incrementalTracker, edge[0])
                            && !incremental.isLooped(incrementalTracker, edge[1])) {
                        assertThat(incremental.getTopologicalOrder(edge[0]))
                                .withFailMessage("Edge (%d, %d) violates the order on seed %d round %d in graph %s"
                                        .formatted(edge[0], edge[1], seed, round, incremental))
                                .isLessThan(incremental.getTopologicalOrder(edge[1]));
                    }
                }
            }
        }
    }
}
