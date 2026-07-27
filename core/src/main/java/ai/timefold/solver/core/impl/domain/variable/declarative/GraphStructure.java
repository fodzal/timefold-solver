package ai.timefold.solver.core.impl.domain.variable.declarative;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
     * A graph structure with exactly two declarative entity classes:
     * a chained class whose only directional parent is previous or next,
     * and a fixed class that owns the planning list variable and depends on
     * the chained class only through the list variable's own elements.
     * Fixed entities may depend on each other's declarative variables through fact collections,
     * which form a static DAG that is processed in topological order;
     * each chained entity is processed by walking its chain,
     * so no explicit edges nor cycle detection are needed.
     * Such a graph cannot be inconsistent.
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
            return new GraphStructureAndDirection(EMPTY, null, null);
        }

        if (!doEntitiesUseDeclarativeShadowVariables(declarativeShadowVariableDescriptors, entities)) {
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
        Class<?> directionalEntityClass = null;
        var isArbitrary = multipleDeclarativeEntityClasses;
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
                                return new GraphStructureAndDirection(GraphStructure.ARBITRARY, null, null);
                            }
                        }
                    }
                    // The group variable is unused/always empty
                }
                case INDIRECT, INVERSE, VARIABLE, LIST_ELEMENT -> isArbitrary = true;
                case NEXT, PREVIOUS -> {
                    if (parentMetaModel == null) {
                        parentMetaModel = variableSource.variableSourceReferences().get(0).variableMetaModel();
                        directionalType = parentVariableType;
                        directionalEntityClass = variableSource.rootEntity();
                    } else if (!parentMetaModel.equals(variableSource.variableSourceReferences().get(0).variableMetaModel())) {
                        return new GraphStructureAndDirection(GraphStructure.ARBITRARY, null, null);
                    }
                }
                case NO_PARENT -> {
                    // Do nothing
                }
            }
        }

        // Most specific structure first; each structure has its own predicate.
        if (directionalType != null && directionalEntityClass != null
                && isMultiEntityDirectional(solutionDescriptor, declarativeShadowVariableDescriptors,
                        rootVariableSources, directionalEntityClass)) {
            return new GraphStructureAndDirection(MULTI_ENTITY_SINGLE_DIRECTIONAL_PARENT, parentMetaModel,
                    directionalType);
        }

        if (isArbitrary) {
            return arbitraryGraphStructure;
        }

        if (directionalType == null) {
            return new GraphStructureAndDirection(NO_DYNAMIC_EDGES, null, null);
        } else {
            // Cannot use a single successor function if there are multiple entity classes
            return new GraphStructureAndDirection(SINGLE_DIRECTIONAL_PARENT, parentMetaModel, directionalType);
        }
    }

    /**
     * True if the model has exactly two declarative entity classes,
     * where the chained class only uses its chain or the fixed class's pre-chain declarative variables,
     * and the fixed class only depends on the chained class through the list variable's own elements.
     * The fixed class must own the planning list variable and its entities may only depend on each other
     * through declarative variables reached from fact collections, which form a static DAG.
     */
    private static <Solution_> boolean isMultiEntityDirectional(
            SolutionDescriptor<Solution_> solutionDescriptor,
            List<DeclarativeShadowVariableDescriptor<Solution_>> declarativeShadowVariableDescriptors,
            List<RootVariableSource<?, ?>> rootVariableSources,
            Class<?> chainedEntityClass) {
        var declarativeEntityClassSet = new LinkedHashSet<Class<?>>();
        for (var descriptor : declarativeShadowVariableDescriptors) {
            declarativeEntityClassSet.add(descriptor.getEntityDescriptor().getEntityClass());
        }
        if (declarativeEntityClassSet.size() != 2) {
            return false;
        }
        var hasAlignmentKey = declarativeShadowVariableDescriptors.stream()
                .anyMatch(descriptor -> descriptor.getAlignmentKeyMap() != null);
        if (hasAlignmentKey) {
            return false;
        }
        Class<?> fixedEntityClass = null;
        for (var entityClass : declarativeEntityClassSet) {
            if (!entityClass.equals(chainedEntityClass)) {
                fixedEntityClass = entityClass;
            }
        }
        if (fixedEntityClass == null
                || fixedEntityClass.isAssignableFrom(chainedEntityClass)
                || chainedEntityClass.isAssignableFrom(fixedEntityClass)) {
            // Entities are classified with instanceof, so assignable classes would misroute updaters.
            return false;
        }
        var listVariableDescriptor = solutionDescriptor.getListVariableDescriptor();
        if (listVariableDescriptor == null
                || listVariableDescriptor.getEntityDescriptor().getEntityClass() != fixedEntityClass
                || !chainedEntityClass.isAssignableFrom(listVariableDescriptor.getElementType())) {
            // The graph maps each chained entity to its inverse and walks the fixed entity's list,
            // so the fixed class must own the list variable and the chained class must cover its elements.
            return false;
        }
        var postChainVariableSet = computePostChainVariables(declarativeShadowVariableDescriptors, chainedEntityClass);
        for (var variableSource : rootVariableSources) {
            var isChainedEntitySource = chainedEntityClass.isAssignableFrom(variableSource.rootEntity());
            var parentVariableType = variableSource.parentVariableType();
            if (isChainedEntitySource) {
                switch (parentVariableType) {
                    case PREVIOUS, NEXT, NO_PARENT -> {
                        // Safe: stays within the chain or the entity itself.
                    }
                    case INVERSE -> {
                        // Only safe when it targets a pre-chain declarative variable of the fixed class:
                        // post-chain variables depend on the chain itself,
                        // and a non-declarative variable change does not trigger a chain walk.
                        var references = variableSource.variableSourceReferences();
                        if (references.size() < 2
                                || !references.get(1).isDeclarative()
                                || postChainVariableSet.contains(references.get(1).variableMetaModel())) {
                            return false;
                        }
                    }
                    default -> {
                        return false;
                    }
                }
            } else {
                switch (parentVariableType) {
                    case NO_PARENT -> {
                        // Safe: stays within the entity itself.
                    }
                    case GROUP -> {
                        // Only safe when every reference is a declarative variable of the fixed class:
                        // the group members form the fixed entities' static DAG,
                        // and only declarative changes enqueue DAG successors.
                        for (var reference : variableSource.variableSourceReferences()) {
                            if (!reference.isDeclarative()
                                    || chainedEntityClass.isAssignableFrom(reference.variableMetaModel().entity().type())) {
                                return false;
                            }
                        }
                    }
                    case LIST_ELEMENT -> {
                        // Only safe when it accesses the list's own elements directly:
                        // an entity reached through an element's fact may belong to another fixed entity,
                        // which has no DAG edge towards this one.
                        var reference = variableSource.variableSourceReferences().get(0);
                        if (!reference.chainFromRootEntityToVariableEntity().isEmpty()
                                || !chainedEntityClass.isAssignableFrom(reference.variableMetaModel().entity().type())) {
                            return false;
                        }
                    }
                    default -> {
                        // A source reached through a genuine variable needs dynamic edges,
                        // which the static DAG cannot represent.
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Classifies the fixed class's declarative shadow variables:
     * a variable is post-chain when it depends on the chained class's elements,
     * directly through a list element source or transitively through another post-chain variable.
     * Pre-chain variables can be computed before the entity's chain is walked;
     * post-chain variables must be computed after it.
     */
    static <Solution_> Set<VariableMetaModel<?, ?, ?>> computePostChainVariables(
            List<DeclarativeShadowVariableDescriptor<Solution_>> declarativeShadowVariableDescriptors,
            Class<?> chainedEntityClass) {
        var fixedDescriptorList = declarativeShadowVariableDescriptors.stream()
                .filter(descriptor -> !chainedEntityClass
                        .isAssignableFrom(descriptor.getEntityDescriptor().getEntityClass()))
                .toList();
        var postChainVariableSet = new LinkedHashSet<VariableMetaModel<?, ?, ?>>();
        var changed = true;
        while (changed) {
            changed = false;
            for (var descriptor : fixedDescriptorList) {
                var variableMetaModel = descriptor.getVariableMetaModel();
                if (postChainVariableSet.contains(variableMetaModel)) {
                    continue;
                }
                for (var source : descriptor.getSources()) {
                    // A group source references other instances of the fixed class,
                    // which the DAG's topological order already handles.
                    var isPostChain = source.parentVariableType() == ParentVariableType.LIST_ELEMENT
                            || (source.parentVariableType() != ParentVariableType.GROUP
                                    && source.variableSourceReferences().stream()
                                            .map(VariableSourceReference::variableMetaModel)
                                            .anyMatch(postChainVariableSet::contains));
                    if (isPostChain) {
                        postChainVariableSet.add(variableMetaModel);
                        changed = true;
                        break;
                    }
                }
            }
        }
        return postChainVariableSet;
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
