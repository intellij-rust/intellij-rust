/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveResult
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.util.SmartList
import org.jetbrains.annotations.VisibleForTesting
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.*
import org.rust.lang.core.stubs.RsPathStub
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.RsPsiSubstitution
import org.rust.lang.core.types.RsPsiSubstitution.*
import org.rust.lang.core.types.infer.ResolvedPath
import org.rust.lang.core.types.infer.TyLowering
import org.rust.lang.core.types.infer.hasTyInfer
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.inference
import org.rust.lang.core.types.rawType
import org.rust.lang.core.types.ty.TyProjection
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.doc.psi.RsDocPathLinkParent
import org.rust.lang.utils.evaluation.PathExprResolver
import org.rust.openapiext.forEachChild
import org.rust.openapiext.testAssert
import org.rust.stdext.buildMap
import org.rust.stdext.intersects

class RsPathReferenceImpl(
    element: RsPath
) : RsReferenceBase<RsPath>(element),
    RsPathReference {

    override fun isReferenceTo(target: PsiElement): Boolean {
        if (target is RsFieldDecl) return false

        val path = this.element
        val pathParent = path.parent

        if (target is RsMod) {
            val canReferToMod = pathParent is RsPath || pathParent is RsUseSpeck || pathParent is RsVisRestriction
                || pathParent is RsDocPathLinkParent || pathParent is RsPathCodeFragment
            return if (canReferToMod) {
                isReferenceToNonAssocItem(target)
            } else {
                false
            }
        }

        if (target is RsNamedElement && !path.allowedNamespaces(parent = pathParent).intersects(target.namespaces)) {
            return false
        }

        val targetAbstractableOwner = (target as? RsAbstractable)?.owner

        // If `path.parent` is expression, then `path.reference.resolve()` will invoke type inference for the
        // function containing `path`, which can be very heavy. Trying to avoid it
        return if (pathParent is RsPathExpr) {
            if (targetAbstractableOwner != null && targetAbstractableOwner.isImplOrTrait) {
                if (target is RsTypeAlias) return false
                if (path.path == null && path.typeQual == null) return false

                val resolvedRaw = resolvePathRaw(path)
                val mgr = target.manager

                when (targetAbstractableOwner) {
                    RsAbstractableOwner.Free, RsAbstractableOwner.Foreign ->
                        resolvedRaw.any { mgr.areElementsEquivalent(it.element, target) }
                    is RsAbstractableOwner.Impl -> if (targetAbstractableOwner.isInherent) {
                        resolvedRaw.any { mgr.areElementsEquivalent(it.element, target) }
                    } else {
                        if (resolvedRaw.size == 1 && mgr.areElementsEquivalent(resolvedRaw.single().element, target)) {
                            return true
                        }
                        val superItem = target.superItem ?: return false
                        val canBeReferenceTo = resolvedRaw.any {
                            mgr.areElementsEquivalent(it.element, target) ||
                                mgr.areElementsEquivalent(it.element, superItem)
                        }
                        if (canBeReferenceTo) {
                            isReferenceToAssocItem(target)
                        } else {
                            false
                        }
                    }
                    is RsAbstractableOwner.Trait -> {
                        val canBeReferenceTo = resolvedRaw.any { mgr.areElementsEquivalent(it.element, target) }
                        if (canBeReferenceTo) {
                            isReferenceToAssocItem(target)
                        } else {
                            false
                        }
                    }
                }
            } else {
                isReferenceToNonAssocItem(target)
            }
        } else {
            if (targetAbstractableOwner != null && targetAbstractableOwner.isImplOrTrait) {
                if (target is RsTypeAlias && pathParent is RsAssocTypeBinding) {
                    return isReferenceToAssocItem(target)
                }

                if (pathParent is RsUseSpeck || path.path == null && path.typeQual == null) {
                    return false
                }

                isReferenceToAssocItem(target)
            } else {
                isReferenceToNonAssocItem(target)
            }
        }
    }

    private fun isReferenceToAssocItem(target: PsiElement): Boolean {
        val resolved = rawMultiResolve()
        return resolved.any { target.manager.areElementsEquivalent(it.element, target) }
    }

    private fun isReferenceToNonAssocItem(target: PsiElement): Boolean {
        val canBeReferenceTo = if (target is RsEnumVariant) {
            // `<Foo as Bar>::Value` can't refer to a enum variant
            element.typeQual?.traitRef == null
        } else {
            !element.isAssociatedItemOrEnumVariantPath
        }
        return if (canBeReferenceTo) {
            val mgr = target.manager
            resolvePathRaw(element, resolveAssocItems = false).any { mgr.areElementsEquivalent(it.element, target) }
        } else {
            false
        }
    }

    private val RsPath.isAssociatedItemOrEnumVariantPath: Boolean
        get() {
            if (typeQual != null) return true

            val qualifier = path ?: return false
            val resolvedQualifier = qualifier.reference?.resolve() ?: return false
            return resolvedQualifier !is RsMod
        }

    override fun advancedResolve(): BoundElement<RsElement>? {
        val resultFromTypeInference = rawMultiResolveUsingInferenceCache()
        if (resultFromTypeInference != null) {
            val single = resultFromTypeInference.singleOrNull() ?: return null
            return BoundElement(single.element, single.resolvedSubst)
        }

        val resolvedNestedPaths = resolveNeighborPaths(element)
        val resolved = resolvedNestedPaths[element]?.singleOrNull() ?: return null
        return TyLowering.lowerPathGenerics(
            element,
            resolved.element,
            resolved.resolvedSubst,
            PathExprResolver.default,
            resolvedNestedPaths
        )
    }

    override fun resolve(): RsElement? = rawMultiResolve().singleOrNull()?.element

    override fun multiResolve(incompleteCode: Boolean): Array<out ResolveResult> =
        rawMultiResolve().toTypedArray()

    override fun multiResolve(): List<RsElement> =
        rawMultiResolve().map { it.element }

    override fun multiResolveIfVisible(): List<RsElement> =
        rawMultiResolve().mapNotNull {
            if (!it.isVisible) return@mapNotNull null
            it.element
        }

    override fun rawMultiResolve(): List<RsPathResolveResult<RsElement>> =
        rawMultiResolveUsingInferenceCache() ?: rawCachedMultiResolve()

    private fun rawMultiResolveUsingInferenceCache(): List<RsPathResolveResult<RsElement>>? {
        val path = element.parent as? RsPathExpr ?: return null
        return path.inference?.getResolvedPath(path)?.map { result ->
            val isVisible = (result as? ResolvedPath.Item)?.isVisible ?: true
            RsPathResolveResult(result.element, result.subst, isVisible)
        }
    }

    private fun rawCachedMultiResolve(): List<RsPathResolveResult<RsElement>> {
        // Optimization: resolve and cache all nested paths at once, so `Foo<Foo<Foo<Foo>>>` resolution
        // costs `O(1)` instead of `O(4)`
        val mapOrList = resolveNeighborPathsInternal(element)

        @Suppress("UNCHECKED_CAST")
        val rawResult = if (mapOrList is Map<*, *>) {
            mapOrList[element]
        } else {
            mapOrList
        } as List<RsPathResolveResult<RsElement>>?

        return rawResult.orEmpty()
    }

    override fun bindToElement(target: PsiElement): PsiElement {
        if (target is RsMod) {
            bindToMod(target)?.let { return it }
        }

        return super.bindToElement(target)
    }

    private fun bindToMod(target: RsMod): PsiElement? {
        if (!element.isAtLeastEdition2018) return null
        var targetPath = target.qualifiedNameRelativeTo(element.containingMod) ?: return null

        // consider old target (`element.reference.resolve()`) was `bar1::bar2::bar3::bar4::foo`
        // and old path (`element`) was `bar1::bar3::bar4::foo` (`bar1` reexports everything from `bar2`)
        // and new target is `bar1::bar2::bar3::baz::foo`
        // then we want to reuse `bar1::bar3` part of old path
        // so that new path will be `bar1::bar3::baz::foo` and not `bar1::bar2::bar3::baz::foo`
        for (pathPrefix in generateSequence(element) { it.path }) {
            val mod = pathPrefix.reference?.resolve() as? RsMod
            if (mod != null && target.superMods.contains(mod)) {
                val modFullPath = mod.qualifiedNameRelativeTo(element.containingMod)
                val modShortPath = pathPrefix.text
                if (modFullPath != null && targetPath.startsWith(modFullPath)) {
                    targetPath = targetPath.replaceFirst(modFullPath, modShortPath)
                }
                break
            }
        }

        val elementNew = RsPsiFactory(element.project).tryCreatePath(targetPath) ?: return null
        return element.replace(elementNew)
    }

    private object Resolver : (RsElement) -> Any {
        /**
         * Returns `List<RsPathResolveResult<RsElement>>` if [root] is a single path or
         * `Map<RsPath, List<RsPathResolveResult<RsElement>>>` if [root] contains nested paths.
         */
        override fun invoke(root: RsElement): Any {
            testAssert { root.parent !is RsPathExpr } // Goes through type inference cache
            val allPaths = collectNestedPathsFromRoot(root)
            if (allPaths.isEmpty()) return emptyList<RsPathResolveResult<RsElement>>()
            val ctx = PathResolutionContext(root, isCompletion = false, processAssocItems = true, null)
            if (allPaths.size == 1) {
                val singlePath = allPaths.single()
                return resolvePath(ctx, singlePath, ctx.classifyPath(singlePath))
                    .onEach { check(!it.resolvedSubst.hasTyInfer) }
            }
            val classifiedPaths = allPaths.map { it to ctx.classifyPath(it) }
            val (unqualified, others) = classifiedPaths.partition { it.second is RsPathResolveKind.UnqualifiedPath }
            val kindToPathList = unqualified
                .groupBy { it.second }
                .mapValues { (_, v) -> v.map { it.first } }
            val resolved = hashMapOf<RsPath, List<RsPathResolveResult<RsElement>>>()
            for ((kind, paths) in kindToPathList) {
                resolved += collectMultiplePathResolveVariants(ctx, paths) {
                    processPathResolveVariants(ctx, kind, it)
                }.mapValues { (path, result) ->
                    filterResolveResults(path, result)
                }
            }
            others.associateTo(resolved) { (path, kind) ->
                path to resolvePath(ctx, path, kind)
            }
            resolved.values.forEach { l -> l.forEach { r -> check(!r.resolvedSubst.hasTyInfer) } }
            return resolved
        }
    }

    companion object {
        /**
         * Returns a PSI element considered a "caching root" for the [path]. Usually it is a topmost [RsPath] or
         * [RsTypeReference], e.g. in `Foo<Bar<Baz>>` `Foo` is a caching root for `Bar` and `Baz` paths, so
         * [getRootCachingElement] returns `Foo` for `Baz`.
         */
        @VisibleForTesting
        fun getRootCachingElement(path: RsPath): RsElement? {
            // Optimization: traversing a stub is much faster than PSI traversing
            val stub = path.greenStub
            return if (stub != null) {
                getRootCachingElementStub(stub)?.psi as RsElement?
            } else {
                getRootCachingElementPsi(path)
            }
        }

        private fun getRootCachingElementPsi(path: RsPath): RsElement? {
            var root: RsElement? = null
            var element: PsiElement = path
            var elementType: IElementType = PATH
            var prevElementType: IElementType? = null
            while (true) {
                if (!isStepToParentAllowed(prevElementType, elementType)) break

                val parent = element.parent
                val parentType = parent.elementType
                if (parentType == PATH_EXPR) break

                if (elementType in ALLOWED_CACHING_ROOTS) {
                    root = element as RsElement
                }
                prevElementType = elementType
                element = parent
                elementType = parentType
            }
            return root
        }

        private fun getRootCachingElementStub(path: RsPathStub): StubElement<*>? {
            var root: StubElement<*>? = null
            var element: StubElement<*> = path
            var elementType: IElementType = PATH
            var prevElementType: IElementType? = null
            while (true) {
                if (!isStepToParentAllowed(prevElementType, elementType)) break

                val parent = element.parentStub
                val parentType = parent.stubType
                if (parentType == PATH_EXPR) break

                if (elementType in ALLOWED_CACHING_ROOTS) {
                    root = element
                }
                prevElementType = elementType
                element = parent
                elementType = parentType
            }
            return root
        }

        private fun isStepToParentAllowed(child: IElementType?, parent: IElementType): Boolean {
            val set = if (child == PATH) {
                ALLOWED_PARENT_FOR_PATH
            } else {
                ALLOWED_PARENT_FOR_OTHERS
            }
            return parent in set
        }

        private val ALLOWED_CACHING_ROOTS = TokenSet.orSet(RS_TYPES, tokenSetOf(PATH, TYPE_ARGUMENT_LIST))
        private val ALLOWED_PARENT_FOR_PATH = TokenSet.orSet(RS_TYPES, tokenSetOf(TYPE_ARGUMENT_LIST, TRAIT_REF))
        private val ALLOWED_PARENT_FOR_OTHERS = TokenSet.orSet(
            RS_TYPES,
            tokenSetOf(
                TYPE_ARGUMENT_LIST, PATH, VALUE_PARAMETER, VALUE_PARAMETER_LIST, RET_TYPE, TRAIT_REF, BOUND, POLYBOUND,
                ASSOC_TYPE_BINDING
            )
        )

        /**
         * Returns all nested [RsPath]s for which [getRootCachingElement] returns [root]
         */
        @VisibleForTesting
        fun collectNestedPathsFromRoot(root: RsElement): List<RsPath> {
            val stub = (root as? StubBasedPsiElementBase<*>)?.greenStub
            return if (stub != null) {
                collectNestedPathsFromRootStub(stub)
            } else {
                collectNestedPathsFromRootPsi(root)
            }
        }

        private fun collectNestedPathsFromRootPsi(root: RsElement): List<RsPath> {
            testAssert { root.elementType in ALLOWED_CACHING_ROOTS }

            val result = SmartList<RsPath>()
            val queue = mutableListOf(root)

            while (queue.isNotEmpty()) {
                val nextElement = queue.removeLast()
                if (nextElement is RsPath) {
                    testAssert { getRootCachingElementPsi(nextElement) == root }
                    result.add(nextElement)
                }

                val nextElementType = nextElement.elementType
                nextElement.forEachChild { child ->
                    if (child is RsElement && isStepToParentAllowed(child.elementType, nextElementType)) {
                        queue += child
                    }
                }
            }

            return result
        }

        private fun collectNestedPathsFromRootStub(root: StubElement<*>): List<RsPath> {
            testAssert { root.stubType in ALLOWED_CACHING_ROOTS }

            val result = SmartList<RsPath>()
            val queue = mutableListOf(root)

            while (queue.isNotEmpty()) {
                val nextStub = queue.removeLast()
                if (nextStub is RsPathStub) {
                    testAssert { getRootCachingElementStub(nextStub) == root }
                    result.add(nextStub.psi)
                }

                val nextStubType = nextStub.stubType
                for (child in nextStub.childrenStubs) {
                    if (isStepToParentAllowed(child.stubType, nextStubType)) {
                        queue += child
                    }
                }
            }

            return result
        }

        private fun resolveNeighborPathsInternal(path: RsPath): Any? {
            val root = getRootCachingElement(path) ?: return null
            return RsResolveCache.getInstance(path.project)
                .resolveWithCaching(root, ResolveCacheDependency.LOCAL_AND_RUST_STRUCTURE, Resolver)
        }

        @Suppress("UNCHECKED_CAST")
        fun resolveNeighborPaths(path: RsPath): Map<RsPath, List<RsPathResolveResult<RsElement>>> {
            val mapOrList = resolveNeighborPathsInternal(path) ?: return emptyMap()
            return if (mapOrList is Map<*, *>) {
                mapOrList as Map<RsPath, List<RsPathResolveResult<RsElement>>>
            } else {
                mapOf(path to mapOrList as List<RsPathResolveResult<RsElement>>)
            }
        }
    }
}

