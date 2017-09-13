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
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.RsDiagnostic
import org.rust.lang.core.types.TraitRef
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.ty.Mutability.IMMUTABLE
import org.rust.lang.core.types.ty.Mutability.MUTABLE
import org.rust.lang.core.types.type
import org.rust.utils.forEachChild

fun inferTypesIn(fn: RsFunction): RsInferenceResult =
    RsInferenceContext().run { preventRecursion { infer(fn) } }

/**
 * [RsInferenceResult] is an immutable per-function map
 * from expressions to their types.
 */
class RsInferenceResult(
    private val bindings: Map<RsPatBinding, Ty>,
    private val exprTypes: Map<RsExpr, Ty>,
    val diagnostics: List<RsDiagnostic>
) {
    fun getExprType(expr: RsExpr): Ty =
        exprTypes[expr] ?: TyUnknown

    fun getBindingType(binding: RsPatBinding): Ty =
        bindings[binding] ?: TyUnknown

    override fun toString(): String =
        "RsInferenceResult(bindings=$bindings, exprTypes=$exprTypes)"
}

/**
 * A mutable object, which is filled while we walk function body top down.
 */
class RsInferenceContext {
    private val bindings: MutableMap<RsPatBinding, Ty> = HashMap()
    private val exprTypes: MutableMap<RsExpr, Ty> = HashMap()
    val diagnostics: MutableList<RsDiagnostic> = mutableListOf()

    private val intUnificationTable: UnificationTable<TyInfer.IntVar, TyInteger.Kind> =
        UnificationTable()
    private val floatUnificationTable: UnificationTable<TyInfer.FloatVar, TyFloat.Kind> =
        UnificationTable()
    private val varUnificationTable: UnificationTable<TyInfer.TyVar, Ty> =
        UnificationTable()

