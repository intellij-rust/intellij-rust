package org.rust.lang.core.resolve.ref

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import org.rust.lang.core.completion.createLookupElement
import org.rust.lang.core.psi.RsFieldExpr
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.psi.ext.fieldIndex
import org.rust.lang.core.psi.ext.fieldName
import org.rust.lang.core.resolve.ResolveConfig
import org.rust.lang.core.resolve.processResolveVariants

class RsFieldExprReferenceImpl(
    fieldExpr: RsFieldExpr
) : RsReferenceBase<RsFieldExpr>(fieldExpr),
    RsReference {

    override val RsFieldExpr.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out Any> {
        val result = mutableListOf<LookupElement>()
        processResolveVariants(element, ResolveConfig(isCompletion = true)) {
            val name = it.name
            val lookupElement = it.element.createLookupElement(name)
            result += lookupElement
            false
        }
        return result.toTypedArray()
    }

    override fun resolveInner(): List<RsCompositeElement> {
        val result = mutableListOf<RsCompositeElement>()
        processResolveVariants(element, ResolveConfig(isCompletion = false)) {
            val name = element.fieldName ?: element.fieldIndex?.toString()
            if (name == it.name) result += it.element
            false
        }
        return result
    }

    override fun handleElementRename(newName: String): PsiElement {
        element.fieldId.identifier?.let { doRename(it, newName) }
        return element
    }
}
