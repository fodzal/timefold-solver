package ai.timefold.solver.core.impl.domain.variable.declarative;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A variable reference graph optimized for multi-entity models where:
 * <ul>
 * <li>One entity class (the "chained" entity, e.g., Visit) uses a single directional parent
 * (PREVIOUS or NEXT) to form chains within each vehicle.</li>
 * <li>Another entity class (the "fixed" entity, e.g., Vehicle) uses only VARIABLE/GROUP/NO_PARENT
 * sources, forming a fixed DAG based on problem facts.</li>
 * </ul>
 * <p>
 * This graph avoids Tarjan SCC by using:
 * <ul>
 * <li>A pre-computed topological order for the fixed entity DAG (computed once at construction).</li>
 * <li>A chain-walk for the chained entity within each vehicle (dynamic order from list indices).</li>
 * <li>A PriorityQueue over fixed entities to handle DAG merges correctly.</li>
 * </ul>
 * <p>
 * Complexity per move: O(k + V_affected * log V) where k = affected chain length,
 * V_affected = number of fixed entities whose shadows changed, V = total fixed entities.
 */
@NullMarked
public final class MultiEntityChainedVariableReferenceGraph<Solution_> implements VariableReferenceGraph {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiEntityChainedVariableReferenceGraph.class);

    // --- Chain functions (for the chained entity, e.g., Visit) ---
    private final UnaryOperator<@Nullable Object> nextInChain;
    private final UnaryOperator<@Nullable Object> entityToFixedParent; // Visit -> Vehicle (via list state supply)
    private final Function<Object, @Nullable Object> directEntityToFixedParent; // Visit -> Vehicle (reads entity field directly)
    private final Comparator<Object> chainOrderComparator;
    private final Function<Object, @Nullable Object> fixedEntityToFirstChainedEntity; // Vehicle -> first Visit

    // --- Updaters per entity class ---
    private final VariableUpdaterInfo<Solution_>[] chainedUpdaters;
    /**
     * Pre-chain updaters: fixed entity shadows that depend ONLY on predecessor vehicles (GROUP sources).
     * Example: previousEndPosition. These are safe to compute before the chain walk because
     * they only read from already-processed predecessor vehicles.
     */
    private final VariableUpdaterInfo<Solution_>[] preChainFixedUpdaters;
    /**
     * Post-chain updaters: fixed entity shadows that depend on chained entity values or
     * on other fixed entity shadows that do. Example: lastVisit, endServiceTime, endPosition.
     * These must be computed after the chain walk so they read up-to-date Visit values.
     */
    private final VariableUpdaterInfo<Solution_>[] postChainFixedUpdaters;

    // --- Fixed entity DAG ---
    private final Map<Object, Integer> fixedEntityToOrder;
    private final Map<Object, Object[]> fixedEntityToSuccessors;

    // --- Entity class identification ---
    private final Class<?> chainedEntityClass;
    private final Class<?> fixedEntityClass;

    // --- Monitoring ---
    private final Set<VariableMetaModel<?, ?, ?>> monitoredSourceVariableSet;
    private final ChangedVariableNotifier<Solution_> changedVariableNotifier;
    private final boolean canTerminateEarly;

    // --- Mutable state ---
    private final List<Object> changedEntities;
    private boolean isUpdating;

    @SuppressWarnings("unchecked")
    public MultiEntityChainedVariableReferenceGraph(
            ConsistencyTracker<Solution_> consistencyTracker,
            List<DeclarativeShadowVariableDescriptor<Solution_>> chainedDescriptors,
            List<DeclarativeShadowVariableDescriptor<Solution_>> fixedDescriptors,
            TopologicalSorter topologicalSorter,
            ChangedVariableNotifier<Solution_> changedVariableNotifier,
            boolean canTerminateEarly,
            Function<Object, @Nullable Object> fixedEntityToFirstChainedEntity,
            Function<Object, @Nullable Object> directEntityToFixedParent,
            Map<Object, Integer> fixedEntityToOrder,
            Map<Object, Object[]> fixedEntityToSuccessors,
            Object[] entities) {

        this.nextInChain = topologicalSorter.successor();
        this.entityToFixedParent = topologicalSorter.key();
        this.directEntityToFixedParent = directEntityToFixedParent;
        this.chainOrderComparator = topologicalSorter.comparator();
        this.fixedEntityToFirstChainedEntity = fixedEntityToFirstChainedEntity;
        this.fixedEntityToOrder = fixedEntityToOrder;
        this.fixedEntityToSuccessors = fixedEntityToSuccessors;
        this.changedVariableNotifier = changedVariableNotifier;
        this.canTerminateEarly = canTerminateEarly;
        this.changedEntities = new ArrayList<>();
        this.isUpdating = false;

        // Determine entity classes
        this.chainedEntityClass = chainedDescriptors.get(0).getEntityDescriptor().getEntityClass();
        this.fixedEntityClass = fixedDescriptors.get(0).getEntityDescriptor().getEntityClass();

        // Build monitored source variable set
        this.monitoredSourceVariableSet = new java.util.HashSet<>();

        // Build chained entity updaters
        var chainedConsistencyState = consistencyTracker.getDeclarativeEntityConsistencyState(
                chainedDescriptors.get(0).getEntityDescriptor());
        this.chainedUpdaters = new VariableUpdaterInfo[chainedDescriptors.size()];
        for (int i = 0; i < chainedDescriptors.size(); i++) {
            var desc = chainedDescriptors.get(i);
            chainedUpdaters[i] = new VariableUpdaterInfo<>(
                    desc.getVariableMetaModel(), i, desc, chainedConsistencyState,
                    desc.getMemberAccessor(), desc.getCalculator());
            addSourcesToMonitoredSet(desc);
        }

        // Build fixed entity updaters, split into pre-chain and post-chain
        var fixedConsistencyState = consistencyTracker.getDeclarativeEntityConsistencyState(
                fixedDescriptors.get(0).getEntityDescriptor());
        var preChainList = new ArrayList<VariableUpdaterInfo<Solution_>>();
        var postChainList = new ArrayList<VariableUpdaterInfo<Solution_>>();

        for (int i = 0; i < fixedDescriptors.size(); i++) {
            var desc = fixedDescriptors.get(i);
            var updater = new VariableUpdaterInfo<>(
                    desc.getVariableMetaModel(), chainedDescriptors.size() + i, desc,
                    fixedConsistencyState, desc.getMemberAccessor(), desc.getCalculator());
            addSourcesToMonitoredSet(desc);

            // Classify: a fixed updater is pre-chain if ALL its sources are GROUP.
            // Everything else (NO_PARENT, VARIABLE, etc.) is post-chain, because
            // those sources may read chained entity data (e.g., visits list, lastVisit field).
            boolean isPreChain = desc.getSources().length > 0;
            for (var source : desc.getSources()) {
                if (source.parentVariableType() != ParentVariableType.GROUP) {
                    isPreChain = false;
                    break;
                }
            }

            if (isPreChain) {
                preChainList.add(updater);
            } else {
                postChainList.add(updater);
            }
        }

        this.preChainFixedUpdaters = preChainList.toArray(new VariableUpdaterInfo[0]);
        this.postChainFixedUpdaters = postChainList.toArray(new VariableUpdaterInfo[0]);

        LOGGER.debug("MultiEntityChainedVariableReferenceGraph: chained={}, preChainFixed={}, postChainFixed={}",
                chainedUpdaters.length, preChainFixedUpdaters.length, postChainFixedUpdaters.length);
        for (var u : preChainFixedUpdaters) {
            LOGGER.debug("  preChain: {}", u);
        }
        for (var u : postChainFixedUpdaters) {
            LOGGER.debug("  postChain: {}", u);
        }

        // Initialize: mark all entities as consistent and schedule initial computation
        for (var entity : entities) {
            if (chainedEntityClass.isInstance(entity)) {
                chainedConsistencyState.setEntityIsInconsistent(changedVariableNotifier, entity, false);
                changedEntities.add(entity);
            } else if (fixedEntityClass.isInstance(entity)) {
                fixedConsistencyState.setEntityIsInconsistent(changedVariableNotifier, entity, false);
                changedEntities.add(entity);
            }
        }

        // Initial computation
        updateChanged();
    }

    private void addSourcesToMonitoredSet(DeclarativeShadowVariableDescriptor<Solution_> descriptor) {
        for (var source : descriptor.getSources()) {
            for (var sourceReference : source.variableSourceReferences()) {
                monitoredSourceVariableSet.add(sourceReference.variableMetaModel());
            }
        }
    }

    @Override
    public void beforeVariableChanged(VariableMetaModel<?, ?, ?> variableReference, Object entity) {
        // When a chained entity (Visit) is about to be unassigned from a fixed entity (Vehicle),
        // capture the old fixed parent BEFORE the field changes. This is necessary because
        // afterVariableChanged will see the new value (null), and the list state supply's
        // internal position map may already be cleared at that point.
        // By adding the old fixed parent to changedEntities, we ensure the Vehicle's shadow
        // variables (lastVisit, endServiceTime, endPosition) are recalculated after the undo.
        if (!isUpdating && monitoredSourceVariableSet.contains(variableReference)
                && chainedEntityClass.isInstance(entity)) {
            var oldFixedParent = directEntityToFixedParent.apply(entity);
            if (oldFixedParent != null) {
                changedEntities.add(oldFixedParent);
            }
        }
    }

    @Override
    public void afterVariableChanged(VariableMetaModel<?, ?, ?> variableReference, Object entity) {
        if (!isUpdating && monitoredSourceVariableSet.contains(variableReference)) {
            if (chainedEntityClass.isInstance(entity) || fixedEntityClass.isInstance(entity)) {
                changedEntities.add(entity);
            }
        }
    }

    @Override
    public void updateChanged() {
        if (changedEntities.isEmpty()) {
            return;
        }
        isUpdating = true;

        // Group changed entities: find dirty fixed entities and earliest dirty chained entity per fixed entity
        var dirtyFixedEntities = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        var fixedEntityToDirtyChainStart = new IdentityHashMap<Object, Object>();

        for (var entity : changedEntities) {
            if (fixedEntityClass.isInstance(entity)) {
                dirtyFixedEntities.add(entity);
            } else if (chainedEntityClass.isInstance(entity)) {
                var fixedParent = entityToFixedParent.apply(entity);
                if (fixedParent != null) {
                    dirtyFixedEntities.add(fixedParent);
                    var existing = fixedEntityToDirtyChainStart.get(fixedParent);
                    if (existing == null || chainOrderComparator.compare(entity, existing) < 0) {
                        fixedEntityToDirtyChainStart.put(fixedParent, entity);
                    }
                }
            }
        }
        changedEntities.clear();

        LOGGER.trace("updateChanged: dirtyFixedEntities={}, dirtyChainStarts={}",
                dirtyFixedEntities.size(), fixedEntityToDirtyChainStart.size());

        // Process fixed entities in DAG topological order using a PriorityQueue
        var queue = new PriorityQueue<>(
                Comparator.comparingInt(v -> fixedEntityToOrder.getOrDefault(v, Integer.MAX_VALUE)));
        var processed = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        queue.addAll(dirtyFixedEntities);

        while (!queue.isEmpty()) {
            var fixedEntity = queue.poll();
            if (!processed.add(fixedEntity)) {
                continue;
            }

            // 1. Pre-chain pass: only updaters that depend solely on predecessor vehicles (GROUP sources).
            //    Example: previousEndPosition. These read from already-processed predecessors in the DAG.
            boolean preChainChanged = false;
            for (var updater : preChainFixedUpdaters) {
                preChainChanged |= updater.updateIfChanged(fixedEntity, changedVariableNotifier);
            }

            // 2. Determine chain start
            var dirtyChainStart = fixedEntityToDirtyChainStart.get(fixedEntity);
            var chainStart = dirtyChainStart;
            if (preChainChanged) {
                // Predecessor data changed -> recompute visits from the first chained entity,
                // because arrival times may depend on previousEndPosition.
                var firstChained = fixedEntityToFirstChainedEntity.apply(fixedEntity);
                if (firstChained != null) {
                    if (chainStart == null || chainOrderComparator.compare(firstChained, chainStart) < 0) {
                        chainStart = firstChained;
                    }
                }
            }

            LOGGER.trace("  fixedEntity={}: preChainChanged={}, chainStart={}, dirtyChainStart={}",
                    fixedEntity, preChainChanged, chainStart, dirtyChainStart);

            // 3. Walk the chained entity chain with short-circuit
            if (chainStart != null) {
                var current = chainStart;
                while (current != null) {
                    boolean anyChanged = false;
                    for (var updater : chainedUpdaters) {
                        anyChanged |= updater.updateIfChanged(current, changedVariableNotifier);
                    }
                    if (canTerminateEarly && !anyChanged) {
                        // Don't terminate early if there's a dirty chain start we haven't reached yet.
                        // This can happen when preChainChanged overrode chainStart to firstChained,
                        // but the actual dirty visits are further in the chain (e.g., a visit was moved
                        // into this vehicle at a later position).
                        if (dirtyChainStart == null
                                || chainOrderComparator.compare(current, dirtyChainStart) >= 0) {
                            LOGGER.trace("    chain walk early termination at {}", current);
                            break;
                        }
                    }
                    current = nextInChain.apply(current);
                }
            }

            // 4. Post-chain pass: updaters that depend on chained entity values.
            //    Example: lastVisit, endServiceTime, endPosition. These read Visit shadow variables
            //    that were just updated by the chain walk.
            boolean postChainChanged = false;
            for (var updater : postChainFixedUpdaters) {
                postChainChanged |= updater.updateIfChanged(fixedEntity, changedVariableNotifier);
            }

            LOGGER.trace("  fixedEntity={}: postChainChanged={}", fixedEntity, postChainChanged);

            // 5. If any fixed entity shadow changed, add successor fixed entities to queue
            if (preChainChanged || postChainChanged) {
                var successors = fixedEntityToSuccessors.get(fixedEntity);
                if (successors != null) {
                    for (var successor : successors) {
                        if (!processed.contains(successor)) {
                            queue.add(successor);
                            LOGGER.trace("  enqueuing successor: {}", successor);
                        }
                    }
                }
            }
        }

        isUpdating = false;
    }
}
