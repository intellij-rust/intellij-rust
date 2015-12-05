package org.rust.lang.core.resolve.scope

import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustDeclaringElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.resolve.RustResolveEngine

public interface RustResolveScope : RustCompositeElement {
    fun getDeclarations(): Collection<RustDeclaringElement>
}

//
// Extension points
//

internal fun RustResolveScope.resolveWith(v: RustResolveEngine.ResolveScopeVisitor): RustNamedElement? {
    this.accept(v)
    return v.matched
}
