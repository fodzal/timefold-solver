package ai.timefold.solver.core.impl.heuristic.selector.entity.nearby;

import java.util.Iterator;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.AbstractNearbyDistanceMatrixDemand;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMatrix;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.timefold.solver.core.impl.heuristic.selector.entity.EntitySelector;

final class EntityNearbyDistanceMatrixDemand<Solution_, Origin_, Destination_>
        extends
        AbstractNearbyDistanceMatrixDemand<Origin_, Destination_, EntitySelector<Solution_>, EntitySelector<Solution_>> {

    private final ToIntFunction<Origin_> destinationSizeFunction;

    public EntityNearbyDistanceMatrixDemand(NearbyDistanceMeter<Origin_, Destination_> meter, NearbyRandom random,
            EntitySelector<Solution_> childSelector, EntitySelector<Solution_> replayingOriginEntitySelector,
            ToIntFunction<Origin_> destinationSizeFunction) {
        super(meter, random, childSelector, replayingOriginEntitySelector);
        this.destinationSizeFunction = destinationSizeFunction;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NearbyDistanceMatrix<Origin_, Destination_> supplyNearbyDistanceMatrix() {
        long originSize = replayingSelector.getSize();
        if (originSize > Integer.MAX_VALUE) {
            throw new IllegalStateException("The originEntitySelector (" + replayingSelector
                    + ") has an entitySize (" + originSize + ") which is higher than Integer.MAX_VALUE.");
        }
        Function<Origin_, Iterator<Destination_>> destinationIteratorProvider =
                origin -> (Iterator<Destination_>) childSelector.endingIterator();
        NearbyDistanceMatrix<Origin_, Destination_> nearbyDistanceMatrix =
                new NearbyDistanceMatrix<>(meter, (int) originSize, destinationIteratorProvider, destinationSizeFunction);
        replayingSelector.endingIterator()
                .forEachRemaining(origin -> nearbyDistanceMatrix.addAllDestinations((Origin_) origin));
        return nearbyDistanceMatrix;
    }
}
