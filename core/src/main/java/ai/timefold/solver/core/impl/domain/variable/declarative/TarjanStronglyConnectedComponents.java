package ai.timefold.solver.core.impl.domain.variable.declarative;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

import ai.timefold.solver.core.impl.util.MutableInt;

/**
 * Computes the strongly connected components of a directed graph
 * with Tarjan's algorithm, in O(nodes + edges) time.
 * <p>
 * The components are returned in reverse topological order of the condensation:
 * iterating the returned list backwards visits every component
 * before any of its successors.
 */
final class TarjanStronglyConnectedComponents {

    /**
     * @param forwardEdges The successors of each node, indexed by node id.
     * @return The strongly connected components, each as a {@link BitSet} of node ids,
     *         in reverse topological order of the condensation.
     */
    static List<BitSet> compute(Set<Integer>[] forwardEdges) {
        var size = forwardEdges.length;
        var index = new MutableInt(1);
        var stackIndex = new MutableInt(0);
        var stack = new int[size];
        var indexMap = new int[size];
        var lowMap = new int[size];
        var onStackSet = new boolean[size];
        var components = new ArrayList<BitSet>();

        for (var node = 0; node < size; node++) {
            if (indexMap[node] == 0) {
                strongConnect(forwardEdges, node, index, stackIndex, stack, indexMap, lowMap, onStackSet, components);
            }
        }
        return components;
    }

    private static void strongConnect(Set<Integer>[] forwardEdges, int node, MutableInt index, MutableInt stackIndex,
            int[] stack, int[] indexMap, int[] lowMap, boolean[] onStackSet, List<BitSet> components) {
        // Set the depth index for node to the smallest unused index
        indexMap[node] = index.intValue();
        lowMap[node] = index.intValue();
        index.increment();
        stack[stackIndex.intValue()] = node;
        onStackSet[node] = true;
        stackIndex.increment();

        // Consider successors of node
        for (var successor : forwardEdges[node]) {
            if (indexMap[successor] == 0) {
                // Successor has not yet been visited; recurse on it
                strongConnect(forwardEdges, successor, index, stackIndex, stack, indexMap, lowMap, onStackSet, components);
                lowMap[node] = Math.min(lowMap[node], lowMap[successor]);
            } else if (onStackSet[successor]) {
                // Successor is in stack S and hence in the current SCC
                // If successor is not on stack, then (node, successor) is an edge pointing to an SCC already found and must be ignored
                // The next line may look odd - but is correct.
                // It says successor.index not successor.low; that is deliberate and from the original paper
                lowMap[node] = Math.min(lowMap[node], indexMap[successor]);
            }
        }

        // If node is a root node, pop the stack and generate an SCC
        if (onStackSet[node] && lowMap[node] == indexMap[node]) {
            var out = new BitSet();

            int current;
            do {
                current = stack[stackIndex.decrement()];
                onStackSet[current] = false;
                out.set(current);
            } while (node != current);
            components.add(out);
        }
    }

    private TarjanStronglyConnectedComponents() {
        // No instances.
    }
}
