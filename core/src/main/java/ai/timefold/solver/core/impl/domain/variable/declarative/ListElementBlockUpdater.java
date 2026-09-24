package ai.timefold.solver.core.impl.domain.variable.declarative;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import ai.timefold.solver.core.impl.domain.variable.ListVariableState;
import ai.timefold.solver.core.impl.domain.variable.descriptor.ListVariableDescriptor;
import ai.timefold.solver.core.preview.api.domain.metamodel.VariableMetaModel;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Updates the declarative shadow variables of a planning list variable's elements,
 * which are excluded from the variable reference graph and represented by one block node
 * per list entity instead; see {@link GraphStructure#LIST_ELEMENT_BLOCK}.
 * <p>
 * A single instance backs every list entity's block node.
 * When a block node is processed, {@link #update(Object, boolean, ChangedVariableNotifier)}
 * walks the entity's list from the earliest dirty element and reports whether anything
 * changed, which propagates to the entity's post-chain variables through the graph's edges.
 * The dirty ranges are maintained by {@link ListElementBlockVariableReferenceGraph}
 * from the list variable's change events and the elements' source variable changes.
 * An entity with no dirty range has its whole chain walked.
 * The dirty state of every list entity is allocated once;
 * after an update, only the entities that update dirtied are reset.
 * <p>
 * When the block node is part of a dependency loop, the elements follow their entity:
 * they are marked inconsistent and their variables are set to null.
 */
@NullMarked
final class ListElementBlockUpdater<Solution_> implements VariableUpdater<Solution_> {

    private final VariableMetaModel<Solution_, ?, ?> listVariableMetaModel;
    private final ListVariableDescriptor<Solution_> listVariableDescriptor;
    private final ListVariableState<Solution_, Object, Object> listVariableState;
    // A previous element parent orders the chain as the list; a next element parent reverses it.
    private final boolean isChainInListOrder;
    private final EntityConsistencyState<Solution_, Object> ownerConsistencyState;
    private final EntityConsistencyState<Solution_, Object> elementConsistencyState;
    private final VariableUpdaterInfo<Solution_>[] elementUpdaters;
    // The list entity's variables its elements read through their inverse.
    private final DeclarativeShadowVariableDescriptor<Solution_>[] preChainVariableDescriptors;
    private final boolean canTerminateEarly;

    // Mutable dirty state, written by ListElementBlockVariableReferenceGraph.
    private final List<Object> changedElementList;
    // The unassigned elements the classification in progress recomputed, so that each is recomputed once.
    private final List<Object> recomputedUnassignedElementList;
    private final IdentityHashMap<Object, ChainState> ownerToChainStateMap;
    private final List<ChainState> dirtyChainStateList;

    @SuppressWarnings("unchecked")
    ListElementBlockUpdater(
            ListVariableDescriptor<Solution_> listVariableDescriptor,
            ListVariableState<Solution_, Object, Object> listVariableState,
            boolean isChainInListOrder,
            EntityConsistencyState<Solution_, Object> ownerConsistencyState,
            EntityConsistencyState<Solution_, Object> elementConsistencyState,
            List<DeclarativeShadowVariableDescriptor<Solution_>> sortedElementDescriptorList,
            List<DeclarativeShadowVariableDescriptor<Solution_>> preChainVariableDescriptorList,
            boolean canTerminateEarly) {
        this.listVariableMetaModel = listVariableDescriptor.getVariableMetaModel();
        this.preChainVariableDescriptors = preChainVariableDescriptorList.toArray(new DeclarativeShadowVariableDescriptor[0]);
        this.listVariableDescriptor = listVariableDescriptor;
        this.listVariableState = listVariableState;
        this.isChainInListOrder = isChainInListOrder;
        this.ownerConsistencyState = ownerConsistencyState;
        this.elementConsistencyState = elementConsistencyState;
        this.canTerminateEarly = canTerminateEarly;
        this.changedElementList = new ArrayList<>();
        this.recomputedUnassignedElementList = new ArrayList<>();
        this.ownerToChainStateMap = new IdentityHashMap<>();
        this.dirtyChainStateList = new ArrayList<>();

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
    public Object nodeGroupKey() {
        // One block node per list entity, which is also how the graph looks them up;
        // a metamodel never collides with the other updaters, whose keys are their group ids.
        return listVariableMetaModel;
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
        // The list cannot change while the graph updates, so it is read once.
        var elementList = listVariableDescriptor.getValue(owner);
        var chainState = ownerToChainStateMap.get(owner);
        if (isEntityInconsistent) {
            // The owner is part of a dependency loop the solver may break later;
            // its elements read its pre-chain variables, so they are inconsistent with it.
            chainState.isChainInconsistent = true;
            return markChainInconsistent(elementList, changedVariableNotifier);
        }
        var chainLength = elementList.size();
        // The pre-chain nodes come before the block node in the graph's order,
        // so they are already up to date for this update.
        var isPreChainChanged = updatePreChainValues(owner, chainState);
        if (isPreChainChanged || chainState.isChainInconsistent || chainState.isWholeChainDirty
                || chainState.lastDirtyIndex < 0) {
            // Every element may read the pre-chain variables, the owner left a dependency loop that took its
            // whole chain down, or nothing was recorded: the whole chain is walked.
            chainState.isChainInconsistent = false;
            return walkChain(elementList, 0, chainLength, changedVariableNotifier);
        }
        var firstDirtyPosition = isChainInListOrder ? chainState.firstDirtyIndex : chainLength - 1 - chainState.lastDirtyIndex;
        var lastDirtyPosition = isChainInListOrder ? chainState.lastDirtyIndex : chainLength - 1 - chainState.firstDirtyIndex;
        return walkChain(elementList, firstDirtyPosition, lastDirtyPosition, changedVariableNotifier);
    }

    /**
     * @param lastDirtyPosition the walk does not stop early before it;
     *        the chain length for a chain walked in full
     */
    private boolean walkChain(List<Object> elementList, int firstDirtyPosition, int lastDirtyPosition,
            ChangedVariableNotifier<Solution_> changedVariableNotifier) {
        var anyElementChangedInWalk = false;
        var chainLength = elementList.size();
        for (var position = firstDirtyPosition; position < chainLength; position++) {
            var element = elementAt(elementList, position);
            if (!elementConsistencyState.isEntityConsistent(element)) {
                elementConsistencyState.setEntityIsInconsistent(changedVariableNotifier, element, false);
            }
            var anyElementVariableChanged = false;
            for (var updater : elementUpdaters) {
                anyElementVariableChanged |= updater.updateIfChanged(element, changedVariableNotifier);
            }
            anyElementChangedInWalk |= anyElementVariableChanged;
            // A swap can leave non-contiguous dirty elements, so stop only past the last one.
            if (canTerminateEarly && !anyElementVariableChanged && position >= lastDirtyPosition) {
                break;
            }
        }
        return anyElementChangedInWalk;
    }

    private boolean markChainInconsistent(List<Object> elementList,
            ChangedVariableNotifier<Solution_> changedVariableNotifier) {
        var anyElementChanged = false;
        for (var position = 0; position < elementList.size(); position++) {
            var element = elementAt(elementList, position);
            if (elementConsistencyState.isEntityConsistent(element)) {
                elementConsistencyState.setEntityIsInconsistent(changedVariableNotifier, element, true);
            }
            for (var updater : elementUpdaters) {
                anyElementChanged |= updater.updateIfChanged(element, null, changedVariableNotifier);
            }
        }
        return anyElementChanged;
    }

    private Object elementAt(List<Object> elementList, int position) {
        return elementList.get(isChainInListOrder ? position : elementList.size() - 1 - position);
    }

    /**
     * @return true if a pre-chain variable differs from the value the chain was last walked with
     */
    private boolean updatePreChainValues(Object owner, ChainState chainState) {
        var isChanged = false;
        for (var i = 0; i < preChainVariableDescriptors.length; i++) {
            var value = preChainVariableDescriptors[i].getValue(owner);
            if (!Objects.equals(chainState.preChainValues[i], value)) {
                chainState.preChainValues[i] = value;
                isChanged = true;
            }
        }
        return isChanged;
    }

    /**
     * Records an element whose source variables changed, or that may have left its list;
     * classified into a per-owner dirty range by {@link #classifyChangedElements}.
     */
    void recordChangedElement(Object element) {
        // An element's list variable state changes all at once, so its events come in a row.
        if (changedElementList.isEmpty() || changedElementList.getLast() != element) {
            changedElementList.add(element);
        }
    }

    /**
     * Registers a list entity whose block node this updater backs.
     */
    void addListEntity(Object owner) {
        ownerToChainStateMap.put(owner, new ChainState(owner, preChainVariableDescriptors.length));
    }

    private void markDirty(ChainState chainState) {
        if (!chainState.isDirty) {
            chainState.isDirty = true;
            dirtyChainStateList.add(chainState);
        }
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
            var owner = listVariableState.getInverseSingleton(element);
            if (owner == null) {
                // A move changing an element before unassigning it records it twice, apart.
                if (!containsSame(recomputedUnassignedElementList, element)) {
                    recomputedUnassignedElementList.add(element);
                    recomputeUnassignedElement(element, changedVariableNotifier);
                }
                continue;
            }
            var chainState = ownerToChainStateMap.get(owner);
            var index = listVariableState.getIndexOrFail(element);
            chainState.firstDirtyIndex = Math.min(chainState.firstDirtyIndex, index);
            chainState.lastDirtyIndex = Math.max(chainState.lastDirtyIndex, index);
            markDirty(chainState);
        }
        changedElementList.clear();
        recomputedUnassignedElementList.clear();
        for (var chainState : dirtyChainStateList) {
            dirtyOwnerConsumer.accept(chainState.owner);
        }
    }

    private void recomputeUnassignedElement(Object element, ChangedVariableNotifier<Solution_> changedVariableNotifier) {
        if (!elementConsistencyState.isEntityConsistent(element)) {
            elementConsistencyState.setEntityIsInconsistent(changedVariableNotifier, element, false);
        }
        for (var updater : elementUpdaters) {
            updater.updateIfChanged(element, changedVariableNotifier);
        }
    }

    @SuppressWarnings("ForLoopReplaceableByForEach")
    private static boolean containsSame(List<Object> elementList, Object element) {
        // Avoid creation of iterators on the hot path.
        for (var i = 0; i < elementList.size(); i++) {
            if (elementList.get(i) == element) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resets the chains the update dirtied.
     * An update that gave up on a dependency loop processed no block node, and the graph keeps their
     * marks for the next update; their chains are kept for it too, whole, because a legacy composite
     * move may change them again before that update, without undoing first.
     */
    void endUpdate(boolean isUpdated) {
        if (!isUpdated) {
            for (var chainState : dirtyChainStateList) {
                chainState.isWholeChainDirty = true;
            }
            return;
        }
        for (var chainState : dirtyChainStateList) {
            chainState.reset();
        }
        dirtyChainStateList.clear();
    }

    /**
     * The dirty part of a list entity's chain, as list indexes.
     */
    private static final class ChainState {

        // Differs from any value, so that the first update walks the whole chain.
        private static final Object NOT_WALKED = new Object();

        private final Object owner;
        // The owner's pre-chain values the chain was last walked with; not reset between updates.
        private final @Nullable Object[] preChainValues;
        private int firstDirtyIndex = Integer.MAX_VALUE;
        private int lastDirtyIndex = -1;
        private boolean isWholeChainDirty;
        // In dirtyChainStateList.
        private boolean isDirty;
        // Marked inconsistent by a dependency loop, until the owner leaves it; not reset between updates.
        private boolean isChainInconsistent;

        private ChainState(Object owner, int preChainVariableCount) {
            this.owner = owner;
            this.preChainValues = new Object[preChainVariableCount];
            Arrays.fill(preChainValues, NOT_WALKED);
        }

        private void reset() {
            firstDirtyIndex = Integer.MAX_VALUE;
            lastDirtyIndex = -1;
            isWholeChainDirty = false;
            isDirty = false;
        }
    }
}
