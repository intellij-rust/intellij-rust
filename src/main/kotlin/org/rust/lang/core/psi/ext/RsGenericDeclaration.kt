package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsTypeParameter
import org.rust.lang.core.psi.RsTypeParameterList
import org.rust.lang.core.psi.RsWhereClause

interface RsGenericDeclaration : RsCompositeElement {
    val typeParameterList: RsTypeParameterList?
    val whereClause: RsWhereClause?
}

val RsGenericDeclaration.typeParameters: List<RsTypeParameter>
    get() = typeParameterList?.typeParameterList.orEmpty()
