package ai.timefold.solver.core.impl.heuristic.selector.list.nearby;

import java.util.Iterator;
import java.util.function.BiPredicate;
import java.util.function.Function;

import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.AbstractNearbyDistanceMatrixDemand;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.timefold.solver.core.impl.heuristic.selector.list.ElementDestinationSelector;
import ai.timefold.solver.core.impl.heuristic.selector.list.SubList;
import ai.timefold.solver.core.impl.heuristic.selector.list.SubListSelector;
import ai.timefold.solver.core.impl.heuristic.selector.list.mimic.MimicReplayingSubListSelector;
import ai.timefold.solver.core.preview.api.domain.metamodel.ElementPosition;
import ai.timefold.solver.core.preview.api.domain.metamodel.PositionInList;

public final class NearSubListNearbyDestinationSelector<Solution_>
        extends AbstractNearbyDestinationSelector<Solution_, MimicReplayingSubListSelector<Solution_>> {

    public NearSubListNearbyDestinationSelector(ElementDestinationSelector<Solution_> childDestinationSelector,
            SubListSelector<Solution_> originSubListSelector, NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
            NearbyRandom nearbyRandom, boolean randomSelection) {
        super(childDestinationSelector, originSubListSelector, nearbyDistanceMeter, nearbyRandom, randomSelection);
    }

    @Override
    protected MimicReplayingSubListSelector<Solution_> castReplayingSelector(Object uncastReplayingSelector) {
        if (!(uncastReplayingSelector instanceof MimicReplayingSubListSelector)) {
            throw new IllegalStateException("Impossible state: Nearby destination selector (" + this
                    + ") did not receive a replaying subList selector (" + uncastReplayingSelector + ").");
        }
        return (MimicReplayingSubListSelector<Solution_>) uncastReplayingSelector;
    }

    @Override
    protected AbstractNearbyDistanceMatrixDemand<?, ?, ?, ?> createDemand() {
        return new SubListNearbyDistanceMatrixDemand<>(nearbyDistanceMeter, nearbyRandom, childSelector, replayingSelector,
                origin -> computeDestinationSize());
    }

    @Override
    public Iterator<ElementPosition> iterator() {
        Iterator<SubList> replayingOriginSubListIterator = replayingSelector.iterator();
        // The raw origin is the whole sub list; the nearby distance matrix is keyed by its first element.
        Function<Iterator<?>, Object> originFunction = Iterator::next;
        Function<Object, Object> originToMatrixKey = o -> firstElement((SubList) o);
        long destinationSize = computeDestinationSize();
        var reachableValues = reachableValuesOrNull();
        var listVariableDescriptor = replayingSelector.getVariableDescriptor();
        // The whole sub list moves to the destination entity; keep only destinations whose entity accepts every value.
        BiPredicate<Object, ElementPosition> destinationAcceptor = reachableValues == null ? null
                : (rawOrigin, destination) -> {
                    if (!(destination instanceof PositionInList positionInList)) {
                        return true;
                    }
                    var entity = positionInList.entity();
                    SubList subList = (SubList) rawOrigin;
                    var to = subList.fromIndex() + subList.length();
                    for (var i = subList.fromIndex(); i < to; i++) {
                        var value = listVariableDescriptor.getElement(subList.entity(), i);
                        if (!reachableValues.isEntityReachable(value, entity)) {
                            return false;
                        }
                    }
                    return true;
                };
        if (!randomSelection) {
            return new OriginalNearbyDestinationIterator(nearbyDistanceMatrix, replayingOriginSubListIterator, originFunction,
                    originToMatrixKey, this::toElementPosition, destinationSize, destinationAcceptor);
        } else {
            return new RandomNearbyDestinationIterator(nearbyDistanceMatrix, nearbyRandom, workingRandom,
                    replayingOriginSubListIterator, originFunction, originToMatrixKey, this::toElementPosition,
                    destinationSize, destinationAcceptor);
        }
    }

    private Object firstElement(SubList subList) {
        return replayingSelector.getVariableDescriptor().getElement(subList.entity(), subList.fromIndex());
    }
}