fun resolvePathRaw(path: RsPath, lookup: ImplLookup? = null, resolveAssocItems: Boolean = true): List<ScopeEntry> {
    return collectResolveVariantsAsScopeEntries(path.referenceName) {
        processPathResolveVariants(lookup, path, isCompletion = false, resolveAssocItems, it)
    }
}

private fun resolvePath(ctx: PathResolutionContext, path: RsPath, kind: RsPathResolveKind): List<RsPathResolveResult<RsElement>> {
    val result = collectPathResolveVariants(ctx, path) {
        processPathResolveVariants(ctx, kind, it)
    }

    return filterResolveResults(path, result)
}

private fun filterResolveResults(
    path: RsPath,
    result: List<RsPathResolveResult<RsElement>>
): List<RsPathResolveResult<RsElement>> {
    // type A = Foo<T>
    //              ~ `T` can be either type or const argument.
    //                    Prefer types if they are
    val pathParent = path.parent
    return if (pathParent is RsTypeReference && pathParent.parent is RsTypeArgumentList) {
        when (result.size) {
            0 -> emptyList()
            1 -> result
            else -> {
                val types = result.filter {
                    Namespace.Types in it.namespaces
                }
                types.ifEmpty { result }
            }
        }
    } else {
        result
    }
}

fun pathPsiSubst(
    path: RsPath,
    resolved: RsGenericDeclaration,
    givenGenericParameters: List<RsGenericParameter>? = null,
): RsPsiSubstitution {
    if (path.hasCself) {
        return RsPsiSubstitution()
    }
    val args = pathTypeParameters(path)

    val lifetimeParameters = mutableListOf<RsLifetimeParameter>()
    val typeOrConstParameters = mutableListOf<RsElement>()

    for (generic in givenGenericParameters ?: resolved.getGenericParameters()) {
        if (generic is RsLifetimeParameter) {
            lifetimeParameters += generic
        } else {
            typeOrConstParameters += generic
        }
    }

    val parent = path.parent

    // Generic arguments are optional in expression context, e.g.
    // `let a = Foo::<u8>::bar::<u16>();` can be written as `let a = Foo::bar();`
    // if it is possible to infer `u8` and `u16` during type inference
    val areOptionalArgs = parent is RsExpr || parent is RsPath && parent.parent is RsExpr

    val regionSubst = associateSubst<RsLifetimeParameter, RsLifetime, Nothing>(
        lifetimeParameters,
        (args as? RsPsiPathParameters.InAngles)?.lifetimeArgs,
        areOptionalArgs
    )

    val typeOrConstArguments = when (args) {
        is RsPsiPathParameters.InAngles -> args.typeOrConstArgs
            .map {
                if (it is RsTypeReference) {
                    TypeValue.InAngles(it)
                } else {
                    it
                }
            }
        is RsPsiPathParameters.FnSugar -> listOf(TypeValue.FnSugar(args.inputArgs))
        null -> null
    }

    val typeOrConstSubst = associateSubst(typeOrConstParameters, typeOrConstArguments, areOptionalArgs) { param ->
        when (param) {
            is RsTypeParameter -> {
                val defaultTy = param.typeReference ?: return@associateSubst null
                val possibleTypeParamOrWherePred =
                    ((((parent as? RsTraitRef)?.parent as? RsBound)?.parent as? RsPolybound)?.parent as? RsTypeParamBounds)?.parent
                val selfTy = when (possibleTypeParamOrWherePred) {
                    is RsWherePred -> possibleTypeParamOrWherePred.typeReference?.rawType ?: TyUnknown
                    is RsTypeParameter -> possibleTypeParamOrWherePred.declaredType
                    else -> null
                }
                TypeDefault(defaultTy, selfTy)
            }

            is RsConstParameter -> param.expr

            else -> null
        }
    }

    val typeSubst = linkedMapOf<RsTypeParameter, Value<TypeValue, TypeDefault>>()
    val constSubst = linkedMapOf<RsConstParameter, Value<RsElement, RsExpr>>()

    for ((k, v) in typeOrConstSubst) {
        when (k) {
            is RsTypeParameter -> {
                val isTypeValue = v is Value.Present && v.value is TypeValue
                    || v is Value.DefaultValue && v.value is TypeDefault
                    || v is Value.RequiredAbsent
                    || v is Value.OptionalAbsent
                if (isTypeValue) {
                    @Suppress("UNCHECKED_CAST")
                    typeSubst[k] = v as Value<TypeValue, TypeDefault>
                }
            }

            is RsConstParameter -> {
                val isConstValue = v is Value.Present && v.value is RsExpr
                    || v is Value.DefaultValue && v.value is RsExpr
                    || v is Value.RequiredAbsent
                    || v is Value.OptionalAbsent
                if (isConstValue) {
                    @Suppress("UNCHECKED_CAST")
                    constSubst[k] = v as Value<RsElement, RsExpr>
                } else if (v is Value.Present && v.value is TypeValue.InAngles && v.value.value is RsPathType) {
                    constSubst[k] = Value.Present(v.value.value)
                }
            }
        }
    }

    val assocTypes: Map<RsTypeAlias, AssocValue> = run {
        if (resolved is RsTraitItem) {
            when (args) {
                // Iterator<Item=T>
                is RsPsiPathParameters.InAngles -> buildMap {
                    args.assoc.forEach { binding ->
                        // We can't just use `binding.reference.resolve()` here because
                        // resolving of an assoc type depends on a parent path resolve,
                        // so we coming back here and entering the infinite recursion
                        resolveAssocTypeBinding(resolved, binding)?.let { assoc ->
                            binding.typeReference?.let { put(assoc, AssocValue.Present(it)) }
                        }

                    }
                }
                // Fn() -> T
                is RsPsiPathParameters.FnSugar -> buildMap {
                    val outputParam = path.knownItems.FnOnce?.findAssociatedType("Output")
                    if (outputParam != null) {
                        val value = if (args.outputArg != null) {
                            AssocValue.Present(args.outputArg)
                        } else {
                            AssocValue.FnSugarImplicitRet
                        }
                        put(outputParam, value)
                    }
                }
                null -> emptyMap()
            }
        } else {
            emptyMap()
        }
    }

    return RsPsiSubstitution(typeSubst, regionSubst, constSubst, assocTypes)
}

