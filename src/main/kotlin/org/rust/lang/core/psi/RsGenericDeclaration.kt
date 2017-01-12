package org.rust.lang.core.psi

interface RsGenericDeclaration : RsCompositeElement {
    val typeParameterList: RsTypeParameterList?
    val whereClause: RsWhereClause?
}

val RsGenericDeclaration.typeParameters: List<RsTypeParameter>
    get() = typeParameterList?.typeParameterList.orEmpty()
