package ru.ifmo.fbsat.core.task.single.complete

import com.github.lipen.satlib.card.Cardinality
import com.soywiz.klock.PerformanceCounter
import ru.ifmo.fbsat.core.automaton.Automaton
import ru.ifmo.fbsat.core.scenario.negative.Counterexample
import ru.ifmo.fbsat.core.scenario.negative.NegativeScenario
import ru.ifmo.fbsat.core.scenario.negative.NegativeScenarioTree
import ru.ifmo.fbsat.core.scenario.positive.PositiveScenarioTree
import ru.ifmo.fbsat.core.task.Inferrer
import ru.ifmo.fbsat.core.task.optimizeN
import ru.ifmo.fbsat.core.task.single.basic.BasicTask
import ru.ifmo.fbsat.core.task.single.extended.ExtendedTask
import ru.ifmo.fbsat.core.task.single.extended.extendedMin
import ru.ifmo.fbsat.core.task.single.extended.extendedMinUB
import ru.ifmo.fbsat.core.task.single.extended.inferExtended
import ru.ifmo.fbsat.core.utils.*
import java.io.File

private val logger = MyLogger {}

fun Inferrer.cegisBounded(
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
    declare(ExtendedTask(maxGuardSize = maxGuardSize, maxTotalGuardsSize = maxTotalGuardsSize))
    declare(CompleteTask(negativeScenarioTree))
    return performCegisBounded(smvDir, loopNumber)
}

fun Inferrer.cegisBoundedStep(
    maxTotalGuardsSize: Int,
    smvDir: File,
    loopNumber: Int,// = 0,
): Automaton? {
    solver.apply {
        val cardinalityN: Cardinality = context["cardinalityN"]
        cardinalityN.assumeUpperBoundLessThanOrEqual(maxTotalGuardsSize)
    }
    return performCegisBounded(smvDir, loopNumber)
}

@Suppress("LocalVariableName")
fun Inferrer.cegisMinBounded(
    scenarioTree: PositiveScenarioTree,
    initialNegativeScenarioTree: NegativeScenarioTree? = null,
    numberOfStates: Int? = null,
    startNumberOfStates: Int? = null,
    maxGuardSize: Int? = null, // P, search if null
    maxPlateauWidth: Int? = null, // w, =Inf if null
    smvDir: File,
): Automaton? {
    check(Globals.IS_USE_ASSUMPTIONS) { "Currently, cegis-min-bounded only works with assumptions being turned on globally. Pass --use-assumptions flag to enable them." }

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
            cegisBounded(
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
            cegisBoundedStep(N, smvDir, loopNumber)
        }

        if (automaton != null) {
            logger.info("Hooray! Minimal full verified automaton has been found!")
            return automaton
        } else {
            logger.info("UNSAT, N = $N is too small, trying to increase...")
            val curAutomaton = optimizeN(start = null, end = N + 1)
            if (curAutomaton == null) {
                logger.error("Automaton not found even without an upper bound on N")
                break
            } else {
                N = curAutomaton.totalGuardsSize
                logger.info("Found new suitable minimal N = $N")
            }
        }
    }
    return null
}

@Suppress("DuplicatedCode")
fun Inferrer.performCegisBounded(smvDir: File, loopNumber: Int): Automaton? {
    logger.info("Performing CEGIS...")

    // Copy smv files to output directory
    smvDir.copyRecursively(outDir, overwrite = true)

    val scenarioTree: PositiveScenarioTree = solver.context["scenarioTree"]
    val negativeScenarioTree: NegativeScenarioTree = solver.context["negativeScenarioTree"]
    lateinit var lastNegativeScenarios: List<NegativeScenario>
    val heat = solver.context("heat") { mutableMapOf<Int, MutableSet<Int>>() }

    mainLoop@ for (iterationNumber in 1 until 10000) {
        // log.info("CEGIS iteration #$iterationNumber")
        logger.debug("CEGIS iteration #$iterationNumber on loop $loopNumber")
        val timeStart = PerformanceCounter.reference

        // Update to take into account possible extension of the negative scenario tree
        solver.updateNegativeReduction()
        // Infer update
        val automaton = inferExtended()
        if (automaton == null) {
            logger.error(
                "CEGIS iteration #$iterationNumber failed to infer an automaton after %.3f s"
                    .format(timeSince(timeStart).seconds)
            )
            return null
        }

        // ==============
        // Dump intermediate automaton
        automaton.dump(outDir, "_automaton_loop%d_iter%04d".format(loopNumber, iterationNumber))

        // ==============
        // Verify automaton with NuSMV
        val counterexamples = automaton.verifyWithNuSMVBounded(outDir)
        if (counterexamples.isEmpty()) {
            logger.info("CEGIS iteration #$iterationNumber done in %.3f s".format(timeSince(timeStart).seconds))
            logger.info("No counterexamples!")
            negativeScenarioTree.dump(outDir, "_negative_tree%d_iter%04d".format(loopNumber, iterationNumber), heat)
            return automaton
        }
        // Convert counterexamples to negative scenarios
        val negativeScenarios = counterexamples.map {
            NegativeScenario.from(
                counterexample = it,
                inputEvents = scenarioTree.inputEvents,
                outputEvents = scenarioTree.outputEvents,
                inputNames = scenarioTree.inputNames,
                outputNames = scenarioTree.outputNames
            )
        }
        // Populate negTree with new negative scenarios
        val treeSize = negativeScenarioTree.size
        for (scenario in negativeScenarios) {
            negativeScenarioTree.addScenario(scenario)
        }

        //if (iterationNumber % 20 == 0) {
        //    val nullVertex: NegativeScenarioTree.Node? = solver.context.getOrNull("nullVertex")
        //    negativeScenarioTree.dump(outDir, "_negative_tree%d_iter%04d".format(loopNumber, iterationNumber), heat, nullVertex?.id)
        //}

        val treeSizeDiff = negativeScenarioTree.size - treeSize
        // Note: it is suffice to check just `negSc == lastNegSc`, but it may be costly,
        // so check it only in a specific case - when negative tree does not change its size
        if (treeSizeDiff == 0 && negativeScenarios == lastNegativeScenarios) {
            error("Stale")
        }
        lastNegativeScenarios = negativeScenarios
        logger.info("CEGIS iteration #$iterationNumber done in %.3f s".format(timeSince(timeStart).seconds))
    }
    return null
}

