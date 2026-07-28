package ai.timefold.solver.core.impl.domain.variable.declarative;

import static ai.timefold.solver.core.impl.domain.variable.declarative.GraphStructure.ARBITRARY;
import static ai.timefold.solver.core.impl.domain.variable.declarative.GraphStructure.ARBITRARY_SINGLE_ENTITY_AT_MOST_ONE_DIRECTIONAL_PARENT_TYPE;
import static ai.timefold.solver.core.impl.domain.variable.declarative.GraphStructure.EMPTY;
import static ai.timefold.solver.core.impl.domain.variable.declarative.GraphStructure.SINGLE_DIRECTIONAL_PARENT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import ai.timefold.solver.core.testdomain.TestdataSolution;
import ai.timefold.solver.core.testdomain.shadow.concurrent.TestdataConcurrentSolution;
import ai.timefold.solver.core.testdomain.shadow.concurrent.TestdataConcurrentValue;
import ai.timefold.solver.core.testdomain.shadow.extended.TestdataDeclarativeExtendedBaseValue;
import ai.timefold.solver.core.testdomain.shadow.extended.TestdataDeclarativeExtendedSolution;
import ai.timefold.solver.core.testdomain.shadow.extended.TestdataDeclarativeExtendedSubclassValue;
import ai.timefold.solver.core.testdomain.shadow.follower.TestdataFollowerEntity;
import ai.timefold.solver.core.testdomain.shadow.follower.TestdataFollowerSolution;
import ai.timefold.solver.core.testdomain.shadow.list_element.TestdataListElementEntity;
import ai.timefold.solver.core.testdomain.shadow.list_element.TestdataListElementSolution;
import ai.timefold.solver.core.testdomain.shadow.list_element.TestdataListElementValue;
import ai.timefold.solver.core.testdomain.shadow.list_element.TestdataMixedListElementEntity;
import ai.timefold.solver.core.testdomain.shadow.list_element.TestdataMixedListElementSolution;
import ai.timefold.solver.core.testdomain.shadow.list_element.TestdataMixedListElementValue;
import ai.timefold.solver.core.testdomain.shadow.multi_directional_parent.TestdataMultiDirectionConcurrentEntity;
import ai.timefold.solver.core.testdomain.shadow.multi_directional_parent.TestdataMultiDirectionConcurrentSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_directional_parent.TestdataMultiDirectionConcurrentValue;
import ai.timefold.solver.core.testdomain.shadow.multi_entity.TestdataMultiEntityDependencyEntity;
import ai.timefold.solver.core.testdomain.shadow.multi_entity.TestdataMultiEntityDependencySolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity.TestdataMultiEntityDependencyValue;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain.TestdataMultiEntityChainSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain.TestdataMultiEntityChainVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain.TestdataMultiEntityChainVisit;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_fallback.TestdataElementFactSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_fallback.TestdataElementFactVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_fallback.TestdataElementFactVisit;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_fallback.TestdataNonOwnerDepot;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_fallback.TestdataNonOwnerSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_fallback.TestdataNonOwnerVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_fallback.TestdataNonOwnerVisit;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_fallback.TestdataWatchedVisitsSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_fallback.TestdataWatchedVisitsVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_fallback.TestdataWatchedVisitsVisit;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_next.TestdataMultiEntityChainNextSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_next.TestdataMultiEntityChainNextVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_next.TestdataMultiEntityChainNextVisit;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_rebase.TestdataMultiEntityChainRebaseSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_rebase.TestdataMultiEntityChainRebaseVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_rebase.TestdataMultiEntityChainRebaseVisit;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence.TestdataPrecedenceSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence.TestdataPrecedenceVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence.TestdataPrecedenceVisit;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence_fallback.TestdataDisjointPrecedenceSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence_fallback.TestdataDisjointPrecedenceVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence_fallback.TestdataDisjointPrecedenceVisit;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence_group.TestdataGroupPrecedenceSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence_group.TestdataGroupPrecedenceVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_precedence_group.TestdataGroupPrecedenceVisit;
import ai.timefold.solver.core.testdomain.shadow.simple_list.TestdataDeclarativeSimpleListSolution;
import ai.timefold.solver.core.testdomain.shadow.simple_list.TestdataDeclarativeSimpleListValue;

