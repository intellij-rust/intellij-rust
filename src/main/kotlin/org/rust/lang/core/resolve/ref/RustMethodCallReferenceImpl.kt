package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiReferenceBase
import org.rust.lang.core.psi.RustMethodCallExpr
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.util.parentRelativeRange
import org.rust.lang.core.type.RustResolvedType
import org.rust.lang.core.type.inferredType

class RustMethodCallReferenceImpl(
    element: RustMethodCallExpr
) : PsiReferenceBase<RustMethodCallExpr>(element, element.identifier?.parentRelativeRange)
  , RustReference {

    override fun getVariants(): Array<out Any> =
        receiverType.nonStaticMethods.filter { it.name != null }.toTypedArray()

    override fun resolve(): RustNamedElement? =
        receiverType.nonStaticMethods.find {
            it.name == element.identifier?.text
        }

    private val receiverType: RustResolvedType get() = element.expr.inferredType
}
