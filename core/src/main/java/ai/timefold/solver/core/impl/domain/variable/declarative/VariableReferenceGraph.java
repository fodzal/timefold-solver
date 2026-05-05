package ai.timefold.solver.core.impl.domain.variable.declarative;

import ai.timefold.solver.core.preview.api.domain.metamodel.VariableMetaModel;

public sealed interface VariableReferenceGraph
        permits AbstractVariableReferenceGraph, EmptyVariableReferenceGraph,
        SingleDirectionalParentVariableReferenceGraph, MultiEntityChainedVariableReferenceGraph {

    void updateChanged();

    void beforeVariableChanged(VariableMetaModel<?, ?, ?> variableReference, Object entity);

    void afterVariableChanged(VariableMetaModel<?, ?, ?> variableReference, Object entity);

}
