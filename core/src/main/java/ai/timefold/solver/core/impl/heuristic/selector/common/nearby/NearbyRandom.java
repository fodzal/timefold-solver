package ai.timefold.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Random;

public interface NearbyRandom {

    int nextInt(Random random, int nearbySize);

    int getOverallSizeMaximum();

}
