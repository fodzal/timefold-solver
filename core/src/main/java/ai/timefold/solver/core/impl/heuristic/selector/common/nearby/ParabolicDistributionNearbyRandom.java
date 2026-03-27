package ai.timefold.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Objects;
import java.util.Random;

public final class ParabolicDistributionNearbyRandom implements NearbyRandom {

    private final int sizeMaximum;

    public ParabolicDistributionNearbyRandom(int sizeMaximum) {
        this.sizeMaximum = sizeMaximum;
        if (sizeMaximum < 1) {
            throw new IllegalArgumentException("The maximum (" + sizeMaximum + ") must be at least 1.");
        }
    }

    @Override
    public int nextInt(Random random, int nearbySize) {
        int m = sizeMaximum <= nearbySize ? sizeMaximum : nearbySize;
        double p = random.nextDouble();
        double x = m * (1.0 - Math.pow(1.0 - p, 1.0 / 3.0));
        int next = (int) x;
        if (next >= m) {
            next = m - 1;
        }
        return next;
    }

    @Override
    public int getOverallSizeMaximum() {
        return sizeMaximum;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (other == null || getClass() != other.getClass())
            return false;
        return sizeMaximum == ((ParabolicDistributionNearbyRandom) other).sizeMaximum;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sizeMaximum);
    }
}