import org.junit.jupiter.api.Test;

class GraphStructureTest {
    @Test
    void emptySimpleListStructure() {
        assertThat(GraphStructure.determineGraphStructure(
                TestdataDeclarativeSimpleListSolution.buildSolutionDescriptor()))
                .hasFieldOrPropertyWithValue("structure", EMPTY);
    }

    @Test
    void simpleListStructure() {
        var entity = new TestdataDeclarativeSimpleListValue();
        assertThat(GraphStructure.determineGraphStructure(
                TestdataDeclarativeSimpleListSolution.buildSolutionDescriptor(), entity))
                .hasFieldOrPropertyWithValue("structure", SINGLE_DIRECTIONAL_PARENT)
                .hasFieldOrPropertyWithValue("direction", ParentVariableType.PREVIOUS);
    }

    @Test
    void extendedSimpleListStructure() {
        var entity = new TestdataDeclarativeExtendedSubclassValue();
        assertThat(GraphStructure.determineGraphStructure(
                TestdataDeclarativeExtendedSolution.buildSolutionDescriptor(), entity))
                .hasFieldOrPropertyWithValue("structure", SINGLE_DIRECTIONAL_PARENT)
                .hasFieldOrPropertyWithValue("direction", ParentVariableType.PREVIOUS);
    }

    @Test
    void extendedSimpleListStructureWithoutDeclarativeEntities() {
        var entity = new TestdataDeclarativeExtendedBaseValue();
        assertThat(GraphStructure.determineGraphStructure(
                TestdataDeclarativeExtendedSolution.buildSolutionDescriptor(), entity))
                .hasFieldOrPropertyWithValue("structure", EMPTY);
    }

    @Test
    void concurrentValuesStructureWithoutGroups() {
        var value1 = new TestdataConcurrentValue("v1");
        var value2 = new TestdataConcurrentValue("v2");
        value2.setConcurrentValueGroup(Collections.emptyList());
        assertThat(GraphStructure.determineGraphStructure(
                TestdataConcurrentSolution.buildSolutionDescriptor(),
                value1, value2))
                .hasFieldOrPropertyWithValue("structure", SINGLE_DIRECTIONAL_PARENT)
                .hasFieldOrPropertyWithValue("direction", ParentVariableType.PREVIOUS);
    }

    @Test
    void concurrentValuesStructureWithGroups() {
        var value1 = new TestdataConcurrentValue("v1");
        var value2 = new TestdataConcurrentValue("v2");
        var group = List.of(value1, value2);
        value2.setConcurrentValueGroup(group);
        assertThat(GraphStructure.determineGraphStructure(
                TestdataConcurrentSolution.buildSolutionDescriptor(),
                value1, value2))
                .hasFieldOrPropertyWithValue("structure", ARBITRARY_SINGLE_ENTITY_AT_MOST_ONE_DIRECTIONAL_PARENT_TYPE);
    }

    @Test
    void followerStructure() {
        var entity = new TestdataFollowerEntity();
        assertThat(GraphStructure.determineGraphStructure(
                TestdataFollowerSolution.buildSolutionDescriptor(), entity))
                .hasFieldOrPropertyWithValue("structure", ARBITRARY_SINGLE_ENTITY_AT_MOST_ONE_DIRECTIONAL_PARENT_TYPE);
    }

    @Test
    void multiEntity() {
        // The values' dependencies on each other are precedences,
        // which the precedence-aware multi-entity graph supports.
        var entity = new TestdataMultiEntityDependencyEntity();
        var value = new TestdataMultiEntityDependencyValue();
        assertThat(GraphStructure.determineGraphStructure(
                TestdataMultiEntityDependencySolution.buildSolutionDescriptor(), entity, value))
                .hasFieldOrPropertyWithValue("structure",
                        GraphStructure.MULTI_ENTITY_SINGLE_DIRECTIONAL_PARENT_WITH_PRECEDENCE)
                .hasFieldOrPropertyWithValue("direction", ParentVariableType.PREVIOUS);
    }