    fun infer(fn: RsFunction): RsInferenceResult {
        extractParameterBindings(fn)

        val block = fn.block
        if (block != null) {
            val items = StdKnownItems.relativeTo(fn)
            val fctx = RsFnInferenceContext(this, ImplLookup(fn.project, items), items)
            fctx.inferBlockType(block)

            fctx.selectObligationsWherePossible()
            exprTypes.replaceAll { _, ty -> fullyResolve(ty) }
            bindings.replaceAll { _, ty -> fullyResolve(ty) }
        }

        return RsInferenceResult(bindings, exprTypes, diagnostics)
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

    fun reportTypeMismatch(expr: RsExpr, expected: Ty, actual: Ty) {
        diagnostics.add(RsDiagnostic.TypeError(expr, expected, actual))
    }

    fun combineTypes(ty1: Ty, ty2: Ty): Boolean {
        return combineTypesResolved(shallowResolve(ty1), shallowResolve(ty2))
    }

    private fun combineTypesResolved(ty1: Ty, ty2: Ty): Boolean {
        return when {
            ty1 is TyInfer.TyVar -> combineTyVar(ty1, ty2)
            ty2 is TyInfer.TyVar -> combineTyVar(ty2, ty1)
            else -> when {
                ty1 is TyInfer -> combineIntOrFloatVar(ty1, ty2)
                ty2 is TyInfer -> combineIntOrFloatVar(ty2, ty1)
                else -> combineTypesNoVars(ty1, ty2)
            }
        }
    }

    private fun combineTyVar(inner1: TyInfer.TyVar, ty2: Ty): Boolean {
        when (ty2) {
            is TyInfer.TyVar -> varUnificationTable.unifyVarVar(inner1, ty2)
            else -> varUnificationTable.unifyVarValue(inner1, ty2)
        }
        return true
    }

    private fun combineIntOrFloatVar(ty1: TyInfer, ty2: Ty): Boolean {
        when (ty1) {
            is TyInfer.IntVar -> when (ty2) {
                is TyInfer.IntVar -> intUnificationTable.unifyVarVar(ty1, ty2)
                is TyInteger -> intUnificationTable.unifyVarValue(ty1, ty2.kind)
            }
            is TyInfer.FloatVar -> when (ty2) {
                is TyInfer.FloatVar -> floatUnificationTable.unifyVarVar(ty1, ty2)
                is TyFloat -> floatUnificationTable.unifyVarValue(ty1, ty2.kind)
            }
            is TyInfer.TyVar -> error("unreachable")
        }
        return true
    }

    private fun combineTypesNoVars(ty1: Ty, ty2: Ty): Boolean {
        return when {
            ty1 is TyPrimitive && ty2 is TyPrimitive && ty1 == ty2 -> true
            ty1 is TyTypeParameter && ty2 is TyTypeParameter && ty1 == ty2 -> true
            ty1 is TyReference && ty2 is TyReference && ty1.mutability == ty2.mutability -> {
                combineTypes(ty1.referenced, ty2.referenced)
            }
            ty1 is TyPointer && ty2 is TyPointer && ty1.mutability == ty2.mutability -> {
                combineTypes(ty1.referenced, ty2.referenced)
            }
            ty1 is TyArray && ty2 is TyArray && ty1.size == ty2.size -> combineTypes(ty1.base, ty2.base)
            ty1 is TySlice && ty2 is TySlice -> combineTypes(ty1.elementType, ty2.elementType)
            ty1 is TyTuple && ty2 is TyTuple -> combinePairs(ty1.types.zip(ty2.types))
            ty1 is TyFunction && ty2 is TyFunction && ty1.paramTypes.size == ty2.paramTypes.size -> {
                combinePairs(ty1.paramTypes.zip(ty2.paramTypes)) && combineTypes(ty1.retType, ty2.retType)
            }
            ty1 is TyStructOrEnumBase && ty2 is TyStructOrEnumBase && ty1.item == ty2.item -> {
                combinePairs(zipValues(ty1.typeParameterValues, ty2.typeParameterValues))
            }
            ty1 is TyTraitObject && ty2 is TyTraitObject && ty1.trait == ty2.trait -> true
            ty1 is TyNever || ty2 is TyNever -> true
            else -> false
        }
    }

    private fun combinePairs(pairs: List<Pair<Ty, Ty>>): Boolean {
        var canUnify = true
        for ((t1, t2) in pairs) {
            canUnify = combineTypes(t1, t2) && canUnify
        }
        return canUnify
    }

    fun shallowResolve(ty: Ty): Ty {
        if (ty !is TyInfer) return ty

        return when (ty) {
            is TyInfer.IntVar -> intUnificationTable.findValue(ty)?.let(::TyInteger) ?: ty
            is TyInfer.FloatVar -> floatUnificationTable.findValue(ty)?.let(::TyFloat) ?: ty
            is TyInfer.TyVar -> varUnificationTable.findValue(ty)?.let(this::shallowResolve) ?: ty
        }
    }

    fun <T: TypeFoldable<T>> resolveTypeVarsIfPossible(ty: T): T {
        return ty.foldTyInferWith(this::shallowResolve)
    }

    private fun fullyResolve(ty: Ty): Ty {
        fun go(ty: Ty): Ty {
            if (ty !is TyInfer) return ty

            return when (ty) {
                is TyInfer.IntVar -> TyInteger(intUnificationTable.findValue(ty) ?: TyInteger.DEFAULT_KIND)
                is TyInfer.FloatVar -> TyFloat(floatUnificationTable.findValue(ty) ?: TyFloat.DEFAULT_KIND)
                is TyInfer.TyVar -> varUnificationTable.findValue(ty)?.let(::go) ?: ty.origin
            }
        }

        return ty.foldTyInferWith(::go)
    }


    fun typeVarForParam(ty: TyTypeParameter): Ty {
        return TyInfer.TyVar(ty)
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
    private val fulfill: FulfillmentContext = FulfillmentContext(ctx, lookup)
    private val RsStructLiteralField.type: Ty get() = resolveToDeclaration?.typeReference?.type ?: TyUnknown

    private fun resolveTypeVarsWithObligations(ty: Ty): Ty {
        val tyRes = ctx.resolveTypeVarsIfPossible(ty)
        selectObligationsWherePossible()
        return ctx.resolveTypeVarsIfPossible(tyRes)
    }

    fun selectObligationsWherePossible() {
        fulfill.selectWherePossible()
    }

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
                val explicitTy = psi.typeReference?.type?.foldTyTypeParameterWith(ctx::typeVarForParam)
                val inferredTy = explicitTy
                    ?.let { psi.expr?.inferTypeCoercableTo(it) }
                    ?: psi.expr?.inferType()
                    ?: TyUnknown
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
            is RsContExpr -> TyNever
            else -> TyUnknown
        }

        ctx.writeTy(this, ty)
        return ty
    }

    private fun RsExpr.inferTypeCoercableTo(expected: Ty): Ty {
        val inferred = inferType(expected)
        coerce(this, inferred, expected)
        return inferred
    }

