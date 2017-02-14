package org.rust.lang.core.resolve.ref

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import org.rust.lang.core.completion.CompletionEngine
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.ResolveEngine

class RsStructExprFieldReferenceImpl(
    field: RsStructExprField
) : RsReferenceBase<RsStructExprField>(field),
    RsReference {

    override val RsStructExprField.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out LookupElement> =
        CompletionEngine.completeFieldName(element)

    override fun resolveInner(): List<RsNamedElement> {
        val structExpr = element.parentOfType<RsStructExpr>() ?: return emptyList()

        return ResolveEngine.resolveStructExprField(structExpr, element.referenceName)
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
