package org.rust.lang.core.psi

interface RsFieldsOwner {
    val blockFields: RsBlockFields?
    val tupleFields: RsTupleFields?
}

val RsFieldsOwner.namedFields: List<RsFieldDecl>
    get() = blockFields?.fieldDeclList.orEmpty()

val RsFieldsOwner.positionalFields: List<RsTupleFieldDecl>
    get() = tupleFields?.tupleFieldDeclList.orEmpty()
