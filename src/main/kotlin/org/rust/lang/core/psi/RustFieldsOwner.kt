package org.rust.lang.core.psi

interface RustFieldsOwner {
    val blockFields: RsBlockFields?
    val tupleFields: RsTupleFields?
}

val RustFieldsOwner.namedFields: List<RsFieldDecl>
    get() = blockFields?.fieldDeclList.orEmpty()

val RustFieldsOwner.positionalFields: List<RsTupleFieldDecl>
    get() = tupleFields?.tupleFieldDeclList.orEmpty()
