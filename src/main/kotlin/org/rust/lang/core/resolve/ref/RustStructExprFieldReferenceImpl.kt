package org.rust.lang.core.resolve.ref

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.PsiElement
import org.rust.lang.core.completion.RustCompletionEngine
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustStructExprElement
import org.rust.lang.core.psi.RustStructExprFieldElement
import org.rust.lang.core.psi.referenceName
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.RustResolveEngine

class RustStructExprFieldReferenceImpl(
    field: RustStructExprFieldElement
) : RustReferenceBase<RustStructExprFieldElement>(field),
    RustReference {

    override val RustStructExprFieldElement.referenceAnchor: PsiElement get() = referenceNameElement

    override fun getVariants(): Array<out LookupElement> =
        // TODO(kudinkin): Fix in the similar way
        RustCompletionEngine.completeFieldName(element)

    override fun resolveInner(): List<RustNamedElement> {
        val structExpr = element.parentOfType<RustStructExprElement>() ?: return emptyList()

        return RustResolveEngine.resolveStructExprField(structExpr, element.referenceName)
    }
}
