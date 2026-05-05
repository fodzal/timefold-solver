package ai.timefold.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Objects;

import ai.timefold.solver.core.impl.domain.variable.supply.Demand;
import ai.timefold.solver.core.impl.domain.variable.supply.SupplyManager;

public abstract class AbstractNearbyDistanceMatrixDemand<Origin_, Destination_, ChildSelector_, ReplayingSelector_>
        implements Demand<NearbyDistanceMatrix<Origin_, Destination_>> {

    protected final NearbyDistanceMeter<Origin_, Destination_> meter;
    protected final NearbyRandom random;
    protected final ChildSelector_ childSelector;
    protected final ReplayingSelector_ replayingSelector;

    protected AbstractNearbyDistanceMatrixDemand(NearbyDistanceMeter<Origin_, Destination_> meter, NearbyRandom random,
            ChildSelector_ childSelector, ReplayingSelector_ replayingSelector) {
        this.meter = meter;
        this.random = random;
        this.childSelector = childSelector;
        this.replayingSelector = replayingSelector;
    }

    @Override
    public final NearbyDistanceMatrix<Origin_, Destination_> createExternalizedSupply(SupplyManager supplyManager) {
        return supplyNearbyDistanceMatrix();
    }

    protected abstract NearbyDistanceMatrix<Origin_, Destination_> supplyNearbyDistanceMatrix();

    @Override
    public final boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        AbstractNearbyDistanceMatrixDemand<?, ?, ?, ?> that = (AbstractNearbyDistanceMatrixDemand<?, ?, ?, ?>) o;
        return Objects.equals(meter, that.meter) && Objects.equals(random, that.random)
                && Objects.equals(childSelector, that.childSelector)
                && Objects.equals(replayingSelector, that.replayingSelector);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(meter, random, childSelector, replayingSelector);
    }
}
