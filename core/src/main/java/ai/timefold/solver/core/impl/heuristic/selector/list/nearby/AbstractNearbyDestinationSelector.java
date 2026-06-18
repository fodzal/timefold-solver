package ai.timefold.solver.core.impl.heuristic.selector.list.nearby;

import ai.timefold.solver.core.impl.domain.variable.ListVariableStateSupply;
import ai.timefold.solver.core.impl.domain.variable.descriptor.ListVariableDescriptor;
import ai.timefold.solver.core.impl.heuristic.selector.common.ReachableValues;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.AbstractNearbySelector;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.timefold.solver.core.impl.heuristic.selector.list.DestinationSelector;
import ai.timefold.solver.core.impl.heuristic.selector.list.ElementDestinationSelector;
import ai.timefold.solver.core.impl.phase.event.PhaseLifecycleListener;
import ai.timefold.solver.core.impl.score.director.ValueRangeManager;
import ai.timefold.solver.core.impl.solver.scope.SolverScope;
import ai.timefold.solver.core.preview.api.domain.metamodel.ElementPosition;
import ai.timefold.solver.core.preview.api.domain.metamodel.PositionInList;

abstract class AbstractNearbyDestinationSelector<Solution_, ReplayingSelector_ extends PhaseLifecycleListener<Solution_>>
        extends AbstractNearbySelector<Solution_, ElementDestinationSelector<Solution_>, ReplayingSelector_>
        implements DestinationSelector<Solution_> {

    protected ListVariableStateSupply<Solution_, Object, Object> listVariableStateSupply;
    private ValueRangeManager<Solution_> valueRangeManager;
    private boolean valueRangeOnEntity;

    public AbstractNearbyDestinationSelector(ElementDestinationSelector<Solution_> childDestinationSelector,
            Object originSelector, NearbyDistanceMeter<?, ?> nearbyDistanceMeter, NearbyRandom nearbyRandom,
            boolean randomSelection) {
        super(childDestinationSelector, originSelector, nearbyDistanceMeter, nearbyRandom, randomSelection);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void solvingStarted(SolverScope<Solution_> solverScope) {
        super.solvingStarted(solverScope);
        var supplyManager = solverScope.getScoreDirector().getSupplyManager();
        ListVariableDescriptor<Solution_> listVariableDescriptor = childSelector.getVariableDescriptor();
        listVariableStateSupply = supplyManager.demand(listVariableDescriptor.getStateDemand());
        valueRangeManager = solverScope.getScoreDirector().getValueRangeManager();
        // Only entity-provided value ranges need per-move reachability filtering; solution-level ranges accept all values.
        valueRangeOnEntity = !listVariableDescriptor.getValueRangeDescriptor().canExtractValueRangeFromSolution();
    }

    /**
     * @return the reachability index when the list variable has an entity-provided value range, otherwise {@code null}
     *         (no destination filtering needed). Cached by the {@link ValueRangeManager}, so cheap to call per iterator.
     */
    protected ReachableValues<Object, Object> reachableValuesOrNull() {
        if (!valueRangeOnEntity) {
            return null;
        }
        return valueRangeManager.getReachableValues(childSelector.getVariableDescriptor());
    }

    protected int computeDestinationSize() {
        long childSize = childSelector.getSize();
        if (childSize > Integer.MAX_VALUE) {
            throw new IllegalStateException("The childDestinationSelector (" + childSelector
                    + ") has a destinationSize (" + childSize + ") which is higher than Integer.MAX_VALUE.");
        }
        int destinationSize = (int) childSize;
        if (randomSelection) {
            int overallSizeMaximum = nearbyRandom.getOverallSizeMaximum();
            if (destinationSize > overallSizeMaximum) {
                destinationSize = overallSizeMaximum;
            }
        }
        return destinationSize;
    }

    @Override
    public void solvingEnded(SolverScope<Solution_> solverScope) {
        super.solvingEnded(solverScope);
        listVariableStateSupply = null;
        valueRangeManager = null;
    }

    @Override
    public boolean isCountable() {
        return childSelector.isCountable();
    }

    @Override
    public long getSize() {
        return childSelector.getSize();
    }

    protected ElementPosition toElementPosition(Object next) {
        if (childSelector.getEntityDescriptor().matchesEntity(next)) {
            return ElementPosition.of(next, 0);
        }
        ElementPosition position = listVariableStateSupply.getElementPosition(next);
        if (position instanceof PositionInList positionInList) {
            return ElementPosition.of(positionInList.entity(), positionInList.index() + 1);
        }
        return ElementPosition.unassigned();
    }
}
