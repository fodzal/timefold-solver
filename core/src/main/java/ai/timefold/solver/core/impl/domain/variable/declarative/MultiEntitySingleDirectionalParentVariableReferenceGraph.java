package ai.timefold.solver.core.impl.domain.variable.declarative;

import java.util.ArrayList;
import java.util.BitSet;
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
 * Each fixed entity is represented by up to three block nodes in a static graph:
 * a pre-chain node updating the variables its chain may read,
 * a chain node walking the entity's chain from the earliest dirty element,
 * and a post-chain node updating the variables that depend on the chain.
 * Cross-entity edges come from fact path sources, which cannot change during solving,
 * so the graph and its topological order are computed once at construction.
 * Dirty nodes are processed in that order, and successors are enqueued when a node changed.
 */
@NullMarked
public final class MultiEntitySingleDirectionalParentVariableReferenceGraph<Solution_> implements VariableReferenceGraph {

    /**
     * The block node ids of a fixed entity; an absent node is {@code -1}.
     * The chain node is always present, since every fixed entity owns a chain.
     */
    public record BlockNodes(Object fixedEntity, int preChainNodeId, int chainNodeId, int postChainNodeId) {
    }

    /**
     * The static block graph shared by all fixed entities:
     * a lookup from entity to its block nodes, the reverse lookup from node id,
     * and the topological order and successor lists computed once at construction.
     */
    public record BlockGraph(Map<Object, BlockNodes> fixedEntityToBlockNodes,
            BlockNodes[] nodeIdToBlockNodes,
            int[] nodeTopologicalOrderArray,
            int[][] nodeSuccessorArrays) {
    }

    private final UnaryOperator<@Nullable Object> nextInChain;
    private final UnaryOperator<@Nullable Object> chainedEntityToFixedEntity;
    private final Comparator<Object> chainOrderComparator;
    private final Function<Object, @Nullable Object> fixedEntityToFirstChainedEntity;

    private final VariableUpdaterInfo<Solution_>[] chainedUpdaters;
    private final VariableUpdaterInfo<Solution_>[] preChainFixedUpdaters;
    private final VariableUpdaterInfo<Solution_>[] postChainFixedUpdaters;

