/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.psi.ext.RsGenericDeclaration
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.resolve.collectCompletionVariants
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.fnOutputParam
import org.rust.lang.core.resolve.processPathResolveVariants
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type


class RsPathReferenceImpl(
    element: RsPath
) : RsReferenceBase<RsPath>(element),
    RsReference {

    override val RsPath.referenceAnchor: PsiElement get() = referenceNameElement

    override fun resolveInner(): List<BoundElement<RsCompositeElement>> {
        val result = collectResolveVariants(element.referenceName) { processPathResolveVariants(element, false, it) }

        val typeArguments: List<Ty>? = run {
            val inAngles = element.typeArgumentList
            val fnSugar = element.valueParameterList
            when {
                inAngles != null -> inAngles.typeReferenceList.map { it.type }
                fnSugar != null -> listOf(
                    TyTuple(fnSugar.valueParameterList.map { it.typeReference?.type ?: TyUnknown })
                )
                else -> null
            }
        }

        val outputArg = element.retType?.typeReference?.type

        return result.map { boundElement ->
            val (element, subst) = boundElement.downcast<RsGenericDeclaration>() ?: return@map boundElement

            val assocTypes = run {
                if (element is RsTraitItem) {
                    val aliases = element.typeAliasList
                        .mapNotNull { it.type as? TyTypeParameter }
                        .associateBy { it }

                    val outputParam = element.fnOutputParam
                    return@run aliases + if (outputArg != null && outputParam != null) {
                        mapOf(outputParam to outputArg)
                    } else {
                        emptySubstitution
                    }
                }
                emptySubstitution
            }

            val parameters = element.typeParameters.map { TyTypeParameter(it) }

            BoundElement(element,
                subst
                    + (if (typeArguments != null) parameters.zip(typeArguments).toMap() else parameters.associateBy { it })
                    + assocTypes
            )
        }
    }

    override fun getVariants(): Array<out Any> =
        collectCompletionVariants { processPathResolveVariants(element, true, it) }

    override fun isReferenceTo(element: PsiElement): Boolean {
        val target = resolve()
        return element.manager.areElementsEquivalent(target, element)
    }
}
