/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapiext.Testmark
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.annotations.TestOnly
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.ref.MethodResolveVariant
import org.rust.lang.core.resolve.ref.resolvePathRaw
import org.rust.lang.core.types.*
import org.rust.lang.core.types.consts.*
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.*
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.snapshot.CombinedSnapshot
import org.rust.lang.utils.snapshot.Snapshot
import org.rust.openapiext.recursionGuard
import org.rust.stdext.zipValues

fun inferTypesIn(element: RsInferenceContextOwner): RsInferenceResult {
    val items = element.knownItems
    val paramEnv = if (element is RsGenericDeclaration) ParamEnv.buildFor(element) else ParamEnv.EMPTY
    val lookup = ImplLookup(element.project, element.cargoProject, items, paramEnv)
    return recursionGuard(element, Computable { lookup.ctx.infer(element) }, memoize = false)
        ?: error("Can not run nested type inference")
}

sealed class Adjustment(open val target: Ty) {
    class Deref(target: Ty) : Adjustment(target)
    class BorrowReference(
        override val target: TyReference,
        val region: Region? = (target as? TyReference)?.region,
        val mutability: Mutability? = (target as? TyReference)?.mutability
    ) : Adjustment(target)

//    class BorrowPointer(target: Ty, val mutability: Mutability) : Adjustment(target)
}

interface RsInferenceData {
    fun getExprAdjustments(expr: RsExpr): List<Adjustment>
    fun getExprType(expr: RsExpr): Ty
    fun getExpectedExprType(expr: RsExpr): Ty
    fun getPatType(pat: RsPat): Ty
    fun getPatFieldType(patField: RsPatField): Ty
    fun getResolvedPath(expr: RsPathExpr): List<ResolvedPath>
    fun getBindingType(binding: RsPatBinding): Ty =
        when (val parent = binding.parent) {
            is RsPat -> getPatType(parent)
            is RsPatField -> getPatFieldType(parent)
            else -> TyUnknown // impossible
        }
}

/**
 * [RsInferenceResult] is an immutable per-function map
 * from expressions to their types.
 */
class RsInferenceResult(
    val exprTypes: Map<RsExpr, Ty>,
    val patTypes: MutableMap<RsPat, Ty>,
    val patFieldTypes: MutableMap<RsPatField, Ty>,
    private val expectedExprTypes: Map<RsExpr, Ty>,
    private val resolvedPaths: Map<RsPathExpr, List<ResolvedPath>>,
    private val resolvedMethods: Map<RsMethodCall, List<MethodResolveVariant>>,
    private val resolvedFields: Map<RsFieldLookup, List<RsElement>>,
    private val adjustments: Map<RsExpr, List<Adjustment>>,
    val diagnostics: List<RsDiagnostic>
) : RsInferenceData {
    private val timestamp: Long = System.nanoTime()

    override fun getExprAdjustments(expr: RsExpr): List<Adjustment> =
        adjustments[expr] ?: emptyList()

    override fun getExprType(expr: RsExpr): Ty =
        exprTypes[expr] ?: TyUnknown

    override fun getPatType(pat: RsPat): Ty =
        patTypes[pat] ?: TyUnknown

    override fun getPatFieldType(patField: RsPatField): Ty =
        patFieldTypes[patField] ?: TyUnknown

    override fun getExpectedExprType(expr: RsExpr): Ty =
        expectedExprTypes[expr] ?: TyUnknown

    override fun getResolvedPath(expr: RsPathExpr): List<ResolvedPath> =
        resolvedPaths[expr] ?: emptyList()

    fun getResolvedMethod(call: RsMethodCall): List<MethodResolveVariant> =
        resolvedMethods[call] ?: emptyList()

    fun getResolvedField(call: RsFieldLookup): List<RsElement> =
        resolvedFields[call] ?: emptyList()

    @TestOnly
    fun isExprTypeInferred(expr: RsExpr): Boolean =
        expr in exprTypes

    @TestOnly
    fun getTimestamp(): Long = timestamp
}

/**
 * A mutable object, which is filled while we walk function body top down.
 */
