package ru.ifmo.fbsat.core.task.extra.library

import com.github.lipen.multiarray.IntMultiArray
import com.github.lipen.multiarray.MultiArray
import com.soywiz.klock.PerformanceCounter
import okio.buffer
import okio.sink
import ru.ifmo.fbsat.core.scenario.InputAction
import ru.ifmo.fbsat.core.scenario.InputEvent
import ru.ifmo.fbsat.core.scenario.InputValues
import ru.ifmo.fbsat.core.scenario.OutputAction
import ru.ifmo.fbsat.core.scenario.OutputEvent
import ru.ifmo.fbsat.core.scenario.OutputValues
import ru.ifmo.fbsat.core.scenario.ScenarioElement
import ru.ifmo.fbsat.core.scenario.positive.PositiveScenario
import ru.ifmo.fbsat.core.scenario.positive.PositiveScenarioTree
import ru.ifmo.fbsat.core.solver.BoolVarArray
import ru.ifmo.fbsat.core.solver.IntVarArray
import ru.ifmo.fbsat.core.solver.Model
import ru.ifmo.fbsat.core.solver.Solver
import ru.ifmo.fbsat.core.solver.convert
import ru.ifmo.fbsat.core.solver.imply
import ru.ifmo.fbsat.core.solver.implyIff
import ru.ifmo.fbsat.core.solver.implyIffAnd
import ru.ifmo.fbsat.core.solver.implyIffOr
import ru.ifmo.fbsat.core.solver.newBoolVarArray
import ru.ifmo.fbsat.core.solver.newDomainVarArray
import ru.ifmo.fbsat.core.solver.newIntVarArray
import ru.ifmo.fbsat.core.solver.sign
import ru.ifmo.fbsat.core.task.extra.schema.Input
import ru.ifmo.fbsat.core.utils.Globals
import ru.ifmo.fbsat.core.utils.log
import ru.ifmo.fbsat.core.utils.timeSince
import ru.ifmo.fbsat.core.utils.toBinaryString
import ru.ifmo.fbsat.core.utils.toBooleanArray
import ru.ifmo.fbsat.core.utils.toList_
import ru.ifmo.fbsat.core.utils.useWith
import ru.ifmo.fbsat.core.utils.withIndex
import ru.ifmo.fbsat.core.utils.writeln
import java.io.File
import kotlin.math.pow

data class Input(
    val values: List<Boolean>,
) {
    constructor(values: BooleanArray) :
        this(values.asList())

    constructor(values: Iterable<Boolean>) :
        this(values.toList_())

    constructor(values: String) :
        this(values.toBooleanArray())

    constructor(i: Int, numberOfVariables: Int) :
        this(i.toLong(), numberOfVariables)

    constructor(i: Long, numberOfVariables: Int) :
        this(List(numberOfVariables) { j -> 2L.pow(numberOfVariables - j - 1) and i != 0L })

    override fun toString(): String {
        return values.toBinaryString()
    }
}

data class Output(
    val values: List<Boolean>,
) {
    constructor(values: BooleanArray) :
        this(values.asList())

    constructor(values: Iterable<Boolean>) :
        this(values.toList_())

    constructor(values: String) :
        this(values.toBooleanArray())

    override fun toString(): String {
        return values.toBinaryString()
    }
}

private var iBlockType = 0

enum class BlockType(val value: Int) {
    AND(++iBlockType),
    OR(++iBlockType),
    NOT(++iBlockType),
    ;

    companion object {
        private val lookup: Map<Int, BlockType> = values().associateBy(BlockType::value)

        fun from(value: Int): BlockType = lookup.getValue(value)
    }
}

fun <T> MultiArray<T>.toList1(): List<T> {
    require(shape.size == 1)
    val (a) = shape
    return List(a) { i -> this[i + 1] }
}

fun <T> MultiArray<T>.toList2(): List<List<T>> {
    require(shape.size == 2)
    val (a, b) = shape
    return List(a) { i -> List(b) { j -> this[i + 1, j + 1] } }
}

fun <T> MultiArray<T>.toList3(): List<List<List<T>>> {
    require(shape.size == 3)
    val (a, b, c) = shape
    return List(a) { i -> List(b) { j -> List(c) { k -> this[i + 1, j + 1, k + 1] } } }
}

