package ru.ifmo.fbsat.core.utils

import org.redundent.kotlin.xml.PrintOptions
import ru.ifmo.fbsat.core.automaton.OutputValues
import ru.ifmo.fbsat.core.solver.VarEncoding

enum class SolverBackend {
    DEFAULT, INCREMENTAL, FILE, MINISAT, CADICAL;
}

enum class EpsilonOutputEvents {
    START, ONLYSTART, NONE;
}

enum class StartStateAlgorithms {
    NOTHING, ZERO, ZERONOTHING, ANY, INIT, INITNOTHING;
}

object Globals {
    var INITIAL_OUTPUT_VALUES: OutputValues? = null
    var EPSILON_OUTPUT_EVENTS: EpsilonOutputEvents = EpsilonOutputEvents.ONLYSTART
    var START_STATE_ALGORITHMS: StartStateAlgorithms = StartStateAlgorithms.ZERO
    var IS_FORBID_OR: Boolean = false
    var IS_FORBID_TRANSITIONS_TO_FIRST_STATE: Boolean = true
    var IS_BFS_AUTOMATON: Boolean = true
    var IS_BFS_GUARD: Boolean = true
    var IS_ENCODE_TRANSITIONS_ORDER: Boolean = true
    var IS_ENCODE_TERMINALS_ORDER: Boolean = false
    var IS_ENCODE_TERMINALS_MINI_ORDER: Boolean = false
    var IS_ENCODE_HARD_TO_EXPLAIN: Boolean = true
    var IS_ENCODE_TOTALIZER: Boolean = true
    var IS_ENCODE_DISJUNCTIVE_TRANSITIONS: Boolean = false
    var IS_DEBUG: Boolean = false
    var IS_REUSE_K: Boolean = true
    val xmlPrintOptions: PrintOptions =
        PrintOptions(pretty = true, singleLineTextElements = true, useSelfClosingTags = true)
    val defaultVarEncoding: VarEncoding = VarEncoding.ONEHOT
}
