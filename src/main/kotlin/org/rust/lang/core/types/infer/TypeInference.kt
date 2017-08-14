/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import com.intellij.psi.PsiElement
import org.rust.ide.utils.isNullOrEmpty
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.StdKnownItems
import org.rust.lang.core.resolve.ref.resolveFieldLookupReferenceWithReceiverType
import org.rust.lang.core.resolve.ref.resolveMethodCallReferenceWithReceiverType
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.ty.Mutability.IMMUTABLE
import org.rust.lang.core.types.ty.Mutability.MUTABLE
import org.rust.lang.core.types.type
import org.rust.utils.forEachChild

fun inferTypesIn(fn: RsFunction): RsInferenceContext =
    RsInferenceContext().apply { preventRecursion { infer(fn) } }

class RsInferenceContext {
    private val bindings: MutableMap<RsPatBinding, Ty> = HashMap()
    private val exprTypes: MutableMap<RsExpr, Ty> = HashMap()

    fun infer(fn: RsFunction) {
        extractParameterBindings(fn)

        val block = fn.block
        if (block != null) {
            val items = StdKnownItems.relativeTo(fn)
            RsFnInferenceContext(this, ImplLookup(fn.project, items), items).inferBlockType(block)
        }
    }

    private fun extractParameterBindings(fn: RsFunction) {
        for (param in fn.valueParameters) {
            extractBindings(param.pat, param.typeReference?.type ?: TyUnknown)
        }
    }

    fun getExprType(expr: RsExpr): Ty {
        return exprTypes[expr] ?: TyUnknown
    }

    fun isTypeInferred(expr: RsExpr): Boolean {
        return exprTypes.containsKey(expr)
    }

    fun getBindingType(binding: RsPatBinding): Ty {
        return bindings[binding] ?: TyUnknown
    }

    fun writeTy(psi: RsExpr, ty: Ty) {
        exprTypes[psi] = ty
    }

    fun extractBindings(pattern: RsPat?, baseType: Ty) {
        if (pattern != null) bindings += collectBindings(pattern, baseType)
    }

    override fun toString(): String {
        return "RsInferenceContext(bindings=$bindings, exprTypes=$exprTypes)"
    }
}