private fun <Param: Any, P: Any, D: Any> associateSubst(
    parameters: List<Param>,
    arguments: List<P>?,
    areOptionalArgs: Boolean,
    default: (Param) -> D? = { null }
): Map<Param, Value<P, D>> {
    return parameters.withIndex().associate { (i, param) ->
        val value = if (areOptionalArgs && arguments == null) {
            Value.OptionalAbsent
        } else if (arguments != null && i < arguments.size) {
            Value.Present(arguments[i])
        } else {
            val defaultValue = default(param)
            if (defaultValue != null) {
                Value.DefaultValue(defaultValue)
            } else {
                Value.RequiredAbsent
            }
        }
        param to value
    }
}

private sealed class RsPsiPathParameters {
    /** `Foo<'a, Bar, Baz, 2+2, Item=i32>` */
    class InAngles(
        val lifetimeArgs: List<RsLifetime>,
        /** [RsTypeReference] or [RsExpr] */
        val typeOrConstArgs: List<RsElement>,
        val assoc: List<RsAssocTypeBinding>
    ) : RsPsiPathParameters()

    /** `Fn(i32, i32) -> i32` */
    class FnSugar(
        val inputArgs: List<RsTypeReference?>,
        val outputArg: RsTypeReference?
    ) : RsPsiPathParameters()
}

