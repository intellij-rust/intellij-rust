/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.macros.MacroExpansion
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.ext.RsBindingModeKind.BindByReference
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.ref.*
import org.rust.lang.core.stubs.RsStubLiteralKind
import org.rust.lang.core.types.*
import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.consts.CtInferVar
import org.rust.lang.core.types.consts.CtUnknown
import org.rust.lang.core.types.infer.Autoderef.AutoderefKind.ArrayToSlice
import org.rust.lang.core.types.infer.Expectation.ExpectHasType
import org.rust.lang.core.types.infer.Expectation.NoExpectation
import org.rust.lang.core.types.infer.MethodPick.AutorefOrPtrAdjustment.Autoref
import org.rust.lang.core.types.regions.ReStatic
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.ty.Mutability.IMMUTABLE
import org.rust.lang.core.types.ty.Mutability.MUTABLE
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.evaluation.ConstExpr
import org.rust.lang.utils.evaluation.PathExprResolver
import org.rust.lang.utils.evaluation.evaluate
import org.rust.lang.utils.evaluation.toConst
import org.rust.openapiext.forEachChild
import org.rust.stdext.RsResult
import org.rust.stdext.RsResult.Ok
import org.rust.stdext.notEmptyOrLet
import org.rust.stdext.singleOrFilter
import org.rust.stdext.singleOrLet