private class RsFnInferenceContext(
    private val ctx: RsInferenceContext,
    private val lookup: ImplLookup,
    private val items: StdKnownItems
) {
    private val RsStructLiteralField.type: Ty get() = resolveToDeclaration?.typeReference?.type ?: TyUnknown

    fun inferBlockType(block: RsBlock): Ty =
        block.inferType()

    private fun RsBlock.inferType(expected: Ty? = null): Ty {
        for (stmt in stmtList) {
            processStatement(stmt)
        }

        return expr?.inferType(expected) ?: TyUnit
    }

    private fun processStatement(psi: RsStmt) {
        when (psi) {
            is RsLetDecl -> {
                val explicitTy = psi.typeReference?.type
                val inferredTy = psi.expr?.inferType(explicitTy) ?: TyUnknown
                ctx.extractBindings(psi.pat, explicitTy ?: inferredTy)
            }
            is RsExprStmt -> psi.expr.inferType()
        }
    }

    private fun RsExpr.inferType(expected: Ty? = null): Ty {
        if (ctx.isTypeInferred(this)) error("Trying to infer expression type twice")

        val ty = when (this) {
            is RsPathExpr -> inferPathExprType(this)
            is RsStructLiteral -> inferStructLiteralType(this, expected)
            is RsTupleExpr -> inferRsTupleExprType(this, expected)
            is RsParenExpr -> this.expr.inferType(expected)
            is RsUnitExpr -> TyUnit
            is RsCastExpr -> inferCastExprType(this)
            is RsCallExpr -> inferCallExprType(this)
            is RsDotExpr -> inferDotExprType(this)
            is RsLitExpr -> inferLitExprType(this, expected)
            is RsBlockExpr -> this.block.inferType(expected)
            is RsIfExpr -> inferIfExprType(this, expected)
            is RsLoopExpr -> inferLoopExprType(this)
            is RsWhileExpr -> inferWhileExprType(this)
            is RsForExpr -> inferForExprType(this)
            is RsMatchExpr -> inferMatchExprType(this, expected)
            is RsUnaryExpr -> inferUnaryExprType(this, expected)
            is RsBinaryExpr -> inferBinaryExprType(this)
            is RsTryExpr -> inferTryExprType(this, expected)
            is RsArrayExpr -> inferArrayType(this, expected)
            is RsRangeExpr -> inferRangeType(this)
            is RsIndexExpr -> inferIndexExprType(this)
            is RsMacroExpr -> inferMacroExprType(this)
            is RsLambdaExpr -> inferLambdaExprType(this, expected)
            is RsRetExpr -> inferRetExprType(this)
            is RsBreakExpr -> inferBreakExprType(this)
            else -> TyUnknown
        }

        ctx.writeTy(this, ty)
        return ty
    }

    fun inferLitExprType(expr: RsLitExpr, expected: Ty?): Ty {
        return when (expr.kind) {
            is RsLiteralKind.Boolean -> TyBool
            is RsLiteralKind.Char -> TyChar
            is RsLiteralKind.String -> TyReference(TyStr, IMMUTABLE)
            is RsLiteralKind.Integer -> {
                val ty = TyInteger.fromLiteral(expr.integerLiteral!!)
                if (ty.isKindWeak) {
                    // rustc treat unsuffixed integer as `u8` if it should be coerced to `char`,
                    // e.g. for code `let a: char = 5;` the error will be shown: 'expected char, found u8'
                    //
                    // rustc treat unsuffixed integer as `usize` if it should be coerced to `*T` or fn(),
                    // e.g. for code `let a: *mut u8 = 5;` the error will be shown: 'expected *-ptr, found usize'
                    when(expected) {
                        is TyInteger -> expected
                        is TyChar -> TyInteger(TyInteger.Kind.u8)
                        is TyPointer, is TyFunction -> TyInteger(TyInteger.Kind.usize)
                        else -> ty
                    }
                } else {
                    ty
                }
            }
            is RsLiteralKind.Float -> {
                val ty = TyFloat.fromLiteral(expr.floatLiteral!!)
                if (ty.isKindWeak) {
                    expected?.takeIf { it is TyFloat } ?: ty
                } else {
                    ty
                }
            }
            null -> TyUnknown
        }
    }

    private fun inferPathExprType(expr: RsPathExpr): Ty {
        val (element, subst) = expr.path.reference.advancedResolve()?.downcast<RsNamedElement>() ?: return TyUnknown
        val type = if (element is RsPatBinding) ctx.getBindingType(element) else inferDeclarationType(element)
        val tupleFields = (element as? RsFieldsOwner)?.tupleFields
        return if (tupleFields != null) {
            // Treat tuple constructor as a function
            TyFunction(tupleFields.tupleFieldDeclList.map { it.typeReference.type }, type)
        } else {
            type
        }.substitute(subst)
    }

    private fun inferStructLiteralType(expr: RsStructLiteral, expected: Ty?): Ty {
        val boundElement = expr.path.reference.advancedResolve()
        val inferredSubst = inferStructTypeArguments(expr, expected as? TyStructOrEnumBase)
        val (element, subst) = boundElement ?: return TyUnknown
        return when (element) {
            is RsStructItem -> element.type
            is RsEnumVariant -> element.parentEnum.type
            else -> TyUnknown
        }.substitute(subst).substitute(inferredSubst)
    }

    private fun inferRsTupleExprType(expr: RsTupleExpr, expected: Ty?): Ty {
        return TyTuple(inferExprList(expr.exprList, (expected as? TyTuple)?.types))
    }

    private fun inferExprList(exprs: List<RsExpr>, expected: List<Ty>?): List<Ty> {
        val extended = expected.orEmpty().asSequence().infiniteWithTyUnknown()
        return exprs.asSequence().zip(extended).map { (expr, ty) -> expr.inferType(ty) }.toList()
    }

    private fun inferCastExprType(expr: RsCastExpr): Ty {
        expr.expr.inferType()
        return expr.typeReference.type
    }

    private fun inferCallExprType(expr: RsCallExpr): Ty {
        val ty = expr.expr.inferType()
        val argExprs = expr.valueArgumentList.exprList
        // `struct S; S();`
        if (ty is TyStructOrEnumBase && argExprs.isEmpty()) return ty

        val calleeType = lookup.asTyFunction(ty) ?: unknownTyFunction(argExprs.size)
        return calleeType.retType.substitute(mapTypeParameters(calleeType.paramTypes, argExprs))
    }

    private fun inferMethodCallExprType(receiver: Ty, methodCall: RsMethodCall): Ty {
        val argExprs = methodCall.valueArgumentList.exprList
        val boundElement = resolveMethodCallReferenceWithReceiverType(lookup, receiver, methodCall)
            .firstOrNull()?.downcast<RsFunction>()
        val methodType = (boundElement?.element?.type as? TyFunction ?: unknownTyFunction(argExprs.size + 1))
            .substitute(boundElement?.subst ?: emptySubstitution)
            .substitute(mapOf(TyTypeParameter.self() to receiver))
        // drop first element of paramTypes because it's `self` param
        // and it doesn't have value in `methodCall.valueArgumentList.exprList`
        val inferredSubst = mapTypeParameters(methodType.paramTypes.drop(1), argExprs)

        return methodType.retType.substitute(inferredSubst)
    }

    private fun unknownTyFunction(arity: Int): TyFunction =
        TyFunction(generateSequence { TyUnknown }.take(arity).toList(), TyUnknown)

    private fun inferFieldExprType(receiver: Ty, fieldLookup: RsFieldLookup): Ty {
        val boundField = resolveFieldLookupReferenceWithReceiverType(lookup, receiver, fieldLookup).firstOrNull()
        if (boundField == null) {
            for (type in lookup.derefTransitively(receiver)) {
                if (type is TyTuple) {
                    val fieldIndex = fieldLookup.integerLiteral?.text?.toIntOrNull() ?: return TyUnknown
                    return type.types.getOrElse(fieldIndex) { TyUnknown }
                }
            }
            return TyUnknown
        }
        val field = boundField.element
        val raw = when (field) {
            is RsFieldDecl -> field.typeReference?.type
            is RsTupleFieldDecl -> field.typeReference.type
            else -> null
        } ?: TyUnknown
        return raw.substitute(boundField.subst)
    }

    private fun inferDotExprType(expr: RsDotExpr): Ty {
        val receiver = expr.expr.inferType()
        val methodCall = expr.methodCall
        val fieldLookup = expr.fieldLookup
        return when {
            methodCall != null -> inferMethodCallExprType(receiver, methodCall)
            fieldLookup != null -> inferFieldExprType(receiver, fieldLookup)
            else -> TyUnknown
        }
    }

    private fun inferLoopExprType(expr: RsLoopExpr): Ty {
        expr.block?.inferType()
        val returningTypes = mutableListOf<Ty>()
        val label = expr.labelDecl?.name

        fun collectReturningTypes(element: PsiElement, matchOnlyByLabel: Boolean) {
            element.forEachChild { child ->
                when (child) {
                    is RsBreakExpr -> {
                        collectReturningTypes(child, matchOnlyByLabel)
                        if (!matchOnlyByLabel && child.label == null || child.label?.referenceName == label) {
                            returningTypes += child.expr?.let(ctx::getExprType) ?: TyUnit
                        }
                    }
                    is RsLabeledExpression -> {
                        if (label != null) {
                            collectReturningTypes(child, true)
                        }
                    }
                    else -> collectReturningTypes(child, matchOnlyByLabel)
                }
            }
        }

        collectReturningTypes(expr, false)
        return getMoreCompleteType(returningTypes)
    }

    private fun inferForExprType(expr: RsForExpr): Ty {
        val exprTy = expr.expr?.inferType() ?: TyUnknown
        ctx.extractBindings(expr.pat, lookup.findIteratorItemType(exprTy))
        expr.block?.inferType()
        return TyUnit
    }

    private fun inferWhileExprType(expr: RsWhileExpr): Ty {
        expr.condition?.let { ctx.extractBindings(it.pat, it.expr.inferType()) }
        expr.block?.inferType()
        return TyUnit
    }

    private fun inferMatchExprType(expr: RsMatchExpr, expected: Ty?): Ty {
        val matchingExprTy = expr.expr?.inferType() ?: TyUnknown
        val arms = expr.matchBody?.matchArmList.orEmpty()
        for (arm in arms) {
            for (pat in arm.patList) {
                ctx.extractBindings(pat, matchingExprTy)
            }
            arm.expr?.inferType(expected)
        }

        return arms.asSequence()
            .mapNotNull { it.expr?.let(ctx::getExprType) }
            .firstOrNull { it !is TyUnknown }
            ?: TyUnknown
    }

    private fun inferUnaryExprType(expr: RsUnaryExpr, expected: Ty?): Ty {
        val innerExpr = expr.expr ?: return TyUnknown
        return when (expr.operatorType) {
            UnaryOperator.REF -> inferRefType(innerExpr, expected, IMMUTABLE)
            UnaryOperator.REF_MUT -> inferRefType(innerExpr, expected, MUTABLE)
            UnaryOperator.DEREF -> {
                // expectation must NOT be used for deref
                val base = innerExpr.inferType()
                when (base) {
                    is TyReference -> base.referenced
                    is TyPointer -> base.referenced
                    else -> TyUnknown
                }
            }
            UnaryOperator.MINUS -> innerExpr.inferType(expected)
            UnaryOperator.NOT -> innerExpr.inferType(expected)
            UnaryOperator.BOX -> {
                innerExpr.inferType()
                TyUnknown
            }
        }
    }

    private fun inferRefType(expr: RsExpr, expected: Ty?, mutable: Mutability): Ty =
        TyReference(expr.inferType((expected as? TyReference)?.referenced), mutable)

    private fun inferIfExprType(expr: RsIfExpr, expected: Ty?): Ty {
        expr.condition?.let { ctx.extractBindings(it.pat, it.expr.inferType(TyBool)) }
        val blockTy = expr.block?.inferType(expected)
        val elseBranch = expr.elseBranch
        if (elseBranch != null) {
            elseBranch.ifExpr?.inferType(expected)
            elseBranch.block?.inferType(expected)
        }
        return if (expr.elseBranch == null) TyUnit else (blockTy ?: TyUnknown)
    }

    private fun inferBinaryExprType(expr: RsBinaryExpr): Ty {
        val op = expr.operatorType
        return when (op) {
            is BoolOp -> {
                expr.left.inferType()
                expr.right?.inferType()
                TyBool
            }
            is ArithmeticOp -> {
                val lhsType = expr.left.inferType()
                val rhsType = expr.right?.inferType() ?: TyUnknown
                lookup.findArithmeticBinaryExprOutputType(lhsType, rhsType, op)
            }
            is AssignmentOp -> {
                val lhsType = expr.left.inferType()
                expr.right?.inferType(lhsType)
                TyUnit
            }
        }
    }

    private fun inferTryExprType(expr: RsTryExpr, expected: Ty?): Ty {
        // See RsMacroExpr where we handle the try! macro in a similar way
        val base = expr.expr.inferType(expected)

        return if (base is TyEnum && base.item == items.findResultItem())
            base.typeArguments.firstOrNull() ?: TyUnknown
        else
            TyUnknown
    }

    private fun inferRangeType(expr: RsRangeExpr): Ty {
        val el = expr.exprList
        val dot2 = expr.dotdot
        val dot3 = expr.dotdotdot

        val (rangeName, indexType) = when {
            dot2 != null && el.size == 0 -> "RangeFull" to null
            dot2 != null && el.size == 1 -> {
                val e = el[0]
                if (e.startOffsetInParent < dot2.startOffsetInParent) {
                    "RangeFrom" to e.inferType()
                } else {
                    "RangeTo" to e.inferType()
                }
            }
            dot2 != null && el.size == 2 -> {
                "Range" to getMoreCompleteType(el[0].inferType(), el[1].inferType())
            }
            dot3 != null && el.size == 1 -> {
                val e = el[0]
                if (e.startOffsetInParent < dot3.startOffsetInParent) {
                    return TyUnknown
                } else {
                    "RangeToInclusive" to e.inferType()
                }
            }
            dot3 != null && el.size == 2 -> {
                "RangeInclusive" to getMoreCompleteType(el[0].inferType(), el[1].inferType())
            }

            else -> error("Unrecognized range expression")
        }

        return items.findRangeTy(rangeName, indexType)
    }

    private fun inferIndexExprType(expr: RsIndexExpr): Ty {
        val containerType = expr.containerExpr?.inferType() ?: return TyUnknown
        val indexType = expr.indexExpr?.inferType() ?: return TyUnknown
        return lookup.findIndexOutputType(containerType, indexType)
    }

    private fun inferMacroExprType(expr: RsMacroExpr): Ty {
        inferChildExprsRecursively(expr.macroCall)
        val vecArg = expr.macroCall.vecMacroArgument
        if (vecArg != null) {
            val elementTypes = vecArg.exprList.map { ctx.getExprType(it) }
            val elementType = getMoreCompleteType(elementTypes)
            return items.findVecForElementTy(elementType)
        }

        val tryArg = expr.macroCall.tryMacroArgument
        if (tryArg != null) {
            // See RsTryExpr where we handle the ? expression in a similar way
            val base = ctx.getExprType(tryArg.expr)
            return if (base is TyEnum && base.item == items.findResultItem())
                base.typeArguments.firstOrNull() ?: TyUnknown
            else
                TyUnknown
        }

        val name = expr.macroCall.macroName?.text ?: return TyUnknown
        return when {
            "print" in name || "assert" in name -> TyUnit
            name == "format" -> items.findStringTy()
            name == "format_args" -> items.findArgumentsTy()
            expr.macroCall.formatMacroArgument != null || expr.macroCall.logMacroArgument != null -> TyUnit
            else -> TyUnknown
        }
    }

    private fun inferChildExprsRecursively(psi: PsiElement) {
        when (psi) {
            is RsExpr -> psi.inferType()
            else -> psi.forEachChild(this::inferChildExprsRecursively)
        }
    }

    private fun inferLambdaExprType(expr: RsLambdaExpr, expected: Ty?): Ty {
        val params = expr.valueParameterList.valueParameterList
        val expectedFnTy = expected?.let(lookup::asTyFunction) ?: unknownTyFunction(params.size)
        val extendedArgs = expectedFnTy.paramTypes.asSequence().infiniteWithTyUnknown()
        val paramTypes = extendedArgs.zip(params.asSequence()).map { (expectedArg, actualArg) ->
            val paramTy = actualArg.typeReference?.type ?: expectedArg
            ctx.extractBindings(actualArg.pat, paramTy)
            paramTy
        }.toList()

        val inferredRetTy = expr.expr?.inferType()
        return TyFunction(paramTypes, inferredRetTy ?: expectedFnTy.retType)
    }

    private fun inferArrayType(expr: RsArrayExpr, expected: Ty?): Ty {
        val expectedElemTy = when (expected) {
            is TyArray -> expected.base
            is TySlice -> expected.elementType
            else -> null
        }
        val (elementType, size) = if (expr.semicolon != null) {
            // It is "repeat expr", e.g. `[1; 5]`
            val elementType = expr.initializer?.inferType(expectedElemTy) ?: return TySlice(TyUnknown)
            expr.sizeExpr?.inferType()
            val size = calculateArraySize(expr.sizeExpr) ?: return TySlice(elementType)
            elementType to size
        } else {
            val elementTypes = expr.arrayElements?.map { it.inferType(expectedElemTy) }
            if (elementTypes.isNullOrEmpty()) return TySlice(TyUnknown)

            // '!!' is safe here because we've just checked that elementTypes isn't null
            val elementType = getMoreCompleteType(elementTypes!!)
            elementType to elementTypes.size
        }

        return TyArray(elementType, size)
    }

    private fun inferRetExprType(expr: RsRetExpr): Ty {
        expr.expr?.inferType()
        return TyUnit // TODO TyNever `!`
    }

    private fun inferBreakExprType(expr: RsBreakExpr): Ty {
        expr.expr?.inferType()
        return TyUnit // TODO TyNever `!`
    }

    private fun getMoreCompleteType(types: List<Ty>): Ty {
        if (types.isEmpty()) return TyUnknown
        return types.reduce { acc, ty -> getMoreCompleteType(acc, ty) }
    }

    private fun inferStructTypeArguments(literal: RsStructLiteral, expected: TyStructOrEnumBase?): Substitution {
        val expectedSubst = expected?.typeParameterValues ?: emptySubstitution
        val results = literal.structLiteralBody.structLiteralFieldList.mapNotNull { field ->
            field.expr?.let { expr ->
                val fieldType = field.type
                fieldType.unifyWith(expr.inferType(fieldType.substitute(expectedSubst)), lookup)
            }
        }
        return UnifyResult.mergeAll(results).substitution().orEmpty()
    }

    private fun mapTypeParameters(argDefs: List<Ty>, argExprs: List<RsExpr>): Substitution {
        // extending argument definitions to be sure that type inference launched for each arg expr
        val argDefsExt = argDefs.asSequence().infiniteWithTyUnknown()
        val results = argDefsExt.zip(argExprs.asSequence()).map { (type, expr) ->
            type.unifyWith(expr.inferType(type), lookup)
        }
        val subst = UnifyResult.mergeAll(results.asIterable()).substitution().orEmpty()
        return subst.substituteInValues(subst) // TODO multiple times?
    }
}

private val threadLocalGuard: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

/**
 * This function asserts that code inside a lambda don't call itself recursively.
 */
private inline fun preventRecursion(action: () -> Unit) {
    if (threadLocalGuard.get()) error("Can not run nested type inference")
    threadLocalGuard.set(true)
    try {
        action()
    } finally {
        threadLocalGuard.set(false)
    }
}

private fun Sequence<Ty>.infiniteWithTyUnknown(): Sequence<Ty> =
    this + generateSequence { TyUnknown }
