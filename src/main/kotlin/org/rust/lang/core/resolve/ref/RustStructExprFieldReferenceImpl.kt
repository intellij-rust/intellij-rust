package org.rust.lang.core.resolve.ref

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import org.rust.lang.core.completion.RustCompletionEngine
import org.rust.lang.core.psi.RsStructExpr
import org.rust.lang.core.psi.RsStructExprField
import org.rust.lang.core.psi.RsNamedElement
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.RustResolveEngine

class RustStructExprFieldReferenceImpl(
    field: RsStructExprField
) : RustReferenceBase<RsStructExprField>(field),
    RustReference {

    override val RsStructExprField.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out LookupElement> =
        RustCompletionEngine.completeFieldName(element)

    override fun resolveInner(): List<RsNamedElement> {
        val structExpr = element.parentOfType<RsStructExpr>() ?: return emptyList()

        return RustResolveEngine.resolveStructExprField(structExpr, element.referenceName)
    }
}
