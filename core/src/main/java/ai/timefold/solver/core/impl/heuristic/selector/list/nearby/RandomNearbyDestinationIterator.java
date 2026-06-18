package ai.timefold.solver.core.impl.heuristic.selector.list.nearby;

import java.util.Iterator;
import java.util.Random;
import java.util.function.BiPredicate;
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
    private final Function<Object, Object> originToMatrixKey;
    private final Function<Object, ElementPosition> elementPositionFunction;
    /**
     * Optional value-range filter: given the raw origin (the value or sub list being moved) and a candidate
     * destination, returns whether the move would keep the destination entity's value range satisfied.
     * {@code null} means no filtering (e.g. solution-level value range).
     */
    private final BiPredicate<Object, ElementPosition> destinationAcceptor;
    private final int nearbySize;

    public RandomNearbyDestinationIterator(NearbyDistanceMatrix<Object, Object> nearbyDistanceMatrix,
            NearbyRandom nearbyRandom, Random workingRandom, Iterator<Object> replayingOriginIterator,
            Function<Object, ElementPosition> elementPositionFunction, long childSize,
            BiPredicate<Object, ElementPosition> destinationAcceptor) {
        this(nearbyDistanceMatrix, nearbyRandom, workingRandom, replayingOriginIterator, Iterator::next,
                Function.identity(), elementPositionFunction, childSize, destinationAcceptor);
    }

    public RandomNearbyDestinationIterator(NearbyDistanceMatrix<Object, Object> nearbyDistanceMatrix,
            NearbyRandom nearbyRandom, Random workingRandom, Iterator<?> replayingOriginIterator,
            Function<Iterator<?>, Object> originFunction, Function<Object, Object> originToMatrixKey,
            Function<Object, ElementPosition> elementPositionFunction, long childSize,
            BiPredicate<Object, ElementPosition> destinationAcceptor) {
        this.nearbyDistanceMatrix = nearbyDistanceMatrix;
        this.nearbyRandom = nearbyRandom;
        this.workingRandom = workingRandom;
        this.replayingOriginIterator = replayingOriginIterator;
        this.originFunction = originFunction;
        this.originToMatrixKey = originToMatrixKey;
        this.elementPositionFunction = elementPositionFunction;
        this.destinationAcceptor = destinationAcceptor;
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
        Object matrixKey = originToMatrixKey.apply(origin);
        // nearbySize is a hint derived from the dynamic childSelector size; the matrix holds a fixed snapshot whose
        // per-origin size may be smaller (e.g. a list variable that allows unassigned values reports unassigned
        // values in its size but filters them out of the matrix). Bound the draw by the actual stored size so we
        // never index past the snapshot or onto a trimmed-away null slot.
        int effectiveSize = Math.min(nearbySize, nearbyDistanceMatrix.getDestinationSize(matrixKey));
        // Draw a random nearby destination; when value-range filtering is active, retry up to effectiveSize times to
        // land on a feasible destination. If none is found within the bailout budget, the last candidate is returned
        // and gets rejected by the move's isMoveDoable backstop.
        ElementPosition destination = null;
        int bailoutSize = effectiveSize;
        do {
            int nearbyIndex = nearbyRandom.nextInt(workingRandom, effectiveSize);
            Object next = nearbyDistanceMatrix.getDestination(matrixKey, nearbyIndex);
            destination = elementPositionFunction.apply(next);
            if (destinationAcceptor == null || destinationAcceptor.test(origin, destination)) {
                return destination;
            }
        } while (--bailoutSize > 0);
        return destination;
    }
}
