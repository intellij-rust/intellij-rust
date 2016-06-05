package org.rust.lang.core.resolve.ref

import org.rust.lang.core.psi.RustFieldExprElement
import org.rust.lang.core.psi.RustFieldIdElement
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.RustResolveEngine

class RustFieldExprReferenceImpl(
    fieldExpr: RustFieldExprElement
) : RustReferenceBase<RustFieldIdElement>(fieldExpr.fieldId!!, fieldExpr.fieldId!!)
  , RustReference {

    override fun getVariants(): Array<out Any> = emptyArray()

    override fun resolveVerbose(): RustResolveEngine.ResolveResult =
        RustResolveEngine.resolveFieldExpr(element.parentOfType<RustFieldExprElement>()!!)
}
