package org.rust.lang.core.stubs

import org.rust.lang.core.stubs.elements.*

fun factory(name: String): RustNamedStubElementType<*, *> = when (name) {
    "CONST_ITEM"       -> RustConstItemStubElementType
    "ENUM_ITEM"        -> RustEnumItemStubElementType
    "FN_ITEM"          -> RustFnItemStubElementType
    "MOD_DECL_ITEM"    -> RustModDeclItemStubElementType
    "MOD_ITEM"         -> RustModItemStubElementType
    "STATIC_ITEM"      -> RustStaticItemStubElementType
    "STRUCT_ITEM"      -> RustStructItemStubElementType
    "TRAIT_ITEM"       -> RustTraitItemStubElementType
    "TYPE_ITEM"        -> RustTypeItemStubElementType

    else               -> {
        throw IllegalArgumentException("Unknown element $name")
    }
}
