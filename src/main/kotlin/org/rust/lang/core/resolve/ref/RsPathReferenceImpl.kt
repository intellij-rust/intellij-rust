/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.psi.ext.RsGenericDeclaration
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.resolve.*
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.type


class RsPathReferenceImpl(
    element: RsPath
) : RsReferenceBase<RsPath>(element),
    RsReference {

    override val RsPath.referenceAnchor: PsiElement get() = referenceNameElement

    override fun resolveInner(): List<BoundElement<RsCompositeElement>> {
        val result = collectResolveVariants(element.referenceName) { processPathResolveVariants(element, false, it) }
        val typeArguments = element.typeArgumentList?.typeReferenceList.orEmpty().map { it.type }
        if (typeArguments.isEmpty()) return result

        return result.map { boundElement ->
            val parameters = (boundElement.element as? RsGenericDeclaration)?.typeParameters ?: return@map boundElement
            BoundElement(
                boundElement.element,
                boundElement.subst + parameters.map { TyTypeParameter(it) }.zip(typeArguments).toMap()
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