    private final Map<Object, BlockNodes> fixedEntityToBlockNodes;
    private final BlockNodes[] nodeIdToBlockNodes;
    private final int[] nodeTopologicalOrderArray;
    private final int[][] nodeSuccessorArrays;

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
            BlockGraph blockGraph,
            Object[] entities) {
        this.nextInChain = topologicalSorter.successor();
        this.chainedEntityToFixedEntity = topologicalSorter.key();
        this.chainOrderComparator = topologicalSorter.comparator();
        this.fixedEntityToFirstChainedEntity = fixedEntityToFirstChainedEntity;
        this.fixedEntityToBlockNodes = blockGraph.fixedEntityToBlockNodes();
        this.nodeIdToBlockNodes = blockGraph.nodeIdToBlockNodes();
        this.nodeTopologicalOrderArray = blockGraph.nodeTopologicalOrderArray();
        this.nodeSuccessorArrays = blockGraph.nodeSuccessorArrays();
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

        var fixedEntityToDirtyChainStart = new IdentityHashMap<Object, Object>();
        var fixedEntityToDirtyChainEnd = new IdentityHashMap<Object, Object>();
        var nodeQueue = new PriorityQueue<Integer>(Comparator.comparingInt(nodeId -> nodeTopologicalOrderArray[nodeId]));
        var enqueuedNodeSet = new BitSet(nodeIdToBlockNodes.length);
        for (var entity : changedEntityList) {
            if (fixedEntityClass.isInstance(entity)) {
                var blockNodes = fixedEntityToBlockNodes.get(entity);
                enqueue(nodeQueue, enqueuedNodeSet, blockNodes.preChainNodeId());
                enqueue(nodeQueue, enqueuedNodeSet, blockNodes.postChainNodeId());
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
                var dirtyChainStart = fixedEntityToDirtyChainStart.get(fixedEntity);
                if (dirtyChainStart == null || chainOrderComparator.compare(entity, dirtyChainStart) < 0) {
                    fixedEntityToDirtyChainStart.put(fixedEntity, entity);
                }
                var dirtyChainEnd = fixedEntityToDirtyChainEnd.get(fixedEntity);
                if (dirtyChainEnd == null || chainOrderComparator.compare(entity, dirtyChainEnd) > 0) {
                    fixedEntityToDirtyChainEnd.put(fixedEntity, entity);
                }
                enqueue(nodeQueue, enqueuedNodeSet, fixedEntityToBlockNodes.get(fixedEntity).chainNodeId());
            }
        }
        changedEntityList.clear();

        var walkWholeChainFixedEntitySet = Collections.newSetFromMap(new IdentityHashMap<>());
        while (!nodeQueue.isEmpty()) {
            var nodeId = nodeQueue.poll();
            var blockNodes = nodeIdToBlockNodes[nodeId];
            var fixedEntity = blockNodes.fixedEntity();
            boolean anyVariableChanged;
            if (nodeId == blockNodes.chainNodeId()) {
                anyVariableChanged = walkChain(fixedEntity,
                        fixedEntityToDirtyChainStart.get(fixedEntity),
                        fixedEntityToDirtyChainEnd.get(fixedEntity),
                        walkWholeChainFixedEntitySet.contains(fixedEntity));
            } else {
                var updaters = nodeId == blockNodes.preChainNodeId() ? preChainFixedUpdaters : postChainFixedUpdaters;
                anyVariableChanged = false;
                for (var updater : updaters) {
                    anyVariableChanged |= updater.updateIfChanged(fixedEntity, changedVariableNotifier);
                }
                if (anyVariableChanged && chainReadsPreChainVariable && nodeId == blockNodes.preChainNodeId()) {
                    // A pre-chain variable changed and any element may read it through its inverse,
                    // so the whole chain must be walked without early termination.
                    walkWholeChainFixedEntitySet.add(fixedEntity);
                }
            }
            if (anyVariableChanged) {
                for (var successor : nodeSuccessorArrays[nodeId]) {
                    enqueue(nodeQueue, enqueuedNodeSet, successor);
                }
            }
        }
        isUpdating = false;
    }

    private static void enqueue(PriorityQueue<Integer> nodeQueue, BitSet enqueuedNodeSet, int nodeId) {
        // The graph is acyclic and processed in topological order,
        // so a node can only be enqueued by not-yet-processed predecessors;
        // once enqueued, it never needs to be enqueued again.
        if (nodeId >= 0 && !enqueuedNodeSet.get(nodeId)) {
            enqueuedNodeSet.set(nodeId);
            nodeQueue.add(nodeId);
        }
    }

    private boolean walkChain(Object fixedEntity, @Nullable Object dirtyChainStart, @Nullable Object dirtyChainEnd,
            boolean walkWholeChain) {
        var chainStart = dirtyChainStart;
        if (walkWholeChain) {
            var firstChainedEntity = fixedEntityToFirstChainedEntity.apply(fixedEntity);
            if (firstChainedEntity != null
                    && (chainStart == null || chainOrderComparator.compare(firstChainedEntity, chainStart) < 0)) {
                chainStart = firstChainedEntity;
            }
        }
        if (chainStart == null) {
            return false;
        }
        var anyChainedVariableChangedInWalk = false;
        var current = chainStart;
        while (current != null) {
            var anyChainedVariableChanged = false;
            for (var updater : chainedUpdaters) {
                anyChainedVariableChanged |= updater.updateIfChanged(current, changedVariableNotifier);
            }
            anyChainedVariableChangedInWalk |= anyChainedVariableChanged;
            if (canTerminateEarly && !walkWholeChain && !anyChainedVariableChanged
            // A swap can create multiple non-contiguous dirty elements on the same chain,
            // so only terminate early once the last dirty element has been reached.
                    && (dirtyChainEnd == null || chainOrderComparator.compare(current, dirtyChainEnd) >= 0)) {
                break;
            }
            current = nextInChain.apply(current);
        }
        return anyChainedVariableChangedInWalk;
    }
}