class RsTypeInferenceWalker(
    val ctx: RsInferenceContext,
    private val returnTy: Ty
) {
    private var tryTy: Ty? = returnTy
    private var yieldTy: Ty? = null
    private val lookup get() = ctx.lookup
    private val items get() = ctx.items
    private val fulfill get() = ctx.fulfill
    private val RsStructLiteralField.type: Ty get() = resolveToDeclaration()?.typeReference?.rawType ?: TyUnknown

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

    private fun inferBlockExprType(blockExpr: RsBlockExpr, expected: Expectation = NoExpectation): Ty =
        when {
            blockExpr.isTry -> inferTryBlockExprType(blockExpr, expected)
            blockExpr.isAsync -> inferAsyncBlockExprType(blockExpr, expected)
            else -> {
                val type = blockExpr.block.inferType(expected)
                inferLabeledExprType(blockExpr, type, true)
            }
        }

    private fun inferTryBlockExprType(blockExpr: RsBlockExpr, expected: Expectation = NoExpectation): Ty {
        require(blockExpr.isTry)
        val oldTryTy = tryTy
        try {
            tryTy = expected.onlyHasTy(ctx) ?: TyInfer.TyVar()
            val resultTy = tryTy ?: TyUnknown
            val okTy = blockExpr.block.inferType()
            registerTryProjection(resultTy, okTy)
            return resultTy
        } finally {
            tryTy = oldTryTy
        }
    }

    private fun inferAsyncBlockExprType(blockExpr: RsBlockExpr, expected: Expectation = NoExpectation): Ty {
        require(blockExpr.isAsync)
        val retTy = expected
            .tyAsNullable(ctx)
            ?.let(::resolveTypeVarsWithObligations)
            .takeUnless { it is TyInfer.TyVar }
            ?.lookupFutureOutputTy(lookup)
            ?: TyInfer.TyVar()
        RsTypeInferenceWalker(ctx, retTy).apply {
            blockExpr.block.inferType(ExpectHasType(retTy), coerce = true)
        }
        return items.makeFuture(resolveTypeVarsWithObligations(retTy))
    }

    fun inferFnBody(block: RsBlock): Ty =
        block.inferTypeCoercableTo(returnTy)

    fun inferLambdaBody(expr: RsExpr): Ty = expr.inferTypeCoercableTo(returnTy)

    private fun RsBlock.inferTypeCoercableTo(expected: Ty): Ty =
        inferType(ExpectHasType(expected), true)

    private fun RsBlock.inferType(expected: Expectation = NoExpectation, coerce: Boolean = false): Ty {
        var isDiverging = false
        val (expandedStmts, tailExpr) = expandedStmtsAndTailExpr
        for (stmt in expandedStmts) {
            val result = processStatement(stmt)
            isDiverging = result || isDiverging
        }
        val type = if (coerce && expected is ExpectHasType) {
            tailExpr?.inferTypeCoercableTo(expected.ty)
        } else {
            tailExpr?.inferType(expected)
        } ?: TyUnit.INSTANCE
        return if (isDiverging) TyNever else type
    }

    fun inferReplCodeFragment(element: RsReplCodeFragment) {
        for (stmt in element.stmtList) {
            if (stmt is RsStmt) {
                processStatement(stmt)
            }
        }
    }

    // returns true if expr is always diverging
    private fun processStatement(psi: RsStmt): Boolean = when (psi) {
        is RsLetDecl -> {
            val explicitTy = psi.typeReference?.rawType
                ?.let { normalizeAssociatedTypesIn(it) }
            val expr = psi.expr
            val pat = psi.pat
            // We need to know type before coercion to correctly identify if expr is always diverging
            // so we can't call `inferTypeCoercableTo` directly here
            val (inferredTy, coercedInferredTy) = if (expr != null) {
                val patHasRefMut = pat?.descendantsOfTypeOrSelf<RsPatIdent>().orEmpty()
                    .any { (it.patBinding.kind as? BindByReference)?.mutability == MUTABLE }
                val initializerNeeds = if (patHasRefMut) Needs.MutPlace else Needs.None
                val inferredTy = expr.inferType(Expectation.maybeHasType(explicitTy), needs = initializerNeeds)
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
            psi.letElseBranch?.block?.inferType(ExpectHasType(TyNever), coerce = true)
            inferredTy == TyNever
        }
        is RsExprStmt -> psi.expr.inferType() == TyNever
        else -> false
    }

    private fun RsExpr.inferType(expected: Ty?): Ty =
        inferType(Expectation.maybeHasType(expected))

    private fun RsExpr.inferType(expected: Expectation = NoExpectation, needs: Needs = Needs.None): Ty {
        ProgressManager.checkCanceled()
        if (ctx.isTypeInferred(this)) error("Trying to infer expression type twice")

        expected.tyAsNullable(ctx)?.let {
            when (this) {
                is RsPathExpr, is RsDotExpr, is RsCallExpr -> ctx.writeExpectedExprTy(this, it)
            }
        }

        val ty = when (this) {
            is RsPathExpr -> inferPathExprType(this)
            is RsStructLiteral -> inferStructLiteralType(this, expected)
            is RsTupleExpr -> inferTupleExprType(this, expected)
            is RsParenExpr -> expr?.inferType(expected) ?: TyUnknown
            is RsUnitExpr -> TyUnit.INSTANCE
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
            is RsLetExpr -> inferLetExprType(this)
            is RsContExpr -> TyNever
            else -> TyUnknown
        }

        ctx.writeExprTy(this, ty)

        if (needs == Needs.MutPlace) {
            ctx.convertPlaceDerefsToMutable(unwrapParenExprs(this))
        }

        return ty
    }

    private fun RsExpr.inferTypeCoercableTo(expected: Ty): Ty {
        val inferred = inferType(Expectation.maybeHasType(expected))
        return if (coerce(this, inferred, expected)) expected else inferred
    }

    @JvmName("inferTypeCoercableTo_")
    fun inferTypeCoercableTo(expr: RsExpr, expected: Ty): Ty =
        expr.inferTypeCoercableTo(expected)


    @JvmName("inferType_")
    fun inferType(expr: RsExpr): Ty =
        expr.inferType()

    fun coerce(element: RsElement, inferred: Ty, expected: Ty): Boolean =
        coerceResolved(
            element,
            resolveTypeVarsWithObligations(inferred),
            resolveTypeVarsWithObligations(expected)
        )

    private fun coerceResolved(element: RsElement, inferred: Ty, expected: Ty): Boolean {
        if (element is RsExpr) {
            ctx.writeExpectedExprTyCoercable(element)
        }
        return when (val result = ctx.tryCoerce(inferred, expected)) {
            is Ok -> {
                ctx.applyAdjustments(element, result.ok.adjustments)
                true
            }

            is RsResult.Err -> when (val err = result.err) {
                is TypeError.TypeMismatch -> {
                    checkTypeMismatch(err, element, inferred, expected)
                    false
                }

                is TypeError.ConstMismatch -> {
                    if (err.const1.javaClass !in IGNORED_CONSTS && err.const2.javaClass !in IGNORED_CONSTS) {
                        reportTypeMismatch(element, expected, inferred)
                    }

                    false
                }
            }
        }
    }

    private fun checkTypeMismatch(result: TypeError.TypeMismatch, element: RsElement, inferred: Ty, expected: Ty) {
        if (result.ty1.javaClass in IGNORED_TYS || result.ty2.javaClass in IGNORED_TYS) return
        if (expected is TyReference && inferred is TyReference &&
            (expected.containsTyOfClass(IGNORED_TYS) || inferred.containsTyOfClass(IGNORED_TYS))) {
            // report errors with unknown types when &mut is needed, but & is present
            if (!(expected.mutability == MUTABLE && inferred.mutability == IMMUTABLE)) {
                return
            }
        }
        reportTypeMismatch(element, expected, inferred)
    }

    // Another awful hack: check that inner expressions did not annotated as an error
    // to disallow annotation intersections. This should be done in a different way
    private fun reportTypeMismatch(element: RsElement, expected: Ty, inferred: Ty) {
        if (ctx.diagnostics.all { !element.isAncestorOf(it.element) }) {
            ctx.reportTypeMismatch(element, expected, inferred)
        }
    }

    private fun inferLitExprType(expr: RsLitExpr, expected: Expectation): Ty {
        return when (val stubKind = expr.stubKind) {
            is RsStubLiteralKind.Boolean -> TyBool.INSTANCE
            is RsStubLiteralKind.Char -> if (stubKind.isByte) TyInteger.U8.INSTANCE else TyChar.INSTANCE
            is RsStubLiteralKind.String -> {
                // TODO infer the actual lifetime
                if (stubKind.isByte) {
                    val size = stubKind.value?.length?.toLong()
                    val const = size?.let { ConstExpr.Value.Integer(it, TyInteger.USize.INSTANCE).toConst() } ?: CtUnknown
                    TyReference(TyArray(TyInteger.U8.INSTANCE, const), IMMUTABLE, ReStatic)
                } else {
                    TyReference(TyStr.INSTANCE, IMMUTABLE, ReStatic)
                }
            }
            is RsStubLiteralKind.Integer -> {
                val ty = stubKind.ty
                ty ?: when (val ety = expected.tyAsNullable(ctx)) {
                    is TyInteger -> ety
                    is TyChar -> TyInteger.U8.INSTANCE
                    is TyPointer, is TyFunction -> TyInteger.USize.INSTANCE
                    else -> TyInfer.IntVar()
                }
            }
            is RsStubLiteralKind.Float -> {
                val ty = stubKind.ty
                ty ?: (expected.tyAsNullable(ctx)?.takeIf { it is TyFloat } ?: TyInfer.FloatVar())
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

        ctx.writePath(expr, filteredVariants.map { ResolvedPath.from(it, expr) })

        val first = filteredVariants.singleOrNull() ?: return TyUnknown
        return instantiatePath(first.element, first, expr)
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
        if (element is RsImplItem) {
            val implForTy = element.typeReference?.rawType?.let { normalizeAssociatedTypesIn(it) } ?: TyUnknown
            val tupleFields = ((implForTy as? TyAdt)?.item as? RsFieldsOwner)?.tupleFields
            return if (tupleFields != null) {
                // Treat tuple constructor as a function
                TyFunction(tupleFields.tupleFieldDeclList.map { it.typeReference.rawType }, implForTy)
                    .substitute(implForTy.typeParameterValues)
            } else {
                implForTy
            }.foldWith(associatedTypeNormalizer)
        }

        val path = pathExpr.path

        if (element is RsGenericDeclaration) {
            inferConstArgumentTypes(element.constParameters, path.constArguments)
        }

        val subst = TyLowering.lowerPathGenerics(
            path,
            element,
            scopeEntry.subst,
            PathExprResolver.fromContext(ctx),
            emptyMap(),
        ).subst

        val typeParameters = when {
            scopeEntry is AssocItemScopeEntry && element is RsAbstractable -> {
                val owner = element.owner
                val (typeParameters, selfTy) = when (owner) {
                    is RsAbstractableOwner.Impl -> {
                        val typeParameters = ctx.instantiateBounds(owner.impl)
                        val selfTy = owner.impl.typeReference?.rawType?.substitute(typeParameters) ?: TyUnknown
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
                            else -> Unit
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

        ctx.writePathSubst(pathExpr, typeParameters)

        val type = when (element) {
            is RsPatBinding -> ctx.getBindingType(element)
            is RsTypeDeclarationElement -> element.declaredType
            is RsEnumVariant -> element.parentEnum.declaredType
            is RsFunction -> element.type
            is RsConstant -> element.typeReference?.rawType ?: TyUnknown
            is RsConstParameter -> element.typeReference?.rawType ?: TyUnknown
            is RsSelfParameter -> element.typeOfValue
            else -> return TyUnknown
        }
        val tupleFields = (element as? RsFieldsOwner)?.tupleFields
        return if (tupleFields != null) {
            // Treat tuple constructor as a function
            TyFunction(tupleFields.tupleFieldDeclList.map { it.typeReference.rawType }, type)
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

    private fun inferStructLiteralType(expr: RsStructLiteral, expected: Expectation): Ty {
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
        expected.onlyHasTy(ctx)?.let {
            unifySubst(typeParameters, it.typeParameterValues)
        }

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

    private fun inferTupleExprType(expr: RsTupleExpr, expected: Expectation): Ty {
        val fields = expected.onlyHasTy(ctx)?.let {
            (resolveTypeVarsWithObligations(it) as? TyTuple)?.types
        }
        return TyTuple(inferExprList(expr.exprList, fields))
    }

    private fun inferExprList(exprs: List<RsExpr>, expected: List<Ty>?): List<Ty> {
        val extended = expected.orEmpty().asSequence().infiniteWithTyUnknown()
        return exprs.asSequence().zip(extended).map { (expr, ty) -> expr.inferTypeCoercableTo(ty) }.toList()
    }

    private fun inferCastExprType(expr: RsCastExpr): Ty {
        expr.expr.inferType()
        return normalizeAssociatedTypesIn(expr.typeReference.rawType)
    }

    private fun inferCallExprType(expr: RsCallExpr, expected: Expectation): Ty {
        val calleeExpr = expr.expr
        val baseTy = resolveTypeVarsWithObligations(calleeExpr.inferType()) // or error
        // TODO add adjustment
        val derefTy = lookup.coercionSequence(baseTy).mapNotNull {
            lookup.asTyFunction(it)
        }.firstOrNull()
        if (baseTy != TyUnknown && derefTy == null) {
            ctx.addDiagnostic(RsDiagnostic.ExpectedFunction(expr))
        }
        val argExprs = expr.valueArgumentList.exprList
        val calleeType = ctx.resolveTypeVarsIfPossible(
            (derefTy?.register() ?: unknownTyFunction(argExprs.size))
                .foldWith(associatedTypeNormalizer)
        ) as TyFunction
        val expectedInputTys = expectedInputsForExpectedOutput(expected, calleeType.retType, calleeType.paramTypes)
        inferArgumentTypes(calleeType.paramTypes, expectedInputTys, argExprs)
        return calleeType.retType
    }

    /**
     * Unifies the output type with the expected type early, for more coercions
     * and forward type information on the input expressions
     */
    private fun expectedInputsForExpectedOutput(
        expectedRet: Expectation,
        formalRet: Ty,
        formalArgs: List<Ty>,
    ): List<Ty> {
        @Suppress("NAME_SHADOWING")
        val formalRet = resolveTypeVarsWithObligations(formalRet)
        val retTy = expectedRet.onlyHasTy(ctx) ?: return emptyList()
        // Rustc does `fudge` instead of `probe` here. But `fudge` seems useless in our simplified type inference
        // because we don't produce new type variables during unification
        // https://github.com/rust-lang/rust/blob/50cf76c24bf6f266ca6d253a/compiler/rustc_infer/src/infer/fudge.rs#L98
        return ctx.probe {
            if (ctx.combineTypes(retTy, formalRet).isOk) {
                formalArgs.map { ctx.resolveTypeVarsIfPossible(it) }
            } else {
                emptyList()
            }
        }
    }

    private fun inferMethodCallExprType(
        receiver: Ty,
        methodCall: RsMethodCall,
        expected: Expectation
    ): Ty {
        val argExprs = methodCall.valueArgumentList.exprList
        val callee = run {
            val variants = resolveMethodCallReferenceWithReceiverType(lookup, receiver, methodCall)
            val callee = pickSingleMethod(receiver, variants, methodCall)
            // If we failed to resolve ambiguity just write the all possible methods
            val variantsForDisplay = callee?.let { listOf(it.toMethodResolveVariant()) } ?: variants
            ctx.writeResolvedMethod(methodCall, variantsForDisplay)

            callee ?: variants.firstOrNull()?.let { MethodPick.from(it) }
        }
        if (callee == null) {
            val methodType = unknownTyFunction(argExprs.size)
            inferArgumentTypes(methodType.paramTypes, methodType.paramTypes, argExprs)
            return methodType.retType
        }

        val adjustments: MutableList<Adjustment> = callee.derefSteps.toAdjustments(items).toMutableList()
        val lastDerefTy = adjustments.lastOrNull()?.target ?: receiver
        if (callee.autorefOrPtrAdjustment is Autoref) {
            val mutability = callee.autorefOrPtrAdjustment.mutability
            adjustments += Adjustment.BorrowReference(TyReference(lastDerefTy, mutability))
            if (callee.autorefOrPtrAdjustment.unsize) {
                val unsizedTy = if (lastDerefTy is TyArray) {
                    TySlice(lastDerefTy.base)
                } else {
                    error("AutorefOrPtrAdjustment's unsize flag should only be set for array ty, found `$lastDerefTy`")
                }
                adjustments += Adjustment.Unsize(TyReference(unsizedTy, mutability))
            }
        }
        ctx.applyAdjustments(methodCall.receiver, adjustments)

        inferConstArgumentTypes(callee.element.constParameters, methodCall.constArguments)

        var newSubst = ctx.instantiateMethodOwnerSubstitution(callee, methodCall)

        newSubst = ctx.instantiateBounds(callee.element, callee.formalSelfTy, newSubst)

        val typeParameters = callee.element.typeParameters.map { TyTypeParameter.named(it) }
        val typeArguments = methodCall.typeArguments.map { it.rawType }
        val typeSubst = typeParameters.zip(typeArguments).toMap()

        val constParameters = callee.element.constParameters.map { CtConstParameter(it) }
        val resolver = PathExprResolver.fromContext(ctx)
        val constSubst = constParameters.zip(methodCall.constArguments).associate { (param, psiValue) ->
            val expectedTy = param.parameter.typeReference?.rawType?.let { normalizeAssociatedTypesIn(it) } ?: TyUnknown
            val value = psiValue.toConst(expectedTy, resolver)
            param to value
        }

        val fnSubst = Substitution(typeSubst = typeSubst, constSubst = constSubst)
        unifySubst(fnSubst, newSubst)

        val methodType = (callee.element.type)
            .substitute(newSubst)
            .foldWith(associatedTypeNormalizer) as TyFunction
        // drop first element of paramTypes because it's `self` param
        // and it doesn't have value in `methodCall.valueArgumentList.exprList`
        val formalInputTys = methodType.paramTypes.drop(1)
        val expectedInputTys = if (!callee.element.isAsync) {
            expectedInputsForExpectedOutput(expected, methodType.retType, formalInputTys)
        } else {
            emptyList()
        }
        inferArgumentTypes(formalInputTys, expectedInputTys, argExprs)
        ctx.writeResolvedMethodSubst(methodCall, newSubst, methodType)

        return methodType.retType
    }

    private fun <T : AssocItemScopeEntryBase<*>> filterAssocItems(variants: List<T>, context: RsElement): List<T> {
        val containingMod = context.containingMod
        return variants.singleOrLet { list ->
            // 1. filter traits that are not imported
            TypeInferenceMarks.MethodPickTraitScope.hit()
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
            filtered.ifEmpty {
                TypeInferenceMarks.MethodPickTraitsOutOfScope.hit()
                list
            }
        }.singleOrFilter { callee ->
            // 2. filter non-visible (private) items
            val source = callee.source
            source !is TraitImplSource.ExplicitImpl || !source.isInherent
                || callee.element.isVisibleFrom(containingMod)
        }.singleOrFilter { callee ->
            // 3. Filter methods by trait bounds (try to select all obligations for each impl)
            TypeInferenceMarks.MethodPickCheckBounds.hit()
            ctx.canEvaluateBounds(callee.source, callee.selfTy)
        }
    }

    private fun pickSingleMethod(
        receiver: Ty,
        variants: List<MethodResolveVariant>,
        methodCall: RsMethodCall
    ): MethodPick? {
        val picked = run {
            val list = filterAssocItems(variants, methodCall)

            // 4. Pick results matching receiver type
            TypeInferenceMarks.MethodPickDerefOrder.hit()

            val autoderef = lookup.coercionSequence(receiver)

            fun pick(ty: Ty, borrow: Mutability?): List<MethodPick> {
                val autoderefSteps = lazy(LazyThreadSafetyMode.NONE) { autoderef.steps() }
                val autorefOrPtrAdjustment = lazy(LazyThreadSafetyMode.NONE) {
                    borrow?.let { Autoref(it, autoderefSteps.value.lastOrNull()?.getKind(items) == ArrayToSlice) }
                }
                return list.filter { it.element.selfParameter?.typeOfValue(it.selfTy) == ty }
                    .map { MethodPick.from(it, ty, autoderefSteps.value, autorefOrPtrAdjustment.value) }
            }

            // https://github.com/rust-lang/rust/blob/a646c912/src/librustc_typeck/check/method/probe.rs#L885
            autoderef.mapNotNull { ty ->
                pick(ty, null)
                    // TODO do something with lifetimes
                    .notEmptyOrLet { pick(TyReference(ty, IMMUTABLE), IMMUTABLE) }
                    .notEmptyOrLet { pick(TyReference(ty, MUTABLE), MUTABLE) }
                    .takeIf { it.isNotEmpty() }
            }.firstOrNull()
                ?: list.singleOrNull()?.let { listOf(MethodPick.from(it)) }
                ?: emptyList()
        }

        return when (picked.size) {
            0 -> null
            1 -> picked.single()
            else -> {
                // 5. Try to collapse multiple resolved methods of the same trait, e.g.
                // ```rust
                // trait Foo<T> { fn foo(&self, _: T) {} }
                // impl Foo<Bar> for S { fn foo(&self, _: Bar) {} }
                // impl Foo<Baz> for S { fn foo(&self, _: Baz) {} }
                // ```
                // In this case `picked` list contains 2 function defined in 2 impls.
                // We want to collapse them into the single function defined in the trait.
                // Specific impl will be selected later according to the method parameter type.
                val first = picked.first()
                collapseToTrait(picked.map { it.element })?.let { fn ->
                    TypeInferenceMarks.MethodPickCollapseTraits.hit()
                    MethodPick(
                        fn,
                        first.formalSelfTy,
                        first.methodSelfTy,
                        first.derefCount,
                        TraitImplSource.Collapsed((fn.owner as RsAbstractableOwner.Trait).trait),
                        first.derefSteps,
                        first.autorefOrPtrAdjustment,
                        first.isValid
                    )
                }
            }
        }
    }

    private fun unknownTyFunction(arity: Int): TyFunction =
        TyFunction(generateSequence { TyUnknown }.take(arity).toList(), TyUnknown)

    private fun inferArgumentTypes(
        formalInputTys: List<Ty>,
        expectedInputTys: List<Ty>,
        argExprs: List<RsExpr>
    ) {
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

            argExprs.forEachIndexed { index, expr ->
                val isLambda = unwrapParenExprs(expr) is RsLambdaExpr
                if (isLambda != checkLambdas) return@forEachIndexed

                val formalInputTy = formalInputTys.getOrNull(index) ?: TyUnknown
                val expectedInputTy = expectedInputTys.getOrNull(index) ?: formalInputTy

                val expectation = Expectation.rvalueHint(expectedInputTy)
                val inferredTy = expr.inferType(expectation)
                val coercedTy = resolveTypeVarsWithObligations(expectation.onlyHasTy(ctx) ?: formalInputTy)
                coerce(expr, inferredTy, coercedTy)
                ctx.combineTypes(formalInputTy, coercedTy)
            }
        }
    }

    fun inferConstArgumentTypes(constParameters: List<RsConstParameter>, constArguments: List<RsElement>) {
        val argDefs = constParameters.asSequence()
            .map { p -> p.typeReference?.rawType?.let { normalizeAssociatedTypesIn(it) } ?: TyUnknown }
            .infiniteWithTyUnknown()
        for ((type, expr) in argDefs.zip(constArguments.asSequence())) {
            when (expr) {
                is RsExpr -> expr.inferTypeCoercableTo(type)
                is RsPathType -> {
                    val typeReference = when (val def = expr.path.reference?.resolve()) {
                        is RsConstant -> def.typeReference?.takeIf { def.isConst }
                        is RsConstParameter -> def.typeReference
                        else -> null
                    }
                    coerce(expr, typeReference?.rawType?.let { normalizeAssociatedTypesIn(it) } ?: TyUnknown, type)
                }
            }
        }
    }

    private fun inferFieldExprType(receiver: Ty, fieldLookup: RsFieldLookup): Ty {
        if (fieldLookup.identifier?.text == "await" && fieldLookup.isAtLeastEdition2018) {
            return receiver.lookupFutureOutputTy(lookup)
        }

        val variants = resolveFieldLookupReferenceWithReceiverType(lookup, receiver, fieldLookup)
        ctx.writeResolvedField(fieldLookup, variants.map { it.element })
        val field = variants.firstOrNull()
        if (field == null) {
            val autoderef = lookup.coercionSequence(receiver)
            for (type in autoderef) {
                if (type is TyTuple) {
                    ctx.applyAdjustments(fieldLookup.parentDotExpr.expr, autoderef.steps().toAdjustments(items))
                    val fieldIndex = fieldLookup.integerLiteral?.text?.toIntOrNull() ?: return TyUnknown
                    return type.types.getOrElse(fieldIndex) { TyUnknown }
                }
            }
            return TyUnknown
        }
        ctx.applyAdjustments(fieldLookup.parentDotExpr.expr, field.derefSteps.toAdjustments(items))

        val fieldElement = field.element

        val raw = (fieldElement as? RsFieldDecl)?.typeReference?.rawType ?: TyUnknown
        return raw.substitute(field.selfTy.typeParameterValues).foldWith(associatedTypeNormalizer)
    }

    private fun inferDotExprType(expr: RsDotExpr, expected: Expectation): Ty {
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

        if (label != null || !matchOnlyByLabel) {
            expr.processBreakExprs(label, matchOnlyByLabel) { breakExpr ->
                returningTypes += breakExpr.expr?.let(ctx::getExprType) ?: TyUnit.INSTANCE
            }
        }
        return getMoreCompleteType(returningTypes)
    }

    private fun inferForExprType(expr: RsForExpr): Ty {
        val exprTy = resolveTypeVarsWithObligations(expr.expr?.inferType() ?: TyUnknown)
        val itemTy = resolveTypeVarsWithObligations(lookup.findIteratorItemType(exprTy)?.register() ?: TyUnknown)
        expr.pat?.extractBindings(itemTy)
        expr.block?.inferType()
        return TyUnit.INSTANCE
    }

    private fun inferWhileExprType(expr: RsWhileExpr): Ty {
        expr.condition?.expr?.inferType(TyBool.INSTANCE)
        expr.block?.inferType()
        return TyUnit.INSTANCE
    }

    private fun inferMatchExprType(expr: RsMatchExpr, expected: Expectation): Ty {
        val matchingExprTy = resolveTypeVarsWithObligations(expr.expr?.inferType() ?: TyUnknown)
        val arms = expr.arms
        for (arm in arms) {
            arm.pat.extractBindings(matchingExprTy)
            arm.expr?.inferType(expected)
            arm.matchArmGuard?.expr?.inferType(TyBool.INSTANCE)
        }

        return getMoreCompleteType(arms.mapNotNull { it.expr?.let(ctx::getExprType) })
    }

    private fun inferUnaryExprType(expr: RsUnaryExpr, expected: Expectation): Ty {
        val innerExpr = expr.expr ?: return TyUnknown
        return when (expr.operatorType) {
            UnaryOperator.REF -> inferRefType(innerExpr, expected, IMMUTABLE, BorrowKind.REF)
            UnaryOperator.REF_MUT -> inferRefType(innerExpr, expected, MUTABLE, BorrowKind.REF)
            UnaryOperator.RAW_REF_CONST -> inferRefType(innerExpr, expected, IMMUTABLE, BorrowKind.RAW)
            UnaryOperator.RAW_REF_MUT -> inferRefType(innerExpr, expected, MUTABLE, BorrowKind.RAW)
            UnaryOperator.DEREF -> inferDerefExprType(expr, innerExpr)
            UnaryOperator.MINUS -> innerExpr.inferType(expected)
            UnaryOperator.NOT -> innerExpr.inferType(expected)
            UnaryOperator.BOX -> {
                val expectedInner = (expected.tyAsNullable(ctx) as? TyAdt)
                    ?.takeIf { it.item == items.Box }
                    ?.typeArguments
                    ?.getOrNull(0)
                    ?.let { Expectation.rvalueHint(it) }
                    ?: NoExpectation
                items.makeBox(innerExpr.inferType(expectedInner))
            }
        }
    }

    private fun inferDerefExprType(expr: RsUnaryExpr, operandExpr: RsExpr): Ty {
        // expectation must NOT be used for deref
        val operandTy = resolveTypeVarsWithObligations(operandExpr.inferType())
        operandTy.builtinDeref(items, explicit = true)?.let {
            return it.first
        }
        val overloadedDeref = lookup.deref(operandTy)
        if (overloadedDeref == null && operandTy != TyUnknown) {
            ctx.addDiagnostic(RsDiagnostic.DerefError(expr, operandTy))
        }
        if (overloadedDeref != null) {
            // Adjust self type before calling `Deref::deref`
            ctx.applyAdjustment(operandExpr, Adjustment.BorrowReference(TyReference(operandTy, IMMUTABLE)))
            ctx.writeOverloadedOperator(expr)
        }
        return overloadedDeref ?: TyUnknown
    }

    private fun inferRefType(expr: RsExpr, expected: Expectation, mutable: Mutability, kind: BorrowKind): Ty {
        val hint = expected.onlyHasTy(ctx)?.let {
            val referenced = when (it) {
                is TyReference -> it.referenced
                is TyPointer -> it.referenced
                else -> null
            }
            if (referenced != null) {
                val isPlace = when (expr) {
                    is RsPathExpr, is RsIndexExpr -> true
                    is RsUnaryExpr -> expr.operatorType == UnaryOperator.DEREF
                    is RsDotExpr -> expr.fieldLookup != null
                    else -> false
                }
                if (isPlace) {
                    ExpectHasType(referenced)
                } else {
                    Expectation.rvalueHint(referenced)
                }
            } else {
                NoExpectation
            }
        } ?: NoExpectation

        val ty = expr.inferType(hint, needs = Needs.maybeMutPlace(mutable))

        return when (kind) {
            BorrowKind.REF -> TyReference(ty, mutable) // TODO infer the actual lifetime
            BorrowKind.RAW -> TyPointer(ty, mutable)
        }
    }

    private fun inferIfExprType(expr: RsIfExpr, expected: Expectation): Ty {
        expr.condition?.expr?.inferType(TyBool.INSTANCE)
        val blockTys = mutableListOf<Ty?>()
        blockTys.add(expr.block?.inferType(expected))
        val elseBranch = expr.elseBranch
        if (elseBranch != null) {
            blockTys.add(elseBranch.ifExpr?.inferType(expected))
            blockTys.add(elseBranch.block?.inferType(expected))
        }
        return if (expr.elseBranch == null) TyUnit.INSTANCE else getMoreCompleteType(blockTys.filterNotNull())
    }

    private fun inferBinaryExprType(expr: RsBinaryExpr): Ty {
        val op = expr.operatorType
        val lhsNeeds = if (op is AssignmentOp) Needs.MutPlace else Needs.None
        val lhsType = resolveTypeVarsWithObligations(expr.left.inferType(needs = lhsNeeds))
        val (rhsType, retTy) = when (op) {
            is BoolOp -> {
                if (op is OverloadableBinaryOperator) {
                    val rhsTypeVar = TyInfer.TyVar()
                    enforceOverloadedBinopTypes(lhsType, rhsTypeVar, op)
                    val rhsType = resolveTypeVarsWithObligations(
                        expr.right?.inferTypeCoercableTo(rhsTypeVar)
                            ?: TyUnknown
                    )

                    val lhsAdjustment = Adjustment.BorrowReference(TyReference(lhsType, IMMUTABLE))
                    ctx.applyAdjustment(expr.left, lhsAdjustment)

                    val rhsAdjustment = Adjustment.BorrowReference(TyReference(rhsType, IMMUTABLE))
                    expr.right?.let { ctx.applyAdjustment(it, rhsAdjustment) }

                    rhsType to TyBool.INSTANCE
                } else {
                    val rhsType = resolveTypeVarsWithObligations(expr.right?.inferTypeCoercableTo(lhsType) ?: TyUnknown)
                    rhsType to TyBool.INSTANCE
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

                val lhsAdjustment = Adjustment.BorrowReference(TyReference(lhsType, MUTABLE))
                ctx.applyAdjustment(expr.left, lhsAdjustment)

                rhsType to TyUnit.INSTANCE
            }
            AssignmentOp.EQ -> {
                val rhsType = expr.right?.inferTypeCoercableTo(lhsType) ?: TyUnknown
                rhsType to TyUnit.INSTANCE
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
            lhsType is TyBool && rhsType is TyBool

        BinOpCategory.Comparison -> lhsType.isScalar && rhsType.isScalar
    }

    private fun enforceBuiltinBinopTypes(lhsType: Ty, rhsType: Ty, op: BinaryOperator): Ty = when (op.category) {
        BinOpCategory.Shortcircuit -> {
            ctx.combineTypes(lhsType, TyBool.INSTANCE)
            ctx.combineTypes(lhsType, TyBool.INSTANCE)
            TyBool.INSTANCE
        }

        BinOpCategory.Shift -> lhsType

        BinOpCategory.Math, BinOpCategory.Bitwise -> {
            ctx.combineTypes(lhsType, rhsType)
            lhsType
        }

        BinOpCategory.Comparison -> {
            ctx.combineTypes(lhsType, rhsType)
            TyBool.INSTANCE
        }
    }

    private fun inferTryExprType(expr: RsTryExpr): Ty {
        val base = resolveTypeVarsWithObligations(expr.expr.inferType()) as? TyAdt ?: return TyUnknown
        if (base.item == items.Result || base.item == items.Option) {
            TypeInferenceMarks.QuestionOperator.hit()
            return base.typeArguments.getOrElse(0) { TyUnknown }
        }
        val tryItem = items.Try ?: return TyUnknown
        val okType = tryItem.findAssociatedType("Output")
            ?: tryItem.findAssociatedType("Ok")
            ?: return TyUnknown
        val projection = TyProjection.valueOf(base, BoundElement(tryItem), BoundElement(okType))
        return ctx.normalizeAssociatedTypesIn(projection).register()
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
        val containerExpr = expr.containerExpr
        val indexExpr = expr.indexExpr ?: return TyUnknown

        val containerType = containerExpr.inferType()
        val indexType = ctx.resolveTypeVarsIfPossible(indexExpr.inferType())

        val autoderef = lookup.coercionSequence(containerType)
        val result = autoderef
            .mapNotNull { lookup.findIndexOutputType(it, indexType) }
            .firstOrNull()
            ?.register()
            ?.takeIf { it !is TyUnknown }
            ?: return TyUnknown

        val steps = autoderef.steps()
        val lastStep = steps.lastOrNull()
        val adjustedTy = lastStep?.to ?: containerType
        val adjustments = mutableListOf<Adjustment>()
        adjustments += steps.toAdjustments(items)
        if (!isBuiltinIndex(adjustedTy, ctx.resolveTypeVarsIfPossible(indexType))) {
            adjustments += Adjustment.BorrowReference(TyReference(adjustedTy, IMMUTABLE))
            if (lastStep?.getKind(items) == ArrayToSlice) {
                adjustments += Adjustment.Unsize(TyReference(lastStep.to, IMMUTABLE))
            }
            ctx.writeOverloadedOperator(expr)
        }
        ctx.applyAdjustments(containerExpr, adjustments)
        return result
    }

    private fun inferMacroExprType(macroExpr: RsMacroExpr, expected: Expectation): Ty {
        val macroCall = macroExpr.macroCall

        val definition = macroCall.resolveToMacro()
        val origin = definition?.containingCrate?.origin
        if (origin != null && origin != PackageOrigin.STDLIB) {
            inferChildExprsRecursively(macroCall)
            return inferMacroAsExpr(macroCall)
        }

        val name = macroCall.macroName
        val exprArg = macroCall.exprMacroArgument
        if (exprArg != null) {
            val type = exprArg.expr?.inferType() ?: return TyUnknown
            return when (name) {
                "dbg" -> type
                else -> TyUnknown
            }
        }

        val vecArg = macroCall.vecMacroArgument
        if (vecArg != null) {
            val expectedElemTy = (expected.tyAsNullable(ctx) as? TyAdt)?.takeIf { it.item == items.Vec }?.typeArguments?.getOrNull(0)
            val elementType = if (vecArg.semicolon != null) {
                // vec![value; repeat]
                run {
                    val exprList = vecArg.exprList
                    val valueExpr = exprList.firstOrNull() ?: return@run TyUnknown
                    exprList.getOrNull(1)?.let { inferTypeCoercableTo(it, TyInteger.USize.INSTANCE) }
                    expectedElemTy?.let { valueExpr.inferTypeCoercableTo(it) } ?: valueExpr.inferType()
                }
            } else {
                // vec![value1, value2, value3]
                val elementTypes = vecArg.exprList.map { it.inferType(expectedElemTy) }
                val elementType = if (elementTypes.isNotEmpty()) getMoreCompleteType(elementTypes) else TyInfer.TyVar()

                if (expectedElemTy != null && ctx.tryCoerce(elementType, expectedElemTy).isOk) {
                    expectedElemTy
                } else {
                    elementType
                }
            }
            return items.findVecForElementTy(elementType)
        }

        inferChildExprsRecursively(macroCall)
        return when {
            macroCall.assertMacroArgument != null -> TyUnit.INSTANCE
            macroCall.formatMacroArgument != null -> inferFormatMacro(macroCall)
            macroCall.includeMacroArgument != null -> inferIncludeMacro(macroCall)
            name == "env" -> TyReference(TyStr.INSTANCE, IMMUTABLE)
            name == "option_env" -> items.findOptionForElementTy(TyReference(TyStr.INSTANCE, IMMUTABLE))
            name == "concat" -> TyReference(TyStr.INSTANCE, IMMUTABLE)
            name == "line" || name == "column" -> TyInteger.U32.INSTANCE
            name == "file" -> TyReference(TyStr.INSTANCE, IMMUTABLE)
            name == "stringify" -> TyReference(TyStr.INSTANCE, IMMUTABLE)
            name == "module_path" -> TyReference(TyStr.INSTANCE, IMMUTABLE)
            name == "cfg" -> TyBool.INSTANCE
            else -> inferMacroAsExpr(macroCall)
        }
    }

    private fun inferIncludeMacro(macroCall: RsMacroCall): Ty {
        return when (macroCall.macroName) {
            "include_str" -> TyReference(TyStr.INSTANCE, IMMUTABLE)
            "include_bytes" -> TyReference(TyArray(TyInteger.U8.INSTANCE, CtUnknown), IMMUTABLE)
            else -> TyUnknown
        }
    }

    private fun inferMacroAsExpr(macroCall: RsMacroCall): Ty
        = (macroCall.expansion as? MacroExpansion.Expr)?.expr?.inferType() ?: TyUnknown

    private fun inferFormatMacro(macroCall: RsMacroCall): Ty {
        val inferredTy = inferMacroAsExpr(macroCall)
        val name = macroCall.macroName
        return when {
            "print" in name -> TyUnit.INSTANCE
            name == "format" -> items.String.asTy()
            name == "format_args" -> items.Arguments.asTy()
            name == "unimplemented" || name == "unreachable" || name == "panic" -> TyNever
            name == "write" || name == "writeln" -> inferredTy
            else -> inferredTy
        }
    }

    private fun inferChildExprsRecursively(psi: PsiElement) {
        when (psi) {
            is RsExpr -> psi.inferType()
            else -> psi.forEachChild(this::inferChildExprsRecursively)
        }
    }

    private fun inferLambdaExprType(expr: RsLambdaExpr, expected: Expectation): Ty {
        val params = expr.valueParameters
        val expectedFnTy = expected
            .tyAsNullable(ctx)
            ?.let(this::deduceLambdaType)
            ?: TyFunction(generateSequence { TyInfer.TyVar() }.take(params.size).toList(), TyUnknown)
        val extendedArgs = expectedFnTy.paramTypes.asSequence().infiniteWithTyUnknown()
        val paramTypes = extendedArgs.zip(params.asSequence()).map { (expectedArg, actualArg) ->
            val paramTy = actualArg.typeReference?.rawType?.let { normalizeAssociatedTypesIn(it) } ?: expectedArg
            actualArg.pat?.extractBindings(paramTy)
            paramTy
        }.toList()
        val expectedRetTy = expr.retType?.typeReference?.rawType
            ?.let { normalizeAssociatedTypesIn(it) }
            ?: expectedFnTy.retType.takeIf { it != TyUnknown }
        val isFreshRetTy = expectedRetTy == null
        val retTy = expectedRetTy ?: TyInfer.TyVar()

        val lambdaBodyContext = RsTypeInferenceWalker(ctx, retTy)
        expr.expr?.let { lambdaBodyContext.inferLambdaBody(it) }
        val isDefaultRetTy = isFreshRetTy && retTy is TyInfer.TyVar && !ctx.isTypeVarAffected(retTy)
        val actualRetTy = if (isDefaultRetTy) TyUnit.INSTANCE else retTy

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
            is TyTraitObject -> lookup.asTyFunction(expected.traits.first()) // TODO: Use all trait bounds
            is TyFunction -> expected
            is TyAnon -> {
                val trait = expected.traits.find { it.element in listOf(items.Fn, items.FnMut, items.FnOnce) }
                trait?.let { lookup.asTyFunction(it) }
            }
            else -> null
        }
    }

    private fun inferArrayType(expr: RsArrayExpr, expected: Expectation): Ty {
        val expectedElemTy = when (val ty = expected.tyAsNullable(ctx)) {
            is TyArray -> ty.base
            is TySlice -> ty.elementType
            else -> null
        }
        val (elementType, size) = if (expr.semicolon != null) {
            // It is "repeat expr", e.g. `[1; 5]`
            val elementType = expectedElemTy
                ?.let { expr.initializer?.inferTypeCoercableTo(it) }
                ?: expr.initializer?.inferType()
                ?: return TySlice(TyUnknown)
            val sizeExpr = expr.sizeExpr
            sizeExpr?.inferType(TyInteger.USize.INSTANCE)
            val size = sizeExpr?.evaluate(TyInteger.USize.INSTANCE, PathExprResolver.fromContext(ctx)) ?: CtUnknown
            elementType to size
        } else {
            val elementTypes = expr.arrayElements?.map { it.inferType(Expectation.maybeHasType(expectedElemTy)) }
            val size = if (elementTypes != null) {
                val size = elementTypes.size.toLong()
                ConstExpr.Value.Integer(size, TyInteger.USize.INSTANCE).toConst()
            } else {
                CtUnknown
            }
            if (elementTypes.isNullOrEmpty()) {
                return TyArray(TyInfer.TyVar(), size.foldCtConstParameterWith { CtInferVar(it) })
            }

            val elementType = getMoreCompleteType(elementTypes)
            val inferredTy = if (expectedElemTy != null && ctx.tryCoerce(elementType, expectedElemTy).isOk) {
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
        return TyUnit.INSTANCE
    }

    private fun inferRetExprType(expr: RsRetExpr): Ty {
        expr.expr?.inferTypeCoercableTo(returnTy)
        return TyNever
    }

    private fun inferBreakExprType(expr: RsBreakExpr): Ty {
        expr.expr?.inferType()
        return TyNever
    }

    private fun inferLetExprType(letExpr: RsLetExpr): Ty {
        val exprTy = letExpr.expr?.inferType()?.let(::resolveTypeVarsWithObligations)
        letExpr.pat?.extractBindings(exprTy ?: TyUnknown)
        return TyBool.INSTANCE
    }

    // TODO should be replaced with coerceMany
    private fun getMoreCompleteType(types: List<Ty>): Ty {
        if (types.isEmpty()) return TyUnknown
        return types.reduce { acc, ty -> getMoreCompleteType(acc, ty) }
    }

    // TODO should be replaced with coerceMany
    private fun getMoreCompleteType(ty1: Ty, ty2: Ty): Ty {
        return when {
            ty1 is TyNever -> ty2
            ty2 is TyNever -> ty1
            ty1 is TyUnknown -> if (ty2 !is TyNever) ty2 else TyUnknown
            else -> {
                ctx.combineTypes(ty1, ty2)
                ty1
            }
        }
    }

    private fun registerTryProjection(resultTy: Ty, assocTypeTy: Ty) {
        val tryTrait = items.Try ?: return
        val assocType = tryTrait.findAssociatedType("Output")
            ?: tryTrait.findAssociatedType("Ok")
            ?: return
        val projection = TyProjection.valueOf(resultTy, BoundElement(assocType))
        val obligation = Obligation(Predicate.Projection(projection, assocTypeTy))
        fulfill.registerPredicateObligation(obligation)
    }

    private fun <T> TyWithObligations<T>.register(): T {
        obligations.forEach(fulfill::registerPredicateObligation)
        return value
    }

    fun extractParameterBindings(fn: RsFunction) {
        for (param in fn.valueParameters) {
            param.pat?.extractBindings(param.typeReference?.rawType?.let { normalizeAssociatedTypesIn(it) } ?: TyUnknown)
        }
    }

    private fun RsPat.extractBindings(ty: Ty) {
        extractBindings(this@RsTypeInferenceWalker, ty)
    }

    fun writePatTy(psi: RsPat, ty: Ty): Unit =
        ctx.writePatTy(psi, ty)

    fun writePatFieldTy(psi: RsPatField, ty: Ty): Unit =
        ctx.writePatFieldTy(psi, ty)

    fun getResolvedPath(expr: RsPathExpr): List<ResolvedPath> {
        return ctx.getResolvedPath(expr)
    }

    private fun Ty.lookupFutureOutputTy(lookup: ImplLookup): Ty {
        val outputTy = this.lookupRawFutureOutputTy(lookup)
        if (outputTy !is TyUnknown) return outputTy
        return this.lookupIntoFutureOutputTy(lookup)
    }

    private fun Ty.lookupRawFutureOutputTy(lookup: ImplLookup): Ty {
        val futureTrait = lookup.items.Future ?: return TyUnknown
        val outputType = futureTrait.findAssociatedType("Output") ?: return TyUnknown
        val selection = lookup.selectProjection(TraitRef(this, futureTrait.withSubst()), outputType.withSubst())
        return selection.ok()?.register() ?: TyUnknown
    }

    private fun Ty.lookupIntoFutureOutputTy(lookup: ImplLookup): Ty {
        val intoFutureTrait = lookup.items.IntoFuture ?: return TyUnknown
        val outputType = intoFutureTrait.findAssociatedType("Output") ?: return TyUnknown
        val selection = lookup.selectProjection(TraitRef(this, intoFutureTrait.withSubst()), outputType.withSubst())
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

/**
 * Specifies whether an expression needs mutable a mutable place or not. For
 * example, `*x` expr in `*x = 1;` needs a mutable place but in `a = *x;` is not.
 */
private enum class Needs {
    MutPlace,
    None;

    companion object {
        fun maybeMutPlace(mutability: Mutability): Needs = when (mutability) {
            MUTABLE -> MutPlace
            IMMUTABLE -> None
        }
    }
}

val RsFunction.selfType: Ty?
    get() {
        return when (val owner = owner) {
            is RsAbstractableOwner.Impl -> owner.impl.selfType
            is RsAbstractableOwner.Trait -> owner.trait.selfType
            else -> null
        }
    }

val RsSelfParameter.typeOfValue: Ty
    get() {
        val selfType = parentFunction.selfType ?: TyUnknown
        return typeOfValue(selfType)
    }

private fun RsSelfParameter.typeOfValue(selfType: Ty): Ty {
    if (isExplicitType) {
        // self: Self, self: &Self, self: &mut Self, self: Box<Self>
        val formalSelfTy = this.typeReference?.rawType ?: TyUnknown
        val ownerImplTy = (parentFunction.owner as? RsAbstractableOwner.Impl)?.impl?.typeReference?.rawType
        return if (ownerImplTy != null) {
            // In `impl`s, `Self` type has already been replaced with `impl`'s formal self ty.
            // Let's replace it to the actual receiver type
            formalSelfTy.foldWith(object : TypeFolder {
                override fun foldTy(ty: Ty): Ty = when {
                    ty.isEquivalentTo(ownerImplTy) -> selfType
                    else -> ty.superFoldWith(this)
                }

                override fun foldRegion(region: Region): Region = region
                override fun foldConst(const: Const): Const = const
            })
        } else {
            formalSelfTy.substitute(mapOf(TyTypeParameter.self() to selfType).toTypeSubst())
        }
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

        paramTypes += valueParameters.map { it.typeReference?.rawType ?: TyUnknown }

        return TyFunction(paramTypes, if (isAsync) knownItems.makeFuture(rawReturnType) else rawReturnType)
    }

private fun Sequence<Ty>.infiniteWithTyUnknown(): Sequence<Ty> =
    this + generateSequence { TyUnknown }

private fun KnownItems.findVecForElementTy(elementTy: Ty): Ty = Vec.makeTy("T", elementTy)

private fun KnownItems.findOptionForElementTy(elementTy: Ty): Ty = Option.makeTy("T", elementTy)

private fun KnownItems.findRangeTy(rangeName: String, indexType: Ty?): Ty {
    val ty = findItem<RsNamedElement>("core::ops::$rangeName")?.asTy() ?: TyUnknown

    if (indexType == null) return ty

    val typeParameter = ty.getTypeParameter("Idx") ?: return ty
    return ty.substitute(mapOf(typeParameter to indexType).toTypeSubst())
}

private fun KnownItems.makeBox(innerTy: Ty): Ty = Box.makeTy("T", innerTy)

private fun <T> T?.makeTy(typeParamName: String, innerTy: Ty): Ty where T : RsTypeDeclarationElement,
                                                                        T : RsGenericDeclaration {
    if (this == null) return TyUnknown
    val itemTy = declaredType

    val typeParameter = itemTy.getTypeParameter(typeParamName) ?: return itemTy
    val substitutionMap = mutableMapOf(typeParameter to innerTy)

    val typeParameters = typeParameters
    if (typeParameters.size > 1) {
        // Potentially, it's O(n^2) if item contains O(n) type parameters with default value.
        // But all known stdlib items contain at most one type parameter with default value
        // so it doesn't matter
        for (param in typeParameters) {
            val name = param.name ?: continue
            val typeReference = param.typeReference ?: continue
            if (name != typeParamName) {
                val typeParam = itemTy.getTypeParameter(name) ?: continue
                substitutionMap[typeParam] = typeReference.rawType
            }
        }
    }

    return itemTy.substitute(substitutionMap.toTypeSubst())
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

fun isBuiltinIndex(baseType: Ty, indexType: Ty) =
    (baseType is TyArray || baseType is TySlice) && indexType is TyInteger.USize
