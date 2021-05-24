package ru.ifmo.fbsat.core.task.single.complete

import com.github.lipen.satlib.card.Cardinality
import com.github.lipen.satlib.core.IntVarArray
import com.github.lipen.satlib.core.Lit
import com.github.lipen.satlib.op.imply
import ru.ifmo.fbsat.core.automaton.Automaton
import ru.ifmo.fbsat.core.scenario.negative.NegativeScenarioTree
import ru.ifmo.fbsat.core.scenario.positive.PositiveScenarioTree
import ru.ifmo.fbsat.core.task.Inferrer
import ru.ifmo.fbsat.core.task.optimizeN
import ru.ifmo.fbsat.core.task.single.basic.BasicTask
import ru.ifmo.fbsat.core.task.single.extended.ExtendedTask
import ru.ifmo.fbsat.core.task.single.extended.extendedMin
import ru.ifmo.fbsat.core.task.single.extended.extendedMinUB
import ru.ifmo.fbsat.core.task.single.extended.inferExtended
import ru.ifmo.fbsat.core.utils.Globals
import ru.ifmo.fbsat.core.utils.MyLogger
import java.io.File

private val logger = MyLogger {}

fun Inferrer.cegisAssumptions(
    scenarioTree: PositiveScenarioTree,
    negativeScenarioTree: NegativeScenarioTree? = null, // empty if null
    numberOfStates: Int, // C
    maxOutgoingTransitions: Int? = null, // K, =C if null
    maxGuardSize: Int, // P
    maxTransitions: Int? = null, // T, unconstrained if null
    maxTotalGuardsSize: Int? = null, // N, unconstrained if null
    smvDir: File,
    loopNumber: Int, // = 0
): Automaton? {
    reset()
    declare(
        BasicTask(
            scenarioTree = scenarioTree,
            numberOfStates = numberOfStates,
            maxOutgoingTransitions = maxOutgoingTransitions,
            maxTransitions = maxTransitions,
            isEncodeReverseImplication = false
        )
    )
    solver.context["canMapTreeToZero"] = true
    solver.context["needRegenerateNullVertex"] = true
    solver.context["doNotUseThisVertices"] = mutableSetOf<NegativeScenarioTree.Node>()
    declare(ExtendedTask(maxGuardSize = maxGuardSize, maxTotalGuardsSize = maxTotalGuardsSize))
    declare(CompleteTask(negativeScenarioTree))
    return performCegis(smvDir, loopNumber)
}

fun Inferrer.cegisAssumptionsStep(
    maxTotalGuardsSize: Int,
    smvDir: File,
    loopNumber: Int,// = 0,
): Automaton? {
    solver.apply {
        // clearAssumptions()
        val cardinalityN: Cardinality = context["cardinalityN"]
        cardinalityN.assumeUpperBoundLessThanOrEqual(maxTotalGuardsSize)
    }
    return performCegis(smvDir, loopNumber)
}

