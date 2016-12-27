package org.rust.lang.core.psi

import org.rust.lang.core.parser.RustPsiTreeUtil

interface RustItemsOwner : RustCompositeElement {
    val fnItemList: List<RustFnItemElement>
    val modItemList: List<RustModItemElement>
    val staticItemList: List<RustStaticItemElement>
    val structItemList: List<RustStructItemElement>
    val enumItemList: List<RustEnumItemElement>
    val unionItemList: List<RustUnionItemElement>
    val implItemList: List<RustImplItemElement>
    val traitItemList: List<RustTraitItemElement>
    val typeItemList: List<RustTypeItemElement>
    val useItemList: List<RustUseItemElement>
    val modDeclItemList: List<RustModDeclItemElement>
    val externCrateItemList: List<RustExternCrateItemElement>
    val foreignModItemList: List<RustForeignModItemElement>
}

