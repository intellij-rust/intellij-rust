package org.rust.lang.core.psi

interface RustItemsOwner : RustCompositeElement {
    val functionList: List<RustFunctionElement>
    val modItemList: List<RustModItemElement>
    val constantList: List<RustConstantElement>
    val structItemList: List<RustStructItemElement>
    val enumItemList: List<RustEnumItemElement>
    val unionItemList: List<RustUnionItemElement>
    val implItemList: List<RustImplItemElement>
    val traitItemList: List<RustTraitItemElement>
    val typeAliasList: List<RustTypeAliasElement>
    val useItemList: List<RustUseItemElement>
    val modDeclItemList: List<RustModDeclItemElement>
    val externCrateItemList: List<RustExternCrateItemElement>
    val foreignModItemList: List<RustForeignModItemElement>
}

