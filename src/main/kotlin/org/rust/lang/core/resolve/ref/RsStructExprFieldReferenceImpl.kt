/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsStructLiteralField
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.resolve.collectCompletionVariants
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processStructLiteralFieldResolveVariants

class RsStructLiteralFieldReferenceImpl(
    field: RsStructLiteralField
) : RsReferenceCached<RsStructLiteralField>(field),
    RsReference {

    override val RsStructLiteralField.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out LookupElement> =
        collectCompletionVariants { processStructLiteralFieldResolveVariants(element, it) }

    override fun resolveInner(): List<RsElement> =
        collectResolveVariants(element.referenceName) {
            processStructLiteralFieldResolveVariants(element, it)
        }

    override fun handleElementRename(newName: String): PsiElement {
        return if (element.colon != null) {
            super.handleElementRename(newName)
        } else {
            val psiFactory = RsPsiFactory(element.project)
            val newIdent = psiFactory.createIdentifier(newName)
            val colon = psiFactory.createColon()
            val initExpression = psiFactory.createExpression(element.identifier.text)

            element.identifier.replace(newIdent)
            element.add(colon)
            element.add(initExpression)
            return element
        }
    }
}
