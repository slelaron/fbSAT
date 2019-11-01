package ru.ifmo.fbsat.core.task.single.extended

import ru.ifmo.fbsat.core.automaton.Automaton
import ru.ifmo.fbsat.core.automaton.NodeType
import ru.ifmo.fbsat.core.constraints.declarePositiveGuardConditionsConstraints
import ru.ifmo.fbsat.core.scenario.positive.ScenarioTree
import ru.ifmo.fbsat.core.solver.Solver
import ru.ifmo.fbsat.core.solver.Solver.Companion.falseVariable
import ru.ifmo.fbsat.core.solver.declareComparatorLessThanOrEqual
import ru.ifmo.fbsat.core.solver.declareTotalizer
import ru.ifmo.fbsat.core.solver.implyAnd
import ru.ifmo.fbsat.core.solver.implyImply
import ru.ifmo.fbsat.core.task.single.basic.BasicTask
import ru.ifmo.fbsat.core.utils.Globals
import java.io.File

@Suppress("MemberVisibilityCanBePrivate", "LocalVariableName", "UnnecessaryVariable")
class ExtendedTask(
    scenarioTree: ScenarioTree,
    numberOfStates: Int, // C
    maxOutgoingTransitions: Int? = null, // K, =C if null
    maxGuardSize: Int, // P
    maxTotalGuardsSize: Int? = null, // N, unconstrained if null
    val outDir: File,
    val solver: Solver,
    val autoFinalize: Boolean = true,
    isEncodeReverseImplication: Boolean = true
) {
    private val basicTask: BasicTask =
        BasicTask(
            scenarioTree = scenarioTree,
            numberOfStates = numberOfStates,
            maxOutgoingTransitions = maxOutgoingTransitions,
            maxTransitions = null,
            outDir = outDir,
            solver = solver,
            autoFinalize = false,
            isEncodeReverseImplication = isEncodeReverseImplication
        )

    val vars: ExtendedVariables

    init {
        with(solver) {
            with(basicTask.vars) {
                /* Constants */
                val P = maxGuardSize

                /* Guard conditions variables */
                val nodeType = newArray(C, K, P, NodeType.values().size, one = true)
                val nodeInputVariable = newArray(C, K, P, X + 1, one = true)
                val nodeParent = newArray(C, K, P, P + 1, one = true) { (c, k, p, par) ->
                    if (par < p || par == P + 1) newVariable()
                    else falseVariable
                }
                val nodeChild = newArray(C, K, P, P + 1, one = true) { (c, k, p, ch) ->
                    if (ch > p || ch == P + 1) newVariable()
                    else falseVariable
                }
                val nodeValue = newArray(C, K, P, U) { (c, k, p, u) ->
                    if (p == 1) transitionFiring[c, k, u]
                    else newVariable()
                }

                vars = ExtendedVariables(
                    basicVars = basicTask.vars,
                    P = P,
                    nodeType = nodeType,
                    nodeInputVariable = nodeInputVariable,
                    nodeParent = nodeParent,
                    nodeChild = nodeChild,
                    nodeValue = nodeValue
                )
            }

            /* Constraints */
            declarePositiveGuardConditionsConstraints(vars)
            declareAdhocConstraints()
        }

        updateCardinality(maxTotalGuardsSize)
    }

    private fun Solver.declareAdhocConstraints() {
        comment("A. AD-HOCs")

        with(vars) {
            comment("A.1. Forbid double negation")
            // (nodeType[p] = NOT) & (nodeChild[p] = ch) => (nodeType[ch] != NOT)
            for (c in 1..C)
                for (k in 1..K)
                    for (p in 1 until P)
                        for (ch in (p + 1)..P)
                            implyImply(
                                nodeType[c, k, p, NodeType.NOT.value],
                                nodeChild[c, k, p, ch],
                                -nodeType[c, k, ch, NodeType.NOT.value]
                            )

            comment("A.2. Distinct transitions")
            // TODO: Distinct transitions

            if (Globals.IS_FORBID_OR) {
                comment("A.3. Forbid ORs")
                for (c in 1..C)
                    for (k in 1..K)
                        for (p in 1..P)
                            clause(-nodeType[c, k, p, NodeType.OR.value])
            }

            if (Globals.IS_ENCODE_TERMINALS_ORDER) {
                comment("A.4. Terminals order")
                // terminal[p, x] => AND_{p'<p, x'>=x}( ~terminal[r_, x_] )
                for (c in 1..C)
                    for (k in 1..K)
                        for (p in 1..P)
                            for (x in 1..X)
                                implyAnd(nodeInputVariable[c, k, p, x], sequence {
                                    for (p_ in 1 until p)
                                        for (x_ in x..X)
                                            yield(-nodeInputVariable[c, k, p_, x_])
                                })
            }
        }
    }

    fun updateCardinality(newMaxTotalGuardsSize: Int?) {
        with(solver) {
            with(vars) {
                maxTotalGuardsSize?.let { N ->
                    check(newMaxTotalGuardsSize != null && newMaxTotalGuardsSize <= N) { "Cannot soften UB" }
                }

                if (newMaxTotalGuardsSize == null && !Globals.IS_ENCODE_TOTALIZER) return
                if (totalizer == null) {
                    totalizer = declareTotalizer {
                        for (c in 1..C)
                            for (k in 1..K)
                                for (p in 1..P)
                                    yield(-nodeType[c, k, p, NodeType.NONE.value])
                    }
                }
                if (newMaxTotalGuardsSize == null) return

                declareComparatorLessThanOrEqual(totalizer!!, newMaxTotalGuardsSize, maxTotalGuardsSize)
                maxTotalGuardsSize = newMaxTotalGuardsSize
            }
        }
    }

    fun infer(): Automaton? {
        val rawAssignment = solver.solve()?.data
        if (autoFinalize) finalize2()
        return rawAssignment?.let { raw ->
            ExtendedAssignment.fromRaw(raw, vars).toAutomaton()
        }
    }

    fun finalize2() {
        basicTask.finalize2()
        // Note: basicTask already finalizes the solver, so it is not necessary to do it here again
        // solver.finalize2()
    }
}
