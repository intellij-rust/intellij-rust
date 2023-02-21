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
import org.rust.lang.core.psi.ext.isShorthand
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processStructLiteralFieldResolveVariants

class RsStructLiteralFieldReferenceImpl(
    field: RsStructLiteralField
) : RsReferenceCached<RsStructLiteralField>(field) {

    override val cacheDependency: ResolveCacheDependency get() = ResolveCacheDependency.LOCAL_AND_RUST_STRUCTURE

    override fun getVariants(): Array<out LookupElement> = LookupElement.EMPTY_ARRAY

    override fun multiResolveUncached(): List<RsElement> =
        collectResolveVariants(element.referenceName) { processStructLiteralFieldResolveVariants(element, false, it) }

    override fun handleElementRename(newName: String): PsiElement {
        return if (!element.isShorthand) {
            super.handleElementRename(newName)
        } else {
            val identifier = element.identifier ?: return element

            val psiFactory = RsPsiFactory(element.project)
            val newIdent = psiFactory.createIdentifier(newName)
            val colon = psiFactory.createColon()
            val initExpression = psiFactory.createExpression(identifier.text)

            identifier.replace(newIdent)
            element.add(colon)
            element.add(initExpression)
            return element
        }
    }
}
