package ai.timefold.solver.core.impl.domain.variable.declarative;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
     * A {@link #MULTI_ENTITY_SINGLE_DIRECTIONAL_PARENT} structure whose chained entities
     * may additionally depend on declarative variables of other chained entities
     * through facts (precedences, e.g. a visit that must start after another visit ends).
     * The precedence edges are static, but combined with the dynamic chains
     * they can form dependency cycles, so the graph detects cycles
     * on a condensed graph of the precedence-linked entities
     * and marks the affected entities inconsistent, like the arbitrary graph does.
     * The detection only accepts models whose static variable dependencies
     * make that entity-level cycle detection exact.
     */
    MULTI_ENTITY_SINGLE_DIRECTIONAL_PARENT_WITH_PRECEDENCE,

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
        if (directionalType != null && directionalEntityClass != null) {
            var multiEntityStructure = determineMultiEntityStructure(solutionDescriptor,
                    declarativeShadowVariableDescriptors, rootVariableSources, directionalEntityClass);
            if (multiEntityStructure != null) {
                return new GraphStructureAndDirection(multiEntityStructure, parentMetaModel, directionalType);
            }
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
     * Returns the multi-entity structure the model fits, or null when it fits none.
     * The model must have exactly two declarative entity classes,
     * where the chained class only uses its chain, the fixed class's pre-chain declarative variables
     * or fact paths to declarative variables of other chained entities (precedences),
     * and the fixed class only depends on the chained class through the list variable's own elements.
     * The fixed class must own the planning list variable and its entities may only depend on each other
     * through declarative variables reached from fact collections, which form a static DAG.
     * When precedences are present, the static variable dependencies must additionally
     * make entity-level cycle detection exact (see {@link #isPrecedenceCycleDetectionExact}),
     * and the result is {@link #MULTI_ENTITY_SINGLE_DIRECTIONAL_PARENT_WITH_PRECEDENCE}.
     */
    private static <Solution_> @Nullable GraphStructure determineMultiEntityStructure(
            SolutionDescriptor<Solution_> solutionDescriptor,
            List<DeclarativeShadowVariableDescriptor<Solution_>> declarativeShadowVariableDescriptors,
            List<RootVariableSource<?, ?>> rootVariableSources,
            Class<?> chainedEntityClass) {
        var declarativeEntityClassSet = new LinkedHashSet<Class<?>>();
        for (var descriptor : declarativeShadowVariableDescriptors) {
            declarativeEntityClassSet.add(descriptor.getEntityDescriptor().getEntityClass());
        }
        if (declarativeEntityClassSet.size() != 2) {
            return null;
        }
        var hasAlignmentKey = declarativeShadowVariableDescriptors.stream()
                .anyMatch(descriptor -> descriptor.getAlignmentKeyMap() != null);
        if (hasAlignmentKey) {
            return null;
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
            return null;
        }
        var listVariableDescriptor = solutionDescriptor.getListVariableDescriptor();
        if (listVariableDescriptor == null
                || listVariableDescriptor.getEntityDescriptor().getEntityClass() != fixedEntityClass
                || !chainedEntityClass.isAssignableFrom(listVariableDescriptor.getElementType())) {
            // The graph maps each chained entity to its inverse and walks the fixed entity's list,
            // so the fixed class must own the list variable and the chained class must cover its elements.
            return null;
        }
        var postChainVariableSet = computePostChainVariables(declarativeShadowVariableDescriptors, chainedEntityClass);
        var hasPrecedenceSource = false;
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
                            return null;
                        }
                    }
                    case INDIRECT, GROUP -> {
                        // Only safe when it is a precedence: a fact path to a declarative variable
                        // of another chained entity, whose edges are static.
                        for (var reference : variableSource.variableSourceReferences()) {
                            if (!reference.isDeclarative()
                                    || !chainedEntityClass.isAssignableFrom(reference.variableMetaModel().entity().type())) {
                                return null;
                            }
                        }
                        hasPrecedenceSource = true;
                    }
                    default -> {
                        return null;
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
                                return null;
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
                            return null;
                        }
                    }
                    default -> {
                        // A source reached through a genuine variable needs dynamic edges,
                        // which the static DAG cannot represent.
                        return null;
                    }
                }
            }
        }
        if (!hasPrecedenceSource) {
            return MULTI_ENTITY_SINGLE_DIRECTIONAL_PARENT;
        }
        if (!isPrecedenceCycleDetectionExact(declarativeShadowVariableDescriptors, chainedEntityClass)) {
            return null;
        }
        return MULTI_ENTITY_SINGLE_DIRECTIONAL_PARENT_WITH_PRECEDENCE;
    }

    /**
     * With precedences, a cycle can pass through an entity by entering at one variable
     * and leaving at another, e.g. entering a visit's startTime from a predecessor's endTime
     * and leaving through its own endTime towards the next visit in the chain.
     * The precedence graph tracks cycles per entity (and per pre-chain/post-chain tier
     * for the fixed class), which matches the arbitrary graph's per-variable tracking
     * only if within an entity, every variable a cycle can enter at
     * reaches every variable a cycle can leave from.
     * This checks that statically; models failing it fall back to the arbitrary graph.
     */
    static <Solution_> boolean isPrecedenceCycleDetectionExact(
            List<DeclarativeShadowVariableDescriptor<Solution_>> declarativeShadowVariableDescriptors,
            Class<?> chainedEntityClass) {
        var chainedReachability =
                computeWithinEntityReachability(declarativeShadowVariableDescriptors, chainedEntityClass, true);
        var fixedReachability =
                computeWithinEntityReachability(declarativeShadowVariableDescriptors, chainedEntityClass, false);
        var inOutSets = computePrecedenceInOutSets(declarativeShadowVariableDescriptors, chainedEntityClass);
        return allPairsReach(inOutSets.chainedInSet(), inOutSets.chainedOutSet(), chainedReachability)
                && allPairsReach(inOutSets.preInSet(), inOutSets.preOutSet(), fixedReachability)
                && allPairsReach(inOutSets.postInSet(), inOutSets.postOutSet(), fixedReachability)
                && isReachabilityUniform(inOutSets.preInSet(), inOutSets.postOutSet(), fixedReachability);
    }

    /**
     * The results of the static variable analysis needed to build the precedence graph:
     * the variables a cycle can affect (whose values are nulled on inconsistent entities)
     * and whether a fixed entity's post-chain variables depend on its pre-chain variables
     * without going through the elements.
     */
    record PrecedenceVariableAnalysis(Set<VariableMetaModel<?, ?, ?>> chainedSusceptibleVariableSet,
            Set<VariableMetaModel<?, ?, ?>> preChainSusceptibleVariableSet,
            boolean staticPreToPostDependency) {
    }

    static <Solution_> PrecedenceVariableAnalysis analyzePrecedenceVariables(
            List<DeclarativeShadowVariableDescriptor<Solution_>> declarativeShadowVariableDescriptors,
            Class<?> chainedEntityClass) {
        var chainedReachability =
                computeWithinEntityReachability(declarativeShadowVariableDescriptors, chainedEntityClass, true);
        var fixedReachability =
                computeWithinEntityReachability(declarativeShadowVariableDescriptors, chainedEntityClass, false);
        var inOutSets = computePrecedenceInOutSets(declarativeShadowVariableDescriptors, chainedEntityClass);
        var chainedSusceptibleVariableSet = new LinkedHashSet<VariableMetaModel<?, ?, ?>>();
        for (var in : inOutSets.chainedInSet()) {
            chainedSusceptibleVariableSet.addAll(chainedReachability.get(in));
        }
        var preChainSusceptibleVariableSet = new LinkedHashSet<VariableMetaModel<?, ?, ?>>();
        for (var in : inOutSets.preInSet()) {
            preChainSusceptibleVariableSet.addAll(fixedReachability.get(in));
        }
        var staticPreToPostDependency = false;
        for (var in : inOutSets.preInSet()) {
            for (var out : inOutSets.postOutSet()) {
                staticPreToPostDependency |= fixedReachability.get(in).contains(out);
            }
        }
        return new PrecedenceVariableAnalysis(chainedSusceptibleVariableSet, preChainSusceptibleVariableSet,
                staticPreToPostDependency);
    }

    /**
     * The variables a cycle can enter an entity at (in) and leave it from (out),
     * per entity class and, for the fixed class, per pre-chain/post-chain tier.
     */
    private record PrecedenceInOutSets(Set<VariableMetaModel<?, ?, ?>> chainedInSet,
            Set<VariableMetaModel<?, ?, ?>> chainedOutSet,
            Set<VariableMetaModel<?, ?, ?>> preInSet,
            Set<VariableMetaModel<?, ?, ?>> preOutSet,
            Set<VariableMetaModel<?, ?, ?>> postInSet,
            Set<VariableMetaModel<?, ?, ?>> postOutSet) {
    }

    private static <Solution_> PrecedenceInOutSets computePrecedenceInOutSets(
            List<DeclarativeShadowVariableDescriptor<Solution_>> declarativeShadowVariableDescriptors,
            Class<?> chainedEntityClass) {
        var postChainVariableSet = computePostChainVariables(declarativeShadowVariableDescriptors, chainedEntityClass);
        var inOutSets = new PrecedenceInOutSets(new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>(),
                new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
        for (var descriptor : declarativeShadowVariableDescriptors) {
            var isChained = chainedEntityClass.isAssignableFrom(descriptor.getEntityDescriptor().getEntityClass());
            var targetVariable = descriptor.getVariableMetaModel();
            for (var source : descriptor.getSources()) {
                var declarativeReferences = source.variableSourceReferences().stream()
                        .filter(VariableSourceReference::isDeclarative)
                        .map(VariableSourceReference::variableMetaModel)
                        .toList();
                if (isChained) {
                    switch (source.parentVariableType()) {
                        case PREVIOUS, NEXT, INVERSE, INDIRECT, GROUP -> {
                            // Only declarative references participate in cycles;
                            // e.g. a bare previous reference is not computed by the graph.
                            if (!declarativeReferences.isEmpty()) {
                                inOutSets.chainedInSet().add(targetVariable);
                                for (var reference : declarativeReferences) {
                                    if (chainedEntityClass.isAssignableFrom(reference.entity().type())) {
                                        inOutSets.chainedOutSet().add(reference);
                                    } else if (source.parentVariableType() == ParentVariableType.INVERSE) {
                                        // A pre-chain variable read through an inverse
                                        // is an out point of the fixed class's pre-chain tier.
                                        inOutSets.preOutSet().add(reference);
                                    }
                                }
                            }
                        }
                        default -> {
                            // NO_PARENT sources stay within the entity.
                        }
                    }
                } else {
                    switch (source.parentVariableType()) {
                        case GROUP -> {
                            var isPostTarget = postChainVariableSet.contains(targetVariable);
                            (isPostTarget ? inOutSets.postInSet() : inOutSets.preInSet()).add(targetVariable);
                            for (var reference : declarativeReferences) {
                                (postChainVariableSet.contains(reference) ? inOutSets.postOutSet()
                                        : inOutSets.preOutSet()).add(reference);
                            }
                        }
                        case LIST_ELEMENT -> {
                            inOutSets.postInSet().add(targetVariable);
                            // The referenced element variable is an out point of the chained class.
                            inOutSets.chainedOutSet().add(source.variableSourceReferences().get(0).variableMetaModel());
                        }
                        default -> {
                            // NO_PARENT sources stay within the entity.
                        }
                    }
                }
            }
        }
        return inOutSets;
    }

    /**
     * Computes for each declarative variable of the chained (or fixed) class
     * the set of declarative variables of the same class that transitively depend on it,
     * following only same-instance references (sources without a parent variable).
     * A variable is included in its own set.
     */
    static <Solution_> Map<VariableMetaModel<?, ?, ?>, Set<VariableMetaModel<?, ?, ?>>> computeWithinEntityReachability(
            List<DeclarativeShadowVariableDescriptor<Solution_>> declarativeShadowVariableDescriptors,
            Class<?> chainedEntityClass, boolean forChainedClass) {
        var edgeMap = new LinkedHashMap<VariableMetaModel<?, ?, ?>, Set<VariableMetaModel<?, ?, ?>>>();
        var variableList = new ArrayList<VariableMetaModel<?, ?, ?>>();
        for (var descriptor : declarativeShadowVariableDescriptors) {
            var isChained = chainedEntityClass.isAssignableFrom(descriptor.getEntityDescriptor().getEntityClass());
            if (isChained != forChainedClass) {
                continue;
            }
            variableList.add(descriptor.getVariableMetaModel());
            for (var source : descriptor.getSources()) {
                if (source.parentVariableType() != ParentVariableType.NO_PARENT) {
                    continue;
                }
                for (var reference : source.variableSourceReferences()) {
                    if (reference.isDeclarative() && reference.isTopLevel()) {
                        edgeMap.computeIfAbsent(reference.variableMetaModel(), ignored -> new LinkedHashSet<>())
                                .add(descriptor.getVariableMetaModel());
                    }
                }
            }
        }
        var reachabilityMap = new LinkedHashMap<VariableMetaModel<?, ?, ?>, Set<VariableMetaModel<?, ?, ?>>>();
        for (var variable : variableList) {
            var reachableSet = new LinkedHashSet<VariableMetaModel<?, ?, ?>>();
            var queue = new ArrayDeque<VariableMetaModel<?, ?, ?>>();
            queue.add(variable);
            while (!queue.isEmpty()) {
                var current = queue.poll();
                if (!reachableSet.add(current)) {
                    continue;
                }
                var successors = edgeMap.get(current);
                if (successors != null) {
                    queue.addAll(successors);
                }
            }
            reachabilityMap.put(variable, reachableSet);
        }
        return reachabilityMap;
    }

    private static boolean allPairsReach(Set<VariableMetaModel<?, ?, ?>> inSet, Set<VariableMetaModel<?, ?, ?>> outSet,
            Map<VariableMetaModel<?, ?, ?>, Set<VariableMetaModel<?, ?, ?>>> reachabilityMap) {
        for (var in : inSet) {
            var reachableSet = reachabilityMap.get(in);
            for (var out : outSet) {
                if (reachableSet == null || !reachableSet.contains(out)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isReachabilityUniform(Set<VariableMetaModel<?, ?, ?>> inSet,
            Set<VariableMetaModel<?, ?, ?>> outSet,
            Map<VariableMetaModel<?, ?, ?>, Set<VariableMetaModel<?, ?, ?>>> reachabilityMap) {
        // The pre-chain to post-chain dependency within a fixed entity is modeled
        // as a single condensed edge, so it must either hold for every in/out pair or for none.
        var anyReach = false;
        var anyMiss = false;
        for (var in : inSet) {
            var reachableSet = reachabilityMap.get(in);
            for (var out : outSet) {
                if (reachableSet != null && reachableSet.contains(out)) {
                    anyReach = true;
                } else {
                    anyMiss = true;
                }
            }
        }
        return !(anyReach && anyMiss);
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
