package org.rust.lang.core.psi

interface RsTupleOrStructFieldDeclElement : RsOuterAttributeOwner, RsVisibilityOwner {
    val typeReference: RsTypeReference
}
