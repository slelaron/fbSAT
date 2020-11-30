package ru.ifmo.fbsat.core.task.extra.schema

import com.github.lipen.multiarray.MultiArray
import com.soywiz.klock.PerformanceCounter
import okio.buffer
import okio.sink
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
import ru.ifmo.fbsat.core.utils.Globals
import ru.ifmo.fbsat.core.utils.log
import ru.ifmo.fbsat.core.utils.timeSince
import ru.ifmo.fbsat.core.utils.toBinaryString
import ru.ifmo.fbsat.core.utils.toBooleanArray
import ru.ifmo.fbsat.core.utils.toList_
import ru.ifmo.fbsat.core.utils.useWith
import ru.ifmo.fbsat.core.utils.writeln
import java.io.File
import kotlin.math.pow

data class Input(
    val values: List<Boolean>
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
    val values: List<Boolean>
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

@Suppress("LocalVariableName")
fun synthesizeSchema(tt: Map<Input, Output>, M: Int, X: Int, Z: Int): Boolean {
    val solver = Solver.minisat()
    val outDir = File("out/schema-synthesis")
    outDir.mkdirs()

    with(solver) {
        val XM = 2
        val ZM = 1

        val modularInboundVarPins: MultiArray<List<Int>> =
            MultiArray.create(M) { (m) -> (1..XM).map { (m - 1) * XM + it } }
        val modularOutboundVarPins: MultiArray<List<Int>> =
            MultiArray.create(M) { (m) -> (1..ZM).map { (m - 1) * ZM + it } }
        val externalInboundVarPins: List<Int> =
            (1..Z).map { modularInboundVarPins.values.flatten().size + it }
        val externalOutboundVarPins: List<Int> =
            (1..X).map { modularOutboundVarPins.values.flatten().size + it }
        val allInboundVarPins: List<Int> =
            modularInboundVarPins.values.flatten() + externalInboundVarPins
        val allOutboundVarPins: List<Int> =
            modularOutboundVarPins.values.flatten() + externalOutboundVarPins

        log.info("Modules inbound pins = ${modularInboundVarPins.values}")
        log.info("External inbound pins = $externalInboundVarPins")
        log.info("Modules outbound pins = ${modularOutboundVarPins.values}")
        log.info("External outbound pins = $externalOutboundVarPins")

        val uniqueInputs: List<Input> = tt.keys.sortedBy { it.values.toBinaryString() }
        val U = uniqueInputs.size

        // =================

        log.info("Declaring variables...")
        val timeStartVariables = PerformanceCounter.reference
        val nVarsStart = numberOfVariables

        val blockType = newDomainVarArray(M) {
            BlockType.values().asIterable()
        }
        val inboundVarPinParent = newIntVarArray(allInboundVarPins.size) { (p) ->
            // (0..allOutboundVarPins.size)
            if (p in externalInboundVarPins) {
                (0..allOutboundVarPins.size)
            } else {
                val m = (p - 1) / XM + 1
                (1 until m).flatMap { m_ -> modularOutboundVarPins[m_] } + externalOutboundVarPins + 0
            }
        }
        val inboundVarPinValue = newBoolVarArray(allInboundVarPins.size, U)
        val outboundVarPinValue = newBoolVarArray(allOutboundVarPins.size, U)

        val nVarsDiff = numberOfVariables - nVarsStart
        log.debug {
            "Done declaring $nVarsDiff variables (total $numberOfVariables) in %.2f s."
                .format(timeSince(timeStartVariables).seconds)
        }

        // =================

        log.info("Declaring constraints...")
        val timeStartConstraints = PerformanceCounter.reference
        val nClausesStart = numberOfClauses

        comment("Inbound pins have the same value as their parents")
        for (pin in allInboundVarPins)
            for (par in inboundVarPinParent[pin].domain - 0)
            // for (par in 1..allOutboundVarPins.size)
                for (u in 1..U)
                    implyIff(
                        inboundVarPinParent[pin] eq par,
                        inboundVarPinValue[pin, u],
                        outboundVarPinValue[par, u]
                    )

        comment("Parent-less pins have false values")
        for (pin in allInboundVarPins)
            for (u in 1..U)
                imply(
                    inboundVarPinParent[pin] eq 0,
                    -inboundVarPinValue[pin, u]
                )

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

        comment("NOT block has its second pin unused")
        for (m in 1..M)
            imply(
                blockType[m] eq BlockType.NOT,
                inboundVarPinParent[modularInboundVarPins[m][1]] eq 0
            )

        comment("AND block semantics")
        for (m in 1..M)
            for (pin in modularOutboundVarPins[m])
                for (u in 1..U)
                    implyIffAnd(
                        blockType[m] eq BlockType.AND,
                        outboundVarPinValue[pin, u],
                        inboundVarPinValue[modularInboundVarPins[m][0], u],
                        inboundVarPinValue[modularInboundVarPins[m][1], u]
                    )

        comment("OR block semantics")
        for (m in 1..M)
            for (pin in modularOutboundVarPins[m])
                for (u in 1..U)
                    implyIffOr(
                        blockType[m] eq BlockType.OR,
                        outboundVarPinValue[pin, u],
                        inboundVarPinValue[modularInboundVarPins[m][0], u],
                        inboundVarPinValue[modularInboundVarPins[m][1], u]
                    )

        comment("NOT block semantics")
        for (m in 1..M)
            for (pin in modularOutboundVarPins[m])
                for (u in 1..U)
                    implyIff(
                        blockType[m] eq BlockType.NOT,
                        outboundVarPinValue[pin, u],
                        -inboundVarPinValue[modularInboundVarPins[m][0], u]
                    )

        comment("Input values")
        for (x in 1..X)
            for (u in 1..U) {
                val pin = externalOutboundVarPins[x - 1]
                val value = uniqueInputs[u - 1].values[x - 1]
                clause(outboundVarPinValue[pin, u] sign value)
            }

        comment("Output values")
        for (z in 1..Z)
            for (u in 1..U) {
                val pin = externalInboundVarPins[z - 1]
                val value = tt.getValue(uniqueInputs[u - 1]).values[z - 1]
                clause(inboundVarPinValue[pin, u] sign value)
            }

        val nClausesDiff = numberOfClauses - nClausesStart
        log.debug {
            "Done declaring $nClausesDiff constraints (total $numberOfClauses) in %.2f s."
                .format(timeSince(timeStartConstraints).seconds)
        }

        // =================

        require(tt.values.all { it.values.size == 1 })
        val fileCnf = outDir.resolve("schema_${ttToBinaryString(tt.mapValues { it.value.values[0] })}_X${X}_M$M.cnf")
        dumpCnf(fileCnf)

        val model: Model? = solve()
        // val model: Model? = null

        @Suppress("NAME_SHADOWING")
        if (model != null) {
            val blockType = blockType.convert(model)
            log.info("blockType = ${blockType.values}")

            val parent = inboundVarPinParent.convert(model)
            for (m in 1..M)
                log.info("(m = $m) parent = ${modularInboundVarPins[m].map { parent[it] }}")
            log.info("(external) parent = ${externalInboundVarPins.map { parent[it] }}")

            val inboundValue = inboundVarPinValue.convert(model)
            val outboundValue = outboundVarPinValue.convert(model)
            log.info("values (input -> external outbound -> modules inbound -> modules outbound -> external inbound):")
            for (u in 1..U) {
                log.just(
                    "  u=${u.toString().padEnd(3)}" +
                        " ${uniqueInputs[u - 1]}" +
                        " -> ${
                            (1..X).map { x ->
                                outboundValue[externalOutboundVarPins[x - 1], u].toInt()
                            }
                        }" +
                        " -> ${
                            (1..M).map { m ->
                                modularInboundVarPins[m].map { inboundValue[it, u] }.toBinaryString()
                            }
                        }" +
                        " -> ${
                            (1..M).map { m ->
                                outboundValue[modularOutboundVarPins[m][0], u].toInt()
                            }
                        }" +
                        " -> ${
                            (1..Z).map { z ->
                                inboundValue[externalInboundVarPins[z - 1], u].toInt()
                            }
                        }"
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
                writeln("  { rank=max")
                for (x in 1..X) {
                    writeln("    X$x [label=\"X$x\"];")
                }
                writeln("  }")
                writeln("    // Output variables")
                writeln("  { rank=min")
                for (z in 1..Z) {
                    writeln("    Z$z [label=\"Z$z\"];")
                }
                writeln("  }")
                writeln("")
                writeln("    // Schema")
                for (m in 1..M) {
                    val ts = mutableListOf<String>()
                    for (pin in modularInboundVarPins[m]) {
                        val par = parent[pin]
                        if (par == 0) {
                            break
                        } else if (par in externalOutboundVarPins) {
                            ts.add("X${externalOutboundVarPins.indexOf(par) + 1}")
                        } else {
                            ts.add("g${(par - 1) / ZM + 1}")
                        }
                    }
                    writeln("    g$m -> {${ts.joinToString(",")}};")
                }
                for (z in 1..Z) {
                    val ts = mutableListOf<String>()
                    for (pin in externalInboundVarPins) {
                        val par = parent[pin]
                        if (par == 0) {
                            break
                        } else if (par in externalOutboundVarPins) {
                            ts.add("X${externalOutboundVarPins.indexOf(par) + 1}")
                        } else {
                            ts.add("g${(par - 1) / ZM + 1}")
                        }
                    }
                    writeln("    Z$z -> {${ts.joinToString(",")}};")
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

 fun synthesizeSchemaIterativelyBottomUp(tt: Map<Input, Output>, X: Int, Z: Int) {
    for (M in 1..30) {
        log.br()
        log.info("Trying M = $M...")

        if (synthesizeSchema(tt, M = M, X = X, Z = Z)) {
            log.success("M = $M: Success")
            break
        } else {
            log.failure("M = $M: Could not infer schema.")
        }
    }
}

fun main() {
    val timeStart = PerformanceCounter.reference
    Globals.IS_DEBUG = true

    val Z = 1
    // val X = 3
    // val values = "10010110"
    // val X = 4
    // val values = "0000001101101001" // 11 sat (28s), 10 unknown
    val X = 5
    val values = "00000000000000000000000010110110" // 10 sat (1s w/ ms, 55s w/ cms), 9 unknown (>10m)
    val tt = values.toTruthTable(X).mapValues { Output(listOf(it.value)) }

    // synthesizeIterativeBottomUp(tt, X = X, Z = Z)
    synthesizeSchema(tt, M = 10, X = X, Z = Z)

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
