package ai.timefold.solver.core.impl.heuristic.selector.entity.nearby;

import java.util.Iterator;
import java.util.ListIterator;

import ai.timefold.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.AbstractNearbyDistanceMatrixDemand;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.AbstractNearbySelector;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.RandomNearbyIterator;
import ai.timefold.solver.core.impl.heuristic.selector.entity.EntitySelector;
import ai.timefold.solver.core.impl.heuristic.selector.entity.mimic.MimicReplayingEntitySelector;

public final class NearEntityNearbyEntitySelector<Solution_>
        extends AbstractNearbySelector<Solution_, EntitySelector<Solution_>, EntitySelector<Solution_>>
        implements EntitySelector<Solution_> {

    private final boolean discardNearbyIndexZero = true;

    public NearEntityNearbyEntitySelector(EntitySelector<Solution_> childEntitySelector,
            EntitySelector<Solution_> originEntitySelector, NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
            NearbyRandom nearbyRandom, boolean randomSelection) {
        super(childEntitySelector, originEntitySelector, nearbyDistanceMeter, nearbyRandom, randomSelection);
    }

    @Override
    protected EntitySelector<Solution_> castReplayingSelector(Object uncastReplayingSelector) {
        if (!(uncastReplayingSelector instanceof MimicReplayingEntitySelector)) {
            throw new IllegalStateException("Impossible state: Nearby entity selector (" + this
                    + ") did not receive a replaying entity selector (" + uncastReplayingSelector + ").");
        }
        return (EntitySelector<Solution_>) uncastReplayingSelector;
    }

    @Override
    protected AbstractNearbyDistanceMatrixDemand<?, ?, ?, ?> createDemand() {
        return new EntityNearbyDistanceMatrixDemand<>(nearbyDistanceMeter, nearbyRandom, childSelector, replayingSelector,
                origin -> computeDestinationSize(childSelector.getSize()));
    }

    private int computeDestinationSize(long childSize) {
        int destinationSize = (int) childSize;
        if (randomSelection) {
            int overallSizeMaximum = nearbyRandom.getOverallSizeMaximum();
            if (discardNearbyIndexZero && overallSizeMaximum != Integer.MAX_VALUE) {
                overallSizeMaximum++;
            }
            if (destinationSize > overallSizeMaximum) {
                destinationSize = overallSizeMaximum;
            }
        }
        return destinationSize;
    }

    @Override
    public EntityDescriptor<Solution_> getEntityDescriptor() {
        return childSelector.getEntityDescriptor();
    }

    @Override
    public boolean isCountable() {
        return true;
    }

    @Override
    public long getSize() {
        return childSelector.getSize() - (discardNearbyIndexZero ? 1 : 0);
    }

    @Override
    public Iterator<Object> iterator() {
        Iterator<Object> replayingOriginEntityIterator = replayingSelector.iterator();
        if (!randomSelection) {
            return new OriginalNearbyEntityIterator(nearbyDistanceMatrix, replayingOriginEntityIterator,
                    childSelector.getSize(), discardNearbyIndexZero);
        } else {
            return new RandomNearbyIterator(nearbyDistanceMatrix, nearbyRandom, workingRandom,
                    replayingOriginEntityIterator, childSelector.getSize(), discardNearbyIndexZero);
        }
    }

    @Override
    public ListIterator<Object> listIterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListIterator<Object> listIterator(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Object> endingIterator() {
        return childSelector.endingIterator();
    }
}
