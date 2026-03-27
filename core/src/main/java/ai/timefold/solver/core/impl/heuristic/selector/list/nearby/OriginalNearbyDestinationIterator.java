package ai.timefold.solver.core.impl.heuristic.selector.list.nearby;

import java.util.Iterator;
import java.util.function.Function;

import ai.timefold.solver.core.impl.heuristic.selector.common.iterator.SelectionIterator;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMatrix;
import ai.timefold.solver.core.preview.api.domain.metamodel.ElementPosition;

final class OriginalNearbyDestinationIterator extends SelectionIterator<ElementPosition> {

    private final NearbyDistanceMatrix<Object, Object> nearbyDistanceMatrix;
    private final Iterator<?> replayingOriginIterator;
    private final Function<Iterator<?>, Object> originFunction;
    private final Function<Object, ElementPosition> elementPositionFunction;
    private final long childSize;
    private boolean originSelected = false;
    private boolean originIsNotEmpty;
    private Object origin;
    private int nextNearbyIndex;

    public OriginalNearbyDestinationIterator(NearbyDistanceMatrix<Object, Object> nearbyDistanceMatrix,
            Iterator<?> replayingOriginIterator, Function<Object, ElementPosition> elementPositionFunction, long childSize) {
        this(nearbyDistanceMatrix, replayingOriginIterator, Iterator::next, elementPositionFunction, childSize);
    }

    public OriginalNearbyDestinationIterator(NearbyDistanceMatrix<Object, Object> nearbyDistanceMatrix,
            Iterator<?> replayingOriginIterator, Function<Iterator<?>, Object> originFunction,
            Function<Object, ElementPosition> elementPositionFunction, long childSize) {
        this.nearbyDistanceMatrix = nearbyDistanceMatrix;
        this.replayingOriginIterator = replayingOriginIterator;
        this.originFunction = originFunction;
        this.elementPositionFunction = elementPositionFunction;
        this.childSize = childSize;
        nextNearbyIndex = 0;
    }

    private void selectOrigin() {
        if (originSelected)
            return;
        originIsNotEmpty = replayingOriginIterator.hasNext();
        origin = originFunction.apply(replayingOriginIterator);
        originSelected = true;
    }

    @Override
    public boolean hasNext() {
        selectOrigin();
        return originIsNotEmpty && nextNearbyIndex < childSize;
    }

    @Override
    public ElementPosition next() {
        selectOrigin();
        Object next = nearbyDistanceMatrix.getDestination(origin, nextNearbyIndex);
        nextNearbyIndex++;
        return elementPositionFunction.apply(next);
    }
}
