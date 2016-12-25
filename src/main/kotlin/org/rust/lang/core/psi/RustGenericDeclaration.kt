package org.rust.lang.core.psi

interface RustGenericDeclaration : RustCompositeElement {
    val genericParams: RustGenericParamsElement?
    val whereClause: RustWhereClauseElement?
}
