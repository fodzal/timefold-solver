package ai.timefold.solver.core.impl.heuristic.selector.entity.nearby;

import java.util.Iterator;

import ai.timefold.solver.core.impl.heuristic.selector.common.iterator.SelectionIterator;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMatrix;

final class OriginalNearbyEntityIterator extends SelectionIterator<Object> {

    private final NearbyDistanceMatrix<Object, Object> nearbyDistanceMatrix;
    private final Iterator<Object> replayingOriginEntityIterator;
    private final long childSize;
    private int nextNearbyIndex;

    public OriginalNearbyEntityIterator(NearbyDistanceMatrix<Object, Object> nearbyDistanceMatrix,
            Iterator<Object> replayingOriginEntityIterator, long childSize, boolean discardNearbyIndexZero) {
        this.nearbyDistanceMatrix = nearbyDistanceMatrix;
        this.replayingOriginEntityIterator = replayingOriginEntityIterator;
        this.childSize = childSize;
        this.nextNearbyIndex = discardNearbyIndexZero ? 1 : 0;
    }

    @Override
    public boolean hasNext() {
        return replayingOriginEntityIterator.hasNext() && nextNearbyIndex < childSize;
    }

    @Override
    public Object next() {
        Object origin = replayingOriginEntityIterator.next();
        Object next = nearbyDistanceMatrix.getDestination(origin, nextNearbyIndex);
        nextNearbyIndex++;
        return next;
    }
}
