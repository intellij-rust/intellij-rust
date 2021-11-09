/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveResult
import com.intellij.util.containers.map2Array
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.*
import org.rust.lang.core.types.*
import org.rust.lang.core.types.RsPsiSubstitution.TypeValue
import org.rust.lang.core.types.RsPsiSubstitution.Value
import org.rust.lang.core.types.infer.ResolvedPath
import org.rust.lang.core.types.infer.foldTyInferWith
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.ty.TyInfer
import org.rust.lang.core.types.ty.TyProjection
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.utils.evaluation.PathExprResolver
import org.rust.stdext.buildMap
import org.rust.stdext.intersects
import org.rust.stdext.mapNotNullToSet

class RsPathReferenceImpl(
    element: RsPath
) : RsReferenceBase<RsPath>(element),
    RsPathReference {

    override fun isReferenceTo(target: PsiElement): Boolean {
        if (target is RsFieldDecl) return false

        val path = this.element
        if (target is RsNamedElement && !path.allowedNamespaces().intersects(target.namespaces)) return false

        if (target is RsAbstractable) {
            val owner = target.owner
            if (owner.isImplOrTrait && (path.parent is RsUseSpeck || path.path == null && path.typeQual == null)) {
                return false
            }

            // If `path.parent` is expression, then `path.reference.resolve()` will invoke type inference for the
            // function containing `path`, which can be very heavy. Trying to avoid it
            if (target !is RsTypeAlias && path.parent is RsPathExpr) {
                val resolvedRaw = resolvePathRaw(path)
                val mgr = target.manager
                when (owner) {
                    RsAbstractableOwner.Free, RsAbstractableOwner.Foreign ->
                        return resolvedRaw.any { mgr.areElementsEquivalent(it.element, target) }
                    is RsAbstractableOwner.Impl -> if (owner.isInherent) {
                        return resolvedRaw.any { mgr.areElementsEquivalent(it.element, target) }
                    } else {
                        if (resolvedRaw.size == 1 && mgr.areElementsEquivalent(resolvedRaw.single().element, target)) return true
                        val superItem = target.superItem ?: return false
                        val canBeReferenceTo = resolvedRaw.any {
                            mgr.areElementsEquivalent(it.element, target) ||
                                mgr.areElementsEquivalent(it.element, superItem)
                        }
                        if (!canBeReferenceTo) return false
                    }
                    is RsAbstractableOwner.Trait -> {
                        val canBeReferenceTo = resolvedRaw.any { mgr.areElementsEquivalent(it.element, target) }
                        if (!canBeReferenceTo) return false
                    }
                }
            }
        }
        val resolved = resolve()
        return target.manager.areElementsEquivalent(resolved, target)
    }

    override fun advancedResolve(): BoundElement<RsElement>? =
        advancedMultiResolve().singleOrNull()?.inner

    override fun multiResolve(incompleteCode: Boolean): Array<out ResolveResult> =
        advancedMultiResolve().map2Array { it.inner }

    override fun multiResolve(): List<RsElement> =
        advancedMultiResolve().map { it.inner.element }

    override fun multiResolveIfVisible(): List<RsElement> =
        advancedMultiResolve().mapNotNull {
            if (!it.isVisible) return@mapNotNull null
            it.inner.element
        }

    private fun advancedMultiResolve(): List<BoundElementWithVisibility<RsElement>> =
        advancedMultiresolveUsingInferenceCache() ?: advancedCachedMultiResolve()

    private fun advancedMultiresolveUsingInferenceCache(): List<BoundElementWithVisibility<RsElement>>? {
        val path = element.parent as? RsPathExpr ?: return null
        return path.inference?.getResolvedPath(path)?.map { result ->
            val element = BoundElement(result.element, result.subst)
            val isVisible = (result as? ResolvedPath.Item)?.isVisible ?: true
            BoundElementWithVisibility(element, isVisible)
        }
    }

    private fun advancedCachedMultiResolve(): List<BoundElementWithVisibility<RsElement>> {
        return RsResolveCache.getInstance(element.project)
            .resolveWithCaching(element, ResolveCacheDependency.LOCAL_AND_RUST_STRUCTURE, Resolver)
            .orEmpty()
            // We can store a fresh `TyInfer.TyVar` to the cache for `_` path parameter (like `Vec<_>`), but
            // TyVar is mutable type, so we must copy it after retrieving from the cache
            .map { boundElementWithVisibility ->
                boundElementWithVisibility.map { boundElement ->
                    boundElement.foldTyInferWith {
                        if (it is TyInfer.TyVar) TyInfer.TyVar(it.origin) else it
                    }
                }
            }
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

    private object Resolver : (RsPath) -> List<BoundElementWithVisibility<RsElement>> {
        override fun invoke(element: RsPath): List<BoundElementWithVisibility<RsElement>> {
            return resolvePath(element)
        }
    }
}

fun resolvePathRaw(path: RsPath, lookup: ImplLookup? = null): List<ScopeEntry> {
    return collectResolveVariantsAsScopeEntries(path.referenceName) {
        processPathResolveVariants(lookup, path, false, it)
    }
}

fun resolvePath(path: RsPath, lookup: ImplLookup? = null): List<BoundElementWithVisibility<RsElement>> {
    val result = collectPathResolveVariants(path) {
        processPathResolveVariants(lookup, path, false, it)
    }.let { rawResult ->
        tryRefineAssocTypePath(path, lookup, rawResult) ?: rawResult
    }

    // type A = Foo<T>
    //              ~ `T` can be either type or const argument.
    //                    Prefer types if they are
    val pathParent = path.parent
    val result2 = if (pathParent is RsTypeReference && pathParent.parent is RsTypeArgumentList) {
        when (result.size) {
            0 -> emptyList()
            1 -> result
            else -> {
                val withoutConstants = result.filter {
                    it.inner.element !is RsConstant && it.inner.element !is RsConstParameter
                }
                withoutConstants.ifEmpty { result }
            }
        }
    } else {
        result
    }

    return when (result2.size) {
        0 -> emptyList()
        1 -> listOf(result2.single().map { instantiatePathGenerics(path, it) })
        else -> result2
    }
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
 * On a lower level (in `processPathResolveVariants`/`resolvePathRaw`) we always resolve
 * `<S as Trait>::Type` to the trait (`trait Trait { type Type; }`). Such behavior is handy
 * for type inference, but unhandy for users (that most likely want to go to declaration in the `impl`)
 * and for other clients of name resolution (like intention actions).
 *
 * Here we're trying to find concrete `impl` for resolved associated type.
 */
private fun tryRefineAssocTypePath(
    path: RsPath,
    lookup: ImplLookup?,
    rawResult: List<BoundElementWithVisibility<RsElement>>
): List<BoundElementWithVisibility<RsElement>>? {
    // 1. Check that we're resolved to an associated type inside a trait:
    // trait Trait {
    //     type Item;
    // }      //^ resolved here
    val resolvedBoundElement = rawResult.singleOrNull() ?: return null
    val resolved = resolvedBoundElement.inner.element as? RsTypeAlias ?: return null
    if (resolved.owner !is RsAbstractableOwner.Trait) return null

    // 2. Check that we resolve a `Self`-qualified path or explicit type-qualified path:
    //    `Self::Type` or `<Foo as Trait>::Type`
    val typeQual = path.typeQual
    if (path.path?.hasCself != true && (typeQual == null || typeQual.traitRef == null)) return null

    // 3. Try to select a concrete impl for the associated type
    @Suppress("NAME_SHADOWING")
    val lookup = lookup ?: ImplLookup.relativeTo(path)
    val selection = lookup.selectStrict(
        (TyProjection.valueOf(resolved).substitute(resolvedBoundElement.inner.subst) as TyProjection).traitRef
    ).ok()
    if (selection?.impl !is RsImplItem) return null
    val element = selection.impl.expandedMembers.types.find { it.name == resolved.name } ?: return null
    val newSubst = lookup.ctx.fullyResolveWithOrigins(selection.subst)
    return listOf(resolvedBoundElement.copy(inner = BoundElement(element, newSubst)))
}

fun <T : RsElement> instantiatePathGenerics(
    path: RsPath,
    resolved: BoundElement<T>,
    resolver: PathExprResolver? = PathExprResolver.default
): BoundElement<T> {
    val (element, subst) = resolved.downcast<RsGenericDeclaration>() ?: return resolved

    val psiSubst = pathPsiSubst(path, element)
    val newSubst = psiSubst.toSubst(resolver)
    return BoundElement(resolved.element, subst + newSubst, psiSubst.assoc.mapValues { it.value.type })
}

fun pathPsiSubst(path: RsPath, resolved: RsGenericDeclaration): RsPsiSubstitution {
    val args = pathTypeParameters(path)

    val parent = path.parent

    // Generic arguments are optional in expression context, e.g.
    // `let a = Foo::<u8>::bar::<u16>();` can be written as `let a = Foo::bar();`
    // if it is possible to infer `u8` and `u16` during type inference
    val areOptionalArgs = parent is RsExpr || parent is RsPath && parent.parent is RsExpr

    val regionParameters = resolved.lifetimeParameters
    val regionArguments = (args as? RsPsiPathParameters.InAngles)?.lifetimeArgs
    val regionSubst = associateSubst(regionParameters, regionArguments, areOptionalArgs)

    val typeArguments = when (args) {
        is RsPsiPathParameters.InAngles -> args.typeOrConstArgs.filterIsInstance<RsTypeReference>().map { TypeValue.Present.InAngles(it) }
        is RsPsiPathParameters.FnSugar -> listOf(TypeValue.Present.FnSugar(args.inputArgs))
        null -> null
    }

    val typeSubst = resolved.typeParameters.withIndex().associate { (i, param) ->
        val value = if (areOptionalArgs && typeArguments == null) {
            // Args are optional and turbofish is not presend. E.g. `Vec::new()`
            // Let the type inference engine infer a type of the type parameter
            TypeValue.OptionalAbsent
        } else if (typeArguments != null && i < typeArguments.size) {
            typeArguments[i]
        } else {
            // Args aren't optional, and some args/turbofish aren't present
            // Use either default argument from a definition `struct S<T=u8>(T);` or falling back to `TyUnknown`
            val defaultTy = param.typeReference
            if (defaultTy != null) {
                val selfTy = if (parent is RsTraitRef && parent.parent is RsBound) {
                    val pred = parent.ancestorStrict<RsWherePred>()
                    if (pred != null) {
                        pred.typeReference?.type
                    } else {
                        parent.ancestorStrict<RsTypeParameter>()?.declaredType
                    } ?: TyUnknown
                } else {
                    null
                }
                TypeValue.DefaultValue(defaultTy, selfTy)
            } else {
                TypeValue.RequiredAbsent
            }
        }
        param to value
    }

    val usedTypeArguments = typeSubst.values.mapNotNullToSet { (it as? TypeValue.Present.InAngles)?.value }

    val constParameters = resolved.constParameters
    val constArguments = (args as? RsPsiPathParameters.InAngles)?.typeOrConstArgs
        ?.let { list -> list.filter { it !is RsTypeReference || it !in usedTypeArguments && it is RsBaseType} }
    val constSubst = associateSubst(constParameters, constArguments, areOptionalArgs)

    val assocTypes = run {
        if (resolved is RsTraitItem) {
            when (args) {
                // Iterator<Item=T>
                is RsPsiPathParameters.InAngles -> buildMap {
                    args.assoc.forEach { binding ->
                        // We can't just use `binding.reference.resolve()` here because
                        // resolving of an assoc type depends on a parent path resolve,
                        // so we coming back here and entering the infinite recursion
                        resolveAssocTypeBinding(resolved, binding)?.let { assoc ->
                            binding.typeReference?.let { put(assoc, it) }
                        }

                    }
                }
                // Fn() -> T
                is RsPsiPathParameters.FnSugar -> buildMap {
                    if (args.outputArg != null) {
                        val outputParam = path.knownItems.FnOnce?.findAssociatedType("Output")
                        if (outputParam != null) {
                            put(outputParam, args.outputArg)
                        }
                    }
                }
                null -> emptyMap()
            }
        } else {
            emptyMap<RsTypeAlias, RsTypeReference>()
        }
    }

    return RsPsiSubstitution(typeSubst, regionSubst, constSubst, assocTypes)
}

private fun <Param, Arg> associateSubst(
    parameters: List<Param>,
    arguments: List<Arg>?,
    areOptionalArgs: Boolean
): Map<Param, Value<Arg>> {
    return parameters.withIndex().associate { (i, param) ->
        val value = if (areOptionalArgs && arguments == null) {
            Value.OptionalAbsent
        } else if (arguments != null && i < arguments.size) {
            Value.Present(arguments[i])
        } else {
            Value.RequiredAbsent
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

private fun resolveAssocTypeBinding(trait: RsTraitItem, binding: RsAssocTypeBinding): RsTypeAlias? =
    collectResolveVariants(binding.referenceName) { processAssocTypeVariants(trait, it) }
        .singleOrNull() as? RsTypeAlias?

/** Resolves a reference through type aliases */
fun RsPathReference.deepResolve(): RsElement? =
    advancedDeepResolve()?.element

/** Resolves a reference through type aliases */
fun RsPathReference.advancedDeepResolve(): BoundElement<RsElement>? {
    val boundElement = advancedResolve()?.let { resolved ->
        // Resolve potential `Self` inside `impl`
        if (resolved.element is RsImplItem && element.hasCself) {
            (resolved.element.typeReference?.skipParens() as? RsBaseType)?.path?.reference?.advancedResolve() ?: resolved
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
        val resolved = ((base.element as RsTypeAlias).typeReference?.skipParens() as? RsBaseType)
            ?.path?.reference?.advancedResolve()
            ?: break
        if (!visited.add(resolved.element)) return null
        // Stop at `type S<T> = T;`
        if (resolved.element is RsTypeParameter) break
        base = resolved.substitute(base.subst)
    }
    return base
}
