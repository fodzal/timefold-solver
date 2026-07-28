package ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence;

import ai.timefold.solver.core.api.score.SimpleScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;

import org.jspecify.annotations.NonNull;

/**
 * Penalizes by the shadow variables, so {@code FULL_ASSERT} catches them if they go stale.
 */
public class TestdataPrecedenceConstraintProvider implements ConstraintProvider {
    @Override
    public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
        return new Constraint[] {
                constraintFactory.forEachIncludingUnassigned(TestdataPrecedenceVisit.class)
                        .filter(visit -> visit.getVehicle() == null)
                        .penalize(SimpleScore.of(100))
                        .asConstraint("Assign all visits"),

                constraintFactory.forEachIncludingUnassigned(TestdataPrecedenceVisit.class)
                        .filter(TestdataPrecedenceVisit::isInconsistent)
                        .penalize(SimpleScore.of(1000))
                        .asConstraint("No dependency cycles"),

                constraintFactory.forEach(TestdataPrecedenceVehicle.class)
                        .penalize(SimpleScore.ONE,
                                vehicle -> vehicle.getEndTime() == null ? 0 : vehicle.getEndTime())
                        .asConstraint("Minimize end time")
        };
    }
}
