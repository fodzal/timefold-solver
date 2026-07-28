package ai.timefold.solver.core.impl.domain.variable.declarative;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultTopologicalOrderGraph implements TopologicalOrderGraph {

    private final int[] nodeIdToTopologicalOrderMap;
    private final Map<Integer, List<Integer>> componentMap;
    private final Set<Integer>[] forwardEdges;
    private final Set<Integer>[] backEdges;
    private final boolean[] isNodeInLoopedComponent;

    @SuppressWarnings({ "unchecked" })
    public DefaultTopologicalOrderGraph(final int size) {
        this.nodeIdToTopologicalOrderMap = new int[size];

        this.componentMap = LinkedHashMap.newLinkedHashMap(size);
        this.forwardEdges = new Set[size];
        this.backEdges = new Set[size];
        this.isNodeInLoopedComponent = new boolean[size];
        for (var i = 0; i < size; i++) {
            forwardEdges[i] = new HashSet<>();
            backEdges[i] = new HashSet<>();
            isNodeInLoopedComponent[i] = false;
            nodeIdToTopologicalOrderMap[i] = i;
        }
    }

    List<Integer> getComponent(int node) {
        return componentMap.get(node);
    }

    List<List<Integer>> getLoopedComponentList() {
        var out = new ArrayList<List<Integer>>(componentMap.size());
        var visited = new boolean[forwardEdges.length];
        for (var component : componentMap.values()) {
            if (component.size() < 2) {
                // all looped components have at least 2 members.
                // non-looped components have exactly one member.
                continue;
            }
            if (visited[component.get(0)]) {
                // already visited this component
                continue;
            }
            // Only need to set first node of a component, since the same
            // list is shared for each node in the component
            visited[component.get(0)] = true;
            out.add(component);
        }
        return out;
    }

    @Override
    public void addEdge(int fromNode, int toNode) {
        forwardEdges[fromNode].add(toNode);
        backEdges[toNode].add(fromNode);
    }

    @Override
    public void removeEdge(int fromNode, int toNode) {
        forwardEdges[fromNode].remove(toNode);
        backEdges[toNode].remove(fromNode);
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
        return forwardEdges[fromNode].stream()
                .mapToInt(Integer::intValue)
                .iterator();
    }

    @Override
    public boolean isLooped(LoopedTracker loopedTracker, int node) {
        return switch (loopedTracker.status(node)) {
            case UNKNOWN -> {
                if (componentMap.get(node).size() > 1) {
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

    @Override
    public void commitChanges(BitSet changed) {
        var components = TarjanStronglyConnectedComponents.compute(forwardEdges);
        componentMap.clear();

        var ordIndex = 0;
        for (var i = components.size() - 1; i >= 0; i--) {
            var component = components.get(i);
            var componentSize = component.cardinality();
            var isComponentLooped = componentSize != 1;
            var componentNodes = new ArrayList<Integer>(componentSize);
            for (var node = component.nextSetBit(0); node >= 0; node = component.nextSetBit(node + 1)) {
                nodeIdToTopologicalOrderMap[node] = ordIndex;
                componentNodes.add(node);
                componentMap.put(node, componentNodes);

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
    }

    @Override
    public String toString() {
        var out = new StringBuilder();
        out.append("DefaultTopologicalOrderGraph{\n");
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
