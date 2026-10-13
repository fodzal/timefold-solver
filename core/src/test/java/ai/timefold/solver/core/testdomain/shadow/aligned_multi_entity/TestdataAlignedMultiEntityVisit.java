package ai.timefold.solver.core.testdomain.shadow.aligned_multi_entity;

import java.util.List;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.timefold.solver.core.api.domain.variable.PreviousElementShadowVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowSources;
import ai.timefold.solver.core.api.domain.variable.ShadowVariable;
import ai.timefold.solver.core.testdomain.TestdataObject;

/**
 * A visit which starts together with the other visits of its alignment group,
 * once all of them are ready.
 */
@PlanningEntity
public class TestdataAlignedMultiEntityVisit extends TestdataObject {

    @InverseRelationShadowVariable(sourceVariableName = "visits")
    TestdataAlignedMultiEntityVehicle vehicle;

    @PreviousElementShadowVariable(sourceVariableName = "visits")
    TestdataAlignedMultiEntityVisit previousVisit;

    List<TestdataAlignedMultiEntityVisit> alignedVisits;

    @ShadowVariable(supplierName = "readyTimeSupplier")
    Integer readyTime;

    @ShadowVariable(supplierName = "startTimeSupplier")
    Integer startTime;

    @ShadowVariable(supplierName = "endTimeSupplier")
    Integer endTime;

    int startTimeSupplierCallCount = 0;

    public TestdataAlignedMultiEntityVisit() {
    }

    public TestdataAlignedMultiEntityVisit(String code) {
        super(code);
    }

    @ShadowSources({ "vehicle", "previousVisit", "previousVisit.endTime" })
    public Integer readyTimeSupplier() {
        if (vehicle == null) {
            return null;
        }
        return previousVisit == null ? vehicle.getDepartureTime() : previousVisit.getEndTime();
    }

    @ShadowSources(value = { "readyTime", "alignedVisits[].readyTime" }, alignmentKey = "alignedVisits")
    public Integer startTimeSupplier() {
        startTimeSupplierCallCount++;
        if (readyTime == null) {
            return null;
        }
        var start = readyTime;
        if (alignedVisits != null) {
            for (var alignedVisit : alignedVisits) {
                if (alignedVisit.getReadyTime() == null) {
                    return null;
                }
                start = Math.max(start, alignedVisit.getReadyTime());
            }
        }
        return start;
    }

    @ShadowSources("startTime")
    public Integer endTimeSupplier() {
        if (startTime == null) {
            return null;
        }
        return startTime + 1;
    }

    public TestdataAlignedMultiEntityVehicle getVehicle() {
        return vehicle;
    }

    public void setVehicle(TestdataAlignedMultiEntityVehicle vehicle) {
        this.vehicle = vehicle;
    }

    public TestdataAlignedMultiEntityVisit getPreviousVisit() {
        return previousVisit;
    }

    public void setPreviousVisit(TestdataAlignedMultiEntityVisit previousVisit) {
        this.previousVisit = previousVisit;
    }

    public List<TestdataAlignedMultiEntityVisit> getAlignedVisits() {
        return alignedVisits;
    }

    public void setAlignedVisits(List<TestdataAlignedMultiEntityVisit> alignedVisits) {
        this.alignedVisits = alignedVisits;
    }

    public Integer getReadyTime() {
        return readyTime;
    }

    public void setReadyTime(Integer readyTime) {
        this.readyTime = readyTime;
    }

    public Integer getStartTime() {
        return startTime;
    }

    public void setStartTime(Integer startTime) {
        this.startTime = startTime;
    }

    public Integer getEndTime() {
        return endTime;
    }

    public void setEndTime(Integer endTime) {
        this.endTime = endTime;
    }

    public int getStartTimeSupplierCallCount() {
        return startTimeSupplierCallCount;
    }
}
