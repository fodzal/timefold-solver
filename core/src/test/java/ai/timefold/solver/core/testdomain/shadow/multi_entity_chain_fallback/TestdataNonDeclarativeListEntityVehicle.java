package ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_fallback;

import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;
import ai.timefold.solver.core.testdomain.TestdataObject;

/**
 * Declares the list variable but has no declarative shadow variables,
 * so the declarative entity classes are the visit and the depot.
 */
@PlanningEntity
public class TestdataNonDeclarativeListEntityVehicle extends TestdataObject {

    int departureTime;

    @PlanningListVariable(allowsUnassignedValues = true)
    List<TestdataNonDeclarativeListEntityVisit> visits = new ArrayList<>();

    public TestdataNonDeclarativeListEntityVehicle() {
    }

    public TestdataNonDeclarativeListEntityVehicle(String code, int departureTime) {
        super(code);
        this.departureTime = departureTime;
    }

    public int getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(int departureTime) {
        this.departureTime = departureTime;
    }

    public List<TestdataNonDeclarativeListEntityVisit> getVisits() {
        return visits;
    }

    public void setVisits(List<TestdataNonDeclarativeListEntityVisit> visits) {
        this.visits = visits;
    }
}
