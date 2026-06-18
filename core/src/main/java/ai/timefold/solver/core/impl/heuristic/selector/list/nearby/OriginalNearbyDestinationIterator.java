package ai.timefold.solver.core.impl.heuristic.selector.list.nearby;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.BiPredicate;
import java.util.function.Function;

import ai.timefold.solver.core.impl.heuristic.selector.common.iterator.SelectionIterator;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMatrix;
import ai.timefold.solver.core.preview.api.domain.metamodel.ElementPosition;

final class OriginalNearbyDestinationIterator extends SelectionIterator<ElementPosition> {

    private final NearbyDistanceMatrix<Object, Object> nearbyDistanceMatrix;
    private final Iterator<?> replayingOriginIterator;
    private final Function<Iterator<?>, Object> originFunction;
    private final Function<Object, Object> originToMatrixKey;
    private final Function<Object, ElementPosition> elementPositionFunction;
    /**
     * Optional value-range filter: given the raw origin (the value or sub list being moved) and a candidate
     * destination, returns whether the move would keep the destination entity's value range satisfied.
     * {@code null} means no filtering (e.g. solution-level value range).
     */
    private final BiPredicate<Object, ElementPosition> destinationAcceptor;
    private boolean originSelected = false;
    private boolean originIsNotEmpty;
    private Object origin;
    private Object matrixKey;
    private int destinationSize;
    private int nextNearbyIndex;
    private ElementPosition peeked;
    private boolean peekedComputed;

    public OriginalNearbyDestinationIterator(NearbyDistanceMatrix<Object, Object> nearbyDistanceMatrix,
            Iterator<?> replayingOriginIterator, Function<Object, ElementPosition> elementPositionFunction,
            long childSize, BiPredicate<Object, ElementPosition> destinationAcceptor) {
        this(nearbyDistanceMatrix, replayingOriginIterator, Iterator::next, Function.identity(), elementPositionFunction,
                childSize, destinationAcceptor);
    }

    public OriginalNearbyDestinationIterator(NearbyDistanceMatrix<Object, Object> nearbyDistanceMatrix,
            Iterator<?> replayingOriginIterator, Function<Iterator<?>, Object> originFunction,
            Function<Object, Object> originToMatrixKey, Function<Object, ElementPosition> elementPositionFunction,
            long childSize, BiPredicate<Object, ElementPosition> destinationAcceptor) {
        this.nearbyDistanceMatrix = nearbyDistanceMatrix;
        this.replayingOriginIterator = replayingOriginIterator;
        this.originFunction = originFunction;
        this.originToMatrixKey = originToMatrixKey;
        this.elementPositionFunction = elementPositionFunction;
        this.destinationAcceptor = destinationAcceptor;
        // childSize is a hint; actual limit per origin comes from the matrix
        this.destinationSize = 0;
        nextNearbyIndex = 0;
    }

    private void selectOrigin() {
        if (originSelected)
            return;
        originIsNotEmpty = replayingOriginIterator.hasNext();
        origin = originFunction.apply(replayingOriginIterator);
        if (originIsNotEmpty) {
            matrixKey = originToMatrixKey.apply(origin);
            // Use the actual matrix size for this origin, not the dynamic childSelector size
            destinationSize = nearbyDistanceMatrix.getDestinationSize(matrixKey);
        }
        originSelected = true;
    }

    /**
     * Advances {@link #nextNearbyIndex} until a destination accepted by {@link #destinationAcceptor} is found,
     * skipping out-of-range destinations so that the nearest <em>feasible</em> destinations are returned.
     */
    private ElementPosition peekNextAccepted() {
        while (nextNearbyIndex < destinationSize) {
            Object next = nearbyDistanceMatrix.getDestination(matrixKey, nextNearbyIndex);
            nextNearbyIndex++;
            ElementPosition destination = elementPositionFunction.apply(next);
            if (destinationAcceptor == null || destinationAcceptor.test(origin, destination)) {
                return destination;
            }
        }
        return null;
    }

    @Override
    public boolean hasNext() {
        selectOrigin();
        if (!originIsNotEmpty) {
            return false;
        }
        if (!peekedComputed) {
            peeked = peekNextAccepted();
            peekedComputed = true;
        }
        return peeked != null;
    }

    @Override
    public ElementPosition next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        ElementPosition result = peeked;
        peeked = null;
        peekedComputed = false;
        return result;
    }
}