    private fun coerce(expr: RsExpr, inferred: Ty, expected: Ty) {
        coerceResolved(expr, resolveTypeVarsWithObligations(inferred), resolveTypeVarsWithObligations(expected))
    }

    private fun coerceResolved(expr: RsExpr, inferred: Ty, expected: Ty) {
        val ok = tryCoerce(inferred, expected)
        if (!ok) {
            // ignoring possible false-positives (it's only basic experimental type checking)
            val ignoredTys = listOf(
                TyUnknown::class.java,
                TyInfer::class.java,
                TyTypeParameter::class.java,
                // TODO TyReference ignored because we actually ignore deref level on method call and so
                // TODO sometimes substitute a wrong receiver. This should be fixed as soon as possible
                TyReference::class.java,
                TyTraitObject::class.java
            )

            if (!expected.containsTyOfClass(ignoredTys) && !inferred.containsTyOfClass(ignoredTys)) {
                // another awful hack: check that inner expressions did not annotated as an error
                // to disallow annotation intersections. This should be done in a different way
                fun PsiElement.isChildOf(psi: PsiElement) = this.ancestors.contains(psi)
                if (ctx.diagnostics.all { !it.element.isChildOf(expr) }) {
                    ctx.reportTypeMismatch(expr, expected, inferred)
                }
            }
        }
    }

    private fun tryCoerce(inferred: Ty, expected: Ty): Boolean {
        return when {
            inferred is TyReference && inferred.referenced is TyArray &&
                expected is TyReference && expected.referenced is TySlice -> {
                ctx.combineTypes(inferred.referenced.base, expected.referenced.elementType)
            }
        // TODO trait object unsizing
            else -> ctx.combineTypes(inferred, expected)
        }
    }

    fun inferLitExprType(expr: RsLitExpr, expected: Ty?): Ty {
        return when (expr.kind) {
            is RsLiteralKind.Boolean -> TyBool
            is RsLiteralKind.Char -> TyChar
            is RsLiteralKind.String -> TyReference(TyStr, IMMUTABLE)
            is RsLiteralKind.Integer -> {
                val ty = TyInteger.fromSuffixedLiteral(expr.integerLiteral!!)
                ty ?: when (expected) {
                    is TyInteger -> expected
                    is TyChar -> TyInteger(TyInteger.Kind.u8)
                    is TyPointer, is TyFunction -> TyInteger(TyInteger.Kind.usize)
                    else -> TyInfer.IntVar()
                }
            }
            is RsLiteralKind.Float -> {
                val ty = TyFloat.fromSuffixedLiteral(expr.floatLiteral!!)
                ty ?: (expected?.takeIf { it is TyFloat } ?: TyInfer.FloatVar())
            }
            null -> TyUnknown
        }
    }

    private fun inferPathExprType(expr: RsPathExpr): Ty {
        val (element, subst) = expr.path.reference.advancedResolve()?.downcast<RsNamedElement>() ?: return TyUnknown
        val type = when (element) {
            is RsPatBinding -> ctx.getBindingType(element)
            is RsTypeDeclarationElement -> element.declaredType
            is RsEnumVariant -> element.parentEnum.declaredType
            is RsFunction -> element.typeOfValue
            is RsConstant -> element.typeReference?.type ?: TyUnknown
            is RsSelfParameter -> element.typeOfValue
            else -> return TyUnknown
        }

        // This BS is a very temporary (I hope)
        val typeParameters = ((element as? RsGenericDeclaration)?.let(this::instantiateBounds) ?: emptyMap()) +
            (element.takeIf { it is RsFunction || it is RsEnumVariant}
                ?.parentOfType<RsGenericDeclaration>()
                ?.let(this::instantiateBounds)
                ?: emptyMap())

        subst.forEach { (k, v1) ->
            typeParameters[k]?.let { v2 ->
                if (k != v1 && v1 !is TyTypeParameter && v1 !is TyUnknown) {
                    ctx.combineTypes(v2, v1)
                }
            }
        }

        val tupleFields = (element as? RsFieldsOwner)?.tupleFields
        return if (tupleFields != null) {
            // Treat tuple constructor as a function
            TyFunction(tupleFields.tupleFieldDeclList.map { it.typeReference.type }, type)
        } else {
            type
        }.foldWithSubst(typeParameters).foldWith(this::normalizeAssociatedTypesIn)
    }

    private fun instantiateBounds(element: RsGenericDeclaration): Map<TyTypeParameter, Ty> =
        instantiateBounds(element, emptySubstitution, null)

