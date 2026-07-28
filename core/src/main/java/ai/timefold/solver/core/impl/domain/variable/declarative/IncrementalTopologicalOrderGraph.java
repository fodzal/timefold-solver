package ai.timefold.solver.core.impl.domain.variable.declarative;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A {@link TopologicalOrderGraph} that maintains its topological order incrementally,
 * unlike {@link DefaultTopologicalOrderGraph}, which recomputes it from scratch on every commit.
 * <p>
 * While the graph is acyclic, edge changes are handled with the Pearce-Kelly algorithm
 * (David J. Pearce, Paul H. J. Kelly: <i>A Dynamic Topological Sort Algorithm for
 * Directed Acyclic Graphs</i>, ACM Journal of Experimental Algorithmics, 2006):
 *
 * <ul>
 * <li>Removing an edge never invalidates a valid topological order, so it is free.</li>
 * <li>Adding an edge {@code (from, to)} with {@code order(from) < order(to)} is also free.</li>
 * <li>Adding any other edge only reorders the nodes between {@code order(to)} and
 * {@code order(from)} that are reachable from {@code to} or reach {@code from};
 * the rest of the graph is untouched.</li>
 * </ul>
 *
 * When adding an edge creates a cycle, when an edge is removed inside a looped
 * strongly connected component, or when any edge changes while a looped component exists,
 * the graph falls back to a full Tarjan recomputation on the next commit,
 * exactly like {@link DefaultTopologicalOrderGraph}.
 * A looped component means the solution is in an inconsistent state,
 * which a healthy model only enters transiently, so the fallback is rare.
 */
public final class IncrementalTopologicalOrderGraph implements TopologicalOrderGraph {

    private final Set<Integer>[] forwardEdges;
    private final Set<Integer>[] backEdges;
    /**
     * A permutation of {@code [0, size)}:
     * unlike {@link DefaultTopologicalOrderGraph}, every node has a unique order.
     */
    private final int[] nodeIdToTopologicalOrderMap;
    /**
     * The component id of a node is the id of one of the component's members,
     * making ids unique across components.
     * Only maintained by full recomputations;
     * incremental repairs never change any component.
     */
    private final int[] nodeIdToComponentIdMap;
    private final int[] componentIdToSizeMap;
    private final boolean[] isNodeInLoopedComponent;

    // Scratch structures for the Pearce-Kelly repair, reused to avoid allocations on the hot path.
    private final int[] dfsStack;
    private final int[] forwardRegionNodes;
    private final int[] backwardRegionNodes;
    /** Each element encodes a (topological order, node id) pair, so sorting it sorts nodes by order. */
    private final long[] sortedRegionEncodedNodes;
    private final int[] sortedRegionSlots;
    private final BitSet visitedForward;
    private final BitSet visitedBackward;

    /**
     * Starts as true, because the initial order is only computed by the first commit.
     */
    private boolean requiresFullRecomputation = true;
    private boolean hasLoopedComponent = false;

    @SuppressWarnings("unchecked")
    public IncrementalTopologicalOrderGraph(int size) {
        this.forwardEdges = new Set[size];
        this.backEdges = new Set[size];
        this.nodeIdToTopologicalOrderMap = new int[size];
        this.nodeIdToComponentIdMap = new int[size];
        this.componentIdToSizeMap = new int[size];
        this.isNodeInLoopedComponent = new boolean[size];
        for (var i = 0; i < size; i++) {
            forwardEdges[i] = new HashSet<>();
            backEdges[i] = new HashSet<>();
            nodeIdToTopologicalOrderMap[i] = i;
            nodeIdToComponentIdMap[i] = i;
            componentIdToSizeMap[i] = 1;
        }
        this.dfsStack = new int[size];
        this.forwardRegionNodes = new int[size];
        this.backwardRegionNodes = new int[size];
        this.sortedRegionEncodedNodes = new long[size];
        this.sortedRegionSlots = new int[size];
        this.visitedForward = new BitSet(size);
        this.visitedBackward = new BitSet(size);
    }

    @Override
    public void addEdge(int fromNode, int toNode) {
        if (!forwardEdges[fromNode].add(toNode)) {
            // The edge already exists, so the order is already valid for it.
            return;
        }
        backEdges[toNode].add(fromNode);
        if (fromNode == toNode) {
            // Callers never add self-edges; Tarjan puts a self-looped node
            // in a size-1 component, so the order is unaffected either way.
            return;
        }
        if (requiresFullRecomputation) {
            return;
        }
        if (hasLoopedComponent) {
            // The order within a looped component is arbitrary,
            // which breaks the precondition of the Pearce-Kelly repair.
            requiresFullRecomputation = true;
            return;
        }
        if (nodeIdToTopologicalOrderMap[fromNode] < nodeIdToTopologicalOrderMap[toNode]) {
            // The order is already valid for the new edge.
            return;
        }
        repairOrder(fromNode, toNode);
    }

