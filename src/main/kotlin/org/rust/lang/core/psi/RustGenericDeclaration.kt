package org.rust.lang.core.psi

interface RustGenericDeclaration : RustCompositeElement {
    val genericParams: RustGenericParams
    val whereClause: RustWhereClause?
}
