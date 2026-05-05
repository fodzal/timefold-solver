package ai.timefold.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Iterator;
import java.util.Random;

import ai.timefold.solver.core.impl.heuristic.selector.common.iterator.SelectionIterator;

public final class RandomNearbyIterator extends SelectionIterator<Object> {

    private final NearbyDistanceMatrix<Object, Object> nearbyDistanceMatrix;
    private final NearbyRandom nearbyRandom;
    private final Random workingRandom;
    private final Iterator<Object> replayingIterator;
    private final int nearbySize;
    private final boolean discardNearbyIndexZero;
    private Object origin;

    public RandomNearbyIterator(NearbyDistanceMatrix<Object, Object> nearbyDistanceMatrix, NearbyRandom nearbyRandom,
            Random workingRandom, Iterator<Object> replayingIterator, long childSize, boolean discardNearbyIndexZero) {
        this.nearbyDistanceMatrix = nearbyDistanceMatrix;
        this.nearbyRandom = nearbyRandom;
        this.workingRandom = workingRandom;
        this.replayingIterator = replayingIterator;
        if (childSize > Integer.MAX_VALUE) {
            throw new IllegalStateException("The childSize (" + childSize + ") is higher than Integer.MAX_VALUE.");
        }
        this.nearbySize = (int) childSize - (discardNearbyIndexZero ? 1 : 0);
        this.discardNearbyIndexZero = discardNearbyIndexZero;
    }

    @Override
    public boolean hasNext() {
        return (origin != null || replayingIterator.hasNext()) && nearbySize > 0;
    }

    @Override
    public Object next() {
        if (replayingIterator.hasNext()) {
            origin = replayingIterator.next();
        }
        int nearbyIndex = nearbyRandom.nextInt(workingRandom, nearbySize);
        if (discardNearbyIndexZero) {
            nearbyIndex++;
        }
        return nearbyDistanceMatrix.getDestination(origin, nearbyIndex);
    }
}
