package org.rust.lang.core.psi

interface RustFieldsOwner {
    val blockFields: RustBlockFieldsElement?
    val tupleFields: RustTupleFieldsElement?
}

val RustFieldsOwner.fields: List<RustFieldDeclElement>
    get() = blockFields?.fieldDeclList.orEmpty()