    private fun instantiateBounds(element: RsGenericDeclaration, subst: Substitution, receiver: Ty?): Map<TyTypeParameter, Ty> {
        val elementBounds = element.bounds
        var map = subst + elementBounds.keys.associate { it to ctx.typeVarForParam(it) }
        if (receiver != null) {
            map = map.substituteInValues(mapOf(TyTypeParameter.self() to receiver)) +
                mapOf(TyTypeParameter.self() to receiver)
        }
        for ((ty, bounds) in elementBounds) {
            bounds.asSequence()
                .map { TraitRef(ty, it) }
                .map { it.foldTyTypeParameterWith { map[it] ?: it } }
                .map(this::normalizeAssociatedTypesIn)
                .forEach { fulfill.registerPredicateObligation(Obligation(Predicate.Trait(it))) }
        }
        return map
    }

    private fun <T: TypeFoldable<T>> normalizeAssociatedTypesIn(ty: T): T {
        return ty.foldTyTypeParameterWith {
            val p = it.parameter
            if (p is TyTypeParameter.AssociatedType) {
                val selfTy = p.type
                val tyVar = ctx.typeVarForParam(it)
                fulfill.registerPredicateObligation(
                    Obligation(0, Predicate.Projection(selfTy, p.trait, p.target, tyVar))
                )
                tyVar
            } else {
                it
            }
        }
    }

    private fun inferStructLiteralType(expr: RsStructLiteral, expected: Ty?): Ty {
        val boundElement = expr.path.reference.advancedResolve()
        val inferredSubst = inferStructTypeArguments(expr, expected as? TyStructOrEnumBase)
        val (element, subst) = boundElement ?: return TyUnknown
        return when (element) {
            is RsStructItem -> element.declaredType
            is RsEnumVariant -> element.parentEnum.declaredType
            else -> TyUnknown
        }.substitute(subst).substitute(inferredSubst)
    }

    private fun inferRsTupleExprType(expr: RsTupleExpr, expected: Ty?): Ty {
        return TyTuple(inferExprList(expr.exprList, (expected as? TyTuple)?.types))
    }

    private fun inferExprList(exprs: List<RsExpr>, expected: List<Ty>?): List<Ty> {
        val extended = expected.orEmpty().asSequence().infiniteWithTyUnknown()
        return exprs.asSequence().zip(extended).map { (expr, ty) -> expr.inferTypeCoercableTo(ty) }.toList()
    }

    private fun inferCastExprType(expr: RsCastExpr): Ty {
        expr.expr.inferType()
        return expr.typeReference.type
    }

    private fun inferCallExprType(expr: RsCallExpr): Ty {
        val ty = resolveTypeVarsWithObligations(expr.expr.inferType()) // or error
        val argExprs = expr.valueArgumentList.exprList
        // `struct S; S();`
        if (ty is TyStructOrEnumBase && argExprs.isEmpty()) return ty

        val calleeType = lookup.asTyFunction(ty) ?: unknownTyFunction(argExprs.size)
        inferArgumentTypes(calleeType.paramTypes, argExprs)
        return calleeType.retType
    }

    private fun inferMethodCallExprType(receiver: Ty, methodCall: RsMethodCall): Ty {
        val receiverRes = resolveTypeVarsWithObligations(receiver)
        val argExprs = methodCall.valueArgumentList.exprList
        val boundElement = resolveMethodCallReferenceWithReceiverType(lookup, receiverRes, methodCall)
            .firstOrNull()?.downcast<RsFunction>()
        val typeParameters = ((boundElement?.element as? RsGenericDeclaration)
            ?.let { instantiateBounds(it, boundElement?.subst ?: emptyMap(), receiverRes) }
            ?: emptyMap())

        boundElement?.subst?.forEach { (k, v1) ->
            typeParameters[k]?.let { v2 ->
                if (k != v1 && v1 !is TyTypeParameter && v1 !is TyUnknown) {
                    ctx.combineTypes(v2, v1)
                }
            }
        }

        val methodType = (boundElement?.element?.typeOfValue ?: unknownTyFunction(argExprs.size + 1))
            .foldWithSubst(typeParameters)
            .foldWith(this::normalizeAssociatedTypesIn) as TyFunction
        // drop first element of paramTypes because it's `self` param
        // and it doesn't have value in `methodCall.valueArgumentList.exprList`
        inferArgumentTypes(methodType.paramTypes.drop(1), argExprs)

        return methodType.retType
    }

    private fun unknownTyFunction(arity: Int): TyFunction =
        TyFunction(generateSequence { TyUnknown }.take(arity).toList(), TyUnknown)