    @Test
    void multiDirectionalParents() {
        var entity = new TestdataMultiDirectionConcurrentEntity();
        var value = new TestdataMultiDirectionConcurrentValue();
        value.setConcurrentValueGroup(List.of(value));
        assertThat(GraphStructure.determineGraphStructure(
                TestdataMultiDirectionConcurrentSolution.buildSolutionDescriptor(), entity, value))
                .hasFieldOrPropertyWithValue("structure", ARBITRARY);
    }

    @Test
    void multiDirectionalParentsEmptyGroups() {
        var entity = new TestdataMultiDirectionConcurrentEntity();
        var value = new TestdataMultiDirectionConcurrentValue();
        assertThat(GraphStructure.determineGraphStructure(
                TestdataMultiDirectionConcurrentSolution.buildSolutionDescriptor(), entity, value))
                .hasFieldOrPropertyWithValue("structure", SINGLE_DIRECTIONAL_PARENT)
                .hasFieldOrPropertyWithValue("direction", ParentVariableType.PREVIOUS);
    }

    @Test
    void listElementStructure() {
        var entity = new TestdataListElementEntity("e1");
        var value = new TestdataListElementValue("v1");
        assertThat(GraphStructure.determineGraphStructure(
                TestdataListElementSolution.buildSolutionDescriptor(), entity, value))
                .hasFieldOrPropertyWithValue("structure", GraphStructure.MULTI_ENTITY_SINGLE_DIRECTIONAL_PARENT)
                .hasFieldOrPropertyWithValue("direction", ParentVariableType.PREVIOUS);
    }

    @Test
    void listElementStructureWithGenuineVariableOnElement() {
        var entity = new TestdataMixedListElementEntity("e1");
        var value = new TestdataMixedListElementValue("v1");
        assertThat(GraphStructure.determineGraphStructure(
                TestdataMixedListElementSolution.buildSolutionDescriptor(), entity, value))
                .hasFieldOrPropertyWithValue("structure", ARBITRARY);
    }

    @Test
    void multiEntityChainStructure() {
        var vehicle = new TestdataMultiEntityChainVehicle("A", 0);
        var visit = new TestdataMultiEntityChainVisit("v1");
        assertThat(GraphStructure.determineGraphStructure(
                TestdataMultiEntityChainSolution.buildSolutionDescriptor(), vehicle, visit))
                .hasFieldOrPropertyWithValue("structure", GraphStructure.MULTI_ENTITY_SINGLE_DIRECTIONAL_PARENT)
                .hasFieldOrPropertyWithValue("direction", ParentVariableType.PREVIOUS);
    }

    @Test
    void multiEntityChainNextStructure() {
        var vehicle = new TestdataMultiEntityChainNextVehicle("A", 100);
        var visit = new TestdataMultiEntityChainNextVisit("v1");
        assertThat(GraphStructure.determineGraphStructure(
                TestdataMultiEntityChainNextSolution.buildSolutionDescriptor(), vehicle, visit))
                .hasFieldOrPropertyWithValue("structure", GraphStructure.MULTI_ENTITY_SINGLE_DIRECTIONAL_PARENT)
                .hasFieldOrPropertyWithValue("direction", ParentVariableType.NEXT);
    }

    @Test
    void multiEntityChainRebaseStructure() {
        var vehicle = new TestdataMultiEntityChainRebaseVehicle("A", 0);
        var visit = new TestdataMultiEntityChainRebaseVisit("v1", 1, true);
        assertThat(GraphStructure.determineGraphStructure(
                TestdataMultiEntityChainRebaseSolution.buildSolutionDescriptor(), vehicle, visit))
                .hasFieldOrPropertyWithValue("structure", GraphStructure.MULTI_ENTITY_SINGLE_DIRECTIONAL_PARENT)
                .hasFieldOrPropertyWithValue("direction", ParentVariableType.PREVIOUS);
    }

