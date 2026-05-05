package ai.timefold.solver.core.impl.domain.variable.declarative;

import java.util.Arrays;
import java.util.List;

import ai.timefold.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.timefold.solver.core.impl.util.MutableInt;
import ai.timefold.solver.core.preview.api.domain.metamodel.VariableMetaModel;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NullMarked
public enum GraphStructure {
    /**
     * A graph structure that only accepts the empty graph.
     */
    EMPTY,

    /**
     * A graph structure without dynamic edges. The topological order
     * of such a graph is fixed, since edges are neither added nor removed.
     */
    NO_DYNAMIC_EDGES,

    /**
     * A graph structure where there is at most
     * one directional parent for each graph node, and
     * no indirect parents.
     * For example, when the only input variable from
     * a different entity is previous. This allows us
     * to use a successor function to find affected entities.
     * Since there is at most a single parent node, such a graph
     * cannot be inconsistent.
     */
    SINGLE_DIRECTIONAL_PARENT,

    /**
     * A graph structure where there are multiple entity classes with declarative
     * shadow variables, but one entity class uses a single directional parent
     * (PREVIOUS or NEXT) for its chain, and the other entity class(es) use only
     * VARIABLE/GROUP/NO_PARENT sources (no dynamic edges).
     * The fixed entity class forms a DAG that is processed in topological order,
     * and the chained entity class is processed within each vehicle using chain walk.
     * This avoids Tarjan SCC and allows O(k) propagation.
     */
    MULTI_ENTITY_SINGLE_DIRECTIONAL_PARENT,

    /**
     * A graph structure that accepts all graphs that only have a single
     * entity that uses declarative shadow variables with all directional
     * parents being the same type.
     */
    ARBITRARY_SINGLE_ENTITY_AT_MOST_ONE_DIRECTIONAL_PARENT_TYPE,

