package ai.timefold.solver.core.impl.domain.variable.declarative;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final VariableMetaModel<Solution_, ?, ?> listVariableMetaModel;
    private final Class<?> elementEntityClass;
    private final Set<VariableMetaModel<?, ?, ?>> monitoredSourceVariableSet;
    private final ChangedVariableNotifier<Solution_> changedVariableNotifier;

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
        this.listVariableMetaModel = listVariableMetaModel;
        this.elementEntityClass = elementEntityClass;
        this.changedVariableNotifier = changedVariableNotifier;
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
        // The elements in the changed range may leave the list;
        // an element that becomes unassigned is reset when the changes are classified.
        for (var elementIndex = fromIndex; elementIndex < toIndex; elementIndex++) {
            blockUpdater.recordChangedElement(elementList.get(elementIndex));
        }
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
    public void updateChanged() {
        isProcessing = true;
        try {
            blockUpdater.classifyChangedElements(changedVariableNotifier, this::markBlockNodeChanged);
            innerGraph.updateChanged();
            // A flag whose block node was never processed (e.g. its entity is no longer
            // in the working solution) must not leak into the next update.
            blockUpdater.clearTransientState();
        } finally {
            isProcessing = false;
        }
    }

    private void markBlockNodeChanged(Object owner) {
        if (innerNodeGraph == null) {
            return;
        }
        var blockNode = innerNodeGraph.lookupOrNull(listVariableMetaModel, owner);
        if (blockNode != null) {
            innerNodeGraph.markChanged(blockNode);
        }
    }
}
