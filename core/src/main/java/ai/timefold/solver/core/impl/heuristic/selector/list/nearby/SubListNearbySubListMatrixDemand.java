package ai.timefold.solver.core.impl.heuristic.selector.list.nearby;

import java.util.Iterator;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.AbstractNearbyDistanceMatrixDemand;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMatrix;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.timefold.solver.core.impl.heuristic.selector.list.RandomSubListSelector;
import ai.timefold.solver.core.impl.heuristic.selector.list.mimic.MimicReplayingSubListSelector;

final class SubListNearbySubListMatrixDemand<Solution_, Origin_, Destination_>
        extends
        AbstractNearbyDistanceMatrixDemand<Origin_, Destination_, RandomSubListSelector<Solution_>, MimicReplayingSubListSelector<Solution_>> {

    private final ToIntFunction<Origin_> destinationSizeFunction;

    public SubListNearbySubListMatrixDemand(NearbyDistanceMeter<Origin_, Destination_> meter, NearbyRandom random,
            RandomSubListSelector<Solution_> childSubListSelector,
            MimicReplayingSubListSelector<Solution_> replayingOriginSubListSelector,
            ToIntFunction<Origin_> destinationSizeFunction) {
        super(meter, random, childSubListSelector, replayingOriginSubListSelector);
        this.destinationSizeFunction = destinationSizeFunction;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NearbyDistanceMatrix<Origin_, Destination_> supplyNearbyDistanceMatrix() {
        long originSize = replayingSelector.getValueCount();
        if (originSize > Integer.MAX_VALUE) {
            throw new IllegalStateException("The originSubListSelector has a size (" + originSize
                    + ") which is higher than Integer.MAX_VALUE.");
        }
        Function<Origin_, Iterator<Destination_>> destinationIteratorProvider =
                origin -> (Iterator<Destination_>) childSelector.endingValueIterator();
        NearbyDistanceMatrix<Origin_, Destination_> nearbyDistanceMatrix =
                new NearbyDistanceMatrix<>(meter, (int) originSize, destinationIteratorProvider, destinationSizeFunction);
        replayingSelector.endingValueIterator()
                .forEachRemaining(origin -> nearbyDistanceMatrix.addAllDestinations((Origin_) origin));
        return nearbyDistanceMatrix;
    }
}
