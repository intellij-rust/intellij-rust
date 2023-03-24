/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir

import org.rust.lang.core.mir.schemas.*
import org.rust.lang.core.psi.RsConstant
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type
import org.rust.openapiext.document
import java.util.*
import kotlin.math.max

internal class MirPrettyPrinter(
    private val filenamePrefix: String = "src/",
    private val mir: MirBody,
) {
    fun print(): String {
        return buildString { printMir(mir) }
    }

    private fun localIndex(local: MirLocal): Int = mir.localDecls.indexOf(local)

    private fun blockIndex(block: MirBasicBlock): Int = mir.basicBlocks.indexOf(block)
    private fun StringBuilder.printMir(mir: MirBody): StringBuilder = apply {
        printIntro(mir.source)
        appendLine("{")
        printScopeTree(mir, 1)
        mir.basicBlocks.withIndex().forEach { (index, block) ->
            appendLine()
            print(
                block = block,
                index = index,
            )
        }
        append("}")
    }

    private fun StringBuilder.print(
        block: MirBasicBlock,
        index: Int,
    ): StringBuilder = apply {
        val cleanup = if (block.unwind) " (cleanup)" else ""
        appendLine("${INDENT}bb$index$cleanup: {")
        block.statements.forEach { stmt ->
            val statement = when (stmt) {
                is MirStatement.Assign -> {
                    "$INDENT${INDENT}_${localIndex(stmt.place.local)} = ${format(stmt.rvalue)};"
                }
                is MirStatement.StorageLive -> "$INDENT${INDENT}StorageLive(_${localIndex(stmt.local)});"
                is MirStatement.StorageDead -> "$INDENT${INDENT}StorageDead(_${localIndex(stmt.local)});"
            }
            appendLine(statement.withComment(" // ${createComment(stmt.source)}"))
        }
        when (val terminator = block.terminator) {
            is MirTerminator.Return -> {
                val comment = createComment(block.terminator.source)
                appendLine("$INDENT${INDENT}return;".withComment(" // $comment"))
            }
            is MirTerminator.Assert -> {
                val neg = if (terminator.expected) "" else "!"
                val successIndex = blockIndex(terminator.target)
                val unwindIndex = terminator.unwind?.let { blockIndex(it) }
                val targets = if (unwindIndex == null) "bb$successIndex" else "[success: bb$successIndex, unwind: bb$unwindIndex]"
                val assert = "$INDENT${INDENT}assert(${neg}${format(terminator.cond)}${format(terminator.msg)}) -> $targets;"
                appendLine(assert.withComment(" // ${createComment(block.terminator.source)}"))
            }
            is MirTerminator.Goto -> {
                val comment = createComment(block.terminator.source)
                appendLine("$INDENT${INDENT}goto -> bb${blockIndex(terminator.target)};".withComment(" // $comment"))
            }
            is MirTerminator.SwitchInt -> {
                val comment = createComment(block.terminator.source)
                val cases = buildString {
                    append("[")
                    // TODO: hardcoded as hell
                    append("false: bb${blockIndex(terminator.targets.targets[0])}")
                    append(", ")
                    append("otherwise: bb${blockIndex(terminator.targets.targets[1])}")
                    append("]")
                }
                val switch = "$INDENT${INDENT}switchInt(${format(terminator.discriminant)}) -> $cases;"
                appendLine(switch.withComment(" // $comment"))
            }
            is MirTerminator.Resume -> {
                val comment = createComment(block.terminator.source)
                appendLine("$INDENT${INDENT}resume;".withComment(" // $comment"))
            }
        }
        appendLine("$INDENT}")
    }

    private fun format(msg: MirAssertKind): String {
        return when (msg) {
            is MirAssertKind.OverflowNeg -> ", \"attempt to negate `{}`, which would overflow\", ${format(msg.arg)}"
            is MirAssertKind.Overflow -> {
                val op = when (msg.op) {
                    ArithmeticOp.SHL -> return ", \"attempt to shift left by `{}`, which would overflow\", ${format(msg.right)}"
                    ArithmeticOp.SHR -> return ", \"attempt to shift right by `{}`, which would overflow\", ${format(msg.right)}"
                    ArithmeticOp.REM -> return ", \"attempt to compute the remainder of `{} % {}`, which would overflow\", ${format(msg.left)}, ${format(msg.right)}"
                    ArithmeticOp.BIT_AND -> throw IllegalStateException("${msg.op} can't overflow")
                    else -> msg.op.sign
                }
                ", \"attempt to compute `{} $op {}`, which would overflow\", ${format(msg.left)}, ${format(msg.right)}"
            }
            is MirAssertKind.DivisionByZero -> ", \"attempt to divide `{}` by zero\", ${format(msg.arg)}"
            is MirAssertKind.ReminderByZero -> ", \"attempt to calculate the remainder of `{}` with a divisor of zero\", ${format(msg.arg)}"
        }
    }

    private fun format(rvalue: MirRvalue): String {
        return when (rvalue) {
            is MirRvalue.BinaryOpUse -> {
                val opName = when (val op = rvalue.op) {
                    is ArithmeticOp -> op.traitName
                    is EqualityOp.EQ -> "Eq"
                    else -> TODO()
                }
                "$opName(${format(rvalue.left)}, ${format(rvalue.right)})"
            }
            is MirRvalue.UnaryOpUse -> "${rvalue.op.formatted}(${format(rvalue.operand)})"
            is MirRvalue.Use -> format(rvalue.operand)
            is MirRvalue.CheckedBinaryOpUse -> {
                val funName = when (val op = rvalue.op) {
                    is ArithmeticOp -> "Checked${op.traitName}"
                    else -> throw IllegalStateException("$op can't be checked")
                }
                "$funName(${format(rvalue.left)}, ${format(rvalue.right)})"
            }
            is MirRvalue.Aggregate.Tuple -> when (rvalue.operands.size) {
                0 -> "()"
                1 -> "(${format(rvalue.operands.single())},)"
                else -> StringJoiner(", ", "(", ")").run {
                    rvalue.operands.forEach {
                        add(format(it))
                    }
                    toString()
                }
            }
        }
    }

    private fun format(operand: MirOperand): String {
        return when (operand) {
            is MirOperand.Constant -> "const ${format(operand.constant)}"
            is MirOperand.Move -> "move ${format(operand.place)}"
            is MirOperand.Copy -> format(operand.place)
        }
    }

    private fun format(place: MirPlace): String {
        val index = localIndex(place.local)
        if (place.projections.isEmpty()) return "_$index"
        check(place.projections.size == 1) // TODO
        return when (val projection = place.projections.single()) {
            is MirProjectionElem.Field -> "(_$index.${projection.fieldIndex}: ${projection.elem})"
        }
    }

    private fun format(constant: MirConstant): String {
        return when (constant) {
            is MirConstant.Value -> {
                val value = value(constant.constValue)
                val type = constant.ty as TyPrimitive
                when (type) {
                    is TyInteger -> if (type.minValue.toString() == value) {
                        "${type.name}::MIN"
                    } else {
                        "${value}_${type.name}"
                    }
                    is TyBool -> when (value) {
                        "0" -> "false"
                        "1" -> "true"
                        else -> TODO()
                    }
                    else -> TODO()
                }
            }
        }
    }

    private fun value(constValue: MirConstValue): String {
        return when (constValue) {
            is MirConstValue.Scalar -> when (val value = constValue.value) {
                is MirScalarValue.Int -> value.scalarInt.data.toString()
            }
        }
    }

    private fun StringBuilder.printIntro(source: MirSourceInfo): StringBuilder = apply {
        val reference = when (source) {
            is MirSourceInfo.End -> source.reference
            MirSourceInfo.Fake -> error("can't print fake source info")
            is MirSourceInfo.Full -> source.reference
        }
        when (reference) {
            is RsConstant -> {
                when {
                    reference.const != null -> append("const ")
                    reference.static != null && reference.mut != null -> append("static mut ")
                    reference.static != null -> append("static ")
                    else -> throw IllegalStateException("Unexpected RsConstant")
                }
                append(reference.name ?: TODO())
                val type = reference.expr?.type as Ty // TODO: later extend for not primitive types
                append(": ${format(type)} = ")
            }
        }
    }

    private fun format(ty: Ty): String {
        return when (ty) {
            is TyUnit -> "()"
            is TyPrimitive -> ty.name
            is TyTuple -> if (ty.types.size == 1) {
                "(${format(ty.types.single())},)"
            } else {
                StringJoiner(", ", "(", ")").run {
                    ty.types.forEach {
                        add(format(it))
                    }
                    toString()
                }
            }
            else -> TODO()
        }
    }

    // just local declarations for now
    private fun StringBuilder.printScopeTree(mir: MirBody, depth: Int): StringBuilder = apply {
        val indent = INDENT.repeat(depth)
        mir.localDecls.withIndex().forEach { (index, local) ->
            val mut = when (local.mutability) {
                Mutability.MUTABLE -> "mut "
                Mutability.IMMUTABLE -> ""
            }
            val definition = "${indent}let ${mut}_$index: ${format(local.ty)};"
            val localName = if (index == 0) " return place" else ""
            val comment = createComment(local.source)
            appendLine(definition.withComment(" //$localName in $comment"))
        }
    }

    private fun String.withComment(comment: String): String {
        return "$this${" ".repeat(max(ALIGN - length, 0))}$comment"
    }

    private fun createComment(source: MirSourceInfo): String {
        val reference = when (source) {
            is MirSourceInfo.End -> source.reference
            MirSourceInfo.Fake -> error("can't print fake source info")
            is MirSourceInfo.Full -> source.reference
        }
        val scope = 0 // TODO
        val fileName = reference.contextualFile.originalFile.name
        val startOffset = reference.startOffset
        val endOffset = reference.endOffset
        val startLine = reference.contextualFile.originalFile.document?.getLineNumber(startOffset)!!
        val endLine = reference.contextualFile.originalFile.document?.getLineNumber(endOffset)!!
        val startLineOffset = startOffset - reference.contextualFile.originalFile.document?.getLineStartOffset(startLine)!!
        val endLineOffset = endOffset - reference.contextualFile.originalFile.document?.getLineStartOffset(endLine)!!
        return when (source) {
            is MirSourceInfo.Full -> {
                "scope $scope at $filenamePrefix$fileName:${startLine + 1}:${startLineOffset + 1}: " +
                    "${endLine + 1}:${endLineOffset + 1}"
            }
            is MirSourceInfo.End -> {
                "scope $scope at $filenamePrefix$fileName:${endLine + 1}:${endLineOffset}: " +
                    "${endLine + 1}:${endLineOffset + 1}"
            }
            MirSourceInfo.Fake -> error("can't print fake source info")
        }
    }

    private val UnaryOperator.formatted: String get() = when (this) {
        UnaryOperator.MINUS -> "Neg"
        UnaryOperator.NOT -> "Not"
        else -> TODO()
    }

    companion object {
        private const val INDENT = "    "
        private const val ALIGN = 40
    }
}
