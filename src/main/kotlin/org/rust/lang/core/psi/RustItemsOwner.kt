package org.rust.lang.core.psi

import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.resolve.scope.RustResolveScope

interface RustItemsOwner : RustResolveScope

private inline fun <reified I : RustItemElement> RustItemsOwner.items(): List<I> =
    PsiTreeUtil.getChildrenOfTypeAsList(this, I::class.java)

val RustItemsOwner.allItems: List<RustItemElement> get() = items()

val RustItemsOwner.allItemDefinitions: List<RustNamedElement>
    get() = listOf<List<RustNamedElement>>(
        items<RustConstItemElement>(),
        items<RustEnumItemElement>(),
        items<RustFnItemElement>(),
        items<RustModItemElement>(),
        items<RustStaticItemElement>(),
        items<RustStructItemElement>(),
        items<RustTraitItemElement>(),
        items<RustTypeItemElement>()
    ).flatten()

val RustItemsOwner.impls: List<RustImplItemElement> get() = items()
val RustItemsOwner.functions: List<RustFnItemElement> get() = items()
val RustItemsOwner.useDeclarations: List<RustUseItemElement> get() = items()
val RustItemsOwner.modDecls: List<RustModDeclItemElement> get() = items()
val RustItemsOwner.externCrates: List<RustExternCrateItemElement> get() = items()

