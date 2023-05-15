/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir

import org.rust.lang.core.mir.schemas.*
import org.rust.lang.core.psi.RsConstant
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.*
import org.rust.openapiext.document
import kotlin.math.max

internal class MirPrettyPrinter(
    private val filenamePrefix: String = "src/",
    private val mir: MirBody,
    private val commentSupplier: CommentSupplier = ScopeCommentSupplier(filenamePrefix)
) {
    fun print(): String {
        return buildString { printMir(mir) }
    }

    private fun StringBuilder.printMir(mir: MirBody): StringBuilder = apply {
        printIntro()
        for (block in mir.basicBlocks) {
            appendLine()
            printBasicBlock(block)
        }
        append("}")
    }

    private fun StringBuilder.printBasicBlock(block: MirBasicBlock): StringBuilder = apply {
        val cleanup = if (block.unwind) " (cleanup)" else ""
        val blockHeader = "${INDENT}bb${block.index}$cleanup: {"
        appendLine(blockHeader.withComment(commentSupplier.blockStartComment(block)))
        block.statements.forEach { stmt ->
            val statement = when (stmt) {
                is MirStatement.Assign -> {
                    "$INDENT${INDENT}_${stmt.place.local.index} = ${format(stmt.rvalue)};"
                }
                is MirStatement.StorageLive -> "$INDENT${INDENT}StorageLive(_${stmt.local.index});"
                is MirStatement.StorageDead -> "$INDENT${INDENT}StorageDead(_${stmt.local.index});"
                is MirStatement.FakeRead -> "$INDENT${INDENT}FakeRead(${format(stmt.cause)}, _${stmt.place.local.index});"
            }
            appendLine(statement.withComment(commentSupplier.statementComment(stmt)))
        }

        val comment = commentSupplier.terminatorComment(block.terminator)
        when (val terminator = block.terminator) {
            is MirTerminator.Return -> {
                appendLine("$INDENT${INDENT}return;".withComment(comment))
            }
            is MirTerminator.Assert -> {
                val neg = if (terminator.expected) "" else "!"
                val successIndex = terminator.target.index
                val unwindIndex = terminator.unwind?.index
                val targets = if (unwindIndex == null) "bb$successIndex" else "[success: bb$successIndex, unwind: bb$unwindIndex]"
                val assert = "$INDENT${INDENT}assert(${neg}${format(terminator.cond)}${format(terminator.msg)}) -> $targets;"
                appendLine(assert.withComment(comment))
            }
            is MirTerminator.Goto -> {
                appendLine("$INDENT${INDENT}goto -> bb${terminator.target.index};".withComment(comment))
            }
            is MirTerminator.SwitchInt -> {
                val cases = buildString {
                    append("[")
                    // TODO: hardcoded as hell
                    append("false: bb${terminator.targets.targets[0].index}")
                    append(", ")
                    append("otherwise: bb${terminator.targets.targets[1].index}")
                    append("]")
                }
                val switch = "$INDENT${INDENT}switchInt(${format(terminator.discriminant)}) -> $cases;"
                appendLine(switch.withComment(comment))
            }
            is MirTerminator.Resume -> {
                appendLine("$INDENT${INDENT}resume;".withComment(comment))
            }
            is MirTerminator.FalseUnwind -> {
                val cases = buildString {
                    append("[")
                    append("real: bb${terminator.realTarget.index}")
                    append(", ")
                    append("cleanup: bb${terminator.unwind!!.index}")
                    append("]")
                }
                appendLine("$INDENT${INDENT}falseUnwind -> $cases;".withComment(comment))
            }
            is MirTerminator.Unreachable -> {
                appendLine("$INDENT${INDENT}unreachable;".withComment(comment))
            }
        }
        appendLine("$INDENT}".withComment(commentSupplier.blockEndComment(block)))
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
                    is MirBinaryOperator.Arithmetic -> op.op.traitName
                    is MirBinaryOperator.Equality -> when (op.op) {
                        EqualityOp.EQ -> "Eq"
                        EqualityOp.EXCLEQ -> TODO()
                    }
                    else -> TODO()
                }
                "$opName(${format(rvalue.left)}, ${format(rvalue.right)})"
            }
            is MirRvalue.UnaryOpUse -> "${rvalue.op.formatted}(${format(rvalue.operand)})"
            is MirRvalue.Use -> format(rvalue.operand)
            is MirRvalue.CheckedBinaryOpUse -> {
                val funName = when (val op = rvalue.op) {
                    is MirBinaryOperator.Arithmetic -> "Checked${op.op.traitName}"
                    else -> throw IllegalStateException("$op can't be checked")
                }
                "$funName(${format(rvalue.left)}, ${format(rvalue.right)})"
            }
            is MirRvalue.Repeat -> {
                val value = format(rvalue.operand)
                val count = rvalue.count
                "[$value; $count]"
            }
            is MirRvalue.Aggregate.Array -> when (rvalue.operands.size) {
                0 -> "[]"
                1 -> "[${format(rvalue.operands.single())}]"
                else -> rvalue.operands.joinToString(", ", "[", "]") { format(it) }
            }
            is MirRvalue.Aggregate.Tuple -> when (rvalue.operands.size) {
                0 -> "()"
                1 -> "(${format(rvalue.operands.single())},)"
                else -> rvalue.operands.joinToString(", ", "(", ")") { format(it) }
            }
            is MirRvalue.Aggregate.Adt -> rvalue.definition.name ?: TODO()
            is MirRvalue.Ref -> "&${if (rvalue.borrowKind == MirBorrowKind.Shared) "" else "mut "}${format(rvalue.place)}"
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
        val index = place.local.index

        return buildString {
            for (projection in place.projections.asReversed()) {
                when (projection) {
                    is MirProjectionElem.Field -> append("(")
                    is MirProjectionElem.Deref -> TODO()
                }
            }

            append("_$index")

            for (projection in place.projections) {
                when (projection) {
                    is MirProjectionElem.Field -> append(".${projection.fieldIndex}: ${projection.elem})")
                    is MirProjectionElem.Deref -> TODO()
                }
            }
        }
    }

    private fun format(constant: MirConstant): String {
        return when {
            constant is MirConstant.Value && constant.constValue is MirConstValue.Scalar -> {
                val value = when (val value = (constant.constValue as MirConstValue.Scalar).value) {
                    is MirScalar.Int -> value.scalarInt.data.toString()
                }
                when (val type = constant.ty as TyPrimitive) {
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
            constant is MirConstant.Value && constant.constValue is MirConstValue.ZeroSized -> {
                when (constant.ty) {
                    is TyUnit -> "()"
                    else -> TODO()
                }
            }
            else -> TODO()
        }
    }

    private fun StringBuilder.printIntro(): StringBuilder = apply {
        printMirSignature()
        appendLine("{")
        printScopeTree(mir.sourceScopesTree, mir.outermostScope, 1)
    }

    private fun StringBuilder.printMirSignature(): StringBuilder = apply {
        when (val reference = mir.sourceElement) {
            is RsConstant -> {
                when {
                    reference.const != null -> append("const ")
                    reference.static != null && reference.mut != null -> append("static mut ")
                    reference.static != null -> append("static ")
                    else -> throw IllegalStateException("Unexpected RsConstant")
                }
                append(reference.name ?: TODO())
                append(": ${format(mir.returnLocal.ty)} = ")
            }
            is RsFunction -> {
                append("fn ")
                append(reference.name)
                append("(")
                // TODO: arguments
                append(") -> ${format(mir.returnLocal.ty)} ")
            }
            else -> TODO("Unsupported type ${reference::class}")
        }
    }

    private fun format(cause: MirStatement.FakeRead.Cause): String {
        return when (cause) {
            is MirStatement.FakeRead.Cause.ForLet -> "ForLet(None)".also { assert(cause.element == null) }
        }
    }

    private fun format(ty: Ty): String {
        return when (ty) {
            is TyUnit -> "()"
            TyNever -> "!"
            is TyPrimitive -> ty.name
            is TyArray -> if (ty.size == null) {
                "[${format(ty.base)}]"
            } else {
                "[${format(ty.base)}; ${ty.size}]"
            }
            is TyTuple -> if (ty.types.size == 1) {
                "(${format(ty.types.single())},)"
            } else {
                ty.types.joinToString(", ", "(", ")") { format(it) }
            }
            is TyAdt -> ty.item.name ?: TODO()
            is TyReference -> "&${if (ty.mutability == Mutability.MUTABLE) "mut " else ""}${format(ty.referenced)}"
            else -> TODO()
        }
    }

    // just local declarations for now
    private fun StringBuilder.printScopeTree(
        scopeTree: Map<MirSourceScope, List<MirSourceScope>>,
        parent: MirSourceScope,
        depth: Int,
    ): StringBuilder = apply {
        val indent = INDENT.repeat(depth)
        for (varDebugInfo in mir.varDebugInfo) {
            if (varDebugInfo.source.scope != parent) continue
            val debugInfo = "${indent}debug ${varDebugInfo.name} => ${format(varDebugInfo.contents)};"
            appendLine(debugInfo.withComment("in ${createComment(varDebugInfo.source)}"))
        }
        for (local in mir.localDecls) {
            if (local.source.scope != parent) continue
            val mut = when (local.mutability) {
                Mutability.MUTABLE -> "mut "
                Mutability.IMMUTABLE -> ""
            }
            val definition = "${indent}let ${mut}_${local.index}: ${format(local.ty)};"
            val localName = if (local.index == 0) " return place" else ""
            val comment = createComment(local.source)
            appendLine(definition.withCommentAsIs(" //$localName in $comment"))
        }
        val children = scopeTree[parent] ?: return@apply
        children.forEach { child ->
            appendLine("${indent}scope ${child.index} {")
            printScopeTree(scopeTree, child, depth + 1)
            appendLine("$indent}")
        }
    }

    private fun format(contents: MirVarDebugInfo.Contents): String {
        return when (contents) {
            is MirVarDebugInfo.Contents.Composite -> TODO()
            is MirVarDebugInfo.Contents.Constant -> format(contents.constant)
            is MirVarDebugInfo.Contents.Place -> format(contents.place)
        }
    }

    private fun String.withComment(comment: String?): String {
        return if (comment != null) {
            withCommentAsIs(" // $comment")
        } else {
            this
        }
    }

    private fun String.withCommentAsIs(comment: String): String {
        return "$this${" ".repeat(max(ALIGN - length, 0))}$comment"
    }

    private fun createComment(source: MirSourceInfo): String {
        return createComment(filenamePrefix, source)
    }

    private val UnaryOperator.formatted: String get() = when (this) {
        UnaryOperator.MINUS -> "Neg"
        UnaryOperator.NOT -> "Not"
        else -> TODO()
    }

    interface CommentSupplier {
        fun blockStartComment(block: MirBasicBlock): String? = null
        fun blockEndComment(block: MirBasicBlock): String? = null
        fun statementComment(stmt: MirStatement): String? = null
        fun terminatorComment(terminator: MirTerminator<*>): String? = null
    }

    class ScopeCommentSupplier(private val filenamePrefix: String) : CommentSupplier {
        override fun statementComment(stmt: MirStatement): String {
            return createComment(filenamePrefix, stmt.source)
        }

        override fun terminatorComment(terminator: MirTerminator<*>): String {
            return createComment(filenamePrefix, terminator.source)
        }
    }

    companion object {
        private const val INDENT = "    "
        private const val ALIGN = 40

        private fun createComment(filenamePrefix: String, source: MirSourceInfo): String {
            val scope = source.scope.index
            val fileName = source.span.reference.contextualFile.originalFile.name
            val startOffset = source.span.reference.startOffset
            val endOffset = source.span.reference.endOffset
            val startLine = source.span.reference.contextualFile.originalFile.document?.getLineNumber(startOffset)!!
            val endLine = source.span.reference.contextualFile.originalFile.document?.getLineNumber(endOffset)!!
            val startLineOffset = startOffset - source.span.reference.contextualFile.originalFile.document?.getLineStartOffset(startLine)!!
            val endLineOffset = endOffset - source.span.reference.contextualFile.originalFile.document?.getLineStartOffset(endLine)!!
            return when (source.span) {
                is MirSpan.Full -> {
                    "scope $scope at $filenamePrefix$fileName:${startLine + 1}:${startLineOffset + 1}: " +
                        "${endLine + 1}:${endLineOffset + 1}"
                }

                is MirSpan.EndPoint -> {
                    "scope $scope at $filenamePrefix$fileName:${endLine + 1}:${endLineOffset}: " +
                        "${endLine + 1}:${endLineOffset + 1}"
                }

                is MirSpan.End -> {
                    "scope $scope at $filenamePrefix$fileName:${endLine + 1}:${endLineOffset + 1}: " +
                        "${endLine + 1}:${endLineOffset + 1}"
                }

                is MirSpan.Start -> {
                    "scope $scope at $filenamePrefix$fileName:${startLine + 1}:${startLineOffset + 1}: " +
                        "${startLine + 1}:${startLineOffset + 1}"
                }

                MirSpan.Fake -> error("can't print fake source info")
            }
        }
    }
}
