package ai.timefold.solver.core.impl.heuristic.selector.list.nearby;

import java.util.Iterator;

import ai.timefold.solver.core.impl.domain.variable.ListVariableStateSupply;
import ai.timefold.solver.core.impl.domain.variable.descriptor.ListVariableDescriptor;
import ai.timefold.solver.core.impl.heuristic.selector.common.iterator.UpcomingSelectionIterator;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.AbstractNearbyDistanceMatrixDemand;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.AbstractNearbySelector;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.timefold.solver.core.impl.heuristic.selector.list.RandomSubListSelector;
import ai.timefold.solver.core.impl.heuristic.selector.list.SubList;
import ai.timefold.solver.core.impl.heuristic.selector.list.SubListSelector;
import ai.timefold.solver.core.impl.heuristic.selector.list.mimic.MimicReplayingSubListSelector;
import ai.timefold.solver.core.impl.solver.scope.SolverScope;
import ai.timefold.solver.core.preview.api.domain.metamodel.PositionInList;

public final class NearSubListNearbySubListSelector<Solution_>
        extends AbstractNearbySelector<Solution_, RandomSubListSelector<Solution_>, MimicReplayingSubListSelector<Solution_>>
        implements SubListSelector<Solution_> {

    private ListVariableStateSupply<Solution_, Object, Object> listVariableStateSupply;

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
        return new RandomSubListNearbySubListIterator(replayingOriginSubListIterator, childSelector.getValueCount());
    }

    private final class RandomSubListNearbySubListIterator extends UpcomingSelectionIterator<SubList> {
        private final Iterator<SubList> replayingOriginSubListIterator;
        private final int nearbySize;

        public RandomSubListNearbySubListIterator(Iterator<SubList> replayingOriginSubListIterator, long childSize) {
            this.replayingOriginSubListIterator = replayingOriginSubListIterator;
            if (childSize > Integer.MAX_VALUE) {
                throw new IllegalStateException("The destinationSize (" + childSize + ") is higher than Integer.MAX_VALUE.");
            }
            nearbySize = (int) childSize;
        }

        @Override
        protected SubList createUpcomingSelection() {
            if (!replayingOriginSubListIterator.hasNext() || nearbySize == 0 || childSelector.getSize() == 0) {
                return noUpcomingSelection();
            }
            SubList subList = replayingOriginSubListIterator.next();
            Object origin = replayingSelector.getVariableDescriptor().getElement(subList.entity(), subList.fromIndex());

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
                availableListSize =
                        childSelector.getVariableDescriptor().getListSize(nearbyElementEntity) - nearbyElementListIndex;
            }

            int maxSubListSize = Math.min(childSelector.getMaximumSubListSize(), availableListSize);
            int subListSizeRange = maxSubListSize - childSelector.getMinimumSubListSize();
            int subListSize = (subListSizeRange == 0 ? 0 : workingRandom.nextInt(subListSizeRange))
                    + childSelector.getMinimumSubListSize();

            return new SubList(nearbyElementEntity, nearbyElementListIndex, subListSize);
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
