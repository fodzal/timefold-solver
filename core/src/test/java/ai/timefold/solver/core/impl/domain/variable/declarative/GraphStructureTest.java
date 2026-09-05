package ai.timefold.solver.core.impl.domain.variable.declarative;

import static ai.timefold.solver.core.impl.domain.variable.declarative.GraphStructure.ARBITRARY;
import static ai.timefold.solver.core.impl.domain.variable.declarative.GraphStructure.ARBITRARY_SINGLE_ENTITY_AT_MOST_ONE_DIRECTIONAL_PARENT_TYPE;
import static ai.timefold.solver.core.impl.domain.variable.declarative.GraphStructure.EMPTY;
import static ai.timefold.solver.core.impl.domain.variable.declarative.GraphStructure.NO_DYNAMIC_EDGES;
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
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_element_sourced.TestdataElementSourcedSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_element_sourced.TestdataElementSourcedVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_element_sourced.TestdataElementSourcedVisit;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_fact.TestdataFactChainSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_fact.TestdataFactChainVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_fact.TestdataFactChainVisit;
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
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_loop.TestdataChainLoopSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_loop.TestdataChainLoopVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_loop.TestdataChainLoopVisit;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_next.TestdataMultiEntityChainNextSolution;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_next.TestdataMultiEntityChainNextVehicle;
import ai.timefold.solver.core.testdomain.shadow.multi_entity_chain_next.TestdataMultiEntityChainNextVisit;
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
        var entity = new TestdataMultiEntityDependencyEntity();
        var value = new TestdataMultiEntityDependencyValue();
        assertThat(GraphStructure.determineGraphStructure(
                TestdataMultiEntityDependencySolution.buildSolutionDescriptor(), entity, value))
                .hasFieldOrPropertyWithValue("structure", ARBITRARY);
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
        // The elements are excluded from the graph and represented by per-entity block nodes;
        // the structure describes the graph covering the remaining classes.
        assertThat(GraphStructure.determineGraphStructure(
                TestdataListElementSolution.buildSolutionDescriptor(), entity, value))
                .hasFieldOrPropertyWithValue("structure", NO_DYNAMIC_EDGES)
                .hasFieldOrPropertyWithValue("direction", ParentVariableType.PREVIOUS)
                .hasFieldOrPropertyWithValue("blockedElementClass", TestdataListElementValue.class);
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
        var vehicleA = new TestdataMultiEntityChainVehicle("A", 0);
        var vehicleB = new TestdataMultiEntityChainVehicle("B", 0);
        vehicleB.setPreviousVehicles(List.of(vehicleA));
        var visit = new TestdataMultiEntityChainVisit("v1");
        // The visits are excluded from the graph, which covers the vehicles
        // and their fact collection dependency.
        assertThat(GraphStructure.determineGraphStructure(
                TestdataMultiEntityChainSolution.buildSolutionDescriptor(), vehicleA, vehicleB, visit))
                .hasFieldOrPropertyWithValue("structure",
                        GraphStructure.ARBITRARY_SINGLE_ENTITY_AT_MOST_ONE_DIRECTIONAL_PARENT_TYPE)
                .hasFieldOrPropertyWithValue("direction", ParentVariableType.PREVIOUS)
                .hasFieldOrPropertyWithValue("blockedElementClass", TestdataMultiEntityChainVisit.class);
    }

    @Test
    void multiEntityChainWithFactChainedVehicles() {
        var vehicleA = new TestdataFactChainVehicle("A", 0);
        var vehicleB = new TestdataFactChainVehicle("B", 0);
        vehicleB.setPreviousVehicle(vehicleA);
        var visit = new TestdataFactChainVisit("v1");
        assertThat(GraphStructure.determineGraphStructure(
                TestdataFactChainSolution.buildSolutionDescriptor(), vehicleA, vehicleB, visit))
                .hasFieldOrPropertyWithValue("structure",
                        GraphStructure.ARBITRARY_SINGLE_ENTITY_AT_MOST_ONE_DIRECTIONAL_PARENT_TYPE)
                .hasFieldOrPropertyWithValue("direction", ParentVariableType.PREVIOUS)
                .hasFieldOrPropertyWithValue("blockedElementClass", TestdataFactChainVisit.class);
    }

    @Test
    void multiEntityChainNextStructure() {
        var vehicle = new TestdataMultiEntityChainNextVehicle("A", 100);
        var visit = new TestdataMultiEntityChainNextVisit("v1");
        // The vehicle's fact collection is empty, so the graph has no dynamic edges.
        assertThat(GraphStructure.determineGraphStructure(
                TestdataMultiEntityChainNextSolution.buildSolutionDescriptor(), vehicle, visit))
                .hasFieldOrPropertyWithValue("structure", NO_DYNAMIC_EDGES)
                .hasFieldOrPropertyWithValue("direction", ParentVariableType.NEXT)
                .hasFieldOrPropertyWithValue("blockedElementClass", TestdataMultiEntityChainNextVisit.class);
    }

    @Test
    void multiEntityChainWithFactCollectionOfElements() {
        var vehicle = new TestdataWatchedVisitsVehicle("A", 0);
        var watcher = new TestdataWatchedVisitsVehicle("W", 0);
        var visit = new TestdataWatchedVisitsVisit("v1", 1);
        watcher.getWatchedVisits().add(visit);
        assertThat(GraphStructure.determineGraphStructure(
                TestdataWatchedVisitsSolution.buildSolutionDescriptor(), vehicle, watcher, visit))
                .hasFieldOrPropertyWithValue("structure", ARBITRARY)
                .hasFieldOrPropertyWithValue("blockedElementClass", null);
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
                .hasFieldOrPropertyWithValue("structure", ARBITRARY)
                .hasFieldOrPropertyWithValue("blockedElementClass", null);
    }

    @Test
    void multiEntityChainWithNonListOwnerEntity() {
        var vehicle = new TestdataNonOwnerVehicle("A", 0);
        var visit = new TestdataNonOwnerVisit("v1", 1);
        var depot = new TestdataNonOwnerDepot("D", 0);
        // The block node reports its looped status through the list entity's consistency state,
        // so a list entity without declarative shadow variables falls back to the arbitrary graph.
        assertThat(GraphStructure.determineGraphStructure(
                TestdataNonOwnerSolution.buildSolutionDescriptor(), vehicle, visit, depot))
                .hasFieldOrPropertyWithValue("structure", ARBITRARY)
                .hasFieldOrPropertyWithValue("blockedElementClass", null);
    }

    @Test
    void multiEntityChainWithPlanningVariableChainedVehicles() {
        var vehicle = new TestdataChainLoopVehicle("A", 0);
        var visit = new TestdataChainLoopVisit("v1", 1);
        // The vehicles chain through a planning variable, so the graph has dynamic edges;
        // that does not concern the visits, which are still represented by a block node.
        assertThat(GraphStructure.determineGraphStructure(
                TestdataChainLoopSolution.buildSolutionDescriptor(), vehicle, visit))
                .hasFieldOrPropertyWithValue("structure",
                        GraphStructure.ARBITRARY_SINGLE_ENTITY_AT_MOST_ONE_DIRECTIONAL_PARENT_TYPE)
                .hasFieldOrPropertyWithValue("direction", ParentVariableType.PREVIOUS)
                .hasFieldOrPropertyWithValue("blockedElementClass", TestdataChainLoopVisit.class);
    }

    @Test
    void multiEntityChainWithElementSourcedEndTime() {
        var vehicle = new TestdataElementSourcedVehicle("A", 0);
        var visit = new TestdataElementSourcedVisit("v1", 1);
        // The vehicle's endTime never reads its own startTime, so nothing but the block node's edges
        // orders it after the route it summarizes.
        assertThat(GraphStructure.determineGraphStructure(
                TestdataElementSourcedSolution.buildSolutionDescriptor(), vehicle, visit))
                .hasFieldOrPropertyWithValue("direction", ParentVariableType.PREVIOUS)
                .hasFieldOrPropertyWithValue("blockedElementClass", TestdataElementSourcedVisit.class);
    }

    @Test
    void emptyStructure() {
        assertThat(GraphStructure.determineGraphStructure(
                TestdataSolution.buildSolutionDescriptor()))
                .hasFieldOrPropertyWithValue("structure", EMPTY);
    }
}