    private fun inferArgumentTypes(argDefs: List<Ty>, argExprs: List<RsExpr>) {
        // We do this just like rustc, and comments copied from rustc too

        // We do this in a pretty awful way: first we typecheck any arguments
        // that are not closures, then we typecheck the closures. This is so
        // that we have more information about the types of arguments when we
        // typecheck the functions.
        for (checkLambdas in booleanArrayOf(false, true)) {
            // More awful hacks: before we check argument types, try to do
            // an "opportunistic" vtable resolution of any trait bounds on
            // the call. This helps coercions.
            if (checkLambdas) {
                selectObligationsWherePossible()
            }

            // extending argument definitions to be sure that type inference launched for each arg expr
            val argDefsExt = argDefs.asSequence().infiniteWithTyUnknown()
            for ((type, expr) in argDefsExt.zip(argExprs.asSequence().map(::unwrapParenExprs))) {
                val isLambda = expr is RsLambdaExpr
                if (isLambda != checkLambdas) continue

                expr.inferTypeCoercableTo(type)
            }
        }
    }

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

        return getMoreCompleteType(arms.mapNotNull { it.expr?.let(ctx::getExprType) })
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
        val blockTys = mutableListOf<Ty?>()
        blockTys.add(expr.block?.inferType(expected))
        val elseBranch = expr.elseBranch
        if (elseBranch != null) {
            blockTys.add(elseBranch.ifExpr?.inferType(expected))
            blockTys.add(elseBranch.block?.inferType(expected))
        }
        return if (expr.elseBranch == null) TyUnit else getMoreCompleteType(blockTys.filterNotNull())
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
                expr.right?.inferTypeCoercableTo(lhsType)
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
            name == "unimplemented" || name == "unreachable" || name == "panic" -> TyNever
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
        val expectedFnTy = expected
            ?.let(this::deduceLambdaType)
            ?: unknownTyFunction(params.size)
        val extendedArgs = expectedFnTy.paramTypes.asSequence().infiniteWithTyUnknown()
        val paramTypes = extendedArgs.zip(params.asSequence()).map { (expectedArg, actualArg) ->
            val paramTy = actualArg.typeReference?.type ?: expectedArg
            ctx.extractBindings(actualArg.pat, paramTy)
            paramTy
        }.toList()

        val inferredRetTy = expr.expr?.inferType()
        return TyFunction(paramTypes, inferredRetTy ?: expectedFnTy.retType)
    }

    private fun deduceLambdaType(expected: Ty): TyFunction? {
        return when (expected) {
            is TyInfer.TyVar -> {
                fulfill.pendingObligations
                    .mapNotNull { it.obligation.predicate as? Predicate.Trait }
                    .find { it.trait.selfTy == expected }
                    ?.let { lookup.asTyFunction(it.trait.trait) }
            }
            is TyReference -> {
                if (expected.referenced is TyTraitObject) {
                    null // TODO
                } else {
                    null
                }
            }
            is TyFunction -> expected
            else -> null
        }
    }

    private fun inferArrayType(expr: RsArrayExpr, expected: Ty?): Ty {
        val expectedElemTy = when (expected) {
            is TyArray -> expected.base
            is TySlice -> expected.elementType
            else -> null
        }
        val (elementType, size) = if (expr.semicolon != null) {
            // It is "repeat expr", e.g. `[1; 5]`
            val elementType = expectedElemTy
                ?.let { expr.initializer?.inferTypeCoercableTo(expectedElemTy) }
                ?: expr.initializer?.inferType()
                ?: return TySlice(TyUnknown)
            expr.sizeExpr?.inferType(TyInteger(TyInteger.Kind.usize))
            val size = calculateArraySize(expr.sizeExpr) ?: return TySlice(elementType)
            elementType to size
        } else {
            val elementTypes = expr.arrayElements?.map { it.inferType(expectedElemTy) }
            if (elementTypes.isNullOrEmpty()) return TySlice(TyUnknown)

            // '!!' is safe here because we've just checked that elementTypes isn't null
            val elementType = getMoreCompleteType(elementTypes!!)
            if (expectedElemTy != null) tryCoerce(elementType, expectedElemTy)
            elementType to elementTypes.size
        }

        return TyArray(elementType, size)
    }

    private fun inferRetExprType(expr: RsRetExpr): Ty {
        expr.expr?.inferType()
        return TyNever
    }

    private fun inferBreakExprType(expr: RsBreakExpr): Ty {
        expr.expr?.inferType()
        return TyNever
    }

    // TODO should be replaced with coerceMany
    private fun getMoreCompleteType(types: List<Ty>): Ty {
        if (types.isEmpty()) return TyUnknown
        return types.reduce { acc, ty -> getMoreCompleteType(acc, ty) }
    }

    // TODO should be replaced with coerceMany
    fun getMoreCompleteType(ty1: Ty, ty2: Ty): Ty = when (ty1) {
        is TyUnknown -> ty2
        is TyNever -> ty2
        else -> {
            ctx.combineTypes(ty1, ty2)
            ty1
        }
    }

    private fun inferStructTypeArguments(literal: RsStructLiteral, expected: TyStructOrEnumBase?): Substitution {
        val expectedSubst = expected?.typeParameterValues ?: emptySubstitution
        val results = literal.structLiteralBody.structLiteralFieldList.mapNotNull { field ->
            field.expr?.let { expr ->
                val fieldType = field.type
                fieldType.unifyWith(expr.inferTypeCoercableTo(fieldType.substitute(expectedSubst)), lookup)
            }
        }
        return UnifyResult.mergeAll(results).substitution().orEmpty()
    }
}