@Suppress("LocalVariableName")
fun synthesizeSystemFromLibrary(
    tree: PositiveScenarioTree,
    M: Int,
    X: Int,
    Z: Int,
): Boolean {
    // TODO: input should be a list of traces (in SMV sense)

    val solver = Solver.minisat()
    val outDir = File("out/library-synthesis")
    outDir.mkdirs()

    with(solver) {
        val V = tree.size
        val XM = 2
        val ZM = 1

        // Note:
        //  - Inbound = input for module, but output for external
        //  - Outbound = output for module, but input for external

        var _inboundVarPins = 0
        var _outboundVarPins = 0
        val modularInputVarPin = IntMultiArray.create(M, XM) { ++_inboundVarPins }
        val modularOutputVarPin = IntMultiArray.create(M, ZM) { ++_outboundVarPins }
        val externalInputVarPin = IntMultiArray.create(X) { ++_outboundVarPins }
        val externalOutputVarPin = IntMultiArray.create(Z) { ++_inboundVarPins }
        check(_inboundVarPins == M * XM + Z)
        check(_outboundVarPins == M * ZM + X)

        val modularInputVarPins: MultiArray<List<Int>> =
            MultiArray.create(M) { (m) -> List(XM) { x -> modularInputVarPin[m, x + 1] } }
        val modularOutputVarPins: MultiArray<List<Int>> =
            MultiArray.create(M) { (m) -> List(ZM) { z -> modularOutputVarPin[m, z + 1] } }
        val externalInputVarPins: List<Int> =
            List(X) { x -> externalInputVarPin[x + 1] }
        val externalOutputVarPins: List<Int> =
            List(Z) { z -> externalOutputVarPin[z + 1] }
        val allInboundVarPins = 1.._inboundVarPins
        val allOutboundVarPins = 1.._outboundVarPins
        check(allInboundVarPins.toList() == modularInputVarPins.values.flatten() + externalOutputVarPins)
        check(allOutboundVarPins.toList() == modularOutputVarPins.values.flatten() + externalInputVarPins)

        fun pin2x(pin: Int): Int {
            @Suppress("UnnecessaryVariable")
            val x = externalInputVarPins.indexOf(pin) + 1
            return x
        }

        fun pin2z(pin: Int): Int {
            @Suppress("UnnecessaryVariable")
            val z = externalOutputVarPins.indexOf(pin) + 1
            return z
        }

        fun pin2mx(pin: Int): Pair<Int, Int> {
            val m = (pin - 1) / XM + 1
            val x = modularInputVarPins[m].indexOf(pin) + 1
            return Pair(m, x)
        }

        fun pin2mz(pin: Int): Pair<Int, Int> {
            val m = (pin - 1) / ZM + 1
            val z = modularOutputVarPins[m].indexOf(pin) + 1
            return Pair(m, z)
        }

        for (m in 1..M)
            for (x in 1..XM)
                check(pin2mx(modularInputVarPin[m, x]) == Pair(m, x))
        for (m in 1..M)
            for (z in 1..ZM)
                check(pin2mz(modularOutputVarPin[m, z]) == Pair(m, z))

        log.info("Modules inbound (input var) pins = ${(1..M).map { m -> modularInputVarPins[m] }}")
        log.info("External inbound (output var) pins = $externalOutputVarPins")
        log.info("Modules outbound (output var) pins = ${(1..M).map { m -> modularOutputVarPins[m] }}")
        log.info("External outbound (input var) pins = $externalInputVarPins")

        // =================

        log.info("Declaring variables...")
        val timeStartVariables = PerformanceCounter.reference
        val nVarsStart = numberOfVariables

        val falseVar by lazy { newLiteral().also { clause(-it) } }

        val blockType = newDomainVarArray(M) {
            BlockType.values().asIterable()
        }

        val modularInputVarParent = newIntVarArray(M, XM) { (m, pin) ->
            (1 until m).flatMap { m_ -> modularOutputVarPins[m_] } + externalInputVarPins + 0
        }
        val externalOutputVarParent = newIntVarArray(Z) { (z) ->
            modularOutputVarPins.values.flatten() + externalInputVarPins + 0
        }

        val modularInputVarValue = newBoolVarArray(M, XM, V)
        val modularOutputVarValue = newBoolVarArray(M, ZM, V)
        // val modularOutputVarCurrentValue = newBoolVarArray(M, ZM, V) { (m, z, v) ->
        //     if (v == 1) falseVar
        //     else modularOutputVarValue[m, z, tree.parent(v)]
        // }
        val externalInputVarValue = newBoolVarArray(X, V)
        val externalOutputVarValue = newBoolVarArray(Z, V)

        val inboundVarPinParent = IntVarArray.create(_inboundVarPins) { (pin) ->
            if (pin in externalOutputVarPins) {
                val z = pin2z(pin)
                externalOutputVarParent[z]
            } else {
                val (m, x) = pin2mx(pin)
                modularInputVarParent[m, x]
            }
        }
        val inboundVarPinValue = BoolVarArray.create(_inboundVarPins, V) { (pin, v) ->
            if (pin in externalOutputVarPins) {
                val z = pin2z(pin)
                externalOutputVarValue[z, v]
            } else {
                val (m, x) = pin2mx(pin)
                modularInputVarValue[m, x, v]
            }
        }
        val outboundVarPinValue = BoolVarArray.create(_outboundVarPins, V) { (pin, v) ->
            if (pin in externalInputVarPins) {
                val x = pin2x(pin)
                externalInputVarValue[x, v]
            } else {
                val (m, z) = pin2mz(pin)
                modularOutputVarValue[m, z, v]
            }
        }

        val nVarsDiff = numberOfVariables - nVarsStart
        log.debug {
            "Done declaring $nVarsDiff variables (total $numberOfVariables) in %.2f s."
                .format(timeSince(timeStartVariables).seconds)
        }

        // =================

        log.info("Declaring constraints...")
        val timeStartConstraints = PerformanceCounter.reference
        val nClausesStart = numberOfClauses

        // comment("ADHOCs")
        // check(M == 2)
        // clause(blockType[1] eq BlockType.AND)
        // clause(blockType[2] eq BlockType.OR)
        // clause(modularInputVarParent[1, 1] eq externalInputVarPins[1 - 1])
        // clause(modularInputVarParent[1, 2] eq externalInputVarPins[2 - 1])
        // clause(modularInputVarParent[2, 1] eq modularOutputVarPins[1][1 - 1])
        // clause(modularInputVarParent[2, 2] eq externalInputVarPins[3 - 1])
        // clause(externalOutputVarParent[1] eq modularOutputVarPins[2][1 - 1])

        comment("Inbound pins have the same value as their parents")
        for (pin in allInboundVarPins)
            for (par in inboundVarPinParent[pin].domain - 0)
                for (v in 1..V)
                    implyIff(
                        inboundVarPinParent[pin] eq par,
                        inboundVarPinValue[pin, v],
                        outboundVarPinValue[par, v]
                    )

        comment("Parent-less pins have false values")
        for (pin in allInboundVarPins)
            for (v in 1..V)
                imply(
                    inboundVarPinParent[pin] eq 0,
                    -inboundVarPinValue[pin, v]
                )

        comment("Parent absence propagation")
        for (m in 1..M)
            for (x in 1 until XM)
                imply(
                    modularInputVarParent[m, x] eq 0,
                    modularInputVarParent[m, x + 1] eq 0
                )

        // FIXME: rewrite
        // Note: this constraint is big and only slows everything down
        // comment("AND/OR pins' parents order")
        // for (t in listOf(BlockType.AND, BlockType.OR))
        //     for (m in 1..M) {
        //         check(modularInboundVarPins[m].size == 2)
        //         val (pin1, pin2) = modularInboundVarPins[m]
        //         for (par1 in inboundVarPinParent[pin1].domain - 0)
        //             for (par2 in inboundVarPinParent[pin2].domain - 0)
        //                 if (par2 <= par1)
        //                     implyImply(
        //                         blockType[m] eq t,
        //                         inboundVarPinParent[pin1] eq par1,
        //                         inboundVarPinParent[pin2] neq par2
        //                     )
        //     }

        comment("NOT block uses only the first input pin")
        for (m in 1..M)
            for (x in 2..XM)
                imply(
                    blockType[m] eq BlockType.NOT,
                    modularInputVarParent[m, x] eq 0
                )

        comment("AND block semantics")
        for (m in 1..M)
            for (z in 1..ZM)
                for (v in 1..V)
                    implyIffAnd(
                        blockType[m] eq BlockType.AND,
                        modularOutputVarValue[m, z, v],
                        modularInputVarValue[m, 1, v],
                        modularInputVarValue[m, 2, v]
                    )

        comment("OR block semantics")
        for (m in 1..M)
            for (z in 1..ZM)
                for (v in 1..V)
                    implyIffOr(
                        blockType[m] eq BlockType.OR,
                        modularOutputVarValue[m, z, v],
                        modularInputVarValue[m, 1, v],
                        modularInputVarValue[m, 2, v]
                    )

        comment("NOT block semantics")
        for (m in 1..M)
            for (z in 1..ZM)
                for (v in 1..V)
                    implyIff(
                        blockType[m] eq BlockType.NOT,
                        modularOutputVarValue[m, z, v],
                        -modularInputVarValue[m, 1, v]
                    )

        comment("Input values")
        for (v in 2..V)
            for (x in 1..X) {
                val value = tree.inputValue(v, x)
                clause(externalInputVarValue[x, v] sign value)
            }

        comment("Output values")
        for (v in 2..V)
            for (z in 1..Z) {
                val value = tree.outputValue(v, z)
                clause(externalOutputVarValue[z, v] sign value)
            }

        comment("Initial (root) input values are false")
        for (x in 1..X) {
            clause(-externalInputVarValue[x, 1])
        }

        // comment("Initial (root) output values are false")
        // for (z in 1..Z) {
        //     clause(-externalOutputVarValue[z, 1])
        // }

        val nClausesDiff = numberOfClauses - nClausesStart
        log.debug {
            "Done declaring $nClausesDiff constraints (total $numberOfClauses) in %.2f s."
                .format(timeSince(timeStartConstraints).seconds)
        }

        // =================

        // require(tt.values.all { it.values.size == 1 })
        // val fileCnf = outDir.resolve("schema_${ttToBinaryString(tt.mapValues { it.value.values[0] })}_X${X}_M$M.cnf")
        // dumpCnf(fileCnf)

        val model: Model? = solve()
        // val model: Model? = null

        @Suppress("NAME_SHADOWING")
        if (model != null) {
            val blockType = blockType.convert(model)
            log.info("blockType = ${blockType.values}")

            val parent = inboundVarPinParent.convert(model)
            for (m in 1..M) {
                log.info("(m = $m) parent = ${modularInputVarPins[m].map { parent[it] }}")
            }
            log.info("(external) parent = ${externalOutputVarPins.map { parent[it] }}")

            val inboundValue = inboundVarPinValue.convert(model)
            val outboundValue = outboundVarPinValue.convert(model)
            log.just("  v   tree.input -> tree.output :: input > input modular > output modular > output ext")
            for (v in 1..V) {
                log.just(
                    "  v=$v" +
                        " ${
                            if (v == 1) "-".repeat(X)
                            else (1..X).map { x -> tree.inputValue(v, x) }.toBinaryString()
                        }" +
                        " -> ${(1..Z).map { z -> tree.outputValue(v, z) }.toBinaryString()}" +
                        " :: ${externalInputVarPins.map { outboundValue[it, v] }.toBinaryString()}" +
                        " >> ${
                            modularInputVarPins.values.map { pins ->
                                pins.map { inboundValue[it, v] }.toBinaryString()
                            }
                        }" +
                        " >> ${
                            modularOutputVarPins.values.map { pins ->
                                pins.map { outboundValue[it, v] }.toBinaryString()
                            }
                        }" +
                        " >> ${externalOutputVarPins.map { inboundValue[it, v] }.toBinaryString()}"
                )
            }

            val fileSchema = outDir.resolve("schema.gv")
            fileSchema.sink().buffer().useWith {
                writeln("digraph {")
                writeln("    // Gates")
                for (m in 1..M) {
                    writeln("    g$m [label=\"$m: ${blockType[m]}\"];")
                }
                writeln("    // Input variables")
                writeln("    { rank=sink; rankdir=LR;")
                for (x in 1..X) {
                    writeln("        X$x [label=\"X$x\"];")
                }
                writeln("    edge [style=invis];")
                writeln("    ${(1..X).joinToString(" -> ") { x -> "X$x" }};")
                writeln("    }")
                writeln("    // Output variables")
                writeln("    { rank=source; rankdir=LR;")
                for (z in 1..Z) {
                    writeln("        Z$z [label=\"Z$z\"];")
                }
                writeln("    edge [style=invis];")
                writeln("    ${(1..Z).joinToString(" -> ") { z -> "Z$z" }};")
                writeln("    }")
                writeln("")
                writeln("    // Schema")
                for (m in 1..M) {
                    val ts = sequence {
                        for (pin in modularInputVarPins[m]) {
                            when (val par = parent[pin]) {
                                0 -> {
                                    // break
                                }
                                in externalInputVarPins -> {
                                    yield("X${externalInputVarPins.indexOf(par) + 1}")
                                }
                                else -> {
                                    yield("g${(par - 1) / ZM + 1}")
                                }
                            }
                        }
                    }
                    for ((i, t) in ts.withIndex(start = 1)) {
                        writeln("    g$m -> $t [label=\"$i\"];")
                    }
                }
                for (z in 1..Z) {
                    val ts = sequence {
                        for (pin in externalOutputVarPins) {
                            when (val par = parent[pin]) {
                                0 -> {
                                    // break
                                }
                                in externalInputVarPins -> {
                                    yield("X${externalInputVarPins.indexOf(par) + 1}")
                                }
                                else -> {
                                    yield("g${(par - 1) / ZM + 1}")
                                }
                            }
                        }
                    }
                    for ((i, t) in ts.withIndex(start = 1)) {
                        writeln("    Z$z -> $t [label=\"$i\"];")
                    }
                }
                writeln("}")
            }
            Runtime.getRuntime().exec("dot -Tpdf -O $fileSchema")
            Runtime.getRuntime().exec("dot -Tpng -O $fileSchema")

            return true
        }
    }

    return false
}

