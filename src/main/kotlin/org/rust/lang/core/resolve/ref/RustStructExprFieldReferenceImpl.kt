package org.rust.lang.core.resolve.ref

import org.rust.lang.core.completion.RustCompletionEngine
import org.rust.lang.core.psi.RustStructExprElement
import org.rust.lang.core.psi.RustStructExprFieldElement
import org.rust.lang.core.psi.referenceName
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.RustResolveEngine

class RustStructExprFieldReferenceImpl(
    field: RustStructExprFieldElement
) : RustReferenceBase<RustStructExprFieldElement>(field, field.referenceNameElement)
  , RustReference {

    override fun getVariants(): Array<out Any> =
        // TODO(kudinkin): Fix in the similar way
        RustCompletionEngine.completeFieldName(element)

    override fun resolveVerbose(): RustResolveEngine.ResolveResult {
        val structExpr  = element.parentOfType<RustStructExprElement>()

        return if (structExpr != null)
            RustResolveEngine.resolveStructExprField(structExpr, element.referenceName)
        else
            RustResolveEngine.ResolveResult.Unresolved
    }
}