    @Test
    void multiEntityPrecedenceStructure() {
        var vehicle = new TestdataPrecedenceVehicle("A", 0);
        var visit1 = new TestdataPrecedenceVisit("v1", 1);
        var visit2 = new TestdataPrecedenceVisit("v2", 1);
        visit2.setRequiredPredecessor(visit1);
        assertThat(GraphStructure.determineGraphStructure(
                TestdataPrecedenceSolution.buildSolutionDescriptor(), vehicle, visit1, visit2))
                .hasFieldOrPropertyWithValue("structure",
                        GraphStructure.MULTI_ENTITY_SINGLE_DIRECTIONAL_PARENT_WITH_PRECEDENCE)
                .hasFieldOrPropertyWithValue("direction", ParentVariableType.PREVIOUS);
    }

    @Test
    void multiEntityGroupPrecedenceStructure() {
        var vehicle = new TestdataGroupPrecedenceVehicle("A", 0);
        var visit1 = new TestdataGroupPrecedenceVisit("v1", 1);
        var visit2 = new TestdataGroupPrecedenceVisit("v2", 1);
        visit2.getRequiredPredecessorList().add(visit1);
        assertThat(GraphStructure.determineGraphStructure(
                TestdataGroupPrecedenceSolution.buildSolutionDescriptor(), vehicle, visit1, visit2))
                .hasFieldOrPropertyWithValue("structure",
                        GraphStructure.MULTI_ENTITY_SINGLE_DIRECTIONAL_PARENT_WITH_PRECEDENCE)
                .hasFieldOrPropertyWithValue("direction", ParentVariableType.PREVIOUS);
    }

    @Test
    void multiEntityPrecedenceWithDisjointVariablesFallsBack() {
        // The precedence targets arrivalTime, which cannot reach departureTime,
        // the variable other visits read, so entity-level cycle detection would be inexact.
        var vehicle = new TestdataDisjointPrecedenceVehicle("A", 0);
        var visit1 = new TestdataDisjointPrecedenceVisit("v1", 1);
        var visit2 = new TestdataDisjointPrecedenceVisit("v2", 1);
        visit2.setRequiredPredecessor(visit1);
        assertThat(GraphStructure.determineGraphStructure(
                TestdataDisjointPrecedenceSolution.buildSolutionDescriptor(), vehicle, visit1, visit2))
                .hasFieldOrPropertyWithValue("structure", ARBITRARY);
    }

    @Test
    void multiEntityChainWithFactCollectionOfElements() {
        var vehicle = new TestdataWatchedVisitsVehicle("A", 0);
        var watcher = new TestdataWatchedVisitsVehicle("W", 0);
        var visit = new TestdataWatchedVisitsVisit("v1", 1);
        watcher.getWatchedVisits().add(visit);
        assertThat(GraphStructure.determineGraphStructure(
                TestdataWatchedVisitsSolution.buildSolutionDescriptor(), vehicle, watcher, visit))
                .hasFieldOrPropertyWithValue("structure", ARBITRARY);
    }

    @Test
    void multiEntityChainWithElementFactSource() {
        var vehicle = new TestdataElementFactVehicle("A", 0);
        var visit1 = new TestdataElementFactVisit("v1", 1);
        var visit2 = new TestdataElementFactVisit("v2", 1);
        visit1.setBuddy(visit2);
        visit2.setBuddy(visit1);
        assertThat(GraphStructure.determineGraphStructure(
                TestdataElementFactSolution.buildSolutionDescriptor(), vehicle, visit1, visit2))
                .hasFieldOrPropertyWithValue("structure", ARBITRARY);
    }

    @Test
    void multiEntityChainWithNonListOwnerEntity() {
        var vehicle = new TestdataNonOwnerVehicle("A", 0);
        var visit = new TestdataNonOwnerVisit("v1", 1);
        var depot = new TestdataNonOwnerDepot("D", 0);
        assertThat(GraphStructure.determineGraphStructure(
                TestdataNonOwnerSolution.buildSolutionDescriptor(), vehicle, visit, depot))
                .hasFieldOrPropertyWithValue("structure", ARBITRARY);
    }

    @Test
    void emptyStructure() {
        assertThat(GraphStructure.determineGraphStructure(
                TestdataSolution.buildSolutionDescriptor()))
                .hasFieldOrPropertyWithValue("structure", EMPTY);
    }
}
