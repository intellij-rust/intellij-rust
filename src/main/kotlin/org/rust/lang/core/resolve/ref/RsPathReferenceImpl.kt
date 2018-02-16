/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsPathExpr
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsGenericDeclaration
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.collectCompletionVariants
import org.rust.lang.core.resolve.collectPathResolveVariants
import org.rust.lang.core.resolve.processPathResolveVariants
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.infer.foldTyInferWith
import org.rust.lang.core.types.inference
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type


class RsPathReferenceImpl(
    element: RsPath
) : RsReferenceBase<RsPath>(element),
    RsPathReference{

    override val RsPath.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> =
        collectCompletionVariants { processPathResolveVariants(ImplLookup.relativeTo(element), element, true, it) }

    override fun isReferenceTo(element: PsiElement): Boolean {
        val target = resolve()
        return element.manager.areElementsEquivalent(target, element)
    }

    override fun advancedResolve(): BoundElement<RsElement>? =
        advancedMultiResolve().singleOrNull()

    override fun advancedMultiResolve(): List<BoundElement<RsElement>> =
        (element.parent as? RsPathExpr)?.let { it.inference?.getResolvedPath(it)?.map { BoundElement(it) } }
            ?: advancedCachedMultiResolve()

    override fun multiResolve(incompleteCode: Boolean): Array<out ResolveResult> {
        return advancedMultiResolve().toTypedArray()
    }

    override fun multiResolve(): List<RsNamedElement> =
        advancedMultiResolve().mapNotNull { it.element as? RsNamedElement }

    private fun advancedCachedMultiResolve(): List<BoundElement<RsElement>> {
        return ResolveCache.getInstance(element.project)
            .resolveWithCaching(this, Resolver,
                /* needToPreventRecursion = */ true,
                /* incompleteCode = */ false)
            .orEmpty()
            // We can store a fresh `TyInfer.TyVar` to the cache for `_` path parameter (like `Vec<_>`), but
            // TyVar is mutable type, so we must copy it after retrieving from the cache
            .map { it.foldTyInferWith { if (it is TyInfer.TyVar) TyInfer.TyVar(it.origin) else it } }
    }

    private object Resolver : ResolveCache.AbstractResolver<RsPathReferenceImpl, List<BoundElement<RsElement>>> {
        override fun resolve(ref: RsPathReferenceImpl, incompleteCode: Boolean): List<BoundElement<RsElement>> {
            return resolvePath(ref.element)
        }
    }
}

fun resolvePath(path: RsPath, lookup: ImplLookup = ImplLookup.relativeTo(path)): List<BoundElement<RsElement>> {
    val result = collectPathResolveVariants(path.referenceName) {
        processPathResolveVariants(lookup, path, false, it)
    }

    val typeArguments: List<Ty>? = run {
        val inAngles = path.typeArgumentList
        val fnSugar = path.valueParameterList
        when {
            inAngles != null -> inAngles.typeReferenceList.map { it.type }
            fnSugar != null -> listOf(
                TyTuple(fnSugar.valueParameterList.map { it.typeReference?.type ?: TyUnknown })
            )
            else -> null
        }
    }

    val outputArg = path.retType?.typeReference?.type

    return result.map { boundElement ->
        val (element, subst) = boundElement.downcast<RsGenericDeclaration>() ?: return@map boundElement

        val assocTypes = run {
            if (element is RsTraitItem) {
                val outputParam = lookup.fnOnceOutput
                return@run if (outputArg != null && outputParam != null) {
                    mapOf(outputParam to outputArg)
                } else {
                    emptyMap()
                }
            }
            emptyMap<RsTypeAlias, Ty>()
        }

        val parameters = element.typeParameters.map { TyTypeParameter.named(it) }

        BoundElement(element,
            subst + parameters.zip(typeArguments ?: parameters).toMap(),
            assocTypes
        )
    }
}
