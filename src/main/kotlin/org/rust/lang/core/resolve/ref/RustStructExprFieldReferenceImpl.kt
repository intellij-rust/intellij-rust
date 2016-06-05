package org.rust.lang.core.resolve.ref

import org.rust.lang.core.completion.RustCompletionEngine
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.RustResolveEngine

class RustStructExprFieldReferenceImpl(
    field: RustFieldNameElement
) : RustReferenceBase<RustFieldNameElement>(field, field.identifier)
  , RustReference {

    override fun getVariants(): Array<out Any> =
        // TODO(kudinkin): Fix in the similar way
        RustCompletionEngine.completeFieldName(element)

    override fun resolveVerbose(): RustResolveEngine.ResolveResult {
        val structExpr  = element.parentOfType<RustStructExprElement>()
        val fieldName   = element.name

        return if (structExpr != null && fieldName != null)
            RustResolveEngine.resolveStructExprField(structExpr, fieldName)
        else
            RustResolveEngine.ResolveResult.Unresolved
    }
}
