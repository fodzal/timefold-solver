package ai.timefold.solver.core.impl.heuristic.selector.list.nearby;

import ai.timefold.solver.core.impl.domain.variable.ListVariableStateSupply;
import ai.timefold.solver.core.impl.domain.variable.descriptor.ListVariableDescriptor;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.AbstractNearbySelector;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import ai.timefold.solver.core.impl.heuristic.selector.list.DestinationSelector;
import ai.timefold.solver.core.impl.heuristic.selector.list.ElementDestinationSelector;
import ai.timefold.solver.core.impl.phase.event.PhaseLifecycleListener;
import ai.timefold.solver.core.impl.solver.scope.SolverScope;
import ai.timefold.solver.core.preview.api.domain.metamodel.ElementPosition;
import ai.timefold.solver.core.preview.api.domain.metamodel.PositionInList;

abstract class AbstractNearbyDestinationSelector<Solution_, ReplayingSelector_ extends PhaseLifecycleListener<Solution_>>
        extends AbstractNearbySelector<Solution_, ElementDestinationSelector<Solution_>, ReplayingSelector_>
        implements DestinationSelector<Solution_> {

    protected ListVariableStateSupply<Solution_, Object, Object> listVariableStateSupply;

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