    @Override
    public void removeEdge(int fromNode, int toNode) {
        if (!forwardEdges[fromNode].remove(toNode)) {
            return;
        }
        backEdges[toNode].remove(fromNode);
        if (requiresFullRecomputation) {
            return;
        }
        // An edge removal never invalidates a valid topological order,
        // but removing an edge inside a looped component may split it.
        var componentId = nodeIdToComponentIdMap[fromNode];
        if (componentId == nodeIdToComponentIdMap[toNode] && componentIdToSizeMap[componentId] > 1) {
            requiresFullRecomputation = true;
        }
    }

    /**
     * The Pearce-Kelly repair for a new edge {@code (fromNode, toNode)}
     * that violates the current order.
     * Precondition: the order is valid for every other edge,
     * which holds because the graph is acyclic and every prior change was repaired.
     * <p>
     * Nodes reaching {@code fromNode} move before nodes reachable from {@code toNode},
     * each group keeping its relative order,
     * within the set of positions both groups currently occupy.
     * If {@code fromNode} is reachable from {@code toNode}, the new edge creates a cycle,
     * which only a full recomputation can turn into a looped component.
     */
    private void repairOrder(int fromNode, int toNode) {
        var upperBound = nodeIdToTopologicalOrderMap[fromNode];
        var lowerBound = nodeIdToTopologicalOrderMap[toNode];

        // Forward search from toNode over nodes with order below upperBound.
        // Any path back to fromNode can only pass through such nodes,
        // because every edge except the new one increases the order.
        var forwardCount = 0;
        var stackSize = 0;
        dfsStack[stackSize++] = toNode;
        visitedForward.set(toNode);
        var createsCycle = false;
        while (stackSize > 0) {
            var node = dfsStack[--stackSize];
            forwardRegionNodes[forwardCount++] = node;
            for (var successor : forwardEdges[node]) {
                if (successor == fromNode) {
                    createsCycle = true;
                    break;
                }
                if (nodeIdToTopologicalOrderMap[successor] < upperBound && !visitedForward.get(successor)) {
                    visitedForward.set(successor);
                    dfsStack[stackSize++] = successor;
                }
            }
            if (createsCycle) {
                break;
            }
        }
        if (createsCycle) {
            // Include the nodes that were discovered but not yet expanded,
            // so that every visited bit gets cleared.
            while (stackSize > 0) {
                forwardRegionNodes[forwardCount++] = dfsStack[--stackSize];
            }
            for (var i = 0; i < forwardCount; i++) {
                visitedForward.clear(forwardRegionNodes[i]);
            }
            requiresFullRecomputation = true;
            return;
        }

        // Backward search from fromNode over nodes with order above lowerBound.
        // It cannot meet the forward region: a node both reachable from toNode
        // and reaching fromNode would mean the cycle detected above.
        var backwardCount = 0;
        dfsStack[stackSize++] = fromNode;
        visitedBackward.set(fromNode);
        while (stackSize > 0) {
            var node = dfsStack[--stackSize];
            backwardRegionNodes[backwardCount++] = node;
            for (var predecessor : backEdges[node]) {
                if (nodeIdToTopologicalOrderMap[predecessor] > lowerBound && !visitedBackward.get(predecessor)) {
                    visitedBackward.set(predecessor);
                    dfsStack[stackSize++] = predecessor;
                }
            }
        }

        // Reorder: the backward region moves before the forward region,
        // each keeping its relative order,
        // within the union of the positions both regions currently occupy.
        var totalCount = backwardCount + forwardCount;
        for (var i = 0; i < backwardCount; i++) {
            var node = backwardRegionNodes[i];
            sortedRegionEncodedNodes[i] = encodeOrderAndNode(node);
            visitedBackward.clear(node);
        }
        Arrays.sort(sortedRegionEncodedNodes, 0, backwardCount);
        for (var i = 0; i < forwardCount; i++) {
            var node = forwardRegionNodes[i];
            sortedRegionEncodedNodes[backwardCount + i] = encodeOrderAndNode(node);
            visitedForward.clear(node);
        }
        Arrays.sort(sortedRegionEncodedNodes, backwardCount, totalCount);
        for (var i = 0; i < totalCount; i++) {
            sortedRegionSlots[i] = (int) (sortedRegionEncodedNodes[i] >>> 32);
        }
        Arrays.sort(sortedRegionSlots, 0, totalCount);
        for (var i = 0; i < totalCount; i++) {
            nodeIdToTopologicalOrderMap[(int) sortedRegionEncodedNodes[i]] = sortedRegionSlots[i];
        }
    }

