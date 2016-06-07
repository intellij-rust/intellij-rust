package org.rust.lang.core.psi

import org.rust.lang.core.resolve.scope.RustResolveScope

interface RustItemsOwner : RustResolveScope {
    val itemList: List<RustItemElement>
}

val RustItemsOwner.useDeclarations: List<RustUseItemElement> get() = itemList.filterIsInstance<RustUseItemElement>()

val RustItemsOwner.externalCrates: Collection<RustExternCrateItemElement>
    get() = itemList.filterIsInstance<RustExternCrateItemElement>()

val RustItemsOwner.modDecls: Collection<RustModDeclItemElement>
    get() = itemList.filterIsInstance<RustModDeclItemElement>()