private fun pathTypeParameters(path: RsPath): RsPsiPathParameters? {
    val inAngles = path.typeArgumentList
    val fnSugar = path.valueParameterList
    return when {
        inAngles != null -> {
            val typeOrConstArgs = mutableListOf<RsElement>()
            val lifetimeArgs = mutableListOf<RsLifetime>()
            val assoc = mutableListOf<RsAssocTypeBinding>()
            for (child in inAngles.stubChildrenOfType<RsElement>()) {
                when (child) {
                    is RsTypeReference, is RsExpr -> typeOrConstArgs.add(child)
                    is RsLifetime -> lifetimeArgs += child
                    is RsAssocTypeBinding -> assoc += child
                }
            }
            RsPsiPathParameters.InAngles(lifetimeArgs, typeOrConstArgs, assoc)
        }
        fnSugar != null -> {
            RsPsiPathParameters.FnSugar(
                fnSugar.valueParameterList.map { it.typeReference },
                path.retType?.typeReference
            )
        }
        else -> null
    }
}

fun resolveAssocTypeBinding(trait: RsTraitItem, binding: RsAssocTypeBinding): RsTypeAlias? =
    collectResolveVariants(binding.path.referenceName) { processAssocTypeVariants(trait, it) }
        .singleOrNull() as? RsTypeAlias?

