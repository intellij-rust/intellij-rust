package org.rust.lang.core.resolve.scope

import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustPathPart
import org.rust.lang.core.psi.util.match
import org.rust.lang.core.resolve.RustResolveEngine

public interface RustResolveScope : RustCompositeElement {

    public fun lookup(pathPart: RustPathPart): RustNamedElement? {
        for (c in children) {
            if (c is RustNamedElement && pathPart.identifier.match(c.name)) {
                return pathPart.pathPart.let {
                    tail ->
                        when (tail) {
                            null -> return c
                            else -> when (c) {
                                        is RustResolveScope -> c.lookup(tail)
                                        else -> null
                                    }
                        }
                }
            }
        }

        return null;
    }

}

//
// Extension points
//

internal fun RustResolveScope.resolveWith(v: RustResolveEngine.ResolveScopeVisitor): RustNamedElement? {
    this.accept(v)
    return v.matched
}
