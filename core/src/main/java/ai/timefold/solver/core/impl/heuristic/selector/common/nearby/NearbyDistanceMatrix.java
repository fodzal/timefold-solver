package ai.timefold.solver.core.impl.heuristic.selector.common.nearby;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import ai.timefold.solver.core.impl.domain.variable.supply.Supply;

public final class NearbyDistanceMatrix<Origin, Destination> implements Supply {

    private final NearbyDistanceMeter<Origin, Destination> nearbyDistanceMeter;
    private final Map<Origin, Destination[]> originToDestinationsMap;
    private final Function<Origin, Iterator<Destination>> destinationIteratorProvider;
    private final ToIntFunction<Origin> destinationSizeFunction;

    public NearbyDistanceMatrix(NearbyDistanceMeter<Origin, Destination> nearbyDistanceMeter, int originSize,
            Function<Origin, Iterator<Destination>> destinationIteratorProvider,
            ToIntFunction<Origin> destinationSizeFunction) {
        this.nearbyDistanceMeter = nearbyDistanceMeter;
        this.originToDestinationsMap = new HashMap<>(originSize, 1.0f);
        this.destinationIteratorProvider = destinationIteratorProvider;
        this.destinationSizeFunction = destinationSizeFunction;
    }

    @SuppressWarnings("unchecked")
    public void addAllDestinations(Origin origin) {
        int destinationSize = destinationSizeFunction.applyAsInt(origin);
        Destination[] destinations = (Destination[]) new Object[destinationSize];
        double[] distances = new double[destinationSize];
        Iterator<Destination> destinationIterator = destinationIteratorProvider.apply(origin);
        int size = 0;
        double highestDistance = Double.MAX_VALUE;
        while (destinationIterator.hasNext()) {
            Destination destination = destinationIterator.next();
            double distance = nearbyDistanceMeter.getNearbyDistance(origin, destination);
            if (distance < highestDistance || size < destinationSize) {
                int insertIndex = Arrays.binarySearch(distances, 0, size, distance);
                if (insertIndex < 0) {
                    insertIndex = -insertIndex - 1;
                } else {
                    while (insertIndex < size && distances[insertIndex] == distance) {
                        insertIndex++;
                    }
                }
                if (size < destinationSize) {
                    size++;
                }
                System.arraycopy(destinations, insertIndex, destinations, insertIndex + 1, size - insertIndex - 1);
                System.arraycopy(distances, insertIndex, distances, insertIndex + 1, size - insertIndex - 1);
                destinations[insertIndex] = destination;
                distances[insertIndex] = distance;
                highestDistance = distances[size - 1];
            }
        }
        // Safety net: destinationSize (the child selector's reported size) is an upper bound; the iterator may yield
        // fewer destinations (e.g. a list variable allowing unassigned values whose size counts unassigned values that
        // the iterator filters out, or pinning). Trim the trailing unused slots so getDestination never returns a null
        // and getDestinationSize reflects the real number of destinations. No-op when the array is already full.
        if (size < destinations.length) {
            destinations = Arrays.copyOf(destinations, size);
        }
        originToDestinationsMap.put(origin, destinations);
    }

    public Object getDestination(Origin origin, int nearbyIndex) {
        Destination[] destinations = originToDestinationsMap.get(origin);
        if (destinations == null) {
            addAllDestinations(origin);
            destinations = originToDestinationsMap.get(origin);
        }
        return destinations[nearbyIndex];
    }

    /**
     * Returns the number of destinations stored for the given origin.
     * This may differ from the child selector's dynamic size (e.g. during construction heuristic).
     */
    public int getDestinationSize(Origin origin) {
        Destination[] destinations = originToDestinationsMap.get(origin);
        if (destinations == null) {
            addAllDestinations(origin);
            destinations = originToDestinationsMap.get(origin);
        }
        return destinations.length;
    }
}