/** Resolves a reference through type aliases */
fun RsPathReference.deepResolve(): RsElement? =
    advancedDeepResolve()?.element

/** Resolves a reference through type aliases */
fun RsPathReference.advancedDeepResolve(): BoundElement<RsElement>? {
    val boundElement = advancedResolve()?.let { resolved ->
        // Resolve potential `Self` inside `impl`
        if (resolved.element is RsImplItem && element.hasCself) {
            (resolved.element.typeReference?.skipParens() as? RsPathType)?.path?.reference?.advancedResolve() ?: resolved
        } else {
            resolved
        }
    }

    // Resolve potential type aliases
    return if (boundElement != null && boundElement.element is RsTypeAlias) {
        resolveThroughTypeAliases(boundElement)
    } else {
        boundElement
    }
}

private fun resolveThroughTypeAliases(boundElement: BoundElement<RsElement>): BoundElement<RsElement>? {
    var base: BoundElement<RsElement> = boundElement
    val visited = mutableSetOf(boundElement.element)
    while (base.element is RsTypeAlias) {
        val resolved = ((base.element as RsTypeAlias).typeReference?.skipParens() as? RsPathType)
            ?.path?.reference?.advancedResolve()
            ?: break
        if (!visited.add(resolved.element)) return null
        // Stop at `type S<T> = T;`
        if (resolved.element is RsTypeParameter) break
        base = resolved.substitute(base.subst)
    }
    return base
}

