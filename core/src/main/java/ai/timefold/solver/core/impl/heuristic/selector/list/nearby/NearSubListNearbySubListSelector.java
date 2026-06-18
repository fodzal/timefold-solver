package ai.timefold.solver.core.impl.heuristic.selector.list.nearby;

import java.util.Iterator;

import ai.timefold.solver.core.impl.domain.variable.ListVariableStateSupply;
import ai.timefold.solver.core.impl.domain.variable.descriptor.ListVariableDescriptor;
import ai.timefold.solver.core.impl.heuristic.selector.common.ReachableValues;
import ai.timefold.solver.core.impl.heuristic.selector.common.iterator.UpcomingSelectionIterator;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.AbstractNearbyDistanceMatrixDemand;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.AbstractNearbySelector;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.timefold.solver.core.impl.heuristic.selector.list.RandomSubListSelector;
import ai.timefold.solver.core.impl.heuristic.selector.list.SubList;
import ai.timefold.solver.core.impl.heuristic.selector.list.SubListSelector;
import ai.timefold.solver.core.impl.heuristic.selector.list.mimic.MimicReplayingSubListSelector;
import ai.timefold.solver.core.impl.score.director.ValueRangeManager;
import ai.timefold.solver.core.impl.solver.scope.SolverScope;
import ai.timefold.solver.core.preview.api.domain.metamodel.PositionInList;

