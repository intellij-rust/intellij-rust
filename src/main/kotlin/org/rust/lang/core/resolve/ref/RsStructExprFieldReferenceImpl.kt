package org.rust.lang.core.resolve.ref

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsStructExprField
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.CompletionProcessor
import org.rust.lang.core.resolve.MultiResolveProcessor
import org.rust.lang.core.resolve.processResolveVariants

class RsStructExprFieldReferenceImpl(
    field: RsStructExprField
) : RsReferenceBase<RsStructExprField>(field),
    RsReference {

    override val RsStructExprField.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out LookupElement> =
        CompletionProcessor().run { processResolveVariants(element, it) }

    override fun resolveInner(): List<RsCompositeElement> =
        MultiResolveProcessor(element.referenceName).run {
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
