package org.rust.lang.core.resolve.ref

import org.rust.lang.core.psi.RustFieldExprElement
import org.rust.lang.core.resolve.RustResolveEngine

class RustFieldExprReferenceImpl(
    field: RustFieldExprElement
) : RustReferenceBase<RustFieldExprElement>(field, (field.identifier ?: field.integerLiteral)!!)
  , RustReference {

    override fun getVariants(): Array<out Any> = emptyArray()

    override fun resolveVerbose(): RustResolveEngine.ResolveResult =
        RustResolveEngine.resolveFieldExpr(element)
}
