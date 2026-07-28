package ai.timefold.solver.core.impl.domain.variable.declarative;

import java.util.ArrayList;
import java.util.Arrays;
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
 * A variable reference graph for {@link GraphStructure#MULTI_ENTITY_SINGLE_DIRECTIONAL_PARENT_WITH_PRECEDENCE}.
 * <p>
 * Like {@link MultiEntitySingleDirectionalParentVariableReferenceGraph},
 * dirty fixed entities are processed in the topological order of their fact DAG
 * and each dirty chain is walked from its earliest dirty element.
 * Additionally, chained entities may depend on declarative variables of other chained entities
 * through static fact references (precedences).
 * When a walked entity changes, its precedence successors are enqueued:
 * on the same chain ahead of the walk the dirty range is extended,
 * on a not-yet-processed fixed entity that entity's dirty range is extended,
 * and otherwise the successor is kept for another round of processing,
 * which repeats until no entity changes.
 * <p>
 * Precedence edges combined with the dynamic chains can form dependency cycles
 * (e.g. a visit scheduled before the visit it must succeed).
 * Cycles are detected with a strongly connected component computation
 * over a condensed graph containing only the precedence-linked chained entities
 * and one node per fixed entity tier (pre-chain and post-chain variables),
 * recomputed only when a list change touches a linked entity.
 * Entities on a cycle, and entities that transitively depend on one,
 * are marked inconsistent and their cycle-susceptible declarative variables are set to null,
 * mirroring the arbitrary graph;
 * the detection in {@link GraphStructure} guarantees this entity-level tracking is exact.
 */
@NullMarked
public final class MultiEntityPrecedenceVariableReferenceGraph<Solution_> implements VariableReferenceGraph {

    /**
     * A fact DAG edge between two fixed entities, refined by the variable tiers it connects:
     * the referenced variable of the predecessor (source) and the depending variable (target)
     * are each either pre-chain or post-chain.
     */
    record FixedTierEdge(Object otherEntity, boolean sourcePost, boolean targetPost) {
    }

    /**
     * The static precedence structure, computed once at construction from the entities' facts.
     */
    record PrecedenceStructure(
            Map<Object, Object[]> predecessorMap,
            Map<Object, Object[]> successorMap,
            Map<Object, FixedTierEdge[]> incomingTierEdgeMap,
            Map<Object, FixedTierEdge[]> outgoingTierEdgeMap,
            boolean staticPreToPostDependency,
            Set<VariableMetaModel<?, ?, ?>> chainedSusceptibleVariableSet,
            Set<VariableMetaModel<?, ?, ?>> preChainSusceptibleVariableSet) {
    }

    private final UnaryOperator<@Nullable Object> nextInChain;
    private final UnaryOperator<@Nullable Object> previousInChain;
    private final UnaryOperator<@Nullable Object> chainedEntityToFixedEntity;
    private final Comparator<Object> chainOrderComparator;
    private final Function<Object, @Nullable Object> fixedEntityToFirstChainedEntity;
    private final Function<Object, @Nullable Object> fixedEntityToLastChainedEntity;

    private final VariableUpdaterInfo<Solution_>[] chainedUpdaters;
    private final VariableUpdaterInfo<Solution_>[] preChainFixedUpdaters;
    private final VariableUpdaterInfo<Solution_>[] postChainFixedUpdaters;
    private final boolean[] chainedSusceptible;
    private final boolean[] preChainFixedSusceptible;

    private final Map<Object, Integer> fixedEntityToOrder;
    private final Map<Object, Object[]> fixedEntityToSuccessors;
    private final Object[] fixedEntitiesByOrder;

    private final Map<Object, Object[]> precedencePredecessorMap;
    private final Map<Object, Object[]> precedenceSuccessorMap;
    private final Map<Object, FixedTierEdge[]> incomingTierEdgeMap;
    private final Map<Object, FixedTierEdge[]> outgoingTierEdgeMap;
    private final boolean staticPreToPostDependency;
    private final Object[] linkedChainedEntities;
    private final Map<Object, Integer> linkedChainedEntityToNodeIndex;

    private final Class<?> chainedEntityClass;
    private final Class<?> fixedEntityClass;
    private final EntityConsistencyState<Solution_, Object> chainedConsistencyState;
    private final EntityConsistencyState<Solution_, Object> fixedConsistencyState;

    private final Set<VariableMetaModel<?, ?, ?>> monitoredSourceVariableSet;
    private final ChangedVariableNotifier<Solution_> changedVariableNotifier;
    private final boolean canTerminateEarly;
    private final boolean chainReadsPreChainVariable;
    private final int maxRoundCount;

    private final List<Object> changedEntityList;
    private boolean isUpdating;
    private boolean structuralChangePending;

    // Cycle membership, recomputed on structural changes.
    private Set<Object> cycleMemberChainedEntitySet;
    private Set<Object> cycleMemberPreTierSet;
    private Set<Object> cycleMemberPostTierSet;

    // Current tier inconsistency, maintained during updates.
    private final Set<Object> preTierInconsistentSet;
    private final Set<Object> postTierInconsistentSet;

    // Per-round state, reused to avoid allocation.
    private final Set<Object> dirtyFixedEntitySet;
    private final Map<Object, Object> dirtyChainStartMap;
    private final Map<Object, Object> dirtyChainEndMap;
    private final PriorityQueue<Object> fixedEntityQueue;
    private final Set<Object> processedFixedEntitySet;

    @SuppressWarnings("unchecked")
    MultiEntityPrecedenceVariableReferenceGraph(
            ConsistencyTracker<Solution_> consistencyTracker,
            List<DeclarativeShadowVariableDescriptor<Solution_>> chainedDescriptorList,
            List<DeclarativeShadowVariableDescriptor<Solution_>> preChainFixedDescriptorList,
            List<DeclarativeShadowVariableDescriptor<Solution_>> postChainFixedDescriptorList,
            TopologicalSorter topologicalSorter,
            UnaryOperator<@Nullable Object> previousInChain,
            ChangedVariableNotifier<Solution_> changedVariableNotifier,
            boolean canTerminateEarly,
            Function<Object, @Nullable Object> fixedEntityToFirstChainedEntity,
            Function<Object, @Nullable Object> fixedEntityToLastChainedEntity,
            Map<Object, Integer> fixedEntityToOrder,
            Map<Object, Object[]> fixedEntityToSuccessors,
            PrecedenceStructure precedenceStructure,
            Object[] entities) {
        this.nextInChain = topologicalSorter.successor();
        this.previousInChain = previousInChain;
        this.chainedEntityToFixedEntity = topologicalSorter.key();
        this.chainOrderComparator = topologicalSorter.comparator();
        this.fixedEntityToFirstChainedEntity = fixedEntityToFirstChainedEntity;
        this.fixedEntityToLastChainedEntity = fixedEntityToLastChainedEntity;
        this.fixedEntityToOrder = fixedEntityToOrder;
        this.fixedEntityToSuccessors = fixedEntityToSuccessors;
        this.fixedEntitiesByOrder = new Object[fixedEntityToOrder.size()];
        for (var entry : fixedEntityToOrder.entrySet()) {
            fixedEntitiesByOrder[entry.getValue()] = entry.getKey();
        }
        this.precedencePredecessorMap = precedenceStructure.predecessorMap();
        this.precedenceSuccessorMap = precedenceStructure.successorMap();
        this.incomingTierEdgeMap = precedenceStructure.incomingTierEdgeMap();
        this.outgoingTierEdgeMap = precedenceStructure.outgoingTierEdgeMap();
        this.staticPreToPostDependency = precedenceStructure.staticPreToPostDependency();
        var linkedSet = Collections.newSetFromMap(new IdentityHashMap<>());
        linkedSet.addAll(precedencePredecessorMap.keySet());
        linkedSet.addAll(precedenceSuccessorMap.keySet());
        this.linkedChainedEntities = linkedSet.toArray();
        this.linkedChainedEntityToNodeIndex = new IdentityHashMap<>();
        for (var i = 0; i < linkedChainedEntities.length; i++) {
            linkedChainedEntityToNodeIndex.put(linkedChainedEntities[i], i);
        }
        this.changedVariableNotifier = changedVariableNotifier;
        this.canTerminateEarly = canTerminateEarly;
        this.maxRoundCount = entities.length + 2;
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
        this.chainedConsistencyState = consistencyTracker
                .getDeclarativeEntityConsistencyState(chainedDescriptorList.get(0).getEntityDescriptor());
        this.fixedConsistencyState = consistencyTracker
                .getDeclarativeEntityConsistencyState(firstFixedDescriptor.getEntityDescriptor());
        var updaterId = 0;
        this.chainedUpdaters = new VariableUpdaterInfo[chainedDescriptorList.size()];
        this.chainedSusceptible = new boolean[chainedDescriptorList.size()];
        for (var i = 0; i < chainedDescriptorList.size(); i++) {
            chainedUpdaters[i] = createUpdater(chainedDescriptorList.get(i), updaterId++, chainedConsistencyState);
            chainedSusceptible[i] = precedenceStructure.chainedSusceptibleVariableSet()
                    .contains(chainedDescriptorList.get(i).getVariableMetaModel());
        }
        this.preChainFixedUpdaters = new VariableUpdaterInfo[preChainFixedDescriptorList.size()];
        this.preChainFixedSusceptible = new boolean[preChainFixedDescriptorList.size()];
        for (var i = 0; i < preChainFixedDescriptorList.size(); i++) {
            preChainFixedUpdaters[i] = createUpdater(preChainFixedDescriptorList.get(i), updaterId++, fixedConsistencyState);
            preChainFixedSusceptible[i] = precedenceStructure.preChainSusceptibleVariableSet()
                    .contains(preChainFixedDescriptorList.get(i).getVariableMetaModel());
        }
        this.postChainFixedUpdaters = new VariableUpdaterInfo[postChainFixedDescriptorList.size()];
        for (var i = 0; i < postChainFixedDescriptorList.size(); i++) {
            postChainFixedUpdaters[i] = createUpdater(postChainFixedDescriptorList.get(i), updaterId++, fixedConsistencyState);
        }

        this.cycleMemberChainedEntitySet = Collections.newSetFromMap(new IdentityHashMap<>());
        this.cycleMemberPreTierSet = Collections.newSetFromMap(new IdentityHashMap<>());
        this.cycleMemberPostTierSet = Collections.newSetFromMap(new IdentityHashMap<>());
        this.preTierInconsistentSet = Collections.newSetFromMap(new IdentityHashMap<>());
        this.postTierInconsistentSet = Collections.newSetFromMap(new IdentityHashMap<>());
        this.dirtyFixedEntitySet = Collections.newSetFromMap(new IdentityHashMap<>());
        this.dirtyChainStartMap = new IdentityHashMap<>();
        this.dirtyChainEndMap = new IdentityHashMap<>();
        this.fixedEntityQueue = new PriorityQueue<>(
                Comparator.comparingInt(entity -> fixedEntityToOrder.getOrDefault(entity, Integer.MAX_VALUE)));
        this.processedFixedEntitySet = Collections.newSetFromMap(new IdentityHashMap<>());

        // Every entity starts consistent and gets an initial computation,
        // which recomputes the actual consistency.
        for (var entity : entities) {
            if (chainedEntityClass.isInstance(entity)) {
                chainedConsistencyState.setEntityIsInconsistent(changedVariableNotifier, entity, false);
                changedEntityList.add(entity);
            } else if (fixedEntityClass.isInstance(entity)) {
                fixedConsistencyState.setEntityIsInconsistent(changedVariableNotifier, entity, false);
                changedEntityList.add(entity);
            }
        }
        this.structuralChangePending = true;
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
        if (isUpdating) {
            return;
        }
        // Covers changes that no element event reports, such as removing the last element.
        changedEntityList.add(entity);
        markStructuralChangeIfNeeded(elementList, fromIndex, toIndex);
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
        markStructuralChangeIfNeeded(elementList, fromIndex, toIndex);
    }

    /**
     * The condensed graph's topology only changes when a precedence-linked entity
     * changes position relative to other linked entities,
     * or when a list becomes empty or non-empty
     * (which toggles the pre-chain to post-chain condensed edge through the elements).
     * All other moves keep the previous cycle membership valid.
     */
    private void markStructuralChangeIfNeeded(List<Object> elementList, int fromIndex, int toIndex) {
        if (structuralChangePending) {
            return;
        }
        for (var elementIndex = fromIndex; elementIndex < toIndex; elementIndex++) {
            if (linkedChainedEntityToNodeIndex.containsKey(elementList.get(elementIndex))) {
                structuralChangePending = true;
                return;
            }
        }
        if (postChainFixedUpdaters.length > 0 && chainReadsPreChainVariable && !staticPreToPostDependency
                && fromIndex == 0 && toIndex == elementList.size()) {
            // The entire list is affected, so it may transition between empty and non-empty.
            structuralChangePending = true;
        }
    }

    @Override
    public void updateChanged() {
        if (changedEntityList.isEmpty() && !structuralChangePending) {
            return;
        }
        isUpdating = true;
        if (structuralChangePending) {
            recomputeCycleMembers();
            structuralChangePending = false;
        }
        var remainingRounds = maxRoundCount;
        while (!changedEntityList.isEmpty()) {
            if (remainingRounds-- <= 0) {
                throw new IllegalStateException(
                        "Impossible state: the precedence propagation did not converge within the round limit (%d)."
                                .formatted(maxRoundCount));
            }
            processRound();
        }
        isUpdating = false;
    }

    /**
     * Processes the currently dirty entities;
     * precedence successors that could not be handled within this round
     * are added back to the changed entity list, triggering another round.
     */
    private void processRound() {
        dirtyFixedEntitySet.clear();
        dirtyChainStartMap.clear();
        dirtyChainEndMap.clear();
        processedFixedEntitySet.clear();
        // Indexed loop: processing an unassigned entity may append its precedence successors.
        for (var i = 0; i < changedEntityList.size(); i++) {
            var entity = changedEntityList.get(i);
            if (fixedEntityClass.isInstance(entity)) {
                dirtyFixedEntitySet.add(entity);
            } else if (chainedEntityClass.isInstance(entity)) {
                var fixedEntity = chainedEntityToFixedEntity.apply(entity);
                if (fixedEntity == null) {
                    processUnassignedChainedEntity(entity);
                    continue;
                }
                dirtyFixedEntitySet.add(fixedEntity);
                extendDirtyChainRange(fixedEntity, entity);
            }
        }
        changedEntityList.clear();

        fixedEntityQueue.addAll(dirtyFixedEntitySet);
        while (!fixedEntityQueue.isEmpty()) {
            var fixedEntity = fixedEntityQueue.poll();
            if (!processedFixedEntitySet.add(fixedEntity)) {
                continue;
            }
            processFixedEntity(fixedEntity);
        }
    }

    private void extendDirtyChainRange(Object fixedEntity, Object chainedEntity) {
        var dirtyChainStart = dirtyChainStartMap.get(fixedEntity);
        if (dirtyChainStart == null || chainOrderComparator.compare(chainedEntity, dirtyChainStart) < 0) {
            dirtyChainStartMap.put(fixedEntity, chainedEntity);
        }
        var dirtyChainEnd = dirtyChainEndMap.get(fixedEntity);
        if (dirtyChainEnd == null || chainOrderComparator.compare(chainedEntity, dirtyChainEnd) > 0) {
            dirtyChainEndMap.put(fixedEntity, chainedEntity);
        }
    }

    /**
     * An unassigned entity has no chain, but it may still depend on its precedence predecessors,
     * whose inconsistency propagates to it, matching the arbitrary graph's static fact edges.
     * Its variables are also reset, so re-assigning it to the same position is detected as a change.
     */
    private void processUnassignedChainedEntity(Object entity) {
        var inconsistent = hasInconsistentPrecedencePredecessor(entity);
        var anyChanged = false;
        for (var i = 0; i < chainedUpdaters.length; i++) {
            anyChanged |= updateVariable(chainedUpdaters[i], entity, inconsistent && chainedSusceptible[i]);
        }
        anyChanged |= updateEntityInconsistency(chainedConsistencyState, entity, inconsistent);
        if (anyChanged) {
            var successors = precedenceSuccessorMap.get(entity);
            if (successors != null) {
                Collections.addAll(changedEntityList, successors);
            }
        }
    }

    private void processFixedEntity(Object fixedEntity) {
        var anyFixedVariableChanged = false;
        var preTierInconsistent = isPreTierInconsistent(fixedEntity);
        for (var i = 0; i < preChainFixedUpdaters.length; i++) {
            anyFixedVariableChanged |=
                    updateVariable(preChainFixedUpdaters[i], fixedEntity, preTierInconsistent && preChainFixedSusceptible[i]);
        }
        anyFixedVariableChanged |= updateTierFlag(preTierInconsistentSet, fixedEntity, preTierInconsistent);

        var chainStart = dirtyChainStartMap.get(fixedEntity);
        var walkWholeChain = false;
        if (anyFixedVariableChanged && chainReadsPreChainVariable) {
            // A pre-chain variable or the pre-chain tier's consistency changed
            // and any element may read it through its inverse,
            // so the whole chain must be walked without early termination.
            walkWholeChain = true;
            var firstChainedEntity = fixedEntityToFirstChainedEntity.apply(fixedEntity);
            if (firstChainedEntity != null
                    && (chainStart == null || chainOrderComparator.compare(firstChainedEntity, chainStart) < 0)) {
                chainStart = firstChainedEntity;
            }
        }
        if (chainStart != null) {
            var dirtyChainEnd = dirtyChainEndMap.get(fixedEntity);
            var previousElement = previousInChain.apply(chainStart);
            var previousInconsistent = previousElement != null && isChainedEntityInconsistent(previousElement);
            var current = chainStart;
            while (current != null) {
                var inconsistent = previousInconsistent
                        || (chainReadsPreChainVariable && preTierInconsistent)
                        || cycleMemberChainedEntitySet.contains(current)
                        || hasInconsistentPrecedencePredecessor(current);
                var anyChainedVariableChanged = false;
                for (var i = 0; i < chainedUpdaters.length; i++) {
                    anyChainedVariableChanged |=
                            updateVariable(chainedUpdaters[i], current, inconsistent && chainedSusceptible[i]);
                }
                anyChainedVariableChanged |= updateEntityInconsistency(chainedConsistencyState, current, inconsistent);
                if (anyChainedVariableChanged) {
                    dirtyChainEnd = enqueuePrecedenceSuccessors(current, fixedEntity, dirtyChainEnd);
                }
                if (canTerminateEarly && !walkWholeChain && !anyChainedVariableChanged
                // A swap can create multiple non-contiguous dirty elements on the same chain,
                // so only terminate early once the last dirty element has been reached.
                        && (dirtyChainEnd == null || chainOrderComparator.compare(current, dirtyChainEnd) >= 0)) {
                    break;
                }
                previousInconsistent = inconsistent;
                current = nextInChain.apply(current);
            }
        }

        var postTierInconsistent = isPostTierInconsistent(fixedEntity, preTierInconsistent);
        for (var postChainFixedUpdater : postChainFixedUpdaters) {
            // Every post-chain variable depends on the elements, so all of them are susceptible.
            anyFixedVariableChanged |= updateVariable(postChainFixedUpdater, fixedEntity, postTierInconsistent);
        }
        anyFixedVariableChanged |= updateTierFlag(postTierInconsistentSet, fixedEntity, postTierInconsistent);
        anyFixedVariableChanged |= updateEntityInconsistency(fixedConsistencyState, fixedEntity,
                preTierInconsistent || postTierInconsistent);

        if (anyFixedVariableChanged) {
            var successors = fixedEntityToSuccessors.get(fixedEntity);
            if (successors != null) {
                for (var successor : successors) {
                    if (processedFixedEntitySet.contains(successor)) {
                        // A precedence brought this entity out of DAG order; reprocess the successor next round.
                        changedEntityList.add(successor);
                    } else {
                        fixedEntityQueue.add(successor);
                    }
                }
            }
        }
    }

    private @Nullable Object enqueuePrecedenceSuccessors(Object chainedEntity, Object currentFixedEntity,
            @Nullable Object dirtyChainEnd) {
        var successors = precedenceSuccessorMap.get(chainedEntity);
        if (successors == null) {
            return dirtyChainEnd;
        }
        for (var successor : successors) {
            var successorFixedEntity = chainedEntityToFixedEntity.apply(successor);
            if (successorFixedEntity == currentFixedEntity) {
                if (chainOrderComparator.compare(successor, chainedEntity) > 0) {
                    // The walk has not reached the successor yet; extend the dirty range.
                    if (dirtyChainEnd == null || chainOrderComparator.compare(successor, dirtyChainEnd) > 0) {
                        dirtyChainEnd = successor;
                    }
                } else {
                    // The walk already passed it; reprocess it in the next round.
                    changedEntityList.add(successor);
                }
            } else if (successorFixedEntity != null && !processedFixedEntitySet.contains(successorFixedEntity)) {
                extendDirtyChainRange(successorFixedEntity, successor);
                fixedEntityQueue.add(successorFixedEntity);
            } else {
                // Unassigned, or its fixed entity was already processed in this round.
                changedEntityList.add(successor);
            }
        }
        return dirtyChainEnd;
    }

    private boolean updateVariable(VariableUpdaterInfo<Solution_> updater, Object entity, boolean forceNull) {
        return forceNull ? updater.updateIfChanged(entity, null, changedVariableNotifier)
                : updater.updateIfChanged(entity, changedVariableNotifier);
    }

    private <Entity_> boolean updateEntityInconsistency(EntityConsistencyState<Solution_, Entity_> consistencyState,
            Entity_ entity, boolean inconsistent) {
        var oldValue = consistencyState.getEntityInconsistentValue(entity);
        if (oldValue != null && oldValue == inconsistent) {
            return false;
        }
        consistencyState.setEntityIsInconsistent(changedVariableNotifier, entity, inconsistent);
        return true;
    }

    private static boolean updateTierFlag(Set<Object> tierInconsistentSet, Object fixedEntity, boolean inconsistent) {
        return inconsistent ? tierInconsistentSet.add(fixedEntity) : tierInconsistentSet.remove(fixedEntity);
    }

    private boolean isChainedEntityInconsistent(Object chainedEntity) {
        return Boolean.TRUE.equals(chainedConsistencyState.getEntityInconsistentValue(chainedEntity));
    }

    private boolean hasInconsistentPrecedencePredecessor(Object chainedEntity) {
        var predecessors = precedencePredecessorMap.get(chainedEntity);
        if (predecessors == null) {
            return false;
        }
        for (var predecessor : predecessors) {
            if (isChainedEntityInconsistent(predecessor)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPreTierInconsistent(Object fixedEntity) {
        return cycleMemberPreTierSet.contains(fixedEntity)
                || hasInconsistentTierPredecessor(fixedEntity, false);
    }

    private boolean isPostTierInconsistent(Object fixedEntity, boolean preTierInconsistent) {
        if (cycleMemberPostTierSet.contains(fixedEntity)) {
            return true;
        }
        if (staticPreToPostDependency && preTierInconsistent) {
            return true;
        }
        // Inconsistency propagates along the chain,
        // so if any element is inconsistent, the last element in dependency order is too.
        var lastChainedEntity = fixedEntityToLastChainedEntity.apply(fixedEntity);
        if (lastChainedEntity != null && isChainedEntityInconsistent(lastChainedEntity)) {
            return true;
        }
        return hasInconsistentTierPredecessor(fixedEntity, true);
    }

    private boolean hasInconsistentTierPredecessor(Object fixedEntity, boolean targetPost) {
        var incomingEdges = incomingTierEdgeMap.get(fixedEntity);
        if (incomingEdges == null) {
            return false;
        }
        for (var edge : incomingEdges) {
            if (edge.targetPost() != targetPost) {
                continue;
            }
            var sourceInconsistent = edge.sourcePost() ? postTierInconsistentSet.contains(edge.otherEntity())
                    : preTierInconsistentSet.contains(edge.otherEntity());
            if (sourceInconsistent) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recomputes which entities are in a dependency cycle,
     * using a strongly connected component computation over the condensed graph.
     * Entities whose membership changed are marked dirty,
     * so the following rounds recompute their variables and consistency.
     */
    private void recomputeCycleMembers() {
        var linkedCount = linkedChainedEntities.length;
        var fixedCount = fixedEntitiesByOrder.length;
        var includeTierNodes = postChainFixedUpdaters.length > 0;
        var nodeCount = linkedCount + (includeTierNodes ? 2 * fixedCount : 0);
        var edgeList = new ArrayList<int[]>();

        // Precedence edges are static.
        for (var nodeIndex = 0; nodeIndex < linkedCount; nodeIndex++) {
            var successors = precedenceSuccessorMap.get(linkedChainedEntities[nodeIndex]);
            if (successors != null) {
                for (var successor : successors) {
                    edgeList.add(new int[] { nodeIndex, linkedChainedEntityToNodeIndex.get(successor) });
                }
            }
        }
        // Chain reachability between linked entities of the same fixed entity,
        // condensed to edges between consecutive linked entities in dependency order,
        // and the edges between linked entities and their fixed entity's tiers.
        var fixedEntityToLinkedList = new IdentityHashMap<Object, List<Object>>();
        for (var linkedChainedEntity : linkedChainedEntities) {
            var fixedEntity = chainedEntityToFixedEntity.apply(linkedChainedEntity);
            if (fixedEntity != null) {
                fixedEntityToLinkedList.computeIfAbsent(fixedEntity, ignored -> new ArrayList<>())
                        .add(linkedChainedEntity);
            }
        }
        for (var entry : fixedEntityToLinkedList.entrySet()) {
            var linkedList = entry.getValue();
            linkedList.sort(chainOrderComparator);
            for (var i = 0; i < linkedList.size() - 1; i++) {
                edgeList.add(new int[] {
                        linkedChainedEntityToNodeIndex.get(linkedList.get(i)),
                        linkedChainedEntityToNodeIndex.get(linkedList.get(i + 1)) });
            }
            if (includeTierNodes) {
                var order = fixedEntityToOrder.get(entry.getKey());
                for (var linkedChainedEntity : linkedList) {
                    var linkedNodeIndex = linkedChainedEntityToNodeIndex.get(linkedChainedEntity);
                    edgeList.add(new int[] { linkedNodeIndex, postTierNode(linkedCount, order) });
                    if (chainReadsPreChainVariable) {
                        edgeList.add(new int[] { preTierNode(linkedCount, order), linkedNodeIndex });
                    }
                }
            }
        }
        if (includeTierNodes) {
            for (var order = 0; order < fixedCount; order++) {
                var fixedEntity = fixedEntitiesByOrder[order];
                if (staticPreToPostDependency
                        || (chainReadsPreChainVariable && fixedEntityToFirstChainedEntity.apply(fixedEntity) != null)) {
                    edgeList.add(new int[] { preTierNode(linkedCount, order), postTierNode(linkedCount, order) });
                }
                var outgoingEdges = outgoingTierEdgeMap.get(fixedEntity);
                if (outgoingEdges != null) {
                    for (var tierEdge : outgoingEdges) {
                        var targetOrder = fixedEntityToOrder.get(tierEdge.otherEntity());
                        edgeList.add(new int[] {
                                tierEdge.sourcePost() ? postTierNode(linkedCount, order) : preTierNode(linkedCount, order),
                                tierEdge.targetPost() ? postTierNode(linkedCount, targetOrder)
                                        : preTierNode(linkedCount, targetOrder) });
                    }
                }
            }
        }

        var inCycle = computeNodesInCycles(toAdjacency(nodeCount, edgeList));
        var newChainedMemberSet = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        for (var nodeIndex = 0; nodeIndex < linkedCount; nodeIndex++) {
            if (inCycle[nodeIndex]) {
                newChainedMemberSet.add(linkedChainedEntities[nodeIndex]);
            }
        }
        var newPreTierMemberSet = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        var newPostTierMemberSet = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        if (includeTierNodes) {
            for (var order = 0; order < fixedCount; order++) {
                if (inCycle[preTierNode(linkedCount, order)]) {
                    newPreTierMemberSet.add(fixedEntitiesByOrder[order]);
                }
                if (inCycle[postTierNode(linkedCount, order)]) {
                    newPostTierMemberSet.add(fixedEntitiesByOrder[order]);
                }
            }
        }
        markMembershipDifferencesDirty(cycleMemberChainedEntitySet, newChainedMemberSet);
        markMembershipDifferencesDirty(cycleMemberPreTierSet, newPreTierMemberSet);
        markMembershipDifferencesDirty(cycleMemberPostTierSet, newPostTierMemberSet);
        cycleMemberChainedEntitySet = newChainedMemberSet;
        cycleMemberPreTierSet = newPreTierMemberSet;
        cycleMemberPostTierSet = newPostTierMemberSet;
    }

    private static int preTierNode(int linkedCount, int order) {
        return linkedCount + 2 * order;
    }

    private static int postTierNode(int linkedCount, int order) {
        return linkedCount + 2 * order + 1;
    }

    private void markMembershipDifferencesDirty(Set<Object> oldMemberSet, Set<Object> newMemberSet) {
        for (var entity : oldMemberSet) {
            if (!newMemberSet.contains(entity)) {
                changedEntityList.add(entity);
            }
        }
        for (var entity : newMemberSet) {
            if (!oldMemberSet.contains(entity)) {
                changedEntityList.add(entity);
            }
        }
    }

    private static int[][] toAdjacency(int nodeCount, List<int[]> edgeList) {
        var edgeCounts = new int[nodeCount];
        for (var edge : edgeList) {
            edgeCounts[edge[0]]++;
        }
        var adjacency = new int[nodeCount][];
        for (var node = 0; node < nodeCount; node++) {
            adjacency[node] = new int[edgeCounts[node]];
        }
        var fillCounts = new int[nodeCount];
        for (var edge : edgeList) {
            adjacency[edge[0]][fillCounts[edge[0]]++] = edge[1];
        }
        return adjacency;
    }

    /**
     * Iterative Tarjan; returns which nodes are in a strongly connected component of size greater than one.
     */
    private static boolean[] computeNodesInCycles(int[][] adjacency) {
        var nodeCount = adjacency.length;
        var discoveryIndex = new int[nodeCount];
        Arrays.fill(discoveryIndex, -1);
        var lowLink = new int[nodeCount];
        var onStack = new boolean[nodeCount];
        var componentStack = new int[nodeCount];
        var componentStackSize = 0;
        var inCycle = new boolean[nodeCount];
        var frameNode = new int[nodeCount];
        var frameEdge = new int[nodeCount];
        var nextIndex = 0;
        for (var root = 0; root < nodeCount; root++) {
            if (discoveryIndex[root] != -1) {
                continue;
            }
            var frameCount = 0;
            frameNode[frameCount] = root;
            frameEdge[frameCount] = 0;
            frameCount++;
            discoveryIndex[root] = lowLink[root] = nextIndex++;
            componentStack[componentStackSize++] = root;
            onStack[root] = true;
            while (frameCount > 0) {
                var node = frameNode[frameCount - 1];
                var edgeIndex = frameEdge[frameCount - 1];
                if (edgeIndex < adjacency[node].length) {
                    frameEdge[frameCount - 1]++;
                    var successor = adjacency[node][edgeIndex];
                    if (discoveryIndex[successor] == -1) {
                        discoveryIndex[successor] = lowLink[successor] = nextIndex++;
                        componentStack[componentStackSize++] = successor;
                        onStack[successor] = true;
                        frameNode[frameCount] = successor;
                        frameEdge[frameCount] = 0;
                        frameCount++;
                    } else if (onStack[successor]) {
                        lowLink[node] = Math.min(lowLink[node], discoveryIndex[successor]);
                    }
                } else {
                    frameCount--;
                    if (frameCount > 0) {
                        var parent = frameNode[frameCount - 1];
                        lowLink[parent] = Math.min(lowLink[parent], lowLink[node]);
                    }
                    if (lowLink[node] == discoveryIndex[node]) {
                        var componentStart = componentStackSize;
                        do {
                            componentStart--;
                        } while (componentStack[componentStart] != node);
                        var componentSize = componentStackSize - componentStart;
                        for (var i = componentStart; i < componentStackSize; i++) {
                            onStack[componentStack[i]] = false;
                            if (componentSize > 1) {
                                inCycle[componentStack[i]] = true;
                            }
                        }
                        componentStackSize = componentStart;
                    }
                }
            }
        }
        return inCycle;
    }
}
