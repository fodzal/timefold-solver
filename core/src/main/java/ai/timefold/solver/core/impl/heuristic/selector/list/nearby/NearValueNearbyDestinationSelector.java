package ai.timefold.solver.core.impl.heuristic.selector.list.nearby;

import java.util.Iterator;
import java.util.function.BiPredicate;

import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.AbstractNearbyDistanceMatrixDemand;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.timefold.solver.core.impl.heuristic.selector.list.ElementDestinationSelector;
import ai.timefold.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import ai.timefold.solver.core.impl.heuristic.selector.value.mimic.MimicReplayingValueSelector;
import ai.timefold.solver.core.preview.api.domain.metamodel.ElementPosition;
import ai.timefold.solver.core.preview.api.domain.metamodel.PositionInList;

public final class NearValueNearbyDestinationSelector<Solution_>
        extends AbstractNearbyDestinationSelector<Solution_, MimicReplayingValueSelector<Solution_>> {

    public NearValueNearbyDestinationSelector(ElementDestinationSelector<Solution_> childDestinationSelector,
            IterableValueSelector<Solution_> originValueSelector, NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
            NearbyRandom nearbyRandom, boolean randomSelection) {
        super(childDestinationSelector, originValueSelector, nearbyDistanceMeter, nearbyRandom, randomSelection);
    }

    @Override
    protected MimicReplayingValueSelector<Solution_> castReplayingSelector(Object uncastReplayingSelector) {
        if (!(uncastReplayingSelector instanceof MimicReplayingValueSelector)) {
            throw new IllegalStateException("Impossible state: Nearby destination selector (" + this
                    + ") did not receive a replaying value selector (" + uncastReplayingSelector + ").");
        }
        return (MimicReplayingValueSelector<Solution_>) uncastReplayingSelector;
    }

    @Override
    protected AbstractNearbyDistanceMatrixDemand<?, ?, ?, ?> createDemand() {
        return new ListNearbyDistanceMatrixDemand<>(nearbyDistanceMeter, nearbyRandom, childSelector, replayingSelector,
                origin -> computeDestinationSize());
    }

    @Override
    public Iterator<ElementPosition> iterator() {
        Iterator<Object> replayingOriginValueIterator = replayingSelector.iterator();
        long destinationSize = computeDestinationSize();
        var reachableValues = reachableValuesOrNull();
        // The origin is the value being moved; keep only destinations whose entity accepts that value.
        BiPredicate<Object, ElementPosition> destinationAcceptor = reachableValues == null ? null
                : (originValue, destination) -> !(destination instanceof PositionInList positionInList)
                        || reachableValues.isEntityReachable(originValue, positionInList.entity());
        if (!randomSelection) {
            return new OriginalNearbyDestinationIterator(nearbyDistanceMatrix, replayingOriginValueIterator,
                    this::toElementPosition, destinationSize, destinationAcceptor);
        } else {
            return new RandomNearbyDestinationIterator(nearbyDistanceMatrix, nearbyRandom, workingRandom,
                    replayingOriginValueIterator, this::toElementPosition, destinationSize, destinationAcceptor);
        }
    }
}