class RsInferenceContext(
    val project: Project,
    val lookup: ImplLookup,
    val items: KnownItems
) : RsInferenceData {
    val fulfill: FulfillmentContext = FulfillmentContext(this, lookup)
    private val exprTypes: MutableMap<RsExpr, Ty> = hashMapOf()
    private val patTypes: MutableMap<RsPat, Ty> = hashMapOf()
    private val patFieldTypes: MutableMap<RsPatField, Ty> = hashMapOf()
    private val expectedExprTypes: MutableMap<RsExpr, Ty> = hashMapOf()
    private val resolvedPaths: MutableMap<RsPathExpr, List<ResolvedPath>> = hashMapOf()
    private val resolvedMethods: MutableMap<RsMethodCall, List<MethodResolveVariant>> = hashMapOf()
    private val resolvedFields: MutableMap<RsFieldLookup, List<RsElement>> = hashMapOf()
    private val pathRefinements: MutableList<Pair<RsPathExpr, TraitRef>> = mutableListOf()
    private val methodRefinements: MutableList<Pair<RsMethodCall, TraitRef>> = mutableListOf()
    private val adjustments: MutableMap<RsExpr, MutableList<Adjustment>> = hashMapOf()
    val diagnostics: MutableList<RsDiagnostic> = mutableListOf()

    private val intUnificationTable: UnificationTable<TyInfer.IntVar, TyInteger> = UnificationTable()
    private val floatUnificationTable: UnificationTable<TyInfer.FloatVar, TyFloat> = UnificationTable()
    private val varUnificationTable: UnificationTable<TyInfer.TyVar, Ty> = UnificationTable()
    private val constUnificationTable: UnificationTable<CtInferVar, Const> = UnificationTable()
    private val projectionCache: ProjectionCache = ProjectionCache()

    fun startSnapshot(): Snapshot = CombinedSnapshot(
        intUnificationTable.startSnapshot(),
        floatUnificationTable.startSnapshot(),
        varUnificationTable.startSnapshot(),
        constUnificationTable.startSnapshot(),
        projectionCache.startSnapshot()
    )

    inline fun <T> probe(action: () -> T): T {
        val snapshot = startSnapshot()
        try {
            return action()
        } finally {
            snapshot.rollback()
        }
    }

    inline fun <T : Any> commitIfNotNull(action: () -> T?): T? {
        val snapshot = startSnapshot()
        val result = action()
        if (result == null) snapshot.rollback() else snapshot.commit()
        return result
    }

    fun infer(element: RsInferenceContextOwner): RsInferenceResult {
        when (element) {
            is RsFunction -> {
                val fctx = RsTypeInferenceWalker(this, element.returnType)
                fctx.extractParameterBindings(element)
                element.block?.let { fctx.inferFnBody(it) }
            }
            is RsReplCodeFragment -> {
                element.context.inference?.let {
                    patTypes.putAll(it.patTypes)
                    patFieldTypes.putAll(it.patFieldTypes)
                    exprTypes.putAll(it.exprTypes)
                }
                RsTypeInferenceWalker(this, TyUnknown).inferReplCodeFragment(element)
            }
            is RsBaseType, is RsTraitRef -> {
                val path = when (element) {
                    is RsBaseType -> element.path
                    is RsTraitRef -> element.path
                    else -> null
                }
                val declaration = path?.let { resolvePathRaw(it, lookup) }?.singleOrNull()?.element as? RsGenericDeclaration
                val constParameters = declaration?.constParameters.orEmpty()
                val constArguments = path?.constArguments.orEmpty()
                RsTypeInferenceWalker(this, TyUnknown).inferConstArgumentTypes(constParameters, constArguments)
            }
            else -> {
                val (retTy, expr) = when (element) {
                    is RsConstant -> element.typeReference?.type to element.expr
                    is RsArrayType -> TyInteger.USize to element.expr
                    is RsVariantDiscriminant -> {
                        val enum = element.contextStrict<RsEnumItem>()
                        enum?.reprType to element.expr
                    }
                    is RsExpressionCodeFragment -> {
                        element.context.inference?.let {
                            patTypes.putAll(it.patTypes)
                            patFieldTypes.putAll(it.patFieldTypes)
                            exprTypes.putAll(it.exprTypes)
                        }
                        null to element.expr
                    }
                    else -> error("Type inference is not implemented for PSI element of type " +
                        "`${element.javaClass}` that implement `RsInferenceContextOwner`")
                }
                if (expr != null) {
                    RsTypeInferenceWalker(this, retTy ?: TyUnknown).inferLambdaBody(expr)
                }
            }
        }

        fulfill.selectWherePossible()

        fallbackUnresolvedTypeVarsIfPossible()

        fulfill.selectWherePossible()

        exprTypes.replaceAll { _, ty -> fullyResolve(ty) }
        expectedExprTypes.replaceAll { _, ty -> fullyResolveWithOrigins(ty) }
        patTypes.replaceAll { _, ty -> fullyResolve(ty) }
        patFieldTypes.replaceAll { _, ty -> fullyResolve(ty) }
        // replace types in diagnostics for better quick fixes
        diagnostics.replaceAll { if (it is RsDiagnostic.TypeError) fullyResolve(it) else it }

        performPathsRefinement(lookup)

        return RsInferenceResult(
            exprTypes,
            patTypes,
            patFieldTypes,
            expectedExprTypes,
            resolvedPaths,
            resolvedMethods,
            resolvedFields,
            adjustments,
            diagnostics
        )
    }

    private fun fallbackUnresolvedTypeVarsIfPossible() {
        val allTypes = exprTypes.values.asSequence() + patTypes.values.asSequence() +
            patFieldTypes.values.asSequence() + expectedExprTypes.values.asSequence()
        for (ty in allTypes) {
            ty.visitInferTys { tyInfer ->
                val rty = shallowResolve(tyInfer)
                if (rty is TyInfer) {
                    fallbackIfPossible(rty)
                }
                false
            }
        }
    }

    private fun fallbackIfPossible(ty: TyInfer) {
        when (ty) {
            is TyInfer.IntVar -> intUnificationTable.unifyVarValue(ty, TyInteger.DEFAULT)
            is TyInfer.FloatVar -> floatUnificationTable.unifyVarValue(ty, TyFloat.DEFAULT)
            is TyInfer.TyVar -> Unit
        }
    }

    private fun performPathsRefinement(lookup: ImplLookup) {
        for ((path, traitRef) in pathRefinements) {
            val variant = resolvedPaths[path]?.firstOrNull() ?: continue
            val fnName = (variant.element as? RsFunction)?.name
            val impl = lookup.select(resolveTypeVarsIfPossible(traitRef)).ok()?.impl as? RsImplItem ?: continue
            val fn = impl.expandedMembers.functions.find { it.name == fnName } ?: continue
            val source = TraitImplSource.ExplicitImpl(RsCachedImplItem.forImpl(impl))
            resolvedPaths[path] = listOf(ResolvedPath.AssocItem(fn, source))
        }
        for ((call, traitRef) in methodRefinements) {
            val variant = resolvedMethods[call]?.firstOrNull() ?: continue
            val impl = lookup.select(resolveTypeVarsIfPossible(traitRef)).ok()?.impl as? RsImplItem ?: continue
            val fn = impl.expandedMembers.functions.find { it.name == variant.name } ?: continue
            val source = TraitImplSource.ExplicitImpl(RsCachedImplItem.forImpl(impl))
            resolvedMethods[call] = listOf(variant.copy(element = fn, source = source))
        }
    }

    override fun getExprAdjustments(expr: RsExpr): List<Adjustment> {
        return adjustments[expr] ?: emptyList()
    }

    override fun getExprType(expr: RsExpr): Ty {
        return exprTypes[expr] ?: TyUnknown
    }

    override fun getPatType(pat: RsPat): Ty {
        return patTypes[pat] ?: TyUnknown
    }

    override fun getPatFieldType(patField: RsPatField): Ty {
        return patFieldTypes[patField] ?: TyUnknown
    }

    override fun getExpectedExprType(expr: RsExpr): Ty {
        return expectedExprTypes[expr] ?: TyUnknown
    }

    override fun getResolvedPath(expr: RsPathExpr): List<ResolvedPath> {
        return resolvedPaths[expr] ?: emptyList()
    }

    fun isTypeInferred(expr: RsExpr): Boolean {
        return exprTypes.containsKey(expr)
    }

    fun writeExprTy(psi: RsExpr, ty: Ty) {
        exprTypes[psi] = ty
    }

    fun writePatTy(psi: RsPat, ty: Ty) {
        patTypes[psi] = ty
    }

    fun writePatFieldTy(psi: RsPatField, ty: Ty) {
        patFieldTypes[psi] = ty
    }

    fun writeExpectedExprTy(psi: RsExpr, ty: Ty) {
        expectedExprTypes[psi] = ty
    }

    fun writePath(path: RsPathExpr, resolved: List<ResolvedPath>) {
        resolvedPaths[path] = resolved
    }

    fun writeResolvedMethod(call: RsMethodCall, resolvedTo: List<MethodResolveVariant>) {
        resolvedMethods[call] = resolvedTo
    }

    fun writeResolvedField(lookup: RsFieldLookup, resolvedTo: List<RsElement>) {
        resolvedFields[lookup] = resolvedTo
    }

    fun registerPathRefinement(path: RsPathExpr, traitRef: TraitRef) {
        pathRefinements.add(Pair(path, traitRef))
    }

    fun registerMethodRefinement(path: RsMethodCall, traitRef: TraitRef) {
        methodRefinements.add(Pair(path, traitRef))
    }

    fun addDiagnostic(diagnostic: RsDiagnostic) {
        if (diagnostic.element.containingFile.isPhysical) {
            diagnostics.add(diagnostic)
        }
    }

    fun addAdjustment(expression: RsExpr, adjustment: Adjustment, count: Int = 1) {
        repeat(count) {
            adjustments.getOrPut(expression) { mutableListOf() }.add(adjustment)
        }
    }

    fun reportTypeMismatch(element: RsElement, expected: Ty, actual: Ty) {
        addDiagnostic(RsDiagnostic.TypeError(element, expected, actual))
    }

    fun canCombineTypes(ty1: Ty, ty2: Ty): Boolean {
        return probe { combineTypes(ty1, ty2).isOk }
    }

    fun combineTypesIfOk(ty1: Ty, ty2: Ty): Boolean {
        return combineTypesIfOkResolved(shallowResolve(ty1), shallowResolve(ty2))
    }

    private fun combineTypesIfOkResolved(ty1: Ty, ty2: Ty): Boolean {
        val snapshot = startSnapshot()
        val res = combineTypesResolved(ty1, ty2).isOk
        if (res) {
            snapshot.commit()
        } else {
            snapshot.rollback()
        }
        return res
    }

    fun combineTypes(ty1: Ty, ty2: Ty): CoerceResult {
        return combineTypesResolved(shallowResolve(ty1), shallowResolve(ty2))
    }

    private fun combineTypesResolved(ty1: Ty, ty2: Ty): CoerceResult {
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

    private fun combineTyVar(ty1: TyInfer.TyVar, ty2: Ty): CoerceResult {
        when (ty2) {
            is TyInfer.TyVar -> varUnificationTable.unifyVarVar(ty1, ty2)
            else -> {
                val ty1r = varUnificationTable.findRoot(ty1)
                val isTy2ContainsTy1 = ty2.visitWith(object : TypeVisitor {
                    override fun visitTy(ty: Ty): Boolean = when {
                        ty is TyInfer.TyVar && varUnificationTable.findRoot(ty) == ty1r -> true
                        ty.hasTyInfer -> ty.superVisitWith(this)
                        else -> false
                    }
                })
                if (isTy2ContainsTy1) {
                    // "E0308 cyclic type of infinite size"
                    TypeInferenceMarks.cyclicType.hit()
                    varUnificationTable.unifyVarValue(ty1r, TyUnknown)
                } else {
                    varUnificationTable.unifyVarValue(ty1r, ty2)
                }
            }
        }
        return CoerceResult.Ok
    }

    private fun combineIntOrFloatVar(ty1: TyInfer, ty2: Ty): CoerceResult {
        when (ty1) {
            is TyInfer.IntVar -> when (ty2) {
                is TyInfer.IntVar -> intUnificationTable.unifyVarVar(ty1, ty2)
                is TyInteger -> intUnificationTable.unifyVarValue(ty1, ty2)
                else -> return CoerceResult.TypeMismatch(ty1, ty2)
            }
            is TyInfer.FloatVar -> when (ty2) {
                is TyInfer.FloatVar -> floatUnificationTable.unifyVarVar(ty1, ty2)
                is TyFloat -> floatUnificationTable.unifyVarValue(ty1, ty2)
                else -> return CoerceResult.TypeMismatch(ty1, ty2)
            }
            is TyInfer.TyVar -> error("unreachable")
        }
        return CoerceResult.Ok
    }

    fun combineTypesNoVars(ty1: Ty, ty2: Ty): CoerceResult =
        when {
            ty1 === ty2 -> CoerceResult.Ok
            ty1 is TyPrimitive && ty2 is TyPrimitive && ty1 == ty2 -> CoerceResult.Ok
            ty1 is TyTypeParameter && ty2 is TyTypeParameter && ty1 == ty2 -> CoerceResult.Ok
            ty1 is TyProjection && ty2 is TyProjection && ty1.target == ty2.target && combineBoundElements(ty1.trait, ty2.trait) -> {
                combineTypes(ty1.type, ty2.type)
            }
            ty1 is TyReference && ty2 is TyReference && ty1.mutability == ty2.mutability -> {
                combineTypes(ty1.referenced, ty2.referenced)
            }
            ty1 is TyPointer && ty2 is TyPointer && ty1.mutability == ty2.mutability -> {
                combineTypes(ty1.referenced, ty2.referenced)
            }
            ty1 is TyArray && ty2 is TyArray && (ty1.size == null || ty2.size == null || ty1.size == ty2.size) ->
                combineTypes(ty1.base, ty2.base).and { combineConsts(ty1.const, ty2.const) }
            ty1 is TySlice && ty2 is TySlice -> combineTypes(ty1.elementType, ty2.elementType)
            ty1 is TyTuple && ty2 is TyTuple && ty1.types.size == ty2.types.size -> {
                combineTypePairs(ty1.types.zip(ty2.types))
            }
            ty1 is TyFunction && ty2 is TyFunction && ty1.paramTypes.size == ty2.paramTypes.size -> {
                combineTypePairs(ty1.paramTypes.zip(ty2.paramTypes))
                    .and { combineTypes(ty1.retType, ty2.retType) }
            }
            ty1 is TyAdt && ty2 is TyAdt && ty1.item == ty2.item -> {
                combineTypePairs(ty1.typeArguments.zip(ty2.typeArguments))
                    .and { combineConstPairs(ty1.constArguments.zip(ty2.constArguments)) }
            }
            ty1 is TyTraitObject && ty2 is TyTraitObject &&
                // TODO: Use all trait bounds
                combineBoundElements(ty1.traits.first(), ty2.traits.first()) -> CoerceResult.Ok
            ty1 is TyAnon && ty2 is TyAnon && ty1.definition != null && ty1.definition == ty2.definition -> CoerceResult.Ok
            ty1 is TyNever || ty2 is TyNever -> CoerceResult.Ok
            else -> CoerceResult.TypeMismatch(ty1, ty2)
        }

    fun combineConsts(const1: Const, const2: Const): CoerceResult {
        return combineConstsResolved(shallowResolve(const1), shallowResolve(const2))
    }

    private fun combineConstsResolved(const1: Const, const2: Const): CoerceResult =
        when {
            const1 is CtInferVar -> combineConstVar(const1, const2)
            const2 is CtInferVar -> combineConstVar(const2, const1)
            else -> combineConstsNoVars(const1, const2)
        }

    private fun combineConstVar(const1: CtInferVar, const2: Const): CoerceResult {
        if (const2 is CtInferVar) {
            constUnificationTable.unifyVarVar(const1, const2)
        } else {
            val const1r = constUnificationTable.findRoot(const1)
            constUnificationTable.unifyVarValue(const1r, const2)
        }
        return CoerceResult.Ok
    }

    private fun combineConstsNoVars(const1: Const, const2: Const): CoerceResult =
        when {
            const1 === const2 -> CoerceResult.Ok
            const1 is CtUnknown || const2 is CtUnknown -> CoerceResult.Ok
            const1 is CtUnevaluated || const2 is CtUnevaluated -> CoerceResult.Ok
            const1 == const2 -> CoerceResult.Ok
            else -> CoerceResult.ConstMismatch(const1, const2)
        }

    fun combineTypePairs(pairs: List<Pair<Ty, Ty>>): CoerceResult = combinePairs(pairs, ::combineTypes)

    fun combineConstPairs(pairs: List<Pair<Const, Const>>): CoerceResult = combinePairs(pairs, ::combineConsts)

    private fun <T : Kind> combinePairs(pairs: List<Pair<T, T>>, combine: (T, T) -> CoerceResult): CoerceResult {
        var canUnify: CoerceResult = CoerceResult.Ok
        for ((k1, k2) in pairs) {
            canUnify = combine(k1, k2).and { canUnify }
        }
        return canUnify
    }

    fun combineTraitRefs(ref1: TraitRef, ref2: TraitRef): Boolean =
        ref1.trait.element == ref2.trait.element &&
            combineTypes(ref1.selfTy, ref2.selfTy).isOk &&
            ref1.trait.subst.zipTypeValues(ref2.trait.subst).all { (a, b) ->
                combineTypes(a, b).isOk
            } &&
            ref1.trait.subst.zipConstValues(ref2.trait.subst).all { (a, b) ->
                combineConsts(a, b).isOk
            }

    fun <T : RsElement> combineBoundElements(be1: BoundElement<T>, be2: BoundElement<T>): Boolean =
        be1.element == be2.element &&
            combineTypePairs(be1.subst.zipTypeValues(be2.subst)).isOk &&
            combineConstPairs(be1.subst.zipConstValues(be2.subst)).isOk &&
            combineTypePairs(zipValues(be1.assoc, be2.assoc)).isOk

    fun <T : TypeFoldable<T>> shallowResolve(value: T): T = value.foldWith(shallowResolver)

    private inner class ShallowResolver : TypeFolder {

        override fun foldTy(ty: Ty): Ty = shallowResolve(ty)

        override fun foldConst(const: Const): Const =
            if (const is CtInferVar) {
                constUnificationTable.findValue(const) ?: const
            } else {
                const
            }

        private fun shallowResolve(ty: Ty): Ty {
            if (ty !is TyInfer) return ty

            return when (ty) {
                is TyInfer.IntVar -> intUnificationTable.findValue(ty) ?: ty
                is TyInfer.FloatVar -> floatUnificationTable.findValue(ty) ?: ty
                is TyInfer.TyVar -> varUnificationTable.findValue(ty)?.let(this::shallowResolve) ?: ty
            }
        }
    }

    private val shallowResolver: ShallowResolver = ShallowResolver()

    fun <T : TypeFoldable<T>> resolveTypeVarsIfPossible(value: T): T = value.foldWith(opportunisticVarResolver)

    private inner class OpportunisticVarResolver : TypeFolder {
        override fun foldTy(ty: Ty): Ty {
            if (!ty.needsInfer) return ty
            val res = shallowResolve(ty)
            return res.superFoldWith(this)
        }

        override fun foldConst(const: Const): Const {
            if (!const.hasCtInfer) return const
            val res = shallowResolve(const)
            return res.superFoldWith(this)
        }
    }

    private val opportunisticVarResolver: OpportunisticVarResolver = OpportunisticVarResolver()

    /**
     * Full type resolution replaces all type and const variables with their concrete results.
     */
    fun <T : TypeFoldable<T>> fullyResolve(value: T): T {
        return value.foldWith(fullTypeResolver)
    }

    private inner class FullTypeResolver : TypeFolder {
        override fun foldTy(ty: Ty): Ty {
            if (!ty.needsInfer) return ty
            val res = shallowResolve(ty)
            return if (res is TyInfer) TyUnknown else res.superFoldWith(this)
        }

        override fun foldConst(const: Const): Const =
            if (const is CtInferVar) {
                constUnificationTable.findValue(const) ?: CtUnknown
            } else {
                const
            }
    }

    private val fullTypeResolver: FullTypeResolver = FullTypeResolver()

    /**
     * Similar to [fullyResolve], but replaces unresolved [TyInfer.TyVar] to its [TyInfer.TyVar.origin]
     * instead of [TyUnknown]
     */
    fun <T : TypeFoldable<T>> fullyResolveWithOrigins(value: T): T {
        return value.foldWith(fullTypeWithOriginsResolver)
    }

    private inner class FullTypeWithOriginsResolver : TypeFolder {
        override fun foldTy(ty: Ty): Ty {
            if (!ty.needsInfer) return ty
            return when (val res = shallowResolve(ty)) {
                is TyUnknown -> (ty as? TyInfer.TyVar)?.origin as? TyTypeParameter ?: TyUnknown
                is TyInfer.TyVar -> res.origin as? TyTypeParameter ?: TyUnknown
                is TyInfer -> TyUnknown
                else -> res.superFoldWith(this)
            }
        }

        override fun foldConst(const: Const): Const =
            if (const is CtInferVar) {
                constUnificationTable.findValue(const) ?: CtUnknown
            } else {
                const
            }
    }

    private val fullTypeWithOriginsResolver: FullTypeWithOriginsResolver = FullTypeWithOriginsResolver()

    fun typeVarForParam(ty: TyTypeParameter): Ty = TyInfer.TyVar(ty)

    fun constVarForParam(const: CtConstParameter): Const = CtInferVar(const)

    /** Deeply normalize projection types. See [normalizeProjectionType] */
    fun <T : TypeFoldable<T>> normalizeAssociatedTypesIn(ty: T, recursionDepth: Int = 0): TyWithObligations<T> {
        val obligations = mutableListOf<Obligation>()
        val normTy = ty.foldTyProjectionWith {
            val normTy = normalizeProjectionType(it, recursionDepth)
            obligations += normTy.obligations
            normTy.value
        }

        return TyWithObligations(normTy, obligations)
    }

    /**
     * Normalize a specific projection like `<T as Trait>::Item`.
     * The result is always a type (and possibly additional obligations).
     * If ambiguity arises, which implies that
     * there are unresolved type variables in the projection, we will
     * substitute a fresh type variable `$X` and generate a new
     * obligation `<T as Trait>::Item == $X` for later.
     */
    private fun normalizeProjectionType(projectionTy: TyProjection, recursionDepth: Int): TyWithObligations<Ty> {
        return optNormalizeProjectionType(projectionTy, recursionDepth) ?: run {
            val tyVar = TyInfer.TyVar(projectionTy)
            val obligation = Obligation(recursionDepth + 1, Predicate.Projection(projectionTy, tyVar))
            TyWithObligations(tyVar, listOf(obligation))
        }
    }

    /**
     * Normalize a specific projection like `<T as Trait>::Item`.
     * The result is always a type (and possibly additional obligations).
     * Returns `null` in the case of ambiguity, which indicates that there
     * are unbound type variables.
     */
    fun optNormalizeProjectionType(projectionTy: TyProjection, recursionDepth: Int): TyWithObligations<Ty>? =
        optNormalizeProjectionTypeResolved(resolveTypeVarsIfPossible(projectionTy) as TyProjection, recursionDepth)

    /** See [optNormalizeProjectionType] */
    private fun optNormalizeProjectionTypeResolved(
        projectionTy: TyProjection,
        recursionDepth: Int
    ): TyWithObligations<Ty>? {
        if (projectionTy.type is TyInfer.TyVar) return null

        return when (val cacheResult = projectionCache.tryStart(projectionTy)) {
            ProjectionCacheEntry.Ambiguous -> {
                // If we found ambiguity the last time, that generally
                // means we will continue to do so until some type in the
                // key changes (and we know it hasn't, because we just
                // fully resolved it).
                // TODO rustc has an exception for closure types here
                null
            }
            ProjectionCacheEntry.InProgress -> {
                // While normalized A::B we are asked to normalize A::B.
                // TODO rustc halts the compilation immediately (panics) here
                TyWithObligations(TyUnknown)
            }
            ProjectionCacheEntry.Error -> {
                // TODO report an error. See rustc's `normalize_to_error`
                TyWithObligations(TyUnknown)
            }
            is ProjectionCacheEntry.NormalizedTy -> {
                var ty = cacheResult.ty
                // If we find the value in the cache, then return it along
                // with the obligations that went along with it. Note
                // that, when using a fulfillment context, these
                // obligations could in principle be ignored: they have
                // already been registered when the cache entry was
                // created (and hence the new ones will quickly be
                // discarded as duplicated). But when doing trait
                // evaluation this is not the case.
                // (See rustc's https://github.com/rust-lang/rust/issues/43132 )
                if (!hasUnresolvedTypeVars(ty.value)) {
                    // Once we have inferred everything we need to know, we
                    // can ignore the `obligations` from that point on.
                    ty = TyWithObligations(ty.value)
                    projectionCache.putTy(projectionTy, ty)
                }
                ty
            }
            null -> {
                when (val selResult = lookup.selectProjection(projectionTy, recursionDepth)) {
                    is SelectionResult.Ok -> {
                        val result = selResult.result ?: TyWithObligations(projectionTy)
                        projectionCache.putTy(projectionTy, pruneCacheValueObligations(result))
                        result
                    }
                    is SelectionResult.Err -> {
                        projectionCache.error(projectionTy)
                        // TODO report an error. See rustc's `normalize_to_error`
                        TyWithObligations(TyUnknown)
                    }
                    is SelectionResult.Ambiguous -> {
                        projectionCache.ambiguous(projectionTy)
                        null
                    }
                }
            }
        }
    }

    /**
     * If there are unresolved type variables, then we need to include
     * any subobligations that bind them, at least until those type
     * variables are fully resolved.
     */
    private fun pruneCacheValueObligations(ty: TyWithObligations<Ty>): TyWithObligations<Ty> {
        if (!hasUnresolvedTypeVars(ty.value)) return TyWithObligations(ty.value)

        // I don't completely understand why we leave the only projection
        // predicates here, but here is the comment from rustc about it
        //
        // If we found a `T: Foo<X = U>` predicate, let's check
        // if `U` references any unresolved type
        // variables. In principle, we only care if this
        // projection can help resolve any of the type
        // variables found in `result.value` -- but we just
        // check for any type variables here, for fear of
        // indirect obligations (e.g., we project to `?0`,
        // but we have `T: Foo<X = ?1>` and `?1: Bar<X =
        // ?0>`).
        //
        // We are only interested in `T: Foo<X = U>` predicates, where
        // `U` references one of `unresolved_type_vars`.
        val obligations = ty.obligations
            .filter { it.predicate is Predicate.Projection && hasUnresolvedTypeVars(it.predicate) }

        return TyWithObligations(ty.value, obligations)
    }

    private fun <T : TypeFoldable<T>> hasUnresolvedTypeVars(_ty: T): Boolean = _ty.visitWith(object : TypeVisitor {
        override fun visitTy(ty: Ty): Boolean {
            val resolvedTy = shallowResolve(ty)
            return when {
                resolvedTy is TyInfer -> true
                resolvedTy.hasTyInfer -> resolvedTy.superVisitWith(this)
                else -> false
            }
        }
    })

    fun <T : TypeFoldable<T>> hasResolvableTypeVars(ty: T): Boolean =
        ty.visitInferTys { it != shallowResolve(it) }

    /** Return true if [ty] was instantiated or unified with another type variable */
    fun isTypeVarAffected(ty: TyInfer.TyVar): Boolean =
        varUnificationTable.findRoot(ty) != ty || varUnificationTable.findValue(ty) != null

    fun instantiateBounds(
        bounds: List<Predicate>,
        subst: Substitution = emptySubstitution,
        recursionDepth: Int = 0
    ): Sequence<Obligation> {
        return bounds.asSequence()
            .map { it.substitute(subst) }
            .map { normalizeAssociatedTypesIn(it, recursionDepth) }
            .flatMap { it.obligations.asSequence() + Obligation(recursionDepth, it.value) }
    }

    fun instantiateBounds(
        element: RsGenericDeclaration,
        selfTy: Ty? = null,
        subst: Substitution = emptySubstitution
    ): Substitution {
        val map = run {
            val typeSubst = element
                .generics
                .associateWith { typeVarForParam(it) }
                .let { if (selfTy != null) it + (TyTypeParameter.self() to selfTy) else it }
            val constSubst = element
                .constGenerics
                .associateWith { constVarForParam(it) }
            subst + Substitution(typeSubst = typeSubst, constSubst = constSubst)
        }
        instantiateBounds(element.predicates, map).forEach(fulfill::registerPredicateObligation)
        return map
    }

    /** Checks that [selfTy] satisfies all trait bounds of the [source] */
    fun canEvaluateBounds(source: TraitImplSource, selfTy: Ty): Boolean {
        return when (source) {
            is TraitImplSource.ExplicitImpl -> canEvaluateBounds(source.value, selfTy)
            is TraitImplSource.Derived, is TraitImplSource.Hardcoded -> {
                if (source.value.typeParameters.isNotEmpty()) return true
                lookup.canSelect(TraitRef(selfTy, BoundElement(source.value as RsTraitItem)))
            }
            else -> return true
        }
    }

    /** Checks that [selfTy] satisfies all trait bounds of the [impl] */
    private fun canEvaluateBounds(impl: RsImplItem, selfTy: Ty): Boolean {
        val ff = FulfillmentContext(this, lookup)
        val subst = Substitution(
            typeSubst = impl.generics.associateWith { typeVarForParam(it) },
            constSubst = impl.constGenerics.associateWith { constVarForParam(it) }
        )
        return probe {
            instantiateBounds(impl.predicates, subst).forEach(ff::registerPredicateObligation)
            impl.typeReference?.type?.substitute(subst)?.let { combineTypes(selfTy, it) }
            ff.selectUntilError()
        }
    }

    fun instantiateMethodOwnerSubstitution(
        callee: AssocItemScopeEntryBase<*>,
        methodCall: RsMethodCall? = null
    ): Substitution = when (val source = callee.source) {
        is TraitImplSource.ExplicitImpl -> {
            val impl = source.value
            val typeParameters = instantiateBounds(impl)
            source.type?.substitute(typeParameters)?.let { combineTypes(callee.selfTy, it) }
            if (callee.element.owner is RsAbstractableOwner.Trait) {
                source.implementedTrait?.substitute(typeParameters)?.subst ?: emptySubstitution
            } else {
                typeParameters
            }
        }
        is TraitImplSource.TraitBound -> lookup.getEnvBoundTransitivelyFor(callee.selfTy)
            .find { it.element == source.value }?.subst ?: emptySubstitution

        is TraitImplSource.ProjectionBound -> {
            val ty = callee.selfTy as TyProjection
            val subst = ty.trait.subst + mapOf(TyTypeParameter.self() to ty.type).toTypeSubst()
            val bound = ty.trait.element.bounds
                .find { it.trait.element == source.value && probe { combineTypes(it.selfTy.substitute(subst), ty) }.isOk }
            bound?.trait?.subst?.substituteInValues(subst) ?: emptySubstitution
        }

        is TraitImplSource.Derived -> emptySubstitution

        is TraitImplSource.Object -> when (val selfTy = callee.selfTy) {
            is TyAnon -> selfTy.getTraitBoundsTransitively()
                .find { it.element == source.value }?.subst ?: emptySubstitution
            is TyTraitObject -> selfTy.getTraitBoundsTransitively()
                .find { it.element == source.value }?.subst ?: emptySubstitution
            else -> emptySubstitution
        }
        is TraitImplSource.Collapsed, is TraitImplSource.Hardcoded -> {
            // Method has been resolved to a trait, so we should add a predicate
            // `Self : Trait<Args>` to select args and also refine method path if possible.
            // Method path refinement needed if there are multiple impls of the same trait to the same type
            val trait = source.value as RsTraitItem
            val typeParameters = instantiateBounds(trait)
            val subst = Substitution(
                typeSubst = trait.generics.associateBy { it },
                constSubst = trait.constGenerics.associateBy { it }
            )
            val boundTrait = BoundElement(trait, subst).substitute(typeParameters)
            val traitRef = TraitRef(callee.selfTy, boundTrait)
            fulfill.registerPredicateObligation(Obligation(Predicate.Trait(traitRef)))
            if (methodCall != null) {
                registerMethodRefinement(methodCall, traitRef)
            }
            typeParameters
        }
        is TraitImplSource.Trait -> {
            if (isUnitTestMode) error("unreachable")
            emptySubstitution
        }
    }
}

val RsGenericDeclaration.generics: List<TyTypeParameter>
    get() = typeParameters.map { TyTypeParameter.named(it) }

val RsGenericDeclaration.constGenerics: List<CtConstParameter>
    get() = constParameters.map { CtConstParameter(it) }

val RsGenericDeclaration.bounds: List<TraitRef>
    get() = predicates.mapNotNull { (it as? Predicate.Trait)?.trait }

val RsGenericDeclaration.predicates: List<Predicate>
    get() = CachedValuesManager.getCachedValue(this) {
        CachedValueProvider.Result.create(
            doGetPredicates(),
            rustStructureOrAnyPsiModificationTracker
        )
    }

private fun RsGenericDeclaration.doGetPredicates(): List<Predicate> {
    val whereBounds = whereClause?.wherePredList.orEmpty().asSequence()
        .flatMap {
            val selfTy = it.typeReference?.type ?: return@flatMap emptySequence<Predicate>()
            it.typeParamBounds?.polyboundList.toPredicates(selfTy)
        }
    val bounds = typeParameters.asSequence().flatMap {
        val selfTy = TyTypeParameter.named(it)
        it.typeParamBounds?.polyboundList.toPredicates(selfTy)
    }
    val assocTypeBounds = if (this is RsTraitItem) {
        expandedMembers.types.asSequence().flatMap { it.typeParamBounds?.polyboundList.toPredicates(it.declaredType) }
    } else {
        emptySequence()
    }
    return (bounds + whereBounds + assocTypeBounds).toList()
}

private fun List<RsPolybound>?.toPredicates(selfTy: Ty): Sequence<Predicate> = orEmpty().asSequence()
    .filter { !it.hasQ } // ignore `?Sized`
    .flatMap { bound ->
        val traitRef = bound.bound.traitRef ?: return@flatMap emptySequence<Predicate>()
        val boundTrait = traitRef.resolveToBoundTrait() ?: return@flatMap emptySequence<Predicate>()

        val assocTypeBounds = traitRef.path.typeArgumentList?.assocTypeBindingList.orEmpty().asSequence()
            .flatMap nestedFlatMap@{
                val assoc = it.reference.resolve() as? RsTypeAlias
                    ?: return@nestedFlatMap emptySequence<Predicate>()
                val projectionTy = TyProjection.valueOf(selfTy, assoc)
                val typeRef = it.typeReference
                if (typeRef != null) {
                    // T: Iterator<Item = Foo>
                    //             ~~~~~~~~~~ expands to predicate `T::Item = Foo`
                    sequenceOf(Predicate.Equate(projectionTy, typeRef.type))
                } else {
                    // T: Iterator<Item: Debug>
                    //             ~~~~~~~~~~~ equivalent to `T::Item: Debug`
                    it.polyboundList.toPredicates(projectionTy)
                }
            }
        sequenceOf(Predicate.Trait(TraitRef(selfTy, boundTrait))) + assocTypeBounds
    }


data class TyWithObligations<out T>(
    val value: T,
    val obligations: List<Obligation> = emptyList()
)

fun <T> TyWithObligations<T>.withObligations(addObligations: List<Obligation>) =
    TyWithObligations(value, obligations + addObligations)

sealed class ResolvedPath {
    abstract val element: RsElement

    class Item(override val element: RsElement) : ResolvedPath()

    class AssocItem(
        override val element: RsAbstractable,
        val source: TraitImplSource
    ) : ResolvedPath()

    companion object {
        fun from(entry: ScopeEntry): ResolvedPath? {
            return if (entry is AssocItemScopeEntry) {
                AssocItem(entry.element, entry.source)
            } else {
                entry.element?.let { Item(it) }
            }
        }

        fun from(entry: AssocItemScopeEntry): ResolvedPath =
            AssocItem(entry.element, entry.source)
    }
}

sealed class CoerceResult {
    object Ok : CoerceResult()
    class TypeMismatch(val ty1: Ty, val ty2: Ty) : CoerceResult()
    class ConstMismatch(val const1: Const, val const2: Const) : CoerceResult()

    val isOk: Boolean get() = this is Ok
}

inline fun CoerceResult.and(rhs: () -> CoerceResult): CoerceResult = if (isOk) rhs() else this

object TypeInferenceMarks {
    val cyclicType = Testmark("cyclicType")
    val questionOperator = Testmark("questionOperator")
    val methodPickTraitScope = Testmark("methodPickTraitScope")
    val methodPickTraitsOutOfScope = Testmark("methodPickTraitsOutOfScope")
    val methodPickCheckBounds = Testmark("methodPickCheckBounds")
    val methodPickDerefOrder = Testmark("methodPickDerefOrder")
    val methodPickCollapseTraits = Testmark("methodPickCollapseTraits")
    val traitSelectionSpecialization = Testmark("traitSelectionSpecialization")
    val macroExprDepthLimitReached = Testmark("reachMacroExprDepthLimit")
}
