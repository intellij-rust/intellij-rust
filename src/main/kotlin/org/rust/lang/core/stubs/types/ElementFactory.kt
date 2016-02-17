package org.rust.lang.core.stubs.types

fun factory(name: String): RustStubElementType<*, *> = when (name) {
    "CONST_ITEM"       -> RustConstItemStubElementType(name)
    "ENUM_ITEM"        -> RustEnumItemStubElementType(name)
    "EXTERN_CRATE_ITEM" -> RustExternCrateItemStubElementType(name)
    "FILE_MOD_ITEM"    -> RustFileModItemStubElementType(name)
    "FN_ITEM"          -> RustFnItemStubElementType(name)
    "FOREIGN_FN_ITEM"  -> RustForeignFnItemStubElementType(name)
    "FOREIGN_MOD_ITEM" -> RustForeignModItemStubElementType(name)
    "IMPL_ITEM"        -> RustImplItemStubElementType(name)
    "MOD_DECL_ITEM"    -> RustModDeclItemStubElementType(name)
    "MOD_ITEM"         -> RustModItemStubElementType(name)
    "STATIC_ITEM"      -> RustStaticItemStubElementType(name)
    "STRUCT_ITEM"      -> RustStructItemStubElementType(name)
    "TRAIT_ITEM"       -> RustTraitItemStubElementType(name)
    "TYPE_ITEM"        -> RustTypeItemStubElementType(name)
    "USE_ITEM"         -> RustUseItemStubElementType(name)

    else               -> {
        throw IllegalArgumentException("Unknown element $name")
    }
}
