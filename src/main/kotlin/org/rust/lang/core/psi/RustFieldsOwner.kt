package org.rust.lang.core.psi

interface RustFieldsOwner {
    val blockFields: RustBlockFieldsElement?
    val tupleFields: RustTupleFieldsElement?
}

val RustFieldsOwner.namedFields: List<RustFieldDeclElement>
    get() = blockFields?.fieldDeclList.orEmpty()

val RustFieldsOwner.positionalFields: List<RustTupleFieldDeclElement>
    get() = tupleFields?.tupleFieldDeclList.orEmpty()
