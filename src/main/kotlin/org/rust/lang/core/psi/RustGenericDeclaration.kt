package org.rust.lang.core.psi

import org.rust.lang.core.resolve.scope.RustResolveScope

interface RustGenericDeclaration : RustResolveScope {
    val genericParams: RustGenericParams?
    val whereClause: RustWhereClause?
}
