package ru.ifmo.fbsat.core.automaton

import com.github.lipen.multiarray.MultiArray
import com.github.lipen.multiarray.map
import com.github.lipen.multiarray.mapIndexed
import ru.ifmo.fbsat.core.scenario.CompoundScenario
import ru.ifmo.fbsat.core.scenario.InputAction
import ru.ifmo.fbsat.core.scenario.InputEvent
import ru.ifmo.fbsat.core.scenario.OutputAction
import ru.ifmo.fbsat.core.scenario.OutputEvent
import ru.ifmo.fbsat.core.scenario.OutputValues
import ru.ifmo.fbsat.core.scenario.modularInputActionsSeq
import ru.ifmo.fbsat.core.scenario.positive.OldPositiveScenarioTree
import ru.ifmo.fbsat.core.scenario.positive.PositiveCompoundScenario
import ru.ifmo.fbsat.core.scenario.positive.PositiveCompoundScenarioTree
import ru.ifmo.fbsat.core.scenario.positive.PositiveScenarioTree
import ru.ifmo.fbsat.core.utils.Compound
import ru.ifmo.fbsat.core.utils.Globals
import ru.ifmo.fbsat.core.utils.ImmutableMultiArray
import ru.ifmo.fbsat.core.utils.M
import ru.ifmo.fbsat.core.utils.log
import ru.ifmo.fbsat.core.utils.mutableListOfNulls
import ru.ifmo.fbsat.core.utils.toImmutable
import ru.ifmo.fbsat.core.utils.toMultiArray

private typealias OldModularScenarioTree = MultiArray<OldPositiveScenarioTree>
private typealias ModularScenarioTree = MultiArray<PositiveScenarioTree>
private typealias ModularAutomaton = MultiArray<Automaton>
private typealias ModularState = MultiArray<Automaton.State>
private typealias ModularEvalState = MultiArray<Automaton.EvalState>
private typealias ModularEvalResult = MultiArray<Automaton.EvalResult>
private typealias ModularEvalResult_adhoc = ImmutableMultiArray<Automaton.EvalResult>
private typealias ModularInputAction = MultiArray<InputAction>
private typealias ModularOutputAction = MultiArray<OutputAction>
private typealias ModularOutputValues = MultiArray<OutputValues>

