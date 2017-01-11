package org.rust.lang.core.psi

interface RustGenericDeclaration : RustCompositeElement {
    val typeParameterList: RustTypeParameterListElement?
    val whereClause: RustWhereClauseElement?
}

val RustGenericDeclaration.typeParameters: List<RustTypeParameterElement>
    get() = typeParameterList?.typeParameterList.orEmpty()
