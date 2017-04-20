package org.rust.lang.core.resolve.ref

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsStructLiteralField
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.*

class RsStructLiteralFieldReferenceImpl(
    field: RsStructLiteralField
) : RsReferenceBase<RsStructLiteralField>(field),
    RsReference {

    override val RsStructLiteralField.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out LookupElement> =
        collectCompletionVariants { processResolveVariants(element, it) }

    override fun resolveInner(): List<RsCompositeElement> =
        collectResolveVariants(element.referenceName) {
            processResolveVariants(element, it)
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
