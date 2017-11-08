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
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.psi.ext.RsGenericDeclaration
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.resolve.collectCompletionVariants
import org.rust.lang.core.resolve.collectPathResolveVariants
import org.rust.lang.core.resolve.processPathResolveVariants
import org.rust.lang.core.types.BoundElement
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

    override fun advancedResolve(): BoundElement<RsCompositeElement>? =
        advancedMultiResolve().firstOrNull()

    override fun multiResolve(incompleteCode: Boolean): Array<out ResolveResult> =
        advancedMultiResolve().toTypedArray()

    override fun multiResolve(): List<RsNamedElement> =
        advancedMultiResolve().mapNotNull { it.element as? RsNamedElement }

    override fun advancedMultiResolve(): List<BoundElement<RsCompositeElement>> =
        (element.parent as? RsPathExpr)?.let { it.inference?.getResolvedPath(it)?.map { BoundElement(it) } }
            ?: advancedCachedMultiResolve()

    private fun advancedCachedMultiResolve(): List<BoundElement<RsCompositeElement>> {
        return ResolveCache.getInstance(element.project)
            .resolveWithCaching(this, Resolver,
                /* needToPreventRecursion = */ true,
                /* incompleteCode = */ false).orEmpty()
    }

    private object Resolver : ResolveCache.AbstractResolver<RsPathReferenceImpl, List<BoundElement<RsCompositeElement>>> {
        override fun resolve(ref: RsPathReferenceImpl, incompleteCode: Boolean): List<BoundElement<RsCompositeElement>> {
            return resolvePath(ref.element)
        }
    }
}

fun resolvePath(path: RsPath, lookup: ImplLookup = ImplLookup.relativeTo(path)): List<BoundElement<RsCompositeElement>> {
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
                val outputParam = lookup.fnOutputParam
                return@run if (outputArg != null && outputParam != null) {
                    mapOf(outputParam to outputArg)
                } else {
                    emptySubstitution
                }
            }
            emptySubstitution
        }

        val parameters = element.typeParameters.map { TyTypeParameter.named(it) }

        BoundElement(element,
            subst
                + parameters.zip(typeArguments ?: parameters).toMap()
                + assocTypes
        )
    }
}
