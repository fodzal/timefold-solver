package ai.timefold.solver.core.impl.domain.variable.declarative;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import ai.timefold.solver.core.api.score.analysis.VariableLoop;
import ai.timefold.solver.core.preview.api.domain.metamodel.VariableMetaModel;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A variable reference graph that excludes a planning list variable's elements from the graph
 * and updates them by a cascade instead;
 * see {@link GraphStructure.GraphStructureAndDirection#cascadedElementClass()}.
 * <p>
 * The inner graph covers everything but the elements and is built by the normal machinery.
 * The cascade walks each entity's chain from its earliest dirty element, at two moments:
 * <ul>
 * <li>for an entity whose list or elements changed, before the inner update;</li>
 * <li>for an entity whose pre-chain variables changed <em>during</em> the inner update,
 * from {@link #walkChainAfterPreChainVariableChanged(Object)}, which the notifier the inner graph
 * was built with calls the moment such a variable is written.</li>
 * </ul>
 * The second moment is what keeps the cost linear in the length of a chain of entities.
 * It relies on two invariants, which
 * {@link DefaultShadowVariableSessionFactory#createFixedVariableRelationEdges} and
 * {@link VariableUpdaterInfo#updateIfChanged} establish and must keep:
 * <ol>
 * <li>each entity's post-chain variables have an edge from the pre-chain variables its elements read,
 * so they are computed after them, and are queued as soon as they change;</li>
 * <li>the notifier fires after the pre-chain variable's new value has been written, and before the
 * post-chain variables' suppliers run.</li>
 * </ol>
 * Together they place the walk between the two, so that a post-chain variable is never computed from
 * a chain that is about to be re-walked. Without them, the recomputation of the post-chain variables
 * runs ahead of the cascade down the whole chain of entities and the walks compound quadratically.
 * <p>
 * Each element is therefore computed once per update, and each entity variable once,
 * with one exception: an entity whose list changed <em>and</em> that is downstream of another change
 * is walked twice, once before the inner update on pre-chain variables that had not settled yet,
 * and once when they do. {@code ListElementCascadeVariableReferenceGraphTest} pins both.
 */
@NullMarked
public final class ListElementCascadeVariableReferenceGraph<Solution_> implements VariableReferenceGraph {

    private final VariableReferenceGraph innerGraph;
    private final @Nullable AbstractVariableReferenceGraph<Solution_, ?> innerNodeGraph;

    private final VariableUpdaterInfo<Solution_>[] elementUpdaters;
    private final Class<?> elementEntityClass;
    private final EntityConsistencyState<Solution_, Object> elementConsistencyState;
    private final @Nullable EntityConsistencyState<Solution_, Object> ownerConsistencyState;
    private final List<VariableMetaModel<?, ?, ?>> postChainVariableIdList;

    private final UnaryOperator<@Nullable Object> nextInChain;
    private final UnaryOperator<@Nullable Object> elementToOwner;
    private final Comparator<Object> chainOrderComparator;
    private final Function<Object, @Nullable Object> ownerToFirstElement;

    private final Set<VariableMetaModel<?, ?, ?>> monitoredSourceVariableSet;
    private final ChangedVariableNotifier<Solution_> changedVariableNotifier;
    private final boolean canTerminateEarly;

    private final List<Object> changedElementList;
    private boolean isProcessing;

    @SuppressWarnings("unchecked")
    ListElementCascadeVariableReferenceGraph(
            ConsistencyTracker<Solution_> consistencyTracker,
            VariableReferenceGraph innerGraph,
            List<DeclarativeShadowVariableDescriptor<Solution_>> sortedElementDescriptorList,
            Class<?> elementEntityClass,
            @Nullable EntityConsistencyState<Solution_, Object> ownerConsistencyState,
            List<VariableMetaModel<?, ?, ?>> postChainVariableIdList,
            TopologicalSorter topologicalSorter,
            Function<Object, @Nullable Object> ownerToFirstElement,
            boolean canTerminateEarly,
            ChangedVariableNotifier<Solution_> changedVariableNotifier,
            Object[] entities) {
        this.innerGraph = innerGraph;
        this.innerNodeGraph = innerGraph instanceof AbstractVariableReferenceGraph<?, ?> abstractGraph
                ? (AbstractVariableReferenceGraph<Solution_, ?>) abstractGraph
                : null;
        this.elementEntityClass = elementEntityClass;
        this.ownerConsistencyState = ownerConsistencyState;
        this.postChainVariableIdList = postChainVariableIdList;
        this.nextInChain = topologicalSorter.successor();
        this.elementToOwner = topologicalSorter.key();
        this.chainOrderComparator = topologicalSorter.comparator();
        this.ownerToFirstElement = ownerToFirstElement;
        this.canTerminateEarly = canTerminateEarly;
        this.changedVariableNotifier = changedVariableNotifier;
        this.changedElementList = new ArrayList<>();
        this.isProcessing = false;

        this.elementConsistencyState = consistencyTracker
                .getDeclarativeEntityConsistencyState(sortedElementDescriptorList.getFirst().getEntityDescriptor());
        this.monitoredSourceVariableSet = new HashSet<>();
        this.elementUpdaters = new VariableUpdaterInfo[sortedElementDescriptorList.size()];
        var updaterId = 0;
        for (var descriptor : sortedElementDescriptorList) {
            for (var source : descriptor.getSources()) {
                for (var sourceReference : source.variableSourceReferences()) {
                    monitoredSourceVariableSet.add(sourceReference.variableMetaModel());
                }
            }
            elementUpdaters[updaterId] = new VariableUpdaterInfo<>(
                    descriptor.getVariableMetaModel(), updaterId, descriptor, elementConsistencyState,
                    descriptor.getMemberAccessor(), descriptor.getCalculator());
            updaterId++;
        }

        // Every element gets an initial computation and starts consistent;
        // its consistency can only change when its owner becomes inconsistent.
        for (var entity : entities) {
            if (elementEntityClass.isInstance(entity)) {
                elementConsistencyState.setEntityIsInconsistent(changedVariableNotifier, entity, false);
                changedElementList.add(entity);
            }
        }
    }

    /**
     * Computes every variable for the first time.
     * Separate from the constructor, since the notifier that triggers the walks needs this graph,
     * so it can only reach it once the constructor has returned.
     */
    void initialize() {
        isProcessing = true;
        try {
            // The inner graph computes the pre-chain variables the first walk reads.
            innerGraph.updateChanged();
        } finally {
            isProcessing = false;
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
            changedElementList.add(entity);
        }
        innerGraph.afterVariableChanged(variableReference, entity);
    }

    @Override
    public void beforeListVariableChanged(VariableMetaModel<?, ?, ?> variableReference, Object entity,
            List<Object> elementList, int fromIndex, int toIndex) {
        if (isProcessing) {
            throw new IllegalStateException("Impossible state: list variable changed during shadow variable update.");
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
            changedElementList.add(elementList.get(elementIndex));
        }
        innerGraph.afterListVariableChanged(variableReference, entity, elementList, fromIndex, toIndex);
        // Stands in for the marking the inner graph does for a non-cascaded model, at the same
        // event: the element cascade skips the list element locators, hence also the mark that
        // comes with them. Without it, removing the list's last element would leave no element
        // to walk and no edge to the owner, so nothing would recompute its post-chain variables.
        markPostChainVariablesChanged(entity);
    }

    @Override
    public boolean updateChanged() {
        isProcessing = true;
        try {
            walkChainsOfChangedElements();
            // A single inner update suffices: nothing marks a node while it is running.
            // The walks it triggers do not have to, their post-chain variables being already queued
            // behind their pre-chain edge, and a mark placed then would only take effect in the
            // inner graph's next update anyway.
            return innerGraph.updateChanged();
        } finally {
            isProcessing = false;
        }
    }

    /**
     * Classifies the changed elements into per-owner dirty ranges and walks those ranges.
     * An unassigned element is reset immediately, so that re-assigning it to the same position
     * is detected as a change.
     */
    private void walkChainsOfChangedElements() {
        if (changedElementList.isEmpty()) {
            return;
        }
        var ownerToDirtyChainStart = new IdentityHashMap<Object, Object>();
        var ownerToDirtyChainEnd = new IdentityHashMap<Object, Object>();
        for (var element : changedElementList) {
            var owner = elementToOwner.apply(element);
            if (owner == null) {
                if (!elementConsistencyState.isEntityConsistent(element)) {
                    elementConsistencyState.setEntityIsInconsistent(changedVariableNotifier, element, false);
                }
                for (var updater : elementUpdaters) {
                    updater.updateIfChanged(element, changedVariableNotifier);
                }
                continue;
            }
            var dirtyChainStart = ownerToDirtyChainStart.get(owner);
            if (dirtyChainStart == null || chainOrderComparator.compare(element, dirtyChainStart) < 0) {
                ownerToDirtyChainStart.put(owner, element);
            }
            var dirtyChainEnd = ownerToDirtyChainEnd.get(owner);
            if (dirtyChainEnd == null || chainOrderComparator.compare(element, dirtyChainEnd) > 0) {
                ownerToDirtyChainEnd.put(owner, element);
            }
        }
        changedElementList.clear();
        for (var dirtyChain : ownerToDirtyChainStart.entrySet()) {
            var owner = dirtyChain.getKey();
            walkOrMarkChainInconsistent(owner, dirtyChain.getValue(), ownerToDirtyChainEnd.get(owner), false);
        }
    }

    /**
     * Walks the whole chain of an entity whose pre-chain variable just changed, since any of its
     * elements may read that variable. Called from the notifier the inner graph was built with,
     * so that the entity's post-chain variables, which the inner graph computes right after,
     * see an up to date chain.
     * <p>
     * Does not mark those post-chain variables: the inner graph has already queued them,
     * behind the edge from the pre-chain variable that changed.
     * <p>
     * This cannot recurse: a walk only ever writes the elements' variables, never the entity's.
     */
    void walkChainAfterPreChainVariableChanged(Object owner) {
        if (isOwnerInconsistent(owner)) {
            markChainInconsistent(owner);
        } else {
            walkChain(owner, null, null, true);
        }
    }

    private void walkOrMarkChainInconsistent(Object owner, @Nullable Object dirtyChainStart,
            @Nullable Object dirtyChainEnd, boolean walkWholeChain) {
        if (isOwnerInconsistent(owner)) {
            markChainInconsistent(owner);
            return;
        }
        var anyElementChanged = walkChain(owner, dirtyChainStart, dirtyChainEnd, walkWholeChain);
        if (anyElementChanged) {
            markPostChainVariablesChanged(owner);
        }
    }

    private boolean isOwnerInconsistent(Object owner) {
        return ownerConsistencyState != null
                && Boolean.TRUE.equals(ownerConsistencyState.getEntityInconsistentValue(owner));
    }

    private boolean walkChain(Object owner, @Nullable Object dirtyChainStart, @Nullable Object dirtyChainEnd,
            boolean walkWholeChain) {
        var chainStart = dirtyChainStart;
        if (walkWholeChain) {
            var firstElement = ownerToFirstElement.apply(owner);
            if (firstElement != null
                    && (chainStart == null || chainOrderComparator.compare(firstElement, chainStart) < 0)) {
                chainStart = firstElement;
            }
        }
        if (chainStart == null) {
            return false;
        }
        var anyElementChangedInWalk = false;
        var current = chainStart;
        while (current != null) {
            if (!elementConsistencyState.isEntityConsistent(current)) {
                // The element's owner recovered from a dependency loop.
                elementConsistencyState.setEntityIsInconsistent(changedVariableNotifier, current, false);
            }
            var anyElementVariableChanged = false;
            for (var updater : elementUpdaters) {
                anyElementVariableChanged |= updater.updateIfChanged(current, changedVariableNotifier);
            }
            anyElementChangedInWalk |= anyElementVariableChanged;
            if (canTerminateEarly && !walkWholeChain && !anyElementVariableChanged
            // A swap can create multiple non-contiguous dirty elements on the same chain,
            // so only terminate early once the last dirty element has been reached.
                    && (dirtyChainEnd == null || chainOrderComparator.compare(current, dirtyChainEnd) >= 0)) {
                break;
            }
            current = nextInChain.apply(current);
        }
        return anyElementChangedInWalk;
    }

    private void markChainInconsistent(Object owner) {
        // The owner is part of a dependency loop the solver may break later;
        // its elements read its pre-chain variables, so they are inconsistent with it.
        var current = ownerToFirstElement.apply(owner);
        while (current != null) {
            if (elementConsistencyState.isEntityConsistent(current)) {
                elementConsistencyState.setEntityIsInconsistent(changedVariableNotifier, current, true);
            }
            for (var updater : elementUpdaters) {
                updater.updateIfChanged(current, null, changedVariableNotifier);
            }
            current = nextInChain.apply(current);
        }
    }

    @Override
    public List<VariableLoop> getVariableLoops() {
        // The elements are only excluded from the inner graph when their sources form a chain
        // fed by the owner's pre-chain variables, so they can never be part of a loop themselves.
        return innerGraph.getVariableLoops();
    }

    private void markPostChainVariablesChanged(Object owner) {
        if (innerNodeGraph == null) {
            return;
        }
        for (var variableId : postChainVariableIdList) {
            var node = innerNodeGraph.lookupOrNull(variableId, owner);
            if (node != null) {
                innerNodeGraph.markChanged(node);
            }
        }
    }
}
