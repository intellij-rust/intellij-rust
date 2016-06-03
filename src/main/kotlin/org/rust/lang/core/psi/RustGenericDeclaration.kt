package org.rust.lang.core.psi

import org.rust.lang.core.resolve.scope.RustResolveScope

interface RustGenericDeclaration : RustResolveScope {
    val genericParams: RustGenericParamsElement?
    val whereClause: RustWhereClauseElement?
}
