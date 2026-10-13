package ai.timefold.solver.core.impl.domain.variable.declarative;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.timefold.solver.core.testdomain.shadow.aligned_multi_entity.TestdataAlignedMultiEntitySolution;
import ai.timefold.solver.core.testdomain.shadow.aligned_multi_entity.TestdataAlignedMultiEntityVehicle;
import ai.timefold.solver.core.testdomain.shadow.aligned_multi_entity.TestdataAlignedMultiEntityVisit;

import org.junit.jupiter.api.Test;

/**
 * Tests that the arbitrary graph calculates an aligned shadow variable once per alignment group,
 * when the declarative shadow variables span more than one entity class.
 */
class AlignedMultiEntityShadowVariableTest {

    @Test
    void graphStructureIsArbitrary() {
        var vehicle = new TestdataAlignedMultiEntityVehicle("A", 0);
        var visit = new TestdataAlignedMultiEntityVisit("a");
        var solutionDescriptor = SolutionDescriptor.buildSolutionDescriptor(TestdataAlignedMultiEntitySolution.class,
                TestdataAlignedMultiEntityVehicle.class, TestdataAlignedMultiEntityVisit.class);

        assertThat(GraphStructure.determineGraphStructure(solutionDescriptor, vehicle, visit).structure())
                .isEqualTo(GraphStructure.ARBITRARY);
    }

    @Test
    void calculateAlignedVariableOncePerGroup() {
        var vehicleA = new TestdataAlignedMultiEntityVehicle("A", 0);
        var vehicleB = new TestdataAlignedMultiEntityVehicle("B", 0);
        var visitA1 = new TestdataAlignedMultiEntityVisit("a1");
        var visitB0 = new TestdataAlignedMultiEntityVisit("b0");
        var visitB1 = new TestdataAlignedMultiEntityVisit("b1");
        var alignedVisits = List.of(visitA1, visitB1);
        visitA1.setAlignedVisits(alignedVisits);
        visitB1.setAlignedVisits(alignedVisits);
        vehicleA.setVisits(new ArrayList<>(List.of(visitA1)));
        vehicleB.setVisits(new ArrayList<>(List.of(visitB0, visitB1)));

        var solution = new TestdataAlignedMultiEntitySolution();
        solution.setVehicles(List.of(vehicleA, vehicleB));
        solution.setVisits(List.of(visitA1, visitB0, visitB1));

        SolutionManager.updateShadowVariables(solution);

        // a1 is ready at 0 and b1 at 1, so both start at 1.
        assertThat(visitA1.getStartTime()).isEqualTo(1);
        assertThat(visitB1.getStartTime()).isEqualTo(1);
        assertThat(vehicleA.getLastEndTime()).isEqualTo(2);
        assertThat(vehicleB.getLastEndTime()).isEqualTo(2);
        assertThat(visitA1.getStartTimeSupplierCallCount() + visitB1.getStartTimeSupplierCallCount())
                .isOne();
    }
}
