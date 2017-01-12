package org.rust.lang.core.psi

interface RustGenericDeclaration : RustCompositeElement {
    val typeParameterList: RsTypeParameterList?
    val whereClause: RsWhereClause?
}

val RustGenericDeclaration.typeParameters: List<RsTypeParameter>
    get() = typeParameterList?.typeParameterList.orEmpty()
