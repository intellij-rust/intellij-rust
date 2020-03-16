/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.util.containers.isNullOrEmpty
import org.rust.lang.core.macros.MacroExpansion
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.ref.*
import org.rust.lang.core.stubs.RsStubLiteralKind
import org.rust.lang.core.types.*
import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.consts.CtInferVar
import org.rust.lang.core.types.consts.CtUnknown
import org.rust.lang.core.types.ty.*
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.evaluation.ConstExpr
import org.rust.lang.utils.evaluation.PathExprResolver
import org.rust.lang.utils.evaluation.evaluate
import org.rust.lang.utils.evaluation.toConst
import org.rust.openapiext.forEachChild
import org.rust.stdext.notEmptyOrLet
import org.rust.stdext.singleOrFilter
import org.rust.stdext.singleOrLet

class RsTypeInferenceWalker(
    private val ctx: RsInferenceContext,
    private val returnTy: Ty
) {
    private var tryTy: Ty? = returnTy
    private var yieldTy: Ty? = null
    private var macroExprDepth: Int = 0
    private val lookup get() = ctx.lookup
    private val items get() = ctx.items
    private val fulfill get() = ctx.fulfill
    private val RsStructLiteralField.type: Ty get() = resolveToDeclaration()?.typeReference?.type ?: TyUnknown

    private fun resolveTypeVarsWithObligations(ty: Ty): Ty {
        if (!ty.needsInfer) return ty
        val tyRes = ctx.resolveTypeVarsIfPossible(ty)
        if (!tyRes.needsInfer) return tyRes
        selectObligationsWherePossible()
        return ctx.resolveTypeVarsIfPossible(tyRes)
    }

    private fun selectObligationsWherePossible() {
        fulfill.selectWherePossible()
    }

    private fun inferBlockExprType(blockExpr: RsBlockExpr, expected: Ty? = null): Ty =
        when {
            blockExpr.isTry -> inferTryBlockExprType(blockExpr, expected)
            blockExpr.isAsync -> inferAsyncBlockExprType(blockExpr, expected)
            else -> {
                val type = blockExpr.block.inferType(expected)
                inferLabeledExprType(blockExpr, type, true)
            }
        }

    private fun inferTryBlockExprType(blockExpr: RsBlockExpr, expected: Ty? = null): Ty {
        require(blockExpr.isTry)
        val oldTryTy = tryTy
        try {
            tryTy = expected ?: TyInfer.TyVar()
            val resultTy = tryTy ?: TyUnknown
            val okTy = blockExpr.block.inferType()
            registerTryProjection(resultTy, "Ok", okTy)
            return resultTy
        } finally {
            tryTy = oldTryTy
        }
    }

    private fun inferAsyncBlockExprType(blockExpr: RsBlockExpr, expected: Ty? = null): Ty {
        require(blockExpr.isAsync)
        val retTy = expected
            ?.let(::resolveTypeVarsWithObligations)
            .takeUnless { it is TyInfer.TyVar }
            ?.lookupFutureOutputTy(lookup)
            ?: TyInfer.TyVar()
        RsTypeInferenceWalker(ctx, retTy).apply {
            blockExpr.block.inferType(retTy, coerce = true)
        }
        return items.makeFuture(resolveTypeVarsWithObligations(retTy))
    }

    fun inferFnBody(block: RsBlock): Ty =
        block.inferTypeCoercableTo(returnTy)

    fun inferLambdaBody(expr: RsExpr): Ty = expr.inferTypeCoercableTo(returnTy)

    private fun RsBlock.inferTypeCoercableTo(expected: Ty): Ty =
        inferType(expected, true)

    private fun RsBlock.inferType(expected: Ty? = null, coerce: Boolean = false): Ty {
        var isDiverging = false
        val (expandedStmts, tailExpr) = expandedStmtsAndTailExpr
        for (stmt in expandedStmts) {
            val result = when (stmt) {
                is RsStmt -> processStatement(stmt)
                is RsExpr -> stmt.inferType() == TyNever
                else -> false
            }
            isDiverging = result || isDiverging
        }
        val type = (if (coerce) tailExpr?.inferTypeCoercableTo(expected!!) else tailExpr?.inferType(expected)) ?: TyUnit
        return if (isDiverging) TyNever else type
    }

    fun inferReplCodeFragment(element: RsReplCodeFragment) {
        for (stmt in element.stmts) {
            processStatement(stmt)
        }
        element.tailExpr?.inferType()
    }

    // returns true if expr is always diverging
    private fun processStatement(psi: RsStmt): Boolean = when (psi) {
        is RsLetDecl -> {
            val explicitTy = psi.typeReference?.type
                ?.let { normalizeAssociatedTypesIn(it) }
            val expr = psi.expr
            // We need to know type before coercion to correctly identify if expr is always diverging
            // so we can't call `inferTypeCoercableTo` directly here
            val (inferredTy, coercedInferredTy) = if (expr != null) {
                val inferredTy = expr.inferType(explicitTy)
                val coercedTy = if (explicitTy != null && coerce(expr, inferredTy, explicitTy)) {
                    explicitTy
                } else {
                    inferredTy
                }
                inferredTy to coercedTy
            } else {
                TyUnknown to TyInfer.TyVar()
            }
            psi.pat?.extractBindings(explicitTy ?: resolveTypeVarsWithObligations(coercedInferredTy))
            inferredTy == TyNever
        }
        is RsExprStmt -> psi.expr.inferType() == TyNever
        else -> false
    }

    private fun RsExpr.inferType(expected: Ty? = null): Ty {
        ProgressManager.checkCanceled()
        if (ctx.isTypeInferred(this)) error("Trying to infer expression type twice")

        if (expected != null) {
            when (this) {
                is RsPathExpr -> ctx.writeExpectedPathExprTy(this, expected)
                is RsDotExpr -> ctx.writeExpectedDotExprTy(this, expected)
            }
        }

        val ty = when (this) {
            is RsPathExpr -> inferPathExprType(this)
            is RsStructLiteral -> inferStructLiteralType(this, expected)
            is RsTupleExpr -> inferRsTupleExprType(this, expected)
            is RsParenExpr -> this.expr.inferType(expected)
            is RsUnitExpr -> TyUnit
            is RsCastExpr -> inferCastExprType(this)
            is RsCallExpr -> inferCallExprType(this, expected)
            is RsDotExpr -> inferDotExprType(this, expected)
            is RsLitExpr -> inferLitExprType(this, expected)
            is RsBlockExpr -> inferBlockExprType(this, expected)
            is RsIfExpr -> inferIfExprType(this, expected)
            is RsLoopExpr -> inferLoopExprType(this)
            is RsWhileExpr -> inferWhileExprType(this)
            is RsForExpr -> inferForExprType(this)
            is RsMatchExpr -> inferMatchExprType(this, expected)
            is RsUnaryExpr -> inferUnaryExprType(this, expected)
            is RsBinaryExpr -> inferBinaryExprType(this)
            is RsTryExpr -> inferTryExprType(this)
            is RsArrayExpr -> inferArrayType(this, expected)
            is RsRangeExpr -> inferRangeType(this)
            is RsIndexExpr -> inferIndexExprType(this)
            is RsMacroExpr -> inferMacroExprType(this, expected)
            is RsLambdaExpr -> inferLambdaExprType(this, expected)
            is RsYieldExpr -> inferYieldExprType(this)
            is RsRetExpr -> inferRetExprType(this)
            is RsBreakExpr -> inferBreakExprType(this)
            is RsContExpr -> TyNever
            else -> TyUnknown
        }

        ctx.writeExprTy(this, ty)
        return ty
    }

    private fun RsExpr.inferTypeCoercableTo(expected: Ty): Ty {
        val inferred = inferType(expected)
        return if (coerce(this, inferred, expected)) expected else inferred
    }

    @JvmName("inferTypeCoercableTo_")
    fun inferTypeCoercableTo(expr: RsExpr, expected: Ty): Ty =
        expr.inferTypeCoercableTo(expected)

    private fun coerce(element: RsElement, inferred: Ty, expected: Ty): Boolean =
        coerceResolved(
            element,
            resolveTypeVarsWithObligations(inferred),
            resolveTypeVarsWithObligations(expected)
        )

    private fun coerceResolved(element: RsElement, inferred: Ty, expected: Ty): Boolean =
        when (val result = tryCoerce(inferred, expected)) {
            CoerceResult.Ok -> true

            is CoerceResult.TypeMismatch -> {
                if (result.ty1.javaClass !in IGNORED_TYS && result.ty2.javaClass !in IGNORED_TYS
                    && !(expected is TyReference && inferred is TyReference
                        && (expected.containsTyOfClass(IGNORED_TYS) || inferred.containsTyOfClass(IGNORED_TYS)))
                ) {
                    reportTypeMismatch(element, expected, inferred)
                }

                false
            }

            is CoerceResult.ConstMismatch -> {
                if (result.const1.javaClass !in IGNORED_CONSTS && result.const2.javaClass !in IGNORED_CONSTS) {
                    reportTypeMismatch(element, expected, inferred)
                }

                false
            }
        }

    // Another awful hack: check that inner expressions did not annotated as an error
    // to disallow annotation intersections. This should be done in a different way
    private fun reportTypeMismatch(element: RsElement, expected: Ty, inferred: Ty) {
        if (ctx.diagnostics.all { !element.isAncestorOf(it.element) }) {
            ctx.reportTypeMismatch(element, expected, inferred)
        }
    }

    private fun tryCoerce(inferred: Ty, expected: Ty): CoerceResult {
        return when {
            inferred == TyNever -> CoerceResult.Ok
            // Coerce array to slice
            inferred is TyReference && inferred.referenced is TyArray &&
                expected is TyReference && expected.referenced is TySlice -> {
                ctx.combineTypes(inferred.referenced.base, expected.referenced.elementType)
            }
            // Coerce reference to pointer
            inferred is TyReference && expected is TyPointer &&
                coerceMutability(inferred.mutability, expected.mutability) -> {
                ctx.combineTypes(inferred.referenced, expected.referenced)
            }
            // Coerce mutable pointer to const pointer
            inferred is TyPointer && inferred.mutability.isMut
                && expected is TyPointer && !expected.mutability.isMut -> {
                ctx.combineTypes(inferred.referenced, expected.referenced)
            }
            // Coerce references
            inferred is TyReference && expected is TyReference &&
                coerceMutability(inferred.mutability, expected.mutability) -> {
                coerceReference(inferred, expected)
            }
            // TODO trait object unsizing
            else -> ctx.combineTypes(inferred, expected)
        }
    }

    private fun coerceMutability(from: Mutability, to: Mutability): Boolean =
        from == to || from.isMut && !to.isMut

    /**
     * Reborrows `&mut A` to `&mut B` and `&(mut) A` to `&B`.
     * To match `A` with `B`, autoderef will be performed
     */
    private fun coerceReference(inferred: TyReference, expected: TyReference): CoerceResult {
        for (derefTy in lookup.coercionSequence(inferred).drop(1)) {
            // TODO proper handling of lifetimes
            val derefTyRef = TyReference(derefTy, expected.mutability, expected.region)
            if (ctx.combineTypesIfOk(derefTyRef, expected)) return CoerceResult.Ok
        }

        return CoerceResult.TypeMismatch(inferred, expected)
    }

    private fun inferLitExprType(expr: RsLitExpr, expected: Ty?): Ty {
        return when (val stubKind = expr.stubKind) {
            is RsStubLiteralKind.Boolean -> TyBool
            is RsStubLiteralKind.Char -> if (stubKind.isByte) TyInteger.U8 else TyChar
            is RsStubLiteralKind.String -> {
                // TODO infer the actual lifetime
                if (stubKind.isByte) {
                    val size = stubKind.value?.length?.toLong()
                    val const = size?.let { ConstExpr.Value.Integer(it, TyInteger.USize).toConst() } ?: CtUnknown
                    TyReference(TyArray(TyInteger.U8, const), Mutability.IMMUTABLE)
                } else {
                    TyReference(TyStr, Mutability.IMMUTABLE)
                }
            }
            is RsStubLiteralKind.Integer -> {
                val ty = stubKind.ty
                ty ?: when (expected) {
                    is TyInteger -> expected
                    TyChar -> TyInteger.U8
                    is TyPointer, is TyFunction -> TyInteger.USize
                    else -> TyInfer.IntVar()
                }
            }
            is RsStubLiteralKind.Float -> {
                val ty = stubKind.ty
                ty ?: (expected?.takeIf { it is TyFloat } ?: TyInfer.FloatVar())
            }
            null -> TyUnknown
        }
    }

    private fun inferPathExprType(expr: RsPathExpr): Ty {
        val path = expr.path
        val resolveVariants = resolvePathRaw(path, lookup)
        val assocVariants = resolveVariants.filterIsInstance<AssocItemScopeEntry>()

        val filteredVariants = if (resolveVariants.size == assocVariants.size) {
            val variants = filterAssocItems(assocVariants, expr)
            val fnVariants = variants.mapNotNull { it.element as? RsFunction }
            if (variants.size > 1 && fnVariants.size == variants.size && path.path != null) {
                val resolved = collapseToTrait(fnVariants)
                if (resolved != null) {
                    ctx.writePath(expr, variants.map { ResolvedPath.from(it) })
                    val subst = collapseSubst(
                        resolved,
                        variants.mapNotNull { e ->
                            (e.element as? RsGenericDeclaration)?.let { BoundElement(it, e.subst) }
                        }
                    )
                    val scopeEntry = variants.first().copy(
                        element = resolved,
                        subst = subst,
                        source = TraitImplSource.Collapsed((resolved.owner as RsAbstractableOwner.Trait).trait)
                    )
                    return instantiatePath(resolved, scopeEntry, expr)
                }
            }
            variants
        } else {
            resolveVariants
        }

        ctx.writePath(expr, filteredVariants.mapNotNull { ResolvedPath.from(it) })

        val first = filteredVariants.singleOrNull() ?: return TyUnknown
        return instantiatePath(first.element ?: return TyUnknown, first, expr)
    }

    /** This works for `String::from` where multiple impls of `From` trait found for `String` */
    private fun collapseToTrait(elements: List<RsFunction>): RsFunction? {
        if (elements.size <= 1) return null

        val traits = elements.mapNotNull {
            when (val owner = it.owner) {
                is RsAbstractableOwner.Impl -> owner.impl.traitRef?.resolveToTrait()
                is RsAbstractableOwner.Trait -> owner.trait
                else -> null
            }
        }

        if (traits.size == elements.size && traits.toSet().size == 1) {
            val fnName = elements.first().name
            val trait = traits.first()
            val functionList = trait.expandedMembers.functions
            return functionList.singleOrNull { it.name == fnName }
        }

        return null
    }

    /** See test `test type arguments remap on collapse to trait` */
    private fun collapseSubst(parentFn: RsFunction, variants: List<BoundElement<RsGenericDeclaration>>): Substitution {
        val generics = parentFn.generics
        val typeSubst = mutableMapOf<TyTypeParameter, Ty>()
        for (fn in variants) {
            for ((key, newValue) in generics.zip(fn.positionalTypeArguments)) {
                @Suppress("NAME_SHADOWING")
                typeSubst.compute(key) { key, oldValue ->
                    if (oldValue == null || oldValue == newValue) newValue else TyInfer.TyVar(key)
                }
            }
        }
        variants.first().subst[TyTypeParameter.self()]?.let { typeSubst[TyTypeParameter.self()] = it }

        val constGenerics = parentFn.constGenerics
        val constSubst = mutableMapOf<CtConstParameter, Const>()
        for (fn in variants) {
            for ((key, newValue) in constGenerics.zip(fn.positionalConstArguments)) {
                @Suppress("NAME_SHADOWING")
                constSubst.compute(key) { key, oldValue ->
                    if (oldValue == null || oldValue == newValue) newValue else CtInferVar(key)
                }
            }
        }

        // TODO: remap lifetimes
        return Substitution(typeSubst = typeSubst, constSubst = constSubst)
    }

    private fun instantiatePath(
        element: RsElement,
        scopeEntry: ScopeEntry,
        pathExpr: RsPathExpr
    ): Ty {
        val path = pathExpr.path

        if (element is RsGenericDeclaration) {
            inferConstArgumentTypes(element.constParameters, path.constArguments)
        }

        val subst = instantiatePathGenerics(
            path,
            BoundElement(element, scopeEntry.subst),
            PathExprResolver.fromContext(ctx)
        ).subst

        val typeParameters = when {
            scopeEntry is AssocItemScopeEntry && element is RsAbstractable -> {
                val owner = element.owner
                val (typeParameters, selfTy) = when (owner) {
                    is RsAbstractableOwner.Impl -> {
                        val typeParameters = ctx.instantiateBounds(owner.impl)
                        val selfTy = owner.impl.typeReference?.type?.substitute(typeParameters) ?: TyUnknown
                        subst[TyTypeParameter.self()]?.let { ctx.combineTypes(selfTy, it) }
                        typeParameters to selfTy
                    }
                    is RsAbstractableOwner.Trait -> {
                        val typeParameters = ctx.instantiateBounds(owner.trait)
                        // UFCS - add predicate `Self : Trait<Args>`
                        val selfTy = subst[TyTypeParameter.self()] ?: ctx.typeVarForParam(TyTypeParameter.self())
                        val newSubst = Substitution(
                            typeSubst = owner.trait.generics.associateBy { it },
                            constSubst = owner.trait.constGenerics.associateBy { it }
                        )
                        val boundTrait = BoundElement(owner.trait, newSubst)
                            .substitute(typeParameters)
                        val traitRef = TraitRef(selfTy, boundTrait)
                        fulfill.registerPredicateObligation(Obligation(Predicate.Trait(traitRef)))
                        when (scopeEntry.source) {
                            is TraitImplSource.Trait, is TraitImplSource.Collapsed -> {
                                ctx.registerPathRefinement(pathExpr, traitRef)
                            }
                        }
                        typeParameters to selfTy
                    }
                    else -> emptySubstitution to null
                }

                if (element is RsGenericDeclaration) {
                    ctx.instantiateBounds(element, selfTy, typeParameters)
                } else {
                    typeParameters
                }
            }
            element is RsEnumVariant -> ctx.instantiateBounds(element.parentEnum)
            element is RsGenericDeclaration -> ctx.instantiateBounds(element)
            else -> emptySubstitution
        }

        unifySubst(subst, typeParameters)

        val type = when (element) {
            is RsPatBinding -> ctx.getBindingType(element)
            is RsTypeDeclarationElement -> element.declaredType
            is RsEnumVariant -> element.parentEnum.declaredType
            is RsFunction -> element.type
            is RsConstant -> element.typeReference?.type ?: TyUnknown
            is RsConstParameter -> element.typeReference?.type ?: TyUnknown
            is RsSelfParameter -> element.typeOfValue
            else -> return TyUnknown
        }
        val tupleFields = (element as? RsFieldsOwner)?.tupleFields
        return if (tupleFields != null) {
            // Treat tuple constructor as a function
            TyFunction(tupleFields.tupleFieldDeclList.map { it.typeReference.type }, type)
        } else {
            type
        }.substitute(typeParameters).foldWith(associatedTypeNormalizer)
    }

    private fun <T : TypeFoldable<T>> normalizeAssociatedTypesIn(ty: T): T {
        val (normTy, obligations) = ctx.normalizeAssociatedTypesIn(ty)
        obligations.forEach(fulfill::registerPredicateObligation)
        return normTy
    }

    private inner class AssociatedTypeNormalizer : TypeFolder {
        override fun foldTy(ty: Ty): Ty = normalizeAssociatedTypesIn(ty)
    }

    private val associatedTypeNormalizer = AssociatedTypeNormalizer()

    private fun unifySubst(subst1: Substitution, subst2: Substitution) {
        subst1.typeSubst.forEach { (k, v1) ->
            subst2[k]?.let { v2 ->
                if (k != v1 && k != TyTypeParameter.self() && v1 !is TyTypeParameter && v1 !is TyUnknown) {
                    ctx.combineTypes(v2, v1)
                }
            }
        }
        subst1.constSubst.forEach { (k, c1) ->
            subst2[k]?.let { c2 ->
                if (k != c1 && c1 !is CtConstParameter && c1 !is CtUnknown) {
                    ctx.combineConsts(c2, c1)
                }
            }
        }
        // TODO take into account the lifetimes
    }

    private fun inferStructLiteralType(expr: RsStructLiteral, expected: Ty?): Ty {
        val boundElement = expr.path.reference?.advancedDeepResolve()

        if (boundElement == null) {
            for (field in expr.structLiteralBody.structLiteralFieldList) {
                field.expr?.inferType()
            }
            // Handle struct update syntax { ..expression }
            expr.structLiteralBody.expr?.inferType()
            return TyUnknown
        }

        val (element, subst) = boundElement

        val genericDecl: RsGenericDeclaration? = when (element) {
            is RsStructItem -> element
            is RsEnumVariant -> element.parentEnum
            else -> null
        }

        val typeParameters = genericDecl?.let { ctx.instantiateBounds(it) } ?: emptySubstitution
        unifySubst(subst, typeParameters)
        if (expected != null) unifySubst(typeParameters, expected.typeParameterValues)

        val type = when (element) {
            is RsStructItem -> element.declaredType
            is RsEnumVariant -> element.parentEnum.declaredType
            else -> TyUnknown
        }.substitute(typeParameters)

        inferStructTypeArguments(expr, typeParameters)

        // Handle struct update syntax { ..expression }
        expr.structLiteralBody.expr?.inferTypeCoercableTo(type)

        return type
    }

    private fun inferStructTypeArguments(literal: RsStructLiteral, typeParameters: Substitution) {
        literal.structLiteralBody.structLiteralFieldList.filterNotNull().forEach { field ->
            val fieldTy = field.type.substitute(typeParameters)
            val expr = field.expr

            if (expr != null) {
                expr.inferTypeCoercableTo(fieldTy)
            } else {
                val bindingTy = field.resolveToBinding()?.let { ctx.getBindingType(it) } ?: TyUnknown
                coerce(field, bindingTy, fieldTy)
            }
        }
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

    private fun inferCallExprType(expr: RsCallExpr, expected: Ty?): Ty {
        val callee = expr.expr
        val ty = resolveTypeVarsWithObligations(callee.inferType()) // or error
        // `struct S; S();`
        if (callee is RsPathExpr) {
            ctx.getResolvedPath(callee).singleOrNull()?.element?.let {
                if (it is RsFieldsOwner && it.isFieldless) {
                    return ty
                }
            }
        }
        val argExprs = expr.valueArgumentList.exprList
        val calleeType = lookup.asTyFunction(ty)?.register() ?: unknownTyFunction(argExprs.size)
        if (expected != null) ctx.combineTypes(expected, calleeType.retType)
        inferArgumentTypes(calleeType.paramTypes, argExprs)
        return calleeType.retType
    }

    private fun inferMethodCallExprType(
        receiver: Ty,
        methodCall: RsMethodCall,
        expected: Ty?
    ): Ty {
        val argExprs = methodCall.valueArgumentList.exprList
        val callee = run {
            val variants = resolveMethodCallReferenceWithReceiverType(lookup, receiver, methodCall)
            val callee = pickSingleMethod(receiver, variants, methodCall)
            // If we failed to resolve ambiguity just write the all possible methods
            val variantsForDisplay = (callee?.let(::listOf) ?: variants)
            ctx.writeResolvedMethod(methodCall, variantsForDisplay)

            callee ?: variants.firstOrNull()
        }
        if (callee == null) {
            val methodType = unknownTyFunction(argExprs.size)
            inferArgumentTypes(methodType.paramTypes, argExprs)
            return methodType.retType
        }

        inferConstArgumentTypes(callee.element.constParameters, methodCall.constArguments)

        var newSubst = ctx.instantiateMethodOwnerSubstitution(callee, methodCall)

        // TODO: borrow adjustments for self parameter
        /*
        if (callee.selfTy is TyReference) {
            val adjustment = BorrowReference( callee.selfTy)
            ctx.addAdjustment(methodCall.receiver, adjustment)
        }
        */

        newSubst = ctx.instantiateBounds(callee.element, callee.selfTy, newSubst)

        val typeParameters = callee.element.typeParameters.map { TyTypeParameter.named(it) }
        val typeArguments = methodCall.typeArguments.map { it.type }
        val typeSubst = typeParameters.zip(typeArguments).toMap()

        val constParameters = callee.element.constParameters.map { CtConstParameter(it) }
        val resolver = PathExprResolver.fromContext(ctx)
        val constArguments = methodCall.constArguments.withIndex().map { (i, expr) ->
            val expectedTy = constParameters.getOrNull(i)?.parameter?.typeReference?.type ?: TyUnknown
            expr.evaluate(expectedTy, resolver)
        }
        val constSubst = constParameters.zip(constArguments).toMap()

        val fnSubst = Substitution(typeSubst = typeSubst, constSubst = constSubst)
        unifySubst(fnSubst, newSubst)

        val methodType = (callee.element.type)
            .substitute(newSubst)
            .foldWith(associatedTypeNormalizer) as TyFunction
        if (expected != null && !callee.element.isAsync) ctx.combineTypes(expected, methodType.retType)
        // drop first element of paramTypes because it's `self` param
        // and it doesn't have value in `methodCall.valueArgumentList.exprList`
        inferArgumentTypes(methodType.paramTypes.drop(1), argExprs)

        return methodType.retType
    }

    private fun <T : AssocItemScopeEntryBase<E>, E> filterAssocItems(variants: List<T>, context: RsElement): List<T> {
        return variants.singleOrLet { list ->
            // 1. filter traits that are not imported
            TypeInferenceMarks.methodPickTraitScope.hit()
            val traitToCallee = hashMapOf<RsTraitItem, MutableList<T>>()
            val filtered = mutableListOf<T>()
            for (callee in list) {
                val trait = callee.source.requiredTraitInScope
                if (trait != null) {
                    traitToCallee.getOrPut(trait) { mutableListOf() }.add(callee)
                } else {
                    filtered.add(callee) // inherent impl
                }
            }
            traitToCallee.keys.filterInScope(context).forEach {
                filtered += traitToCallee.getValue(it)
            }
            if (filtered.isNotEmpty()) {
                filtered
            } else {
                TypeInferenceMarks.methodPickTraitsOutOfScope.hit()
                list
            }
        }.singleOrFilter { callee ->
            // 2. Filter methods by trait bounds (try to select all obligations for each impl)
            TypeInferenceMarks.methodPickCheckBounds.hit()
            ctx.canEvaluateBounds(callee.source, callee.selfTy)
        }
    }

    private fun pickSingleMethod(
        receiver: Ty,
        variants: List<MethodResolveVariant>,
        methodCall: RsMethodCall
    ): MethodResolveVariant? {
        val filtered = filterAssocItems(variants, methodCall).singleOrLet { list ->
            // 3. Pick results matching receiver type
            TypeInferenceMarks.methodPickDerefOrder.hit()

            fun pick(ty: Ty): List<MethodResolveVariant> =
                list.filter { it.element.selfParameter?.typeOfValue(it.selfTy) == ty }

            // https://github.com/rust-lang/rust/blob/a646c912/src/librustc_typeck/check/method/probe.rs#L885
            lookup.coercionSequence(receiver).mapNotNull { ty ->
                pick(ty)
                    // TODO do something with lifetimes
                    .notEmptyOrLet { pick(TyReference(ty, Mutability.IMMUTABLE)) }
                    .notEmptyOrLet { pick(TyReference(ty, Mutability.MUTABLE)) }
                    .takeIf { it.isNotEmpty() }
            }.firstOrNull() ?: emptyList()
        }

        return when (filtered.size) {
            0 -> null
            1 -> filtered.single()
            else -> {
                // 4. Try to collapse multiple resolved methods of the same trait, e.g.
                // ```rust
                // trait Foo<T> { fn foo(&self, _: T) {} }
                // impl Foo<Bar> for S { fn foo(&self, _: Bar) {} }
                // impl Foo<Baz> for S { fn foo(&self, _: Baz) {} }
                // ```
                // In this case we `filtered` list contains 2 function defined in 2 impls.
                // We want to collapse them to the single function defined in the trait.
                // Specific impl will be selected later according to the method parameter type.
                val first = filtered.first()
                collapseToTrait(filtered.map { it.element })?.let { fn ->
                    TypeInferenceMarks.methodPickCollapseTraits.hit()
                    MethodResolveVariant(
                        first.name,
                        fn,
                        first.selfTy,
                        first.derefCount,
                        TraitImplSource.Collapsed((fn.owner as RsAbstractableOwner.Trait).trait)
                    )
                }
            }
        }
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

            val argDefsExt = argDefs.asSequence()
                .map(ctx::resolveTypeVarsIfPossible)
                // extending argument definitions to be sure that type inference launched for each arg expr
                .infiniteWithTyUnknown()
            for ((type, expr) in argDefsExt.zip(argExprs.asSequence().map(::unwrapParenExprs))) {
                val isLambda = expr is RsLambdaExpr
                if (isLambda != checkLambdas) continue

                expr.inferTypeCoercableTo(type)
            }
        }
    }

    fun inferConstArgumentTypes(constParameters: List<RsConstParameter>, constArguments: List<RsExpr>) {
        inferArgumentTypes(constParameters.map { it.typeReference?.type ?: TyUnknown }, constArguments)
    }

    private fun inferFieldExprType(receiver: Ty, fieldLookup: RsFieldLookup): Ty {
        if (fieldLookup.identifier?.text == "await" && fieldLookup.isEdition2018) {
            return receiver.lookupFutureOutputTy(lookup)
        }

        val variants = resolveFieldLookupReferenceWithReceiverType(lookup, receiver, fieldLookup)
        ctx.writeResolvedField(fieldLookup, variants.map { it.element })
        val field = variants.firstOrNull()
        if (field == null) {
            for ((index, type) in lookup.coercionSequence(receiver).withIndex()) {
                if (type is TyTuple) {
                    ctx.addAdjustment(fieldLookup.parentDotExpr.expr, Adjustment.Deref(receiver), index)
                    val fieldIndex = fieldLookup.integerLiteral?.text?.toIntOrNull() ?: return TyUnknown
                    return type.types.getOrElse(fieldIndex) { TyUnknown }
                }
            }
            return TyUnknown
        }
        ctx.addAdjustment(fieldLookup.parentDotExpr.expr, Adjustment.Deref(receiver), field.derefCount)

        val fieldElement = field.element

        val raw = (fieldElement as? RsFieldDecl)?.typeReference?.type ?: TyUnknown
        return raw.substitute(field.selfTy.typeParameterValues).foldWith(associatedTypeNormalizer)
    }

    private fun inferDotExprType(expr: RsDotExpr, expected: Ty?): Ty {
        val receiver = resolveTypeVarsWithObligations(expr.expr.inferType())
        val methodCall = expr.methodCall
        val fieldLookup = expr.fieldLookup
        return when {
            methodCall != null -> inferMethodCallExprType(receiver, methodCall, expected)
            fieldLookup != null -> inferFieldExprType(receiver, fieldLookup)
            else -> TyUnknown
        }
    }

    private fun inferLoopExprType(expr: RsLoopExpr): Ty {
        expr.block?.inferType()
        return inferLabeledExprType(expr, TyNever, false)
    }

    private fun inferLabeledExprType(expr: RsLabeledExpression, baseType: Ty, matchOnlyByLabel: Boolean): Ty {
        val returningTypes = mutableListOf(baseType)
        val label = expr.takeIf { it.block?.stub == null }?.labelDecl?.name

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

        if (label != null || !matchOnlyByLabel) {
            collectReturningTypes(expr, matchOnlyByLabel)
        }
        return getMoreCompleteType(returningTypes)
    }

    private fun inferForExprType(expr: RsForExpr): Ty {
        val exprTy = resolveTypeVarsWithObligations(expr.expr?.inferType() ?: TyUnknown)
        val itemTy = resolveTypeVarsWithObligations(lookup.findIteratorItemType(exprTy)?.register() ?: TyUnknown)
        expr.pat?.extractBindings(itemTy)
        expr.block?.inferType()
        return TyUnit
    }

    private fun inferWhileExprType(expr: RsWhileExpr): Ty {
        expr.condition?.inferTypes()
        expr.block?.inferType()
        return TyUnit
    }

    private fun inferMatchExprType(expr: RsMatchExpr, expected: Ty?): Ty {
        val matchingExprTy = resolveTypeVarsWithObligations(expr.expr?.inferType() ?: TyUnknown)
        val arms = expr.arms
        for (arm in arms) {
            arm.orPats.extractBindings(matchingExprTy)
            arm.expr?.inferType(expected)
            arm.matchArmGuard?.expr?.inferType(TyBool)
        }

        return getMoreCompleteType(arms.mapNotNull { it.expr?.let(ctx::getExprType) })
    }

    private fun inferUnaryExprType(expr: RsUnaryExpr, expected: Ty?): Ty {
        val innerExpr = expr.expr ?: return TyUnknown
        return when (expr.operatorType) {
            UnaryOperator.REF -> inferRefType(innerExpr, expected, Mutability.IMMUTABLE)
            UnaryOperator.REF_MUT -> inferRefType(innerExpr, expected, Mutability.MUTABLE)
            UnaryOperator.DEREF -> {
                // expectation must NOT be used for deref
                val base = resolveTypeVarsWithObligations(innerExpr.inferType())
                val deref = lookup.deref(base)
                if (deref == null && base != TyUnknown) {
                    ctx.addDiagnostic(RsDiagnostic.DerefError(expr, base))
                }
                deref ?: TyUnknown
            }
            UnaryOperator.MINUS -> innerExpr.inferType(expected)
            UnaryOperator.NOT -> innerExpr.inferType(expected)
            UnaryOperator.BOX -> {
                val expectedInner = (expected as? TyAdt)?.takeIf { it.item == items.Box }?.typeArguments?.getOrNull(0)
                items.makeBox(innerExpr.inferType(expectedInner))
            }
        }
    }

    private fun inferRefType(expr: RsExpr, expected: Ty?, mutable: Mutability): Ty =
        TyReference(expr.inferType((expected as? TyReference)?.referenced), mutable) // TODO infer the actual lifetime

    private fun inferIfExprType(expr: RsIfExpr, expected: Ty?): Ty {
        expr.condition?.inferTypes()
        val blockTys = mutableListOf<Ty?>()
        blockTys.add(expr.block?.inferType(expected))
        val elseBranch = expr.elseBranch
        if (elseBranch != null) {
            blockTys.add(elseBranch.ifExpr?.inferType(expected))
            blockTys.add(elseBranch.block?.inferType(expected))
        }
        return if (expr.elseBranch == null) TyUnit else getMoreCompleteType(blockTys.filterNotNull())
    }

    private fun RsCondition.inferTypes() {
        val orPats = orPats
        if (orPats != null) {
            // if let Some(a) = ... {}
            // if let V1(a) | V2(a) = ... {}
            // or
            // while let Some(a) = ... {}
            // while let V1(a) | V2(a) = ... {}
            val exprTy = resolveTypeVarsWithObligations(expr.inferType())
            orPats.extractBindings(exprTy)
        } else {
            expr.inferType(TyBool)
        }
    }

    private fun inferBinaryExprType(expr: RsBinaryExpr): Ty {
        val lhsType = resolveTypeVarsWithObligations(expr.left.inferType())
        val op = expr.operatorType
        val (rhsType, retTy) = when (op) {
            is BoolOp -> {
                if (op is OverloadableBinaryOperator) {
                    val rhsTypeVar = TyInfer.TyVar()
                    enforceOverloadedBinopTypes(lhsType, rhsTypeVar, op)
                    val rhsType = resolveTypeVarsWithObligations(
                        expr.right?.inferTypeCoercableTo(rhsTypeVar)
                            ?: TyUnknown
                    )

                    val lhsAdjustment = Adjustment.BorrowReference(TyReference(lhsType, Mutability.IMMUTABLE))
                    ctx.addAdjustment(expr.left, lhsAdjustment)

                    val rhsAdjustment = Adjustment.BorrowReference(TyReference(rhsType, Mutability.IMMUTABLE))
                    expr.right?.let { ctx.addAdjustment(it, rhsAdjustment) }

                    rhsType to TyBool
                } else {
                    val rhsType = resolveTypeVarsWithObligations(expr.right?.inferTypeCoercableTo(lhsType) ?: TyUnknown)
                    rhsType to TyBool
                }
            }
            is ArithmeticOp -> {
                val rhsTypeVar = TyInfer.TyVar()
                val retTy = lookup.findArithmeticBinaryExprOutputType(lhsType, rhsTypeVar, op)?.register() ?: TyUnknown
                val rhsType = resolveTypeVarsWithObligations(expr.right?.inferTypeCoercableTo(rhsTypeVar) ?: TyUnknown)
                rhsType to retTy
            }
            is ArithmeticAssignmentOp -> {
                val rhsTypeVar = TyInfer.TyVar()
                enforceOverloadedBinopTypes(lhsType, rhsTypeVar, op)
                val rhsType = resolveTypeVarsWithObligations(expr.right?.inferTypeCoercableTo(rhsTypeVar) ?: TyUnknown)

                val lhsAdjustment = Adjustment.BorrowReference(TyReference(lhsType, Mutability.MUTABLE))
                ctx.addAdjustment(expr.left, lhsAdjustment)

                rhsType to TyUnit
            }
            AssignmentOp.EQ -> {
                val rhsType = expr.right?.inferTypeCoercableTo(lhsType) ?: TyUnknown
                rhsType to TyUnit
            }
        }

        if (op != AssignmentOp.EQ && isBuiltinBinop(lhsType, rhsType, op)) {
            val builtinRetTy = enforceBuiltinBinopTypes(lhsType, rhsType, op)
            if (op !is ArithmeticAssignmentOp) {
                ctx.combineTypes(builtinRetTy, retTy)
            }
        }

        return retTy
    }

    private fun enforceOverloadedBinopTypes(lhsType: Ty, rhsType: Ty, op: OverloadableBinaryOperator) {
        selectOverloadedOp(lhsType, rhsType, op)?.let { fulfill.registerPredicateObligation(it) }
    }

    private fun selectOverloadedOp(lhsType: Ty, rhsType: Ty, op: OverloadableBinaryOperator): Obligation? {
        val trait = op.findTrait(items) ?: return null
        return Obligation(Predicate.Trait(TraitRef(lhsType, trait.withSubst(rhsType))))
    }

    private fun isBuiltinBinop(lhsType: Ty, rhsType: Ty, op: BinaryOperator): Boolean = when (op.category) {
        BinOpCategory.Shortcircuit -> true

        BinOpCategory.Shift -> lhsType.isIntegral && rhsType.isIntegral

        BinOpCategory.Math -> lhsType.isIntegral && rhsType.isIntegral ||
            lhsType.isFloat && rhsType.isFloat

        BinOpCategory.Bitwise -> lhsType.isIntegral && rhsType.isIntegral ||
            lhsType.isFloat && rhsType.isFloat ||
            lhsType == TyBool && rhsType == TyBool

        BinOpCategory.Comparison -> lhsType.isScalar && rhsType.isScalar
    }

    private fun enforceBuiltinBinopTypes(lhsType: Ty, rhsType: Ty, op: BinaryOperator): Ty = when (op.category) {
        BinOpCategory.Shortcircuit -> {
            ctx.combineTypes(lhsType, TyBool)
            ctx.combineTypes(lhsType, TyBool)
            TyBool
        }

        BinOpCategory.Shift -> lhsType

        BinOpCategory.Math, BinOpCategory.Bitwise -> {
            ctx.combineTypes(lhsType, rhsType)
            lhsType
        }

        BinOpCategory.Comparison -> {
            ctx.combineTypes(lhsType, rhsType)
            TyBool
        }
    }

    private fun inferTryExprType(expr: RsTryExpr): Ty {
        val base = expr.expr.inferType() as? TyAdt ?: return TyUnknown
        // TODO: make it work with generic `std::ops::Try` trait
        if (base.item != items.Result && base.item != items.Option) return TyUnknown
        TypeInferenceMarks.questionOperator.hit()
        return base.typeArguments.getOrElse(0) { TyUnknown }
    }

    private fun inferTryMacroArgumentType(exprTy: Ty): Ty {
        val base = exprTy as? TyAdt ?: return TyUnknown
        if (base.item != items.Result) return TyUnknown
        return base.typeArguments.firstOrNull() ?: TyUnknown
    }

    private fun inferRangeType(expr: RsRangeExpr): Ty {
        val el = expr.exprList
        val dot2 = expr.dotdot
        val dot3 = expr.dotdotdot ?: expr.dotdoteq

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
            else -> return TyUnknown
        }

        return items.findRangeTy(rangeName, indexType)
    }

    private fun inferIndexExprType(expr: RsIndexExpr): Ty {
        fun isArrayToSlice(prevType: Ty?, type: Ty?): Boolean =
            prevType is TyArray && type is TySlice

        val containerExpr = expr.containerExpr ?: return TyUnknown
        val indexExpr = expr.indexExpr ?: return TyUnknown

        val containerType = containerExpr.inferType()
        val indexType = ctx.resolveTypeVarsIfPossible(indexExpr.inferType())

        if (indexType is TyReference) {
            ctx.addAdjustment(indexExpr, Adjustment.BorrowReference(indexType)) // TODO
        }

        var derefCount = -1 // starts with -1 because the fist element of the coercion sequence is the type itself
        var prevType: Ty? = null
        var result: Ty = TyUnknown
        for (type in lookup.coercionSequence(containerType)) {
            if (!isArrayToSlice(prevType, type)) derefCount++

            val outputType = lookup.findIndexOutputType(type, indexType)
            if (outputType != null) {
                result = outputType.register()
                break
            }

            prevType = type
        }

        ctx.addAdjustment(containerExpr, Adjustment.Deref(containerType), derefCount)
        return result
    }

    private fun inferMacroExprType(macroExpr: RsMacroExpr, expected: Ty?): Ty {
        if (macroExprDepth > DEFAULT_RECURSION_LIMIT) {
            TypeInferenceMarks.macroExprDepthLimitReached.hit()
            return TyUnknown
        }
        macroExprDepth++
        try {
            return inferMacroExprType0(macroExpr, expected)
        } finally {
            macroExprDepth--
        }
    }

    private fun inferMacroExprType0(macroExpr: RsMacroExpr, expected: Ty?): Ty {
        val macroCall = macroExpr.macroCall
        val name = macroCall.macroName
        val exprArg = macroCall.exprMacroArgument
        if (exprArg != null) {
            val type = exprArg.expr?.inferType() ?: return TyUnknown
            return when (name) {
                "try" -> inferTryMacroArgumentType(type)
                "dbg" -> type
                "await" -> type.lookupFutureOutputTy(lookup)
                else -> TyUnknown
            }
        }

        val vecArg = macroCall.vecMacroArgument
        if (vecArg != null) {
            val expectedElemTy = (expected as? TyAdt)?.takeIf { it.item == items.Vec }?.typeArguments?.getOrNull(0)
            val elementType = if (vecArg.semicolon != null) {
                // vec![value; repeat]
                run {
                    val exprList = vecArg.exprList
                    val valueExpr = exprList.firstOrNull() ?: return@run TyUnknown
                    exprList.getOrNull(1)?.let { inferTypeCoercableTo(it, TyInteger.USize) }
                    expectedElemTy?.let { valueExpr.inferTypeCoercableTo(it) } ?: valueExpr.inferType()
                }
            } else {
                // vec![value1, value2, value3]
                val elementTypes = vecArg.exprList.map { it.inferType(expectedElemTy) }
                val elementType = if (elementTypes.isNotEmpty()) getMoreCompleteType(elementTypes) else TyInfer.TyVar()

                if (expectedElemTy != null && tryCoerce(elementType, expectedElemTy).isOk) {
                    expectedElemTy
                } else {
                    elementType
                }
            }
            return items.findVecForElementTy(elementType)
        }

        inferChildExprsRecursively(macroCall)
        return when {
            macroCall.assertMacroArgument != null || macroCall.logMacroArgument != null -> TyUnit
            macroCall.formatMacroArgument != null -> inferFormatMacro(macroCall)
            macroCall.includeMacroArgument != null -> inferIncludeMacro(macroCall)
            name == "env" -> TyReference(TyStr, Mutability.IMMUTABLE)
            name == "option_env" -> items.findOptionForElementTy(TyReference(TyStr, Mutability.IMMUTABLE))
            name == "concat" -> TyReference(TyStr, Mutability.IMMUTABLE)
            name == "line" || name == "column" -> TyInteger.U32
            name == "file" -> TyReference(TyStr, Mutability.IMMUTABLE)
            name == "stringify" -> TyReference(TyStr, Mutability.IMMUTABLE)
            name == "module_path" -> TyReference(TyStr, Mutability.IMMUTABLE)
            name == "cfg" -> TyBool
            else -> (macroCall.expansion as? MacroExpansion.Expr)?.expr?.inferType() ?: TyUnknown
        }
    }

    private fun inferIncludeMacro(macroCall: RsMacroCall): Ty {
        return when (macroCall.macroName) {
            "include_str" -> TyReference(TyStr, Mutability.IMMUTABLE)
            "include_bytes" -> TyReference(TyArray(TyInteger.U8, CtUnknown), Mutability.IMMUTABLE)
            else -> TyUnknown
        }
    }

    private fun inferFormatMacro(macroCall: RsMacroCall): Ty {
        val name = macroCall.macroName
        return when {
            "print" in name -> TyUnit
            name == "format" -> items.String.asTy()
            name == "format_args" -> items.Arguments.asTy()
            name == "unimplemented" || name == "unreachable" || name == "panic" -> TyNever
            name == "write" || name == "writeln" -> {
                (macroCall.expansion as? MacroExpansion.Expr)?.expr?.inferType() ?: TyUnknown
            }
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
        val params = expr.valueParameters
        val expectedFnTy = expected
            ?.let(this::deduceLambdaType)
            ?: unknownTyFunction(params.size)
        val extendedArgs = expectedFnTy.paramTypes.asSequence().infiniteWithTyUnknown()
        val paramTypes = extendedArgs.zip(params.asSequence()).map { (expectedArg, actualArg) ->
            val paramTy = actualArg.typeReference?.type ?: expectedArg
            actualArg.pat?.extractBindings(paramTy)
            paramTy
        }.toList()
        val expectedRetTy = expr.retType?.typeReference?.type
            ?: expectedFnTy.retType.takeIf { it != TyUnknown }
        val isFreshRetTy = expectedRetTy == null
        val retTy = expectedRetTy ?: TyInfer.TyVar()

        val lambdaBodyContext = RsTypeInferenceWalker(ctx, retTy)
        expr.expr?.let { lambdaBodyContext.inferLambdaBody(it) }
        val isDefaultRetTy = isFreshRetTy && retTy is TyInfer.TyVar && !ctx.isTypeVarAffected(retTy)
        val actualRetTy = if (isDefaultRetTy) TyUnit else retTy

        val yieldTy = lambdaBodyContext.yieldTy
        return if (yieldTy == null) {
            TyFunction(paramTypes, if (expr.isAsync) items.makeFuture(actualRetTy) else actualRetTy)
        } else {
            items.makeGenerator(yieldTy, actualRetTy)
        }
    }

    private fun deduceLambdaType(expected: Ty): TyFunction? {
        return when (expected) {
            is TyInfer.TyVar -> {
                fulfill.pendingObligations
                    .mapNotNull { it.obligation.predicate as? Predicate.Trait }
                    .find { it.trait.selfTy == expected && it.trait.trait.element in listOf(items.Fn, items.FnMut, items.FnOnce) }
                    ?.let { lookup.asTyFunction(it.trait.trait) }
            }
            is TyTraitObject -> lookup.asTyFunction(expected.trait)
            is TyFunction -> expected
            is TyAnon -> {
                val trait = expected.traits.find { it.element in listOf(items.Fn, items.FnMut, items.FnOnce) }
                trait?.let { lookup.asTyFunction(it) }
            }
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
            val sizeExpr = expr.sizeExpr
            sizeExpr?.inferType(TyInteger.USize)
            val size = sizeExpr?.evaluate(TyInteger.USize, PathExprResolver.fromContext(ctx)) ?: CtUnknown
            elementType to size
        } else {
            val elementTypes = expr.arrayElements?.map { it.inferType(expectedElemTy) }
            val size = if (elementTypes != null) {
                val size = elementTypes.size.toLong()
                ConstExpr.Value.Integer(size, TyInteger.USize).toConst()
            } else {
                CtUnknown
            }
            if (elementTypes.isNullOrEmpty()) {
                return TyArray(TyInfer.TyVar(), size.foldCtConstParameterWith { CtInferVar(it) })
            }

            // '!!' is safe here because we've just checked that elementTypes isn't null
            val elementType = getMoreCompleteType(elementTypes!!)
            val inferredTy = if (expectedElemTy != null && tryCoerce(elementType, expectedElemTy).isOk) {
                expectedElemTy
            } else {
                elementType
            }
            inferredTy to size
        }

        return TyArray(elementType, size)
    }

    private fun inferYieldExprType(expr: RsYieldExpr): Ty {
        val oldYieldTy = yieldTy
        if (oldYieldTy == null) {
            yieldTy = expr.expr?.inferType()
        } else {
            expr.expr?.inferTypeCoercableTo(oldYieldTy)
        }
        return TyUnit
    }

    private fun inferRetExprType(expr: RsRetExpr): Ty {
        expr.expr?.inferTypeCoercableTo(returnTy)
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
    private fun getMoreCompleteType(ty1: Ty, ty2: Ty): Ty = when (ty1) {
        is TyNever -> ty2
        is TyUnknown -> if (ty2 !is TyNever) ty2 else TyUnknown
        else -> {
            ctx.combineTypes(ty1, ty2)
            ty1
        }
    }

    private fun registerTryProjection(resultTy: Ty, assocTypeName: String, assocTypeTy: Ty) {
        val tryTrait = items.Try ?: return
        val assocType = tryTrait.findAssociatedType(assocTypeName) ?: return
        val projection = TyProjection.valueOf(resultTy, assocType)
        val obligation = Obligation(Predicate.Projection(projection, assocTypeTy))
        fulfill.registerPredicateObligation(obligation)
    }

    private fun <T> TyWithObligations<T>.register(): T {
        obligations.forEach(fulfill::registerPredicateObligation)
        return value
    }

    fun extractParameterBindings(fn: RsFunction) {
        for (param in fn.valueParameters) {
            param.pat?.extractBindings(param.typeReference?.type ?: TyUnknown)
        }
    }

    private fun RsPat.extractBindings(ty: Ty) {
        extractBindings(this@RsTypeInferenceWalker, ty)
    }

    private fun RsOrPats.extractBindings(ty: Ty) {
        patList.forEach { it.extractBindings(ty) }
    }

    fun writePatTy(psi: RsPat, ty: Ty): Unit =
        ctx.writePatTy(psi, ty)

    fun writePatFieldTy(psi: RsPatField, ty: Ty): Unit =
        ctx.writePatFieldTy(psi, ty)

    private fun Ty.lookupFutureOutputTy(lookup: ImplLookup): Ty {
        val futureTrait = lookup.items.Future ?: return TyUnknown
        val outputType = futureTrait.findAssociatedType("Output") ?: return TyUnknown
        val selection = lookup.selectProjection(TraitRef(this, futureTrait.withSubst()), outputType)
        return selection.ok()?.register() ?: TyUnknown
    }

    companion object {
        // ignoring possible false-positives (it's only basic experimental type checking)

        val IGNORED_TYS: List<Class<out Ty>> = listOf(
            TyUnknown::class.java,
            TyInfer.TyVar::class.java,
            TyTypeParameter::class.java,
            TyProjection::class.java,
            TyTraitObject::class.java,
            TyAnon::class.java
        )

        val IGNORED_CONSTS: List<Class<out Const>> = listOf(
            CtUnknown::class.java,
            CtInferVar::class.java
        )
    }
}

private val RsSelfParameter.typeOfValue: Ty
    get() {
        val selfType = when (val owner = parentFunction.owner) {
            is RsAbstractableOwner.Impl -> owner.impl.selfType
            is RsAbstractableOwner.Trait -> owner.trait.selfType
            else -> return TyUnknown
        }

        return typeOfValue(selfType)
    }

private fun RsSelfParameter.typeOfValue(selfType: Ty): Ty {
    if (isExplicitType) {
        // self: Self, self: &Self, self: &mut Self, self: Box<Self>
        val ty = this.typeReference?.type ?: TyUnknown
        return ty.substitute(mapOf(TyTypeParameter.self() to selfType).toTypeSubst())
    }

    // self, &self, &mut self
    return if (isRef) TyReference(selfType, mutability, lifetime.resolve()) else selfType

}

val RsFunction.type: TyFunction
    get() {
        val paramTypes = mutableListOf<Ty>()

        val self = selfParameter
        if (self != null) {
            paramTypes += self.typeOfValue
        }

        paramTypes += valueParameters.map { it.typeReference?.type ?: TyUnknown }

        return TyFunction(paramTypes, if (isAsync) knownItems.makeFuture(returnType) else returnType)
    }

private fun Sequence<Ty>.infiniteWithTyUnknown(): Sequence<Ty> =
    this + generateSequence { TyUnknown }

private fun KnownItems.findVecForElementTy(elementTy: Ty): Ty {
    val ty = Vec?.declaredType ?: TyUnknown

    val typeParameter = ty.getTypeParameter("T") ?: return ty
    return ty.substitute(mapOf(typeParameter to elementTy).toTypeSubst())
}

private fun KnownItems.findOptionForElementTy(elementTy: Ty): Ty {
    val ty = Option?.declaredType ?: TyUnknown

    val typeParameter = ty.getTypeParameter("T") ?: return ty
    return ty.substitute(mapOf(typeParameter to elementTy).toTypeSubst())
}

private fun KnownItems.findRangeTy(rangeName: String, indexType: Ty?): Ty {
    val ty = findItem<RsNamedElement>("core::ops::$rangeName")?.asTy() ?: TyUnknown

    if (indexType == null) return ty

    val typeParameter = ty.getTypeParameter("Idx") ?: return ty
    return ty.substitute(mapOf(typeParameter to indexType).toTypeSubst())
}

private fun KnownItems.makeBox(innerTy: Ty): Ty {
    val box = Box ?: return TyUnknown
    val boxTy = TyAdt.valueOf(box)
    return boxTy.substitute(mapOf(boxTy.typeArguments[0] as TyTypeParameter to innerTy).toTypeSubst())
}

private fun KnownItems.makeGenerator(yieldTy: Ty, returnTy: Ty): Ty {
    val generatorTrait = Generator ?: return TyUnknown
    val boundGenerator = generatorTrait
        .substAssocType("Yield", yieldTy)
        .substAssocType("Return", returnTy)
    return TyAnon(null, listOf(boundGenerator))
}

private fun KnownItems.makeFuture(outputTy: Ty): Ty {
    val futureTrait = Future ?: return TyUnknown
    val boundFuture = futureTrait.substAssocType("Output", outputTy)
    return TyAnon(null, listOf(boundFuture))
}
