/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import com.intellij.openapi.project.Project
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.annotations.TestOnly
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.ref.MethodResolveVariant
import org.rust.lang.core.resolve.ref.pathPsiSubst
import org.rust.lang.core.resolve.ref.resolvePathRaw
import org.rust.lang.core.types.*
import org.rust.lang.core.types.consts.*
import org.rust.lang.core.types.infer.TypeError.ConstMismatch
import org.rust.lang.core.types.infer.TypeError.TypeMismatch
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.ty.Mutability.IMMUTABLE
import org.rust.lang.core.types.ty.Mutability.MUTABLE
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.snapshot.CombinedSnapshot
import org.rust.lang.utils.snapshot.Snapshot
import org.rust.openapiext.Testmark
import org.rust.openapiext.recursionGuard
import org.rust.openapiext.testAssert
import org.rust.stdext.*
import org.rust.stdext.RsResult.Err
import org.rust.stdext.RsResult.Ok

fun inferTypesIn(element: RsInferenceContextOwner): RsInferenceResult {
    val items = element.knownItems
    val paramEnv = if (element is RsItemElement) ParamEnv.buildFor(element) else ParamEnv.EMPTY
    val lookup = ImplLookup(element.project, element.containingCrate, items, paramEnv)
    return recursionGuard(element, { lookup.ctx.infer(element) }, memoize = false)
        ?: error("Can not run nested type inference")
}

sealed class Adjustment: TypeFoldable<Adjustment> {
    abstract val target: Ty
    override fun superVisitWith(visitor: TypeVisitor): Boolean = target.visitWith(visitor)

    class NeverToAny(override val target: Ty) : Adjustment() {
        override fun superFoldWith(folder: TypeFolder): Adjustment = NeverToAny(target.foldWith(folder))
    }

    class Deref(
        override val target: Ty,
        /** Non-null if dereference has been done using Deref/DerefMut trait */
        val overloaded: Mutability?
    ) : Adjustment() {
        override fun superFoldWith(folder: TypeFolder): Adjustment = Deref(target.foldWith(folder), overloaded)
    }

    class BorrowReference(
        override val target: TyReference
    ) : Adjustment() {
        val region: Region = target.region
        val mutability: Mutability = target.mutability
        override fun superFoldWith(folder: TypeFolder): Adjustment = BorrowReference(target.foldWith(folder) as TyReference)
    }

    class BorrowPointer(
        override val target: TyPointer,
    ) : Adjustment() {
        val mutability: Mutability = target.mutability
        override fun superFoldWith(folder: TypeFolder): Adjustment = BorrowPointer(target.foldWith(folder) as TyPointer)
    }

    class MutToConstPointer(override val target: TyPointer) : Adjustment() {
        override fun superFoldWith(folder: TypeFolder): Adjustment = MutToConstPointer(target.foldWith(folder) as TyPointer)
    }

    class Unsize(override val target: Ty) : Adjustment() {
        override fun superFoldWith(folder: TypeFolder): Adjustment = Unsize(target.foldWith(folder))
    }
}

interface RsInferenceData {
    fun getExprAdjustments(expr: RsElement): List<Adjustment>
    fun getExprType(expr: RsExpr): Ty
    fun getExpectedExprType(expr: RsExpr): ExpectedType
    fun getPatType(pat: RsPat): Ty
    fun getPatFieldType(patField: RsPatField): Ty
    fun getResolvedPath(expr: RsPathExpr): List<ResolvedPath>
    fun isOverloadedOperator(expr: RsExpr): Boolean
    fun getBindingType(binding: RsPatBinding): Ty =
        when (val parent = binding.parent) {
            is RsPat -> getPatType(parent)
            is RsPatField -> getPatFieldType(parent)
            else -> TyUnknown // impossible
        }
    fun getExprTypeAdjusted(expr: RsExpr): Ty = getExprAdjustments(expr).lastOrNull()?.target ?: getExprType(expr)
}

/**
 * [RsInferenceResult] is an immutable per-function map
 * from expressions to their types.
 */