@Suppress("LocalVariableName")
fun Inferrer.cegisMinAssumptions(
    scenarioTree: PositiveScenarioTree,
    initialNegativeScenarioTree: NegativeScenarioTree? = null,
    numberOfStates: Int? = null,
    startNumberOfStates: Int? = null,
    maxGuardSize: Int? = null, // P, search if null
    maxPlateauWidth: Int? = null, // w, =Inf if null
    smvDir: File,
): Automaton? {
    check(Globals.IS_USE_ASSUMPTIONS) { "Currently, cegis-min-assumptions only works with assumptions being turned on globally. Pass --use-assumptions flag to enable them." }

    val extendedAutomaton = if (maxGuardSize == null) {
        extendedMinUB(scenarioTree, numberOfStates = startNumberOfStates, maxPlateauWidth = maxPlateauWidth)
    } else {
        extendedMin(scenarioTree, numberOfStates = numberOfStates, maxGuardSize = maxGuardSize)
    }
    checkNotNull(extendedAutomaton)
    val C = extendedAutomaton.numberOfStates
    // Note: reusing K from extMinTask may fail!
    val K = if (Globals.IS_REUSE_K) extendedAutomaton.maxOutgoingTransitions else C
    logger.info("Using K = $K")
    val P = extendedAutomaton.maxGuardSize
    var N = extendedAutomaton.totalGuardsSize

    logger.info("extendedAutomaton:")
    extendedAutomaton.pprint()
    logger.info("extendedAutomaton has C = $C, P = $P, N = $N")

    val negativeScenarioTree = initialNegativeScenarioTree ?: NegativeScenarioTree(scenarioTree, true)

    for (loopNumber in 1..100) {
        logger.just("===== Loop number #$loopNumber, N = $N =====")

        val automaton = if (loopNumber == 1) {
            cegisAssumptions(
                scenarioTree = scenarioTree,
                negativeScenarioTree = negativeScenarioTree,
                numberOfStates = C,
                maxOutgoingTransitions = K,
                maxGuardSize = P,
                maxTotalGuardsSize = N,
                smvDir = smvDir,
                loopNumber = loopNumber
            )
        } else {
            cegisAssumptionsStep(N, smvDir, loopNumber)
        }

        solver.context["canMapTreeToZero"] = true

        if (automaton != null) {
            logger.info("Hooray! Minimal full verified automaton has been found!")
            return automaton
        } else {
            logger.info("UNSAT, N = $N is too small, trying to increase...")
            val observer: (() -> List<Lit>)? = solver.context.getOrNull("observer")
            if (observer == null) {
                val curAutomaton = optimizeN(start = null, end = N + 1)
                if (curAutomaton == null) {
                    logger.error("Automaton not found even without an upper bound on N")
                    break
                } else {
                    N = curAutomaton.totalGuardsSize
                    logger.info("Found new suitable minimal N = $N")
                }
            } else {
                val nullVertex: NegativeScenarioTree.Node = solver.context["nullVertex"]
                val cardinality: Cardinality = solver.context["cardinalityN"]
                val negMapping: IntVarArray = solver.context["negMapping"]
                cardinality.assumeUpperBoundLessThanOrEqual(null)
                var curAutomaton = inferExtended()
                if (solver.assumptionsObservable.listeners.contains(observer)) {
                    solver.assumptionsObservable.unregister(observer)
                }
                if (curAutomaton == null) {
                    curAutomaton = optimizeN(start = null, end = N + 1)
                    if (curAutomaton == null) {
                        logger.error("Automaton not found even without an upper bound on N and without any mapping to zero")
                        break
                    }
                    logger.info("Branch 1")
                    var root: NegativeScenarioTree.Node? = nullVertex
                    val doNotUseThisVertices: MutableSet<NegativeScenarioTree.Node> = solver.context["doNotUseThisVertices"]
                    while (root != null) {
                        solver.addClause(-(negMapping[root.id] eq 0))
                        doNotUseThisVertices += root
                        root = root.parent
                    }
                    solver.context["needRegenerateNullVertex"] = true
                    continue
                }
                curAutomaton = optimizeN(start = curAutomaton.totalGuardsSize - 1, end = N)
                if (curAutomaton == null) {
                    logger.error("Broken CEGIS halt!!!!!")
                    break
                } else {
                    if (curAutomaton.totalGuardsSize == N) {
                        logger.info("Branch 2")
                        var root: NegativeScenarioTree.Node? = nullVertex
                        while (root != null) {
                            solver.imply(negMapping[root.id] eq 0, cardinality.totalizer[N])
                            root = root.parent
                        }
                        solver.context["canMapTreeToZero"] = false
                    } else {
                        logger.info("Branch 3")
                        solver.assumptionsObservable.register(observer)
                    }
                    N = curAutomaton.totalGuardsSize
                    logger.info("Found new suitable minimal N = $N")
                }
            }
        }
    }
    return null
}
