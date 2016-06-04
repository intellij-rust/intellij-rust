package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiReferenceBase
import org.rust.lang.core.psi.RustMethodCallExprElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.util.parentRelativeRange
import org.rust.lang.core.type.*

class RustMethodCallReferenceImpl(
    element: RustMethodCallExprElement
) : PsiReferenceBase<RustMethodCallExprElement>(element, element.identifier?.parentRelativeRange)
  , RustReference {

    override fun getVariants(): Array<out Any> = emptyArray()

    override fun resolve(): RustNamedElement? {
        val recType = receiverType
        return when (recType) {
            is RustStructType -> recType.nonStaticMethods.find { it.name == referenceName }

            is RustTraitImplType -> recType.trait.traitBody.traitMethodMemberList.find { it.name == referenceName }

            is RustImplType -> recType.type .let { it as? RustStructType }
                                           ?.let { it.nonStaticMethods.find { it.name == referenceName } }
            else -> null
        }
    }

    private val referenceName: String?
        get () = element.identifier?.text

    private val receiverType: RustType
        get() = element.expr.resolvedType
}
