package ai.timefold.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Objects;
import java.util.Random;

import org.apache.commons.math3.distribution.BetaDistribution;

public final class BetaDistributionNearbyRandom implements NearbyRandom {

    private final BetaDistribution betaDistribution;

    public BetaDistributionNearbyRandom(double alpha, double beta) {
        if (alpha <= 0) {
            throw new IllegalArgumentException("The betaDistributionAlpha (" + alpha + ") must be greater than 0.");
        }
        if (beta <= 0) {
            throw new IllegalArgumentException("The betaDistributionBeta (" + beta + ") must be greater than 0.");
        }
        betaDistribution = new BetaDistribution(alpha, beta);
    }

    @Override
    public int nextInt(Random random, int nearbySize) {
        double d = betaDistribution.inverseCumulativeProbability(random.nextDouble());
        int next = (int) (d * nearbySize);
        if (next >= nearbySize) {
            next = nearbySize - 1;
        }
        return next;
    }

    @Override
    public int getOverallSizeMaximum() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (other == null || getClass() != other.getClass())
            return false;
        BetaDistributionNearbyRandom that = (BetaDistributionNearbyRandom) other;
        return Objects.equals(betaDistribution.getAlpha(), that.betaDistribution.getAlpha())
                && Objects.equals(betaDistribution.getBeta(), that.betaDistribution.getBeta());
    }

    @Override
    public int hashCode() {
        return Objects.hash(betaDistribution.getAlpha(), betaDistribution.getBeta());
    }
}