    /**
     * A graph structure that accepts all graphs.
     */
    ARBITRARY;

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphStructure.class);

    public record GraphStructureAndDirection(GraphStructure structure,
            @Nullable VariableMetaModel<?, ?, ?> parentMetaModel,
            @Nullable ParentVariableType direction) {
    }

    public static <Solution_> GraphStructureAndDirection determineGraphStructure(
            SolutionDescriptor<Solution_> solutionDescriptor,
            Object... entities) {
        var declarativeShadowVariableDescriptors = solutionDescriptor.getDeclarativeShadowVariableDescriptors();
        if (declarativeShadowVariableDescriptors.isEmpty()) {
            LOGGER.trace("Graph structure: EMPTY (no declarative shadow variables)");
            return new GraphStructureAndDirection(EMPTY, null, null);
        }

        if (!doEntitiesUseDeclarativeShadowVariables(declarativeShadowVariableDescriptors, entities)) {
            LOGGER.trace("Graph structure: EMPTY (no entities use declarative shadow variables)");
            return new GraphStructureAndDirection(EMPTY, null, null);
        }

        var multipleDeclarativeEntityClasses = declarativeShadowVariableDescriptors.stream()
                .map(variable -> variable.getEntityDescriptor().getEntityClass())
                .distinct().count() > 1;

        final var arbitraryGraphStructure = new GraphStructureAndDirection(
                multipleDeclarativeEntityClasses ? ARBITRARY : ARBITRARY_SINGLE_ENTITY_AT_MOST_ONE_DIRECTIONAL_PARENT_TYPE,
                null, null);

        var rootVariableSources = declarativeShadowVariableDescriptors.stream()
                .flatMap(descriptor -> Arrays.stream(descriptor.getSources()))
                .toList();
        ParentVariableType directionalType = null;
        VariableMetaModel<?, ?, ?> parentMetaModel = null;
        var isArbitrary = false;
        var hasIndirect = false;
        Class<?> directionalEntityClass = null;
        for (var variableSource : rootVariableSources) {
            var parentVariableType = variableSource.parentVariableType();
            LOGGER.trace("{} has parentVariableType {}", variableSource, parentVariableType);
            switch (parentVariableType) {
                case GROUP -> {
                    var groupMemberCount = new MutableInt(0);
                    for (var entity : entities) {
                        if (variableSource.rootEntity().isInstance(entity)) {
                            variableSource.valueEntityFunction().accept(entity, fromEntity -> groupMemberCount.increment());
                        }
                    }
                    if (groupMemberCount.intValue() != 0) {
                        isArbitrary = true;
                        var groupParentVariableType = variableSource.groupParentVariableType();
                        if (groupParentVariableType != null && groupParentVariableType.isDirectional()) {
                            var groupParentVariableMetamodel =
                                    variableSource.variableSourceReferences().get(0).variableMetaModel();
                            if (parentMetaModel == null) {
                                parentMetaModel = groupParentVariableMetamodel;
                            } else if (!parentMetaModel
                                    .equals(variableSource.variableSourceReferences().get(0).variableMetaModel())) {
                                LOGGER.trace("Graph structure: ARBITRARY (conflicting group parent meta-models)");
                                return new GraphStructureAndDirection(GraphStructure.ARBITRARY, null, null);
                            }
                        }
                    }
                    // The group variable is unused/always empty
                }
                // CHAINED_NEXT has a complex comparator function;
                // so use ARBITRARY despite the fact it can be represented using SINGLE_DIRECTIONAL_PARENT
                case INDIRECT -> {
                    isArbitrary = true;
                    hasIndirect = true;
                }
                case INVERSE -> isArbitrary = true;
                case VARIABLE, CHAINED_NEXT -> isArbitrary = true;
                case NEXT, PREVIOUS -> {
                    if (parentMetaModel == null) {
                        parentMetaModel = variableSource.variableSourceReferences().get(0).variableMetaModel();
                        directionalType = parentVariableType;
                        directionalEntityClass = variableSource.rootEntity();
                    } else if (!parentMetaModel.equals(variableSource.variableSourceReferences().get(0).variableMetaModel())) {
                        LOGGER.trace("Graph structure: ARBITRARY (conflicting directional parent meta-models)");
                        return new GraphStructureAndDirection(GraphStructure.ARBITRARY, null, null);
                    }
                }
                case NO_PARENT -> {
                    // Do nothing
                }
            }
        }

        if (isArbitrary) {
            // Check if this is a multi-entity case that can use the optimized chained graph.
            // Requirements:
            //   1. Multiple declarative entity classes
            //   2. Exactly one directional type (PREVIOUS or NEXT) on one entity class
            //   3. No INDIRECT sources; INVERSE from chained to fixed entity is allowed
            //      (e.g. Visit.vehicle.lastVisitPreviousVehicle where vehicle is an InverseRelation)
            //   4. The non-directional entity class only uses VARIABLE/GROUP/NO_PARENT
            if (multipleDeclarativeEntityClasses && !hasIndirect && directionalType != null
                    && directionalEntityClass != null) {
                var finalDirectionalEntityClass = directionalEntityClass;
                boolean directionalEntitySafe = rootVariableSources.stream()
                        .filter(s -> finalDirectionalEntityClass.isAssignableFrom(s.rootEntity()))
                        .allMatch(s -> {
                            var type = s.parentVariableType();
                            if (type == ParentVariableType.PREVIOUS || type == ParentVariableType.NEXT
                                    || type == ParentVariableType.NO_PARENT) {
                                return true;
                            }
                            if (type == ParentVariableType.INVERSE) {
                                // Allow INVERSE from the chained entity only if it targets the fixed
                                // (non-chained) entity, e.g. "vehicle.lastVisitPreviousVehicle".
                                // If it targeted another chained entity, the algorithm cannot handle it.
                                var refs = s.variableSourceReferences();
                                if (refs.size() < 2) {
                                    // Single-hop INVERSE is overridden to NO_PARENT; safe
                                    return true;
                                }
                                var targetEntityClass = refs.get(1).variableMetaModel().entity().type();
                                return !finalDirectionalEntityClass.isAssignableFrom(targetEntityClass);
                            }
                            return false;
                        });
                boolean nonDirectionalEntitySafe = rootVariableSources.stream()
                        .filter(s -> !finalDirectionalEntityClass.isAssignableFrom(s.rootEntity()))
                        .map(RootVariableSource::parentVariableType)
                        .allMatch(type -> type == ParentVariableType.VARIABLE
                                || type == ParentVariableType.GROUP
                                || type == ParentVariableType.NO_PARENT);

                if (directionalEntitySafe && nonDirectionalEntitySafe) {
                    LOGGER.trace("Graph structure: MULTI_ENTITY_SINGLE_DIRECTIONAL_PARENT (direction={}, parentMetaModel={})",
                            directionalType, parentMetaModel);
                    return new GraphStructureAndDirection(
                            MULTI_ENTITY_SINGLE_DIRECTIONAL_PARENT, parentMetaModel, directionalType);
                }
            }
            LOGGER.trace("Graph structure: {} (arbitrary fallback)", arbitraryGraphStructure.structure());
            return arbitraryGraphStructure;
        }

        if (directionalType == null) {
            LOGGER.trace("Graph structure: NO_DYNAMIC_EDGES");
            return new GraphStructureAndDirection(NO_DYNAMIC_EDGES, null, null);
        } else if (multipleDeclarativeEntityClasses) {
            // Multiple entity classes but no VARIABLE/GROUP/INDIRECT/INVERSE sources.
            // This shouldn't normally happen, but fall back to arbitrary for safety.
            LOGGER.trace("Graph structure: {} (multiple entity classes, unexpected fallback)",
                    arbitraryGraphStructure.structure());
            return arbitraryGraphStructure;
        } else {
            // Cannot use a single successor function if there are multiple entity classes
            LOGGER.trace("Graph structure: SINGLE_DIRECTIONAL_PARENT (direction={}, parentMetaModel={})",
                    directionalType, parentMetaModel);
            return new GraphStructureAndDirection(SINGLE_DIRECTIONAL_PARENT, parentMetaModel, directionalType);
        }
    }

    private static <Solution_> boolean doEntitiesUseDeclarativeShadowVariables(
            List<DeclarativeShadowVariableDescriptor<Solution_>> declarativeShadowVariableDescriptors, Object... entities) {
        boolean anyDeclarativeEntities = false;
        for (var declarativeShadowVariable : declarativeShadowVariableDescriptors) {
            var entityClass = declarativeShadowVariable.getEntityDescriptor().getEntityClass();
            for (var entity : entities) {
                if (entityClass.isInstance(entity)) {
                    anyDeclarativeEntities = true;
                    break;
                }
                if (anyDeclarativeEntities) {
                    break;
                }
            }
        }
        return anyDeclarativeEntities;
    }
}
