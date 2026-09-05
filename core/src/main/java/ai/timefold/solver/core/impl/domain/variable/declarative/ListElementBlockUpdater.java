package ai.timefold.solver.core.impl.domain.variable.declarative;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import ai.timefold.solver.core.preview.api.domain.metamodel.VariableMetaModel;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Updates the declarative shadow variables of a planning list variable's elements,
 * which are excluded from the variable reference graph and represented by one block node
 * per list entity instead; see {@link GraphStructure.GraphStructureAndDirection#blockedElementClass()}.
 * <p>
 * A single instance backs every list entity's block node.
 * When a block node is processed, {@link #update(Object, boolean, ChangedVariableNotifier)}
 * walks the entity's chain from the earliest dirty element and reports whether anything
 * changed, which propagates to the entity's post-chain variables through the graph's edges.
 * The dirty ranges are maintained by {@link ListElementBlockVariableReferenceGraph}
 * from the list variable's change events and the elements' source variable changes;
 * a change of a pre-chain variable read by the elements flags its entity for a whole-chain
 * walk instead, because any element may read it.
 * <p>
 * When the block node is part of a dependency loop, the elements follow their entity:
 * they are marked inconsistent and their variables are set to null.
 */
@NullMarked
public final class ListElementBlockUpdater<Solution_> implements VariableUpdater<Solution_> {

    /**
     * {@link DefaultShadowVariableSessionFactory#getGroupVariableUpdaterInfoMap} allocates the other
     * updaters' group ids by counting up from zero, so a negative id gives the block nodes a bucket
     * of their own in {@link VariableReferenceGraphBuilder#addVariableReferenceEntity}.
     */
    private static final int BLOCK_GROUP_ID = -1;

    private final VariableMetaModel<Solution_, ?, ?> listVariableMetaModel;
    private final EntityConsistencyState<Solution_, Object> ownerConsistencyState;
    private final EntityConsistencyState<Solution_, Object> elementConsistencyState;
    private final VariableUpdaterInfo<Solution_>[] elementUpdaters;

    private final UnaryOperator<@Nullable Object> nextInChain;
    private final UnaryOperator<Object> elementToOwner;
    private final Comparator<Object> chainOrderComparator;
    private final Function<Object, @Nullable Object> ownerToFirstElement;
    private final boolean canTerminateEarly;

    // Mutable dirty state, written by ListElementBlockVariableReferenceGraph
    // and by the notifier wrapper created in DefaultShadowVariableSessionFactory.
    private final List<Object> changedElementList;
    private final IdentityHashMap<Object, Object> ownerToDirtyChainStart;
    private final IdentityHashMap<Object, Object> ownerToDirtyChainEnd;
    private final Set<Object> wholeChainOwnerSet;
    private final Set<Object> structuralChangeOwnerSet;

    @SuppressWarnings("unchecked")
    ListElementBlockUpdater(
            VariableMetaModel<Solution_, ?, ?> listVariableMetaModel,
            EntityConsistencyState<Solution_, Object> ownerConsistencyState,
            EntityConsistencyState<Solution_, Object> elementConsistencyState,
            List<DeclarativeShadowVariableDescriptor<Solution_>> sortedElementDescriptorList,
            TopologicalSorter topologicalSorter,
            Function<Object, @Nullable Object> ownerToFirstElement,
            boolean canTerminateEarly,
            Set<Object> wholeChainOwnerSet) {
        this.listVariableMetaModel = listVariableMetaModel;
        this.ownerConsistencyState = ownerConsistencyState;
        this.elementConsistencyState = elementConsistencyState;
        this.nextInChain = topologicalSorter.successor();
        this.elementToOwner = topologicalSorter.key();
        this.chainOrderComparator = topologicalSorter.comparator();
        this.ownerToFirstElement = ownerToFirstElement;
        this.canTerminateEarly = canTerminateEarly;
        this.wholeChainOwnerSet = wholeChainOwnerSet;
        this.changedElementList = new ArrayList<>();
        this.ownerToDirtyChainStart = new IdentityHashMap<>();
        this.ownerToDirtyChainEnd = new IdentityHashMap<>();
        this.structuralChangeOwnerSet = Collections.newSetFromMap(new IdentityHashMap<>());

        this.elementUpdaters = new VariableUpdaterInfo[sortedElementDescriptorList.size()];
        var updaterId = 0;
        for (var descriptor : sortedElementDescriptorList) {
            elementUpdaters[updaterId] = new VariableUpdaterInfo<>(
                    descriptor.getVariableMetaModel(), updaterId, descriptor, elementConsistencyState,
                    descriptor.getMemberAccessor(), descriptor.getCalculator());
            updaterId++;
        }
    }

    @Override
    public VariableMetaModel<Solution_, ?, ?> id() {
        return listVariableMetaModel;
    }

    @Override
    public int groupId() {
        return BLOCK_GROUP_ID;
    }

    @Override
    public @Nullable Object[] groupEntities() {
        return null;
    }

    @Override
    public EntityConsistencyState<Solution_, Object> entityConsistencyState() {
        return ownerConsistencyState;
    }

    @Override
    public boolean update(Object owner, boolean isEntityInconsistent,
            ChangedVariableNotifier<Solution_> changedVariableNotifier) {
        var dirtyChainStart = ownerToDirtyChainStart.remove(owner);
        var dirtyChainEnd = ownerToDirtyChainEnd.remove(owner);
        var walkWholeChain = wholeChainOwnerSet.remove(owner);
        var isStructurallyChanged = structuralChangeOwnerSet.remove(owner);
        if (isEntityInconsistent) {
            // The owner is part of a dependency loop the solver may break later;
            // its elements read its pre-chain variables, so they are inconsistent with it.
            return markChainInconsistent(owner, changedVariableNotifier) || isStructurallyChanged;
        }
        var firstElement = ownerToFirstElement.apply(owner);
        if (firstElement != null && !elementConsistencyState.isEntityConsistent(firstElement)) {
            // The owner recovered from a dependency loop; its whole chain was inconsistent.
            walkWholeChain = true;
        }
        var chainStart = dirtyChainStart;
        if (walkWholeChain
                && firstElement != null
                && (chainStart == null || chainOrderComparator.compare(firstElement, chainStart) < 0)) {
            chainStart = firstElement;
        }
        var anyElementChanged = walkChain(chainStart, dirtyChainEnd, walkWholeChain, changedVariableNotifier);
        // A structural list change (e.g. a removed element) changes the post-chain variables'
        // dependency set even when no element value changed, so it always propagates.
        return anyElementChanged || isStructurallyChanged;
    }

    private boolean walkChain(@Nullable Object chainStart, @Nullable Object dirtyChainEnd, boolean walkWholeChain,
            ChangedVariableNotifier<Solution_> changedVariableNotifier) {
        if (chainStart == null) {
            return false;
        }
        var anyElementChangedInWalk = false;
        var current = chainStart;
        while (current != null) {
            if (!elementConsistencyState.isEntityConsistent(current)) {
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

    private boolean markChainInconsistent(Object owner, ChangedVariableNotifier<Solution_> changedVariableNotifier) {
        var anyElementChanged = false;
        var current = ownerToFirstElement.apply(owner);
        while (current != null) {
            if (elementConsistencyState.isEntityConsistent(current)) {
                elementConsistencyState.setEntityIsInconsistent(changedVariableNotifier, current, true);
            }
            for (var updater : elementUpdaters) {
                anyElementChanged |= updater.updateIfChanged(current, null, changedVariableNotifier);
            }
            current = nextInChain.apply(current);
        }
        return anyElementChanged;
    }

    /**
     * Records an element whose source variables changed, or that may have left its list;
     * classified into a per-owner dirty range by {@link #classifyChangedElements}.
     */
    void recordChangedElement(Object element) {
        changedElementList.add(element);
    }

    /**
     * Records that the given entity's list changed structurally,
     * so its post-chain variables must be recomputed even when no element value changes.
     */
    void recordStructuralChange(Object owner) {
        structuralChangeOwnerSet.add(owner);
    }

    /**
     * Classifies the recorded elements into per-owner dirty ranges and feeds each dirty owner
     * to the given consumer, so its block node can be marked changed.
     * An unassigned element is recomputed here rather than by a block node, having no list entity;
     * its suppliers read a null inverse, so it ends up cleared,
     * and re-assigning it to the same position is detected as a change.
     */
    void classifyChangedElements(ChangedVariableNotifier<Solution_> changedVariableNotifier,
            Consumer<Object> dirtyOwnerConsumer) {
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
        // Marking is a bit in a set (or a slot in a topologically ordered queue) either way,
        // so the order these identity maps happen to iterate in does not reach the result.
        for (var owner : ownerToDirtyChainStart.keySet()) {
            dirtyOwnerConsumer.accept(owner);
        }
        for (var owner : structuralChangeOwnerSet) {
            dirtyOwnerConsumer.accept(owner);
        }
    }

    /**
     * Clears the flags a block node did not consume during the update,
     * e.g. when its entity was removed from the working solution.
     */
    void clearTransientState() {
        ownerToDirtyChainStart.clear();
        ownerToDirtyChainEnd.clear();
        wholeChainOwnerSet.clear();
        structuralChangeOwnerSet.clear();
    }
}
