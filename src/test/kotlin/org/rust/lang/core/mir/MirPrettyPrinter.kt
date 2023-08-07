/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir

import com.intellij.psi.PsiElement
import org.rust.ide.presentation.render
import org.rust.lang.core.mir.schemas.*
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.KnownItems
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.thir.variant
import org.rust.lang.core.types.ty.*
import org.rust.openapiext.document
import kotlin.math.max

internal class MirPrettyPrinter(
    private val filenamePrefix: String = "src/",
    private val mir: MirBody,
    private val commentSupplier: CommentSupplier = ScopeCommentSupplier(filenamePrefix)
) {
    private val knownItems: KnownItems get() = mir.sourceElement.knownItems

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
                    "$INDENT${INDENT}${format(stmt.place)} = ${format(stmt.rvalue)};"
                }
                is MirStatement.StorageLive -> "$INDENT${INDENT}StorageLive(${format(stmt.local)});"
                is MirStatement.StorageDead -> "$INDENT${INDENT}StorageDead(${format(stmt.local)});"
                is MirStatement.FakeRead -> "$INDENT${INDENT}FakeRead(${format(stmt.cause)}, ${format(stmt.place.local)});"
            }
            appendLine(statement.withComment(commentSupplier.statementComment(stmt)))
        }

        appendLine(format(block.terminator).withComment(commentSupplier.terminatorComment(block.terminator)))
        appendLine("$INDENT}".withComment(commentSupplier.blockEndComment(block)))
    }

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_middle/src/mir/terminator.rs#L267
    private fun format(terminator: MirTerminator<MirBasicBlock>): String = buildString {
        append("$INDENT${INDENT}")
        append(formatHead(terminator))

        val successors = terminator.successors
        val labels = formatSuccessorLabels(terminator)
        check(successors.size == labels.size)
        when (successors.size) {
            0 -> Unit
            1 -> append(" -> bb${successors.single().index}")
            else -> {
                val targets = (labels zip successors).joinToString(", ") { (label, successor) ->
                    "$label: bb${successor.index}"
                }
                append(" -> [$targets]")
            }
        }
        append(';')
    }

    private fun formatHead(terminator: MirTerminator<MirBasicBlock>): String {
        return when (terminator) {
            is MirTerminator.Goto -> "goto"
            is MirTerminator.SwitchInt -> "switchInt(${format(terminator.discriminant)})"
            is MirTerminator.Return -> "return"
            is MirTerminator.Resume -> "resume"
            is MirTerminator.Unreachable -> "unreachable"
            is MirTerminator.Drop -> "drop(${format(terminator.place)})"
            is MirTerminator.Call -> {
                val args = terminator.args.joinToString(separator = ", ") { format(it) }
                "${format(terminator.destination)} = ${format(terminator.callee)}($args)"
            }
            is MirTerminator.Assert -> {
                val neg = if (terminator.expected) "" else "!"
                "assert($neg${format(terminator.cond)}${format(terminator.msg)})"
            }
            is MirTerminator.FalseEdge -> "falseEdge"
            is MirTerminator.FalseUnwind -> "falseUnwind"
        }
    }

    private fun formatSuccessorLabels(terminator: MirTerminator<MirBasicBlock>): List<String> {
        return when (terminator) {
            is MirTerminator.Return,
            is MirTerminator.Resume,
            is MirTerminator.Unreachable -> listOf()
            is MirTerminator.Goto -> listOf("")
            is MirTerminator.SwitchInt -> terminator.targets.values.map { it.toString() } + "otherwise"
            is MirTerminator.Call -> listOfNotNull(
                "return".takeIf { terminator.target != null },
                "unwind".takeIf { terminator.unwind != null },
            )
            is MirTerminator.Drop -> listOfNotNull(
                "return",
                "unwind".takeIf { terminator.unwind != null },
            )
            is MirTerminator.Assert -> listOfNotNull(
                "success",
                "unwind".takeIf { terminator.unwind != null },
            )
            is MirTerminator.FalseEdge -> listOf("real", "imaginary")
            is MirTerminator.FalseUnwind -> listOfNotNull(
                "real",
                "unwind".takeIf { terminator.unwind != null },
            )
        }
    }

    private fun format(local: MirLocal): String {
        return "_${local.index}"
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
            is MirAssertKind.BoundsCheck -> ", \"index out of bounds: the length is {} but the index is {}\", ${format(msg.len)}, ${format(msg.index)}"
        }
    }

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_middle/src/mir/mod.rs#L2024
    private fun format(rvalue: MirRvalue): String {
        return when (rvalue) {
            is MirRvalue.BinaryOpUse -> {
                val opName = when (val op = rvalue.op) {
                    is MirBinaryOperator.Arithmetic -> op.op.traitName
                    is MirBinaryOperator.Equality -> when (op.op) {
                        EqualityOp.EQ -> "Eq"
                        EqualityOp.EXCLEQ -> "Ne"
                    }
                    is MirBinaryOperator.Comparison -> when (op.op) {
                        ComparisonOp.LT -> "Lt"
                        ComparisonOp.GT -> "Gt"
                        ComparisonOp.GTEQ -> "Ge"
                        ComparisonOp.LTEQ -> "Le"
                    }
                    MirBinaryOperator.Offset -> TODO()
                }
                "$opName(${format(rvalue.left)}, ${format(rvalue.right)})"
            }
            is MirRvalue.UnaryOpUse -> "${rvalue.op.formatted}(${format(rvalue.operand)})"
            is MirRvalue.Discriminant -> "discriminant(${format(rvalue.place)})"
            is MirRvalue.NullaryOpUse -> TODO()
            is MirRvalue.ThreadLocalRef -> TODO()
            is MirRvalue.Use -> format(rvalue.operand)
            is MirRvalue.CheckedBinaryOpUse -> {
                val funName = when (val op = rvalue.op) {
                    is MirBinaryOperator.Arithmetic -> "Checked${op.op.traitName}"
                    else -> throw IllegalStateException("$op can't be checked")
                }
                "$funName(${format(rvalue.left)}, ${format(rvalue.right)})"
            }
            is MirRvalue.CopyForDeref -> TODO()
            is MirRvalue.AddressOf -> TODO()
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
            is MirRvalue.Aggregate.Adt -> {
                val definition = rvalue.definition.variant(rvalue.variantIndex)
                val name = when (definition) {
                    is RsStructItem -> {
                        val name = definition.name!!
                        val langAttributes = definition.getTraversedRawAttributes().langAttributes.toList()
                        if ("Range" in langAttributes || "RangeInclusive" in langAttributes) {
                            val typeArguments = (rvalue.ty as? TyAdt)
                                ?.typeArguments
                                ?.joinToString(prefix = "::<", postfix = ">")
                                .orEmpty()
                            "std::ops::$name$typeArguments"
                        } else {
                            name
                        }
                    }
                    is RsEnumVariant -> "${definition.parentEnum.name!!}::${definition.name!!}"
                    else -> error("unreachable")
                }
                when {
                    definition.isFieldless -> name
                    definition.tupleFields != null -> {
                        rvalue.operands.joinToString(", ", "$name(", ")") { format(it) }
                    }
                    else -> {
                        check(definition.fields.size == rvalue.operands.size)
                        val fields = (definition.fields zip rvalue.operands)
                            .joinToString { (fieldDeclaration, fieldValue) ->
                                "${fieldDeclaration.name}: ${format(fieldValue)}"
                            }
                        "$name { $fields }"
                    }
                }
            }
            is MirRvalue.Ref -> "&${if (rvalue.borrowKind == MirBorrowKind.Shared) "" else "mut "}${format(rvalue.place)}"
            is MirRvalue.Len -> "Len(${format(rvalue.place)})"
            is MirRvalue.Cast.IntToInt -> "${format(rvalue.operand)} as ${format(rvalue.ty)} (IntToInt)"
        }
    }

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_middle/src/mir/mod.rs#L1860
    private fun format(operand: MirOperand): String {
        return when (operand) {
            is MirOperand.Constant -> {
                val constant = operand.constant
                val formattedConst = format(constant)
                if (constant is MirConstant.Value && constant.ty is TyFunctionBase) {
                    formattedConst
                } else {
                    "const $formattedConst"
                }
            }
            is MirOperand.Move -> "move ${format(operand.place)}"
            is MirOperand.Copy -> format(operand.place)
        }
    }

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_middle/src/mir/mod.rs#L1714
    private fun format(place: MirPlace): String {
        val index = place.local.index

        return buildString {
            for (projection in place.projections.asReversed()) {
                when (projection) {
                    is MirProjectionElem.Downcast,
                    is MirProjectionElem.Field -> append("(")
                    is MirProjectionElem.Deref -> append("(*")
                    is MirProjectionElem.Index,
                    is MirProjectionElem.ConstantIndex -> Unit
                }
            }

            append("_$index")

            for (projection in place.projections) {
                when (projection) {
                    is MirProjectionElem.Downcast -> {
                        val name = projection.name ?: "variant#${projection.variantIndex}"
                        append(" as $name)")
                    }
                    is MirProjectionElem.Field -> append(".${projection.fieldIndex}: ${projection.elem})")
                    is MirProjectionElem.Deref -> append(")")
                    is MirProjectionElem.Index -> append("[_${projection.index.index}]")
                    is MirProjectionElem.ConstantIndex -> append("[${projection.offset} of ${projection.minLength}]")
                }
            }
        }
    }

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_middle/src/mir/mod.rs#L2790
    private fun format(constant: MirConstant): String {
        return when (constant) {
            is MirConstant.Value -> format(constant)
            is MirConstant.Unevaluated -> "_"
        }
    }

    private fun format(constant: MirConstant.Value): String {
        return when (constant.constValue) {
            is MirConstValue.Scalar -> {
                val value = when (val value = (constant.constValue as MirConstValue.Scalar).value) {
                    is MirScalar.Int -> value.scalarInt.data.toString()
                }
                when (val type = constant.ty as TyPrimitive) {
                    is TyInteger -> if (type.isSigned && type.minValue.toString() == value) {
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
            is MirConstValue.ZeroSized -> {
                when (val ty = constant.ty) {
                    is TyUnit -> "()"
                    is TyFunctionDef -> ty.def.name ?: ""
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
                val prefix = reference
                    .parent
                    ?.parent
                    ?.let { it as? RsImplItem }
                    ?.let {
                        val impl = it.impl
                        val name = it.typeReference ?: error("Could not find type reference of impl")
                        val fileName = it.contextualFile.originalFile.name
                        val location = LocationRange(
                            fileName = "$filenamePrefix$fileName",
                            start = getStartLocation(impl),
                            end = getEndLocation(name)
                        )
                        "<impl at $location>::"
                    }
                append("${prefix.orEmpty()}${reference.name}")
                append("(")
                mir.args.forEachIndexed { index, arg ->
                    if (index != 0) append(", ")
                    append("${format(arg)}: ${format(arg.ty)}")
                }
                append(") -> ${format(mir.returnLocal.ty)} ")
            }
            else -> TODO("Unsupported type ${reference::class}")
        }
    }

    private fun format(cause: MirStatement.FakeRead.Cause): String {
        return when (cause) {
            is MirStatement.FakeRead.Cause.ForMatchedPlace -> "ForMatchedPlace(None)".also { assert(cause.element == null) }
            is MirStatement.FakeRead.Cause.ForLet -> "ForLet(None)".also { assert(cause.element == null) }
            MirStatement.FakeRead.Cause.ForIndex -> "ForIndex"
        }
    }

    private fun format(ty: Ty): String {
        val useQualifiedName = setOfNotNull(knownItems.Range, knownItems.RangeInclusive)
        return ty.render(context = mir.sourceElement, useQualifiedName = useQualifiedName)
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
            if (local.index in (1..mir.argCount)) continue
            if (local.source.scope != parent) continue
            val mut = when (local.mutability) {
                Mutability.MUTABLE -> "mut "
                Mutability.IMMUTABLE -> ""
            }
            val definition = "${indent}let ${mut}${format(local)}: ${format(local.ty)};"
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

    private data class Location(val line: Int, val lineOffset: Int) {
        init {
            assert(lineOffset >= 0)
        }
        val previous get() = Location(line, lineOffset - 1)
        override fun toString() = "${line + 1}:${lineOffset + 1}"
    }

    private data class LocationRange(val fileName: String, val start: Location, val end: Location) {
        override fun toString() = "$fileName:$start: $end"
    }

    companion object {
        private const val INDENT = "    "
        private const val ALIGN = 40

        private fun createComment(filenamePrefix: String, source: MirSourceInfo): String {
            val scope = source.scope.index
            val scopeAt = "scope $scope at"
            val location = getLocationRange(filenamePrefix, source.span.reference)
            return when (source.span) {
                is MirSpan.Full -> {
                    val ref = source.span.reference
                    val adjustedLocation = if (ref is RsSelfParameter && ref.colon != null) {
                        LocationRange(location.fileName, location.start, getEndLocation(ref.self))
                    } else {
                        location
                    }
                    "$scopeAt $adjustedLocation"
                }
                is MirSpan.EndPoint -> "$scopeAt ${LocationRange(location.fileName, location.end.previous, location.end)}"
                is MirSpan.End -> "$scopeAt ${LocationRange(location.fileName, location.end, location.end)}"
                is MirSpan.Start -> "$scopeAt ${LocationRange(location.fileName, location.start, location.start)}"
                MirSpan.Fake -> error("can't print fake source info")
            }
        }

        private fun getLocationRange(filePrefix: String, element: PsiElement): LocationRange {
            val fileName = element.contextualFile.originalFile.name
            return LocationRange(
                fileName = "$filePrefix$fileName",
                start = getStartLocation(element),
                end = getEndLocation(element),
            )
        }

        private fun getStartLocation(element: PsiElement): Location {
            val startOffset = element.startOffset
            val startLine = element.contextualFile.originalFile.document?.getLineNumber(startOffset)!!
            val startLineOffset = startOffset - element.contextualFile.originalFile.document?.getLineStartOffset(startLine)!!
            return Location(startLine, startLineOffset)
        }

        private fun getEndLocation(element: PsiElement): Location {
            val endOffset = element.endOffset
            val endLine = element.contextualFile.originalFile.document?.getLineNumber(endOffset)!!
            val endLineOffset = endOffset - element.contextualFile.originalFile.document?.getLineStartOffset(endLine)!!
            return Location(endLine, endLineOffset)
        }
    }
}
