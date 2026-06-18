package ai.timefold.solver.core.impl.heuristic.selector.list.nearby;

import java.util.Iterator;
import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.Function;

import ai.timefold.solver.core.impl.heuristic.selector.common.iterator.SelectionIterator;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMatrix;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.timefold.solver.core.preview.api.domain.metamodel.ElementPosition;
import ai.timefold.solver.core.preview.api.domain.metamodel.PositionInList;

final class RandomNearbyDestinationIterator extends SelectionIterator<ElementPosition> {

    /**
     * When {@code true}, destinations that resolve to an unassigned position (i.e. nearby points that are themselves
     * unassigned values) are skipped, retrying with another nearby draw until an assigned anchor (a vehicle, or an
     * assigned value's position) is found. This forbids "insert next to an unassigned anchor" moves, at the cost of
     * losing the ability to unassign through these moves. Defaults to {@code false} (the unassigned destination is
     * returned as-is, yielding an unassign / no-op move). Toggled with -Dtimefold.nearby.skipUnassignedDestination=true.
     */
    private static final boolean SKIP_UNASSIGNED_DESTINATION =
            Boolean.getBoolean("timefold.nearby.skipUnassignedDestination");

    private final NearbyDistanceMatrix<Object, Object> nearbyDistanceMatrix;
    private final NearbyRandom nearbyRandom;
    private final Random workingRandom;
    private final Iterator<?> replayingOriginIterator;
    private final Function<Iterator<?>, Object> originFunction;
    private final Function<Object, ElementPosition> elementPositionFunction;
    /**
     * Optional compatibility filter: given the origin (the value being moved) and a resolved destination, returns
     * whether the destination's entity can host the origin (per the entity value range). {@code null} means no
     * filtering (solution-level value range, or the skip-incompatible toggle is off).
     */
    private final BiPredicate<Object, ElementPosition> destinationAcceptor;
    private final int nearbySize;

    public RandomNearbyDestinationIterator(NearbyDistanceMatrix<Object, Object> nearbyDistanceMatrix,
            NearbyRandom nearbyRandom, Random workingRandom, Iterator<Object> replayingOriginIterator,
            Function<Object, ElementPosition> elementPositionFunction, long childSize,
            BiPredicate<Object, ElementPosition> destinationAcceptor) {
        this(nearbyDistanceMatrix, nearbyRandom, workingRandom, replayingOriginIterator, Iterator::next,
                elementPositionFunction, childSize, destinationAcceptor);
    }

    public RandomNearbyDestinationIterator(NearbyDistanceMatrix<Object, Object> nearbyDistanceMatrix,
            NearbyRandom nearbyRandom, Random workingRandom, Iterator<?> replayingOriginIterator,
            Function<Iterator<?>, Object> originFunction, Function<Object, ElementPosition> elementPositionFunction,
            long childSize, BiPredicate<Object, ElementPosition> destinationAcceptor) {
        this.nearbyDistanceMatrix = nearbyDistanceMatrix;
        this.nearbyRandom = nearbyRandom;
        this.workingRandom = workingRandom;
        this.replayingOriginIterator = replayingOriginIterator;
        this.originFunction = originFunction;
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
        // Clamp the draw to the matrix's actual per-origin size: nearbySize is the child selector's reported size,
        // but the matrix holds a (trimmed) snapshot that may be smaller, so indexing by nearbySize alone could run
        // past the array or onto a slot that no longer exists.
        int effectiveSize = Math.min(nearbySize, nearbyDistanceMatrix.getDestinationSize(origin));
        if (effectiveSize <= 0) {
            return ElementPosition.unassigned();
        }
        // bailout: a single draw normally; when filtering (skip unassigned and/or skip incompatible) retry up to
        // effectiveSize times to land on an acceptable anchor. Vehicles are always assigned anchors (and, when the
        // entity value range accepts the origin, reachable), so this terminates as soon as a suitable one is hit.
        boolean filtering = SKIP_UNASSIGNED_DESTINATION || destinationAcceptor != null;
        int bailout = filtering ? effectiveSize : 1;
        ElementPosition position = null;
        do {
            int nearbyIndex = nearbyRandom.nextInt(workingRandom, effectiveSize);
            Object next = nearbyDistanceMatrix.getDestination(origin, nearbyIndex);
            position = elementPositionFunction.apply(next);
            boolean assignedOk = !SKIP_UNASSIGNED_DESTINATION || position instanceof PositionInList;
            boolean reachableOk = destinationAcceptor == null || destinationAcceptor.test(origin, position);
            if (assignedOk && reachableOk) {
                return position;
            }
        } while (--bailout > 0);
        return position;
    }
}