public final class NearSubListNearbySubListSelector<Solution_>
        extends AbstractNearbySelector<Solution_, RandomSubListSelector<Solution_>, MimicReplayingSubListSelector<Solution_>>
        implements SubListSelector<Solution_> {

    private ListVariableStateSupply<Solution_, Object, Object> listVariableStateSupply;
    private ValueRangeManager<Solution_> valueRangeManager;
    private boolean valueRangeOnEntity;

    public NearSubListNearbySubListSelector(RandomSubListSelector<Solution_> childSubListSelector,
            SubListSelector<Solution_> originSubListSelector, NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
            NearbyRandom nearbyRandom) {
        super(childSubListSelector, originSubListSelector, nearbyDistanceMeter, nearbyRandom, true);
    }

    @Override
    protected MimicReplayingSubListSelector<Solution_> castReplayingSelector(Object uncastReplayingSelector) {
        if (!(uncastReplayingSelector instanceof MimicReplayingSubListSelector)) {
            throw new IllegalStateException("Impossible state: Nearby subList selector (" + this
                    + ") did not receive a replaying subList selector (" + uncastReplayingSelector + ").");
        }
        return (MimicReplayingSubListSelector<Solution_>) uncastReplayingSelector;
    }

    @Override
    protected AbstractNearbyDistanceMatrixDemand<?, ?, ?, ?> createDemand() {
        return new SubListNearbySubListMatrixDemand<>(nearbyDistanceMeter, nearbyRandom, childSelector, replayingSelector,
                this::computeDestinationSize);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void solvingStarted(SolverScope<Solution_> solverScope) {
        super.solvingStarted(solverScope);
        var supplyManager = solverScope.getScoreDirector().getSupplyManager();
        ListVariableDescriptor<Solution_> listVariableDescriptor = childSelector.getVariableDescriptor();
        listVariableStateSupply = supplyManager.demand(listVariableDescriptor.getStateDemand());
        valueRangeManager = solverScope.getScoreDirector().getValueRangeManager();
        // Only entity-provided value ranges need per-move reachability filtering; solution-level ranges accept all values.
        valueRangeOnEntity = !listVariableDescriptor.getValueRangeDescriptor().canExtractValueRangeFromSolution();
    }

    private int computeDestinationSize(Object origin) {
        long valueCount = childSelector.getValueCount();
        if (valueCount > Integer.MAX_VALUE) {
            throw new IllegalStateException("The childSubListSelector (" + childSelector
                    + ") has a valueCount (" + valueCount + ") which is higher than Integer.MAX_VALUE.");
        }
        int destinationSize = (int) valueCount;
        int overallSizeMaximum = nearbyRandom.getOverallSizeMaximum();
        if (destinationSize > overallSizeMaximum) {
            destinationSize = overallSizeMaximum;
        }
        return destinationSize;
    }

    @Override
    public void solvingEnded(SolverScope<Solution_> solverScope) {
        super.solvingEnded(solverScope);
        listVariableStateSupply = null;
        valueRangeManager = null;
    }

    @Override
    public boolean isCountable() {
        return childSelector.isCountable();
    }

    @Override
    public long getSize() {
        return childSelector.getSize();
    }

    @Override
    public Iterator<SubList> iterator() {
        Iterator<SubList> replayingOriginSubListIterator = replayingSelector.iterator();
        var reachableValues = valueRangeOnEntity ? valueRangeManager.getReachableValues(childSelector.getVariableDescriptor())
                : null;
        return new RandomSubListNearbySubListIterator(replayingOriginSubListIterator, childSelector.getValueCount(),
                reachableValues);
    }

    private final class RandomSubListNearbySubListIterator extends UpcomingSelectionIterator<SubList> {
        private final Iterator<SubList> replayingOriginSubListIterator;
        private final int nearbySize;
        /** Reachability index, or {@code null} when the value range is solution-level (no swap filtering needed). */
        private final ReachableValues<Object, Object> reachableValues;

        public RandomSubListNearbySubListIterator(Iterator<SubList> replayingOriginSubListIterator, long childSize,
                ReachableValues<Object, Object> reachableValues) {
            this.replayingOriginSubListIterator = replayingOriginSubListIterator;
            if (childSize > Integer.MAX_VALUE) {
                throw new IllegalStateException("The destinationSize (" + childSize + ") is higher than Integer.MAX_VALUE.");
            }
            nearbySize = (int) childSize;
            this.reachableValues = reachableValues;
        }

        @Override
        protected SubList createUpcomingSelection() {
            if (!replayingOriginSubListIterator.hasNext() || nearbySize == 0 || childSelector.getSize() == 0) {
                return noUpcomingSelection();
            }
            SubList originSubList = replayingOriginSubListIterator.next();
            ListVariableDescriptor<Solution_> variableDescriptor = childSelector.getVariableDescriptor();
            Object origin = variableDescriptor.getElement(originSubList.entity(), originSubList.fromIndex());

            // When the value range is entity-provided, retry until the swap is value-range-feasible (both sub lists fit
            // the other entity's value range). With a solution-level value range, this runs exactly once as before.
            int reachabilityBudget = reachableValues == null ? 1 : nearbySize;
            while (reachabilityBudget-- > 0) {
                Object nearbyElementEntity = null;
                int nearbyElementListIndex = -1;
                int availableListSize = -1;

                // Bound retries to avoid a potential infinite loop when allowsUnassignedValues=true and the
                // truncated nearby matrix happens to contain only unassigned elements for this origin.
                int unassignedSkipBudget = nearbySize;
                while (availableListSize < childSelector.getMinimumSubListSize()) {
                    int nearbyIndex = nearbyRandom.nextInt(workingRandom, nearbySize);
                    Object nearbyElement = nearbyDistanceMatrix.getDestination(origin, nearbyIndex);
                    // Skip unassigned elements: getIndex would return null and unbox to NPE.
                    if (!(listVariableStateSupply.getElementPosition(nearbyElement) instanceof PositionInList position)) {
                        if (--unassignedSkipBudget < 0) {
                            // The nearby matrix only contains unassigned elements for this origin: abort the move.
                            return noUpcomingSelection();
                        }
                        continue;
                    }
                    nearbyElementEntity = position.entity();
                    nearbyElementListIndex = position.index();
                    availableListSize = variableDescriptor.getListSize(nearbyElementEntity) - nearbyElementListIndex;
                }

                int maxSubListSize = Math.min(childSelector.getMaximumSubListSize(), availableListSize);
                int subListSizeRange = maxSubListSize - childSelector.getMinimumSubListSize();
                int subListSize = (subListSizeRange == 0 ? 0 : workingRandom.nextInt(subListSizeRange))
                        + childSelector.getMinimumSubListSize();

                if (reachableValues == null
                        || isSwapReachable(variableDescriptor, originSubList, nearbyElementEntity, nearbyElementListIndex,
                                subListSize)) {
                    return new SubList(nearbyElementEntity, nearbyElementListIndex, subListSize);
                }
            }
            // No value-range-feasible swap target found within the budget; abort.
            // SubListSwapMove#isMoveDoable still guarantees correctness for any move that is produced.
            return noUpcomingSelection();
        }

        /**
         * After a sub list swap, the origin sub list values are assigned to {@code otherEntity} and the other sub list
         * values to the origin entity. Returns whether both transfers stay within the entities' value ranges.
         */
        private boolean isSwapReachable(ListVariableDescriptor<Solution_> variableDescriptor, SubList originSubList,
                Object otherEntity, int otherFromIndex, int otherLength) {
            Object originEntity = originSubList.entity();
            int originTo = originSubList.fromIndex() + originSubList.length();
            for (int i = originSubList.fromIndex(); i < originTo; i++) {
                if (!reachableValues.isEntityReachable(variableDescriptor.getElement(originEntity, i), otherEntity)) {
                    return false;
                }
            }
            int otherTo = otherFromIndex + otherLength;
            for (int i = otherFromIndex; i < otherTo; i++) {
                if (!reachableValues.isEntityReachable(variableDescriptor.getElement(otherEntity, i), originEntity)) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public ListVariableDescriptor<Solution_> getVariableDescriptor() {
        return childSelector.getVariableDescriptor();
    }

    @Override
    public Iterator<Object> endingValueIterator() {
        throw new UnsupportedOperationException("Not used.");
    }

    @Override
    public long getValueCount() {
        throw new UnsupportedOperationException("Not used.");
    }
}
