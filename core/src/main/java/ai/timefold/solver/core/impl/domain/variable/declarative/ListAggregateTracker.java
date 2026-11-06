package ai.timefold.solver.core.impl.domain.variable.declarative;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ai.timefold.solver.core.impl.domain.variable.ListVariableState;
import ai.timefold.solver.core.impl.util.ShrinkingIdentityHashMap;
import ai.timefold.solver.core.preview.api.domain.metamodel.PositionInList;
import ai.timefold.solver.core.preview.api.domain.metamodel.VariableMetaModel;

import org.jspecify.annotations.NullMarked;

/**
 * Tracks the changes the aggregate nodes of a {@link ListAggregation} need, like a graph tracks its changed nodes.
 * The elements have no graph nodes, so their source variables have no node to mark changed:
 * this records the elements whose source variables changed and the list entities whose list changed,
 * then, before the graph updates,
 * classifies them by list entity, into the chain state the {@link ListAggregateUpdater} walks from,
 * and marks the aggregate nodes of the dirty lists changed.
 * <p>
 * The classification recomputes the elements that left their list,
 * whose writes come back to the graph through the score director;
 * the graph's reentrancy guard therefore spans the classification too.
 */
@NullMarked
final class ListAggregateTracker<Solution_> {

    // These are immutable.
    private final ListAggregateUpdater<Solution_> updater;
    private final ListVariableState<Solution_, Object, Object> listVariableState;
    private final VariableMetaModel<?, ?, ?> listVariableMetaModel;
    private final Class<?> elementEntityClass;
    // The variables the element variables read, on the elements or on their list entity.
    private final Set<VariableMetaModel<?, ?, ?>> elementSourceVariableSet;
    private final ChangedVariableNotifier<Solution_> changedVariableNotifier;

    // These are mutable.
    private final List<Object> changedElementList;
    private final List<Object> changedListEntityList;
    // The unassigned elements the classification in progress recomputed, so that each is recomputed once.
    // Its clear() keeps the update cheap even after the initial update, which records every unassigned element.
    private final ShrinkingIdentityHashMap<Object, Boolean> recomputedUnassignedElementMap;
    private final List<ListAggregateUpdater.ChainState<Solution_>> dirtyChainStateList;

    ListAggregateTracker(ListAggregation<Solution_> aggregation, ListAggregateUpdater<Solution_> updater,
            ListVariableState<Solution_, Object, Object> listVariableState,
            EntityConsistencyState<Solution_, Object> elementConsistencyState,
            ChangedVariableNotifier<Solution_> changedVariableNotifier, Object[] entities) {
        this.updater = updater;
        this.listVariableState = listVariableState;
        this.listVariableMetaModel = aggregation.listVariableDescriptor().getVariableMetaModel();
        this.elementEntityClass = aggregation.elementEntityClass();
        this.changedVariableNotifier = changedVariableNotifier;
        this.elementSourceVariableSet = new HashSet<>();
        for (var descriptor : aggregation.elementDescriptorList()) {
            for (var source : descriptor.getSources()) {
                for (var sourceReference : source.variableSourceReferences()) {
                    elementSourceVariableSet.add(sourceReference.variableMetaModel());
                }
            }
        }
        this.changedElementList = new ArrayList<>();
        this.changedListEntityList = new ArrayList<>();
        this.recomputedUnassignedElementMap = new ShrinkingIdentityHashMap<>();
        this.dirtyChainStateList = new ArrayList<>();

        // Every element gets an initial computation and starts consistent;
        // its consistency can only change when its list entity becomes inconsistent.
        for (var entity : entities) {
            if (elementEntityClass.isInstance(entity)) {
                elementConsistencyState.setEntityIsInconsistent(changedVariableNotifier, entity, false);
                recordChangedElement(entity);
            }
        }
    }

    /**
     * @return the variables whose changes this tracker needs to hear about
     */
    Set<VariableMetaModel<?, ?, ?>> getMonitoredVariableSet() {
        var monitoredVariableSet = new HashSet<>(elementSourceVariableSet);
        monitoredVariableSet.add(listVariableMetaModel);
        return monitoredVariableSet;
    }

    boolean isElementSource(VariableMetaModel<?, ?, ?> variableId) {
        return elementSourceVariableSet.contains(variableId);
    }

    boolean isListVariable(VariableMetaModel<?, ?, ?> variableId) {
        return listVariableMetaModel.equals(variableId);
    }

    void afterElementSourceChanged(Object entity) {
        // A declarative pre-chain variable of the list entity, also a source, only changes during an update.
        if (elementEntityClass.isInstance(entity)) {
            recordChangedElement(entity);
        }
    }

    void afterListChanged(Object listEntity, int fromIndex, int toIndex) {
        // The elements whose source variables changed record themselves; when none of them is in this list,
        // as when a forced update of every shadow variable simulates a change on every list,
        // the whole list is walked.
        if (fromIndex < toIndex) {
            changedListEntityList.add(listEntity);
        }
    }

    boolean hasPendingChanges() {
        // A dirty list has its aggregate node marked in the graph.
        return !changedElementList.isEmpty() || !changedListEntityList.isEmpty();
    }

    private void recordChangedElement(Object element) {
        // An element's list variable state changes all at once, so its events come in a row.
        if (changedElementList.isEmpty() || changedElementList.getLast() != element) {
            changedElementList.add(element);
        }
    }

    /**
     * Classifies the recorded changes by list entity, and marks the aggregate nodes of the dirty lists changed.
     * An unassigned element has no aggregate node to walk it, so it is recomputed here.
     */
    void classifyChanges(AbstractVariableReferenceGraph<Solution_, ?> graph) {
        for (var element : changedElementList) {
            if (!(listVariableState.getElementPosition(element) instanceof PositionInList positionInList)) {
                // A move changing an element before unassigning it records it twice, apart.
                if (recomputedUnassignedElementMap.put(element, Boolean.TRUE) == null) {
                    updater.recomputeUnassignedElement(element, changedVariableNotifier);
                }
                continue;
            }
            var chainState = updater.getChainState(positionInList.entity());
            chainState.addChangedElementIndex(positionInList.index());
            markChainDirty(graph, chainState);
        }
        for (var listEntity : changedListEntityList) {
            markChainDirty(graph, updater.getChainState(listEntity));
        }
        changedElementList.clear();
        changedListEntityList.clear();
        recomputedUnassignedElementMap.clear();
    }

    private void markChainDirty(AbstractVariableReferenceGraph<Solution_, ?> graph,
            ListAggregateUpdater.ChainState<Solution_> chainState) {
        if (chainState.markDirty()) {
            dirtyChainStateList.add(chainState);
            graph.markChanged(chainState.aggregateNode());
        }
    }

    /**
     * Resets the lists the update dirtied.
     */
    void endUpdate() {
        for (var chainState : dirtyChainStateList) {
            chainState.endUpdate();
        }
        dirtyChainStateList.clear();
    }
}
