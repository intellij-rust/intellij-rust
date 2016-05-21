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
            is RustTraitItem    -> RustTraitTreeElement(it)
            is RustStructItem   -> RustStructTreeElement(it)
            is RustImplItem     -> RustImplTreeElement(it)
            is RustFnItem       -> RustFnTreeElement(it)
            is RustModItem      -> RustModTreeElement(it)
            else                -> null
        }
}
