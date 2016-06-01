package org.rust.lang.core.resolve.ref

import org.rust.lang.core.completion.RustCompletionEngine
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.RustResolveEngine

class RustFieldReferenceImpl(
    field: RustFieldName
) : RustReferenceBase<RustFieldName>(field, field.identifier)
  , RustReference {

    override fun getVariants(): Array<out Any> = RustCompletionEngine.completeFieldName(element)

    override fun resolveVerbose(): RustResolveEngine.ResolveResult = RustResolveEngine.resolveFieldName(element)
}
