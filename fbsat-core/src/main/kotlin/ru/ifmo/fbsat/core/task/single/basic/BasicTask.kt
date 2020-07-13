package ru.ifmo.fbsat.core.task.single.basic

import ru.ifmo.fbsat.core.constraints.declareAutomatonBfsConstraints
import ru.ifmo.fbsat.core.constraints.declareAutomatonStructureConstraints
import ru.ifmo.fbsat.core.constraints.declarePositiveMappingConstraints
import ru.ifmo.fbsat.core.scenario.positive.OldPositiveScenarioTree
import ru.ifmo.fbsat.core.solver.Solver
import ru.ifmo.fbsat.core.task.Task
import ru.ifmo.fbsat.core.task.basicVars
import ru.ifmo.fbsat.core.utils.Globals

data class BasicTask(
    val scenarioTree: OldPositiveScenarioTree,
    val numberOfStates: Int, // C
    val maxOutgoingTransitions: Int? = null, // K, =C if null
    val maxTransitions: Int? = null, // T, unconstrained if null
    val isEncodeReverseImplication: Boolean = true
) : Task() {
    override fun Solver.declare_() {
        /* Variables */
        val vars = declareBasicVariables(
            scenarioTree = scenarioTree,
            C = numberOfStates,
            K = maxOutgoingTransitions ?: numberOfStates
        ).also {
            context.basicVars = it
        }

        /* Constraints */
        declareAutomatonStructureConstraints(vars)
        if (Globals.IS_BFS_AUTOMATON) declareAutomatonBfsConstraints(vars)
        declarePositiveMappingConstraints(vars, isEncodeReverseImplication = isEncodeReverseImplication)

        /* Initial cardinality constraints */
        vars.cardinality.updateUpperBoundLessThanOrEqual(maxTransitions)
    }
}
