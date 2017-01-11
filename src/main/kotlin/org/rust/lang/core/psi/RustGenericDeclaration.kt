package org.rust.lang.core.psi

interface RustGenericDeclaration : RustCompositeElement {
    val typeParameterList: RustTypeParameterListElement?
    val whereClause: RustWhereClauseElement?
}
