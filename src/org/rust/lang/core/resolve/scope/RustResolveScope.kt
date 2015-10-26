package org.rust.lang.core.resolve.scope

import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustPatIdent
import org.rust.lang.core.resolve.RustResolveEngine

public interface RustResolveScope : RustCompositeElement {
    fun listDeclarations(): List<RustPatIdent>
}

//
// Extension points
//

internal fun RustResolveScope.resolveWith(v: RustResolveEngine.ResolveScopeVisitor): RustNamedElement? {
    this.accept(v)
    return v.matched
}