private val RsSelfParameter.typeOfValue: Ty
    get() {
        val impl = parentOfType<RsImplItem>()
        var Self: Ty = if (impl != null) {
            impl.typeReference?.type ?: return TyUnknown
        } else {
            val trait = parentOfType<RsTraitItem>()
                ?: return TyUnknown
            TyTypeParameter.self(trait)
        }

        if (isRef) {
            Self = TyReference(Self, mutability)
        }

        return Self
    }

private val RsFunction.typeOfValue: TyFunction
    get() {
        val paramTypes = mutableListOf<Ty>()

        val self = selfParameter
        if (self != null) {
            paramTypes += self.typeOfValue
        }

        paramTypes += valueParameters.map { it.typeReference?.type ?: TyUnknown }

        val ownerType = (owner as? RsFunctionOwner.Impl)?.impl?.typeReference?.type
        val subst = if (ownerType != null) mapOf(TyTypeParameter.self() to ownerType) else emptyMap()

        return TyFunction(paramTypes, returnType).substitute(subst) as TyFunction
    }

private val RsGenericDeclaration.bounds: Map<TyTypeParameter, List<BoundElement<RsTraitItem>>> get() {
    val whereBounds = this.whereClause?.wherePredList.orEmpty()
        .mapNotNull {
            val key = (it.typeReference?.typeElement as? RsBaseType)?.path?.reference?.resolve()
                ?.let { it as? RsTypeDeclarationElement }
                ?.let { it.declaredType as? TyTypeParameter }
            val value = it.typeParamBounds?.polyboundList.orEmpty()
                .mapNotNull { it.bound.traitRef?.resolveToBoundTrait }
            if (key == null) null else key to value
        }.toMap()

    return typeParameters.associate {
        TyTypeParameter.named(it) to it.typeParamBounds?.polyboundList.orEmpty()
            .mapNotNull { it.bound.traitRef?.resolveToBoundTrait }
    }.mergeReduce(whereBounds) { v1, v2 -> v1 + v2}
}

private val threadLocalGuard: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

/**
 * This function asserts that code inside a lambda don't call itself recursively.
 */
private inline fun <T> preventRecursion(action: () -> T): T {
    if (threadLocalGuard.get()) error("Can not run nested type inference")
    threadLocalGuard.set(true)
    try {
        return action()
    } finally {
        threadLocalGuard.set(false)
    }
}

private fun Sequence<Ty>.infiniteWithTyUnknown(): Sequence<Ty> =
    this + generateSequence { TyUnknown }

private fun unwrapParenExprs(expr: RsExpr): RsExpr {
    var child = expr
    while (child is RsParenExpr) {
        child = child.expr
    }
    return child
}

private fun <K, V1, V2> zipValues(map1: Map<K, V1>, map2: Map<K, V2>): List<Pair<V1, V2>> =
    map1.mapNotNull { (k, v1) -> map2[k]?.let { v2 -> Pair(v1, v2) } }

private fun <K, V> Map<K, V>.mergeReduce(other: Map<K, V>, reduce: (V, V) -> V): Map<K, V> {
    val result = LinkedHashMap<K, V>(this.size + other.size)
    result.putAll(this)
    other.forEach { e -> result[e.key] = result[e.key]?.let { reduce(e.value, it) } ?: e.value }
    return result
}