class RsInferenceResult(
    val exprTypes: Map<RsExpr, Ty>,
    val patTypes: Map<RsPat, Ty>,
    val patFieldTypes: Map<RsPatField, Ty>,
    private val expectedExprTypes: Map<RsExpr, ExpectedType>,
    private val resolvedPaths: Map<RsPathExpr, List<ResolvedPath>>,
    private val resolvedMethods: Map<RsMethodCall, InferredMethodCallInfo>,
    private val resolvedFields: Map<RsFieldLookup, List<RsElement>>,
    private val adjustments: Map<RsElement, List<Adjustment>>,
    private val overloadedOperators: Set<RsElement>,
    val diagnostics: List<RsDiagnostic>
) : RsInferenceData {
    private val timestamp: Long = System.nanoTime()

    override fun getExprAdjustments(expr: RsElement): List<Adjustment> =
        adjustments[expr] ?: emptyList()

    override fun getExprType(expr: RsExpr): Ty =
        exprTypes[expr] ?: TyUnknown

    override fun getPatType(pat: RsPat): Ty =
        patTypes[pat] ?: TyUnknown

    override fun getPatFieldType(patField: RsPatField): Ty =
        patFieldTypes[patField] ?: TyUnknown

    override fun getExpectedExprType(expr: RsExpr): ExpectedType =
        expectedExprTypes[expr] ?: ExpectedType.UNKNOWN

    override fun getResolvedPath(expr: RsPathExpr): List<ResolvedPath> =
        resolvedPaths[expr] ?: emptyList()

    override fun isOverloadedOperator(expr: RsExpr): Boolean = expr in overloadedOperators

    fun getResolvedMethod(call: RsMethodCall): List<MethodResolveVariant> =
        resolvedMethods[call]?.resolveVariants ?: emptyList()

    fun getResolvedMethodType(call: RsMethodCall): TyFunction? =
        resolvedMethods[call]?.type

    fun getResolvedMethodSubst(call: RsMethodCall): Substitution =
        resolvedMethods[call]?.subst ?: emptySubstitution

    fun getResolvedField(call: RsFieldLookup): List<RsElement> =
        resolvedFields[call] ?: emptyList()

    @TestOnly
    fun isExprTypeInferred(expr: RsExpr): Boolean =
        expr in exprTypes

    @TestOnly
    fun getTimestamp(): Long = timestamp

    companion object {
        @JvmStatic
        val EMPTY: RsInferenceResult = RsInferenceResult(
            emptyMap(),
            emptyMap(),
            emptyMap(),
            emptyMap(),
            emptyMap(),
            emptyMap(),
            emptyMap(),
            emptyMap(),
            emptySet(),
            emptyList(),
        )
    }
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
    private val expectedExprTypes: MutableMap<RsExpr, ExpectedType> = hashMapOf()
    private val resolvedPaths: MutableMap<RsPathExpr, List<ResolvedPath>> = hashMapOf()
    private val resolvedMethods: MutableMap<RsMethodCall, InferredMethodCallInfo> = hashMapOf()
    private val resolvedFields: MutableMap<RsFieldLookup, List<RsElement>> = hashMapOf()
    private val pathRefinements: MutableList<Pair<RsPathExpr, TraitRef>> = mutableListOf()
    private val methodRefinements: MutableList<Pair<RsMethodCall, TraitRef>> = mutableListOf()
    private val adjustments: MutableMap<RsElement, MutableList<Adjustment>> = hashMapOf()
    private val overloadedOperators: MutableSet<RsElement> = hashSetOf()
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
                val retTy = normalizeAssociatedTypesIn(element.rawReturnType).value
                val fctx = RsTypeInferenceWalker(this, retTy)
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
            is RsPath -> {
                val declaration = resolvePathRaw(element, lookup).singleOrNull()?.element as? RsGenericDeclaration
                if (declaration != null) {
                    val constParameters = mutableListOf<RsConstParameter>()
                    val constArguments = mutableListOf<RsElement>()
                    for ((param, value) in pathPsiSubst(element, declaration).constSubst) {
                        if (value is RsPsiSubstitution.Value.Present) {
                            constParameters += param
                            constArguments += value.value
                        }
                    }
                    RsTypeInferenceWalker(this, TyUnknown).inferConstArgumentTypes(constParameters, constArguments)
                }
            }
            else -> {
                val (retTy, expr) = when (element) {
                    is RsConstant -> element.typeReference?.rawType to element.expr
                    is RsConstParameter -> element.typeReference?.rawType to element.expr
                    is RsArrayType -> TyInteger.USize.INSTANCE to element.expr
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
                    else -> error(
                        "Type inference is not implemented for PSI element of type " +
                            "`${element.javaClass}` that implement `RsInferenceContextOwner`"
                    )
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
        adjustments.replaceAll { _, it -> it.mapToMutableList { fullyResolve(it) } }

        performPathsRefinement(lookup)

        resolvedPaths.values.asSequence().flatten()
            .forEach { it.subst = it.subst.foldValues(fullTypeWithOriginsResolver) }
        resolvedMethods.replaceAll { _, ty -> fullyResolveWithOrigins(ty) }

        return RsInferenceResult(
            exprTypes,
            patTypes,
            patFieldTypes,
            expectedExprTypes,
            resolvedPaths,
            resolvedMethods,
            resolvedFields,
            adjustments,
            overloadedOperators,
            diagnostics
        )
    }

    private fun fallbackUnresolvedTypeVarsIfPossible() {
        val allTypes = exprTypes.values.asSequence() + patTypes.values.asSequence() +
            patFieldTypes.values.asSequence() + expectedExprTypes.values.asSequence().map { it.ty }
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
            val source = RsCachedImplItem.forImpl(impl).explicitImpl
            val result = ResolvedPath.AssocItem(fn, source)
            result.subst = variant.subst // TODO remap subst
            resolvedPaths[path] = listOf(result)
        }
        for ((call, traitRef) in methodRefinements) {
            val info = resolvedMethods[call] ?: continue
            val variant = info.resolveVariants.firstOrNull() ?: continue
            val impl = lookup.select(resolveTypeVarsIfPossible(traitRef)).ok()?.impl as? RsImplItem ?: continue
            val fn = impl.expandedMembers.functions.find { it.name == variant.name } ?: continue
            val source = RsCachedImplItem.forImpl(impl).explicitImpl
            // TODO remap subst
            resolvedMethods[call] = info.copy(resolveVariants = listOf(variant.copy(element = fn, source = source)))
        }
    }

    override fun getExprAdjustments(expr: RsElement): List<Adjustment> {
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

    override fun getExpectedExprType(expr: RsExpr): ExpectedType {
        return expectedExprTypes[expr] ?: ExpectedType.UNKNOWN
    }

    override fun getResolvedPath(expr: RsPathExpr): List<ResolvedPath> {
        return resolvedPaths[expr] ?: emptyList()
    }

    override fun isOverloadedOperator(expr: RsExpr): Boolean = expr in overloadedOperators

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
        expectedExprTypes[psi] = ExpectedType(ty)
    }

    fun writeExpectedExprTyCoercable(psi: RsExpr) {
        expectedExprTypes.computeIfPresent(psi) { _, v -> v.copy(coercable = true) }
    }

    fun writePath(path: RsPathExpr, resolved: List<ResolvedPath>) {
        resolvedPaths[path] = resolved
    }

    fun writePathSubst(path: RsPathExpr, subst: Substitution) {
        resolvedPaths[path]?.singleOrNull()?.subst = subst
    }

    fun writeResolvedMethod(call: RsMethodCall, resolvedTo: List<MethodResolveVariant>) {
        resolvedMethods[call] = InferredMethodCallInfo(resolvedTo)
    }

    fun writeResolvedMethodSubst(call: RsMethodCall, subst: Substitution, ty: TyFunction) {
        resolvedMethods[call]?.let {
            it.subst = subst
            it.type = ty
        }
    }

    fun writeResolvedField(lookup: RsFieldLookup, resolvedTo: List<RsElement>) {
        resolvedFields[lookup] = resolvedTo
    }

    fun registerPathRefinement(path: RsPathExpr, traitRef: TraitRef) {
        pathRefinements.add(Pair(path, traitRef))
    }

    private fun registerMethodRefinement(path: RsMethodCall, traitRef: TraitRef) {
        methodRefinements.add(Pair(path, traitRef))
    }

    fun addDiagnostic(diagnostic: RsDiagnostic) {
        if (diagnostic.element.containingFile.isPhysical) {
            diagnostics.add(diagnostic)
        }
    }

    fun applyAdjustment(expr: RsElement, adjustment: Adjustment) {
        applyAdjustments(expr, listOf(adjustment))
    }

    fun applyAdjustments(expr: RsElement, adjustment: List<Adjustment>) {
        if (adjustment.isEmpty()) return
        val unwrappedExpr = if (expr is RsExpr) {
            unwrapParenExprs(expr)
        } else {
            expr
        }

        val isAutoborrowMut = adjustment.any { it is Adjustment.BorrowReference && it.mutability == MUTABLE }

        adjustments.getOrPut(unwrappedExpr) { mutableListOf() }.addAll(adjustment)

        if (isAutoborrowMut && unwrappedExpr is RsExpr) {
            convertPlaceDerefsToMutable(unwrappedExpr)
        }
    }

    fun writeOverloadedOperator(expr: RsExpr) {
        overloadedOperators += expr
    }

    fun reportTypeMismatch(element: RsElement, expected: Ty, actual: Ty) {
        addDiagnostic(RsDiagnostic.TypeError(element, expected, actual))
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun canCombineTypes(ty1: Ty, ty2: Ty): Boolean {
        return probe { combineTypes(ty1, ty2).isOk }
    }

    private fun combineTypesIfOk(ty1: Ty, ty2: Ty): Boolean {
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

    fun combineTypes(ty1: Ty, ty2: Ty): RelateResult {
        return combineTypesResolved(shallowResolve(ty1), shallowResolve(ty2))
    }

    private fun combineTypesResolved(ty1: Ty, ty2: Ty): RelateResult {
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

    private fun combineTyVar(ty1: TyInfer.TyVar, ty2: Ty): RelateResult {
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
                    TypeInferenceMarks.CyclicType.hit()
                    varUnificationTable.unifyVarValue(ty1r, TyUnknown)
                } else {
                    varUnificationTable.unifyVarValue(ty1r, ty2)
                }
            }
        }
        return Ok(Unit)
    }

    private fun combineIntOrFloatVar(ty1: TyInfer, ty2: Ty): RelateResult {
        when (ty1) {
            is TyInfer.IntVar -> when (ty2) {
                is TyInfer.IntVar -> intUnificationTable.unifyVarVar(ty1, ty2)
                is TyInteger -> intUnificationTable.unifyVarValue(ty1, ty2)
                else -> return Err(TypeMismatch(ty1, ty2))
            }
            is TyInfer.FloatVar -> when (ty2) {
                is TyInfer.FloatVar -> floatUnificationTable.unifyVarVar(ty1, ty2)
                is TyFloat -> floatUnificationTable.unifyVarValue(ty1, ty2)
                else -> return Err(TypeMismatch(ty1, ty2))
            }
            is TyInfer.TyVar -> error("unreachable")
        }
        return Ok(Unit)
    }

    fun combineTypesNoVars(ty1: Ty, ty2: Ty): RelateResult =
        when {
            ty1 === ty2 -> Ok(Unit)
            ty1 is TyPrimitive && ty2 is TyPrimitive && ty1.javaClass == ty2.javaClass -> Ok(Unit)
            ty1 is TyTypeParameter && ty2 is TyTypeParameter && ty1 == ty2 -> Ok(Unit)
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
                combineBoundElements(ty1.traits.first(), ty2.traits.first()) -> Ok(Unit)
            ty1 is TyAnon && ty2 is TyAnon && ty1.definition != null && ty1.definition == ty2.definition -> Ok(Unit)
            ty1 is TyNever || ty2 is TyNever -> Ok(Unit)
            else -> Err(TypeMismatch(ty1, ty2))
        }

    fun combineConsts(const1: Const, const2: Const): RelateResult {
        return combineConstsResolved(shallowResolve(const1), shallowResolve(const2))
    }

    private fun combineConstsResolved(const1: Const, const2: Const): RelateResult =
        when {
            const1 is CtInferVar -> combineConstVar(const1, const2)
            const2 is CtInferVar -> combineConstVar(const2, const1)
            else -> combineConstsNoVars(const1, const2)
        }

    private fun combineConstVar(const1: CtInferVar, const2: Const): RelateResult {
        if (const2 is CtInferVar) {
            constUnificationTable.unifyVarVar(const1, const2)
        } else {
            val const1r = constUnificationTable.findRoot(const1)
            constUnificationTable.unifyVarValue(const1r, const2)
        }
        return Ok(Unit)
    }

    private fun combineConstsNoVars(const1: Const, const2: Const): RelateResult =
        when {
            const1 === const2 -> Ok(Unit)
            const1 is CtUnknown || const2 is CtUnknown -> Ok(Unit)
            const1 is CtUnevaluated || const2 is CtUnevaluated -> Ok(Unit)
            const1 == const2 -> Ok(Unit)
            else -> Err(ConstMismatch(const1, const2))
        }

    fun combineTypePairs(pairs: List<Pair<Ty, Ty>>): RelateResult = combinePairs(pairs, ::combineTypes)

    fun combineConstPairs(pairs: List<Pair<Const, Const>>): RelateResult = combinePairs(pairs, ::combineConsts)

    private fun <T : Kind> combinePairs(pairs: List<Pair<T, T>>, combine: (T, T) -> RelateResult): RelateResult {
        var canUnify: RelateResult = Ok(Unit)
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

    fun tryCoerce(inferred: Ty, expected: Ty): CoerceResult {
        if (inferred === expected) {
            return Ok(CoerceOk())
        }
        if (inferred == TyNever) {
            return Ok(CoerceOk(adjustments = listOf(Adjustment.NeverToAny(expected))))
        }
        if (inferred is TyInfer.TyVar) {
            return combineTypes(inferred, expected).into()
        }
        val unsize = commitIfNotNull { coerceUnsized(inferred, expected) }
        if (unsize != null) {
            return Ok(unsize)
        }
        return when {
            // Coerce reference to pointer
            inferred is TyReference && expected is TyPointer &&
                coerceMutability(inferred.mutability, expected.mutability) -> {
                combineTypes(inferred.referenced, expected.referenced).map {
                    CoerceOk(
                        adjustments = listOf(
                            Adjustment.Deref(inferred.referenced, overloaded = null),
                            Adjustment.BorrowPointer(expected)
                        )
                    )
                }
            }
            // Coerce mutable pointer to const pointer
            inferred is TyPointer && inferred.mutability.isMut
                && expected is TyPointer && !expected.mutability.isMut -> {
                combineTypes(inferred.referenced, expected.referenced).map {
                    CoerceOk(adjustments = listOf(Adjustment.MutToConstPointer(expected)))
                }
            }
            // Coerce references
            inferred is TyReference && expected is TyReference &&
                coerceMutability(inferred.mutability, expected.mutability) -> {
                coerceReference(inferred, expected)
            }
            else -> combineTypes(inferred, expected).into()
        }
    }

    // &[T; n] or &mut [T; n] -> &[T]
    // or &mut [T; n] -> &mut [T]
    // or &Concrete -> &Trait, etc.
    // Mirrors rustc's `coerce_unsized`
    // https://github.com/rust-lang/rust/blob/97d48bec2d/compiler/rustc_typeck/src/check/coercion.rs#L486
    private fun coerceUnsized(source: Ty, target: Ty): CoerceOk? {
        if (source is TyInfer.TyVar || target is TyInfer.TyVar) return null

        // Optimization: return early if unsizing is not needed to match types
        if (target.isScalar || canCombineTypes(source, target)) return null

        val unsizeTrait = items.Unsize ?: return null
        val coerceUnsizedTrait = items.CoerceUnsized ?: return null
        val traits = listOf(unsizeTrait, coerceUnsizedTrait)

        val reborrow = when {
            source is TyReference && target is TyReference -> {
                if (!coerceMutability(source.mutability, target.mutability)) return null
                Pair(
                    Adjustment.Deref(source.referenced, overloaded = null),
                    Adjustment.BorrowReference(TyReference(source.referenced, target.mutability))
                )
            }
            source is TyReference && target is TyPointer -> {
                if (!coerceMutability(source.mutability, target.mutability)) return null
                Pair(
                    Adjustment.Deref(source.referenced, overloaded = null),
                    Adjustment.BorrowPointer(TyPointer(source.referenced, target.mutability))
                )
            }
            else -> null
        }
        val coerceSource = reborrow?.second?.target ?: source

        val adjustments = listOfNotNull(
            reborrow?.first,
            reborrow?.second,
            Adjustment.Unsize(target)
        )
        val resultObligations = mutableListOf<Obligation>()

        val queue = dequeOf(Obligation(Predicate.Trait(TraitRef(coerceSource, coerceUnsizedTrait.withSubst(target)))))

        while (!queue.isEmpty()) {
            val obligation = queue.removeFirst()
            val predicate = resolveTypeVarsIfPossible(obligation.predicate)
            if (predicate is Predicate.Trait && predicate.trait.trait.element in traits) {
                when (val selection = lookup.select(predicate.trait, obligation.recursionDepth)) {
                    SelectionResult.Err -> return null
                    is SelectionResult.Ok -> queue += selection.result.nestedObligations
                    SelectionResult.Ambiguous -> if (predicate.trait.trait.element == unsizeTrait) {
                        val selfTy = predicate.trait.selfTy
                        val unsizeTy = predicate.trait.trait.singleParamValue
                        if (selfTy is TyInfer.TyVar && unsizeTy is TyTraitObject && typeVarIsSized(selfTy)) {
                            resultObligations += obligation
                        } else {
                            return null
                        }
                    } else {
                        return null
                    }
                }
            } else {
                resultObligations += obligation
                continue
            }
        }

        return CoerceOk(adjustments, resultObligations)
    }

    private fun typeVarIsSized(ty: TyInfer.TyVar): Boolean {
        return fulfill.pendingObligations
            .mapNotNull { it.obligation.predicate as? Predicate.Trait }
            .any { it.trait.selfTy == ty && it.trait.trait.element == items.Sized }
    }

    private fun coerceMutability(from: Mutability, to: Mutability): Boolean =
        from == to || from.isMut && !to.isMut

    /**
     * Reborrows `&mut A` to `&mut B` and `&(mut) A` to `&B`.
     * To match `A` with `B`, autoderef will be performed
     */
    private fun coerceReference(inferred: TyReference, expected: TyReference): CoerceResult {
        val autoderef = lookup.coercionSequence(inferred)
        for (derefTy in autoderef.drop(1)) {
            // TODO proper handling of lifetimes
            val derefTyRef = TyReference(derefTy, expected.mutability, expected.region)
            if (combineTypesIfOk(derefTyRef, expected)) {
                // Deref `&a` to `a` and then reborrow as `&a`. No-op. See rustc's `coerce_borrowed_pointer`
                val isTrivialReborrow = autoderef.stepCount() == 1
                    && inferred.mutability == expected.mutability
                    && !expected.mutability.isMut

                if (!isTrivialReborrow) {
                    val adjustments = autoderef.steps().toAdjustments(items) +
                        listOf(Adjustment.BorrowReference(derefTyRef))
                    return Ok(CoerceOk(adjustments))
                }
                return Ok(CoerceOk())
            }
        }

        return Err(TypeMismatch(inferred, expected))
    }

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
        val resolved = value.foldWith(fullTypeResolver)
        testAssert { !resolved.hasTyPlaceholder }
        return resolved
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

    fun <T : TypeFoldable<T>> fullyNormalizeAssociatedTypesIn(ty: T): T {
        val (normalizedTy, obligations) = normalizeAssociatedTypesIn(ty)
        obligations.forEach(fulfill::registerPredicateObligation)
        fulfill.selectWherePossible()
        return fullyResolve(normalizedTy)
    }

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
                TypeInferenceMarks.RecursiveProjectionNormalization.hit()
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
            is TraitImplSource.Derived, is TraitImplSource.Builtin -> {
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
            impl.typeReference?.rawType?.substitute(subst)?.let { combineTypes(selfTy, it) }
            ff.selectUntilError()
        }
    }

    fun instantiateMethodOwnerSubstitution(
        callee: AssocItemScopeEntryBase<*>,
        methodCall: RsMethodCall? = null
    ): Substitution = instantiateMethodOwnerSubstitution(callee.source, callee.selfTy, callee.element, methodCall)

    fun instantiateMethodOwnerSubstitution(
        callee: MethodPick,
        methodCall: RsMethodCall? = null
    ): Substitution = instantiateMethodOwnerSubstitution(callee.source, callee.formalSelfTy, callee.element, methodCall)

    private fun instantiateMethodOwnerSubstitution(
        source: TraitImplSource,
        selfTy: Ty,
        element: RsAbstractable,
        methodCall: RsMethodCall? = null
    ): Substitution = when (source) {
        is TraitImplSource.ExplicitImpl -> {
            val impl = source.value
            val typeParameters = instantiateBounds(impl)
            source.type?.substitute(typeParameters)?.let { combineTypes(selfTy, it) }
            if (element.owner is RsAbstractableOwner.Trait) {
                source.implementedTrait?.substitute(typeParameters)?.subst ?: emptySubstitution
            } else {
                typeParameters
            }
        }
        is TraitImplSource.TraitBound -> lookup.getEnvBoundTransitivelyFor(selfTy)
            .find { it.element == source.value }?.subst ?: emptySubstitution

        is TraitImplSource.ProjectionBound -> {
            val ty = selfTy as TyProjection
            val subst = ty.trait.subst + mapOf(TyTypeParameter.self() to ty.type).toTypeSubst()
            val bound = ty.trait.element.bounds
                .find { it.trait.element == source.value && probe { combineTypes(it.selfTy.substitute(subst), ty) }.isOk }
            bound?.trait?.subst?.substituteInValues(subst) ?: emptySubstitution
        }

        is TraitImplSource.Derived -> emptySubstitution

        is TraitImplSource.Object -> when (selfTy) {
            is TyAnon -> selfTy.getTraitBoundsTransitively()
                .find { it.element == source.value }?.subst ?: emptySubstitution
            is TyTraitObject -> selfTy.getTraitBoundsTransitively()
                .find { it.element == source.value }?.subst ?: emptySubstitution
            else -> emptySubstitution
        }
        is TraitImplSource.Collapsed, is TraitImplSource.Builtin -> {
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
            val traitRef = TraitRef(selfTy, boundTrait)
            fulfill.registerPredicateObligation(Obligation(Predicate.Trait(traitRef)))
            if (methodCall != null) {
                registerMethodRefinement(methodCall, traitRef)
            }
            typeParameters
        }
        is TraitImplSource.Trait -> {
            // It's possible in type-qualified UFCS paths (like `<A as Trait>::Type`) during completion
            emptySubstitution
        }
    }

    /**
     * Convert auto-derefs, indices, etc of an expression from `Deref` and `Index`
     * into `DerefMut` and `IndexMut` respectively.
     */
    fun convertPlaceDerefsToMutable(receiver: RsExpr) {
        val exprs = mutableListOf(receiver)

        while (true) {
            exprs += when (val expr = exprs.last()) {
                is RsIndexExpr -> expr.containerExpr
                is RsUnaryExpr -> if (expr.isDereference) expr.expr else null
                is RsDotExpr -> if (expr.fieldLookup != null) expr.expr else null
                is RsParenExpr -> expr.expr
                else -> null
            } ?: break
        }

        for (expr in exprs.asReversed()) {
            val exprAdjustments = adjustments[expr]
            exprAdjustments?.forEachIndexed { i, adjustment ->
                if (adjustment is Adjustment.Deref && adjustment.overloaded == IMMUTABLE) {
                    exprAdjustments[i] = Adjustment.Deref(adjustment.target, MUTABLE)
                }
            }

            val base = unwrapParenExprs(
                when (expr) {
                    is RsIndexExpr -> expr.containerExpr
                    is RsUnaryExpr -> if (expr.isDereference) expr.expr else null
                    else -> null
                } ?: continue
            )

            val baseAdjustments = adjustments[base] ?: continue

            baseAdjustments.forEachIndexed { i, adjustment ->
                if (adjustment is Adjustment.BorrowReference && adjustment.mutability == IMMUTABLE) {
                    baseAdjustments[i] = Adjustment.BorrowReference(adjustment.target.copy(mutability = MUTABLE))
                }
            }

            val lastAdjustment = baseAdjustments.lastOrNull()
            if (lastAdjustment is Adjustment.Unsize
                && baseAdjustments.getOrNull(baseAdjustments.lastIndex - 1) is Adjustment.BorrowReference
                && lastAdjustment.target is TyReference) {
                baseAdjustments[baseAdjustments.lastIndex] =
                    Adjustment.Unsize((lastAdjustment.target as TyReference).copy(mutability = MUTABLE))
            }
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
            val selfTy = it.typeReference?.rawType ?: return@flatMap emptySequence<PsiPredicate>()
            it.typeParamBounds?.polyboundList.toPredicates(selfTy)
        }
    val bounds = typeParameters.asSequence().flatMap {
        val selfTy = TyTypeParameter.named(it)
        it.typeParamBounds?.polyboundList.toPredicates(selfTy)
    }
    val assocTypes = if (this is RsTraitItem) {
        expandedMembers.types.map { TyProjection.valueOf(it) }
    } else {
        emptyList()
    }
    val assocTypeBounds = assocTypes.asSequence().flatMap { it.target.typeParamBounds?.polyboundList.toPredicates(it) }
    val explicitPredicates = (bounds + whereBounds + assocTypeBounds).toList()
    val sized = knownItems.Sized
    val implicitPredicates = if (sized != null) {
        val sizedBounds = explicitPredicates.mapNotNullToSet {
            when (it) {
                is PsiPredicate.Unbound -> it.selfTy
                is PsiPredicate.Bound -> if (it.predicate is Predicate.Trait && it.predicate.trait.trait.element == sized) {
                    it.predicate.trait.selfTy
                } else {
                    null
                }
            }
        }
        (generics.asSequence() + assocTypes.asSequence())
            .filter { it !in sizedBounds }
            .map { Predicate.Trait(TraitRef(it, sized.withSubst())) }
    } else {
        emptySequence()
    }
    return explicitPredicates.mapNotNull { (it as? PsiPredicate.Bound)?.predicate } + implicitPredicates
}

private fun List<RsPolybound>?.toPredicates(selfTy: Ty): Sequence<PsiPredicate> = orEmpty().asSequence()
    .flatMap { bound ->
        if (bound.hasQ) { // ?Sized
            return@flatMap sequenceOf(PsiPredicate.Unbound(selfTy))
        }
        val traitRef = bound.bound.traitRef ?: return@flatMap emptySequence<PsiPredicate>()
        val boundTrait = traitRef.resolveToBoundTrait() ?: return@flatMap emptySequence<PsiPredicate>()

        val assocTypeBounds = traitRef.path.assocTypeBindings.asSequence()
            .flatMap nestedFlatMap@{
                val assoc = it.path.reference?.resolve() as? RsTypeAlias
                    ?: return@nestedFlatMap emptySequence<PsiPredicate>()
                val projectionTy = TyProjection.valueOf(selfTy, assoc)
                val typeRef = it.typeReference
                if (typeRef != null) {
                    // T: Iterator<Item = Foo>
                    //             ~~~~~~~~~~ expands to predicate `T::Item = Foo`
                    sequenceOf(PsiPredicate.Bound(Predicate.Equate(projectionTy, typeRef.rawType)))
                } else {
                    // T: Iterator<Item: Debug>
                    //             ~~~~~~~~~~~ equivalent to `T::Item: Debug`
                    it.polyboundList.toPredicates(projectionTy)
                }
            }
        val constness = if (bound.hasConst) {
            BoundConstness.ConstIfConst
        } else {
            BoundConstness.NotConst
        }
        sequenceOf(PsiPredicate.Bound(Predicate.Trait(TraitRef(selfTy, boundTrait), constness))) + assocTypeBounds
    }

private sealed class PsiPredicate {
    data class Bound(val predicate: Predicate) : PsiPredicate()
    data class Unbound(val selfTy: Ty) : PsiPredicate()
}


data class TyWithObligations<out T>(
    val value: T,
    val obligations: List<Obligation> = emptyList()
)

fun <T> TyWithObligations<T>.withObligations(addObligations: List<Obligation>) =
    TyWithObligations(value, obligations + addObligations)

sealed class ResolvedPath {
    abstract val element: RsElement
    var subst: Substitution = emptySubstitution

    class Item(override val element: RsElement, val isVisible: Boolean) : ResolvedPath()

    class AssocItem(
        override val element: RsAbstractable,
        val source: TraitImplSource
    ) : ResolvedPath()

    companion object {
        fun from(entry: ScopeEntry, context: RsElement): ResolvedPath? {
            return if (entry is AssocItemScopeEntry) {
                AssocItem(entry.element, entry.source)
            } else {
                entry.element?.let {
                    val isVisible = entry.isVisibleFrom(context.containingMod)
                    Item(it, isVisible)
                }
            }
        }

        fun from(entry: AssocItemScopeEntry): ResolvedPath =
            AssocItem(entry.element, entry.source)
    }
}

data class InferredMethodCallInfo(
    val resolveVariants: List<MethodResolveVariant>,
    var subst: Substitution = emptySubstitution,
    var type: TyFunction? = null,
) : TypeFoldable<InferredMethodCallInfo> {
    override fun superFoldWith(folder: TypeFolder): InferredMethodCallInfo = InferredMethodCallInfo(
        resolveVariants,
        subst.foldValues(folder),
        type?.foldWith(folder) as? TyFunction
    )

    override fun superVisitWith(visitor: TypeVisitor): Boolean {
        val type = type
        return subst.visitValues(visitor) || type != null && type.visitWith(visitor)
    }

}

data class MethodPick(
    val element: RsFunction,
    /** A type that should be unified with `Self` type of the `impl` */
    val formalSelfTy: Ty,
    /** An actual type of `self` inside the method. Can differ from [formalSelfTy] because of `&mut self`, etc */
    val methodSelfTy: Ty,
    val derefCount: Int,
    val source: TraitImplSource,
    val derefSteps: List<Autoderef.AutoderefStep>,
    val autorefOrPtrAdjustment: AutorefOrPtrAdjustment?,
    val isValid: Boolean
) {
    fun toMethodResolveVariant(): MethodResolveVariant =
        MethodResolveVariant(element.name!!, element, formalSelfTy, derefCount, source)

    sealed class AutorefOrPtrAdjustment {
        data class Autoref(val mutability: Mutability, val unsize: Boolean) : AutorefOrPtrAdjustment()
        object ToConstPtr : AutorefOrPtrAdjustment()
    }

    companion object {
        fun from(
            m: MethodResolveVariant,
            methodSelfTy: Ty,
            derefSteps: List<Autoderef.AutoderefStep>,
            autorefOrPtrAdjustment: AutorefOrPtrAdjustment?
        ) = MethodPick(m.element, m.selfTy, methodSelfTy, m.derefCount, m.source, derefSteps, autorefOrPtrAdjustment, true)

        fun from(m: MethodResolveVariant) =
            MethodPick(m.element, m.selfTy, TyUnknown, m.derefCount, m.source, emptyList(), null, false)
    }
}

typealias RelateResult = RsResult<Unit, TypeError>

private inline fun RelateResult.and(rhs: () -> RelateResult): RelateResult = if (isOk) rhs() else this

sealed class TypeError {
    class TypeMismatch(val ty1: Ty, val ty2: Ty) : TypeError()
    class ConstMismatch(val const1: Const, val const2: Const) : TypeError()
}

typealias CoerceResult = RsResult<CoerceOk, TypeError>
data class CoerceOk(
    val adjustments: List<Adjustment> = emptyList(),
    val obligations: List<Obligation> = emptyList()
)

fun RelateResult.into(): CoerceResult = map { CoerceOk() }

data class ExpectedType(val ty: Ty, val coercable: Boolean = false) : TypeFoldable<ExpectedType> {
    override fun superFoldWith(folder: TypeFolder): ExpectedType = copy(ty = ty.foldWith(folder))

    override fun superVisitWith(visitor: TypeVisitor): Boolean = ty.visitWith(visitor)

    companion object {
        val UNKNOWN: ExpectedType = ExpectedType(TyUnknown)
    }
}

object TypeInferenceMarks {
    object CyclicType : Testmark()
    object RecursiveProjectionNormalization : Testmark()
    object QuestionOperator : Testmark()
    object MethodPickTraitScope : Testmark()
    object MethodPickTraitsOutOfScope : Testmark()
    object MethodPickCheckBounds : Testmark()
    object MethodPickDerefOrder : Testmark()
    object MethodPickCollapseTraits : Testmark()
    object WinnowSpecialization : Testmark()
    object WinnowParamCandidateWins : Testmark()
    object WinnowParamCandidateLoses : Testmark()
    object WinnowObjectOrProjectionCandidateWins : Testmark()
    object TraitSelectionOverflow : Testmark()
    object UnsizeToTraitObject : Testmark()
    object UnsizeArrayToSlice : Testmark()
    object UnsizeStruct : Testmark()
    object UnsizeTuple : Testmark()
}
