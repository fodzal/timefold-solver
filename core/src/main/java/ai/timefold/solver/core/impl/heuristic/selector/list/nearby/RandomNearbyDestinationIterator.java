package ai.timefold.solver.core.impl.heuristic.selector.list.nearby;

import java.util.Iterator;
import java.util.Random;
import java.util.function.Function;

import ai.timefold.solver.core.impl.heuristic.selector.common.iterator.SelectionIterator;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMatrix;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.timefold.solver.core.preview.api.domain.metamodel.ElementPosition;

final class RandomNearbyDestinationIterator extends SelectionIterator<ElementPosition> {

    private final NearbyDistanceMatrix<Object, Object> nearbyDistanceMatrix;
    private final NearbyRandom nearbyRandom;
    private final Random workingRandom;
    private final Iterator<?> replayingOriginIterator;
    private final Function<Iterator<?>, Object> originFunction;
    private final Function<Object, ElementPosition> elementPositionFunction;
    private final int nearbySize;

    public RandomNearbyDestinationIterator(NearbyDistanceMatrix<Object, Object> nearbyDistanceMatrix,
            NearbyRandom nearbyRandom, Random workingRandom, Iterator<Object> replayingOriginIterator,
            Function<Object, ElementPosition> elementPositionFunction, long childSize) {
        this(nearbyDistanceMatrix, nearbyRandom, workingRandom, replayingOriginIterator, Iterator::next,
                elementPositionFunction, childSize);
    }

    public RandomNearbyDestinationIterator(NearbyDistanceMatrix<Object, Object> nearbyDistanceMatrix,
            NearbyRandom nearbyRandom, Random workingRandom, Iterator<?> replayingOriginIterator,
            Function<Iterator<?>, Object> originFunction, Function<Object, ElementPosition> elementPositionFunction,
            long childSize) {
        this.nearbyDistanceMatrix = nearbyDistanceMatrix;
        this.nearbyRandom = nearbyRandom;
        this.workingRandom = workingRandom;
        this.replayingOriginIterator = replayingOriginIterator;
        this.originFunction = originFunction;
        this.elementPositionFunction = elementPositionFunction;
        if (childSize > Integer.MAX_VALUE) {
            throw new IllegalStateException("The destinationSize (" + childSize + ") is higher than Integer.MAX_VALUE.");
        }
        nearbySize = (int) childSize;
    }

    @Override
    public boolean hasNext() {
        return replayingOriginIterator.hasNext() && nearbySize > 0;
    }

    @Override
    public ElementPosition next() {
        Object origin = originFunction.apply(replayingOriginIterator);
        int nearbyIndex = nearbyRandom.nextInt(workingRandom, nearbySize);
        Object next = nearbyDistanceMatrix.getDestination(origin, nearbyIndex);
        return elementPositionFunction.apply(next);
    }
}
