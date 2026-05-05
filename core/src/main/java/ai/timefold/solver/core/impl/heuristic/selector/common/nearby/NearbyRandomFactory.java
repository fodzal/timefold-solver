package ai.timefold.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Objects;

import ai.timefold.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import ai.timefold.solver.core.config.heuristic.selector.common.nearby.NearbySelectionDistributionType;

public class NearbyRandomFactory {

    public static NearbyRandomFactory create(NearbySelectionConfig nearbySelectionConfig) {
        return new NearbyRandomFactory(nearbySelectionConfig);
    }

    private final NearbySelectionConfig nearbySelectionConfig;

    public NearbyRandomFactory(NearbySelectionConfig nearbySelectionConfig) {
        this.nearbySelectionConfig = nearbySelectionConfig;
    }

    public NearbyRandom buildNearbyRandom(boolean randomSelection) {
        boolean blockEnabled =
                nearbySelectionConfig.getNearbySelectionDistributionType() == NearbySelectionDistributionType.BLOCK_DISTRIBUTION
                        || nearbySelectionConfig.getBlockDistributionSizeMinimum() != null
                        || nearbySelectionConfig.getBlockDistributionSizeMaximum() != null
                        || nearbySelectionConfig.getBlockDistributionSizeRatio() != null
                        || nearbySelectionConfig.getBlockDistributionUniformDistributionProbability() != null;
        boolean linearEnabled = nearbySelectionConfig
                .getNearbySelectionDistributionType() == NearbySelectionDistributionType.LINEAR_DISTRIBUTION
                || nearbySelectionConfig.getLinearDistributionSizeMaximum() != null;
        boolean parabolicEnabled = nearbySelectionConfig
                .getNearbySelectionDistributionType() == NearbySelectionDistributionType.PARABOLIC_DISTRIBUTION
                || nearbySelectionConfig.getParabolicDistributionSizeMaximum() != null;
        boolean betaEnabled =
                nearbySelectionConfig.getNearbySelectionDistributionType() == NearbySelectionDistributionType.BETA_DISTRIBUTION
                        || nearbySelectionConfig.getBetaDistributionAlpha() != null
                        || nearbySelectionConfig.getBetaDistributionBeta() != null;
        if (!randomSelection) {
            if (blockEnabled || linearEnabled || parabolicEnabled || betaEnabled) {
                throw new IllegalArgumentException("The nearbySelectorConfig (" + nearbySelectionConfig
                        + ") with randomSelection (" + randomSelection + ") has distribution parameters.");
            }
            return null;
        }
        long enabledCount =
                (blockEnabled ? 1 : 0) + (linearEnabled ? 1 : 0) + (parabolicEnabled ? 1 : 0) + (betaEnabled ? 1 : 0);
        if (enabledCount > 1) {
            throw new IllegalArgumentException("The nearbySelectorConfig (" + nearbySelectionConfig
                    + ") has multiple distribution types enabled simultaneously.");
        }
        if (blockEnabled) {
            return new BlockDistributionNearbyRandom(
                    Objects.requireNonNullElse(nearbySelectionConfig.getBlockDistributionSizeMinimum(), 1),
                    Objects.requireNonNullElse(nearbySelectionConfig.getBlockDistributionSizeMaximum(), Integer.MAX_VALUE),
                    Objects.requireNonNullElse(nearbySelectionConfig.getBlockDistributionSizeRatio(), 1.0),
                    Objects.requireNonNullElse(nearbySelectionConfig.getBlockDistributionUniformDistributionProbability(),
                            0.0));
        } else if (linearEnabled) {
            return new LinearDistributionNearbyRandom(
                    Objects.requireNonNullElse(nearbySelectionConfig.getLinearDistributionSizeMaximum(), Integer.MAX_VALUE));
        } else if (parabolicEnabled) {
            return new ParabolicDistributionNearbyRandom(
                    Objects.requireNonNullElse(nearbySelectionConfig.getParabolicDistributionSizeMaximum(), Integer.MAX_VALUE));
        } else if (betaEnabled) {
            return new BetaDistributionNearbyRandom(
                    Objects.requireNonNullElse(nearbySelectionConfig.getBetaDistributionAlpha(), 1.0),
                    Objects.requireNonNullElse(nearbySelectionConfig.getBetaDistributionBeta(), 5.0));
        } else {
            return new LinearDistributionNearbyRandom(Integer.MAX_VALUE);
        }
    }
}