fun synthesizeIterativeBottomUp(
    tree: PositiveScenarioTree,
    X: Int,
    Z: Int,
    maxM: Int = 30,
) {
    for (M in 1..maxM) {
        log.br()
        log.info("Trying M = $M...")

        if (synthesizeSystemFromLibrary(tree, M = M, X = X, Z = Z)) {
            log.success("M = $M: Success")
            break
        } else {
            log.failure("M = $M: Could not infer schema.")
        }
    }
}

@Suppress("UnnecessaryVariable")
fun main() {
    val timeStart = PerformanceCounter.reference

    val X = 5
    val Z = 1

    Globals.IS_DEBUG = true
    Globals.INITIAL_OUTPUT_VALUES = OutputValues.zeros(Z)

    val tree = PositiveScenarioTree(
        inputEvents = listOf(InputEvent("REQ")),
        outputEvents = listOf(OutputEvent("CNF")),
        inputNames = (1..X).map { x -> "x$x" },
        outputNames = (1..Z).map { z -> "z$z" },
        isTrie = false
    )

    // TODO: populate the tree

    fun elem(inputValues: InputValues, outputValues: OutputValues): ScenarioElement =
        ScenarioElement(
            InputAction(InputEvent("REQ"), inputValues),
            OutputAction(OutputEvent("CNF"), outputValues)
        )

    fun elem(inputValues: String, outputValues: String): ScenarioElement =
        elem(InputValues(inputValues), OutputValues(outputValues))

    fun elem(values: Pair<String, String>): ScenarioElement =
        elem(values.first, values.second)

    // val tt = listOf(
    //     "000" to "0",
    //     "001" to "1",
    //     "010" to "0",
    //     "011" to "1",
    //     "100" to "0",
    //     "101" to "1",
    //     "110" to "1",
    //     "111" to "1",
    // )
    // val es = tt.map { elem(it) }

    val tt = "00000000000000000000000010110110"
    val es = tt.toTruthTable(X = X).map { (input, output) ->
        elem(InputValues(input.values), OutputValues(listOf(output)))
    }

    val elements = es
    tree.addScenario(PositiveScenario(elements))
    tree.addScenario(PositiveScenario(es.shuffled()))
    tree.addScenario(PositiveScenario(es.shuffled()))

    val M = 10
    if (synthesizeSystemFromLibrary(tree, M = M, X = X, Z = Z)) {
        log.success("M = $M: SAT")
    } else {
        log.failure("M = $M: UNSAT")
    }

    // synthesizeIterativeBottomUp(tree, X = X, Z = Z, maxM = 12)

    log.success("All done in %.3f s.".format(timeSince(timeStart).seconds))
}

private fun Int.pow(n: Int): Int =
    if (this == 2) 1 shl n
    else this.toDouble().pow(n).toInt()

private fun Long.pow(n: Int): Long =
    if (this == 2L) 1L shl n
    else this.toDouble().pow(n).toLong()

private fun Boolean.toInt(): Int = if (this) 1 else 0

private fun valuesToTruthTable(values: List<Boolean?>, X: Int): Map<Input, Boolean> {
    val tt: MutableMap<Input, Boolean> = mutableMapOf()
    for ((i, b) in values.withIndex()) {
        if (b != null) {
            tt[Input(i, X)] = b
        }
    }
    return tt
}

@Suppress("LocalVariableName")
private fun String.toTruthTable(X: Int): Map<Input, Boolean> {
    val values = map {
        when (it) {
            '0' -> false
            '1' -> true
            'x', '-' -> null
            else -> error("Bad char '$it'")
        }
    }
    return valuesToTruthTable(values, X)
}

@Suppress("LocalVariableName")
private fun ttToBinaryString(tt: Map<Input, Boolean>): String {
    val X = tt.keys.first().values.size
    return (0 until 2.pow(X)).joinToString("") { f -> tt[Input(f, X)]?.toInt()?.toString() ?: "x" }
}
