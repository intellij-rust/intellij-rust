package org.rust.lang.core.psi

interface RustItemsOwner : RustCompositeElement {
    val functionList: List<RsFunction>
    val modItemList: List<RsModItem>
    val constantList: List<RsConstant>
    val structItemList: List<RsStructItem>
    val enumItemList: List<RsEnumItem>
    val implItemList: List<RsImplItem>
    val traitItemList: List<RsTraitItem>
    val typeAliasList: List<RsTypeAlias>
    val useItemList: List<RsUseItem>
    val modDeclItemList: List<RsModDeclItem>
    val externCrateItemList: List<RsExternCrateItem>
    val foreignModItemList: List<RsForeignModItem>
}