fun Automaton.verifyWithNuSMVBounded(dir: File): List<Counterexample> {
    // Save automaton to smv directory
    dumpSmv(dir.resolve("control.smv"))

    // Perform formal verification using NuSMV, generate counterexamples to given ltl-spec
    run {
        val cmd = "make model counterexamples"
        logger.debug { "Running '$cmd'..." }
        val timeStart = PerformanceCounter.reference
        val exitcode = Runtime.getRuntime().exec(cmd, null, dir).waitFor()
        val runningTime = timeSince(timeStart)
        logger.debug { "'$cmd' returned with $exitcode in %.3f s.".format(runningTime.seconds) }
        check(exitcode == 0) { "NuSMV exitcode: $exitcode" }
    }

    // Handle counterexamples after verification
    val fileCounterexamples = dir.resolve("counterexamples")


    return if (fileCounterexamples.exists()) {
        // Read new counterexamples
        val counterexamples: List<Counterexample> = Counterexample.from(fileCounterexamples)

        val ltl = dir.resolve("ltl")
        check(ltl.exists())
        var totalSpecs = 0
        val fileCounterexamplesAfter = ltl.useLines { lines ->
            dir.resolve("commands-min").printWriter().useWith {
                println("go_bmc")
                var states = 0
                var spec: String? = null
                for (line in lines.map(String::trim)) {
                    when {
                        line.startsWith("-- specification") && line.endsWith("is false") -> {
                            spec?.let { println("check_ltlspec_bmc_inc -k ${states - 1} -l * -p \"$it\"") }
                            //spec?.let { println("check_ltlspec_bmc_inc -k 15 -p \"$it\"") }
                            spec = line.substringAfter("-- specification  ").substringBefore("is false").trim()
                            ++totalSpecs
                            states = 0
                        }
                        line.startsWith("-> State:") -> ++states
                    }
                }
                spec?.let { println("check_ltlspec_bmc_inc -k ${states - 1} -l * -p \"$it\"") }
                //spec?.let { println("check_ltlspec_bmc_inc -k 15 -p \"$it\"") }
                print("show_traces -a -v -o counterexamples\ntime\nquit")
            }
            val cmd = "make counterexamples-min"
            logger.debug { "Running '$cmd'..." }
            val timeStart = PerformanceCounter.reference
            val exitcode = Runtime.getRuntime().exec(cmd, null, dir).waitFor()
            val runningTime = timeSince(timeStart)
            logger.debug { "'$cmd' returned with $exitcode in %.3f s.".format(runningTime.seconds) }
            check(exitcode == 0) { "NuSMV exitcode: $exitcode" }
            val file = dir.resolve("counterexamples")
            check(file.exists()) { "After bounded model checking 'counterexamples' file doesn't exist" }
            val tracesCount = file.readLines().count { it.startsWith("Trace Description: BMC Counterexample") }
            check(tracesCount == totalSpecs) { "Wrong number of minimized counterexamples: actual: $tracesCount, expected: $totalSpecs" }
            dir.resolve("counterexamples")
        }

        // Read new counterexamples
        val counterexamplesAfter: List<Counterexample> = Counterexample.from(fileCounterexamplesAfter)

        // [DEBUG] Append new counterexamples to 'ce'
        logger.debug { "Dumping ${counterexamples.size} + ${counterexamplesAfter.size} counterexample(s)..." }
        dir.resolve("ce").appendText(fileCounterexamples.readText())

        counterexamples + counterexamplesAfter
    } else {
        emptyList()
    }
}
