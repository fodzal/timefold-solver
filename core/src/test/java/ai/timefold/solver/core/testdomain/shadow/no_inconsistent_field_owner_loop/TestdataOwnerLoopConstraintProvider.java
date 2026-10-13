package ai.timefold.solver.core.testdomain.shadow.no_inconsistent_field_owner_loop;

import ai.timefold.solver.core.api.score.SimpleScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;

import org.jspecify.annotations.NonNull;

public class TestdataOwnerLoopConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint @NonNull [] defineConstraints(@NonNull ConstraintFactory constraintFactory) {
        return new Constraint[] {
                constraintFactory.forEach(TestdataOwnerLoopVehicle.class)
                        .filter(vehicle -> vehicle.getEndTime() != null)
                        .penalize(SimpleScore.ONE, TestdataOwnerLoopVehicle::getEndTime)
                        .asConstraint("Return as early as possible")
        };
    }
}