@Suppress("MemberVisibilityCanBePrivate", "FunctionName", "PropertyName")
class DistributedAutomaton(
    override val modular: ImmutableMultiArray<Automaton>
) : Compound<Automaton> {
    val modules: ModularAutomaton = modular.toMultiArray()
    val numberOfModules: Int = M
    val modularInputEvents: MultiArray<List<InputEvent>> = modules.map { it.inputEvents }
    val modularOutputEvents: MultiArray<List<OutputEvent>> = modules.map { it.outputEvents }
    val modularInputNames: MultiArray<List<String>> = modules.map { it.inputNames }
    val modularOutputNames: MultiArray<List<String>> = modules.map { it.outputNames }

    val numberOfStates: Int = modules.values.sumBy { it.numberOfStates }
    val numberOfTransitions: Int = modules.values.sumBy { it.numberOfTransitions }
    val totalGuardsSize: Int = modules.values.sumBy { it.totalGuardsSize }

    // TODO: remove when ImmutableMultiArray is stabilized
    constructor(modules: ModularAutomaton) : this(modules.toImmutable())

    class CompoundEvalState private constructor(
        val modularState: ModularState,
        val modularOutputValues: ModularOutputValues,
        override val modular: ImmutableMultiArray<Automaton.EvalState>
    ) : Compound<Automaton.EvalState> {
        constructor(modularEvalState: ModularEvalState) : this(
            modularState = modularEvalState.map { it.state },
            modularOutputValues = modularEvalState.map { it.outputValues },
            modular = modularEvalState.toImmutable()
        )

        constructor(
            M: Int,
            modularState: ModularState,
            modularOutputValues: ModularOutputValues
        ) : this(
            modularState = modularState,
            modularOutputValues = modularOutputValues,
            modular = MultiArray.create(M) { (m) ->
                Automaton.EvalState(modularState[m], modularOutputValues[m])
            }.toImmutable()
        )

        fun eval(modularInputAction: ModularInputAction): CompoundEvalResult =
            CompoundEvalResult(
                modular.toMultiArray().mapIndexed { (m), evalState ->
                    evalState.eval(modularInputAction[m])
                }
            )

        fun eval(modularInputActions: Sequence<ModularInputAction>): Sequence<CompoundEvalResult> {
            var currentEvalState = this
            return modularInputActions.map { modularInputAction ->
                currentEvalState.eval(modularInputAction).also {
                    currentEvalState = it.newEvalState
                }
            }
        }
    }

    class CompoundEvalResult private constructor(
        val modularDestination: ModularState,
        val modularOutputAction: ModularOutputAction,
        override val modular: ModularEvalResult_adhoc
    ) : Compound<Automaton.EvalResult> {
        val modularOutputValues: ModularOutputValues = modularOutputAction.map { it.values }
        val newEvalState: CompoundEvalState =
            CompoundEvalState(M, modularDestination, modularOutputValues)

        constructor(modularResult: ModularEvalResult) : this(
            modularDestination = modularResult.map { it.destination },
            modularOutputAction = modularResult.map { it.outputAction },
            modular = modularResult.toImmutable()
        )

        constructor(
            M: Int,
            modularDestination: ModularState,
            modularOutputAction: ModularOutputAction
        ) : this(
            modularDestination = modularDestination,
            modularOutputAction = modularOutputAction,
            modular = MultiArray.create(M) { (m) ->
                Automaton.EvalResult(modularDestination[m], modularOutputAction[m])
            }.toImmutable()
        )

        override fun toString(): String {
            return "CompoundEvalResult(destination = ${modularDestination.values}, outputAction = ${modularOutputAction.values})"
        }
    }

    @Deprecated(
        "Use evalState.eval directly",
        ReplaceWith("compoundEvalState.eval(modularInputAction)"),
        level = DeprecationLevel.ERROR
    )
    fun eval(
        modularInputAction: ModularInputAction,
        evalState: CompoundEvalState
    ): CompoundEvalResult =
        evalState.eval(modularInputAction)

    @Deprecated(
        "Use evalState.eval directly",
        ReplaceWith("startEvalState.eval(modularInputActions)"),
        level = DeprecationLevel.ERROR
    )
    fun eval(
        modularInputActions: Sequence<ModularInputAction>,
        startEvalState: CompoundEvalState
    ): Sequence<CompoundEvalResult> =
        startEvalState.eval(modularInputActions)

    fun eval(
        modularInputActions: Sequence<ModularInputAction>,
        modularStartState: ModularState = modules.map { it.initialState },
        modularStartOutputValues: ModularOutputValues = modules.map { Globals.INITIAL_OUTPUT_VALUES }
    ): Sequence<CompoundEvalResult> =
        CompoundEvalState(M, modularStartState, modularStartOutputValues).eval(modularInputActions)

    fun eval(
        modularInputActions: Iterable<ModularInputAction>,
        modularStartState: ModularState = modules.map { it.initialState },
        modularStartOutputValues: ModularOutputValues = modules.map { Globals.INITIAL_OUTPUT_VALUES }
    ): List<CompoundEvalResult> =
        eval(modularInputActions.asSequence(), modularStartState, modularStartOutputValues).toList()

    /**
     * Evaluate the given [scenario].
     */
    fun eval(scenario: CompoundScenario): Sequence<CompoundEvalResult> =
        eval(scenario.modularInputActionsSeq)

    fun map_(scenario: CompoundScenario): List<CompoundEvalResult?> {
        val mapping: MutableList<CompoundEvalResult?> = mutableListOfNulls(scenario.elements.size)
        out@ for ((i, result) in eval(scenario).withIndex()) {
            val element = scenario.elements[i]
            for (m in 1..M) {
                if (element.modular.toMultiArray()[m].outputAction != result.modular.toMultiArray()[m].outputAction) {
                    log.error("No mapping for m = $m, element = $element, result = $result")
                    break@out
                }
            }
            mapping[i] = result
        }
        return mapping
    }

    /**
     * Map the given [scenario].
     * Nulls represent the absence of a mapping.
     */
    fun map(scenario: CompoundScenario): List<ModularState?> =
        map_(scenario).map { result ->
            result?.modularDestination
        }

    /**
     * Verify the given [positiveCompoundScenario].
     *
     * @return `true` if [positiveCompoundScenario] is satisfied.
     */
    fun verify(positiveCompoundScenario: PositiveCompoundScenario): Boolean {
        val mapping: List<ModularState?> = map(positiveCompoundScenario)
        return mapping.last() != null
    }

    /**
     * Verify all positive scenarios in the given [scenarioTree].
     *
     * @return `true` if **all** scenarios are satisfied.
     */
    fun verify(scenarioTree: PositiveCompoundScenarioTree): Boolean =
        scenarioTree.scenarios.all(::verify)

    /**
     * Verify all positive scenarios in the given [modularScenarioTree].
     *
     * @return `true` if **all** scenarios are satisfied.
     */
    fun verify(modularScenarioTree: ModularScenarioTree): Boolean {
        for (m in 1..M) {
            if (!modules[m].verify(modularScenarioTree[m])) {
                log.error("Scenario tree verification failed for m = $m")
                return false
            }
        }
        return true
    }
}
