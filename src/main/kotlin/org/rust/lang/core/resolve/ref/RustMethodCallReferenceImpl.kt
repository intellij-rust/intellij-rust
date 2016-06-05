package org.rust.lang.core.resolve.ref

import org.rust.lang.core.psi.RustMethodCallExprElement
import org.rust.lang.core.psi.util.parentRelativeRange
import org.rust.lang.core.resolve.RustResolveEngine

class RustMethodCallReferenceImpl(
    element: RustMethodCallExprElement
) : RustReferenceBase<RustMethodCallExprElement>(element, element.identifier!!.parentRelativeRange)
  , RustReference {

    override fun getVariants(): Array<out Any> = emptyArray()

    override fun resolveVerbose(): RustResolveEngine.ResolveResult =
        RustResolveEngine.resolveMethodCallExpr(element)

}
