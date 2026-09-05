package ai.timefold.solver.core.impl.domain.variable.declarative;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ai.timefold.solver.core.api.score.analysis.VariableLoop;
import ai.timefold.solver.core.preview.api.domain.metamodel.VariableMetaModel;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A variable reference graph that excludes a planning list variable's elements from the graph
 * and represents each list entity's chain by a single block node instead;
 * see {@link GraphStructure.ListElementBlock}.
 * <p>
 * The graph itself covers everything else and is built by the normal machinery,
 * with fixed edges from each entity's pre-chain variables to its block node
 * and from its block node to its post-chain variables;
 * a single {@link #updateChanged()} pass in topological order therefore walks each dirty chain
 * exactly once, after its pre-chain variables and before its post-chain variables.
 * This wrapper only routes the events the block nodes need:
 * it records the elements whose source variables changed and the list variables' change ranges,
 * classifies them into per-entity dirty ranges,
 * and marks the dirty entities' block nodes before delegating the update.
 */
@NullMarked
public final class ListElementBlockVariableReferenceGraph<Solution_> implements VariableReferenceGraph {

    private final VariableReferenceGraph innerGraph;
    private final @Nullable AbstractVariableReferenceGraph<Solution_, ?> innerNodeGraph;
    private final ListElementBlockUpdater<Solution_> blockUpdater;
    private final Class<?> elementEntityClass;
    private final Set<VariableMetaModel<?, ?, ?>> monitoredSourceVariableSet;
    private final ChangedVariableNotifier<Solution_> changedVariableNotifier;
    /** Owner to block node, hoisted out of the per-variable node map for the hot path. */
    private final Map<Object, GraphNode<Solution_>> ownerToBlockNodeMap;

    private boolean isProcessing;

    ListElementBlockVariableReferenceGraph(
            VariableReferenceGraph innerGraph,
            ListElementBlockUpdater<Solution_> blockUpdater,
            VariableMetaModel<Solution_, ?, ?> listVariableMetaModel,
            Class<?> elementEntityClass,
            EntityConsistencyState<Solution_, Object> elementConsistencyState,
            List<DeclarativeShadowVariableDescriptor<Solution_>> elementDescriptorList,
            ChangedVariableNotifier<Solution_> changedVariableNotifier,
            Object[] entities) {
        this.innerGraph = innerGraph;
        this.innerNodeGraph = innerGraph instanceof AbstractVariableReferenceGraph<?, ?> abstractGraph
                ? (AbstractVariableReferenceGraph<Solution_, ?>) abstractGraph
                : null;
        this.blockUpdater = blockUpdater;
        this.elementEntityClass = elementEntityClass;
        this.changedVariableNotifier = changedVariableNotifier;
        this.ownerToBlockNodeMap = innerNodeGraph == null ? Map.of()
                : innerNodeGraph.variableReferenceToContainingNodeMap.getOrDefault(listVariableMetaModel, Map.of());
        this.isProcessing = false;

        this.monitoredSourceVariableSet = new HashSet<>();
        for (var descriptor : elementDescriptorList) {
            for (var source : descriptor.getSources()) {
                for (var sourceReference : source.variableSourceReferences()) {
                    monitoredSourceVariableSet.add(sourceReference.variableMetaModel());
                }
            }
        }

        // Every element gets an initial computation and starts consistent;
        // its consistency can only change when its entity becomes inconsistent.
        for (var entity : entities) {
            if (elementEntityClass.isInstance(entity)) {
                elementConsistencyState.setEntityIsInconsistent(changedVariableNotifier, entity, false);
                blockUpdater.recordChangedElement(entity);
            }
        }
        updateChanged();
    }

    @Override
    public void beforeVariableChanged(VariableMetaModel<?, ?, ?> variableReference, Object entity) {
        if (isProcessing) {
            // A reentrant event of this graph's own update.
            return;
        }
        innerGraph.beforeVariableChanged(variableReference, entity);
    }

    @Override
    public void afterVariableChanged(VariableMetaModel<?, ?, ?> variableReference, Object entity) {
        if (isProcessing) {
            return;
        }
        if (monitoredSourceVariableSet.contains(variableReference) && elementEntityClass.isInstance(entity)) {
            blockUpdater.recordChangedElement(entity);
        }
        innerGraph.afterVariableChanged(variableReference, entity);
    }

    @Override
    public void beforeListVariableChanged(VariableMetaModel<?, ?, ?> variableReference, Object entity,
            List<Object> elementList, int fromIndex, int toIndex) {
        if (isProcessing) {
            throw new IllegalStateException("Impossible state: list variable changed during shadow variable update.");
        }
        // An element that leaves the list is recorded through its shadow variable changes
        // (inverse and previous element), which the list variable state supply notifies.
        innerGraph.beforeListVariableChanged(variableReference, entity, elementList, fromIndex, toIndex);
    }

    @Override
    public void afterListVariableChanged(VariableMetaModel<?, ?, ?> variableReference, Object entity,
            List<Object> elementList, int fromIndex, int toIndex) {
        if (isProcessing) {
            throw new IllegalStateException("Impossible state: list variable changed during shadow variable update.");
        }
        for (var elementIndex = fromIndex; elementIndex < toIndex; elementIndex++) {
            blockUpdater.recordChangedElement(elementList.get(elementIndex));
        }
        // The post-chain variables' dependency set changed with the list's contents,
        // even when the changed range is empty (e.g. an element was removed).
        blockUpdater.recordStructuralChange(entity);
        innerGraph.afterListVariableChanged(variableReference, entity, elementList, fromIndex, toIndex);
    }

    @Override
    public boolean updateChanged() {
        isProcessing = true;
        try {
            blockUpdater.classifyChangedElements(changedVariableNotifier, this::markBlockNodeChanged);
            if (!innerGraph.updateChanged()) {
                // The graph gave up on a structurally flawed solution, leaving block nodes unprocessed.
                // Their dirty ranges are kept, because the caller undoes the move and updates again;
                // clearing them would leave the chains that were not walked with stale values.
                return false;
            }
            // A flag whose block node was never processed (e.g. its entity is no longer
            // in the working solution) must not leak into the next update.
            blockUpdater.clearTransientState();
            return true;
        } finally {
            isProcessing = false;
        }
    }

    @Override
    public List<VariableLoop> getVariableLoops() {
        // A loop that closes through a chain runs through the entity's block node,
        // which the graph reports as its list variable; its elements follow their entity.
        return innerGraph.getVariableLoops();
    }

    private void markBlockNodeChanged(Object owner) {
        var nodeGraph = innerNodeGraph;
        if (nodeGraph == null) {
            return;
        }
        var blockNode = ownerToBlockNodeMap.get(owner);
        if (blockNode != null) {
            nodeGraph.markChanged(blockNode);
        }
    }
}
