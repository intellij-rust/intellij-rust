/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.*

interface RsItemsOwner : RsElement {
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
    val macroDefinitionList: List<RsMacroDefinition>
    val macroCallList: List<RsMacroCall>
}