    private long encodeOrderAndNode(int node) {
        return ((long) nodeIdToTopologicalOrderMap[node] << 32) | node;
    }

    @Override
    public void commitChanges(BitSet changed) {
        if (!requiresFullRecomputation) {
            // Incremental repairs kept the order valid and no component changed,
            // so there is nothing to commit.
            return;
        }
        var components = TarjanStronglyConnectedComponents.compute(forwardEdges);
        hasLoopedComponent = false;
        var ordIndex = 0;
        for (var i = components.size() - 1; i >= 0; i--) {
            var component = components.get(i);
            var componentSize = component.cardinality();
            var isComponentLooped = componentSize != 1;
            var componentId = component.nextSetBit(0);
            if (isComponentLooped) {
                hasLoopedComponent = true;
            }
            componentIdToSizeMap[componentId] = componentSize;
            for (var node = component.nextSetBit(0); node >= 0; node = component.nextSetBit(node + 1)) {
                nodeIdToTopologicalOrderMap[node] = ordIndex;
                nodeIdToComponentIdMap[node] = componentId;
                if (isComponentLooped != isNodeInLoopedComponent[node]) {
                    // It is enough to only mark nodes whose component
                    // status changed; the updater will notify descendants
                    // since a looped status change force updates descendants.
                    isNodeInLoopedComponent[node] = isComponentLooped;
                    changed.set(node);
                }
                ordIndex++;

                if (node == Integer.MAX_VALUE) {
                    break;
                }
            }
        }
        requiresFullRecomputation = false;
    }

    @Override
    public void forEachEdge(EdgeConsumer edgeConsumer) {
        for (var fromNode = 0; fromNode < forwardEdges.length; fromNode++) {
            for (var toNode : forwardEdges[fromNode]) {
                edgeConsumer.accept(fromNode, toNode);
            }
        }
    }

    @Override
    public PrimitiveIterator.OfInt nodeForwardEdges(int fromNode) {
        var iterator = forwardEdges[fromNode].iterator();
        return new PrimitiveIterator.OfInt() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public int nextInt() {
                return iterator.next();
            }
        };
    }

    @Override
    public boolean isLooped(LoopedTracker loopedTracker, int node) {
        return switch (loopedTracker.status(node)) {
            case UNKNOWN -> {
                if (componentIdToSizeMap[nodeIdToComponentIdMap[node]] > 1) {
                    loopedTracker.mark(node, LoopedStatus.LOOPED);
                    yield true;
                }
                for (var backEdge : backEdges[node]) {
                    if (isLooped(loopedTracker, backEdge)) {
                        loopedTracker.mark(node, LoopedStatus.LOOPED);
                        yield true;
                    }
                }
                loopedTracker.mark(node, LoopedStatus.NOT_LOOPED);
                yield false;
            }
            case NOT_LOOPED -> false;
            case LOOPED -> true;
        };
    }

    @Override
    public int getTopologicalOrder(int node) {
        return nodeIdToTopologicalOrderMap[node];
    }

    List<Integer> getComponent(int node) {
        var componentId = nodeIdToComponentIdMap[node];
        var out = new ArrayList<Integer>();
        for (var i = 0; i < nodeIdToComponentIdMap.length; i++) {
            if (nodeIdToComponentIdMap[i] == componentId) {
                out.add(i);
            }
        }
        return out;
    }

    boolean requiresFullRecomputation() {
        return requiresFullRecomputation;
    }

    int getSize() {
        return forwardEdges.length;
    }

    @Override
    public String toString() {
        var out = new StringBuilder();
        out.append("IncrementalTopologicalOrderGraph{\n");
        for (var node = 0; node < forwardEdges.length; node++) {
            out.append("    ").append(node).append("(").append(nodeIdToTopologicalOrderMap[node]).append(") -> ")
                    .append(forwardEdges[node].stream()
                            .sorted()
                            .map(Object::toString)
                            .collect(Collectors.joining(",", "[", "]\n")));
        }
        out.append("}");
        return out.toString();
    }
}
