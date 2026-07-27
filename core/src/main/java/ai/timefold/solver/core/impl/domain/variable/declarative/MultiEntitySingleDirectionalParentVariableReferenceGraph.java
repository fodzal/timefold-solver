package ai.timefold.solver.core.impl.domain.variable.declarative;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import ai.timefold.solver.core.preview.api.domain.metamodel.VariableMetaModel;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A variable reference graph for {@link GraphStructure#MULTI_ENTITY_SINGLE_DIRECTIONAL_PARENT}.
 * <p>
 * The fixed entities are processed in the topological order of their fact DAG,
 * computed once at construction.
 * For each dirty fixed entity, its pre-chain variables are updated first,
 * then its chain is walked from the earliest dirty element,
 * then its post-chain variables are updated;
 * successors are enqueued when a fixed variable changed.
 * There are no explicit edges and no strongly connected component computation.
 */
@NullMarked
public final class MultiEntitySingleDirectionalParentVariableReferenceGraph<Solution_> implements VariableReferenceGraph {

    private final UnaryOperator<@Nullable Object> nextInChain;
    private final UnaryOperator<@Nullable Object> chainedEntityToFixedEntity;
    private final Comparator<Object> chainOrderComparator;
    private final Function<Object, @Nullable Object> fixedEntityToFirstChainedEntity;

    private final VariableUpdaterInfo<Solution_>[] chainedUpdaters;
    private final VariableUpdaterInfo<Solution_>[] preChainFixedUpdaters;
    private final VariableUpdaterInfo<Solution_>[] postChainFixedUpdaters;

    private final Map<Object, Integer> fixedEntityToOrder;
    private final Map<Object, Object[]> fixedEntityToSuccessors;

    private final Class<?> chainedEntityClass;
    private final Class<?> fixedEntityClass;

    private final Set<VariableMetaModel<?, ?, ?>> monitoredSourceVariableSet;
    private final ChangedVariableNotifier<Solution_> changedVariableNotifier;
    private final boolean canTerminateEarly;
    private final boolean chainReadsPreChainVariable;

    private final List<Object> changedEntityList;
    private boolean isUpdating;

    @SuppressWarnings("unchecked")
    MultiEntitySingleDirectionalParentVariableReferenceGraph(
            ConsistencyTracker<Solution_> consistencyTracker,
            List<DeclarativeShadowVariableDescriptor<Solution_>> chainedDescriptorList,
            List<DeclarativeShadowVariableDescriptor<Solution_>> preChainFixedDescriptorList,
            List<DeclarativeShadowVariableDescriptor<Solution_>> postChainFixedDescriptorList,
            TopologicalSorter topologicalSorter,
            ChangedVariableNotifier<Solution_> changedVariableNotifier,
            boolean canTerminateEarly,
            Function<Object, @Nullable Object> fixedEntityToFirstChainedEntity,
            Map<Object, Integer> fixedEntityToOrder,
            Map<Object, Object[]> fixedEntityToSuccessors,
            Object[] entities) {
        this.nextInChain = topologicalSorter.successor();
        this.chainedEntityToFixedEntity = topologicalSorter.key();
        this.chainOrderComparator = topologicalSorter.comparator();
        this.fixedEntityToFirstChainedEntity = fixedEntityToFirstChainedEntity;
        this.fixedEntityToOrder = fixedEntityToOrder;
        this.fixedEntityToSuccessors = fixedEntityToSuccessors;
        this.changedVariableNotifier = changedVariableNotifier;
        this.canTerminateEarly = canTerminateEarly;
        this.changedEntityList = new ArrayList<>();
        this.isUpdating = false;

        this.chainedEntityClass = chainedDescriptorList.get(0).getEntityDescriptor().getEntityClass();
        var firstFixedDescriptor = preChainFixedDescriptorList.isEmpty() ? postChainFixedDescriptorList.get(0)
                : preChainFixedDescriptorList.get(0);
        this.fixedEntityClass = firstFixedDescriptor.getEntityDescriptor().getEntityClass();
        var anyInverseSource = false;
        for (var descriptor : chainedDescriptorList) {
            for (var source : descriptor.getSources()) {
                anyInverseSource |= source.parentVariableType() == ParentVariableType.INVERSE;
            }
        }
        this.chainReadsPreChainVariable = anyInverseSource;

        this.monitoredSourceVariableSet = new HashSet<>();
        var chainedConsistencyState = consistencyTracker
                .getDeclarativeEntityConsistencyState(chainedDescriptorList.get(0).getEntityDescriptor());
        var fixedConsistencyState = consistencyTracker
                .getDeclarativeEntityConsistencyState(firstFixedDescriptor.getEntityDescriptor());
        var updaterId = 0;
        this.chainedUpdaters = new VariableUpdaterInfo[chainedDescriptorList.size()];
        for (var i = 0; i < chainedDescriptorList.size(); i++) {
            chainedUpdaters[i] = createUpdater(chainedDescriptorList.get(i), updaterId++, chainedConsistencyState);
        }
        this.preChainFixedUpdaters = new VariableUpdaterInfo[preChainFixedDescriptorList.size()];
        for (var i = 0; i < preChainFixedDescriptorList.size(); i++) {
            preChainFixedUpdaters[i] = createUpdater(preChainFixedDescriptorList.get(i), updaterId++, fixedConsistencyState);
        }
        this.postChainFixedUpdaters = new VariableUpdaterInfo[postChainFixedDescriptorList.size()];
        for (var i = 0; i < postChainFixedDescriptorList.size(); i++) {
            postChainFixedUpdaters[i] = createUpdater(postChainFixedDescriptorList.get(i), updaterId++, fixedConsistencyState);
        }

        // This graph structure cannot be inconsistent, and every entity gets an initial computation.
        for (var entity : entities) {
            if (chainedEntityClass.isInstance(entity)) {
                chainedConsistencyState.setEntityIsInconsistent(changedVariableNotifier, entity, false);
                changedEntityList.add(entity);
            } else if (fixedEntityClass.isInstance(entity)) {
                fixedConsistencyState.setEntityIsInconsistent(changedVariableNotifier, entity, false);
                changedEntityList.add(entity);
            }
        }
        updateChanged();
    }

    private VariableUpdaterInfo<Solution_> createUpdater(DeclarativeShadowVariableDescriptor<Solution_> descriptor,
            int updaterId, EntityConsistencyState<Solution_, Object> consistencyState) {
        for (var source : descriptor.getSources()) {
            for (var sourceReference : source.variableSourceReferences()) {
                monitoredSourceVariableSet.add(sourceReference.variableMetaModel());
            }
        }
        return new VariableUpdaterInfo<>(
                descriptor.getVariableMetaModel(), updaterId, descriptor, consistencyState,
                descriptor.getMemberAccessor(), descriptor.getCalculator());
    }

    @Override
    public void beforeVariableChanged(VariableMetaModel<?, ?, ?> variableReference, Object entity) {
        // The old owner of a moved element is marked dirty by beforeListVariableChanged instead.
    }

    @Override
    public void afterVariableChanged(VariableMetaModel<?, ?, ?> variableReference, Object entity) {
        if (!isUpdating && monitoredSourceVariableSet.contains(variableReference)
                && (chainedEntityClass.isInstance(entity) || fixedEntityClass.isInstance(entity))) {
            changedEntityList.add(entity);
        }
    }

    @Override
    public void beforeListVariableChanged(VariableMetaModel<?, ?, ?> variableReference, Object entity,
            List<Object> elementList, int fromIndex, int toIndex) {
        if (!isUpdating) {
            // Covers changes that no element event reports, such as removing the last element.
            changedEntityList.add(entity);
        }
    }

    @Override
    public void afterListVariableChanged(VariableMetaModel<?, ?, ?> variableReference, Object entity,
            List<Object> elementList, int fromIndex, int toIndex) {
        if (isUpdating) {
            return;
        }
        changedEntityList.add(entity);
        for (var elementIndex = fromIndex; elementIndex < toIndex; elementIndex++) {
            changedEntityList.add(elementList.get(elementIndex));
        }
    }

    @Override
    public void updateChanged() {
        if (changedEntityList.isEmpty()) {
            return;
        }
        isUpdating = true;

        var dirtyFixedEntitySet = Collections.newSetFromMap(new IdentityHashMap<>());
        var fixedEntityToDirtyChainStart = new IdentityHashMap<Object, Object>();
        var fixedEntityToDirtyChainEnd = new IdentityHashMap<Object, Object>();
        for (var entity : changedEntityList) {
            if (fixedEntityClass.isInstance(entity)) {
                dirtyFixedEntitySet.add(entity);
            } else if (chainedEntityClass.isInstance(entity)) {
                var fixedEntity = chainedEntityToFixedEntity.apply(entity);
                if (fixedEntity == null) {
                    // The entity is unassigned; reset its shadow variables,
                    // so re-assigning it to the same position is detected as a change.
                    for (var updater : chainedUpdaters) {
                        updater.updateIfChanged(entity, changedVariableNotifier);
                    }
                    continue;
                }
                dirtyFixedEntitySet.add(fixedEntity);
                var dirtyChainStart = fixedEntityToDirtyChainStart.get(fixedEntity);
                if (dirtyChainStart == null || chainOrderComparator.compare(entity, dirtyChainStart) < 0) {
                    fixedEntityToDirtyChainStart.put(fixedEntity, entity);
                }
                var dirtyChainEnd = fixedEntityToDirtyChainEnd.get(fixedEntity);
                if (dirtyChainEnd == null || chainOrderComparator.compare(entity, dirtyChainEnd) > 0) {
                    fixedEntityToDirtyChainEnd.put(fixedEntity, entity);
                }
            }
        }
        changedEntityList.clear();

        var queue = new PriorityQueue<>(
                Comparator.comparingInt(entity -> fixedEntityToOrder.getOrDefault(entity, Integer.MAX_VALUE)));
        var processedSet = Collections.newSetFromMap(new IdentityHashMap<>());
        queue.addAll(dirtyFixedEntitySet);
        while (!queue.isEmpty()) {
            var fixedEntity = queue.poll();
            if (!processedSet.add(fixedEntity)) {
                continue;
            }

            var anyFixedVariableChanged = false;
            for (var updater : preChainFixedUpdaters) {
                anyFixedVariableChanged |= updater.updateIfChanged(fixedEntity, changedVariableNotifier);
            }

            var chainStart = fixedEntityToDirtyChainStart.get(fixedEntity);
            var walkWholeChain = false;
            if (anyFixedVariableChanged && chainReadsPreChainVariable) {
                // A pre-chain variable changed and any element may read it through its inverse,
                // so the whole chain must be walked without early termination.
                walkWholeChain = true;
                var firstChainedEntity = fixedEntityToFirstChainedEntity.apply(fixedEntity);
                if (firstChainedEntity != null
                        && (chainStart == null || chainOrderComparator.compare(firstChainedEntity, chainStart) < 0)) {
                    chainStart = firstChainedEntity;
                }
            }
            if (chainStart != null) {
                var dirtyChainEnd = fixedEntityToDirtyChainEnd.get(fixedEntity);
                var current = chainStart;
                while (current != null) {
                    var anyChainedVariableChanged = false;
                    for (var updater : chainedUpdaters) {
                        anyChainedVariableChanged |= updater.updateIfChanged(current, changedVariableNotifier);
                    }
                    if (canTerminateEarly && !walkWholeChain && !anyChainedVariableChanged
                    // A swap can create multiple non-contiguous dirty elements on the same chain,
                    // so only terminate early once the last dirty element has been reached.
                            && (dirtyChainEnd == null || chainOrderComparator.compare(current, dirtyChainEnd) >= 0)) {
                        break;
                    }
                    current = nextInChain.apply(current);
                }
            }

            for (var updater : postChainFixedUpdaters) {
                anyFixedVariableChanged |= updater.updateIfChanged(fixedEntity, changedVariableNotifier);
            }
            if (anyFixedVariableChanged) {
                var successors = fixedEntityToSuccessors.get(fixedEntity);
                if (successors != null) {
                    for (var successor : successors) {
                        if (!processedSet.contains(successor)) {
                            queue.add(successor);
                        }
                    }
                }
            }
        }
        isUpdating = false;
    }
}
