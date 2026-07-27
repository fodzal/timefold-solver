package ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_rebase;

import ai.timefold.solver.core.api.score.SimpleScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;

import org.jspecify.annotations.NonNull;

/**
 * Penalizes by the shadow variables, so {@code FULL_ASSERT} catches them if they go stale.
 */
public class TestdataMultiEntityChainRebaseConstraintProvider implements ConstraintProvider {
    @Override
    public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
        return new Constraint[] {
                constraintFactory.forEachIncludingUnassigned(TestdataMultiEntityChainRebaseVisit.class)
                        .filter(visit -> visit.getVehicle() == null)
                        .penalize(SimpleScore.of(100))
                        .asConstraint("Assign all visits"),

                constraintFactory.forEach(TestdataMultiEntityChainRebaseVehicle.class)
                        .penalize(SimpleScore.ONE, TestdataMultiEntityChainRebaseVehicle::getEndTime)
                        .asConstraint("Minimize end time")
        };
    }
}