/**
 * Consider this code:
 *
 * ```rust
 * trait Trait {
 *     type Type;
 * }
 * struct S;
 * impl Trait for S {
 *     type Type = ();
 * }
 * type A = <S as Trait>::Type;
 * ```
 *
 * Usual `path.reference.resolve()` always resolves `<S as Trait>::Type` path
 * to the associated type in the trait (`trait Trait { type Type; }`). Such behavior is handy
 * for type inference, but unhandy for users (that most likely want to go to declaration in the `impl`)
 * and for other clients of name resolution (like intention actions).
 *
 * Here we're trying to find concrete `impl` for resolved associated type.
 */
private fun RsPathReference.tryAdvancedResolveTypeAliasToImpl(): BoundElement<RsElement>? {
    val resolvedBoundElement = advancedResolve() ?: return null

    // 1. Check that we're resolved to an associated type inside a trait:
    // trait Trait {
    //     type Item;
    // }      //^ resolved here
    val resolved = resolvedBoundElement.element as? RsTypeAlias ?: return null
    if (resolved.owner !is RsAbstractableOwner.Trait) return null

    // 2. Check that we resolve a `Self`-qualified path or explicit type-qualified path:
    //    `Self::Type` or `<Foo as Trait>::Type`
    val typeQual = element.typeQual
    if (element.path?.hasCself != true && (typeQual == null || typeQual.traitRef == null)) return null

    // 3. Try to select a concrete impl for the associated type
    val lookup = ImplLookup.relativeTo(element)
    val projection = TyProjection.valueOf(resolved.withDefaultSubst()).substitute(resolvedBoundElement.subst) as TyProjection
    val selection = lookup.selectStrict(projection.traitRef).ok()
    if (selection?.impl !is RsImplItem) return null
    val element = selection.impl.expandedMembers.types.find { it.name == resolved.name } ?: return null
    val newSubst = lookup.ctx.fullyResolveWithOrigins(selection.subst)
    NameResolutionTestmarks.TypeAliasToImpl.hit()
    return BoundElement(element, newSubst)
}

/** @see tryAdvancedResolveTypeAliasToImpl */
fun RsPathReference.advancedResolveTypeAliasToImpl(): BoundElement<RsElement>? =
    tryAdvancedResolveTypeAliasToImpl() ?: advancedResolve()

/** @see tryAdvancedResolveTypeAliasToImpl */
fun RsPathReference.tryResolveTypeAliasToImpl(): RsElement? =
    tryAdvancedResolveTypeAliasToImpl()?.element

/** @see tryAdvancedResolveTypeAliasToImpl */
fun RsPathReference.resolveTypeAliasToImpl(): RsElement? {
    return tryResolveTypeAliasToImpl() ?: resolve()
}
