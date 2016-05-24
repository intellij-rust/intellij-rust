package org.rust.ide.structure

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import org.rust.lang.core.psi.*

open class RustModTreeElement(item: RustMod) : PsiTreeElementBase<RustMod>(item) {
    override fun getPresentableText() = element?.name

    override fun getChildrenBase(): Collection<StructureViewTreeElement> =
        element?.items.orEmpty().mapNotNull { toTreeElement(it) }

    private fun toTreeElement(it: RustItem): StructureViewTreeElement? =
        when (it) {
            is RustEnumItem     -> RustEnumTreeElement(it)
            is RustFnItem       -> RustFnTreeElement(it)
            is RustImplItem     -> RustImplTreeElement(it)
            is RustModDeclItem  -> RustModDeclTreeElement(it)
            is RustModItem      -> RustModTreeElement(it)
            is RustStructItem   -> RustStructTreeElement(it)
            is RustTraitItem    -> RustTraitTreeElement(it)
            is RustTypeItem     -> RustTypeTreeElement(it)
            else                -> null
        }
}
